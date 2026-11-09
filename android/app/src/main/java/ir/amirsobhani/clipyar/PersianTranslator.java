package ir.amirsobhani.clipyar;

import com.google.android.gms.tasks.*;
import com.google.mlkit.common.model.DownloadConditions;
import com.google.mlkit.nl.translate.*;
import ir.amirsobhani.clipyar.core.*;
import org.json.JSONObject;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.*;

/** English -> Persian happens on-device after the language model is available. */
public final class PersianTranslator implements AutoCloseable {
    private final Translator client;
    private final YoutubeSource.Progress progress;
    private final Store store;
    private boolean ready;
    public PersianTranslator(YoutubeSource.Progress progress,Store store) {
        this.progress=progress;this.store=store;
        client=Translation.getClient(new TranslatorOptions.Builder().setSourceLanguage(TranslateLanguage.ENGLISH)
            .setTargetLanguage(TranslateLanguage.PERSIAN).build());
    }
    public void prepare(boolean wifiOnly) throws Exception {
        if(ready) return;
        String message="آماده‌سازی مترجم فارسی؛ بار اول مدل Google ML Kit دانلود می‌شود…";
        progress.update(message);
        DownloadConditions.Builder conditions=new DownloadConditions.Builder();if(wifiOnly) conditions.requireWifi();
        try {await(client.downloadModelIfNeeded(conditions.build()),10*60,message);ready=true;}
        catch(InterruptedException e) {throw e;}
        catch(Exception e) {throw new IOException("مدل ترجمهٔ فارسی آماده نشد؛ دریافت اولیهٔ مدل به اینترنت نیاز دارد. "+YoutubeSource.shortError(e),e);}
    }
    private <T> T await(Task<T> task,int timeoutSeconds,String message) throws Exception {
        long deadline=android.os.SystemClock.elapsedRealtime()+timeoutSeconds*1000L,last=0;
        while(!task.isComplete()) {
            if(progress.stopped()) throw new InterruptedException("Stopped");
            if(android.os.SystemClock.elapsedRealtime()>deadline) throw new IOException("زمان انتظار برای مترجم تمام شد؛ دوباره پس از بررسی اتصال امتحان کن");
            if(android.os.SystemClock.elapsedRealtime()-last>5000) {progress.update(message);last=android.os.SystemClock.elapsedRealtime();}
            try {Tasks.await(task,1,TimeUnit.SECONDS);}catch(TimeoutException ignored) { }
        }
        if(progress.stopped()) throw new InterruptedException("Stopped");
        if(!task.isSuccessful()) throw new IOException("مترجم آماده نیست",task.getException());
        return task.getResult();
    }
    public String translate(String text) throws Exception {
        if(!ready) throw new IllegalStateException("Translation model is not ready");
        return await(client.translate(text),90,"ترجمهٔ زیرنویس انگلیسی به فارسی روی گوشی…");
    }
    public List<Captions.Cue> translateFile(File source,File directory,boolean wifiOnly) throws Exception {
        byte[] input=Files.readAllBytes(source.toPath());
        String hash=hex(MessageDigest.getInstance("SHA-256").digest(input));
        File complete=new File(directory,"fa-mlkit-v1-"+hash.substring(0,20)+".vtt");
        if(complete.isFile()) {
            try {
                List<Captions.Cue> cached=Captions.parseVtt(new String(Files.readAllBytes(complete.toPath()),StandardCharsets.UTF_8));
                if(Captions.hasPersianScript(cached)) {store.log("استفاده از ترجمهٔ فارسی ذخیره‌شده؛ دریافت یا ترجمه تکرار نشد.");return cached;}
            } catch(IOException ignored) { }
        }
        List<Captions.Cue> original=Captions.parseFile(new String(input,StandardCharsets.UTF_8));
        prepare(wifiOnly);
        File cacheFile=new File(directory,"translation-"+hash.substring(0,20)+".json");JSONObject readCache;
        try {readCache=cacheFile.isFile()?new JSONObject(new String(Files.readAllBytes(cacheFile.toPath()),StandardCharsets.UTF_8)):new JSONObject();}
        catch(Exception e) {readCache=new JSONObject();}
        final JSONObject cache=readCache;final int[] changed={0};
        List<Captions.Cue> result=CaptionTranslation.toPersian(original,text-> {
            String translated=cache.optString(text,"");if(!translated.isEmpty()) return translated;
            translated=translate(text);
            if(translated!=null&&!translated.trim().isEmpty()) {
                cache.put(text,translated);changed[0]++;
                if(changed[0]%10==0) atomic(cacheFile,cache.toString());
            }
            return translated;
        },(done,total)-> {
            if(progress.stopped()) throw new InterruptedException("Stopped");
            progress.update("ترجمهٔ فارسی روی گوشی · "+done+" از "+total+" بخش · Google ML Kit");
        });
        atomic(cacheFile,cache.toString());atomic(complete,Captions.toVtt(result));
        store.log("ترجمهٔ فارسی روی گوشی کامل شد · Google ML Kit · "+result.size()+" بخش زمان‌دار؛ اصطلاحات فنی نیازمند بازبینی هستند.");
        return result;
    }
    private static String hex(byte[] bytes) {StringBuilder s=new StringBuilder();for(byte b:bytes)s.append(String.format(Locale.ROOT,"%02x",b&255));return s.toString();}
    private static void atomic(File file,String text) throws IOException {
        File temp=new File(file.getParentFile(),file.getName()+".tmp");Files.write(temp.toPath(),text.getBytes(StandardCharsets.UTF_8));
        Files.move(temp.toPath(),file.toPath(),StandardCopyOption.REPLACE_EXISTING);
    }
    @Override public void close() {client.close();}
}
