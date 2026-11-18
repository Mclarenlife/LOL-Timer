package rift;

import android.content.Context;
import android.content.res.AssetManager;
import android.media.*;
import java.io.*;

final class AndroidMic implements Speech.Device {
    private final Context context;
    AndroidMic(Context context){this.context=context.getApplicationContext();}
    public String library(){return "vosk";}
    public synchronized String model()throws IOException{
        File root=new File(context.getFilesDir(),"speech-cn-022");File ready=new File(root,".ready");
        synchronized(AndroidMic.class){if(!ready.exists()){unpack(context.getAssets(),"model",root);if(!ready.createNewFile())throw new IOException("模型初始化失败");}}
        return root.getAbsolutePath();
    }
    private static void unpack(AssetManager assets,String path,File dest)throws IOException{
        String[] items=assets.list(path);
        if(items!=null&&items.length>0){
            if(!dest.isDirectory()&&!dest.mkdirs())throw new IOException("无法创建模型目录");
            for(String name:items)unpack(assets,path+"/"+name,new File(dest,name));
        }else try(InputStream in=assets.open(path);OutputStream out=new FileOutputStream(dest)){
            byte[] bytes=new byte[65536];int n;while((n=in.read(bytes))!=-1)out.write(bytes,0,n);
        }
    }
    public Speech.Input microphone()throws IOException{
        int size=AudioRecord.getMinBufferSize(16000,AudioFormat.CHANNEL_IN_MONO,AudioFormat.ENCODING_PCM_16BIT);
        if(size<0)throw new IOException("当前麦克风不支持 16 kHz");
        AudioRecord recorder=new AudioRecord(MediaRecorder.AudioSource.VOICE_RECOGNITION,16000,AudioFormat.CHANNEL_IN_MONO,AudioFormat.ENCODING_PCM_16BIT,Math.max(size,6400));
        if(recorder.getState()!=AudioRecord.STATE_INITIALIZED){recorder.release();throw new IOException("麦克风初始化失败");}
        try{recorder.startRecording();}catch(RuntimeException e){recorder.release();throw e;}
        return new Speech.Input(){
            boolean closed;
            public int read(byte[] buf){return recorder.read(buf,0,buf.length);}
            public synchronized void close(){if(!closed){closed=true;try{recorder.stop();}finally{recorder.release();}}}
        };
    }
}