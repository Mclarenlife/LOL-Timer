package rift;

import com.sun.jna.Memory;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.WString;
import com.sun.jna.ptr.IntByReference;
import com.sun.jna.win32.StdCallLibrary;
import java.awt.*;
import java.util.Arrays;
import java.util.function.BiConsumer;
import javax.swing.*;

/** Foreground-only Windows Raw Input, owned by the shortcut dialog's EDT. */
final class RawBindingInput {
    interface User32 extends StdCallLibrary {
        User32 API=Native.load("user32",User32.class);
        Pointer CreateWindowExW(int ex,WString cls,WString title,int style,int x,int y,int w,int h,Pointer parent,Pointer menu,Pointer instance,Pointer param);
        boolean RegisterRawInputDevices(Pointer devices,int count,int size);
        boolean PeekMessageW(Pointer msg,Pointer window,int first,int last,int remove);
        Pointer DispatchMessageW(Pointer msg);
        int GetRawInputData(Pointer raw,int command,Pointer data,IntByReference size,int header);
        boolean DestroyWindow(Pointer window);
    }
    private static final int WM_INPUT=0xff, RID_INPUT=0x10000003, RIDEV_REMOVE=1;
    private static final int PTR=Native.POINTER_SIZE, DEVICE_SIZE=8+PTR, HEADER_SIZE=8+2*PTR;
    private final Window dialog;
    private final BiConsumer<Integer,Integer> accept;
    private final Memory devices=new Memory(2*DEVICE_SIZE),message=new Memory(64),packet=new Memory(128);
    private final IntByReference size=new IntByReference();
    private final boolean[] held=new boolean[256];
    private final Timer pump=new Timer(8,e->read());
    private Pointer window;
    private int pendingModifier;
    RawBindingInput(Window dialog,BiConsumer<Integer,Integer> accept){this.dialog=dialog;this.accept=accept;}

    void start(){
        if(window!=null)return;
        for(int vk=1;vk<held.length;vk++)held[vk]=VoiceKeys.pressed(vk);
        pendingModifier=0;
        window=User32.API.CreateWindowExW(0,new WString("STATIC"),null,0,0,0,0,0,Pointer.createConstant(-3L),null,null,null);
        if(window==null)throw new IllegalStateException("CreateWindowExW");
        if(!register(0,window)){
            User32.API.DestroyWindow(window);window=null;
            throw new IllegalStateException("RegisterRawInputDevices");
        }
        pump.start();
    }
    private boolean register(int flags,Pointer target){
        for(int i=0;i<2;i++){
            int offset=i*DEVICE_SIZE;
            devices.setShort(offset,(short)1);                // Generic desktop
            devices.setShort(offset+2,(short)(i==0?2:6));    // Mouse, keyboard
            devices.setInt(offset+4,flags);
            devices.setPointer(offset+8,target);
        }
        return User32.API.RegisterRawInputDevices(devices,2,DEVICE_SIZE);
    }
    void stop(){
        pump.stop();
        if(window!=null){
            register(RIDEV_REMOVE,null);
            User32.API.DestroyWindow(window);
            window=null;
        }
        pendingModifier=0;
        Arrays.fill(held,false);
    }
    private void read(){
        if(window==null||!dialog.isActive())return;
        while(window!=null&&User32.API.PeekMessageW(message,window,WM_INPUT,WM_INPUT,1)){
            // MSG.lParam follows hwnd, message (padded on x64), and wParam.
            Pointer handle=message.getPointer(3*PTR);
            size.setValue((int)packet.size());
            int bytes=User32.API.GetRawInputData(handle,RID_INPUT,packet,size,HEADER_SIZE);
            User32.API.DispatchMessageW(message);
            if(bytes<HEADER_SIZE+16)continue;
            if(packet.getInt(0)==1)keyboard();
            else if(packet.getInt(0)==0)mouse();
        }
    }
    private void keyboard(){
        int vk=packet.getShort(HEADER_SIZE+6)&0xffff,flags=packet.getShort(HEADER_SIZE+2)&0xffff;
        if(vk==0||vk>=255)return;
        if(vk==16)vk=(packet.getShort(HEADER_SIZE)&255)==0x36?161:160;
        if(vk==17)vk=(flags&2)!=0?163:162;
        if(vk==18)vk=(flags&2)!=0?165:164;
        boolean released=(flags&1)!=0,wasHeld=held[vk];
        held[vk]=!released;
        if(released){
            if(pendingModifier==vk)finish(vk);
        }else if(!wasHeld){
            if((vk>=160&&vk<=165)||vk==91||vk==92)pendingModifier=vk;
            else finish(vk);
        }
    }
    private void mouse(){
        int flags=packet.getShort(HEADER_SIZE+4)&0xffff;
        if((flags&0x40)!=0)finish(5);           // RI_MOUSE_BUTTON_4_DOWN (X1)
        else if((flags&0x100)!=0)finish(6);     // RI_MOUSE_BUTTON_5_DOWN (X2)
        else if((flags&0x10)!=0)finish(4);
        else if((flags&4)!=0)finish(2);
        else if((flags&1)!=0&&onRecordingSurface())finish(1);
    }
    private boolean onRecordingSurface(){
        PointerInfo pointer=MouseInfo.getPointerInfo();
        if(pointer==null)return false;
        Point p=pointer.getLocation();SwingUtilities.convertPointFromScreen(p,dialog);
        Component target=SwingUtilities.getDeepestComponentAt(dialog,p.x,p.y);
        return target!=null&&!(target instanceof AbstractButton)
            &&SwingUtilities.getAncestorOfClass(AbstractButton.class,target)==null;
    }
    private void finish(int vk){
        int mods=(held[162]||held[163]?1:0)|(held[164]||held[165]?2:0)
            |(held[160]||held[161]?4:0)|(held[91]||held[92]?8:0);
        stop();
        accept.accept(vk,mods);
    }
}
