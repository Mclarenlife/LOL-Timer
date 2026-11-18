package rift;

import com.sun.jna.*;
import com.google.gson.*;
import java.io.*;
import java.util.*;
import java.util.function.Consumer;

/** All microphone frames and decoding stay on this worker. Only the final command reaches the UI. */
public final class Speech {
    public interface Decoder extends Library {
        Pointer vosk_model_new(String path);
        void vosk_model_free(Pointer model);
        int vosk_model_find_word(Pointer model,String word);
        Pointer vosk_recognizer_new_grm(Pointer model,float rate,String grammar);
        void vosk_recognizer_set_words(Pointer recognizer,int enabled);
        int vosk_recognizer_accept_waveform(Pointer recognizer,byte[] bytes,int count);
        String vosk_recognizer_result(Pointer recognizer);
        String vosk_recognizer_final_result(Pointer recognizer);
        void vosk_recognizer_reset(Pointer recognizer);
        void vosk_recognizer_free(Pointer recognizer);
        void vosk_set_log_level(int level);
    }
    public interface Input extends AutoCloseable {int read(byte[] pcm)throws Exception;void close();}
    public interface Device {String model()throws Exception; String library(); Input microphone()throws Exception;}
    private final Device device;private final Consumer<String> state,heard;private final Consumer<Runnable> ui;
    private volatile long session;private volatile boolean running;private volatile Input stream;
    public Speech(Device device,Consumer<Runnable> ui,Consumer<String> state,Consumer<String> heard){
        this.device=device;this.ui=ui;this.state=state;this.heard=heard;
    }
    private volatile boolean gate=true;
    private final Object signal=new Object();
    public void gate(boolean open){synchronized(signal){gate=open;signal.notifyAll();}}
    public boolean active(){return running;}
    public void stop(){session++;running=false;synchronized(signal){signal.notifyAll();}Input input=stream;if(input!=null)input.close();state.accept("");}
    public void start(){
        if(running)return;running=true;long ticket=++session;state.accept("准备语音模型");
        Thread thread=new Thread(()->listen(ticket),"rift-microphone");thread.setDaemon(true);thread.start();
    }
    private void tell(long ticket,Runnable work){ui.accept(()->{if(session==ticket)work.run();});}
    static String grammar(Decoder decoder,Pointer model){
        Set<String> entries=new LinkedHashSet<>();
        for(String phrase:Commands.phrases()){
            StringBuilder words=new StringBuilder();int pos=0;
            while(pos<phrase.length()){
                int end=phrase.length();
                while(end>pos&&decoder.vosk_model_find_word(model,phrase.substring(pos,end))<0)end--;
                if(end==pos)break;
                if(words.length()>0)words.append(' ');words.append(phrase,pos,end);pos=end;
            }
            if(pos==phrase.length())entries.add(words.toString());
        }
        entries.add("[unk]");return Model.JSON.toJson(entries);
    }
    private void decode(long ticket,String json){
        JsonObject result=JsonParser.parseString(json).getAsJsonObject();
        String phrase=result.get("text").getAsString();
        if(Commands.match(phrase)>=0&&confident(result))tell(ticket,()->heard.accept(phrase));
    }
    private void listen(long ticket){
        Decoder d=null;Pointer model=null,rec=null;Input input=null;String failure="";
        try{
            String path=device.model();if(session!=ticket)return;
            d=Native.load(device.library(),Decoder.class,Collections.singletonMap(Library.OPTION_STRING_ENCODING,"UTF-8"));
            d.vosk_set_log_level(-1);model=d.vosk_model_new(path);
            if(model==null)throw new IOException("中文模型加载失败");
            rec=d.vosk_recognizer_new_grm(model,16000,grammar(d,model));
            if(rec==null)throw new IOException("口令词表加载失败");
            if(session!=ticket)return;
            d.vosk_recognizer_set_words(rec,1);byte[] buffer=new byte[1280];
            while(session==ticket){
                if(!gate){
                    tell(ticket,()->state.accept("快捷键待命"));
                    synchronized(signal){while(session==ticket&&!gate)signal.wait();}
                }
                if(session!=ticket)break;
                d.vosk_recognizer_reset(rec);input=device.microphone();stream=input;tell(ticket,()->state.accept("正在聆听"));
                try{
                    while(session==ticket&&gate){
                        int count=input.read(buffer);if(count<0)throw new IOException("麦克风已断开");
                        if(count>0&&d.vosk_recognizer_accept_waveform(rec,buffer,count)==1)decode(ticket,d.vosk_recognizer_result(rec));
                    }
                    if(session==ticket)decode(ticket,d.vosk_recognizer_final_result(rec));
                }finally{input.close();if(stream==input)stream=null;input=null;}
            }
        }catch(Exception|LinkageError e){failure="语音不可用 · "+(e.getMessage()==null?"请检查麦克风":e.getMessage());}
        finally{
            if(input!=null)input.close();if(stream==input)stream=null;
            if(rec!=null)d.vosk_recognizer_free(rec);if(model!=null)d.vosk_model_free(model);
            final String message=failure;tell(ticket,()->{running=false;state.accept(message);});
        }
    }
    static boolean confident(JsonObject result){
        JsonArray words=result.getAsJsonArray("result");if(words==null||words.size()==0)return false;
        for(JsonElement w:words)if(w.getAsJsonObject().get("conf").getAsDouble()<0.65)return false;
        return true;
    }
}