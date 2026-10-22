package ir.hamrah.agent;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import org.json.JSONArray;
import org.json.JSONObject;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/** Receives only explicit internal alarms and Android boot/time changes. */
public final class AgentReceiver extends BroadcastReceiver {
    private static final ExecutorService DISPATCH = Executors.newSingleThreadExecutor();
    // A stalled document provider must neither block the broadcast nor create unbounded readers.
    private static final ExecutorService NOTE_IO = new ThreadPoolExecutor(0, 1, 30,
        TimeUnit.SECONDS, new SynchronousQueue<>());

    @Override public void onReceive(Context context, Intent intent) {
        if (intent == null || intent.getAction() == null) return;
        if (AgentScheduler.ACTION_NOTES.equals(intent.getAction())) {
            AgentScheduler.clearDailyPlan(context, AgentScheduler.ACTION_NOTES);
            PendingResult pending = goAsync();
            Context app = context.getApplicationContext();
            DISPATCH.execute(() -> {
                try {
                    dispatchNotes(app);
                } finally {
                    try { AgentScheduler.reschedule(app); }
                    finally { pending.finish(); }
                }
            });
            return;
        }
        AgentStore store = new AgentStore(context);
        String action = intent.getAction();
        if (AgentScheduler.ACTION_MAIL.equals(action)) {
            AgentScheduler.clearDailyPlan(context, AgentScheduler.ACTION_MAIL);
            if (store.mailEnabled()) AgentScheduler.requestDailyMail(context);
        } else if (AgentScheduler.ACTION_MEETING.equals(action)) {
            int id = intent.getIntExtra("meetingId", -1);
            JSONArray meetings = store.meetings();
            for (int i = 0; i < meetings.length(); i++) {
                JSONObject meeting = meetings.optJSONObject(i);
                if (meeting == null || meeting.optInt("id", -2) != id) continue;
                long when = meeting.optLong("whenMillis", 0);
                long fired = context.getSharedPreferences(AgentScheduler.STATE, Context.MODE_PRIVATE)
                    .getLong("meeting_fired_" + id, 0);
                // Inexact alarms may arrive after the meeting starts; retain a short grace period.
                if (when + 3_600_000 > System.currentTimeMillis() && fired != when &&
                    Notifications.meeting(context, id, meeting.optString("title", "جلسه"))) {
                    context.getSharedPreferences(AgentScheduler.STATE, Context.MODE_PRIVATE).edit()
                        .putLong("meeting_fired_" + id, when).apply();
                    store.recordLog("یادآوری جلسه نمایش داده شد.");
                }
                break;
            }
        }
        AgentScheduler.reschedule(context);
    }

    private static void dispatchNotes(Context context) {
        AgentStore store = new AgentStore(context);
        String today = LocalDate.now(ZoneId.systemDefault()).toString();
        if (!store.notesEnabled() || today.equals(store.lastNotesDate())) return;
        String text = store.notesText();
        String savedSnapshot = text;
        String linked = store.notesUri();
        boolean staleFallback = false;
        if (linked != null && !linked.trim().isEmpty()) {
            Future<String> read = null;
            try {
                read = NOTE_IO.submit(() -> NotesImport.readTextForRefresh(context, Uri.parse(linked)));
                text = read.get(5, TimeUnit.SECONDS);
                // Keep a valid last-known version, including an intentionally emptied file.
                // A user edit/import made during the provider read takes precedence.
                if (linked.equals(store.notesUri()) && savedSnapshot.equals(store.notesText())) {
                    store.setNotesText(text);
                } else {
                    text = store.notesText();
                }
            } catch (Exception unavailable) {
                if (unavailable instanceof InterruptedException) Thread.currentThread().interrupt();
                staleFallback = true;
                store.recordLog("فایل یادداشت بازخوانی نشد؛ یادآوری از آخرین نسخهٔ ذخیره‌شده آماده شد.");
            } finally {
                if (read != null) read.cancel(true);
            }
        }
        if (!store.notesEnabled()) return;
        String content = activeNotes(text);
        if (content.isEmpty()) return;
        if (staleFallback) content = "از آخرین نسخهٔ ذخیره‌شده؛ فایل اصلی بازخوانی نشد:\n" + content;
        if (Notifications.notes(context, content)) {
            store.setLastNotesDate(today);
            store.recordLog("یادآوری کارهای روزانه نمایش داده شد.");
        }
    }

    private static String activeNotes(String text) {
        if (text == null) return "";
        StringBuilder active = new StringBuilder();
        for (String line : text.split("\\r?\\n")) {
            String item = line.trim();
            if (item.isEmpty() || item.equals("کارهای مهم روزانه")) continue;
            if (item.matches("^(?:[-*]\\s*)?\\[[xX✓]\\].*") || item.startsWith("☑") || item.startsWith("✅")) continue;
            if (active.length() > 0) active.append('\n');
            active.append(item);
            if (active.length() > 3200) break;
        }
        return active.toString();
    }
}
