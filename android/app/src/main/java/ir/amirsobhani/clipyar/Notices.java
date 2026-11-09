package ir.amirsobhani.clipyar;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.os.Build;
import androidx.work.*;
import java.util.UUID;

public final class Notices {
    private Notices() { }
    public static void channels(Context c) {
        NotificationManager n=c.getSystemService(NotificationManager.class);
        n.createNotificationChannel(new NotificationChannel("work","آماده‌سازی ویدئو",NotificationManager.IMPORTANCE_LOW));
        n.createNotificationChannel(new NotificationChannel("done","ویدئوهای آماده",NotificationManager.IMPORTANCE_DEFAULT));
    }
    private static Notification.Builder base(Context c,String channel,String text) {
        PendingIntent open=PendingIntent.getActivity(c,1,new Intent(c,MainActivity.class),PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);
        return new Notification.Builder(c,channel).setSmallIcon(R.drawable.ic_clipyar).setContentTitle("کلیپ‌یار")
            .setContentText(text).setStyle(new Notification.BigTextStyle().bigText(text)).setContentIntent(open);
    }
    public static ForegroundInfo foreground(Context c,UUID id,String text) {
        Notification notification=base(c,"work",text).setOngoing(true).setOnlyAlertOnce(true)
            .addAction(new Notification.Action.Builder(null,"توقف",WorkManager.getInstance(c).createCancelPendingIntent(id)).build()).build();
        int type=ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC;
        if(Build.VERSION.SDK_INT>=35) type|=ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROCESSING;
        return new ForegroundInfo(11,notification,type);
    }
    public static void done(Context c,String text) {
        if(Build.VERSION.SDK_INT>=33 && c.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED) return;
        c.getSystemService(NotificationManager.class).notify(12,base(c,"done",text).setAutoCancel(true).build());
    }
}
