package ir.amirsobhani.clipyar.data;

import ir.amirsobhani.clipyar.core.*;
import org.junit.Test;
import static org.junit.Assert.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;

public final class ChartsListParserTest {
    private String fixture() {
        return "<h1>Most viewed videos in the past 24 hours</h1><p>Updated 2026-11-09 09:15 UTC</p>"
            +"<table><tr><th>#</th><th>+/−</th><th>Video</th><th>24h views</th><th>Total views</th><th>Likes</th></tr>"
            +"<tr><td>1</td><td>=</td><td><a href='/youtube/video/aaaaaaaaaaa/old-video/'>Old video</a><small>A</small></td><td>10,000</td><td>1,000,000</td><td>90</td></tr>"
            +"<tr><td>2</td><td>NEW</td><td><a href='/youtube/video/bbbbbbbbbbb/new-video/'>New video</a></td><td>9,000</td><td>20,000,000</td><td>20</td></tr></table>"
            +"<footer>Last updated 2026-11-09 09:15 UTC</footer>";
    }
    @Test public void dailyColumnAndIdsAreParsedWithoutUsingTotalViewOrder() throws Exception {
        DailyChart result=ChartsListParser.parse(fixture());
        assertEquals(2,result.entries.size());assertEquals("aaaaaaaaaaa",result.entries.get(0).videoId);
        assertEquals(10_000,result.entries.get(0).dailyViews);assertEquals(1_000_000,result.entries.get(0).totalViews);
        assertEquals(20_000_000,result.entries.get(1).totalViews);
        assertEquals("2026-11-09T09:15:00Z",result.updatedAt.toString());
    }
    @Test public void unknownLayoutAndUnverifiableNumbersFailClosed() {
        for(String html:new String[]{fixture().replace("24h views","Views"),fixture().replace("10,000","ten thousand"),
                fixture().replace("9,000","11,000"),fixture().replace("bbbbbbbbbbb","aaaaaaaaaaa"),
                fixture().replace("href='/youtube/video/aaaaaaaaaaa/old-video/'","href='https://evil.test/youtube/video/aaaaaaaaaaa/'"),
                fixture().replace("2026-11-09 09:15 UTC</footer>","2026-11-08 09:15 UTC</footer>")}) {
            try {ChartsListParser.parse(html);fail("Invalid chart was accepted");}catch(IOException expected) { }
        }
    }
    @Test public void livePublicPageUsesTheSameParserAsThePhone() throws Exception {
        if(!"true".equals(System.getenv("GITHUB_ACTIONS"))) return;
        HttpURLConnection connection=(HttpURLConnection)new URL(ChartsListParser.URL).openConnection();
        connection.setConnectTimeout(20_000);connection.setReadTimeout(30_000);connection.setInstanceFollowRedirects(false);
        connection.setRequestProperty("User-Agent","ClipYar/2.0 (Android; daily chart reader)");
        try {
            assertEquals("ChartsList public HTTP access",200,connection.getResponseCode());
            byte[] bytes;
            try(InputStream in=connection.getInputStream()) {bytes=in.readNBytes(5*1024*1024);}
            DailyChart result=ChartsListParser.parse(new String(bytes,StandardCharsets.UTF_8));
            assertTrue(result.entries.size()>0);assertEquals(1,result.entries.get(0).rank);
            System.out.println("LIVE_CHARTSLIST_PASS rows="+result.entries.size()+" snapshot="+result.updatedAt
                +" firstId="+result.entries.get(0).videoId+" daily="+result.entries.get(0).dailyViews+" total="+result.entries.get(0).totalViews);
        } finally {connection.disconnect();}
    }
}
