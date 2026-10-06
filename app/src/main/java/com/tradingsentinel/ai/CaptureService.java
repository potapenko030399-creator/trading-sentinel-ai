package com.tradingsentinel.ai;

import android.app.*;
import android.content.Intent;
import android.os.IBinder;

public class CaptureService extends Service {
 private static final String CHANNEL="screen_capture";
 @Override public void onCreate(){super.onCreate();NotificationManager nm=getSystemService(NotificationManager.class);nm.createNotificationChannel(new NotificationChannel(CHANNEL,"Screen capture",NotificationManager.IMPORTANCE_LOW));}
 @Override public int onStartCommand(Intent i,int f,int id){Notification n=new Notification.Builder(this,CHANNEL).setContentTitle("Trading Sentinel AI").setContentText("Захват экрана активен").setSmallIcon(android.R.drawable.ic_menu_view).setOngoing(true).build();startForeground(1001,n);return START_NOT_STICKY;}
 @Override public IBinder onBind(Intent i){return null;}
}