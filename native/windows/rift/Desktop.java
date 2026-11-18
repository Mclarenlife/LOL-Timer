package rift;

import javax.swing.*;
import javax.swing.border.*;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

import static rift.Studio.*;

public final class Desktop implements Controller.Host {
    final JFrame frame=new JFrame("Rift Timer · 召唤师技能计时");
    private final Path dir=Paths.get(System.getenv("LOCALAPPDATA")==null?System.getProperty("user.home"):System.getenv("LOCALAPPDATA"),"RiftTimer");
    private final Controller ctl=new Controller(this);
    private final JButton[] pick=new JButton[10],start=new JButton[10],reset=new JButton[10];
    private final JLabel[] times=new JLabel[10];
    private final JProgressBar[] progress=new JProgressBar[10];
    private final JLabel status=label("",14,MINT), summary=label("",14,MUTED), message=label("",14,MINT);
    private final Map<String,Icon> icons=new HashMap<>();
    public String load(){try{return new String(Files.readAllBytes(dir.resolve("state.json")),StandardCharsets.UTF_8);}catch(IOException e){return "";}}
    public void store(String json){try{Files.createDirectories(dir);Path temp=dir.resolve("state.tmp");
        Files.write(temp,json.getBytes(StandardCharsets.UTF_8));Files.move(temp,dir.resolve("state.json"),StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);
    }catch(IOException e){notice("无法保存本机状态");}}
    public void post(Runnable r){SwingUtilities.invokeLater(()->{r.run();refresh();});}
    public void notice(String s){message.setText(s);}
    public void remind(String s,Model.Settings cfg){
        notice(s);if(cfg.sound)Toolkit.getDefaultToolkit().beep();
        if(cfg.voice){muteUntil=System.currentTimeMillis()+5500;
            String script="Add-Type -AssemblyName System.Speech; $v=New-Object System.Speech.Synthesis.SpeechSynthesizer; $v.Speak('"+s.replace("'","''")+"')";
            String encoded=Base64.getEncoder().encodeToString(script.getBytes(StandardCharsets.UTF_16LE));
            try{new ProcessBuilder("powershell.exe","-NoProfile","-NonInteractive","-WindowStyle","Hidden","-EncodedCommand",encoded).start();}catch(IOException ignored){}
        }
    }
    Icon icon(String key,int size){return icons.computeIfAbsent(key+size,k->new ImageIcon(new ImageIcon(getClass().getResource("/art/"+key+".png")).getImage().getScaledInstance(size,size,Image.SCALE_SMOOTH)));}
    private Overlay hud;
    private JButton mic;
    private Speech speech;
    final VoiceKeys voiceKeys=new VoiceKeys();
    boolean transmitting(){return speech!=null&&speech.active()&&voiceKeys.open&&voiceKeys.ready;}
    private long muteUntil;
    public void expired(int index,long deadline){if(hud!=null)hud.expired(index,deadline);}
    void open(){
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);frame.setIconImage(new ImageIcon(getClass().getResource("/art/SummonerFlash.png")).getImage());
        UIManager.put("Button.disabledText",MUTED.darker());
        JPanel root=panel(new BorderLayout(0,15));root.setOpaque(true);root.setBackground(BG);root.setBorder(new EmptyBorder(20,22,16,22));frame.setContentPane(root);
        JPanel top=panel(new BorderLayout(0,16));JPanel heading=panel(new BorderLayout());
        JLabel wordmark=label("RIFT  /  TIMER",24,INK);wordmark.setFont(font(24).deriveFont(Font.BOLD));heading.add(wordmark);
        JPanel tools=panel(new FlowLayout(FlowLayout.RIGHT,5,0));tools.add(key("对局码",this::room));tools.add(key("设置",this::settings));
        tools.add(key("悬浮窗",()->hud.show(!hud.window.isVisible())));tools.add(key("说明",this::help));heading.add(tools,BorderLayout.EAST);top.add(heading,BorderLayout.NORTH);
        JPanel console=card(new BorderLayout(12,0),RAISED,22);console.setBorder(new EmptyBorder(13,16,13,16));
        JPanel state=panel(new GridLayout(2,1));state.add(status);state.add(summary);console.add(state);
        mic=key(voiceKeys.startLabel(),MINT,BG,()->{if(speech.active())voiceKeys.stop(speech);else voiceKeys.arm(speech);});console.add(mic,BorderLayout.EAST);top.add(console);root.add(top,BorderLayout.NORTH);
        speech=new Speech(voiceKeys.device,SwingUtilities::invokeLater,s->{voiceKeys.ready="正在聆听".equals(s);mic.setText(speech.active()?"关闭语音":voiceKeys.startLabel());notice(s.isEmpty()?"语音已关闭":s+(speech.active()?"  ·  说「上单闪现」":""));},
            phrase->{if(System.currentTimeMillis()>=muteUntil){notice("听到  "+phrase.replace(" ",""));Commands.apply(ctl,phrase);refresh();}});
        hud=new Overlay(this,ctl);
        JPanel board=panel(new GridLayout(1,5,10,10));
        for(int r=0;r<5;r++){
            JPanel role=card(new BorderLayout(0,10),PANEL,20);role.setBorder(new EmptyBorder(13,9,12,9));
            JPanel cap=panel(new BorderLayout());JLabel lane=label(Model.ROLES[r],20,LANES[r]);lane.setIcon(icon(Model.ICONS[r],30));lane.setIconTextGap(8);cap.add(lane);final int roleIndex=r;cap.add(key("急速",()->haste(roleIndex)),BorderLayout.EAST);role.add(cap,BorderLayout.NORTH);
            JPanel pair=panel(new GridLayout(2,1,0,9));pair.add(slot(2*r));pair.add(slot(2*r+1));role.add(pair);board.add(role);
        }
        JScrollPane scroll=new JScrollPane(board);scroll.setBorder(null);scroll.getViewport().setBackground(BG);scroll.getVerticalScrollBar().setUnitIncrement(45);root.add(scroll);
        scroll.getViewport().addComponentListener(new ComponentAdapter(){public void componentResized(ComponentEvent e){
            int cols=Math.max(1,Math.min(5,scroll.getViewport().getWidth()/190));GridLayout grid=(GridLayout)board.getLayout();grid.setRows(0);grid.setColumns(cols);board.revalidate();}});
        JPanel foot=panel(new BorderLayout());foot.add(message);JPanel small=panel(new FlowLayout(FlowLayout.RIGHT,4,0));small.add(key("最近提醒",this::history));
        small.add(key("重置本局",()->confirm("重置全部计时？","保留技能选择及提醒设置",ctl::resetGame)));foot.add(small,BorderLayout.EAST);root.add(foot,BorderLayout.SOUTH);
        Dimension screen=Toolkit.getDefaultToolkit().getScreenSize();frame.setMinimumSize(new Dimension(600,430));frame.setSize(Math.min(1170,screen.width),Math.min(705,screen.height-48));frame.setLocationRelativeTo(null);refresh();frame.setVisible(true);
        new javax.swing.Timer(200,e->{ctl.tick();refresh();}).start();
    }
    JPanel slot(int i){
        JPanel box=card(new BorderLayout(0,8),BG,15);box.setBorder(new EmptyBorder(10,9,10,9));
        pick[i]=key("",()->choose(i));pick[i].setHorizontalAlignment(SwingConstants.LEFT);box.add(pick[i],BorderLayout.NORTH);
        JPanel center=panel(new GridBagLayout());times[i]=label("",30,INK);times[i].setFont(new Font(Font.MONOSPACED,Font.BOLD,30));center.add(times[i]);box.add(center);
        JPanel lower=panel(new BorderLayout(0,8)),actions=panel(new GridLayout(1,3,4,0));progress[i]=new JProgressBar();progress[i].setForeground(LANES[i/2]);progress[i].setBackground(LINE);progress[i].setBorderPainted(false);progress[i].setPreferredSize(new Dimension(60,3));lower.add(progress[i],BorderLayout.NORTH);
        start[i]=key("计时",MINT,BG,()->{ctl.edit(m->m.start(i,ctl.now()));refresh();});actions.add(start[i]);actions.add(key("扣秒",()->adjust(i)));reset[i]=key("重置",()->{ctl.edit(m->m.slots[i].reset());refresh();});actions.add(reset[i]);lower.add(actions);box.add(lower,BorderLayout.SOUTH);return box;
    }
    void adjust(int i){Sheet sheet=new Sheet(frame,Model.ROLES[i/2]+" · 扣减时间");JPanel choices=panel(new GridLayout(1,3,6,0));
        for(int seconds:new int[]{5,10,20})choices.add(key("−"+seconds+" 秒",()->{ctl.edit(m->m.deduct(i,ctl.now(),seconds));sheet.close();refresh();}));sheet.row(choices);sheet.open();}
    void choose(int i){Sheet sheet=new Sheet(frame,Model.ROLES[i/2]+" · 选择技能");JPanel grid=panel(new GridLayout(3,3,7,7));
        for(Model.Spell spell:Model.Spell.values()){boolean selected=ctl.state().slots[i].spellId==spell;
            JButton tile=key(spell.label+" "+spell.seconds+"s",selected?MINT:RAISED,selected?BG:INK,()->{ctl.choose(i,spell);sheet.close();refresh();});
            tile.setIcon(icon(spell.name(),34));tile.setVerticalTextPosition(SwingConstants.BOTTOM);tile.setHorizontalTextPosition(SwingConstants.CENTER);tile.setEnabled(spell!=ctl.state().slots[i^1].spellId);grid.add(tile);}
        sheet.row(grid);sheet.open();
    }
    void refresh(){
        int count=0,min=Integer.MAX_VALUE;String next="";
        for(int i=0;i<10;i++){
            Model.Slot s=ctl.state().slots[i];int left=s.left(ctl.now());
            pick[i].setText(s.spellId.label+"  ﹀");pick[i].setIcon(icon(s.spellId.name(),25));pick[i].setToolTipText(s.spellId==Model.Spell.SummonerSmite?"惩戒：两次施放间隔 15 秒":"更换技能");
            times[i].setText(Model.time(left>0?left:ctl.state().seconds(i)));times[i].setForeground(left>0?MINT:INK);
            start[i].setEnabled(left==0);reset[i].setEnabled(left>0);
            progress[i].setMaximum(ctl.state().seconds(i));progress[i].setValue(left>0?left:ctl.state().seconds(i));
            if(left>0){count++;if(left<min){min=left;next=Model.ROLES[i/2]+" "+s.spellId.label+" "+Model.time(left);}}
        }
        if(hud!=null&&hud.window.isVisible())hud.board.repaint();
        status.setText(ctl.status());summary.setText("冷却中 "+count+" / 10"+(count>0?"     下一个就绪  "+next:""));
    }
    void confirm(String title,String note,Runnable fn){Sheet s=new Sheet(frame,title);s.row(label(note,14,MUTED));s.action("确定",()->{fn.run();s.close();refresh();});s.open();}
    void room(){
        if(ctl.inRoom()){confirm("离开对局 "+ctl.code()+"？","离开后恢复自己的单人计时",ctl::leave);return;}
        Sheet s=new Sheet(frame,"对局码");JTextField code=field("");s.row(label("输入 6 至 32 位数字",14,MUTED));s.row(code);
        for(boolean create:new boolean[]{true,false})s.action(create?"创建对局":"加入对局",()->{
            String digits=code.getText().trim();if(!digits.matches("[0-9]{6,32}")){code.setBorder(new LineBorder(LANES[4],2));code.requestFocusInWindow();return;}ctl.enter(digits,create);s.close();});s.open();
    }
    void help(){
        Sheet sheet=new Sheet(frame,"使用说明");
        String[][] sections={
            {"记录技能","点击技能名称更换技能，点击「计时」开表。「扣秒」可提前 5、10 或 20 秒；空闲技能会直接开始计时。「重置」结束本次计时。"},
            {"装备与提醒","点击位置旁的「急速」选择鞋子与符文。设置里可配置记录时自动扣秒、提前提醒、提示音、中文播报及悬浮窗到期闪烁。"},
            {"对局同步","单人计时自动保存在本机。点击「对局码」创建或加入 6–32 位数字对局。相同对局的队友共享技能配置和倒计时。"},
            {"语音口令","开启语音后说「上单闪现」等位置与技能组合。设置 → 语音输入，可选择麦克风、随时说话、长按说话或按键切换模式。模式与快捷键选定后自动保存。先在主窗口开启语音，再使用绑定的按键。"},
            {"悬浮窗","未锁定时左键点技能开表，右键点技能扣秒或重置，点位置名称设置急速。拖动标题移动，拖动右下角缩放。点右上锁图标锁定，再点即可解锁。锁定后其余区域均可穿透。"}
        };
        JPanel notes=panel(new GridLayout(0,1,0,8));
        for(String[] part:sections){JPanel block=panel(new BorderLayout(0,3));block.add(label(part[0],15,MINT),BorderLayout.NORTH);
            JTextArea copy=new JTextArea(part[1],2,30);copy.setLineWrap(true);copy.setWrapStyleWord(true);copy.setEditable(false);copy.setOpaque(false);copy.setForeground(INK);copy.setFont(font(14));block.add(copy);notes.add(block);}
        sheet.row(notes);sheet.open();
    }
    void history(){Sheet s=new Sheet(frame,"最近提醒");JTextArea a=new JTextArea(ctl.alerts.isEmpty()?"暂无提醒":String.join("\n",ctl.alerts),6,23);
        a.setEditable(false);a.setBackground(BG);a.setForeground(INK);a.setFont(font(15));a.setBorder(new EmptyBorder(8,8,8,8));s.row(new JScrollPane(a));
        s.action("清空记录",()->{ctl.clearHistory();notice("");s.close();});s.open();}
    void haste(int role){haste(role,frame);}
    void haste(int role,Window owner){
        Sheet sheet=new Sheet(owner,Model.ROLES[role]+" · 召唤师技能急速");int mask=ctl.state().loadout[role];JCheckBox[] choices=new JCheckBox[4];
        for(int i=0;i<4;i++){choices[i]=toggle(Model.OPTIONS[i],(mask&(1<<i))!=0);sheet.row(choices[i]);}
        JLabel info=label("",14,MINT);sheet.row(info);java.util.function.IntSupplier selected=()->{int value=0;for(int i=0;i<4;i++)if(choices[i].isSelected())value|=1<<i;return value;};
        Runnable preview=()->{int m=selected.getAsInt();info.setText(String.format("总急速 %d · 闪现 %.2f 秒",Model.haste(m),Model.durationMs(Model.Spell.SummonerFlash,m)/1000.0));};
        for(int i=0;i<4;i++){final int k=i;choices[i].addActionListener(e->{if(k<2&&choices[k].isSelected())choices[1-k].setSelected(false);preview.run();});}
        preview.run();sheet.action("保存",()->{int updated=selected.getAsInt();ctl.edit(m->m.configure(role,updated,ctl.now()));sheet.close();refresh();});sheet.open();
    }
    void settings(){
        Sheet s=new Sheet(frame,"提醒与悬浮窗");Model.Settings current=ctl.state().settings;JTextField seconds=field(String.valueOf(current.lead));
        s.row(label("提前提醒时间  /  秒，0 为结束时提醒",14,MUTED));s.row(seconds);JTextField debit=field(String.valueOf(current.autoDeduct));JPanel debitRow=panel(new GridLayout(1,2));debitRow.add(label("记录时自动扣秒（0–300）",14,MUTED));debitRow.add(debit);s.row(debitRow);
        JPanel audio=panel(new GridLayout(1,3));JCheckBox sound=toggle("提示音",current.sound),voice=toggle("播报",current.voice);audio.add(sound);audio.add(voice);
        audio.add(key("试听",()->{Model.Settings cfg=new Model.Settings();cfg.sound=sound.isSelected();cfg.voice=voice.isSelected();remind("上单 · 闪现 已就绪",cfg);}));s.row(audio);
        JCheckBox pin=toggle("主窗口置顶",frame.isAlwaysOnTop()),visible=toggle("显示悬浮窗",hud.window.isVisible()),lock=toggle("锁定悬浮窗并穿透鼠标",hud.locked);
        JPanel display=panel(new GridLayout(1,2));display.add(pin);display.add(visible);s.row(display);JCheckBox blink=toggle("到期行闪烁 3 秒",hud.flash);JPanel hudOpts=panel(new GridLayout(1,2));hudOpts.add(lock);hudOpts.add(blink);s.row(hudOpts);
        s.row(key("语音输入 · 设备、模式与快捷键",()->{voiceKeys.settings(this,speech);if(!speech.active())mic.setText(voiceKeys.startLabel());}));
        JLabel percent=label("悬浮窗不透明度  "+hud.opacity+"%",14,MUTED);s.row(percent);
        JSlider alpha=new JSlider(25,100,hud.opacity);alpha.setOpaque(false);alpha.setForeground(MINT);alpha.addChangeListener(e->percent.setText("悬浮窗不透明度  "+alpha.getValue()+"%"));s.row(alpha);
        s.action("保存设置",()->{int n;try{n=Integer.parseInt(seconds.getText().trim());}catch(NumberFormatException e){n=-1;}if(n<0||n>600){seconds.setBorder(new LineBorder(LANES[4],2));return;}
            int off;try{off=Integer.parseInt(debit.getText().trim());}catch(NumberFormatException e){off=-1;}if(off<0||off>300){debit.setBorder(new LineBorder(LANES[4],2));return;}final int amount=off;final int lead=n;if(current.autoDeduct!=amount||current.lead!=lead||current.sound!=sound.isSelected()||current.voice!=voice.isSelected())ctl.edit(m->{m.settings.autoDeduct=amount;m.settings.lead=lead;m.settings.sound=sound.isSelected();m.settings.voice=voice.isSelected();});
            hud.flash=blink.isSelected();hud.prefs.putBoolean("flash",hud.flash);hud.settings(lock.isSelected(),alpha.getValue());hud.show(visible.isSelected());frame.setAlwaysOnTop(pin.isSelected());s.close();});s.open();
    }
    public static void main(String[] args){SwingUtilities.invokeLater(()->new Desktop().open());}
}
