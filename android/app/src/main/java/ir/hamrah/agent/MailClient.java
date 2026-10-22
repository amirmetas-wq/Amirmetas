package ir.hamrah.agent;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Looper;

import com.google.android.gms.auth.GoogleAuthException;
import com.google.android.gms.auth.GoogleAuthUtil;
import com.google.android.gms.auth.UserRecoverableAuthException;
import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.common.api.Scope;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Read-only Gmail integration. Call summarize on a worker thread.
 *
 * The digest contains email subjects and snippets, not a semantic AI summary of
 * entire messages. Email text is untrusted display data, never an instruction.
 * No OAuth token is persisted here or sent to any host other than Google.
 */
public final class MailClient {
    public static final String GMAIL_READ_ONLY =
            "https://www.googleapis.com/auth/gmail.readonly";
    public static final int SIGN_IN_REQUEST = 4201;
    public static final int AUTHORIZATION_REQUEST = 4202;

    private static final String API_BASE = "https://gmail.googleapis.com/gmail/v1/users/me/";
    private static final String PREFS = "mail_digest_private";
    private static final String SUMMARY = "last_summary";
    private static final String SUMMARY_TIME = "last_summary_time";
    private static final int MAX_MESSAGES = 30;
    private static final int MAX_RESPONSE_BYTES = 262_144;
    private static final int TIMEOUT_MS = 12_000;
    private static final Object CACHE_LOCK = new Object();
    private static final AtomicLong CACHE_GENERATION = new AtomicLong();

    private MailClient() { }

    public static GoogleSignInClient signInClient(Activity activity) {
        GoogleSignInOptions options = new GoogleSignInOptions.Builder(
                GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestEmail()
                .requestScopes(new Scope(GMAIL_READ_ONLY))
                .build();
        return GoogleSignIn.getClient(activity, options);
    }

    /** A prior sign-in exists; actual API authorization is checked on each fetch. */
    public static boolean isConnected(Context context) {
        GoogleSignInAccount account = GoogleSignIn.getLastSignedInAccount(context);
        return account != null && account.getAccount() != null
                && GoogleSignIn.hasPermissions(account, new Scope(GMAIL_READ_ONLY));
    }

    public static String lastSummary(Context context) {
        return prefs(context).getString(SUMMARY, "");
    }

    public static long lastSummaryTime(Context context) {
        return prefs(context).getLong(SUMMARY_TIME, 0L);
    }

    /** Clears app digest immediately and revokes the Google sign-in asynchronously. */
    public static void disconnect(Activity activity) {
        synchronized (CACHE_LOCK) {
            // An in-flight fetch must not recreate a report after disconnect.
            CACHE_GENERATION.incrementAndGet();
            prefs(activity).edit().clear().apply();
        }
        signInClient(activity).revokeAccess();
    }

    /**
     * Returns and caches a Persian digest of up to 30 inbox messages from 48h.
     * Never sets messages read, deletes, archives, sends, or changes any label.
     */
    public static String summarize(Context context) throws Exception {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            throw new MailException("خواندن ایمیل باید در پس‌زمینه انجام شود.");
        }
        Context app = context.getApplicationContext();
        GoogleSignInAccount account = GoogleSignIn.getLastSignedInAccount(app);
        if (account == null || account.getAccount() == null) {
            throw new MailException("ابتدا حساب Gmail را در برنامه متصل کن.");
        }
        long generation = CACHE_GENERATION.get();
        // Account and token are intentionally not logged or stored in preferences.
        String token = accessToken(app, account);
        List<MailItem> items;
        boolean more;
        try {
            FetchResult result;
            try {
                result = fetch(token);
            } catch (UnauthorizedException expired) {
                GoogleAuthUtil.clearToken(app, token);
                token = accessToken(app, account);
                result = fetch(token);
            }
            items = result.items;
            more = result.more;
        } catch (AuthorizationRequiredException consent) {
            throw consent;
        } catch (MailException safeError) {
            throw safeError;
        } catch (IOException networkError) {
            throw new MailException("ارتباط با Gmail برقرار نشد؛ اینترنت را بررسی کن.");
        } catch (GoogleAuthException authError) {
            throw new MailException("مجوز Gmail نیاز به اتصال دوبارهٔ حساب دارد.");
        } catch (JSONException malformedResponse) {
            throw new MailException("پاسخ Gmail قابل پردازش نبود؛ بعداً دوباره امتحان کن.");
        }

        String report = format(items, more);
        synchronized (CACHE_LOCK) {
            GoogleSignInAccount current = GoogleSignIn.getLastSignedInAccount(app);
            if (generation != CACHE_GENERATION.get() || current == null
                    || !account.getAccount().equals(current.getAccount())) {
                throw new MailException("حساب Gmail تغییر کرد؛ گزارش قبلی ذخیره نشد.");
            }
            prefs(app).edit().putString(SUMMARY, report)
                    .putLong(SUMMARY_TIME, System.currentTimeMillis()).apply();
        }
        return report;
    }

    private static String accessToken(Context context, GoogleSignInAccount account)
            throws MailException, AuthorizationRequiredException {
        try {
            return GoogleAuthUtil.getToken(context, account.getAccount(),
                    "oauth2:" + GMAIL_READ_ONLY);
        } catch (UserRecoverableAuthException consent) {
            throw new AuthorizationRequiredException(consent.getIntent());
        } catch (IOException networkError) {
            throw new MailException("دریافت مجوز Gmail به اینترنت نیاز دارد.");
        } catch (GoogleAuthException authError) {
            throw new MailException("اتصال Gmail کامل نیست؛ تنظیم OAuth و ورود حساب را بررسی کن.");
        }
    }

    private static FetchResult fetch(String token)
            throws IOException, JSONException, MailException {
        String query = URLEncoder.encode("in:inbox newer_than:2d", StandardCharsets.UTF_8.name());
        JSONObject page = get("messages?maxResults=" + MAX_MESSAGES + "&q=" + query
                + "&fields=messages(id),nextPageToken,resultSizeEstimate", token);
        JSONArray messages = page.optJSONArray("messages");
        List<MailItem> items = new ArrayList<>();
        if (messages != null) {
            for (int index = 0; index < Math.min(MAX_MESSAGES, messages.length()); index++) {
                if (Thread.currentThread().isInterrupted()) {
                    throw new MailException("خواندن Gmail متوقف شد؛ دوباره تلاش کن.");
                }
                String id = messages.getJSONObject(index).getString("id");
                // IDs come from Gmail, but validate before inserting into a URL path.
                if (!id.matches("[A-Za-z0-9_-]{1,128}")) {
                    throw new MailException("شناسهٔ پیام Gmail معتبر نبود.");
                }
                JSONObject message = get("messages/" + id
                        + "?format=metadata&metadataHeaders=From&metadataHeaders=Subject"
                        + "&fields=id,snippet,internalDate,labelIds,payload(headers)", token);
                JSONObject payload = message.optJSONObject("payload");
                JSONArray headers = payload == null ? null : payload.optJSONArray("headers");
                String from = "فرستندهٔ نامشخص";
                String subject = "بدون موضوع";
                if (headers != null) {
                    for (int headerIndex = 0; headerIndex < headers.length(); headerIndex++) {
                        JSONObject header = headers.getJSONObject(headerIndex);
                        String name = header.optString("name");
                        if ("From".equalsIgnoreCase(name)) {
                            from = clean(header.optString("value"), 130);
                        } else if ("Subject".equalsIgnoreCase(name)) {
                            subject = clean(header.optString("value"), 180);
                        }
                    }
                }
                boolean unread = false;
                JSONArray labels = message.optJSONArray("labelIds");
                if (labels != null) {
                    for (int labelIndex = 0; labelIndex < labels.length(); labelIndex++) {
                        unread |= "UNREAD".equals(labels.optString(labelIndex));
                    }
                }
                items.add(new MailItem(from, subject,
                        clean(decodeEntities(message.optString("snippet")), 260), unread));
            }
        }
        return new FetchResult(items, page.has("nextPageToken"));
    }

    private static JSONObject get(String relativeUrl, String token)
            throws IOException, JSONException, MailException {
        HttpURLConnection connection = (HttpURLConnection)
                new URL(API_BASE + relativeUrl).openConnection();
        try {
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(TIMEOUT_MS);
            connection.setReadTimeout(TIMEOUT_MS);
            connection.setInstanceFollowRedirects(false);
            connection.setRequestProperty("Authorization", "Bearer " + token);
            connection.setRequestProperty("Accept", "application/json");
            int status = connection.getResponseCode();
            if (status == 401) throw new UnauthorizedException();
            if (status == 403) {
                throw new MailException("Gmail اجازهٔ خواندن نداد؛ Gmail API و مجوز فقط‌خواندن را بررسی کن.");
            }
            if (status == 429) {
                throw new MailException("تعداد درخواست‌های Gmail زیاد شده؛ بعداً دوباره امتحان کن.");
            }
            if (status < 200 || status >= 300) {
                // Never surface a raw response body or OAuth token in user-visible errors.
                throw new MailException("دریافت ایمیل از Gmail کامل نشد؛ بعداً دوباره امتحان کن.");
            }
            try (InputStream input = connection.getInputStream();
                 ByteArrayOutputStream output = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[4096];
                int count;
                while ((count = input.read(buffer)) != -1) {
                    if (output.size() + count > MAX_RESPONSE_BYTES) {
                        throw new MailException("پاسخ Gmail بیش از اندازهٔ مجاز بود.");
                    }
                    output.write(buffer, 0, count);
                }
                return new JSONObject(output.toString(StandardCharsets.UTF_8.name()));
            }
        } finally {
            connection.disconnect();
        }
    }

    private static String format(List<MailItem> items, boolean more) {
        String time = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT,
                new Locale("fa")).format(new Date());
        StringBuilder report = new StringBuilder("گزارش Gmail — ").append(time)
                .append("\nصندوق ورودی، ۴۸ ساعت گذشته\n")
                .append("این گزارش از موضوع و پیش‌نمایش ایمیل‌ها ساخته شده؛ متن کامل ایمیل تحلیل نشده است.\n");
        if (items.isEmpty()) {
            return report.append("\nدر این بازه ایمیلی در صندوق ورودی پیدا نشد.").toString();
        }
        int unread = 0;
        for (MailItem item : items) if (item.unread) unread++;
        report.append("\nپیام‌های بررسی‌شده: ").append(items.size())
                .append(" | خوانده‌نشده در این فهرست: ").append(unread).append('\n');
        if (more) {
            report.append("بیش از ۳۰ پیام وجود دارد؛ فقط ۳۰ پیام جدیدتر در این گزارش آمده است.\n");
        }
        for (int index = 0; index < items.size(); index++) {
            MailItem item = items.get(index);
            report.append('\n').append(index + 1).append(". ")
                    .append(item.unread ? "[خوانده‌نشده] " : "")
                    .append(item.subject).append('\n').append("از: ").append(item.from);
            if (!item.snippet.isEmpty()) report.append('\n').append(item.snippet);
            report.append('\n');
        }
        return report.toString();
    }

    private static String clean(String value, int maxLength) {
        if (value == null) return "";
        // Keep Persian ZWNJ, while removing controls and bidi overrides from email data.
        String result = value.replaceAll("[\\p{Cntrl}\\u202A-\\u202E\\u2066-\\u2069]", " ")
                .replaceAll("\\s+", " ").trim();
        int length = result.codePointCount(0, result.length());
        return length <= maxLength ? result
                : result.substring(0, result.offsetByCodePoints(0, maxLength)) + "…";
    }

    private static String decodeEntities(String value) {
        // Snippets are displayed as plain text. These are common Gmail snippet entities.
        return value.replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">")
                .replace("&quot;", "\"").replace("&#39;", "'").replace("&nbsp;", " ");
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static class MailException extends Exception {
        public MailException(String message) { super(message); }
    }

    /** Catch on a worker, then launch getIntent() from the Activity on the UI thread. */
    public static final class AuthorizationRequiredException extends MailException {
        private final Intent intent;
        public AuthorizationRequiredException(Intent intent) {
            super("برای خواندن Gmail باید مجوز Google را در برنامه تأیید کنی.");
            this.intent = intent;
        }
        public Intent getIntent() { return intent; }
    }

    private static final class UnauthorizedException extends MailException {
        UnauthorizedException() { super("مجوز Gmail منقضی شده؛ حساب را دوباره متصل کن."); }
    }

    private static final class MailItem {
        final String from;
        final String subject;
        final String snippet;
        final boolean unread;
        MailItem(String from, String subject, String snippet, boolean unread) {
            this.from = from;
            this.subject = subject;
            this.snippet = snippet;
            this.unread = unread;
        }
    }

    private static final class FetchResult {
        final List<MailItem> items;
        final boolean more;
        FetchResult(List<MailItem> items, boolean more) {
            this.items = items;
            this.more = more;
        }
    }
}
