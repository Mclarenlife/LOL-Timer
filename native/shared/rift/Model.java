package rift;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

public final class Model {
    public static final Gson JSON = new GsonBuilder().serializeNulls().create();
    public static final String[] ROLES={"上单","打野","中单","射手","辅助"};
    public static final String[] ICONS={"top","jungle","middle","bottom","utility"};
    public enum Spell {
        SummonerFlash("闪现",300), SummonerTeleport("传送",300), SummonerDot("引燃",180),
        SummonerSmite("惩戒",15), SummonerHeal("治疗术",240), SummonerBarrier("屏障",180),
        SummonerExhaust("虚弱",240), SummonerBoost("净化",240), SummonerHaste("幽灵疾步",240);
        public final String label;
        public final int seconds;
        Spell(String label,int seconds){this.label=label;this.seconds=seconds;}
        public String toString(){return label;}
    }
    public static final class Slot {
        public Spell spellId;
        public Long endAt;
        public boolean alerted;
        Slot(Spell spell){spellId=spell;}
        public int left(long now){return endAt==null?0:(int)Math.max(0,(endAt-now+999)/1000);}
        public void reset(){endAt=null;alerted=false;}

    }
    public static final class Settings {
        public int lead,autoDeduct;
        public boolean sound=true;
        public boolean voice;
    }
    public static final String[] OPTIONS={"明朗之靴  +10","猩红明朗（升级明朗之靴）  +20","星界洞悉  +18","启封的秘籍  ·  仅影响置换"};
    public int[] loadout=new int[5];
    public static int haste(int mask){return ((mask&2)!=0?20:(mask&1)!=0?10:0)+((mask&4)!=0?18:0);}
    public static long durationMs(Spell spell,int mask){
        return spell==Spell.SummonerSmite?15000:Math.round(spell.seconds*100000.0/(100+haste(mask)));
    }
    public long durationMs(int i){return durationMs(slots[i].spellId,loadout[i/2]);}
    public int seconds(int i){return (int)((durationMs(i)+999)/1000);}
    public void start(int i,long now){
        Slot s=slots[i];if(s.left(now)>0)return;
        s.endAt=now+Math.max(0,durationMs(i)-1000L*settings.autoDeduct);s.alerted=false;
    }
    public void deduct(int i,long now,int seconds){start(i,now);slots[i].endAt=Math.max(now,slots[i].endAt-1000L*seconds);}
    public void configure(int role,int mask,long now){
        int old=loadout[role];loadout[role]=mask;
        for(int i=role*2;i<role*2+2;i++){
            Slot s=slots[i];if(s.left(now)>0)s.endAt=Math.max(now,s.endAt+durationMs(i)-durationMs(s.spellId,old));
        }
    }
    public int version=1;
    public Settings settings=new Settings();
    public Slot[] slots=new Slot[10];
    public Model(){
        Spell[] other={Spell.SummonerTeleport,Spell.SummonerSmite,Spell.SummonerDot,Spell.SummonerBarrier,Spell.SummonerExhaust};
        for(int i=0;i<5;i++){slots[i*2]=new Slot(Spell.SummonerFlash);slots[i*2+1]=new Slot(other[i]);}
    }
    public Model copy(){return JSON.fromJson(JSON.toJson(this),Model.class);}
    public boolean valid(){
        if(version!=1||slots==null||slots.length!=10||settings==null||settings.lead<0||settings.lead>600)return false;
        if(loadout==null||loadout.length!=5||settings.autoDeduct<0||settings.autoDeduct>300)return false;
        for(int mask:loadout)if(mask<0||mask>15||(mask&3)==3)return false;
        for(int i=0;i<slots.length;i++){
            if(slots[i]==null||slots[i].spellId==null)return false;
            if(i%2==1&&slots[i].spellId==slots[i-1].spellId)return false;
        }
        return true;
    }
    public static String time(int s){return String.format(java.util.Locale.ROOT,"%02d:%02d",s/60,s%60);}
}

