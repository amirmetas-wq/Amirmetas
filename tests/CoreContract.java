import ir.amirsobhani.clipyar.core.*;
import java.time.*;
import java.util.*;

public final class CoreContract {
    static int checks;
    static void expect(boolean value,String message) {checks++;if(!value) throw new AssertionError(message);}
    public static void main(String[] args) {
        expect(DailyTime.next(Instant.parse("2026-11-09T06:29:59Z"),10,0).toInstant().equals(Instant.parse("2026-11-09T06:30:00Z")),"before ten");
        expect(DailyTime.next(Instant.parse("2026-11-09T06:30:00Z"),10,0).toInstant().equals(Instant.parse("2026-11-10T06:30:00Z")),"next day at ten");
        expect(DailyTime.next(Instant.parse("2026-12-31T21:30:00Z"),10,0).toLocalDate().equals(LocalDate.of(2027,1,1)),"year rollover");
        TimeZone.setDefault(TimeZone.getTimeZone("America/Los_Angeles"));
        expect(DailyTime.day(Instant.parse("2026-11-09T21:00:00Z")).equals("2026-11-10"),"Iran day independent of device");
        for(long duration:new long[]{1,59_999,60_000,60_001,120_000,123_456,1_800_000}) {
            List<ClipPlan.Part> parts=ClipPlan.split(duration);long cursor=0;
            for(ClipPlan.Part part:parts) {
                expect(part.startMs==cursor,"gap or repeat");expect(part.durationMs>0 && part.durationMs<=60_000,"part length");cursor+=part.durationMs;
            }
            expect(cursor==duration,"complete source coverage");
            expect(parts.size()==(duration+59_999)/60_000,"no empty last part");
        }
        boolean rejected=false;try{ClipPlan.split(0);}catch(IllegalArgumentException e){rejected=true;}expect(rejected,"zero rejected");
        String id="dQw4w9WgXcQ",url="https://www.youtube.com/watch?v="+id;
        expect(SourcePolicy.canonical("https://youtu.be/"+id+"?si=test").equals(url),"short link normalized");
        expect(SourcePolicy.canonical("https://www.youtube.com/shorts/"+id).equals(url),"shorts link");
        for(String invalid:new String[]{"https://youtube.com.evil.test/watch?v="+id,"http://www.youtube.com/watch?v="+id,
            "https://evil@youtube.com/watch?v="+id,"file:///tmp/video.mp4","https://youtube.com/watch?v=$(touch)","https://youtube.com:444/watch?v="+id}) {
            rejected=false;try{SourcePolicy.canonical(invalid);}catch(IllegalArgumentException e){rejected=true;}expect(rejected,"unsafe URL accepted: "+invalid);
        }
        Instant now=Instant.parse("2026-11-09T06:30:00Z");long epoch=now.getEpochSecond();
        List<VideoCandidate> candidates=Arrays.asList(v("old",epoch-86401,1_000_000),v("unknown",0,8_000_000),v("future",epoch+1,7_000_000),
            v("fresh",epoch-20,90),v("fresh",epoch-20,95),v("top",epoch-200,100),v("edge",epoch-86400,50));
        List<VideoCandidate> ranked=VideoRanker.recent(candidates,now,5,0);
        expect(ranked.size()==3,"strict cutoff and dedupe");expect(ranked.get(0).id.equals("top"),"views order");
        expect(ranked.get(1).views==95,"best duplicate");expect(ranked.get(2).id.equals("edge"),"rolling boundary inclusive");
        expect(VideoRanker.recent(candidates,now,1,0).size()==1,"limit");
        expect(VideoRanker.recent(Collections.singletonList(new VideoCandidate("long","","","","",epoch,10,61_000,false)),now,5,60_000).isEmpty(),"long source skipped, not truncated");
        if(args.length>0 && args[0].equals("ffmpeg")) {
            System.out.println(String.join("\u001f",ClipPlan.ffmpeg("ffmpeg",args[1],args[2],new ClipPlan.Part(Long.parseLong(args[3]),Long.parseLong(args[4])),Integer.parseInt(args[5]))));
        } else System.out.println("PASS: "+checks+" core assertions");
    }
    static VideoCandidate v(String id,long timestamp,long views) {return new VideoCandidate(id,"https://youtube.com/"+id,id,"","",timestamp,views,90_000,false);}
}
