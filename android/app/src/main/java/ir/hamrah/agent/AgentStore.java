package ir.hamrah.agent;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONObject;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

/** App-private state; credentials live separately in Android Keystore-backed SecretStore. */
public final class AgentStore {
    private static final Object LOCK = new Object();
    private final SharedPreferences prefs;
    public AgentStore(Context context) {
        prefs = context.getApplicationContext().getSharedPreferences("hamrah_state_v1", Context.MODE_PRIVATE);
    }
    public String notesText() { return prefs.getString("notes", ""); }
    public void setNotesText(String value) { put("notes", value, 65536); }
    public String notesUri() { return prefs.getString("notes_uri", ""); }
    public void setNotesUri(String value) { put("notes_uri", value, 8192); }
    public boolean notesEnabled() { return prefs.getBoolean("notes_enabled", false); }
    public void setNotesEnabled(boolean value) { prefs.edit().putBoolean("notes_enabled", value).apply(); }
    public int notesHour() { return prefs.getInt("notes_hour", 8); }
    public int notesMinute() { return prefs.getInt("notes_minute", 0); }
    public void setNotesTime(int hour, int minute) { setTime("notes", hour, minute); }
    public String lastNotesDate() { return prefs.getString("last_notes_date", ""); }
    public void setLastNotesDate(String value) { put("last_notes_date", value, 32); }
    public boolean mailEnabled() { return prefs.getBoolean("mail_enabled", false); }
    public void setMailEnabled(boolean value) { prefs.edit().putBoolean("mail_enabled", value).apply(); }
    public int mailHour() { return prefs.getInt("mail_hour", 20); }
    public int mailMinute() { return prefs.getInt("mail_minute", 0); }
    public void setMailTime(int hour, int minute) { setTime("mail", hour, minute); }
    public int mailIntervalMinutes() { return prefs.getInt("mail_interval", 180); }
    public void setMailIntervalMinutes(int value) {
        if (value < 60 || value > 1440) throw new IllegalArgumentException("فاصلهٔ بررسی باید بین ۶۰ تا ۱۴۴۰ دقیقه باشد.");
        prefs.edit().putInt("mail_interval", value).apply();
    }
    public String lastMail() { return prefs.getString("last_mail", ""); }
    public void putLastMail(String value) {
        String text = value == null ? "" : value;
        if (text.length() > 100000) text = text.substring(0, 100000);
        prefs.edit().putString("last_mail", text).putLong("last_mail_at", text.isEmpty() ? 0 : System.currentTimeMillis()).apply();
    }
    public boolean aiMailEnabled() { return prefs.getBoolean("ai_mail_enabled", false); }
    public void setAiMailEnabled(boolean value) { prefs.edit().putBoolean("ai_mail_enabled", value).apply(); }
    public String memoryText() { return prefs.getString("memory", ""); }
    public void setMemoryText(String value) { put("memory", value, 4000); }
    public JSONArray meetings() {
        synchronized (LOCK) {
            try { return new JSONArray(prefs.getString("meetings", "[]")); }
            catch (Exception failure) { throw new IllegalStateException("فهرست جلسه‌ها قابل خواندن نیست؛ داده‌ها پاک نشده‌اند."); }
        }
    }
    public void addMeeting(JSONObject meeting) {
        if (meeting == null || meeting.optInt("id", 0) <= 0 || meeting.optLong("whenMillis", 0) <= 0
                || meeting.optString("title", "").trim().isEmpty()) {
            throw new IllegalArgumentException("اطلاعات جلسه کامل نیست.");
        }
        synchronized (LOCK) {
            JSONArray all = meetings();
            if (all.length() >= 1000) throw new IllegalStateException("فهرست جلسه‌ها پر شده است؛ جلسه‌های قدیمی را حذف کنید.");
            for (int i = 0; i < all.length(); i++) {
                JSONObject item = all.optJSONObject(i);
                if (item != null && item.optInt("id") == meeting.optInt("id")) {
                    throw new IllegalArgumentException("شناسهٔ جلسه تکراری است.");
                }
            }
            all.put(meeting);
            if (!prefs.edit().putString("meetings", all.toString()).commit()) {
                throw new IllegalStateException("ذخیرهٔ جلسه در گوشی انجام نشد.");
            }
        }
    }
    public void removeMeeting(int id) {
        synchronized (LOCK) {
            JSONArray all = meetings(), kept = new JSONArray();
            for (int i = 0; i < all.length(); i++) {
                JSONObject item = all.optJSONObject(i);
                if (item != null && item.optInt("id") != id) kept.put(item);
            }
            if (!prefs.edit().putString("meetings", kept.toString()).commit()) {
                throw new IllegalStateException("حذف یادآور محلی انجام نشد.");
            }
        }
    }
    public String logText() { return prefs.getString("log", ""); }
    public void recordLog(String message) {
        if (message == null) return;
        synchronized (LOCK) {
            String safe = message.replace('\n', ' ').replace('\r', ' ');
            if (safe.length() > 240) safe = safe.substring(0, 240);
            String timestamp = ZonedDateTime.now().format(DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm"));
            String value = timestamp + " · " + safe + "\n" + logText();
            String[] lines = value.split("\n");
            StringBuilder bounded = new StringBuilder();
            for (int i = 0; i < Math.min(lines.length, 50); i++) bounded.append(lines[i]).append('\n');
            prefs.edit().putString("log", bounded.toString()).apply();
        }
    }
    private void put(String key, String value, int max) {
        String text = value == null ? "" : value;
        if (text.length() > max) throw new IllegalArgumentException("متن بیش از حد طولانی است.");
        prefs.edit().putString(key, text).apply();
    }
    private void setTime(String prefix, int hour, int minute) {
        if (hour < 0 || hour > 23 || minute < 0 || minute > 59) throw new IllegalArgumentException("ساعت معتبر نیست.");
        prefs.edit().putInt(prefix + "_hour", hour).putInt(prefix + "_minute", minute).apply();
    }
}
