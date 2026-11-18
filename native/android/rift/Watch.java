package rift;

import android.app.*;
import android.content.Intent;
import android.os.IBinder;

public final class Watch extends Service {
    public int onStartCommand(Intent intent,int flags,int id){
        MobileApp app=(MobileApp)getApplication();
        startForeground(1,app.notification("watch","Rift Timer 正在后台计时","点按返回对局，可在提醒设置中关闭后台提醒").setOngoing(true).build());
        app.watching=true;app.schedule();return START_NOT_STICKY;
    }
    public void onDestroy(){MobileApp app=(MobileApp)getApplication();app.watching=false;app.schedule();super.onDestroy();}
    public IBinder onBind(Intent intent){return null;}
}
