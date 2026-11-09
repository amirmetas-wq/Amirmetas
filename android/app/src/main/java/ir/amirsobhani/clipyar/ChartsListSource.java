package ir.amirsobhani.clipyar;

import ir.amirsobhani.clipyar.core.*;
import ir.amirsobhani.clipyar.data.ChartsListParser;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;

public final class ChartsListSource {
    public static final String CACHE_PREFIX="chartslist-v2:";
    private final YoutubeSource youtube;
    private final YoutubeSource.Progress progress;
    private final Store store;
    public static final class NotReady extends IOException {
        public NotReady(String text) {super(text);}
    }
    public ChartsListSource(YoutubeSource youtube,YoutubeSource.Progress progress,Store store) {
        this.youtube=youtube;this.progress=progress;this.store=store;
    }
    public List<VideoCandidate> discover(Config config,String day) throws Exception {
        progress.update("دریافت جدول بازدید ۲۴ساعته از ChartsList…");
        DailyChart chart=ChartsListParser.parse(fetch());
        store.log("ChartsList · زمان آمار: "+chart.updatedAt+" · "+chart.entries.size()+" رتبه در صفحهٔ اول");
        if(!chart.isToday(Instant.now())) throw new NotReady("آمار امروز ChartsList هنوز آماده نیست؛ آخرین آمار: "+chart.updatedAt);
        List<VideoCandidate> selected=new ArrayList<>();int checked=0,verified=0;
        for(ChartEntry entry:chart.entries) {
            if(progress.stopped()) throw new InterruptedException();
            if(selected.size()>=config.count() || checked>=60) break;
            if(store.completed("yt-"+entry.videoId)) continue;
            checked++;progress.update("بررسی رتبهٔ "+entry.rank+" ChartsList · بازدید ۲۴ساعته: "+entry.dailyViews);
            try {
                VideoCandidate v=youtube.info("https://www.youtube.com/watch?v="+entry.videoId);
                verified++;
                String reason=null;
                if(v.live) reason="پخش زنده یا آماده‌نشده";
                else if(v.durationMs<=0) reason="مدت نامشخص";
                else if(config.maxMinutes()>0 && v.durationMs>config.maxMinutes()*60_000L) reason="بیشتر از سقف مدت انتخاب‌شده";
                else if(config.ccOnly() && !v.license.toLowerCase(Locale.ROOT).contains("creative commons")) reason="مجوز Creative Commons تأیید نشد";
                if(reason!=null) {store.log("رتبهٔ "+entry.rank+" کنار گذاشته شد: "+reason);continue;}
                VideoCandidate result=v.withChart(entry.rank,entry.dailyViews,entry.totalViews,chart.updatedAt.getEpochSecond(),entry.pageUrl);
                selected.add(result);
                store.log("انتخاب رتبهٔ "+entry.rank+" · "+entry.dailyViews+" بازدید در ۲۴ ساعت · "+v.title);
            } catch(Exception e) {
                if(progress.stopped() || e instanceof InterruptedException) throw e;
                store.log("رتبهٔ "+entry.rank+" قابل بررسی نبود: "+YoutubeSource.shortError(e));
            }
        }
        store.log("منبع انتخاب ChartsList؛ "+checked+" صفحهٔ ویدئو بررسی و "+selected.size()+" ویدئو انتخاب شد. بازدید کل معیار مرتب‌سازی نیست.");
        if(checked>0 && verified==0) throw new IOException("جدول ChartsList خوانده شد، اما دسترسی به صفحه‌های یوتیوب موفق نبود.");
        return selected;
    }
    private String fetch() throws IOException {
        HttpURLConnection connection=(HttpURLConnection)new URL(ChartsListParser.URL).openConnection();
        connection.setConnectTimeout(20_000);connection.setReadTimeout(30_000);connection.setInstanceFollowRedirects(false);
        connection.setRequestProperty("User-Agent","ClipYar/2.0 (Android; daily chart reader)");
        connection.setRequestProperty("Accept","text/html");connection.setRequestProperty("Cache-Control","no-cache");
        try {
            int status=connection.getResponseCode();
            if(status!=200) throw new IOException("دریافت ChartsList ناموفق بود؛ HTTP "+status);
            try(InputStream input=connection.getInputStream();ByteArrayOutputStream bytes=new ByteArrayOutputStream()) {
                byte[] buffer=new byte[16*1024];int n;
                while((n=input.read(buffer))!=-1) {
                    if(progress.stopped()) throw new java.io.InterruptedIOException();
                    if(bytes.size()+n>5*1024*1024) throw new IOException("اندازهٔ صفحهٔ ChartsList غیرمنتظره است");
                    bytes.write(buffer,0,n);
                }
                return bytes.toString(StandardCharsets.UTF_8.name());
            }
        } finally {connection.disconnect();}
    }
}
