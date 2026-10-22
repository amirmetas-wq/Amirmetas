package ir.hamrah.agent;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.content.ContentUris;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.CalendarContract;
import android.provider.Settings;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.common.api.ApiException;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.DateFormat;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Consumer;

/** Persian, device-first control surface. Network operations always run on a worker thread. */
public final class MainActivity extends Activity {
    private static final int OPEN_NOTE = 4101;
    private static final int CALENDAR_PERMISSION = 4301;
    private static final int NOTIFICATION_PERMISSION = 4302;
    private static final int BG = Color.rgb(10, 19, 33);
    private static final int SURFACE = Color.rgb(19, 34, 52);
    private static final int FIELD = Color.rgb(10, 25, 42);
    private static final int TEXT = Color.rgb(235, 243, 248);
    private static final int MUTED = Color.rgb(160, 181, 199);
    private static final int TEAL = Color.rgb(71, 220, 185);
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private AgentStore store;
    private LinearLayout shell;
    private LinearLayout content;
    private String page = "home";
    private TextView digestView;
    private TextView mailStateView;
    private Runnable pendingCalendarAction;
    private boolean resumed;
    private boolean destroyed;
    private volatile boolean accountBusy;
    private String visibleDigest = "";

    private final Runnable mailRefresh = new Runnable() {
        @Override public void run() {
            if (!resumed || destroyed) return;
            if ("mail".equals(page) && digestView != null) {
                String value = store.lastMail();
                if (!value.equals(visibleDigest)) {
                    visibleDigest = value;
                    digestView.setText(value.isEmpty() ? "هنوز گزارشی آماده نشده است. ابتدا حساب را متصل کنید و «دریافت گزارش» را بزنید." : value);
                }
                if (mailStateView != null) mailStateView.setText(accountStatus());
            }
            handler.postDelayed(this, 2000);
        }
    };

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        store = new AgentStore(this);
        if (state != null) page = state.getString("page", "home");
        applyNotificationSection(getIntent());
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        getWindow().getDecorView().setSystemUiVisibility(0);
        Notifications.ensureChannels(this);
        render();
        AgentScheduler.reschedule(this);
        handleSharedIntent(getIntent());
    }

    @Override protected void onSaveInstanceState(Bundle state) {
        state.putString("page", page);
        super.onSaveInstanceState(state);
    }

    @Override protected void onResume() {
        super.onResume();
        resumed = true;
        handler.removeCallbacks(mailRefresh);
        handler.post(mailRefresh);
    }

    @Override protected void onPause() {
        resumed = false;
        handler.removeCallbacks(mailRefresh);
        super.onPause();
    }

    @Override protected void onDestroy() {
        destroyed = true;
        handler.removeCallbacksAndMessages(null);
        executor.shutdownNow();
        super.onDestroy();
    }

    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        if (applyNotificationSection(intent)) render();
        handleSharedIntent(intent);
    }

    private boolean applyNotificationSection(Intent intent) {
        if (intent == null) return false;
        String destination = intent.getStringExtra("section");
        if ("notes".equals(destination) || "mail".equals(destination)
                || "meetings".equals(destination)) {
            page = destination;
            return true;
        }
        return false;
    }

    private void render() {
        digestView = null;
        mailStateView = null;
        shell = new LinearLayout(this);
        shell.setOrientation(LinearLayout.VERTICAL);
        shell.setBackgroundColor(BG);
        shell.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        shell.setPadding(dp(18), dp(12), dp(18), dp(8));
        shell.setOnApplyWindowInsetsListener((view, insets) -> {
            int top = insets.getSystemWindowInsetTop();
            int bottom = insets.getSystemWindowInsetBottom();
            view.setPadding(dp(18), top + dp(12), dp(18), bottom + dp(8));
            return insets;
        });
        TextView brand = text("همراه", 29, TEAL);
        brand.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        shell.addView(brand);
        shell.addView(text("کارهای کوچک، روزهای مرتب‌تر", 13, MUTED));
        HorizontalScrollView navScroll = new HorizontalScrollView(this);
        navScroll.setHorizontalScrollBarEnabled(false);
        LinearLayout nav = new LinearLayout(this);
        nav.setOrientation(LinearLayout.HORIZONTAL);
        String[] ids = {"home", "notes", "mail", "meetings", "assistant"};
        String[] labels = {"خانه", "کارها", "ایمیل", "جلسه‌ها", "دستیار"};
        for (int i = 0; i < ids.length; i++) {
            final String id = ids[i];
            Button tab = button(labels[i], () -> navigate(id), id.equals(page));
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, dp(46));
            params.setMargins(dp(4), dp(12), 0, dp(12));
            nav.addView(tab, params);
        }
        navScroll.addView(nav);
        shell.addView(navScroll);
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setClipToPadding(false);
        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(0, 0, 0, dp(24));
        scroll.addView(content);
        shell.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        setContentView(shell);
        shell.requestApplyInsets();
        switch (page) {
            case "notes": showNotes(); break;
            case "mail": showMail(); break;
            case "meetings": showMeetings(); break;
            case "assistant": showAssistant(); break;
            default: showHome();
        }
    }

    private void navigate(String destination) {
        View focused = getCurrentFocus();
        if (focused != null) {
            InputMethodManager keyboard = getSystemService(InputMethodManager.class);
            if (keyboard != null) keyboard.hideSoftInputFromWindow(focused.getWindowToken(), 0);
        }
        page = destination;
        render();
    }

    private void showHome() {
        heading("روزت را از همین‌جا شروع کن", "همهٔ زمان‌ها بر اساس ساعت و منطقهٔ زمانی گوشی هستند.");
        LinearLayout daily = card("کارهای مهم روزانه");
        paragraph(daily, store.notesEnabled() ? "یادآوری فعال · " + clock(store.notesHour(), store.notesMinute()) : "یادآوری هنوز فعال نشده است");
        String note = store.notesText();
        paragraph(daily, note.isEmpty() ? "یادداشتت را از Samsung Notes به همراه بفرست، یا اینجا بنویس." : shorten(note, 320));
        action(daily, "مدیریت کارها", () -> navigate("notes"), true);
        LinearLayout mail = card("گزارش ایمیل‌ها");
        paragraph(mail, accountStatus());
        paragraph(mail, store.mailEnabled() ? "گزارش روزانه · " + clock(store.mailHour(), store.mailMinute()) : "گزارش زمان‌بندی‌شده خاموش است");
        action(mail, "حساب‌ها و گزارش", () -> navigate("mail"), false);
        LinearLayout meetings = card("جلسهٔ بعدی");
        JSONObject next = nextMeeting();
        paragraph(meetings, next == null ? "جلسه‌ای برای آینده ثبت نشده است." : next.optString("title") + "\n" + dateTime(next.optLong("whenMillis")));
        action(meetings, "ثبت و مشاهدهٔ جلسه‌ها", () -> navigate("meetings"), false);
        LinearLayout setup = card("آماده‌سازی گوشی");
        paragraph(setup, "اعلان‌ها را روشن کنید. برای تأخیر کمتر در Galaxy S25 Ultra، همراه را از Sleeping apps خارج کنید. محدودیت‌های اندروید ممکن است زمان اجرا را عقب بیندازند.");
        action(setup, "دسترسی اعلان‌ها", this::notificationSettings, false);
        action(setup, "تنظیمات باتری", () -> openSettings(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS), false);
        action(setup, "گزارش اجراهای اخیر", () -> message("گزارش اجرا", emptyFallback(store.logText(), "هنوز اجرایی ثبت نشده است.")), false);
    }

    private void showNotes() {
        heading("کارهای مهم روزانه", "منبع: متن ذخیره‌شده در همراه یا فایل متنی انتخابی شما.");
        LinearLayout importCard = card("یادداشت Samsung Notes");
        paragraph(importCard, "در Samsung Notes، یادداشت «کارهای مهم روزانه» را باز کنید؛ Share ← Text file یا متن ← همراه. بعد از هر تغییر، دوباره آن را به اشتراک بگذارید. همراه به فهرست خصوصی یادداشت‌های سامسونگ دسترسی مستقیم ندارد.");
        action(importCard, "انتخاب فایل متنی", this::selectNoteFile, false);
        paragraph(importCard, store.notesUri().isEmpty() ? "اشتراک‌گذاری، یک نسخهٔ محلی از یادداشت می‌سازد." : "یک فایل متنی متصل شده است. تغییر سند اصلی با تغییر یادداشت Samsung Notes یکسان نیست.");
        if (!store.notesUri().isEmpty()) {
            action(importCard, "خواندن دوبارهٔ فایل متصل", this::refreshLinkedNote, false);
            action(importCard, "قطع اتصال فایل؛ نگه‌داشتن متن", () -> {
                replaceNoteLink("");
                toast("اتصال فایل قطع شد؛ متن فعلی باقی ماند.");
                render();
            }, false);
        }
        LinearLayout editor = card("متن کارها");
        EditText notes = edit("هر کار در یک خط؛ کار انجام‌شده را با [x] علامت بزنید.", false, true);
        notes.setMinLines(7);
        notes.setText(store.notesText());
        editor.addView(notes, blockParams());
        paragraph(editor, "خطوطی که با [x]، ☑ یا ✅ شروع شوند در اعلان روزانه نمایش داده نمی‌شوند.");
        action(editor, "ذخیرهٔ متن کارها", () -> {
            String value = notes.getText().toString().trim();
            if (value.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > NotesImport.MAX_BYTES) {
                message("متن طولانی است", "حداکثر اندازهٔ یادداشت ۶۴ کیلوبایت است.");
                return;
            }
            Runnable save = () -> {
                store.setNotesText(value);
                replaceNoteLink("");
                AgentScheduler.reschedule(this);
                toast("متن کارها ذخیره شد.");
                render();
            };
            if (!store.notesUri().isEmpty()) {
                confirm("ذخیرهٔ ویرایش محلی", "با ویرایش این متن، اتصال فایل قطع می‌شود تا خواندن دوبارهٔ فایل روی ویرایش شما نوشته نشود.", "ذخیره", save);
            } else save.run();
        }, true);
        LinearLayout schedule = card("یادآوری روزانه");
        Switch enabled = toggle("نمایش اعلان کارها", store.notesEnabled());
        schedule.addView(enabled);
        enabled.setOnCheckedChangeListener((view, checked) -> {
            if (checked && store.notesText().trim().isEmpty()) {
                enabled.setChecked(false);
                toast("ابتدا متن کارها را ذخیره کنید.");
                return;
            }
            store.setNotesEnabled(checked);
            if (checked) requestNotificationPermission();
            AgentScheduler.reschedule(this);
        });
        action(schedule, "زمان اعلان · " + clock(store.notesHour(), store.notesMinute()), () -> {
            timePicker(store.notesHour(), store.notesMinute(), (h, m) -> {
                store.setNotesTime(h, m);
                AgentScheduler.reschedule(this);
                render();
            });
        }, false);
        action(schedule, "آزمایش اعلان با متن ذخیره‌شده", () -> {
            if (store.notesText().trim().isEmpty()) { toast("ابتدا متن کارها را ذخیره کنید."); return; }
            if (!requestNotificationPermission()) return;
            Notifications.notes(this, store.notesText());
        }, false);
        paragraph(schedule, "زمان‌بندی دقیق تضمین نمی‌شود. بعد از Force stop، برنامه را دوباره باز کنید.");
    }

    private void showMail() {
        heading("صندوق ورودی، در یک نگاه", "اتصال‌ها فقط برای خواندن هستند؛ ایمیلی ارسال، حذف یا خوانده‌شده نمی‌شود.");
        LinearLayout gmail = card("Gmail");
        mailStateView = text(accountStatus(), 14, MUTED);
        gmail.addView(mailStateView);
        action(gmail, MailClient.isConnected(this) ? "تغییر حساب Gmail" : "اتصال با ورود رسمی Google", this::beginGmailSignIn, true);
        if (MailClient.isConnected(this)) {
            action(gmail, "آزمایش دسترسی Gmail", this::testGmail, false);
            action(gmail, "قطع اتصال Gmail", () -> confirm("قطع اتصال Gmail", "مجوز Google لغو و گزارش ذخیره‌شده پاک می‌شود.", "قطع اتصال", () -> {
                if (accountBusy) return;
                accountBusy = true;
                invalidateMail();
                MailClient.disconnect(this);
                // Revocation is asynchronous. Clear local sign-in too, and only
                // restore scheduling after Google has finished clearing it.
                MailClient.signInClient(this).signOut().addOnCompleteListener(task -> {
                    accountBusy = false;
                    AgentScheduler.reschedule(getApplicationContext());
                    ui(() -> { if ("mail".equals(page)) render(); });
                });
            }), false);
        }
        action(gmail, "راهنمای فعال‌سازی Gmail", () -> message("راه‌اندازی Google", "برای این APK باید Gmail API و OAuth اندروید در Google Cloud با نام بستهٔ ir.hamrah.agent و اثرانگشت گواهی APK تنظیم شود. فقط مجوز gmail.readonly درخواست می‌شود. رمز Gmail را داخل همراه یا این گفتگو وارد نکنید. راهنمای همراه APK جزئیات تنظیم را دارد."), false);

        LinearLayout yahoo = card("Yahoo");
        paragraph(yahoo, YahooClient.isConnected(this) ? "پیکربندی شده: " + YahooClient.email(this) : "هنوز حسابی متصل نشده است.");
        EditText address = edit("نشانی Yahoo / ymail / rocketmail", false, false);
        address.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
        address.setTextDirection(View.TEXT_DIRECTION_LTR);
        address.setText(YahooClient.email(this));
        yahoo.addView(address, blockParams());
        EditText password = edit("رمز مخصوص برنامهٔ Yahoo (App password)", true, false);
        yahoo.addView(password, blockParams());
        paragraph(yahoo, "از بخش Account security در Yahoo یک App password بسازید. رمز اصلی ایمیل را وارد نکنید. این رمز با Android Keystore روی همین گوشی رمزگذاری می‌شود.");
        action(yahoo, "ذخیره و آزمایش اتصال Yahoo", () -> {
            if (accountBusy) { toast("آزمایش اتصال در حال اجراست."); return; }
            String email = address.getText().toString();
            String appPassword = password.getText().toString();
            if (appPassword.trim().isEmpty()) { toast("رمز مخصوص برنامه را وارد کنید."); return; }
            try {
                invalidateMail();
                YahooClient.configure(this, email, appPassword);
                password.setText("");
                AgentScheduler.reschedule(this);
                testYahoo();
            } catch (Exception failure) { safeError(failure, "ذخیرهٔ اتصال Yahoo انجام نشد."); }
        }, true);
        if (YahooClient.isConnected(this)) {
            action(yahoo, "آزمایش اتصال فعلی Yahoo", this::testYahoo, false);
            action(yahoo, "قطع اتصال Yahoo", () -> confirm("قطع اتصال Yahoo", "رمز مخصوص برنامه از گوشی و گزارش ذخیره‌شده پاک می‌شوند. برای لغو کامل، آن App password را در حساب Yahoo نیز حذف کنید.", "قطع اتصال", () -> {
                if (accountBusy) { toast("ابتدا منتظر پایان آزمایش اتصال بمانید."); return; }
                try {
                    invalidateMail();
                    YahooClient.disconnect(this);
                    AgentScheduler.reschedule(this);
                    render();
                } catch (Exception failure) { safeError(failure, "قطع اتصال انجام نشد."); }
            }), false);
        }
        LinearLayout scheduling = card("برنامهٔ گزارش‌ها");
        Switch enabled = toggle("بررسی منظم و گزارش روزانه", store.mailEnabled());
        scheduling.addView(enabled);
        enabled.setOnCheckedChangeListener((view, checked) -> {
            if (checked && !AgentScheduler.hasMailConnection(this)) {
                enabled.setChecked(false);
                toast("ابتدا دست‌کم یک حساب ایمیل را متصل کنید.");
                return;
            }
            store.setMailEnabled(checked);
            if (checked) requestNotificationPermission();
            AgentScheduler.reschedule(this);
        });
        action(scheduling, "زمان گزارش روزانه · " + clock(store.mailHour(), store.mailMinute()), () -> timePicker(store.mailHour(), store.mailMinute(), (h, m) -> {
            store.setMailTime(h, m); AgentScheduler.reschedule(this); render();
        }), false);
        action(scheduling, "فاصلهٔ بررسی · " + store.mailIntervalMinutes() + " دقیقه", () -> {
            String[] labels = {"هر یک ساعت", "هر سه ساعت", "هر شش ساعت", "هر دوازده ساعت"};
            int[] values = {60, 180, 360, 720};
            new AlertDialog.Builder(this).setTitle("فاصلهٔ بررسی ایمیل").setItems(labels, (dialog, which) -> {
                store.setMailIntervalMinutes(values[which]);
                AgentScheduler.reschedule(this); render();
            }).setNegativeButton("بستن", null).show();
        }, false);
        paragraph(scheduling, "در هر گزارش حداکثر ۳۰ پیام اخیر هر حساب از ۴۸ ساعت گذشته بررسی می‌شود. زمان اجرای پس‌زمینه ممکن است با محدودیت باتری عقب بیفتد.");
        action(scheduling, "دریافت گزارش همین حالا", () -> {
            if (!AgentScheduler.hasMailConnection(this)) { toast("ابتدا یک حساب ایمیل متصل کنید."); return; }
            AgentScheduler.requestMailNow(this, false);
            toast("درخواست دریافت گزارش ثبت شد؛ با اتصال اینترنت، گزارش این صفحه به‌روز می‌شود.");
        }, true);
        LinearLayout report = card("آخرین گزارش");
        visibleDigest = store.lastMail();
        digestView = text(visibleDigest.isEmpty() ? "هنوز گزارشی آماده نشده است. ابتدا حساب را متصل کنید و «دریافت گزارش» را بزنید." : visibleDigest, 15, TEXT);
        digestView.setTextIsSelectable(true);
        report.addView(digestView);
    }

    private void showMeetings() {
        heading("جلسه‌ها و یادآورها", "جلسه در تقویم انتخابی ذخیره می‌شود و همراه هم یک اعلان برنامه‌ریزی می‌کند.");
        LinearLayout form = card("جلسهٔ جدید");
        EditText title = edit("عنوان جلسه", false, false);
        form.addView(title, blockParams());
        Calendar chosen = Calendar.getInstance();
        chosen.add(Calendar.HOUR_OF_DAY, 1);
        chosen.set(Calendar.SECOND, 0);
        chosen.set(Calendar.MILLISECOND, 0);
        Button dateButton = button("تاریخ میلادی · " + dateOnly(chosen.getTimeInMillis()), () -> {}, false);
        dateButton.setOnClickListener(view -> {
            DatePickerDialog dialog = new DatePickerDialog(this, (picker, year, month, day) -> {
                chosen.set(Calendar.YEAR, year); chosen.set(Calendar.MONTH, month); chosen.set(Calendar.DAY_OF_MONTH, day);
                dateButton.setText("تاریخ میلادی · " + dateOnly(chosen.getTimeInMillis()));
            }, chosen.get(Calendar.YEAR), chosen.get(Calendar.MONTH), chosen.get(Calendar.DAY_OF_MONTH));
            dialog.getDatePicker().setMinDate(System.currentTimeMillis() - 86_400_000L);
            dialog.show();
        });
        form.addView(dateButton, blockParams());
        Button timeButton = button("ساعت · " + clock(chosen.get(Calendar.HOUR_OF_DAY), chosen.get(Calendar.MINUTE)), () -> {}, false);
        timeButton.setOnClickListener(view -> timePicker(chosen.get(Calendar.HOUR_OF_DAY), chosen.get(Calendar.MINUTE), (hour, minute) -> {
            chosen.set(Calendar.HOUR_OF_DAY, hour); chosen.set(Calendar.MINUTE, minute);
            timeButton.setText("ساعت · " + clock(hour, minute));
        }));
        form.addView(timeButton, blockParams());
        paragraph(form, "تقویم این انتخاب‌گر میلادی است؛ منطقهٔ زمانی: " + ZoneId.systemDefault().getId());
        EditText duration = number("مدت جلسه، دقیقه", "60");
        form.addView(label("مدت جلسه به دقیقه")); form.addView(duration, blockParams());
        EditText reminder = number("یادآوری چند دقیقه قبل؟", "15");
        form.addView(label("یادآوری چند دقیقه قبل؟")); form.addView(reminder, blockParams());
        action(form, "انتخاب تقویم و ثبت جلسه", () -> {
            String value = title.getText().toString().trim();
            int minutes = parseInt(duration.getText().toString(), -1);
            int before = parseInt(reminder.getText().toString(), -1);
            if (value.isEmpty() || value.length() > 200) { toast("عنوانی بین ۱ تا ۲۰۰ نویسه وارد کنید."); return; }
            if (chosen.getTimeInMillis() <= System.currentTimeMillis()) { toast("زمان جلسه باید در آینده باشد."); return; }
            if (minutes < 5 || minutes > 1440 || before < 0 || before > 10080) { toast("مدت جلسه ۵ تا ۱۴۴۰ دقیقه و فاصلهٔ یادآوری صفر تا ۱۰۰۸۰ دقیقه باشد."); return; }
            proposeMeeting(value, chosen.getTimeInMillis(), minutes, before);
        }, true);
        LinearLayout list = card("جلسه‌های ثبت‌شده");
        JSONArray meetings = store.meetings();
        if (meetings.length() == 0) paragraph(list, "هنوز جلسه‌ای ثبت نشده است.");
        for (int i = 0; i < meetings.length(); i++) {
            JSONObject meeting = meetings.optJSONObject(i);
            if (meeting == null) continue;
            TextView name = text(meeting.optString("title", "جلسه"), 17, TEXT);
            name.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
            list.addView(name, blockParams());
            paragraph(list, dateTime(meeting.optLong("whenMillis")) + "\nیادآوری " + meeting.optInt("reminderMinutes", 15) + " دقیقه قبل");
            action(list, "نمایش در تقویم", () -> {
                long eventId = meeting.optLong("calendarEventId", -1);
                if (eventId < 0) return;
                try {
                    Intent intent = new Intent(Intent.ACTION_VIEW,
                            ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, eventId));
                    intent.putExtra("beginTime", meeting.optLong("whenMillis"));
                    startActivity(intent);
                } catch (Exception failure) { toast("برنامهٔ تقویم برای نمایش این جلسه در دسترس نیست."); }
            }, false);
            action(list, "حذف از تقویم و همراه", () -> confirm("حذف جلسه", "جلسهٔ «" + meeting.optString("title") + "» از تقویم و همراه حذف شود؟", "حذف", () -> withCalendarPermission(() -> deleteMeeting(meeting))), false);
        }
    }

    private void showAssistant() {
        heading("دستیار و حافظه", "درخواست شما را به پیشنهاد قابل‌بررسی تبدیل می‌کند؛ ثبت جلسه و ترجیح با تأیید شما انجام می‌شود.");
        LinearLayout prompt = card("درخواستت را بنویس");
        EditText instruction = edit("مثلاً: برای فردا ساعت ۱۰ یک جلسهٔ یک‌ساعته بگذار، ۱۵ دقیقه قبل یادآوری کن.", false, true);
        instruction.setMinLines(3);
        prompt.addView(instruction, blockParams());
        paragraph(prompt, "درخواست و ترجیحات ذخیره‌شده برای پردازش به OpenAI فرستاده می‌شوند. قابلیت‌های فعلی: پیشنهاد جلسه و ذخیرهٔ ترجیح صریح شما.");
        action(prompt, "بررسی درخواست با هوش مصنوعی", () -> {
            String value = instruction.getText().toString().trim();
            if (value.isEmpty()) { toast("ابتدا درخواستتان را بنویسید."); return; }
            if (!aiConfigured()) { message("اتصال هوش مصنوعی", "ابتدا کلید API خودتان را در بخش پایین وارد کنید. اشتراک ChatGPT جای اعتبار API را نمی‌گیرد."); return; }
            toast("در حال بررسی درخواست…");
            executor.submit(() -> {
                try {
                    JSONObject plan = AiClient.plan(getApplicationContext(), value);
                    ui(() -> showPlan(plan));
                } catch (Exception failure) { ui(() -> safeError(failure, "درخواست هوش مصنوعی کامل نشد؛ اتصال و تنظیمات را بررسی کنید.")); }
            });
        }, true);

        LinearLayout memory = card("ترجیحات و درس‌آموخته‌ها");
        paragraph(memory, "اصلاح‌ها و ترجیحات دلخواهتان را اینجا نگه دارید؛ مثلاً «گزارش‌ها کوتاه باشند» یا «جلسه‌های کاری ۳۰ دقیقه باشند». این حافظه قابل‌مشاهده و حذف است؛ مدل روی گوشی آموزش مجدد نمی‌بیند.");
        EditText preferences = edit("ترجیحات شخصی شما", false, true);
        preferences.setMinLines(4);
        preferences.setText(store.memoryText());
        memory.addView(preferences, blockParams());
        action(memory, "ذخیرهٔ ترجیحات", () -> {
            String value = preferences.getText().toString().trim();
            if (value.length() > 4000) { toast("ترجیحات حداکثر ۴۰۰۰ نویسه باشند."); return; }
            store.setMemoryText(value);
            toast("ترجیحات ذخیره شدند.");
        }, true);
        action(memory, "پاک‌کردن حافظهٔ ترجیحات", () -> confirm("پاک‌کردن حافظه", "فقط ترجیحات و درس‌آموخته‌های محلی حذف می‌شوند؛ جلسه‌ها و کارها باقی می‌مانند.", "پاک‌کردن", () -> {
            store.setMemoryText(""); render();
        }), false);

        LinearLayout connection = card("اتصال هوش مصنوعی");
        paragraph(connection, aiConfigured() ? "کلید API روی گوشی ذخیره شده است." : "کلید API هنوز تنظیم نشده یا قابل بازیابی نیست. یادآوری و گزارش اولیهٔ ایمیل بدون هوش مصنوعی ابری کار می‌کنند.");
        EditText key = edit("کلید OpenAI API؛ برای تغییر وارد کنید", true, false);
        connection.addView(key, blockParams());
        EditText model = edit("نام مدل", false, false);
        model.setTextDirection(View.TEXT_DIRECTION_LTR);
        model.setText(AiClient.model(this));
        connection.addView(label("مدل")); connection.addView(model, blockParams());
        paragraph(connection, "کلید داخل Android Keystore رمزگذاری می‌شود و برای درخواست‌ها به OpenAI استفاده می‌شود. مصرف API به اعتبار حساب خودتان نیاز دارد؛ کلید یا رمز را در چت نفرستید.");
        action(connection, "ذخیرهٔ تنظیمات هوش مصنوعی", () -> {
            try {
                AiClient.setModel(this, model.getText().toString());
                String value = key.getText().toString().trim();
                if (!value.isEmpty()) new SecretStore(this).setApiKey(value);
                key.setText("");
                toast("تنظیمات ذخیره شدند.");
                render();
            } catch (Exception failure) { safeError(failure, "تنظیمات ذخیره نشدند."); }
        }, true);
        action(connection, "حذف کلید API از گوشی", () -> confirm("حذف کلید", "کلید از گوشی حذف و خلاصه‌سازی ابری ایمیل خاموش می‌شود. برای لغو خود کلید، به حساب OpenAI مراجعه کنید.", "حذف کلید", () -> {
            try { new SecretStore(this).clear(); store.setAiMailEnabled(false); render(); }
            catch (Exception failure) { safeError(failure, "حذف کلید انجام نشد."); }
        }), false);
        Switch aiMail = toggle("خلاصه‌سازی ابری گزارش ایمیل‌ها", store.aiMailEnabled());
        connection.addView(aiMail);
        aiMail.setOnCheckedChangeListener((view, checked) -> {
            if (!checked) { store.setAiMailEnabled(false); return; }
            aiMail.setChecked(false);
            if (!aiConfigured()) { toast("ابتدا کلید API را ذخیره کنید."); return; }
            confirm("اجازهٔ ارسال گزیدهٔ ایمیل به OpenAI", "با فعال‌کردن، موضوع، فرستنده و گزیده‌های دریافت‌شدهٔ ایمیل به همراه ترجیحات شما برای خلاصه‌سازی به OpenAI فرستاده می‌شوند. متن کامل و پیوست‌ها ارسال نمی‌شوند. فعال شود؟", "فعال‌کردن", () -> {
                store.setAiMailEnabled(true); render();
            });
        });
        paragraph(connection, "این گزینه پیش‌فرض خاموش است. خاموش بودن آن، گزارش اولیه را روی گوشی نگه می‌دارد.");
        LinearLayout future = card("گسترش قابلیت‌ها");
        paragraph(future, "ترجیحات شما در درخواست‌های بعدی استفاده می‌شوند. انجام کارهای جدید، اتصال به سرویس‌های تازه و تغییر رفتار اجرایی به پیاده‌سازی و به‌روزرسانی برنامه نیاز دارد؛ همراه خودسرانه کد یا دسترسی جدید نصب نمی‌کند.");
    }

    private void showPlan(JSONObject plan) {
        String action = plan.optString("action");
        if ("create_meeting".equals(action)) {
            try {
                long start = OffsetDateTime.parse(plan.getString("start_iso")).toInstant().toEpochMilli();
                proposeMeeting(plan.getString("title"), start,
                        plan.getInt("duration_minutes"), plan.getInt("reminder_minutes"));
            } catch (Exception failure) { message("زمان جلسه روشن نیست", "تاریخ و ساعت دقیق را بنویسید و دوباره تلاش کنید."); }
        } else if ("update_preference".equals(action)) {
            String preference = plan.optString("preference_text").trim();
            confirm("ذخیرهٔ این ترجیح؟", preference, "ذخیره", () -> {
                String existing = store.memoryText().trim();
                String updated = existing.isEmpty() ? preference : existing + "\n" + preference;
                if (updated.length() > 4000) { message("حافظه پر شده است", "ابتدا بخشی از ترجیحات قبلی را کوتاه یا حذف کنید."); return; }
                store.setMemoryText(updated);
                toast("ترجیح با تأیید شما ذخیره شد.");
                render();
            });
        } else message("توضیح دستیار", emptyFallback(plan.optString("explanation"), "این کار هنوز پشتیبانی نمی‌شود؛ قابلیت‌های فعلی ثبت جلسه و ذخیرهٔ ترجیحات هستند."));
    }

    private void proposeMeeting(String title, long start, int duration, int reminder) {
        withCalendarPermission(() -> chooseCalendar(choice -> {
            String details = title + "\n\n" + dateTime(start) + "\nمدت: " + duration + " دقیقه"
                    + "\nیادآوری: " + reminder + " دقیقه قبل\nتقویم: " + choice.name
                    + "\nمنطقهٔ زمانی: " + ZoneId.systemDefault().getId();
            confirm("تأیید ثبت جلسه", details, "ثبت در تقویم", () -> saveMeeting(choice.id, title, start, duration, reminder));
        }));
    }

    private void chooseCalendar(Consumer<CalendarHelper.CalendarChoice> selected) {
        executor.submit(() -> {
            try {
                List<CalendarHelper.CalendarChoice> calendars = CalendarHelper.writableCalendars(getApplicationContext());
                String[] labels = new String[calendars.size()];
                for (int i = 0; i < labels.length; i++) labels[i] = calendars.get(i).toString();
                ui(() -> new AlertDialog.Builder(this).setTitle("تقویم مقصد")
                        .setItems(labels, (dialog, which) -> selected.accept(calendars.get(which)))
                        .setNegativeButton("انصراف", null).show());
            } catch (Exception failure) { ui(() -> safeError(failure, "تقویم قابل‌نوشتن پیدا نشد؛ حساب و دسترسی تقویم را بررسی کنید.")); }
        });
    }

    private void saveMeeting(long calendarId, String title, long start, int duration, int reminder) {
        if (start <= System.currentTimeMillis()) { toast("زمان پیشنهادی گذشته است؛ تاریخ را دوباره انتخاب کنید."); return; }
        requestNotificationPermission();
        toast("در حال ثبت جلسه…");
        executor.submit(() -> {
            long eventId = -1;
            try {
                JSONArray existing = store.meetings();
                for (int i = 0; i < existing.length(); i++) {
                    JSONObject item = existing.optJSONObject(i);
                    if (item != null && item.optLong("whenMillis") == start
                            && title.equals(item.optString("title"))) {
                        ui(() -> message("جلسه قبلاً ثبت شده است", "جلسه‌ای با همین عنوان و زمان در همراه وجود دارد."));
                        return;
                    }
                }
                eventId = CalendarHelper.insert(getApplicationContext(), calendarId, title, start, duration, reminder);
                int id = newMeetingId(existing);
                JSONObject meeting = new JSONObject().put("id", id).put("title", title)
                        .put("whenMillis", start).put("durationMinutes", duration)
                        .put("reminderMinutes", reminder).put("calendarEventId", eventId)
                        .put("calendarId", calendarId);
                try {
                    store.addMeeting(meeting);
                } catch (Exception localFailure) {
                    getContentResolver().delete(ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, eventId), null, null);
                    eventId = -1;
                    throw localFailure;
                }
                AgentScheduler.scheduleMeeting(getApplicationContext(), meeting);
                store.recordLog("جلسه در تقویم و همراه ثبت شد.");
                ui(() -> { toast("جلسه و یادآور ثبت شدند."); navigate("meetings"); });
            } catch (Exception failure) {
                final boolean hasCalendarEvent = eventId >= 0;
                ui(() -> {
                    if (hasCalendarEvent) message("ثبت کامل نشد", "ممکن است جلسه در تقویم ثبت شده باشد؛ قبل از تلاش دوباره، تقویم را بررسی کنید.");
                    else safeError(failure, "ثبت جلسه انجام نشد؛ دسترسی و تقویم مقصد را بررسی کنید.");
                });
            }
        });
    }

    private void deleteMeeting(JSONObject meeting) {
        executor.submit(() -> {
            try {
                long eventId = meeting.optLong("calendarEventId", -1);
                if (eventId >= 0) getContentResolver().delete(ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, eventId), null, null);
                int id = meeting.optInt("id");
                AgentScheduler.cancelMeeting(getApplicationContext(), id);
                store.removeMeeting(id);
                ui(() -> { toast("جلسه حذف شد."); render(); });
            } catch (Exception failure) { ui(() -> safeError(failure, "حذف جلسه انجام نشد؛ دسترسی تقویم را بررسی کنید.")); }
        });
    }

    private void beginGmailSignIn() {
        if (accountBusy) { toast("منتظر پایان آزمایش اتصال بمانید."); return; }
        try {
            accountBusy = true;
            invalidateMail();
            startActivityForResult(MailClient.signInClient(this).getSignInIntent(), MailClient.SIGN_IN_REQUEST);
        } catch (Exception failure) {
            accountBusy = false;
            message("اتصال Google", "ورود رسمی Google در این گوشی در دسترس نیست؛ Google Play services و تنظیم OAuth برنامه را بررسی کنید.");
        }
    }

    private void testGmail() {
        if (accountBusy) { toast("آزمایش اتصال در حال اجراست."); return; }
        accountBusy = true;
        long generation = mailGeneration();
        toast("در حال آزمایش دسترسی Gmail…");
        executor.submit(() -> {
            try {
                String report = MailClient.summarize(getApplicationContext());
                synchronized (AgentScheduler.class) {
                    if (generation == mailGeneration()) store.putLastMail(report);
                }
                ui(() -> { toast("اتصال Gmail تأیید شد."); if ("mail".equals(page)) render(); });
            } catch (MailClient.AuthorizationRequiredException permission) {
                ui(() -> {
                    try { startActivityForResult(permission.getIntent(), MailClient.AUTHORIZATION_REQUEST); }
                    catch (Exception ignored) { message("مجوز Google", "مجوز خواندن Gmail را با اتصال دوبارهٔ حساب تأیید کنید."); }
                });
            } catch (Exception failure) { ui(() -> safeError(failure, "آزمایش Gmail کامل نشد؛ تنظیم OAuth و اینترنت را بررسی کنید.")); }
            finally { accountBusy = false; }
        });
    }

    private void testYahoo() {
        if (accountBusy) { toast("آزمایش اتصال در حال اجراست."); return; }
        accountBusy = true;
        long generation = mailGeneration();
        toast("در حال آزمایش اتصال Yahoo…");
        executor.submit(() -> {
            try {
                String report = YahooClient.summarize(getApplicationContext());
                synchronized (AgentScheduler.class) {
                    if (generation == mailGeneration()) store.putLastMail(report);
                }
                ui(() -> { toast("اتصال Yahoo تأیید شد."); if ("mail".equals(page)) render(); });
            } catch (Exception failure) { ui(() -> safeError(failure, "آزمایش Yahoo کامل نشد؛ اینترنت و رمز مخصوص برنامه را بررسی کنید.")); }
            finally { accountBusy = false; }
        });
    }

    private void invalidateMail() {
        AgentScheduler.invalidateMailWork(this);
        store.putLastMail("");
    }

    private long mailGeneration() {
        return getSharedPreferences(AgentScheduler.STATE, MODE_PRIVATE).getLong("mail_generation", 0);
    }

    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request == OPEN_NOTE && result == RESULT_OK && data != null) {
            importNote(data, true);
        } else if (request == MailClient.SIGN_IN_REQUEST) {
            accountBusy = false;
            if (result != RESULT_OK) { toast("ورود Google انجام نشد."); AgentScheduler.reschedule(this); return; }
            try {
                GoogleSignIn.getSignedInAccountFromIntent(data).getResult(ApiException.class);
                AgentScheduler.reschedule(this);
                navigate("mail");
                testGmail();
            } catch (Exception failure) { message("ورود Gmail کامل نشد", "تنظیم OAuth اندروید، اثرانگشت گواهی APK و Gmail API را بررسی کنید. رمز حساب را داخل برنامه وارد نکنید."); }
        } else if (request == MailClient.AUTHORIZATION_REQUEST) {
            if (result == RESULT_OK) testGmail();
            else toast("مجوز Gmail تأیید نشد.");
        }
    }

    private void selectNoteFile() {
        Intent picker = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        picker.addCategory(Intent.CATEGORY_OPENABLE);
        picker.setType("text/*");
        picker.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        try { startActivityForResult(picker, OPEN_NOTE); }
        catch (Exception failure) { toast("انتخاب‌گر فایل در این گوشی در دسترس نیست؛ متن را از یادداشت به اشتراک بگذارید."); }
    }

    private void handleSharedIntent(Intent intent) {
        if (intent == null) return;
        String action = intent.getAction();
        if (Intent.ACTION_SEND.equals(action) || Intent.ACTION_SEND_MULTIPLE.equals(action) || Intent.ACTION_VIEW.equals(action)) {
            // Remove the launch payload from the Activity to avoid importing it on recreation.
            setIntent(new Intent(this, MainActivity.class));
            importNote(intent, false);
        }
    }

    private void importNote(Intent incoming, boolean mayLink) {
        executor.submit(() -> {
            try {
                String imported = NotesImport.extractText(getApplicationContext(), incoming);
                if (imported == null) return;
                ui(() -> {
                    String preview = shorten(imported, 2000);
                    String description = "این متن جایگزین متن فعلی «کارهای مهم روزانه» می‌شود:\n\n" + preview;
                    confirm("واردکردن یادداشت", description, "ذخیرهٔ این متن", () -> {
                        store.setNotesText(imported);
                        boolean persistent = mayLink && NotesImport.persistReadGrant(this, incoming);
                        replaceNoteLink(persistent && incoming.getData() != null ? incoming.getData().toString() : "");
                        AgentScheduler.reschedule(this);
                        toast(persistent ? "متن ذخیره و فایل متصل شد." : "یک نسخهٔ محلی از یادداشت ذخیره شد.");
                        navigate("notes");
                    });
                });
            } catch (Exception failure) { ui(() -> safeError(failure, "خواندن یادداشت انجام نشد؛ آن را به‌صورت متن ساده به اشتراک بگذارید.")); }
        });
    }

    private void refreshLinkedNote() {
        String uri = store.notesUri();
        if (uri.isEmpty()) return;
        executor.submit(() -> {
            try {
                String updated = NotesImport.readTextForRefresh(getApplicationContext(), Uri.parse(uri));
                ui(() -> confirm("به‌روزرسانی از فایل متصل", shorten(updated, 2000), "جایگزینی متن", () -> {
                    store.setNotesText(updated); AgentScheduler.reschedule(this); render();
                }));
            } catch (Exception failure) { ui(() -> safeError(failure, "فایل متصل قابل‌خواندن نیست؛ دوباره آن را انتخاب کنید.")); }
        });
    }

    /** Drop a previously granted file capability when the user disconnects it. */
    private void replaceNoteLink(String next) {
        String previous = store.notesUri();
        store.setNotesUri(next);
        if (!previous.isEmpty() && !previous.equals(next)) {
            try {
                getContentResolver().releasePersistableUriPermission(Uri.parse(previous),
                        Intent.FLAG_GRANT_READ_URI_PERMISSION);
            } catch (SecurityException | IllegalArgumentException ignored) {
                // A provider may already have revoked the old grant.
            }
        }
    }

    private void withCalendarPermission(Runnable action) {
        if (checkSelfPermission(Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED
                && checkSelfPermission(Manifest.permission.WRITE_CALENDAR) == PackageManager.PERMISSION_GRANTED) {
            action.run();
        } else {
            pendingCalendarAction = action;
            requestPermissions(new String[]{Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR}, CALENDAR_PERMISSION);
        }
    }

    private boolean requestNotificationPermission() {
        if (Build.VERSION.SDK_INT < 33 || checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) return true;
        requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, NOTIFICATION_PERMISSION);
        return false;
    }

    private void notificationSettings() {
        Intent intent = new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName());
        try { startActivity(intent); } catch (Exception ignored) { openSettings(Settings.ACTION_APPLICATION_DETAILS_SETTINGS); }
    }

    @Override public void onRequestPermissionsResult(int request, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(request, permissions, results);
        if (request == CALENDAR_PERMISSION) {
            Runnable next = pendingCalendarAction;
            pendingCalendarAction = null;
            boolean granted = checkSelfPermission(Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED
                    && checkSelfPermission(Manifest.permission.WRITE_CALENDAR) == PackageManager.PERMISSION_GRANTED;
            if (granted && next != null) next.run();
            else message("دسترسی تقویم فعال نشد", "ثبت جلسه به دسترسی خواندن و نوشتن تقویم نیاز دارد. می‌توانید آن را از تنظیمات برنامه فعال کنید.");
        } else if (request == NOTIFICATION_PERMISSION) {
            if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                toast("برای دیدن یادآوری‌ها، اعلان همراه را در تنظیمات برنامه روشن کنید.");
            }
        }
    }

    private void openSettings(String action) {
        try {
            Intent intent = new Intent(action);
            if (Settings.ACTION_APPLICATION_DETAILS_SETTINGS.equals(action)) intent.setData(Uri.parse("package:" + getPackageName()));
            startActivity(intent);
        } catch (Exception ignored) { toast("این بخش از تنظیمات در دسترس نیست؛ تنظیمات برنامهٔ همراه را در گوشی باز کنید."); }
    }

    private String accountStatus() {
        return "Gmail: " + (MailClient.isConnected(this) ? "متصل" : "متصل نیست")
                + "   •   Yahoo: " + (YahooClient.isConnected(this) ? "پیکربندی شده" : "متصل نیست");
    }

    private boolean aiConfigured() {
        try { return AiClient.isConfigured(this); }
        catch (RuntimeException unavailable) { return false; }
    }

    private JSONObject nextMeeting() {
        JSONObject next = null;
        JSONArray meetings = store.meetings();
        long now = System.currentTimeMillis();
        for (int i = 0; i < meetings.length(); i++) {
            JSONObject item = meetings.optJSONObject(i);
            if (item == null || item.optLong("whenMillis") <= now) continue;
            if (next == null || item.optLong("whenMillis") < next.optLong("whenMillis")) next = item;
        }
        return next;
    }

    private int newMeetingId(JSONArray existing) {
        while (true) {
            int candidate = ThreadLocalRandom.current().nextInt(10000, Integer.MAX_VALUE);
            boolean duplicate = false;
            for (int i = 0; i < existing.length(); i++) {
                JSONObject meeting = existing.optJSONObject(i);
                if (meeting != null && meeting.optInt("id") == candidate) duplicate = true;
            }
            if (!duplicate) return candidate;
        }
    }

    private void heading(String title, String subtitle) {
        TextView heading = text(title, 23, TEXT);
        heading.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        content.addView(heading, blockParams());
        TextView description = text(subtitle, 14, MUTED);
        content.addView(description, blockParams());
    }

    private LinearLayout card(String title) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(17), dp(17), dp(17), dp(17));
        card.setBackground(background(SURFACE, 18, Color.rgb(31, 52, 70)));
        LinearLayout.LayoutParams params = blockParams();
        params.topMargin = dp(12);
        content.addView(card, params);
        TextView heading = text(title, 18, TEAL);
        heading.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        card.addView(heading, blockParams());
        return card;
    }

    private TextView text(String value, int size, int color) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        view.setGravity(Gravity.START);
        view.setTextDirection(View.TEXT_DIRECTION_FIRST_STRONG_RTL);
        view.setLineSpacing(dp(4), 1.08f);
        return view;
    }

    private TextView label(String value) { return text(value, 13, MUTED); }
    private void paragraph(LinearLayout parent, String value) { parent.addView(text(value, 14, MUTED), blockParams()); }

    private EditText edit(String hint, boolean secret, boolean multiline) {
        EditText input = new EditText(this);
        input.setTextSize(15);
        input.setTextColor(TEXT);
        input.setHintTextColor(MUTED);
        input.setHint(hint);
        input.setPadding(dp(12), dp(12), dp(12), dp(12));
        input.setBackground(background(FIELD, 10, Color.rgb(44, 67, 86)));
        input.setBackgroundTintList(null);
        input.setGravity(Gravity.TOP | Gravity.START);
        input.setTextDirection(secret ? View.TEXT_DIRECTION_LTR : View.TEXT_DIRECTION_FIRST_STRONG_RTL);
        input.setSingleLine(!multiline);
        if (multiline) {
            input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        } else if (secret) {
            input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
            input.setSaveEnabled(false);
            input.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO);
        } else input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        return input;
    }

    private EditText number(String hint, String initial) {
        EditText value = edit(hint, false, false);
        value.setInputType(InputType.TYPE_CLASS_NUMBER);
        value.setText(initial);
        return value;
    }

    private Switch toggle(String title, boolean checked) {
        Switch control = new Switch(this);
        control.setText(title);
        control.setTextSize(15);
        control.setTextColor(TEXT);
        control.setPadding(0, dp(10), 0, dp(10));
        control.setChecked(checked);
        return control;
    }

    private Button button(String label, Runnable callback, boolean primary) {
        Button button = new Button(this);
        button.setText(label);
        button.setTextSize(14);
        button.setAllCaps(false);
        button.setTextColor(primary ? BG : TEXT);
        button.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        button.setMinHeight(dp(46));
        button.setMinimumHeight(dp(46));
        button.setPadding(dp(14), dp(8), dp(14), dp(8));
        button.setBackground(background(primary ? TEAL : Color.rgb(29, 49, 67), 11, primary ? TEAL : Color.rgb(44, 67, 86)));
        button.setBackgroundTintList(null);
        button.setOnClickListener(view -> callback.run());
        return button;
    }

    private void action(LinearLayout parent, String label, Runnable callback, boolean primary) {
        parent.addView(button(label, callback, primary), blockParams());
    }

    private GradientDrawable background(int color, int radius, int border) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(radius));
        drawable.setStroke(dp(1), border);
        return drawable;
    }

    private LinearLayout.LayoutParams blockParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.topMargin = dp(7);
        params.bottomMargin = dp(7);
        return params;
    }

    private void confirm(String title, String description, String positive, Runnable accepted) {
        new AlertDialog.Builder(this).setTitle(title).setMessage(description)
                .setPositiveButton(positive, (dialog, which) -> accepted.run())
                .setNegativeButton("انصراف", null).show();
    }

    private void message(String title, String description) {
        new AlertDialog.Builder(this).setTitle(title).setMessage(description).setPositiveButton("باشه", null).show();
    }

    private void safeError(Exception failure, String fallback) {
        String message = failure.getMessage();
        // Our modules supply Persian messages. Never expose raw provider/server/credential errors.
        boolean safe = message != null && message.length() < 600 && message.matches("(?s).*[\\u0600-\\u06FF].*")
                && !message.contains("Bearer ") && !message.contains("sk-") && !message.contains("https://");
        message("انجام نشد", safe ? message : fallback);
    }

    private void ui(Runnable action) {
        handler.post(() -> { if (!destroyed && !isFinishing()) action.run(); });
    }

    private void toast(String value) { Toast.makeText(this, value, Toast.LENGTH_LONG).show(); }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    private String clock(int hour, int minute) { return String.format(Locale.US, "%02d:%02d", hour, minute); }
    private String dateOnly(long millis) { return new java.text.SimpleDateFormat("yyyy/MM/dd", Locale.US).format(new Date(millis)); }
    private String dateTime(long millis) { return dateOnly(millis) + "  ·  " + new java.text.SimpleDateFormat("HH:mm", Locale.US).format(new Date(millis)); }
    private String shorten(String value, int length) { return value.length() <= length ? value : value.substring(0, length) + "\n…"; }
    private String emptyFallback(String value, String fallback) { return value == null || value.trim().isEmpty() ? fallback : value; }

    private int parseInt(String value, int fallback) {
        try {
            StringBuilder ascii = new StringBuilder();
            for (char c : value.trim().toCharArray()) {
                int digit = Character.digit(c, 10);
                ascii.append(digit >= 0 ? (char) ('0' + digit) : c);
            }
            return Integer.parseInt(ascii.toString());
        } catch (NumberFormatException ignored) { return fallback; }
    }

    private interface TimeChosen { void choose(int hour, int minute); }
    private void timePicker(int hour, int minute, TimeChosen chosen) {
        new TimePickerDialog(this, (picker, h, m) -> chosen.choose(h, m), hour, minute, true).show();
    }
}
