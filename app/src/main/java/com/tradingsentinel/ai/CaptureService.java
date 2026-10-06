package com.tradingsentinel.ai;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.IBinder;

public class CaptureService extends Service {
    private static final String CHANNEL="screen_capture";
    @Override public void onCreate(){
        super.onCreate();
        NotificationManager nm=getSystemService(NotificationManager.class);
        if(nm!=null)nm.createNotificationChannel(new NotificationChannel(CHANNEL,"Screen capture",NotificationManager.IMPORTANCE_LOW));
    }
    @Override public int onStartCommand(Intent intent,int flags,int startId){
        Notification n=new Notification.Builder(this,CHANNEL)
            .setContentTitle("Trading Sentinel AI")
            .setContentText("Захват экрана активен")
            .setSmallIcon(android.R.drawable.ic_menu_view)
            .setOngoing(true).build();
        if(Build.VERSION.SDK_INT>=29)startForeground(1001,n,ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION);
        else startForeground(1001,n);
        return START_NOT_STICKY;
    }
    @Override public IBinder onBind(Intent intent){return null;}
}