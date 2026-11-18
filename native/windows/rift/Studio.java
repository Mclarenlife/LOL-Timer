package rift;

import java.awt.*;
import java.awt.event.*;
import javax.swing.*;
import javax.swing.border.*;

final class Studio {
    static final Color BG=new Color(12,17,27), PANEL=new Color(23,31,45), RAISED=new Color(33,45,62), LINE=new Color(55,70,90), INK=new Color(237,243,249),
        MUTED=new Color(165,179,197), MINT=new Color(107,237,194), VIOLET=new Color(186,167,255);
    static final Color[] LANES={MINT,new Color(246,188,108),VIOLET,new Color(133,200,255),new Color(246,156,179)};
    static Font font(int n){return new Font("Microsoft YaHei UI",Font.PLAIN,n);}
    static JPanel panel(LayoutManager lm){JPanel p=new JPanel(lm);p.setOpaque(false);return p;}
    static JPanel card(LayoutManager lm,Color color,int radius){
        JPanel p=new JPanel(lm){protected void paintComponent(Graphics graphics){Graphics2D g=(Graphics2D)graphics.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);g.setColor(color);g.fillRoundRect(0,0,getWidth(),getHeight(),radius,radius);g.dispose();super.paintComponent(graphics);}};
        p.setOpaque(false);return p;
    }
    static JLabel label(String text,int size,Color color){JLabel l=new JLabel(text);l.setFont(font(size));l.setForeground(color);return l;}
    static JButton key(String text,Color fill,Color ink,Runnable action){
        JButton b=new JButton(text){protected void paintComponent(Graphics original){
            Graphics2D g=(Graphics2D)original.create();g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
            g.setColor(!isEnabled()?PANEL:getModel().isPressed()?fill.darker():getModel().isRollover()?fill.brighter():fill);
            g.fillRoundRect(0,0,getWidth(),getHeight(),14,14);if(hasFocus()){g.setColor(MINT);g.drawRoundRect(1,1,getWidth()-3,getHeight()-3,14,14);}g.dispose();super.paintComponent(original);}};
        b.setOpaque(false);b.setContentAreaFilled(false);b.setBorderPainted(false);b.setFocusPainted(false);b.setForeground(ink);b.setFont(font(14));
        b.setBorder(new EmptyBorder(7,10,7,10));b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));b.addActionListener(e->action.run());return b;
    }
    static JButton key(String text,Runnable fn){return key(text,RAISED,INK,fn);}
    static class Sheet {
        final JDialog dialog;final JPanel body=panel(new GridLayout(0,1,0,6)),actions=panel(new FlowLayout(FlowLayout.RIGHT,6,0));
        Sheet(Window owner,String title){
            dialog=new JDialog(owner,title,Dialog.ModalityType.APPLICATION_MODAL);dialog.setAlwaysOnTop(owner.isAlwaysOnTop());dialog.setUndecorated(true);dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
            JPanel container=panel(new BorderLayout(0,16));container.setOpaque(true);container.setBackground(PANEL);
            container.setBorder(new CompoundBorder(new LineBorder(LINE),new EmptyBorder(20,20,18,20)));
            JPanel head=panel(new BorderLayout());JLabel heading=label(title,21,INK);head.add(heading);head.add(key("关闭",this::close),BorderLayout.EAST);
            container.add(head,BorderLayout.NORTH);JScrollPane scroll=new JScrollPane(body);scroll.setBorder(null);scroll.setOpaque(false);scroll.getViewport().setOpaque(false);scroll.getVerticalScrollBar().setUnitIncrement(26);
            container.add(scroll);container.add(actions,BorderLayout.SOUTH);dialog.setContentPane(container);
            dialog.getRootPane().registerKeyboardAction(e->close(),KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE,0),JComponent.WHEN_IN_FOCUSED_WINDOW);
        }
        void close(){dialog.dispose();}
        void row(JComponent c){body.add(c);}
        void action(String title,Runnable fn){actions.add(key(title,MINT,BG,fn));}
        void open(){dialog.pack();dialog.setSize(Math.max(350,dialog.getWidth()),Math.min(dialog.getHeight(),GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds().height-20));dialog.setLocationRelativeTo(dialog.getOwner());dialog.setVisible(true);}
    }
    static JTextField field(String v){JTextField f=new JTextField(v,18);f.setBackground(BG);f.setForeground(INK);f.setCaretColor(MINT);f.setFont(font(16));f.setBorder(new CompoundBorder(new LineBorder(LINE),new EmptyBorder(7,10,7,10)));return f;}
    static JCheckBox toggle(String name,boolean value){JCheckBox b=new JCheckBox(name,value);b.setOpaque(false);b.setForeground(INK);b.setFont(font(15));
        Icon glyph=new Icon(){public int getIconWidth(){return 36;}public int getIconHeight(){return 20;}
            public void paintIcon(Component c,Graphics g,int x,int y){Graphics2D p=(Graphics2D)g.create();p.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);p.setColor(b.isSelected()?MINT:LINE);p.fillRoundRect(x,y+1,33,18,18,18);p.setColor(b.isSelected()?BG:INK);p.fillOval(x+(b.isSelected()?17:3),y+3,14,14);p.dispose();}};
        b.setIcon(glyph);b.setSelectedIcon(glyph);return b;}
}