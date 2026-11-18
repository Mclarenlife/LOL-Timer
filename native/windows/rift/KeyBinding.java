package rift;

import com.sun.jna.Native;
import com.sun.jna.win32.StdCallLibrary;
import java.awt.*;
import java.util.function.BiConsumer;
import javax.swing.*;
import static rift.Studio.*;

final class KeyBinding {
    interface Layout extends StdCallLibrary {
        Layout API=Native.load("user32",Layout.class);
        int MapVirtualKeyW(int key,int mode);
        int GetKeyNameTextW(int scan,char[] text,int length);
    }
    static String name(int vk){
        switch(vk){
            case 1:return "鼠标左键"; case 2:return "鼠标右键"; case 4:return "鼠标中键";
            case 5:return "鼠标侧键 1 (X1)"; case 6:return "鼠标侧键 2 (X2)";
            case 160:return "左 Shift"; case 161:return "右 Shift"; case 162:return "左 Ctrl"; case 163:return "右 Ctrl";
            case 164:return "左 Alt"; case 165:return "右 Alt"; case 91:return "左 Win"; case 92:return "右 Win";
        }
        if(vk>=112&&vk<=135)return "F"+(vk-111);
        if(vk>=96&&vk<=105)return "小键盘 "+(vk-96);
        int scan=Layout.API.MapVirtualKeyW(vk,4);
        char[] text=new char[100];
        if(scan!=0 && Layout.API.GetKeyNameTextW(((scan&255)<<16)|((scan&0xff00)!=0?1<<24:0),text,text.length)>0)return Native.toString(text);
        return String.format("VK 0x%02X",vk);
    }
    static boolean modifier(int k){return k>=160&&k<=165||k==91||k==92;}
    static int mask(){return (VoiceKeys.pressed(17)?1:0)|(VoiceKeys.pressed(18)?2:0)|(VoiceKeys.pressed(16)?4:0)|(VoiceKeys.pressed(91)||VoiceKeys.pressed(92)?8:0);}
    static void choose(Window owner,BiConsumer<Integer,Integer> accept){
        Sheet sheet=new Sheet(owner,"设置说话快捷键");
        JLabel hint=label("可录入单键、组合键或鼠标侧键。",14,MUTED);sheet.row(hint);
        boolean[] armed={false},previous=new boolean[255];int[] modifierKey={0};
        sheet.row(key("开始录入",()->{
            for(int i=1;i<255;i++)previous[i]=VoiceKeys.pressed(i);
            modifierKey[0]=0;armed[0]=true;hint.setText("请按快捷键，单独使用修饰键请按下后松开。");
        }));
        BiConsumer<Integer,Integer> finish=(key,mods)->{armed[0]=false;accept.accept(key,mods);sheet.close();};
        for(int side=5;side<=6;side++){final int k=side;sheet.row(key(name(k),()->finish.accept(k,0)));}
        sheet.row(key("从完整键位表选择",()->{
            armed[0]=false;
            Sheet list=new Sheet(sheet.dialog,"Windows 键位表");JPanel flags=panel(new GridLayout(1,4));list.row(flags);
            JCheckBox[] masks=new JCheckBox[4];String[] titles={"Ctrl","Alt","Shift","Win"};
            for(int i=0;i<4;i++){masks[i]=toggle(titles[i],false);flags.add(masks[i]);}
            for(int i=1;i<=254;i++){final int k=i;list.row(key(String.format("%02X    %s",k,name(k)),()->{
                int mods=0;for(int j=0;j<4;j++)if(masks[j].isSelected())mods|=1<<j;
                list.close();finish.accept(k,mods);
            }));}list.open();
        }));
        Timer poll=new Timer(12,e->{
            if(!armed[0]||!sheet.dialog.isActive())return;
            for(int k=1;k<=254;k++){
                if(k>=16&&k<=18)continue;
                boolean hit=VoiceKeys.pressed(k),old=previous[k];previous[k]=hit;
                if(hit&&!old){
                    if(modifier(k)){modifierKey[0]=k;continue;}
                    finish.accept(k,mask());return;
                }
                if(!hit&&old&&modifierKey[0]==k){finish.accept(k,mask());return;}
            }
        });
        KeyEventDispatcher dispatcher=e->armed[0]&&sheet.dialog.isActive();
        KeyboardFocusManager focus=KeyboardFocusManager.getCurrentKeyboardFocusManager();focus.addKeyEventDispatcher(dispatcher);
        try{poll.start();sheet.open();}finally{poll.stop();focus.removeKeyEventDispatcher(dispatcher);}
    }
}
