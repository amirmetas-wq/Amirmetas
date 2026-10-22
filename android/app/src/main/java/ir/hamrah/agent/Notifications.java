package ir.hamrah.agent;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;

/** Private lock-screen notifications; message content is never public. */
public final class Notifications {
    private static final String NOTES = "daily_notes";
    private static final String MAIL = "mail_reports";
    private static final String MEETINGS = "meeting_reminders";
    private Notifications() {}

    public static void ensureChannels(Context context) {
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        if (manager == null) return;
        create(manager, NOTES, "کارهای مهم روزانه", "یادآوری روزانه از متن یادداشت انتخاب‌شده");
        create(manager, MAIL, "گزارش ایمیل", "اعلام آماده‌شدن گزارش ایمیل‌ها");
        create(manager, MEETINGS, "جلسه‌ها", "یادآوری جلسه‌های ثبت‌شده در همراه");
    }

    private static void create(NotificationManager manager, String id, String title, String description) {
        NotificationChannel channel = new NotificationChannel(id, title, NotificationManager.IMPORTANCE_DEFAULT);
        channel.setDescription(description);
        channel.setLockscreenVisibility(Notification.VISIBILITY_PRIVATE);
        manager.createNotificationChannel(channel);
    }

    public static boolean canPost(Context context) {
        if (Build.VERSION.SDK_INT >= 33 && context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED) return false;
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        return manager != null && manager.areNotificationsEnabled();
    }

    public static boolean notes(Context context, String content) {
        return post(context, NOTES, 3100, "کارهای مهم روزانه", content, "یادآوری کارهای روزانه", "notes");
    }

    public static boolean mail(Context context) {
        return post(context, MAIL, 3101, "گزارش ایمیل آماده است", "برای دیدن گزارش، همراه را باز کنید.",
            "گزارش ایمیل آماده است", "mail");
    }

    public static boolean meeting(Context context, int id, String title) {
        // A tag prevents a meeting ID from replacing a daily notification.
        return post(context, MEETINGS, id, "یادآوری جلسه", title, "یادآوری جلسه", "meetings");
    }

    private static boolean post(Context context, String channel, int id, String title, String text,
                                String publicTitle, String section) {
        ensureChannels(context);
        if (!canPost(context)) return false;
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        NotificationChannel configured = manager.getNotificationChannel(channel);
        if (configured != null && configured.getImportance() == NotificationManager.IMPORTANCE_NONE) return false;
        Intent open = new Intent(context, MainActivity.class)
            .setAction("ir.hamrah.agent.OPEN_" + section)
            .putExtra("section", section)
            .setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent tap = PendingIntent.getActivity(context, section.hashCode(), open,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification publicVersion = new Notification.Builder(context, channel)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(publicTitle)
            .setContentText("برای دیدن جزئیات قفل گوشی را باز کنید.")
            .setContentIntent(tap)
            .build();
        String bounded = text == null ? "" : text.substring(0, Math.min(3500, text.length()));
        Notification notification = new Notification.Builder(context, channel)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(bounded)
            .setStyle(new Notification.BigTextStyle().bigText(bounded))
            .setContentIntent(tap)
            .setAutoCancel(true)
            .setVisibility(Notification.VISIBILITY_PRIVATE)
            .setPublicVersion(publicVersion)
            .setCategory(MEETINGS.equals(channel) ? Notification.CATEGORY_EVENT : Notification.CATEGORY_REMINDER)
            .build();
        try {
            manager.notify(channel, id, notification);
            return true;
        } catch (SecurityException denied) {
            return false;
        }
    }
}
