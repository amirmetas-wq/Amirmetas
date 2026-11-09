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
        Instant now=Instant.parse("2026-11-09T10:30:00Z");
        expect(DailyTime.next(Instant.parse("2026-11-09T10:29:59Z"),14,0).toInstant().equals(now),"14 Tehran");
        expect(DailyTime.next(now,14,0).toInstant().equals(now.plusSeconds(86400)),"next day 14");
        DailyChart current=new DailyChart(Instant.parse("2026-11-09T09:15:00Z"),Collections.emptyList());
        expect(current.isToday(now),"today chart accepted");
        expect(!new DailyChart(Instant.parse("2026-11-08T09:15:00Z"),Collections.emptyList()).isToday(now),"yesterday chart rejected");
        expect(!new DailyChart(now.plusSeconds(600),Collections.emptyList()).isToday(now),"future chart rejected");
        VideoCandidate old=new VideoCandidate("old","","old","","",1,100_000_000,90_000,false).withChart(1,10_000,90_000_000,now.getEpochSecond(),"https://chartslist.com/");
        expect(old.chartRank==1 && old.dailyViews==10_000 && old.uploadedEpoch==1,"old upload retains daily-view evidence");
        try {
            List<Captions.Cue> captions=Captions.parseVtt("WEBVTT\n\n00:00:58.500 --> 00:01:02.000 align:start\n<c>حلقهٔ فشاری و سازهٔ کششی</c>\n\n00:01:02.000 --> 00:01:04.000\nاین بخش دوم &amp; ادامهٔ توضیح است\n");
            expect(captions.size()==2,"VTT cue extraction");expect(Captions.hasPersianScript(captions),"actual Persian text verified");
            List<Captions.Cue> first=Captions.slice(captions,new ClipPlan.Part(0,60_000));
            List<Captions.Cue> second=Captions.slice(captions,new ClipPlan.Part(60_000,60_000));
            expect(first.size()==1 && first.get(0).startMs==58_500 && first.get(0).endMs==60_000,"caption clipped at first boundary");
            expect(second.size()==2 && second.get(0).startMs==0 && second.get(0).endMs==2_000,"caption survives into next file at t=0");
            expect(!first.get(0).text.contains("<c>"),"VTT tags removed");expect(second.get(1).text.contains(" & "),"HTML entities decoded");
            expect(Captions.slice(captions,new ClipPlan.Part(64_000,500)).isEmpty(),"no sticky subtitle after its end");
            List<Captions.Cue> repeated=Captions.parseVtt("WEBVTT\n\n00:00:01.000 --> 00:00:03.000\nمتن تکراری فارسی\n\n00:00:02.000 --> 00:00:04.000\nمتن تکراری فارسی\n");
            expect(repeated.size()==1 && repeated.get(0).startMs==1_000 && repeated.get(0).endMs==4_000,"repeated rolling text merged");
            expect(!Captions.hasPersianScript(Captions.parseVtt("WEBVTT\n\n00:00:01.000 --> 00:00:02.000\nEnglish only\n")),"English cannot be labelled Persian");
            StringBuilder sentence=new StringBuilder();for(int i=0;i<100;i++) sentence.append("کلمه").append(i).append(' ');
            List<Captions.Cue> longer=Captions.parseVtt("WEBVTT\n\n00:00:00.000 --> 00:00:10.000\n"+sentence+"\n");
            expect(longer.size()>1,"long sentences become readable screens");
            String joined=String.join(" ",longer.stream().map(c->c.text).toArray(String[]::new));
            expect(joined.equals(sentence.toString().trim()),"long caption retains every word in order");
            long cursor=0;for(Captions.Cue cue:longer) {expect(cue.startMs==cursor,"long cue has no timing gap");cursor=cue.endMs;}expect(cursor==10_000,"long cue retains full duration");
            for(String broken:new String[]{"Not a VTT","WEBVTT\n\ntext without timing","WEBVTT\n\n00:99:01.000 --> 00:99:02.000\nفارسی\n"}) {
                rejected=false;try{Captions.parseVtt(broken);}catch(java.io.IOException e){rejected=true;}expect(rejected,"invalid captions rejected");
            }
            List<String> command=ClipPlan.ffmpeg("ffmpeg","source.mp4","part.mp4",new ClipPlan.Part(60_000,5_000),720,"captions.ffconcat");
            expect(command.contains("concat")&&command.contains("captions.ffconcat"),"real command includes timed caption input");
            expect(command.get(command.indexOf("-ss")+1).equals("60.000"),"seek applies to video input");
            expect(command.get(command.indexOf("-filter_complex")+1).contains("eof_action=pass:repeatlast=0"),"caption cannot stick past its end");
        } catch(java.io.IOException e) {throw new AssertionError(e);}
        try {
            SubtitlePolicy.Track picked=SubtitlePolicy.choose(Arrays.asList("en"),Arrays.asList("fa","fa-en"));
            expect(picked!=null && picked.key.equals("en")&&!picked.automatic,"original English chosen over translated Persian that failed on phone");
            picked=SubtitlePolicy.choose(Collections.emptyList(),Arrays.asList("fa","en-orig","en"));
            expect(picked.key.equals("en-orig")&&picked.language.equals("en")&&picked.automatic,"original automatic English preferred to translated fa");
            picked=SubtitlePolicy.choose(Arrays.asList("en","fa"),Arrays.asList("en-orig"));
            expect(picked.key.equals("fa")&&!picked.automatic,"native publisher Persian needs no machine translation");
            picked=SubtitlePolicy.choose(Arrays.asList("en-GB"),Collections.emptyList());
            expect(picked.key.equals("en-GB"),"actual regional English caption supported");
            expect(SubtitlePolicy.choose(Collections.emptyList(),Arrays.asList("en-fr","fa-en"))==null,"translated language variants not passed off as original tracks");
            expect(SubtitlePolicy.isRateLimited("ERROR: Unable to download video subtitles for 'fa': HTTP Error 429: Too Many Requests"),"exact screenshot error classified as rate limit");
            expect(SubtitlePolicy.isRateLimited("HTTP 429"),"short HTTP status supported");
            expect(!SubtitlePolicy.isRateLimited("Video id 429foobar missing, HTTP Error 403"),"unrelated digits and 403 not classified as rate limit");
            expect(!SubtitlePolicy.isHumanVerification("HTTP Error 429: Too Many Requests"),"429 is not a human verification prompt");
            expect(SubtitlePolicy.isHumanVerification("Sign in to confirm you’re not a bot"),"actual human verification detected separately");
            expect(SubtitlePolicy.remaining(1_000,1_000+SubtitlePolicy.COOLDOWN_MS)==3_600_000,"rate-limit cooldown retained");
            expect(SubtitlePolicy.remaining(4_000_000,3_601_000)==0,"cooldown expires without claiming service recovery");
            List<Captions.Cue> source=Captions.parseFile("1\r\n00:00:58,500 --> 00:01:02,000\r\nThe compression ring supports the roof.\r\n\r\n2\r\n00:01:04,000 --> 00:01:06,250\r\nThe compression ring supports the roof.\r\n");
            expect(source.size()==2&&source.get(0).startMs==58_500&&source.get(1).endMs==66_250,"imported SRT timings parsed exactly");
            int[] calls={0};List<Captions.Cue> translated=CaptionTranslation.toPersian(source,text->{calls[0]++;return "حلقهٔ فشاری سقف را نگه می‌دارد.";},(a,b)->{});
            expect(calls[0]==1,"identical sentences translated once");
            expect(translated.size()==2 && translated.get(0).startMs==58_500 && translated.get(0).endMs==62_000 && translated.get(1).startMs==64_000,"translation retains every source cue and timing");
            List<Captions.Cue> next=Captions.slice(translated,new ClipPlan.Part(60_000,60_000));
            expect(next.get(0).startMs==0&&next.get(0).endMs==2_000,"locally translated Persian survives minute boundary");
            List<Captions.Cue> roundtrip=Captions.parseVtt(Captions.toVtt(Collections.singletonList(new Captions.Cue(1234,5678,"اگر A < B و C > D & E باشد"))));
            expect(roundtrip.get(0).text.equals("اگر A < B و C > D & E باشد")&&roundtrip.get(0).startMs==1234,"VTT cache preserves engineering comparisons, Persian and timing");
            rejected=false;try {CaptionTranslation.toPersian(source,text->"still English",(a,b)->{});}catch(java.io.IOException e){rejected=true;}expect(rejected,"untranslated English cannot become a successful Persian output");
            rejected=false;try {CaptionTranslation.toPersian(source,text->"",(a,b)->{});}catch(java.io.IOException e){rejected=true;}expect(rejected,"empty engine result fails instead of dropping a caption");
            rejected=false;try {CaptionTranslation.toPersian(source,text->"نمونهٔ ترجمهٔ فارسی",(done,total)->{if(done==1)throw new InterruptedException("cancel");});}
            catch(InterruptedException e){rejected=true;}expect(rejected,"cancellation propagates instead of publishing a partial translation");
        } catch(Exception e) {throw new AssertionError(e);}
        if(args.length>0 && args[0].equals("ffmpeg")) {
            System.out.println(String.join("\u001f",ClipPlan.ffmpeg("ffmpeg",args[1],args[2],new ClipPlan.Part(Long.parseLong(args[3]),Long.parseLong(args[4])),Integer.parseInt(args[5]))));
        } else if(args.length>0 && args[0].equals("ffmpeg-subtitles")) {
            System.out.println(String.join("\u001f",ClipPlan.ffmpeg("ffmpeg",args[1],args[2],new ClipPlan.Part(Long.parseLong(args[3]),Long.parseLong(args[4])),Integer.parseInt(args[5]),args[6])));
        } else System.out.println("PASS: "+checks+" core assertions");
    }
    static VideoCandidate v(String id,long timestamp,long views) {return new VideoCandidate(id,"https://youtube.com/"+id,id,"","",timestamp,views,90_000,false);}
}
