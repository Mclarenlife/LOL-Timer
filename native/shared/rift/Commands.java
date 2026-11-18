package rift;

import java.util.*;

public final class Commands {
    private Commands(){}
    private static final String[][] LANES={{"上单","上路"},{"打野"},{"中单","中路"},{"射手","下路","AD","ADC"},{"辅助"}};
    private static final String[][] NAMES={{"闪现","闪"},{"传送","TP"},{"引燃","点燃"},{"惩戒"},{"治疗术","治疗"},{"屏障"},{"虚弱"},{"净化"},{"幽灵疾步","疾跑"}};
    private static final Map<String,Integer> phrases=new LinkedHashMap<>();
    static {
        for(int r=0;r<LANES.length;r++)for(String lane:LANES[r])
            for(int s=0;s<NAMES.length;s++)for(String name:NAMES[s])phrases.put(lane+name,r*10+s);
    }
    public static Set<String> phrases(){return Collections.unmodifiableSet(phrases.keySet());}
    public static int match(String text){
        if(text==null)return -1;
        String key=text.toUpperCase(Locale.ROOT).replaceAll("[\\s，。！？、,.!?·]+","");
        Integer value=phrases.get(key);return value==null?-1:value;
    }
    public static void apply(Controller ctl,String text){
        int match=match(text);
        if(match<0){ctl.feedback("未匹配口令，请说「位置 + 技能」");return;}
        int first=(match/10)*2;Model.Spell spell=Model.Spell.values()[match%10];
        Model.Slot[] slots=ctl.state().slots;
        int index=slots[first].spellId==spell?first:slots[first+1].spellId==spell?first+1:-1;
        if(index<0){ctl.feedback(Model.ROLES[first/2]+"未配置"+spell.label);return;}
        if(slots[index].left(ctl.now())>0){ctl.feedback(Model.ROLES[first/2]+spell.label+"正在计时");return;}
        ctl.edit(m->m.start(index,ctl.now()));
    }
}