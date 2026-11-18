package rift;

import java.util.Objects;
import java.util.concurrent.*;
import java.util.function.Consumer;

/** Public methods and callbacks run on the platform UI thread. HTTP runs on workers. */
public final class Controller {
    public interface Host {
        String load(); void store(String json); void post(Runnable action);
        default void expired(int index,long deadline){}
        void notice(String text); void remind(String text,Model.Settings settings);
    }
    public static final class Saved {
        public Model local=new Model();
        public Model room;
        public String code="";
        public String endpoint;
        public long offset;
        public int rev;
    }
    private final Host host;
    private final ExecutorService workers=Executors.newFixedThreadPool(2,r->{Thread t=new Thread(r,"rift-http");t.setDaemon(true);return t;});
    private Saved saved;
    private boolean pending,reading,connected;
    private long sequence,nextPoll;
    private final long[] expired=new long[10];
    public String lastAlert="";
    public final java.util.ArrayList<String> alerts=new java.util.ArrayList<>();
    public Controller(Host host){
        this.host=host;
        try{saved=Model.JSON.fromJson(host.load(),Saved.class);}catch(RuntimeException ignored){}
        if(saved==null||saved.local==null||!saved.local.valid())saved=new Saved();
        if(saved.room==null||!saved.room.valid()||saved.code==null||!saved.code.matches("[0-9]{6,32}"))saved.code="";
    }
    public String endpoint(){return saved.endpoint==null?Rooms.ENDPOINT:saved.endpoint;}
    public boolean endpoint(String input){
        try{
            java.net.URI uri=new java.net.URI(input.trim());
            if(!("https".equalsIgnoreCase(uri.getScheme())||"http".equalsIgnoreCase(uri.getScheme()))||uri.getHost()==null||uri.getUserInfo()!=null||uri.getQuery()!=null||uri.getFragment()!=null)throw new IllegalArgumentException();
            if(pending||inRoom())return false;
            saved.endpoint=uri.toASCIIString();persist();return true;
        }catch(Exception e){host.notice("请输入完整的对局 API 地址");return false;}
    }
    public Model state(){return inRoom()?saved.room:saved.local;}
    public String code(){return saved.code;}
    public boolean inRoom(){return saved.code!=null&&!saved.code.isEmpty();}
    public String status(){return inRoom()?("对局 "+code()+"  ·  "+(connected?"已同步":"连接中")):(pending?"正在连接":"单人模式");}
    public long now(){return System.currentTimeMillis()+(inRoom()?saved.offset:0);}
    private void persist(){host.store(Model.JSON.toJson(saved));}
    private void accept(Rooms.Snapshot result,boolean entering){
        Model previous=state();saved.offset=result.offset;
        if(entering||result.rev!=saved.rev){
            long now=now();
            for(int i=0;i<10;i++){
                Model.Slot s=result.state.slots[i],old=previous.slots[i];
                boolean shortened=s.endAt!=null&&old.endAt!=null&&old.endAt>=now&&s.endAt<old.endAt;
                s.alerted=(!entering&&old.spellId==s.spellId&&(Objects.equals(old.endAt,s.endAt)||shortened||(s.endAt!=null&&old.left(now)>0&&result.state.loadout[i/2]!=previous.loadout[i/2]))&&old.alerted)
                    || (s.endAt!=null&&s.endAt<now-30000);
            }
            saved.room=result.state;saved.rev=result.rev;persist();
        }
        connected=true;
    }
    public void enter(String code,boolean create){
        if(!code.matches("[0-9]{6,32}")){host.notice("请输入 6 至 32 位数字");return;}
        if(pending||inRoom())return;
        send(create?"create":"read",code,create?state().copy():null,true);
    }
    public void leave(){
        if(pending){host.notice("正在提交，请稍后再试");return;}
        sequence++;reading=false;saved.code="";saved.room=null;saved.rev=0;saved.offset=0;
        connected=false;clearHistory();persist();
    }
    private void send(String action,String code,Model proposal,boolean entering){
        pending=true;reading=false;long ticket=++sequence;int rev=saved.rev;
        workers.execute(()->{
            try{
                Rooms.Snapshot data=Rooms.request(endpoint(),action,code,rev,proposal);
                host.post(()->{
                    if(sequence!=ticket)return;pending=false;
                    if(entering){saved.room=state().copy();saved.code=code;clearHistory();}
                    accept(data,entering);nextPoll=0;
                });
            }catch(Exception ex){host.post(()->{if(sequence==ticket){pending=false;connected=false;nextPoll=0;host.notice(error(ex));}});}
        });
    }
    private String error(Exception e){return e.getMessage()==null?"连接失败，请重试":e.getMessage();}
    public void edit(Consumer<Model> edit){
        if(pending){host.notice("正在同步，请稍后再试");return;}
        if(!inRoom()){edit.accept(saved.local);persist();return;}
        if(!connected){host.notice("正在恢复连接，请稍后再试");return;}
        Model copy=state().copy();edit.accept(copy);send("write",code(),copy,false);
    }
    public void feedback(String message){host.notice(message);}
    public void choose(int index,Model.Spell spell){
        edit(m->{if(m.slots[index^1].spellId!=spell&&m.slots[index].spellId!=spell)m.slots[index]=new Model.Slot(spell);});
    }
    public void clearHistory(){alerts.clear();lastAlert="";}
    public void resetGame(){edit(m->{for(Model.Slot s:m.slots)s.reset();});clearHistory();}
    public void tick(){
        long now=now();
        for(int i=0;i<10;i++){
            Model.Slot s=state().slots[i];
            if(s.endAt==null){expired[i]=0;continue;}
            int left=s.left(now);
            if(left==0&&expired[i]!=s.endAt.longValue()){expired[i]=s.endAt;if(now-s.endAt<3000)host.expired(i,s.endAt);}
            if(!s.alerted&&left<=state().settings.lead){
                s.alerted=true;
                lastAlert=Model.ROLES[i/2]+" · "+s.spellId.label+(left==0?" 已就绪":" 还有 "+left+" 秒");
                alerts.add(0,lastAlert);if(alerts.size()>30)alerts.remove(30);
                host.remind(lastAlert,state().settings);persist();
            }
            if(!inRoom()&&left==0){s.reset();persist();}
        }
        if(inRoom()&&!pending&&!reading&&System.currentTimeMillis()>=nextPoll){
            nextPoll=System.currentTimeMillis()+1000;reading=true;
            final String code=code();final long ticket=sequence;
            workers.execute(()->{
                try{Rooms.Snapshot data=Rooms.request(endpoint(),"read",code,0,null);host.post(()->{
                    if(ticket==sequence){reading=false;accept(data,false);}
                });}catch(Exception e){host.post(()->{if(ticket==sequence){reading=false;connected=false;nextPoll=System.currentTimeMillis()+1500;}});}
            });
        }
    }
}

