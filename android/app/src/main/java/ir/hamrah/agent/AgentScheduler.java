package ir.hamrah.agent;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.PersistableBundle;
import org.json.JSONArray;
import org.json.JSONObject;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.concurrent.TimeUnit;

/** Installs one future local-time alarm at a time; Android may defer inexact alarms. */
public final class AgentScheduler {
    public static final String ACTION_NOTES = "ir.hamrah.agent.NOTES";
    public static final String ACTION_MAIL = "ir.hamrah.agent.MAIL";
    public static final String ACTION_MEETING = "ir.hamrah.agent.MEETING";
    static final String STATE = "scheduler_state";
    static final int PERIODIC_MAIL_JOB = 2200;
    static final int IMMEDIATE_MAIL_JOB = 2201;
    private static final int NOTES_ALARM = 1001;
    private static final int MAIL_ALARM = 1002;

    private AgentScheduler() {}

    public static void reschedule(Context context) {
        Context app = context.getApplicationContext();
        AgentStore store = new AgentStore(app);
        Notifications.ensureChannels(app);
        if (store.notesEnabled()) {
            scheduleDaily(app, ACTION_NOTES, NOTES_ALARM, store.notesHour(), store.notesMinute());
        } else {
            cancelAlarm(app, ACTION_NOTES, NOTES_ALARM);
            clearDailyPlan(app, ACTION_NOTES);
        }
        JobScheduler jobs = app.getSystemService(JobScheduler.class);
        if (store.mailEnabled() && hasMailConnection(app)) {
            scheduleDaily(app, ACTION_MAIL, MAIL_ALARM, store.mailHour(), store.mailMinute());
            if (jobs != null) {
                long interval = TimeUnit.MINUTES.toMillis(Math.max(60, store.mailIntervalMinutes()));
                JobInfo old = jobs.getPendingJob(PERIODIC_MAIL_JOB);
                if (old == null || !old.isPeriodic() || old.getIntervalMillis() != interval) {
                    JobInfo job = new JobInfo.Builder(PERIODIC_MAIL_JOB,
                        new ComponentName(app, MailJobService.class))
                        .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                        .setPersisted(true)
                        .setPeriodic(interval)
                        .build();
                    jobs.schedule(job);
                }
            }
        } else {
            cancelAlarm(app, ACTION_MAIL, MAIL_ALARM);
            clearDailyPlan(app, ACTION_MAIL);
            if (jobs != null) {
                jobs.cancel(PERIODIC_MAIL_JOB);
                JobInfo immediate = jobs.getPendingJob(IMMEDIATE_MAIL_JOB);
                // Turning scheduled reports off must also remove a daily report
                // that was queued while offline. Keep an explicitly requested sync.
                if (!hasMailConnection(app) || (immediate != null
                        && immediate.getExtras().getBoolean("daily", false))) {
                    jobs.cancel(IMMEDIATE_MAIL_JOB);
                }
            }
        }
        JSONArray meetings = store.meetings();
        for (int i = 0; i < meetings.length(); i++) {
            JSONObject meeting = meetings.optJSONObject(i);
            if (meeting != null) scheduleMeeting(app, meeting);
        }
    }

    private static void scheduleDaily(Context context, String action, int requestCode, int hour, int minute) {
        android.content.SharedPreferences state = context.getSharedPreferences(STATE, Context.MODE_PRIVATE);
        ZoneId zone = ZoneId.systemDefault();
        long now = System.currentTimeMillis();
        long next = nextDailyMillis(hour, minute, now, zone);
        String spec = hour + ":" + minute + "@" + zone.getId();
        long previous = state.getLong(action + ".plannedAt", 0);
        if (spec.equals(state.getString(action + ".plan", "")) && previous > 0 &&
            Instant.ofEpochMilli(previous).atZone(zone).toLocalDate()
                .equals(Instant.ofEpochMilli(now).atZone(zone).toLocalDate())) {
            // Preserve today's still-undelivered alarm if Android deferred its chosen time.
            next = previous;
        }
        state.edit().putLong(action + ".plannedAt", next).putString(action + ".plan", spec).apply();
        scheduleAlarm(context, action, requestCode, next, null);
    }

    static void clearDailyPlan(Context context, String action) {
        context.getSharedPreferences(STATE, Context.MODE_PRIVATE).edit()
            .remove(action + ".plannedAt").remove(action + ".plan").apply();
    }

    /** Returns tomorrow if today's time has passed; resolves DST using Java zone rules. */
    public static long nextDailyMillis(int hour, int minute, long nowMillis, ZoneId zone) {
        int h = Math.max(0, Math.min(23, hour));
        int m = Math.max(0, Math.min(59, minute));
        ZonedDateTime now = Instant.ofEpochMilli(nowMillis).atZone(zone);
        LocalDate date = now.toLocalDate();
        ZonedDateTime candidate = LocalDateTime.of(date, java.time.LocalTime.of(h, m)).atZone(zone);
        if (!candidate.toInstant().isAfter(now.toInstant())) {
            candidate = LocalDateTime.of(date.plusDays(1), java.time.LocalTime.of(h, m)).atZone(zone);
        }
        return candidate.toInstant().toEpochMilli();
    }

    public static void scheduleMeeting(Context context, JSONObject meeting) {
        int id = meeting.optInt("id", -1);
        long when = meeting.optLong("whenMillis", 0);
        if (id <= 0) return;
        cancelAlarm(context, ACTION_MEETING, id);
        long firedFor = context.getSharedPreferences(STATE, Context.MODE_PRIVATE)
            .getLong("meeting_fired_" + id, 0);
        long now = System.currentTimeMillis();
        if (when <= now || firedFor == when) return;
        long lead = TimeUnit.MINUTES.toMillis(Math.max(0, Math.min(10080, meeting.optInt("reminderMinutes", 15))));
        long notifyAt = Math.max(now + 2000, when - lead);
        Intent extra = new Intent().putExtra("meetingId", id);
        scheduleAlarm(context, ACTION_MEETING, id, notifyAt, extra);
    }

    public static void cancelMeeting(Context context, int id) {
        cancelAlarm(context, ACTION_MEETING, id);
        context.getSharedPreferences(STATE, Context.MODE_PRIVATE).edit()
            .remove("meeting_fired_" + id).apply();
    }

    public static void requestMailNow(Context context, boolean notify) {
        requestMail(context, notify, false);
    }

    public static boolean hasMailConnection(Context context) {
        return MailClient.isConnected(context) || YahooClient.isConnected(context);
    }

    /** Call before changing account credentials or clearing a disconnected mailbox's report. */
    public static synchronized void invalidateMailWork(Context context) {
        android.content.SharedPreferences state = context.getSharedPreferences(STATE, Context.MODE_PRIVATE);
        state.edit().putLong("mail_generation", state.getLong("mail_generation", 0) + 1)
            .remove("last_mail_fetch_at").commit();
        JobScheduler jobs = context.getSystemService(JobScheduler.class);
        if (jobs != null) {
            jobs.cancel(PERIODIC_MAIL_JOB);
            jobs.cancel(IMMEDIATE_MAIL_JOB);
        }
    }

    static void requestDailyMail(Context context) {
        requestMail(context, true, true);
    }

    private static void requestMail(Context context, boolean notify, boolean daily) {
        if (!hasMailConnection(context)) return;
        JobScheduler jobs = context.getSystemService(JobScheduler.class);
        if (jobs == null) return;
        JobInfo existing = jobs.getPendingJob(IMMEDIATE_MAIL_JOB);
        if (existing != null) {
            // Retain an already queued daily notification if a manual sync follows it.
            notify |= existing.getExtras().getBoolean("notify", false);
            daily |= existing.getExtras().getBoolean("daily", false);
        }
        PersistableBundle extras = new PersistableBundle();
        extras.putBoolean("notify", notify);
        extras.putBoolean("daily", daily);
        jobs.schedule(new JobInfo.Builder(IMMEDIATE_MAIL_JOB,
            new ComponentName(context, MailJobService.class))
            .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
            .setExtras(extras)
            .setMinimumLatency(1)
            .setBackoffCriteria(TimeUnit.MINUTES.toMillis(5), JobInfo.BACKOFF_POLICY_EXPONENTIAL)
            .build());
    }

    private static PendingIntent alarmIntent(Context context, String action, int requestCode, Intent extras) {
        Intent intent = new Intent(context, AgentReceiver.class).setAction(action);
        // Action + data isolate meeting IDs from the daily alarm request codes.
        intent.setData(android.net.Uri.parse("hamrah://alarm/" + action + "/" + requestCode));
        if (extras != null && extras.getExtras() != null) intent.putExtras(extras.getExtras());
        return PendingIntent.getBroadcast(context, requestCode, intent,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private static void scheduleAlarm(Context context, String action, int requestCode, long at, Intent extras) {
        AlarmManager alarms = context.getSystemService(AlarmManager.class);
        if (alarms != null) {
            alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at,
                alarmIntent(context, action, requestCode, extras));
        }
    }

    private static void cancelAlarm(Context context, String action, int requestCode) {
        AlarmManager alarms = context.getSystemService(AlarmManager.class);
        if (alarms != null) alarms.cancel(alarmIntent(context, action, requestCode, null));
    }
}
