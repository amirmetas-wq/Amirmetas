package ir.hamrah.agent;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Looper;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Properties;

import javax.mail.Address;
import javax.mail.AuthenticationFailedException;
import javax.mail.FetchProfile;
import javax.mail.Flags;
import javax.mail.Folder;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.Multipart;
import javax.mail.Part;
import javax.mail.Session;
import javax.mail.Store;
import javax.mail.internet.ContentType;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeUtility;
import javax.mail.search.ComparisonTerm;
import javax.mail.search.ReceivedDateTerm;

/** Read-only Yahoo inbox digest. No email or credential is sent to an AI provider here. */
public final class YahooClient {
    private static final String HOST = "imap.mail.yahoo.com";
    private static final String PREFS = "hamrah_yahoo";
    private static final String PASSWORD_KEY = "yahoo_password";
    private static final long LOOKBACK_MILLIS = 48L * 60 * 60 * 1000;
    private static final int MAX_MESSAGES = 30;
    private static final int MAX_CANDIDATES = 120;
    private static final int MAX_SNIPPET_CHARS = 300;

    private YahooClient() { }

    /** Store a Yahoo-generated app password, never the normal Yahoo sign-in password. */
    public static void configure(Context context, String email, String appPassword) {
        String address = email == null ? "" : email.trim();
        if (address.length() > 254 || !address.matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+")) {
            throw new IllegalArgumentException("نشانی ایمیل Yahoo را درست وارد کن.");
        }
        String domain = address.substring(address.lastIndexOf('@') + 1).toLowerCase(Locale.ROOT);
        if (!(domain.startsWith("yahoo.") || domain.equals("ymail.com")
                || domain.equals("rocketmail.com"))) {
            throw new IllegalArgumentException("این اتصال برای حساب Yahoo، ymail یا rocketmail است.");
        }
        String password = appPassword == null ? "" : appPassword.replaceAll("\\s", "");
        if (password.length() < 8 || password.length() > 256) {
            throw new IllegalArgumentException("رمز مخصوص برنامه را از بخش امنیت حساب Yahoo بگیر و اینجا وارد کن.");
        }
        new SecretStore(context).putSecret(PASSWORD_KEY, password);
        preferences(context).edit().putString("email", address).apply();
    }

    public static boolean isConnected(Context context) {
        if (email(context).isEmpty()) return false;
        try {
            String password = new SecretStore(context).getSecret(PASSWORD_KEY);
            return password != null && !password.isEmpty();
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    public static String email(Context context) {
        return preferences(context).getString("email", "");
    }

    public static void disconnect(Context context) {
        new SecretStore(context).removeSecret(PASSWORD_KEY);
        preferences(context).edit().clear().apply();
    }

    /** Call on a worker thread. This returns a local digest of at most 30 recent messages. */
    public static String summarize(Context context) throws IOException {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            throw new IllegalStateException("خواندن Yahoo باید در پس‌زمینه انجام شود.");
        }
        String account = email(context);
        String password;
        try {
            password = new SecretStore(context).getSecret(PASSWORD_KEY);
        } catch (RuntimeException ignored) {
            throw new IOException("ذخیرهٔ امن Yahoo قابل خواندن نیست؛ حساب را دوباره متصل کن.");
        }
        if (account.isEmpty() || password == null || password.isEmpty()) {
            throw new IOException("ابتدا حساب Yahoo را با رمز مخصوص برنامه متصل کن.");
        }
        throwIfCancelled();

        Store store = null;
        Folder inbox = null;
        try {
            Session session = Session.getInstance(connectionProperties());
            session.setDebug(false);
            store = session.getStore("imaps");
            store.connect(HOST, 993, account, password);
            throwIfCancelled();
            inbox = store.getFolder("INBOX");
            inbox.open(Folder.READ_ONLY);
            throwIfCancelled();
            long sinceMillis = System.currentTimeMillis() - LOOKBACK_MILLIS;
            Message[] matching = inbox.search(new ReceivedDateTerm(
                    ComparisonTerm.GE, new Date(sinceMillis)));
            throwIfCancelled();
            if (matching.length == 0) return "Yahoo — در ۴۸ ساعت گذشته ایمیل تازه‌ای در صندوق ورودی پیدا نشد.";

            // IMAP search is server-side. Bound subsequent metadata and body reads separately.
            int first = Math.max(0, matching.length - MAX_CANDIDATES);
            Message[] candidates = new Message[matching.length - first];
            System.arraycopy(matching, first, candidates, 0, candidates.length);
            FetchProfile profile = new FetchProfile();
            profile.add(FetchProfile.Item.ENVELOPE);
            profile.add(FetchProfile.Item.FLAGS);
            inbox.fetch(candidates, profile);
            throwIfCancelled();

            List<RecentMessage> recent = new ArrayList<>();
            for (Message message : candidates) {
                throwIfCancelled();
                Date date = message.getReceivedDate();
                if (date == null) date = message.getSentDate();
                // IMAP date search has day precision; enforce the 48-hour window locally.
                if (date != null && date.getTime() >= sinceMillis) {
                    recent.add(new RecentMessage(message, date));
                }
            }
            recent.sort(Comparator.comparingLong((RecentMessage item) -> item.date.getTime()).reversed());
            int count = Math.min(MAX_MESSAGES, recent.size());
            if (count == 0) return "Yahoo — در ۴۸ ساعت گذشته ایمیل تازه‌ای در صندوق ورودی پیدا نشد.";

            StringBuilder result = new StringBuilder("Yahoo — گزارش محلی عنوان و گزیدهٔ متن\n");
            result.append("حداکثر ۳۰ ایمیل از ۴۸ ساعت گذشته؛ این گزارش خلاصهٔ معنایی هوش مصنوعی نیست.\n");
            DateFormat dateFormat = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT);
            for (int i = 0; i < count; i++) {
                throwIfCancelled();
                RecentMessage entry = recent.get(i);
                Message message = entry.message;
                result.append('\n').append(i + 1).append(". ");
                if (!message.isSet(Flags.Flag.SEEN)) result.append("[خوانده‌نشده] ");
                result.append(clean(message.getSubject(), 180, "بدون عنوان"));
                result.append("\nفرستنده: ").append(sender(message));
                result.append("\nزمان: ").append(dateFormat.format(entry.date));
                String snippet;
                try {
                    snippet = plainTextSnippet(message, new ParseBudget(), 0);
                } catch (IOException | MessagingException | RuntimeException ignored) {
                    snippet = "";
                }
                throwIfCancelled();
                if (snippet.isEmpty()) {
                    result.append("\nگزیده: متن سادهٔ قابل‌خواندن دریافت نشد؛ ایمیل را در Yahoo باز کن.\n");
                } else {
                    result.append("\nگزیده: ").append(snippet).append('\n');
                }
            }
            if (matching.length > count) {
                result.append("\nاین فهرست محدود است و تمام صندوق ورودی را پوشش نمی‌دهد.\n");
            }
            return result.toString();
        } catch (AuthenticationFailedException ignored) {
            throw new IOException("ورود به Yahoo انجام نشد؛ نشانی و رمز مخصوص برنامه را بررسی کن. رمز اصلی حساب کافی نیست.");
        } catch (MessagingException | RuntimeException ignored) {
            throw new IOException("ارتباط امن با Yahoo برقرار نشد. اینترنت و فعال بودن دسترسی حساب را بررسی کن و دوباره تلاش کن.");
        } finally {
            if (inbox != null && inbox.isOpen()) {
                try { inbox.close(false); } catch (MessagingException ignored) { }
            }
            if (store != null && store.isConnected()) {
                try { store.close(); } catch (MessagingException ignored) { }
            }
        }
    }

    private static Properties connectionProperties() {
        Properties properties = new Properties();
        properties.setProperty("mail.store.protocol", "imaps");
        for (String protocol : new String[] { "imap", "imaps" }) {
            String prefix = "mail." + protocol + ".";
            properties.setProperty(prefix + "ssl.enable", "true");
            properties.setProperty(prefix + "ssl.checkserveridentity", "true");
            properties.setProperty(prefix + "ssl.protocols", "TLSv1.2");
            properties.setProperty(prefix + "connectiontimeout", "15000");
            properties.setProperty(prefix + "timeout", "20000");
            properties.setProperty(prefix + "writetimeout", "15000");
            properties.setProperty(prefix + "peek", "true");
            properties.setProperty(prefix + "partialfetch", "true");
            properties.setProperty(prefix + "fetchsize", "2048");
            properties.setProperty(prefix + "connectionpoolsize", "1");
            properties.setProperty(prefix + "connectionpooltimeout", "1000");
        }
        return properties;
    }

    /** Only read a bounded inline text/plain body; attachments and HTML are not downloaded. */
    private static String plainTextSnippet(Part part, ParseBudget budget, int depth)
            throws MessagingException, IOException {
        throwIfCancelled();
        if (depth > 5 || ++budget.visitedParts > 24) return "";
        String disposition = part.getDisposition();
        if (Part.ATTACHMENT.equalsIgnoreCase(disposition) || part.getFileName() != null) return "";
        if (part.isMimeType("text/plain")) {
            Charset charset = StandardCharsets.UTF_8;
            try {
                String declared = new ContentType(part.getContentType()).getParameter("charset");
                if (declared != null) charset = Charset.forName(MimeUtility.javaCharset(declared));
            } catch (Exception ignored) { }
            char[] buffer = new char[1024];
            int total = 0;
            try (InputStream stream = part.getInputStream(); Reader reader = new InputStreamReader(stream, charset)) {
                while (total < buffer.length) {
                    throwIfCancelled();
                    int read = reader.read(buffer, total, buffer.length - total);
                    throwIfCancelled();
                    if (read < 0) break;
                    if (read == 0) break;
                    total += read;
                }
            }
            return clean(new String(buffer, 0, total), MAX_SNIPPET_CHARS, "");
        }
        if (part.isMimeType("multipart/*")) {
            Object content = part.getContent();
            if (!(content instanceof Multipart)) return "";
            Multipart multipart = (Multipart) content;
            int count = Math.min(multipart.getCount(), 12);
            for (int i = 0; i < count && budget.visitedParts < 24; i++) {
                throwIfCancelled();
                String text = plainTextSnippet(multipart.getBodyPart(i), budget, depth + 1);
                if (!text.isEmpty()) return text;
            }
        }
        return "";
    }

    private static void throwIfCancelled() throws IOException {
        if (Thread.currentThread().isInterrupted()) {
            throw new IOException("خواندن Yahoo متوقف شد.");
        }
    }

    private static String sender(Message message) throws MessagingException {
        Address[] addresses = message.getFrom();
        if (addresses == null || addresses.length == 0) return "نامشخص";
        Address first = addresses[0];
        if (first instanceof InternetAddress) {
            InternetAddress address = (InternetAddress) first;
            String personal = address.getPersonal();
            if (personal != null && !personal.trim().isEmpty()) {
                return clean(personal, 80, "") + " <" + clean(address.getAddress(), 120, "") + ">";
            }
            return clean(address.getAddress(), 160, "نامشخص");
        }
        return clean(first.toString(), 160, "نامشخص");
    }

    private static String clean(String value, int limit, String fallback) {
        if (value == null) return fallback;
        // Remove controls including bidi overrides that could disguise sender names.
        String normalized = value.replaceAll("[\\p{Cntrl}\\u202A-\\u202E\\u2066-\\u2069]", " ")
                .replaceAll("\\s+", " ").trim();
        if (normalized.isEmpty()) return fallback;
        if (normalized.length() <= limit) return normalized;
        return normalized.substring(0, limit) + "…";
    }

    private static SharedPreferences preferences(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private static final class RecentMessage {
        final Message message;
        final Date date;
        RecentMessage(Message message, Date date) { this.message = message; this.date = date; }
    }

    private static final class ParseBudget {
        int visitedParts;
    }
}
