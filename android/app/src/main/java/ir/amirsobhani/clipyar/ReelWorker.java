package ir.amirsobhani.clipyar;

import android.content.*;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.StatFs;
import androidx.work.*;
import ir.amirsobhani.clipyar.core.*;
import org.json.*;
import java.io.*;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.locks.ReentrantLock;

public final class ReelWorker extends Worker implements YoutubeSource.Progress {
    private static final ReentrantLock PIPELINE_LOCK=new ReentrantLock();
    private volatile NativeTools nativeTools;
    private volatile YoutubeSource youtube;
    private long lastProgress;
    private Store store;
    private int saved;
    private String lastStage="شروع",lastFailure="",failedStage="";
    public ReelWorker(Context c,WorkerParameters p) { super(c,p); }
    @Override public boolean stopped() { return isStopped() || Thread.currentThread().isInterrupted(); }
    @Override public void update(String text) {
        lastStage=text;
        setProgressAsync(new Data.Builder().putString("status",text).build());
        if(System.currentTimeMillis()-lastProgress>1800) {
            setForegroundAsync(Notices.foreground(getApplicationContext(),getId(),text));lastProgress=System.currentTimeMillis();
        }
    }
    private void check() throws InterruptedException { if(stopped()) throw new InterruptedException("Stopped"); }
    @Override public void onStopped() {
        super.onStopped();
        YoutubeSource source=youtube;if(source!=null) source.cancel();
        NativeTools tools=nativeTools;if(tools!=null) tools.cancel();
    }
    @Override public Result doWork() {
        Context c=getApplicationContext();store=Store.get(c);
        if(!PIPELINE_LOCK.tryLock()) return Result.retry();
        try {
            setForegroundAsync(Notices.foreground(c,getId(),"آماده‌سازی ابزار ویدئو…")).get();
            store.log("شروع کار؛ زمان و تاریخ بر پایهٔ تهران است.");
            ensureSpace(400L*1024*1024);
            if(getInputData().getBoolean("test_translation",false)) {
                String original="The steel roof is supported by a compression ring and a tension cone.";
                String translated;
                try(PersianTranslator translator=new PersianTranslator(this,store)) {
                    translator.prepare(new Config(c).wifiOnly());translated=translator.translate(original);
                }
                if(translated==null||!Captions.hasPersianScript(Collections.singletonList(new Captions.Cue(0,5000,translated))))
                    throw new IOException("نمونهٔ خروجی مترجم فارسی نبود");
                String proof=original+"\n\n"+translated;
                store.batch("translation-proof",proof);store.log("آزمون ترجمهٔ فارسی روی گوشی موفق شد:\n"+proof);
                Notices.done(c,"ترجمهٔ آزمایشی آماده شد؛ متن را در صفحهٔ برنامه ببین.");
                return Result.success(new Data.Builder().putString("translation_sample",proof).build());
            }
            update("آماده‌سازی ابزار ویدئو…");
            nativeTools=new NativeTools(c);nativeTools.init();check();
            String encoders=nativeTools.run(Arrays.asList(nativeTools.ffmpeg(),"-hide_banner","-encoders"),1);
            // A bounded tail may omit libx264; the real encoder invocation is authoritative.
            if(encoders.isEmpty()) throw new IOException("ابزار ویدئو اجرا نشد");
            youtube=new YoutubeSource(c,this,store);
            String local=getInputData().getString("local"),manual=getInputData().getString("url");
            String captionUri=getInputData().getString("caption");
            boolean persian=getInputData().getBoolean("persian",false);
            if(persian && manual==null && captionUri==null) throw new IOException("برای زیرنویس فارسی یک لینک یا ویدئو و فایل زیرنویس از گوشی انتخاب کن");
            String day=DailyTime.day(Instant.now());Config config=new Config(c);
            List<VideoCandidate> sources;
            File imported=null;
            File importedCaption=null;String captionHash="";
            if(captionUri!=null) {
                importedCaption=new File(c.getCacheDir(),"selected-caption.txt");
                MessageDigest digest=MessageDigest.getInstance("SHA-256");int size=0;
                try(InputStream in=c.getContentResolver().openInputStream(Uri.parse(captionUri));OutputStream out=new FileOutputStream(importedCaption)) {
                    if(in==null) throw new IOException("فایل زیرنویس انتخاب‌شده قابل خواندن نیست");
                    byte[] buffer=new byte[16*1024];int n;
                    while((n=in.read(buffer))!=-1) {check();size+=n;if(size>16*1024*1024) throw new IOException("فایل زیرنویس بیش از ۱۶ مگابایت است");out.write(buffer,0,n);digest.update(buffer,0,n);}
                }
                StringBuilder hash=new StringBuilder();for(byte b:digest.digest())hash.append(String.format(Locale.ROOT,"%02x",b&255));captionHash="-"+hash.substring(0,16);
            }
            if(local!=null) {
                update("دریافت ویدئوی انتخاب‌شده از گوشی…");
                imported=new File(c.getCacheDir(),"selected-source.mp4");
                MessageDigest digest=MessageDigest.getInstance("SHA-256");
                try(InputStream in=c.getContentResolver().openInputStream(Uri.parse(local));OutputStream out=new FileOutputStream(imported)) {
                    if(in==null) throw new IOException("فایل انتخاب‌شده قابل خواندن نیست؛ دوباره انتخاب کن");
                    byte[] buffer=new byte[1024*1024];int n;
                    while((n=in.read(buffer))!=-1) { check();ensureSpace(150L*1024*1024);out.write(buffer,0,n);digest.update(buffer,0,n); }
                }
                StringBuilder hash=new StringBuilder();for(byte b:digest.digest()) hash.append(String.format(Locale.ROOT,"%02x",b&255));
                sources=Collections.singletonList(new VideoCandidate("local-"+hash, "فایل انتخاب‌شده از گوشی", "ویدئوی گوشی", "منبع محلی", "",0,0,0,false));
            } else if(manual!=null) {
                update("بررسی لینک انتخاب‌شده…");sources=Collections.singletonList(youtube.info(manual));
            } else {
                String cache=store.batch(ChartsListSource.CACHE_PREFIX+day);
                if(cache==null) {
                    sources=new ChartsListSource(youtube,this,store).discover(config,day);
                    JSONArray array=new JSONArray();for(VideoCandidate v:sources) array.put(YoutubeSource.json(v));
                    if(!sources.isEmpty()) store.batch(ChartsListSource.CACHE_PREFIX+day,array.toString());
                } else {
                    sources=new ArrayList<>();JSONArray array=new JSONArray(cache);
                    for(int i=0;i<array.length();i++) sources.add(YoutubeSource.fromJson(array.getJSONObject(i)));
                    store.log("ادامهٔ فهرست ذخیره‌شدهٔ امروز؛ قطعه‌های کامل دوباره ساخته نمی‌شوند.");
                }
            }
            int failed=0;
            for(VideoCandidate source:sources) {
                check();
                String workKey=source.id+(persian?":fa-local-v2"+captionHash:":plain-v1");
                if(store.completed(workKey)) { store.log("این خروجی قبلاً کامل شده؛ دریافت تکرار نشد: "+source.title);continue; }
                SubtitleFrames subtitleFrames=null;
                try {
                    String evidence=YoutubeSource.evidence(source);
                    if(persian) evidence+="\nزیرنویس فارسی حک‌شده روی تصویر؛ ترجمهٔ متن انگلیسی با Google ML Kit روی گوشی یا فایل فارسیِ دریافت/انتخاب‌شده. اصطلاحات فنی باید بازبینی شوند.";
                    if(!evidence.trim().isEmpty()) store.evidence(day,workKey,evidence);
                    if(source.live) throw new IOException("پخش زنده یا ویدئوی در انتظار انتشار قابل تقسیم نیست");
                    File folder=new File(c.getCacheDir(),"sources/"+source.id);folder.mkdirs();
                    List<Captions.Cue> captions=Collections.emptyList();
                    if(persian) {
                        if(importedCaption!=null) {
                            update("خواندن زیرنویس انتخاب‌شده از گوشی…");
                            captions=youtube.preparePersian(importedCaption,getInputData().getString("caption_language"),folder);
                        } else captions=youtube.persianCaptions(source,folder);
                    }
                    File original=imported!=null?imported:youtube.download(source,folder,config.width()==1080?1080:720);
                    long duration=duration(original);if(duration<=0) throw new IOException("مدت ویدئو قابل خواندن نیست");
                    if(persian) subtitleFrames=new SubtitleFrames(new File(folder,"subtitle-frames"),config.width(),this);
                    List<ClipPlan.Part> parts=ClipPlan.split(duration);int partNumber=0;
                    for(ClipPlan.Part part:parts) {
                        check();partNumber++;
                        Store.Clip clip=store.reserve(day,workKey,part.startMs,part.durationMs,source.title,source.url,source.channel);
                        if("done".equals(clip.state)) continue;
                        ensureSpace(200L*1024*1024);
                        update("ساخت "+clip.name()+" · بخش "+partNumber+" از "+parts.size()+" · "+source.title);
                        File output=new File(folder,"part.mp4");
                        try {
                            String timeline=subtitleFrames==null?null:subtitleFrames.timeline(captions,part).getAbsolutePath();
                            nativeTools.run(ClipPlan.ffmpeg(nativeTools.ffmpeg(),original.getAbsolutePath(),output.getAbsolutePath(),part,config.width(),timeline),30);
                            check();
                            long actual=duration(output);
                            if(actual<=0 || actual>60_100 || Math.abs(actual-part.durationMs)>250)
                                throw new IOException("مدت قطعهٔ خروجی با بازهٔ مورد انتظار تطبیق ندارد");
                            new Gallery(c,store).save(clip,output);saved++;
                            try { new Gallery(c,store).credits(clip.day); } catch(IOException e) { store.log("فهرست منابع: "+e.getMessage()); }
                        } finally { if(output.exists()) output.delete(); }
                    }
                    store.log("تمام ویدئو تقسیم شد: "+source.title+" · "+parts.size()+" قطعه · "+duration/1000.0+" ثانیه · "+source.license);
                    store.complete(source.id);
                    store.complete(workKey);
                    if(subtitleFrames!=null) {subtitleFrames.clear();subtitleFrames=null;}
                    // Only private temporary media owned by this app is removed.
                    if(original.exists()) original.delete();
                    if(importedCaption!=null) importedCaption.delete();
                    File[] leftovers=folder.listFiles();if(leftovers!=null) for(File f:leftovers) if(f.isFile()) f.delete();
                    folder.delete();
                } catch(Exception e) {
                    if(stopped() || e instanceof InterruptedException || e instanceof java.io.InterruptedIOException) throw e;
                    failed++;lastFailure=YoutubeSource.shortError(e);failedStage=lastStage;
                    store.log("پردازش کامل نشد: "+source.title+"\nمرحله: "+failedStage+"\n"+lastFailure);
                } finally {if(subtitleFrames!=null) subtitleFrames.clear();}
            }
            String message=sources.isEmpty()?"در رتبه‌های بررسی‌شدهٔ ChartsList ویدئوی تازهٔ قابل پردازش پیدا نشد؛ جزئیات در گزارش است."
                :saved+" قطعهٔ جدید در Download/clipYar ذخیره شد. ویدئوی ناموفق: "+failed;
            store.log(message);Notices.done(c,message);
            return failed==sources.size() && failed>0?failure(lastFailure,failedStage):Result.success();
        } catch(Exception e) {
            if(stopped() || e instanceof InterruptedException) { store.log("کار متوقف شد؛ فایل‌های کامل‌شده حفظ شده‌اند.");return Result.failure(); }
            if(e instanceof ChartsListSource.NotReady && getRunAttemptCount()<4) {
                String message=e.getMessage()+" · تلاش دوباره حدود "+(30*(getRunAttemptCount()+1))+" دقیقهٔ دیگر";
                store.log(message);Notices.done(c,message);return Result.retry();
            }
            String message="آماده‌سازی کامل نشد: "+YoutubeSource.shortError(e);
            store.log("مرحله: "+lastStage+"\n"+message);Notices.done(c,message);return failure(message,lastStage);
        } finally { PIPELINE_LOCK.unlock(); }
    }
    private static Result failure(String error,String stage) {
        return Result.failure(new Data.Builder().putString("error",error).putString("stage",stage).build());
    }
    private void ensureSpace(long bytes) throws IOException {
        if(new StatFs(getApplicationContext().getCacheDir().getAbsolutePath()).getAvailableBytes()<bytes)
            throw new IOException("فضای خالی گوشی کافی نیست؛ دست‌کم "+(bytes/1024/1024)+" مگابایت فضای خالی لازم است.");
    }
    private static long duration(File file) throws Exception {
        try(MediaMetadataRetriever reader=new MediaMetadataRetriever()) {
            reader.setDataSource(file.getAbsolutePath());String value=reader.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
            return value==null?0:Long.parseLong(value);
        }
    }
}
