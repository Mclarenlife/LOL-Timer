package rift;

import java.nio.file.*;
import java.util.*;
import com.sun.jna.*;
import com.google.gson.*;

public final class VoiceCheck {
    public static void main(String[] args)throws Exception{
        Path root=Paths.get(args[0]);Speech.Decoder d=Native.load(root.resolve("windows/libvosk.dll").toAbsolutePath().toString(),Speech.Decoder.class,Collections.singletonMap(Library.OPTION_STRING_ENCODING,"UTF-8"));
        d.vosk_set_log_level(-1);Pointer m=d.vosk_model_new(root.resolve("model").toAbsolutePath().toString());
        if(m==null)throw new AssertionError("load model");String grammar=Speech.grammar(d,m);
        Files.write(Paths.get(args[1]),grammar.getBytes("UTF-8"));
        if(args.length>2){
            Pointer rec=d.vosk_recognizer_new_grm(m,16000,grammar);d.vosk_recognizer_set_words(rec,1);
            byte[] pcm=Files.readAllBytes(Paths.get(args[2]));byte[] padded=Arrays.copyOf(pcm,pcm.length+64000);boolean found=false;
            for(int i=0;i<padded.length;i+=3200){byte[] part=Arrays.copyOfRange(padded,i,Math.min(i+3200,padded.length));if(d.vosk_recognizer_accept_waveform(rec,part,part.length)==1){
                JsonObject result=JsonParser.parseString(d.vosk_recognizer_result(rec)).getAsJsonObject();String words=result.get("text").getAsString();
                System.out.println("decoded="+words+", match="+Commands.match(words));if(Commands.match(words)==0&&Speech.confident(result))found=true;}}
            d.vosk_recognizer_free(rec);if(!found)throw new AssertionError("expected top flash");
        }
        d.vosk_model_free(m);if(Commands.match("上单。闪现")!=0||Commands.match("打野惩戒")!=13||Commands.match("上单没有闪现")!=-1)throw new AssertionError("grammar");
        System.out.println("PASS command grammar");
    }
}