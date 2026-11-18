package rift;

import java.util.UUID;

/** Offline regression checks. Pass "rooms" to exercise an isolated new room on the configured service. */
public final class Check implements Controller.Host {
    private String disk="";
    private int reminders;
    public String load(){return disk;}
    public void store(String value){disk=value;}
    public void post(Runnable r){r.run();}
    public void notice(String s){}
    public void remind(String s,Model.Settings settings){reminders++;}
    private static void expect(boolean value,String reason){if(!value)throw new AssertionError(reason);}
    private void offline(){
        Model m0=new Model();long t=1000000;m0.settings.autoDeduct=5;m0.configure(0,5,t);m0.start(0,t);
        expect(m0.slots[0].endAt==t+229375,"28 haste and one automatic deduction");
        m0.configure(0,6,t+5000);expect(m0.slots[0].endAt==t+212391,"mid-cooldown adjustment preserves elapsed time");
        m0.deduct(0,t+6000,5);expect(m0.slots[0].endAt==t+207391,"deduction not applied twice");
        m0.configure(1,14,t);expect(m0.durationMs(3)==15000,"smite cast interval");
        Controller c=new Controller(this);expect(c.state().valid(),"defaults");
        c.choose(0,Model.Spell.SummonerTeleport);expect(c.state().slots[0].spellId==Model.Spell.SummonerFlash,"no duplicate spells");
        c.choose(0,Model.Spell.SummonerHeal);
        long now=c.now();c.edit(m->m.deduct(0,now,10));expect(c.state().slots[0].left(now)==230,"deduct from idle");
        c.edit(m->m.deduct(0,now+5000,20));expect(c.state().slots[0].left(now+5000)==205,"deduct while running");
        Controller restored=new Controller(this);expect(restored.state().slots[0].left(now)==210,"persisted deadline");
        restored.edit(m->{m.settings.lead=60;m.slots[0].endAt=now+59000;});restored.tick();restored.tick();expect(reminders==1,"once at threshold");
        restored.edit(m->m.slots[0].endAt=now-10);restored.tick();expect(restored.state().slots[0].endAt==null&&reminders==1,"restore after expiry");
        restored.edit(m->{m.settings.lead=0;m.deduct(3,now,20);});restored.tick();expect(reminders==2&&restored.state().slots[3].endAt==null,"deduct past expiry");
        restored.resetGame();expect(restored.state().slots[0].spellId==Model.Spell.SummonerHeal,"reset keeps choices");
        restored.choose(0,Model.Spell.SummonerHaste);
        restored.choose(7,Model.Spell.SummonerHeal);
        Commands.apply(restored,"上单 疾 跑");Commands.apply(restored,"下 路 治疗");
        expect(restored.state().slots[0].left(restored.now())>0,"voice starts top ghost");
        expect(restored.state().slots[7].left(restored.now())>0,"voice starts bottom heal");
        System.out.println("PASS offline timers, reminders, expiry, persistence and selection");
    }
    private static void online()throws Exception{
        String code="01"+Long.toUnsignedString(UUID.randomUUID().getLeastSignificantBits());
        Model a=new Model();a.loadout[0]=6;a.settings.autoDeduct=5;a.start(0,System.currentTimeMillis());
        Rooms.Snapshot host=Rooms.request(System.getProperty("rift.test.api","http://127.0.0.1:19437/rift-timer/api"),"create",code,0,a);
        Rooms.Snapshot guest=Rooms.request(System.getProperty("rift.test.api","http://127.0.0.1:19437/rift-timer/api"),"read",code,0,null);
        expect(guest.state.loadout[0]==6&&guest.state.settings.autoDeduct==5,"shared haste and deduction");
        expect(guest.rev==host.rev&&guest.state.slots[0].endAt.equals(host.state.slots[0].endAt),"join running timer");
        guest.state.deduct(0,guest.now,10);guest.state.settings.lead=60;
        Rooms.Snapshot updated=Rooms.request(System.getProperty("rift.test.api","http://127.0.0.1:19437/rift-timer/api"),"write",code,guest.rev,guest.state);
        Rooms.Snapshot seen=Rooms.request(System.getProperty("rift.test.api","http://127.0.0.1:19437/rift-timer/api"),"read",code,0,null);
        expect(seen.rev==updated.rev&&seen.state.settings.lead==60&&host.state.slots[0].endAt-seen.state.slots[0].endAt==10000,"reverse sync");
        boolean conflict=false;try{Rooms.request(System.getProperty("rift.test.api","http://127.0.0.1:19437/rift-timer/api"),"write",code,host.rev,host.state);}catch(java.io.IOException e){conflict=true;}
        expect(conflict&&Rooms.request(System.getProperty("rift.test.api","http://127.0.0.1:19437/rift-timer/api"),"read",code,0,null).rev==updated.rev,"stale client cannot overwrite");
        seen.state.slots[0].reset();Rooms.request(System.getProperty("rift.test.api","http://127.0.0.1:19437/rift-timer/api"),"write",code,seen.rev,seen.state);
        expect(Rooms.request(System.getProperty("rift.test.api","http://127.0.0.1:19437/rift-timer/api"),"read",code,0,null).state.slots[0].endAt==null,"shared reset");
        System.out.println("PASS live room create, join, deadline, reverse edit, settings, conflict and reset");
    }
    public static void main(String[] args)throws Exception{new Check().offline();if(args.length>0&&args[0].equals("rooms"))online();}
}
