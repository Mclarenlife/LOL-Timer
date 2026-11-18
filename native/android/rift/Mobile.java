package rift;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.*;
import android.graphics.drawable.*;
import android.os.*;
import android.text.InputType;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.util.*;

public final class Mobile extends Activity {
    private static final int BG=0xff0c111b,PANEL=0xff171f2d,LINE=0xff253349,FG=0xffedf3f9,GOLD=0xff6bedc2,MUTED=0xffa5b3c5;
    private MobileApp app; private Controller ctl;
    private final Button[] picks=new Button[10],starts=new Button[10],resets=new Button[10];
    private final TextView[] values=new TextView[10];
    private final ProgressBar[] bars=new ProgressBar[10];
    private final Model.Spell[] shown=new Model.Spell[10];
    private final Map<String,Bitmap> art=new HashMap<>();
    private final LinearLayout[] editors=new LinearLayout[5];private final TextView[] laneTimes=new TextView[10];private final TextView[] expanders=new TextView[5];private int selectedRole=0;
    private TextView status,summary,voiceStatus; private Button microphone;private Speech speech;long muteUntil;
    private final int[] laneColors={GOLD,0xfff6bc6c,0xffbaa7ff,0xff85c8ff,0xfff69cb3};
    private int dp(int n){return Math.round(getResources().getDisplayMetrics().density*n);}
    private LinearLayout layout(int dir){LinearLayout v=new LinearLayout(this);v.setOrientation(dir);return v;}
    private void pad(View v,int l,int t,int r,int b){v.setPadding(dp(l),dp(t),dp(r),dp(b));}
    private GradientDrawable shape(int color,int outline){GradientDrawable g=new GradientDrawable();g.setColor(color);g.setCornerRadius(dp(15));if(outline!=0)g.setStroke(dp(1),outline);return g;}
    private TextView text(String title,int sp,int color){TextView t=new TextView(this);t.setText(title);t.setTextSize(sp);t.setTextColor(color);return t;}
    private void change(TextView t,String title){if(!title.contentEquals(t.getText()))t.setText(title);}
    private Button button(String title,Runnable action){
        Button b=new Button(this);b.setAllCaps(false);b.setSingleLine(true);b.setTextSize(14);b.setTextColor(FG);b.setMinWidth(0);b.setMinimumWidth(0);b.setMinimumHeight(0);b.setMinHeight(0);
        b.setBackground(new RippleDrawable(android.content.res.ColorStateList.valueOf(0x447e947e),shape(LINE,0),null));pad(b,4,0,4,0);b.setText(title);b.setOnClickListener(v->action.run());return b;
    }
    private void flex(LinearLayout row,View child,int height){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,height<0?height:dp(height),1);p.setMargins(dp(2),dp(2),dp(2),dp(2));row.addView(child,p);}
    private BitmapDrawable icon(String name,int size){
        try{if(!art.containsKey(name))try(InputStream in=getAssets().open(name+".png")){art.put(name,BitmapFactory.decodeStream(in));}
            BitmapDrawable d=new BitmapDrawable(getResources(),art.get(name));d.setBounds(0,0,dp(size),dp(size));return d;
        }catch(IOException e){return null;}
    }
    protected void onCreate(Bundle state){
        super.onCreate(state);if(state!=null)selectedRole=state.getInt("selectedRole",0);app=(MobileApp)getApplication();ctl=app.ctl;
        LinearLayout page=layout(1);page.setFitsSystemWindows(true);page.setBackgroundColor(BG);setContentView(page);
        LinearLayout header=layout(1);pad(header,14,14,14,6);page.addView(header);
        LinearLayout titleRow=layout(0);titleRow.setGravity(Gravity.CENTER_VERTICAL);header.addView(titleRow);
        TextView title=text("RIFT  /  TIMER",24,FG);title.setTypeface(Typeface.DEFAULT,Typeface.BOLD);titleRow.addView(title);
        status=text("",14,MUTED);status.setGravity(Gravity.END);flex(titleRow,status,-2);
        LinearLayout nav=layout(0);header.addView(nav);flex(nav,button("对局码",this::room),46);flex(nav,button("提醒设置",this::settings),46);flex(nav,button("更多",this::more),46);
        LinearLayout voiceRow=layout(0);voiceRow.setGravity(Gravity.CENTER_VERTICAL);voiceRow.setBackground(shape(PANEL,0));pad(voiceRow,10,3,4,3);header.addView(voiceRow);
        voiceStatus=text("说「上单闪现」开始计时",13,MUTED);flex(voiceRow,voiceStatus,-2);microphone=button("开启语音",this::voiceToggle);accent(microphone);voiceRow.addView(microphone,dp(93),dp(42));
        speech=new Speech(new AndroidMic(this),this::runOnUiThread,msg->{voiceStatus.setText(msg.isEmpty()?"说「上单闪现」开始计时":msg);microphone.setText(speech.active()?"关闭语音":"开启语音");},
            phrase->{if(System.currentTimeMillis()>=muteUntil){voiceStatus.setText("听到  "+phrase.replace(" ",""));Commands.apply(ctl,phrase);refresh();}});
        summary=text("",14,MUTED);pad(summary,2,5,0,2);header.addView(summary);
        ScrollView scroll=new ScrollView(this);page.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        LinearLayout board=layout(1);pad(board,10,0,10,10);scroll.addView(board);
        for(int role=0;role<5;role++){
            LinearLayout group=layout(1);group.setBackground(shape(PANEL,0));pad(group,7,8,7,6);
            LinearLayout.LayoutParams gp=new LinearLayout.LayoutParams(-1,-2);gp.setMargins(0,dp(6),0,dp(4));board.addView(group,gp);
            final int index=role;LinearLayout strip=layout(0);strip.setGravity(Gravity.CENTER_VERTICAL);strip.setMinimumHeight(dp(45));group.addView(strip);
            TextView name=text(Model.ROLES[role],17,laneColors[role]);name.setGravity(Gravity.CENTER_VERTICAL);name.setCompoundDrawables(icon(Model.ICONS[role],26),null,null,null);name.setCompoundDrawablePadding(dp(8));pad(name,3,0,0,0);strip.addView(name);Button haste=button("急速",()->haste(index));haste.setTextSize(12);strip.addView(haste,dp(39),dp(36));
            for(int i=role*2;i<role*2+2;i++){laneTimes[i]=text("",14,FG);laneTimes[i].setGravity(Gravity.CENTER_VERTICAL|Gravity.CENTER_HORIZONTAL);laneTimes[i].setTypeface(Typeface.MONOSPACE,Typeface.BOLD);flex(strip,laneTimes[i],40);}
            expanders[role]=text("",15,MUTED);strip.addView(expanders[role]);strip.setContentDescription(Model.ROLES[role]+"技能设置");strip.setOnClickListener(v->selectRole(selectedRole==index?-1:index));
            LinearLayout pair=editors[role]=layout(0);group.addView(pair);for(int i=role*2;i<role*2+2;i++)flex(pair,slot(i),-2);
        }selectRole(selectedRole);refresh();
    }
    private void selectRole(int role){selectedRole=role;for(int r=0;r<5;r++){editors[r].setVisibility(r==role?View.VISIBLE:View.GONE);expanders[r].setText(r==role?"收起":"展开");}}
    protected void onSaveInstanceState(Bundle out){super.onSaveInstanceState(out);out.putInt("selectedRole",selectedRole);}
    private View slot(int i){
        LinearLayout box=layout(1);box.setBackground(shape(BG,0));pad(box,4,5,4,4);picks[i]=button("",()->choose(i));picks[i].setCompoundDrawablePadding(dp(3));pad(picks[i],6,0,4,0);box.addView(picks[i],-1,dp(44));
        LinearLayout clock=layout(0);clock.setGravity(Gravity.CENTER_VERTICAL);box.addView(clock);
        values[i]=text("",26,FG);values[i].setTypeface(Typeface.MONOSPACE,Typeface.BOLD);values[i].setGravity(Gravity.CENTER);clock.addView(values[i],new LinearLayout.LayoutParams(0,dp(46),1));
        Button adjust=button("扣秒",()->adjust(i));adjust.setContentDescription("扣减剩余时间");clock.addView(adjust,dp(44),dp(44));
        bars[i]=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);bars[i].setProgressTintList(android.content.res.ColorStateList.valueOf(GOLD));box.addView(bars[i],-1,dp(4));
        LinearLayout actions=layout(0);box.addView(actions);
        starts[i]=button("计时",()->{ctl.edit(m->m.start(i,ctl.now()));refresh();});resets[i]=button("重置",()->{ctl.edit(m->m.slots[i].reset());refresh();});accent(starts[i]);flex(actions,starts[i],44);flex(actions,resets[i],44);return box;
    }
    protected void onResume(){super.onResume();app.screen=this;app.schedule();}
    protected void onPause(){if(speech!=null&&speech.active())speech.stop();if(app.screen==this)app.screen=null;app.schedule();super.onPause();}
    void refresh(){
        int count=0,min=Integer.MAX_VALUE;String next="";long now=ctl.now();
        for(int i=0;i<10;i++){Model.Slot s=ctl.state().slots[i];int left=s.left(now);
            if(shown[i]!=s.spellId){shown[i]=s.spellId;laneTimes[i].setCompoundDrawables(icon(s.spellId.name(),19),null,null,null);picks[i].setText(s.spellId.label+"  ﹀");picks[i].setCompoundDrawables(icon(s.spellId.name(),26),null,null,null);}
            change(laneTimes[i],Model.time(left>0?left:ctl.state().seconds(i)));laneTimes[i].setTextColor(left>0?GOLD:MUTED);laneTimes[i].setContentDescription(s.spellId.label+" "+laneTimes[i].getText());
            change(values[i],Model.time(left>0?left:ctl.state().seconds(i)));values[i].setTextColor(left>0?GOLD:FG);
            bars[i].setMax(ctl.state().seconds(i));bars[i].setProgress(left>0?left:ctl.state().seconds(i));
            starts[i].setEnabled(left==0);resets[i].setEnabled(left>0);starts[i].setAlpha(left==0?1f:.4f);resets[i].setAlpha(left>0?1f:.4f);
            if(left>0){count++;if(left<min){min=left;next=Model.ROLES[i/2]+" · "+s.spellId.label+" "+Model.time(left);}}
        }change(status,ctl.status());change(summary,"冷却中 "+count+" / 10"+(count>0?"    下一个就绪  "+next:""));
    }
    private void accent(Button b){b.setTextColor(BG);b.setTypeface(Typeface.DEFAULT,Typeface.BOLD);b.setBackground(new RippleDrawable(android.content.res.ColorStateList.valueOf(0x44787878),shape(GOLD,0),null));}
    private void voiceToggle(){
        if(speech.active()){speech.stop();return;}
        if(checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED){requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO},72);return;}speech.start();
    }
    private final class Sheet {
        final Dialog dialog=new Dialog(Mobile.this);final LinearLayout body=layout(1);
        Sheet(String title){
            dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);ScrollView scroll=new ScrollView(Mobile.this);scroll.setFillViewport(false);
            LinearLayout surface=layout(1);surface.setBackground(shape(PANEL,LINE));pad(surface,18,14,18,16);scroll.addView(surface);
            LinearLayout bar=layout(0);bar.setGravity(Gravity.CENTER_VERTICAL);TextView heading=text(title,21,FG);heading.setTypeface(Typeface.DEFAULT,Typeface.BOLD);flex(bar,heading,-2);
            Button close=button("关闭",dialog::dismiss);bar.addView(close,dp(58),dp(44));surface.addView(bar);surface.addView(body);pad(body,0,12,0,0);
            dialog.setContentView(scroll);dialog.setCanceledOnTouchOutside(true);Window win=dialog.getWindow();win.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));win.setGravity(Gravity.BOTTOM);
            win.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);win.setLayout(-1,-2);
        }
        void action(String title,Runnable fn){Button b=button(title,fn);accent(b);add(b);}
        void add(View v){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.setMargins(0,dp(3),0,dp(3));v.setMinimumHeight(dp(46));body.addView(v,p);}
        void open(){dialog.show();Window window=dialog.getWindow();window.setLayout(Math.min(getResources().getDisplayMetrics().widthPixels,dp(560)),-2);}
        void close(){dialog.dismiss();}
    }
    private void adjust(int i){
        Sheet s=new Sheet(Model.ROLES[i/2]+" · 扣减时间");LinearLayout row=layout(0);s.add(row);for(int n:new int[]{5,10,20})flex(row,button("−"+n+" 秒",()->{ctl.edit(m->m.deduct(i,ctl.now(),n));s.close();refresh();}),48);s.open();
    }
    private void choose(int i){Sheet s=new Sheet(Model.ROLES[i/2]+" · 选择技能");LinearLayout row=null;int col=0;
        for(Model.Spell spell:Model.Spell.values()){if(col++%3==0){row=layout(0);s.add(row);}
            Button tile=button(spell.label+"\n"+spell.seconds+" 秒",()->{ctl.choose(i,spell);s.close();refresh();});tile.setSingleLine(false);tile.setGravity(Gravity.CENTER);tile.setTextSize(13);
            if(spell==ctl.state().slots[i].spellId)accent(tile);pad(tile,3,8,3,5);tile.setCompoundDrawables(null,icon(spell.name(),32),null,null);
            if(spell==ctl.state().slots[i^1].spellId){tile.setEnabled(false);tile.setAlpha(.35f);}flex(row,tile,94);
        }s.open();
    }
    private EditText number(String value){EditText e=new EditText(this);e.setSingleLine(true);e.setInputType(InputType.TYPE_CLASS_NUMBER);e.setTextColor(FG);e.setHintTextColor(MUTED);e.setBackground(shape(BG,LINE));pad(e,12,6,12,6);e.setText(value);return e;}
    private void room(){Sheet s=new Sheet(ctl.inRoom()?"对局 "+ctl.code():"对局码");
        if(ctl.inRoom())s.action("离开对局",()->{ctl.leave();s.close();refresh();});
        else{EditText code=number("");code.setHint("6 至 32 位数字");s.add(code);LinearLayout row=layout(0);s.add(row);
            for(boolean create:new boolean[]{true,false}){Button b=button(create?"创建对局":"加入对局",()->{String input=code.getText().toString();if(!input.matches("[0-9]{6,32}")){code.setError("请输入 6 至 32 位数字");return;}ctl.enter(input,create);s.close();});if(create)accent(b);flex(row,b,48);}}
        s.open();
    }
    private Switch option(Sheet s,String name,boolean value){Switch b=new Switch(this);b.setText(name);b.setTextColor(FG);b.setTextSize(15);b.setChecked(value);
        b.setThumbTintList(new android.content.res.ColorStateList(new int[][]{new int[]{android.R.attr.state_checked},new int[]{}},new int[]{GOLD,MUTED}));s.add(b);return b;}
    private void haste(int role){
        Sheet sheet=new Sheet(Model.ROLES[role]+" · 召唤师技能急速");int mask=ctl.state().loadout[role];Switch[] items=new Switch[4];
        for(int i=0;i<4;i++)items[i]=option(sheet,Model.OPTIONS[i],(mask&(1<<i))!=0);
        TextView info=text("",14,GOLD);sheet.add(info);
        Runnable preview=()->{int m=0;for(int i=0;i<4;i++)if(items[i].isChecked())m|=1<<i;info.setText("总急速 "+Model.haste(m)+" · 闪现 "+Model.durationMs(Model.Spell.SummonerFlash,m)/1000.0+" 秒");};
        for(int i=0;i<4;i++){final int k=i;items[i].setOnCheckedChangeListener((v,on)->{if(on&&k<2)items[1-k].setChecked(false);preview.run();});}
        preview.run();sheet.action("保存",()->{int value=0;for(int i=0;i<4;i++)if(items[i].isChecked())value|=1<<i;final int updated=value;ctl.edit(m->m.configure(role,updated,ctl.now()));sheet.close();refresh();});sheet.open();
    }
    private void settings(){
        Sheet s=new Sheet("提醒设置");Model.Settings cfg=ctl.state().settings;s.add(text("提前提醒秒数（0 为结束时）",14,MUTED));
        EditText lead=number(Integer.toString(cfg.lead));s.add(lead);s.add(text("记录时自动扣秒（0–300 秒）",14,MUTED));EditText debit=number(Integer.toString(cfg.autoDeduct));s.add(debit);Switch sound=option(s,"提示音",cfg.sound),voice=option(s,"中文播报",cfg.voice),watch=option(s,"本机后台提醒",app.watching);
        s.add(button("试听提醒",()->{Model.Settings sample=new Model.Settings();sample.sound=sound.isChecked();sample.voice=voice.isChecked();app.remind("上单 · 闪现 已就绪",sample);}));
        s.action("保存设置",()->{int n;try{n=Integer.parseInt(lead.getText().toString());}catch(NumberFormatException ex){n=-1;}if(n<0||n>600){lead.setError("请输入 0 至 600 秒");return;}int value=n;
            int deduction;try{deduction=Integer.parseInt(debit.getText().toString());}catch(NumberFormatException e){deduction=-1;}if(deduction<0||deduction>300){debit.setError("请输入 0 至 300 秒");return;}final int amount=deduction;
            ctl.edit(m->{m.settings.autoDeduct=amount;m.settings.lead=value;m.settings.sound=sound.isChecked();m.settings.voice=voice.isChecked();});
            if(watch.isChecked()&&!app.watching){if(Build.VERSION.SDK_INT>=33&&checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},71);else startForegroundService(new Intent(this,Watch.class));}
            else if(!watch.isChecked()&&app.watching)stopService(new Intent(this,Watch.class));s.close();});s.open();
    }
    public void onRequestPermissionsResult(int request,String[] names,int[] results){super.onRequestPermissionsResult(request,names,results);
        boolean granted=results.length>0&&results[0]==PackageManager.PERMISSION_GRANTED;
        if(request==71){if(granted)startForegroundService(new Intent(this,Watch.class));else app.notice("后台提醒需要通知权限");}
        if(request==72){if(granted)speech.start();else voiceStatus.setText("请在系统设置中允许麦克风");}}
    private void more(){Sheet s=new Sheet("对局操作");s.add(button("最近提醒",()->{s.close();Sheet log=new Sheet("最近提醒");log.add(text(ctl.alerts.isEmpty()?"暂无提醒":android.text.TextUtils.join("\n",ctl.alerts),15,FG));log.action("清空记录",()->{ctl.clearHistory();log.close();});log.open();}));
        s.add(button("重置本局",()->{s.close();Sheet reset=new Sheet("重置全部计时？");reset.add(text("技能选择和提醒设置将保留",14,MUTED));reset.action("确认重置",()->{ctl.resetGame();reset.close();refresh();});reset.open();}));s.open();}
}
