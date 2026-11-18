package rift;

import com.sun.jna.Library;
import com.sun.jna.Native;
import java.awt.event.*;
import java.util.prefs.Preferences;
import javax.swing.*;
import static rift.Studio.*;

final class VoiceKeys {
    interface Keys extends Library {Keys API=Native.load("user32",Keys.class);short GetAsyncKeyState(int vk);}
    static final String[] MODES={"随时说话","长按按键说话","按键切换说话"};
    final Preferences prefs=Preferences.userNodeForPackage(VoiceKeys.class).node("voice");
    final DesktopMic device=new DesktopMic();
    int mode=Math.max(0,Math.min(2,prefs.getInt("mode",0))),vk=prefs.getInt("key",119),mods=prefs.getInt("mods",0);
    boolean down,open,ready,suspended;
    VoiceKeys(){device.selected=prefs.get("device","");}
    static boolean pressed(int key){return (Keys.API.GetAsyncKeyState(key)&0x8000)!=0;}
    boolean chord(){return pressed(vk)&&((mods&1)==0||pressed(17))&&((mods&2)==0||pressed(18))&&((mods&4)==0||pressed(16))&&((mods&8)==0||pressed(91)||pressed(92));}
    void arm(Speech speech){open=mode==0;down=mode!=0&&chord();ready=false;speech.gate(open);speech.start();}
    void poll(Speech speech){
        if(!speech.active()||mode==0||suspended)return;
        handleKey(chord(),speech);
    }
    void handleKey(boolean hit,Speech speech){
        boolean next=mode==1?hit:(hit&&!down?!open:open);
        if(next!=open){open=next;speech.gate(open);}
        down=hit;
    }
    static String name(int key,int mods){return ((mods&1)!=0?"Ctrl + ":"")+((mods&2)!=0?"Alt + ":"")+((mods&4)!=0?"Shift + ":"")+((mods&8)!=0?"Win + ":"")+KeyBinding.name(key);}
    void settings(Desktop desktop,Speech speech){
        boolean resume=speech.active();suspended=true;if(resume)speech.stop();
        try{
            Sheet s=new Sheet(desktop.frame,"语音输入");int[] chosen={mode,vk,mods};String[] input={device.selected};
            JLabel desc=label("",14,MUTED);s.row(label("录音设备",14,MUTED));
            JButton devices=key(input[0].isEmpty()?"系统默认录音设备":input[0],()->{});s.row(devices);
            devices.addActionListener(e->{
                Sheet picker=new Sheet(desktop.frame,"选择录音设备");java.util.List<String> list=device.devices();list.add(0,"");
                for(String item:list){String title=item.isEmpty()?"系统默认录音设备":item;picker.row(key(title,()->{input[0]=item;devices.setText(title);picker.close();}));}picker.open();
            });
            s.row(label("语音模式",14,MUTED));JPanel modes=panel(new java.awt.GridLayout(1,3,4,0));s.row(modes);
            JButton bind=key("",()->{});Runnable draw=()->{bind.setText("快捷键   "+name(chosen[1],chosen[2]));bind.setEnabled(chosen[0]!=0);desc.setText("当前模式 · "+MODES[chosen[0]]);};
            for(int i=0;i<3;i++){final int m=i;modes.add(key(MODES[i],()->{chosen[0]=m;draw.run();}));}
            s.row(desc);s.row(bind);draw.run();
            bind.addActionListener(e->KeyBinding.choose(s.dialog,(key,mask)->{chosen[1]=key;chosen[2]=mask;draw.run();}));
            s.action("保存语音设置",()->{mode=chosen[0];vk=chosen[1];mods=chosen[2];device.selected=input[0];
                prefs.putInt("mode",mode);prefs.putInt("key",vk);prefs.putInt("mods",mods);prefs.put("device",device.selected);s.close();});
            s.open();
        }finally{suspended=false;if(resume)arm(speech);}
    }
}