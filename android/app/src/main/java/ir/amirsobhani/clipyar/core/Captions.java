package ir.amirsobhani.clipyar.core;

import java.io.IOException;
import java.util.*;
import java.util.regex.*;

/** Timed text only. All offsets are milliseconds in the original, complete video. */
public final class Captions {
    public static final class Cue {
        public final long startMs,endMs;
        public final String text;
        public Cue(long startMs,long endMs,String text) {
            this.startMs=startMs;this.endMs=endMs;this.text=text;
        }
    }
    private static final Pattern BLOCK=Pattern.compile("(?m)^([0-9:.]+)\\s+-->\\s+([0-9:.]+)[^\\n]*\\n([\\s\\S]*?)(?=\\n\\s*\\n|\\z)");
    private Captions() { }
    public static List<Cue> parseVtt(String source) throws IOException {
        return reflow(parseSourceVtt(source));
    }
    /** Keeps full source sentences together for on-device translation. */
    public static List<Cue> parseSourceVtt(String source) throws IOException {
        source=source.replace("\r\n","\n").replace('\r','\n').replace("\uFEFF","");
        if(!source.startsWith("WEBVTT")) throw new IOException("فایل زیرنویس WebVTT معتبر نیست");
        List<Cue> raw=new ArrayList<>();Matcher blocks=BLOCK.matcher(source);
        while(blocks.find()) {
            long start=time(blocks.group(1)),end=time(blocks.group(2));
            String text=blocks.group(3).replaceAll("<[^>]*>","")
                .replace("&nbsp;"," ").replace("&amp;","&").replace("&lt;","<").replace("&gt;",">")
                .replace("&quot;","\"").replace("&#39;","'").replaceAll("\\s+"," ").trim();
            if(end>start && !text.isEmpty()) raw.add(new Cue(start,end,text));
            if(raw.size()>100_000) throw new IOException("تعداد سطرهای زیرنویس غیرمنتظره است");
        }
        raw.sort(Comparator.comparingLong(c->c.startMs));
        List<Cue> unique=new ArrayList<>();
        for(Cue cue:raw) {
            if(!unique.isEmpty()) {
                Cue previous=unique.get(unique.size()-1);
                if(previous.startMs==cue.startMs) {unique.set(unique.size()-1,cue);continue;}
                if(previous.text.equals(cue.text) && previous.endMs>=cue.startMs) {
                    unique.set(unique.size()-1,new Cue(previous.startMs,Math.max(previous.endMs,cue.endMs),cue.text));continue;
                }
            }
            unique.add(cue);
        }
        List<Cue> result=new ArrayList<>();
        for(int i=0;i<unique.size();i++) {
            Cue cue=unique.get(i);long end=i+1<unique.size()?Math.min(cue.endMs,unique.get(i+1).startMs):cue.endMs;
            if(end<=cue.startMs) continue;
            result.add(new Cue(cue.startMs,end,cue.text));
        }
        if(result.isEmpty()) throw new IOException("زیرنویس زمان‌دار قابل خواندن پیدا نشد");
        return Collections.unmodifiableList(result);
    }
    public static List<Cue> parseFile(String source) throws IOException {
        source=source.replace("\r\n","\n").replace('\r','\n').replace("\uFEFF","").trim();
        if(source.startsWith("WEBVTT")) return parseSourceVtt(source);
        Pattern srt=Pattern.compile("(?m)^([0-9:]+),([0-9]{3})(\\s+-->\\s+[0-9:]+),([0-9]{3})([^\\n]*)$");
        Matcher matcher=srt.matcher(source);
        if(!matcher.find()) throw new IOException("فایل زیرنویس باید SRT یا WebVTT زمان‌دار باشد");
        return parseSourceVtt("WEBVTT\n\n"+matcher.replaceAll("$1.$2$3.$4$5")+"\n");
    }
    public static List<Cue> reflow(List<Cue> source) {
        List<Cue> result=new ArrayList<>();
        for(Cue cue:source) {
            long end=cue.endMs;
            // Long sentences become successive readable screens, without dropping words.
            List<String> screens=new ArrayList<>();StringBuilder screen=new StringBuilder();
            for(String word:cue.text.split(" ")) {
                if(screen.length()>0 && screen.length()+word.length()+1>120) {screens.add(screen.toString());screen.setLength(0);}
                if(screen.length()>0) screen.append(' ');screen.append(word);
            }
            if(screen.length()>0) screens.add(screen.toString());
            for(int n=0;n<screens.size();n++) {
                long a=cue.startMs+(end-cue.startMs)*n/screens.size(),b=cue.startMs+(end-cue.startMs)*(n+1)/screens.size();
                if(b>a) result.add(new Cue(a,b,screens.get(n)));
            }
        }
        return Collections.unmodifiableList(result);
    }
    public static String toVtt(List<Cue> source) {
        StringBuilder text=new StringBuilder("WEBVTT\n\n");
        for(Cue cue:source) text.append(clock(cue.startMs)).append(" --> ").append(clock(cue.endMs)).append('\n')
            .append(cue.text.replace('\n',' ').replace("&","&amp;").replace("<","&lt;").replace(">","&gt;")).append("\n\n");
        return text.toString();
    }
    private static String clock(long ms) {
        return String.format(Locale.ROOT,"%02d:%02d:%02d.%03d",ms/3_600_000,(ms/60_000)%60,(ms/1000)%60,ms%1000);
    }
    private static long time(String value) throws IOException {
        try {
            String[] fields=value.split(":");
            if(fields.length<2||fields.length>3||!fields[fields.length-1].matches("[0-5][0-9]\\.[0-9]{3}")) throw new NumberFormatException();
            long minutes=Long.parseLong(fields[fields.length-2]);
            if(fields.length==3 && minutes>59) throw new NumberFormatException();
            long hours=fields.length==3?Long.parseLong(fields[0]):0;
            return hours*3_600_000+minutes*60_000+Math.round(Double.parseDouble(fields[fields.length-1])*1000);
        } catch(RuntimeException e) {throw new IOException("زمان‌بندی زیرنویس معتبر نیست: "+value,e);}
    }
    /** Clip boundaries may fall inside a cue. The text is retained on both sides. */
    public static List<Cue> slice(List<Cue> captions,ClipPlan.Part part) {
        List<Cue> result=new ArrayList<>();long end=part.startMs+part.durationMs;
        for(Cue cue:captions) {
            if(cue.startMs>=end) break;
            if(cue.endMs<=part.startMs) continue;
            result.add(new Cue(Math.max(cue.startMs,part.startMs)-part.startMs,Math.min(cue.endMs,end)-part.startMs,cue.text));
        }
        return result;
    }
    public static boolean hasPersianScript(List<Cue> captions) {
        int letters=0;
        for(Cue cue:captions) for(int i=0;i<cue.text.length();i++) {
            char ch=cue.text.charAt(i);if(ch>=0x0600&&ch<=0x06ff&&Character.isLetter(ch)) letters++;
            if(letters>=10) return true;
        }
        return false;
    }
}
