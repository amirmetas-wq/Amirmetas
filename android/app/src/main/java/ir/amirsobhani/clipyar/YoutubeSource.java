package ir.amirsobhani.clipyar;

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
    private final String processId=UUID.randomUUID().toString();
    public YoutubeSource(Progress progress,Store store) { this.progress=progress;this.store=store; }
    public void cancel() { YoutubeDL.getInstance().destroyProcessById(processId); }
    private String execute(YoutubeDLRequest request,int timeoutMinutes) throws Exception {
        if(progress.stopped()) throw new InterruptedException();
        request.addOption("--ignore-config").addOption("--no-warnings").addOption("--socket-timeout",20)
            .addOption("--retries",2).addOption("--fragment-retries",2).addOption("--no-progress");
        ScheduledExecutorService timer=Executors.newSingleThreadScheduledExecutor();
        timer.schedule(this::cancel,timeoutMinutes,TimeUnit.MINUTES);
        try { return YoutubeDL.getInstance().execute(request,processId,false,null).getOut(); }
        finally { timer.shutdownNow(); }
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
        return new VideoCandidate("yt-"+id,canonical,j.optString("title",id),j.optString("channel",j.optString("uploader","")),
            j.optString("license","نامشخص"),epoch,j.optLong("view_count",-1),Math.round(j.optDouble("duration",0)*1000),
            j.optBoolean("is_live",false)||live.equals("is_live")||live.equals("is_upcoming")||live.equals("post_live"));
    }
    public List<VideoCandidate> discover(Config config) throws Exception {
        Map<String,Long> candidates=new HashMap<>();int successes=0,failures=0;
        String[] queries=config.queries().split("\\R");
        for(int i=0;i<Math.min(queries.length,10);i++) {
            if(progress.stopped()) throw new InterruptedException();
            String query=queries[i].trim();if(query.isEmpty()) continue;
            progress.update("جست‌وجوی امروز: "+query);
            try {
                JSONObject json=new JSONObject(execute(new YoutubeDLRequest(SourcePolicy.search(query,config.ccOnly()))
                    .addOption("--flat-playlist").addOption("--skip-download").addOption("--dump-single-json")
                    .addOption("--playlist-end",10),2));
                JSONArray entries=json.optJSONArray("entries");
                if(entries!=null) for(int n=0;n<entries.length();n++) {
                    JSONObject entry=entries.optJSONObject(n);if(entry==null) continue;
                    String id=entry.optString("id");
                    if(id.matches("[A-Za-z0-9_-]{11}")) candidates.merge(id,entry.optLong("view_count",0),Math::max);
                }
                successes++;
            } catch(Exception e) {
                if(progress.stopped() || e instanceof InterruptedException) throw e;
                failures++;store.log("جست‌وجو ناموفق: "+query+" · "+shortError(e));
            }
        }
        if(successes==0) throw new IOException("دسترسی به جست‌وجوی یوتیوب موفق نبود؛ اتصال اینترنت/فیلترشکن را بررسی کن.");
        List<String> ids=new ArrayList<>(candidates.keySet());
        ids.sort(Comparator.comparingLong((String id)->candidates.get(id)).reversed().thenComparing(id->id));
        List<VideoCandidate> details=new ArrayList<>();int checked=0;
        for(String id:ids.subList(0,Math.min(ids.size(),40))) {
            if(progress.stopped()) throw new InterruptedException();
            progress.update("بررسی زمان انتشار و بازدید: "+(++checked)+" از "+Math.min(ids.size(),40));
            try {
                VideoCandidate v=info("https://www.youtube.com/watch?v="+id);
                if(config.ccOnly() && !v.license.toLowerCase(Locale.ROOT).contains("creative commons")) continue;
                details.add(v);
            } catch(Exception e) {
                if(progress.stopped() || e instanceof InterruptedException) throw e;
                store.log("بررسی ویدئو ناموفق: "+id+" · "+shortError(e));
            }
        }
        List<VideoCandidate> result=VideoRanker.recent(details,Instant.now(),config.count(),config.maxMinutes()*60_000L);
        store.log("از "+candidates.size()+" سرنخ، "+checked+" ویدئو بررسی و "+result.size()+" ویدئوی واجد شرایط پیدا شد. جست‌وجوی ناموفق: "+failures);
        int unknown=0,tooLong=0;
        for(VideoCandidate v:details) {
            if(v.uploadedEpoch<=0) unknown++;
            if(config.maxMinutes()>0 && v.durationMs>config.maxMinutes()*60_000L) tooLong++;
        }
        if(unknown>0 || tooLong>0) store.log("کنارگذاشته‌شده در پالایش: زمان انتشار دقیق نامشخص "+unknown+"؛ بیشتر از سقف مدت "+tooLong);
        if(!ids.isEmpty() && details.isEmpty()) throw new IOException("هیچ صفحهٔ ویدئو قابل بررسی نبود؛ نتیجه به معنی نبود ویدئوی تازه نیست.");
        return result;
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
            .put("license",v.license).put("timestamp",v.uploadedEpoch).put("views",v.views).put("duration",v.durationMs).put("live",v.live);
    }
    public static VideoCandidate fromJson(JSONObject j) {
        return new VideoCandidate(j.optString("id"),j.optString("url"),j.optString("title"),j.optString("channel"),
            j.optString("license"),j.optLong("timestamp"),j.optLong("views"),j.optLong("duration"),j.optBoolean("live"));
    }
}
