package rift;

import com.sun.jna.*;
import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.util.prefs.Preferences;
import static rift.Studio.*;

final class Overlay {
    interface OwnWindow extends Library {
        OwnWindow API=Native.load("user32",OwnWindow.class);
        int GetWindowLongW(Pointer hwnd,int index);
        int SetWindowLongW(Pointer hwnd,int index,int flags);
    }
    final Preferences prefs=Preferences.userNodeForPackage(Overlay.class).node("hud");
    final JFrame window=new JFrame("Rift Timer · 悬浮窗");
    final JWindow lockTarget=new JWindow(window);
    final Desktop desktop;final Board board=new Board();final Controller ctl;
    boolean locked,flash;int opacity;final long[] flashingUntil=new long[5];
    void expired(int i,long deadline){if(flash&&window.isVisible())flashingUntil[i/2]=deadline+3000;}
    Overlay(Desktop desktop,Controller ctl){
        this.desktop=desktop;this.ctl=ctl;flash=prefs.getBoolean("flash",false);locked=prefs.getBoolean("locked",false);opacity=Math.max(25,Math.min(100,prefs.getInt("opacity",92)));
        window.setUndecorated(true);window.setFocusableWindowState(false);window.setAutoRequestFocus(false);window.setAlwaysOnTop(true);window.setType(Window.Type.UTILITY);window.setContentPane(board);
        window.setBounds(prefs.getInt("x",60),prefs.getInt("y",100),Math.max(154,prefs.getInt("compactWidth",166)),Math.max(120,prefs.getInt("compactHeight",138)));
        boolean reachable=false;for(GraphicsDevice d:GraphicsEnvironment.getLocalGraphicsEnvironment().getScreenDevices())if(d.getDefaultConfiguration().getBounds().intersects(window.getBounds()))reachable=true;
        if(!reachable)window.setLocationRelativeTo(desktop.frame);
        lockTarget.setType(Window.Type.UTILITY);lockTarget.setFocusableWindowState(false);lockTarget.setAutoRequestFocus(false);lockTarget.setAlwaysOnTop(true);
        JComponent lockButton=new JComponent(){protected void paintComponent(Graphics g){paintLock(g,getWidth(),getHeight());}};
        lockButton.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));lockButton.setToolTipText("解锁悬浮窗");
        lockButton.addMouseListener(new MouseAdapter(){public void mouseClicked(MouseEvent e){if(SwingUtilities.isLeftMouseButton(e))settings(false,opacity);}});
        lockTarget.setContentPane(lockButton);
        window.addComponentListener(new ComponentAdapter(){public void componentMoved(ComponentEvent e){positionLock();}public void componentResized(ComponentEvent e){positionLock();}});
        MouseAdapter mouse=new MouseAdapter(){
            Point anchor;Rectangle original;boolean sizing;
            boolean dragged;
            public void mousePressed(MouseEvent e){
                anchor=null;dragged=false;if(locked||!SwingUtilities.isLeftMouseButton(e))return;
                sizing=grip(e.getX(),e.getY());if(sizing||(e.getY()<board.top()&&!board.lockArea().contains(e.getPoint()))){anchor=e.getLocationOnScreen();original=window.getBounds();}
            }
            public void mouseDragged(MouseEvent e){if(locked||anchor==null)return;int dx=e.getXOnScreen()-anchor.x,dy=e.getYOnScreen()-anchor.y;
                if(Math.abs(dx)+Math.abs(dy)>2)dragged=true;
                if(sizing)window.setSize(Math.max(154,original.width+dx),Math.max(120,original.height+dy));else window.setLocation(original.x+dx,original.y+dy);}
            public void mouseReleased(MouseEvent e){if(anchor!=null){if(dragged)saveBounds();anchor=null;}}
            public void mouseClicked(MouseEvent e){
                if(locked||dragged||grip(e.getX(),e.getY()))return;
                if(board.lockArea().contains(e.getPoint())){if(SwingUtilities.isLeftMouseButton(e))settings(true,opacity);return;}
                int role=board.roleAt(e.getY());if(role<0)return;
                if(e.getX()<board.laneWidth()){if(SwingUtilities.isLeftMouseButton(e))desktop.haste(role,window);return;}
                int index=role*2+board.column(e.getX());
                if(SwingUtilities.isRightMouseButton(e)){actions(index,e.getPoint());return;}
                if(SwingUtilities.isLeftMouseButton(e)){ctl.edit(m->m.start(index,ctl.now()));desktop.refresh();}
            }
            public void mouseMoved(MouseEvent e){int cursor=grip(e.getX(),e.getY())?Cursor.SE_RESIZE_CURSOR:
                e.getY()<board.top()&&!board.lockArea().contains(e.getPoint())?Cursor.MOVE_CURSOR:Cursor.HAND_CURSOR;
                board.setCursor(Cursor.getPredefinedCursor(cursor));}
        };board.addMouseListener(mouse);board.addMouseMotionListener(mouse);
    }
    boolean grip(int x,int y){return x>=board.getWidth()-9&&y>=board.getHeight()-9;}
    void actions(int index,Point pt){
        JPopupMenu pop=new JPopupMenu();pop.setLightWeightPopupEnabled(false);pop.setBackground(PANEL);pop.setBorder(BorderFactory.createLineBorder(LINE));pop.setLayout(new GridLayout(0,1,0,2));
        for(int seconds:new int[]{5,10,20})pop.add(key("扣 "+seconds+" 秒",()->{pop.setVisible(false);ctl.edit(m->m.deduct(index,ctl.now(),seconds));desktop.refresh();}));
        pop.add(key("重置计时",()->{pop.setVisible(false);ctl.edit(m->m.slots[index].reset());desktop.refresh();}));
        pop.show(board,pt.x,pt.y);
    }
    void paintLock(Graphics raw,int w,int h){Graphics2D g=(Graphics2D)raw.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);g.setColor(BG);g.fillRect(0,0,w,h);
        g.translate((w-14)/2,(h-14)/2);g.setColor(locked?MINT:MUTED);g.setStroke(new BasicStroke(1.5f));
        g.drawArc(locked?4:7,1,6,8,0,180);g.drawLine(locked?4:7,5,locked?4:7,7);if(locked)g.drawLine(10,5,10,7);
        g.drawRoundRect(2,7,10,6,2,2);g.drawLine(7,9,7,11);g.dispose();}
    void positionLock(){if(!window.isShowing())return;Rectangle r=board.lockArea();Point p=board.getLocationOnScreen();
        lockTarget.setBounds(p.x+r.x,p.y+r.y,r.width,r.height);}
    void saveBounds(){Rectangle b=window.getBounds();prefs.putInt("x",b.x);prefs.putInt("y",b.y);prefs.putInt("compactWidth",b.width);prefs.putInt("compactHeight",b.height);}
    void show(boolean show){if(!show)lockTarget.setVisible(false);window.setVisible(show);if(show)apply();}
    void settings(boolean lock,int alpha){locked=lock;opacity=alpha;prefs.putBoolean("locked",lock);prefs.putInt("opacity",alpha);if(window.isVisible())apply();}
    void apply(){
        try{
            window.setOpacity(opacity/100f);
            // Only this process's own Java window handle is accessed.
            Pointer hwnd=Native.getWindowPointer(window);int flags=OwnWindow.API.GetWindowLongW(hwnd,-20);
            int desired=locked?(flags|0x80000|0x20|0x08000000):(flags&~0x20);
            OwnWindow.API.SetWindowLongW(hwnd,-20,desired);positionLock();lockTarget.setOpacity(opacity/100f);lockTarget.setVisible(locked);if(locked){lockTarget.toFront();lockTarget.repaint();}board.repaint();
        }catch(UnsupportedOperationException|LinkageError e){desktop.notice("当前桌面未能应用悬浮窗透明或鼠标穿透设置");}
    }
    class Board extends JPanel {
        double scale(){return Math.min(getWidth()/166.0,getHeight()/138.0);}
        int top(){return (int)(16*scale());}
        int laneWidth(){return (int)(30*scale());}
        int column(int x){return x-laneWidth()>=(getWidth()-laneWidth())/2?1:0;}
        int roleAt(int y){int row=Math.max(1,(getHeight()-top()-2)/5);return y<top()||y>=top()+5*row?-1:(y-top())/row;}
        Rectangle lockArea(){int side=Math.max(15,top());return new Rectangle(getWidth()-side,0,side,top());}
        protected void paintComponent(Graphics raw){
            Graphics2D g=(Graphics2D)raw.create();g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
            int w=getWidth(),h=getHeight();g.setColor(BG);g.fillRect(0,0,w,h);
            long now=ctl.now();double scale=Math.min(w/166.0,h/138.0);
            int top=(int)(16*scale),row=Math.max(1,(h-top-2)/5),size=Math.max(9,(int)(12*scale));
            g.setFont(font(Math.max(10,(int)(11*scale))));g.setColor(MUTED);g.drawString("技能计时",3,top-3);
            if(desktop.transmitting()){g.setColor(MINT);g.drawString("语音交流中",6+g.getFontMetrics().stringWidth("技能计时"),top-3);}
            Rectangle badge=lockArea();Graphics badgeGraphics=g.create(badge.x,badge.y,badge.width,badge.height);paintLock(badgeGraphics,badge.width,badge.height);badgeGraphics.dispose();
            int left=laneWidth(),cell=(w-left)/2;
            for(int r=0;r<5;r++){int y=top+r*row;
                boolean lit=flash&&now<flashingUntil[r]&&((flashingUntil[r]-now)/200)%2==0;
                g.setColor(lit?new Color(59,83,87):r%2==0?PANEL:BG);g.fillRect(0,y,w,row);
                g.setColor(LANES[r]);g.fillRect(0,y+2,2,row-4);g.setFont(font(size));g.drawString(Model.ROLES[r],3,y+(row+size)/2-1);
                for(int j=0;j<2;j++){int index=2*r+j;Model.Slot slot=ctl.state().slots[index];int sec=slot.left(now),x=left+j*cell;
                    int iconSize=Math.min(row-3,(int)(20*scale));desktop.icon(slot.spellId.name(),iconSize).paintIcon(this,g,x,y+(row-iconSize)/2);
                    int tx=x+iconSize+2;g.setColor(MUTED);g.setFont(font(Math.max(9,(int)(10*scale))));g.drawString(slot.spellId.label,tx,y+row/2-1);
                    g.setColor(sec>0?MINT:INK);g.setFont(new Font(Font.MONOSPACED,Font.BOLD,size));g.drawString(Model.time(sec>0?sec:ctl.state().seconds(index)),tx,y+row/2+size-1);
                }
            }if(!locked){g.setColor(MUTED);g.drawLine(w-7,h-2,w-2,h-7);}g.dispose();
        }
    }
}