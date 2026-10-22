package ir.hamrah.agent;

import android.content.ClipData;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;

/** Imports user-shared text without attempting access to another app's private database. */
public final class NotesImport {
    public static final int MAX_BYTES = 65_536;

    private NotesImport() {}

    /** Returns null for an unrelated intent. The activity must preview and confirm the text. */
    public static String extractText(Context context, Intent intent) throws IOException {
        if (intent == null) return null;
        String action = intent.getAction();
        if (Intent.ACTION_SEND.equals(action) || Intent.ACTION_SEND_MULTIPLE.equals(action)) {
            CharSequence shared = intent.getCharSequenceExtra(Intent.EXTRA_TEXT);
            if (shared != null) return checkText(shared.toString());
            if (Intent.ACTION_SEND_MULTIPLE.equals(action)) {
                ArrayList<Uri> streams = intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM);
                if (streams != null && streams.size() > 1) {
                    throw new IOException("برای کارهای روزانه، هر بار فقط یک یادداشت یا فایل متنی انتخاب کنید.");
                }
                if (streams != null && streams.size() == 1) {
                    return readText(context, streams.get(0));
                }
            } else {
                Uri stream = intent.getParcelableExtra(Intent.EXTRA_STREAM);
                if (stream != null) return readText(context, stream);
            }
            ClipData clip = intent.getClipData();
            if (clip != null && clip.getItemCount() > 0) {
                if (clip.getItemCount() > 1) {
                    throw new IOException("برای کارهای روزانه، هر بار فقط یک یادداشت یا فایل متنی انتخاب کنید.");
                }
                ClipData.Item item = clip.getItemAt(0);
                if (item.getText() != null) return checkText(item.getText().toString());
                if (item.getUri() != null) return readText(context, item.getUri());
            }
            throw new IOException("متن یادداشت همراه اشتراک‌گذاری نبود. در برنامهٔ یادداشت، گزینهٔ اشتراک‌گذاری به‌صورت متن را انتخاب کنید.");
        }
        if (Intent.ACTION_VIEW.equals(action) || Intent.ACTION_OPEN_DOCUMENT.equals(action)
                || Intent.ACTION_GET_CONTENT.equals(action) || action == null) {
            Uri uri = intent.getData();
            if (uri != null) return readText(context, uri);
        }
        return null;
    }

    /** Reads a granted content URI, never file paths or network URLs. Safe to use for refresh. */
    public static String readText(Context context, Uri uri) throws IOException {
        return readTextInternal(context, uri, false);
    }

    /** An intentionally emptied linked document clears tasks instead of retaining stale text. */
    public static String readTextForRefresh(Context context, Uri uri) throws IOException {
        return readTextInternal(context, uri, true);
    }

    private static String readTextInternal(Context context, Uri uri, boolean allowEmpty) throws IOException {
        if (uri == null || !"content".equalsIgnoreCase(uri.getScheme())) {
            throw new IOException("فایل متنی را با انتخاب‌گر فایل اندروید انتخاب کنید؛ مسیر مستقیم فایل پشتیبانی نمی‌شود.");
        }
        try {
            String mime = context.getContentResolver().getType(uri);
            if (mime != null && !mime.startsWith("text/")
                    && !"application/octet-stream".equalsIgnoreCase(mime)) {
                throw new IOException("این نوع فایل یادداشت پشتیبانی نمی‌شود. یادداشت را به‌صورت متن ساده یا فایل TXT به اشتراک بگذارید.");
            }
            byte[] bytes;
            try (InputStream input = context.getContentResolver().openInputStream(uri);
                 ByteArrayOutputStream output = new ByteArrayOutputStream()) {
                if (input == null) throw new IOException("فایل متنی باز نشد؛ دوباره آن را انتخاب کنید.");
                byte[] buffer = new byte[4096];
                int count;
                while ((count = input.read(buffer)) != -1) {
                    if (output.size() + count > MAX_BYTES) {
                        throw new IOException("یادداشت بیش از ۶۴ کیلوبایت است. فقط کارهای روزانهٔ موردنیاز را وارد کنید.");
                    }
                    output.write(buffer, 0, count);
                }
                bytes = output.toByteArray();
            }
            String text;
            try {
                text = StandardCharsets.UTF_8.newDecoder()
                        .onMalformedInput(CodingErrorAction.REPORT)
                        .onUnmappableCharacter(CodingErrorAction.REPORT)
                        .decode(ByteBuffer.wrap(bytes)).toString();
            } catch (CharacterCodingException e) {
                throw new IOException("فایل باید متن ساده با کدگذاری UTF-8 باشد؛ از برنامهٔ یادداشت، متن را مستقیم به اشتراک بگذارید.", e);
            }
            if (text.startsWith("\uFEFF")) text = text.substring(1);
            return checkText(text, allowEmpty);
        } catch (SecurityException e) {
            throw new IOException("دسترسی به این فایل برقرار نیست. فایل را دوباره با انتخاب‌گر اندروید انتخاب کنید.", e);
        }
    }

    /** True only if Android granted persistent read access, suitable for later refresh. */
    public static boolean persistReadGrant(Context context, Intent intent) {
        if (intent == null || intent.getData() == null
                || !"content".equalsIgnoreCase(intent.getData().getScheme())) return false;
        int flags = intent.getFlags();
        if ((flags & Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION) == 0
                || (flags & Intent.FLAG_GRANT_READ_URI_PERMISSION) == 0) return false;
        try {
            context.getContentResolver().takePersistableUriPermission(intent.getData(),
                    Intent.FLAG_GRANT_READ_URI_PERMISSION);
            return true;
        } catch (SecurityException | UnsupportedOperationException e) {
            return false;
        }
    }

    public static String sharedTitle(Intent intent) {
        if (intent == null) return null;
        CharSequence title = intent.getCharSequenceExtra(Intent.EXTRA_TITLE);
        if (title == null || title.toString().trim().isEmpty()) {
            title = intent.getCharSequenceExtra(Intent.EXTRA_SUBJECT);
        }
        return title == null ? null : title.toString().trim();
    }

    private static String checkText(String text) throws IOException {
        return checkText(text, false);
    }

    private static String checkText(String text, boolean allowEmpty) throws IOException {
        if (text.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES) {
            throw new IOException("یادداشت بیش از ۶۴ کیلوبایت است. فقط کارهای روزانهٔ موردنیاز را وارد کنید.");
        }
        if (text.indexOf('\u0000') >= 0) {
            throw new IOException("محتوای فایل متن ساده نیست؛ یادداشت را به‌صورت متن یا فایل TXT به اشتراک بگذارید.");
        }
        String normalized = text.replace("\r\n", "\n").replace('\r', '\n').trim();
        if (normalized.isEmpty() && !allowEmpty) throw new IOException("یادداشت خالی است؛ ابتدا متن کارهای روزانه را وارد کنید.");
        return normalized;
    }
}
