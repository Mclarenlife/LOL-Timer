package rift;

import java.nio.file.*;
import java.io.*;
import javax.sound.sampled.*;

final class DesktopMic implements Speech.Device {
    static final AudioFormat FORMAT=new AudioFormat(16000,16,1,true,false);
    volatile String selected="";
    static java.util.List<String> devices(){
        java.util.ArrayList<String> names=new java.util.ArrayList<>();
        for(Mixer.Info info:AudioSystem.getMixerInfo())if(AudioSystem.getMixer(info).isLineSupported(new DataLine.Info(TargetDataLine.class,FORMAT)))names.add(info.getName());
        return names;
    }
    private Path resources(){try{return Paths.get(DesktopMic.class.getProtectionDomain().getCodeSource().getLocation().toURI()).getParent().resolve("speech");}catch(Exception e){throw new IllegalStateException(e);}}
    public String model(){return resources().resolve("model").toString();}
    public String library(){return resources().resolve("libvosk.dll").toString();}
    public Speech.Input microphone()throws Exception{
        String name=selected;Mixer.Info mixer=null;
        if(!name.isEmpty()){
            for(Mixer.Info info:AudioSystem.getMixerInfo())if(info.getName().equals(name)){mixer=info;break;}
            if(mixer==null)throw new IOException("所选录音设备已断开，请重新选择");
        }
        TargetDataLine line=AudioSystem.getTargetDataLine(FORMAT,mixer);line.open(FORMAT);line.start();
        return new Speech.Input(){
            public int read(byte[] buf){return line.read(buf,0,buf.length);}
            public synchronized void close(){if(line.isOpen()){line.stop();line.close();}}
        };
    }
}