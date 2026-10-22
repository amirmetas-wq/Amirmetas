package ir.hamrah.agent;

import android.Manifest;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.provider.CalendarContract;

import java.util.ArrayList;
import java.util.List;
import java.util.TimeZone;

/** Calendar access is granted by Android, and the user chooses the destination calendar. */
public final class CalendarHelper {
    private CalendarHelper() {}

    public static final class CalendarChoice {
        public final long id;
        public final String name;
        public final String account;

        public CalendarChoice(long id, String name, String account) {
            this.id = id;
            this.name = name == null || name.trim().isEmpty() ? "تقویم" : name;
            this.account = account == null ? "" : account;
        }

        @Override public String toString() {
            return account.isEmpty() ? name : name + " — " + account;
        }
    }

    public static List<CalendarChoice> writableCalendars(Context context) {
        requirePermission(context, Manifest.permission.READ_CALENDAR);
        List<CalendarChoice> calendars = new ArrayList<>();
        String[] projection = {
                CalendarContract.Calendars._ID,
                CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
                CalendarContract.Calendars.ACCOUNT_NAME
        };
        String selection = CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL + ">=?";
        String[] args = {String.valueOf(CalendarContract.Calendars.CAL_ACCESS_CONTRIBUTOR)};
        try (Cursor cursor = context.getContentResolver().query(
                CalendarContract.Calendars.CONTENT_URI, projection, selection, args,
                CalendarContract.Calendars.CALENDAR_DISPLAY_NAME + " ASC")) {
            if (cursor != null) {
                while (cursor.moveToNext()) {
                    calendars.add(new CalendarChoice(cursor.getLong(0), cursor.getString(1),
                            cursor.getString(2)));
                }
            }
        }
        if (calendars.isEmpty()) {
            throw new IllegalStateException("تقویم قابل‌نوشتن پیدا نشد. ابتدا در برنامهٔ تقویم گوشی یک حساب یا تقویم فعال کنید و همگام‌سازی تقویم را بررسی کنید.");
        }
        return calendars;
    }

    /** Inserts only an event and its local calendar reminder; no invitations are sent. */
    public static long insert(Context context, long calendarId, String title, long startMillis,
                              int durationMinutes, int reminderMinutes) {
        requirePermission(context, Manifest.permission.READ_CALENDAR);
        requirePermission(context, Manifest.permission.WRITE_CALENDAR);
        if (calendarId < 0 || title == null || title.trim().isEmpty()) {
            throw new IllegalArgumentException("عنوان جلسه و تقویم مقصد را مشخص کنید.");
        }
        if (startMillis <= 0 || durationMinutes < 1 || durationMinutes > 10080
                || reminderMinutes < 0 || reminderMinutes > 525600) {
            throw new IllegalArgumentException("تاریخ، مدت جلسه یا فاصلهٔ یادآوری معتبر نیست.");
        }
        long endMillis;
        try {
            endMillis = Math.addExact(startMillis, durationMinutes * 60_000L);
        } catch (ArithmeticException e) {
            throw new IllegalArgumentException("تاریخ جلسه معتبر نیست.", e);
        }
        ContentResolver resolver = context.getContentResolver();
        validateDestination(resolver, calendarId);

        ContentValues event = new ContentValues();
        event.put(CalendarContract.Events.CALENDAR_ID, calendarId);
        event.put(CalendarContract.Events.TITLE, title.trim());
        event.put(CalendarContract.Events.DTSTART, startMillis);
        event.put(CalendarContract.Events.DTEND, endMillis);
        event.put(CalendarContract.Events.EVENT_TIMEZONE, TimeZone.getDefault().getID());
        event.put(CalendarContract.Events.HAS_ALARM, 1);
        event.put(CalendarContract.Events.STATUS, CalendarContract.Events.STATUS_CONFIRMED);

        Uri eventUri = null;
        try {
            eventUri = resolver.insert(CalendarContract.Events.CONTENT_URI, event);
            if (eventUri == null) {
                throw new IllegalStateException("برنامهٔ تقویم ثبت جلسه را نپذیرفت.");
            }
            long eventId = ContentUris.parseId(eventUri);
            ContentValues reminder = new ContentValues();
            reminder.put(CalendarContract.Reminders.EVENT_ID, eventId);
            reminder.put(CalendarContract.Reminders.MINUTES, reminderMinutes);
            reminder.put(CalendarContract.Reminders.METHOD, CalendarContract.Reminders.METHOD_ALERT);
            Uri reminderUri = resolver.insert(CalendarContract.Reminders.CONTENT_URI, reminder);
            if (reminderUri == null) {
                throw new IllegalStateException("تقویم نتوانست یادآور جلسه را ثبت کند؛ ثبت جلسه برگردانده شد.");
            }
            return eventId;
        } catch (RuntimeException failure) {
            // Keep a failed reminder operation from silently leaving an event without its reminder.
            if (eventUri != null) {
                try {
                    if (resolver.delete(eventUri, null, null) == 0) {
                        throw new IllegalStateException("بازگردانی جلسه تأیید نشد؛ تقویم را بررسی کنید.");
                    }
                } catch (RuntimeException rollbackFailure) {
                    failure.addSuppressed(rollbackFailure);
                    throw new IllegalStateException("ثبت یادآور کامل نشد و بازگردانی هم ناموفق بود؛ برای جلوگیری از ثبت تکراری، تقویم گوشی را بررسی کنید.", failure);
                }
            }
            throw failure;
        }
    }

    private static void validateDestination(ContentResolver resolver, long calendarId) {
        Uri uri = ContentUris.withAppendedId(CalendarContract.Calendars.CONTENT_URI, calendarId);
        String[] projection = {
                CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL,
                CalendarContract.Calendars.MAX_REMINDERS,
                CalendarContract.Calendars.ALLOWED_REMINDERS
        };
        try (Cursor cursor = resolver.query(uri, projection, null, null, null)) {
            if (cursor == null || !cursor.moveToFirst()
                    || cursor.getInt(0) < CalendarContract.Calendars.CAL_ACCESS_CONTRIBUTOR) {
                throw new IllegalStateException("تقویم انتخاب‌شده قابل‌نوشتن نیست؛ یک تقویم دیگر انتخاب کنید.");
            }
            if (!cursor.isNull(1) && cursor.getInt(1) < 1) {
                throw new IllegalStateException("تقویم انتخاب‌شده از یادآور پشتیبانی نمی‌کند؛ یک تقویم دیگر انتخاب کنید.");
            }
            String allowed = cursor.getString(2);
            if (allowed != null && !allowed.trim().isEmpty()) {
                boolean alertAllowed = false;
                for (String method : allowed.split(",")) {
                    if (method.trim().equals(String.valueOf(CalendarContract.Reminders.METHOD_ALERT))) {
                        alertAllowed = true;
                    }
                }
                if (!alertAllowed) {
                    throw new IllegalStateException("تقویم انتخاب‌شده یادآور اعلان را نمی‌پذیرد؛ یک تقویم دیگر انتخاب کنید.");
                }
            }
        }
    }

    private static void requirePermission(Context context, String permission) {
        if (context.checkSelfPermission(permission) != PackageManager.PERMISSION_GRANTED) {
            throw new SecurityException("برای ثبت جلسه، دسترسی تقویم را از داخل برنامه فعال کنید.");
        }
    }
}
