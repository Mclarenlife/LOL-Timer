package rift;

import com.sun.jna.Native;
import com.sun.jna.Library;
import java.awt.GridLayout;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;
import javax.swing.*;
import static rift.Studio.*;

final class VoiceKeys {
    interface Keys extends Library {
        Keys API=Native.load("user32",Keys.class);
        short GetAsyncKeyState(int key);
    }
    static final String[] MODES={"随时说话","长按按键说话","按键切换说话"};
    final DesktopMic device=new DesktopMic();
    private final Preferences prefs;
    boolean down,open,ready,suspended;
    private int mode,vk,mods;

    VoiceKeys(){this(Preferences.userNodeForPackage(VoiceKeys.class).node("voice"));}
    VoiceKeys(Preferences prefs){
        this.prefs=prefs;
        mode=Math.max(0,Math.min(2,prefs.getInt("mode",0)));
        vk=prefs.getInt("key",119);
        mods=prefs.getInt("mods",0);
        device.selected=prefs.get("device","");
    }
    String startLabel(){return "开启语音 · "+MODES[mode];}
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
    static String name(int key,int mods){
        return ((mods&1)!=0?"Ctrl + ":"")+((mods&2)!=0?"Alt + ":"")+((mods&4)!=0?"Shift + ":"")+
            ((mods&8)!=0?"Win + ":"")+KeyBinding.name(key);
    }
    private void save(int selectedMode,int key,int mask,String input)throws BackingStoreException{
        prefs.putInt("mode",selectedMode);prefs.putInt("key",key);prefs.putInt("mods",mask);prefs.put("device",input);
        prefs.flush();
        mode=selectedMode;vk=key;mods=mask;device.selected=input;
    }
    private boolean apply(int selectedMode,int key,int mask,String input,JLabel status){
        try{save(selectedMode,key,mask,input);status.setText("已保存 · "+MODES[mode]);return true;}
        catch(BackingStoreException|SecurityException e){status.setText("保存失败，请检查当前 Windows 用户的设置权限后重试");return false;}
    }
    void settings(Desktop desktop,Speech speech){
        boolean resume=speech.active();
        suspended=true;if(resume)speech.stop();
        try{
            Sheet sheet=new Sheet(desktop.frame,"语音输入");
            JLabel status=label("当前模式 · "+MODES[mode],14,MUTED);
            sheet.row(label("录音设备",14,MUTED));
            JButton devices=key(device.selected.isEmpty()?"系统默认录音设备":device.selected,()->{});
            sheet.row(devices);
            devices.addActionListener(e->{
                Sheet picker=new Sheet(sheet.dialog,"选择录音设备");
                java.util.List<String> list=DesktopMic.devices();list.add(0,"");
                for(String item:list){
                    String title=item.isEmpty()?"系统默认录音设备":item;
                    picker.row(key(title,()->{if(apply(mode,vk,mods,item,status))devices.setText(title);picker.close();}));
                }
                picker.open();
            });
            sheet.row(label("语音模式",14,MUTED));
            JPanel modes=panel(new GridLayout(1,3,4,0));sheet.row(modes);
            JButton[] choices=new JButton[MODES.length];
            JButton bind=key("",()->{});
            Runnable draw=()->{
                for(int i=0;i<choices.length;i++){
                    choices[i].setForeground(i==mode?MINT:INK);
                    choices[i].setText((i==mode?"✓  ":"")+MODES[i]);
                }
                bind.setText("录入快捷键   "+name(vk,mods));bind.setEnabled(mode!=0);
            };
            for(int i=0;i<choices.length;i++){
                final int selected=i;
                choices[i]=key(MODES[i],()->{apply(selected,vk,mods,device.selected,status);draw.run();});
                modes.add(choices[i]);
            }
            bind.addActionListener(e->KeyBinding.record(sheet.dialog,(key,mask)->{apply(mode,key,mask,device.selected,status);draw.run();}));
            sheet.row(status);sheet.row(bind);draw.run();
            sheet.action("完成",sheet::close);sheet.open();
        }finally{suspended=false;if(resume)arm(speech);}
    }
}
