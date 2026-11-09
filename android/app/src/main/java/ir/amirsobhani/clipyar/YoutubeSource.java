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
