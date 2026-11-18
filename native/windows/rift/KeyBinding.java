package rift;

import com.sun.jna.Native;
import com.sun.jna.win32.StdCallLibrary;
import java.awt.*;
import java.awt.event.*;
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
    static void record(Window owner,BiConsumer<Integer,Integer> accept){
        Sheet sheet=new Sheet(owner,"录入说话快捷键");
        JLabel prompt=label("在此窗口按下键盘按键、组合键或鼠标侧键",16,INK);
        sheet.row(prompt);
        sheet.row(label("单独绑定 Ctrl、Alt、Shift 或 Win 时，按下后松开。",13,MUTED));
        RawBindingInput input=new RawBindingInput(sheet.dialog,(key,mods)->{
            accept.accept(key,mods);
            sheet.close();
        });
        sheet.dialog.addWindowFocusListener(new WindowAdapter(){
            public void windowGainedFocus(WindowEvent e){
                try{input.start();}catch(IllegalStateException ex){prompt.setText("无法打开按键录入，请关闭后重试");}
            }
            public void windowLostFocus(WindowEvent e){input.stop();}
        });
        // Keep Escape, Tab and Enter available to the recorder.
        KeyEventDispatcher keys=e->sheet.dialog.isActive();
        KeyboardFocusManager focus=KeyboardFocusManager.getCurrentKeyboardFocusManager();
        focus.addKeyEventDispatcher(keys);
        try{sheet.open();}finally{input.stop();focus.removeKeyEventDispatcher(keys);}
    }
}
