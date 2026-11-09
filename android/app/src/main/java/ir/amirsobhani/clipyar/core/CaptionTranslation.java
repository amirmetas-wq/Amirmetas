package ir.amirsobhani.clipyar.core;

import java.io.IOException;
import java.util.*;

/** Translate complete source cues; keep timings intact and publish no partial translation. */
public final class CaptionTranslation {
    public interface Engine {String translate(String text) throws Exception;}
    public interface Progress {void update(int completed,int total) throws Exception;}
    private CaptionTranslation() { }
    public static List<Captions.Cue> toPersian(List<Captions.Cue> source,Engine engine,Progress progress) throws Exception {
        if(source.isEmpty()) throw new IOException("متن زیرنویس برای ترجمه خالی است");
        List<Captions.Cue> translated=new ArrayList<>();Map<String,String> memo=new HashMap<>();int done=0;
        for(Captions.Cue cue:source) {
            progress.update(done,source.size());String text=memo.get(cue.text);
            if(text==null) {
                text=engine.translate(cue.text);
                if(text==null||text.trim().isEmpty()) throw new IOException("ترجمهٔ یک بخش از زیرنویس خالی بود؛ خروجی ناقص ذخیره نشد");
                text=text.trim();memo.put(cue.text,text);
            }
            translated.add(new Captions.Cue(cue.startMs,cue.endMs,text));done++;
        }
        if(!Captions.hasPersianScript(translated)) throw new IOException("خروجی مترجم فارسی نبود؛ قطعهٔ بدون زیرنویس فارسی ساخته نشد");
        progress.update(done,source.size());return Captions.reflow(translated);
    }
}
