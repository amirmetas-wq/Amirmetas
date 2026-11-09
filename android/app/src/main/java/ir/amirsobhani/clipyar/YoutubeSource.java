package ir.amirsobhani.clipyar;

import android.content.Context;
import android.content.SharedPreferences;
import com.yausername.youtubedl_android.*;
import ir.amirsobhani.clipyar.core.*;
import org.json.*;
import java.io.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;

public final class YoutubeSource {
    public interface Progress { void update(String text); boolean stopped(); }
    private final Progress progress;
    private final Store store;
    private final Context context;
    private final SharedPreferences rateState;
    private final Map<String,SubtitlePolicy.Track> captionTracks=new HashMap<>();
    private final String processId=UUID.randomUUID().toString();
    public YoutubeSource(Context context,Progress progress,Store store) {
        this.context=context.getApplicationContext();this.progress=progress;this.store=store;
        rateState=context.getSharedPreferences("youtube-rate-limit",Context.MODE_PRIVATE);
    }
    public void cancel() { YoutubeDL.getInstance().destroyProcessById(processId); }
    private String execute(YoutubeDLRequest request,int timeoutMinutes) throws Exception {
        if(progress.stopped()) throw new InterruptedException();
        long blockedUntil=rateState.getLong("blocked_until",0);
        if(SubtitlePolicy.remaining(System.currentTimeMillis(),blockedUntil)>0) throw limited(blockedUntil);
        request.addOption("--ignore-config").addOption("--no-warnings").addOption("--socket-timeout",20)
            .addOption("--retries",0).addOption("--fragment-retries",0).addOption("--extractor-retries",0).addOption("--no-progress");
        ScheduledExecutorService timer=Executors.newSingleThreadScheduledExecutor();
        timer.schedule(this::cancel,timeoutMinutes,TimeUnit.MINUTES);
        try { return YoutubeDL.getInstance().execute(request,processId,false,null).getOut(); }
        catch(Exception e) {
            if(progress.stopped()||e instanceof InterruptedException) throw e;
            String error=e.toString();
            if(SubtitlePolicy.isRateLimited(error)) {
                long until=System.currentTimeMillis()+SubtitlePolicy.COOLDOWN_MS;
                rateState.edit().putLong("blocked_until",until).apply();store.log("HTTP 429 دریافت شد؛ درخواست‌های خودکار متوقف شدند.");
                throw limited(until);
            }
            if(SubtitlePolicy.isHumanVerification(error)) throw new IOException("یوتیوب تأیید انسانی می‌خواهد؛ این مرحله باید توسط خودت انجام شود. نشست کروم به‌صورت خودکار به دریافت‌کنندهٔ برنامه منتقل نمی‌شود.\n"+shortError(e),e);
            throw e;
        }
        finally { timer.shutdownNow(); }
    }
    private static IOException limited(long until) {
        String time=java.time.format.DateTimeFormatter.ofPattern("HH:mm").withZone(DailyTime.TEHRAN).format(Instant.ofEpochMilli(until));
        return new IOException("یوتیوب تعداد درخواست‌ها را محدود کرده است (HTTP 429). برنامه تا "+time+" به وقت ایران درخواست تازه نمی‌فرستد؛ پایان این مکث تضمین رفع محدودیت نیست. می‌توانی ویدئو و فایل زیرنویس SRT/VTT را از گوشی انتخاب کنی.");
    }
    public VideoCandidate info(String url) throws Exception {
        String canonical=SourcePolicy.canonical(url);
        JSONObject j=new JSONObject(execute(new YoutubeDLRequest(canonical).addOption("--skip-download")
            .addOption("--no-playlist").addOption("--dump-single-json"),2));
        String id=j.optString("id");
        if(!canonical.endsWith(id) || id.length()!=11) throw new IOException("شناسهٔ ویدئو با لینک منبع تطبیق ندارد");
        long epoch=j.optLong("timestamp",0);
        if(epoch==0) epoch=j.optLong("release_timestamp",0);
        String live=j.optString("live_status","");
        SubtitlePolicy.Track track=SubtitlePolicy.choose(keys(j.optJSONObject("subtitles")),keys(j.optJSONObject("automatic_captions")));
        if(track!=null) captionTracks.put("yt-"+id,track);
        return new VideoCandidate("yt-"+id,canonical,j.optString("title",id),j.optString("channel",j.optString("uploader","")),
            j.optString("license","نامشخص"),epoch,j.optLong("view_count",-1),Math.round(j.optDouble("duration",0)*1000),
            j.optBoolean("is_live",false)||live.equals("is_live")||live.equals("is_upcoming")||live.equals("post_live"));
    }
    private static List<String> keys(JSONObject object) {
        List<String> result=new ArrayList<>();if(object!=null)object.keys().forEachRemaining(result::add);return result;
    }
    public List<Captions.Cue> persianCaptions(VideoCandidate video,File directory) throws Exception {
        SubtitlePolicy.Track track=captionTracks.get(video.id);
        if(track==null) throw new IOException("زیرنویس اصلی انگلیسی یا فارسی برای این ویدئو پیدا نشد. می‌توانی ویدئو و زیرنویس SRT/VTT را از گوشی انتخاب کنی؛ این نسخه تشخیص گفتار ندارد.");
        directory.mkdirs();
        File file=new File(directory,"captions."+track.key+".vtt");
        boolean valid=false;
        if(file.isFile()&&file.length()>0&&file.length()<=16*1024*1024) {
            try {Captions.parseSourceVtt(new String(java.nio.file.Files.readAllBytes(file.toPath()),java.nio.charset.StandardCharsets.UTF_8));valid=true;}catch(IOException ignored) { }
        }
        if(!valid) {
            progress.update("دریافت زیرنویس اصلی "+("en".equals(track.language)?"انگلیسی":"فارسی")+"…");
            YoutubeDLRequest request=new YoutubeDLRequest(video.url).addOption("--no-playlist").addOption("--skip-download")
                .addOption(track.automatic?"--write-auto-subs":"--write-subs").addOption("--sub-langs","^"+track.key+"$")
                .addOption("--sleep-subtitles",2).addOption("--sub-format","vtt")
                .addOption("--output",new File(directory,"captions.%(ext)s").getAbsolutePath());
            // A 429 exits here. No other subtitle track is requested in response.
            execute(request,5);
        } else store.log("زیرنویس اصلی از حافظهٔ موقت خوانده شد؛ دریافت تکرار نشد.");
        if(!file.isFile()||file.length()==0||file.length()>16*1024*1024) throw new IOException("فایل زیرنویس اصلی دریافت نشد؛ می‌توانی SRT/VTT را از گوشی انتخاب کنی.");
        store.log("زیرنویس انتخاب‌شده: "+track.key+" · "+(track.automatic?"زیرنویس یا ترجمهٔ خودکار یوتیوب":"زیرنویس ناشر"));
        return preparePersian(file,track.language,directory);
    }
    public List<Captions.Cue> preparePersian(File file,String language,File directory) throws Exception {
        if("en".equals(language)) {
            try(PersianTranslator translator=new PersianTranslator(progress,store)) {
                return translator.translateFile(file,directory,new Config(context).wifiOnly());
            }
        }
        if(!"fa".equals(language)) throw new IOException("برای فایل واردشده زبان انگلیسی یا فارسی را انتخاب کن");
        List<Captions.Cue> cues=Captions.reflow(Captions.parseFile(new String(java.nio.file.Files.readAllBytes(file.toPath()),java.nio.charset.StandardCharsets.UTF_8)));
        if(!Captions.hasPersianScript(cues)) throw new IOException("متن فایل واردشده فارسی نبود؛ اگر انگلیسی است زبان زیرنویس را انگلیسی انتخاب کن.");
        return cues;
    }
    public List<VideoCandidate> recentB1M() throws Exception {
        progress.update("بررسی ویدئوهای تازهٔ The B1M…");
        JSONObject j=new JSONObject(execute(new YoutubeDLRequest("https://www.youtube.com/@TheB1M/videos")
            .addOption("--flat-playlist").addOption("--playlist-end",8).addOption("--dump-single-json").addOption("--skip-download"),3));
        JSONArray entries=j.optJSONArray("entries");List<VideoCandidate> videos=new ArrayList<>();
        if(entries!=null) for(int i=0;i<entries.length();i++) {
            JSONObject entry=entries.optJSONObject(i);if(entry==null) continue;String id=entry.optString("id");
            if(!id.matches("[A-Za-z0-9_-]{11}")) continue;
            videos.add(new VideoCandidate("yt-"+id,"https://www.youtube.com/watch?v="+id,entry.optString("title",id),"The B1M","",0,entry.optLong("view_count",-1),Math.round(entry.optDouble("duration",0)*1000),false));
        }
        if(videos.isEmpty()) throw new IOException("فهرست تازه‌های The B1M دریافت نشد");
        return videos;
    }
    public File download(VideoCandidate v,File directory,int height) throws Exception {
        directory.mkdirs();File output=new File(directory,"source.mp4");
        if(output.exists() && output.length()>0) return output;
        progress.update("دریافت کامل: "+v.title);
        YoutubeDLRequest r=new YoutubeDLRequest(v.url).addOption("--no-playlist")
            .addOption("--format","bv*[height<="+height+"][ext=mp4]+ba[ext=m4a]/b[ext=mp4][height<="+height+"]/b[height<="+height+"]")
            .addOption("--merge-output-format","mp4").addOption("--remux-video","mp4")
            .addOption("--output",new File(directory,"download.%(ext)s").getAbsolutePath());
        execute(r,45);
        File downloaded=new File(directory,"download.mp4");
        if(!downloaded.exists() || downloaded.length()==0) throw new IOException("فایل ویدئو دریافت نشد");
        java.nio.file.Files.move(downloaded.toPath(),output.toPath(),java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        return output;
    }
    public static String shortError(Exception e) {
        String text=e.getMessage()==null?e.getClass().getSimpleName():e.getMessage();
        return text.length()>700?text.substring(text.length()-700):text;
    }
    public static JSONObject json(VideoCandidate v) throws JSONException {
        return new JSONObject().put("id",v.id).put("url",v.url).put("title",v.title).put("channel",v.channel)
            .put("license",v.license).put("timestamp",v.uploadedEpoch).put("views",v.views).put("duration",v.durationMs).put("live",v.live)
            .put("chartRank",v.chartRank).put("dailyViews",v.dailyViews).put("chartTotal",v.chartTotalViews).put("chartEpoch",v.chartEpoch).put("chartUrl",v.chartUrl);
    }
    public static VideoCandidate fromJson(JSONObject j) {
        return new VideoCandidate(j.optString("id"),j.optString("url"),j.optString("title"),j.optString("channel"),
            j.optString("license"),j.optLong("timestamp"),j.optLong("views"),j.optLong("duration"),j.optBoolean("live"))
            .withChart(j.optInt("chartRank"),j.optLong("dailyViews",-1),j.optLong("chartTotal",-1),j.optLong("chartEpoch"),j.optString("chartUrl"));
    }
    public static String evidence(VideoCandidate v) {
        if(v.chartRank<=0) return "";
        String time=java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(DailyTime.TEHRAN)
            .format(java.time.Instant.ofEpochSecond(v.chartEpoch));
        return "ChartsList · رتبهٔ "+v.chartRank+" · بازدید ۲۴ساعته: "+v.dailyViews+"\nبازدید کل در زمان آمار: "+v.chartTotalViews+"\nزمان آمار: "+time+" تهران\n"+v.chartUrl;
    }
}
