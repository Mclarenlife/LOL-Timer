package rift;

import android.app.*;
import android.content.*;
import android.os.*;
import android.media.*;
import android.speech.tts.TextToSpeech;
import java.util.Locale;

public final class MobileApp extends Application implements Controller.Host {
    Controller ctl;
    Mobile screen;
    boolean watching, speechReady;
    private TextToSpeech speech;
    private ToneGenerator tone;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final Runnable pulse=new Runnable(){
        public void run(){if(screen==null&&!watching)return;ctl.tick();if(screen!=null)screen.refresh();handler.postDelayed(this,250);}
    };
    void schedule(){handler.removeCallbacks(pulse);handler.post(pulse);}
    @Override public void onCreate(){
        super.onCreate();ctl=new Controller(this);
        NotificationManager nm=getSystemService(NotificationManager.class);
        nm.createNotificationChannel(new NotificationChannel("watch","后台计时",NotificationManager.IMPORTANCE_LOW));
        NotificationChannel reminder=new NotificationChannel("timers","技能提醒",NotificationManager.IMPORTANCE_DEFAULT);
        reminder.setSound(null,null);nm.createNotificationChannel(reminder);
        speech=new TextToSpeech(this,result->{if(result==TextToSpeech.SUCCESS&&speech!=null)speechReady=speech.setLanguage(Locale.SIMPLIFIED_CHINESE)>=0;});
    }
    Notification.Builder notification(String channel,String title,String body){
        PendingIntent open=PendingIntent.getActivity(this,0,new Intent(this,Mobile.class).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        return new Notification.Builder(this,channel).setSmallIcon(android.R.drawable.ic_lock_idle_alarm).setContentTitle(title).setContentText(body).setContentIntent(open);
    }
    public String load(){return getSharedPreferences("tracker",0).getString("state","");}
    public void store(String json){getSharedPreferences("tracker",0).edit().putString("state",json).apply();}
    public void post(Runnable action){handler.post(()->{action.run();if(screen!=null)screen.refresh();});}
    public void notice(String text){android.widget.Toast.makeText(this,text,android.widget.Toast.LENGTH_SHORT).show();}
    public void remind(String text,Model.Settings settings){
        if(screen!=null)notice(text);
        if(settings.sound){try{if(tone==null)tone=new ToneGenerator(AudioManager.STREAM_MUSIC,75);tone.startTone(ToneGenerator.TONE_PROP_BEEP2,300);}catch(RuntimeException ignored){}}
        if(settings.voice&&speechReady&&screen!=null)screen.muteUntil=System.currentTimeMillis()+5500;
        if(settings.voice&&speechReady)speech.speak(text,TextToSpeech.QUEUE_ADD,null,Long.toString(System.nanoTime()));
        if(screen==null&&watching&&getSystemService(NotificationManager.class).areNotificationsEnabled())
            getSystemService(NotificationManager.class).notify((int)SystemClock.uptimeMillis(),notification("timers","技能提醒",text).setAutoCancel(true).build());
    }
}
