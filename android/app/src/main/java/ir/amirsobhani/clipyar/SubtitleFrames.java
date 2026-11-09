package ir.amirsobhani.clipyar;

import android.graphics.*;
import android.text.*;
import ir.amirsobhani.clipyar.core.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Android shapes Persian and mixed-direction text before FFmpeg composites it. */
public final class SubtitleFrames {
    private final File directory;
    private final int width,height;
    private final YoutubeSource.Progress progress;
    private final Map<String,File> frames=new HashMap<>();
    private final File blank;
    public SubtitleFrames(File directory,int width,YoutubeSource.Progress progress) throws IOException {
        this.directory=directory;this.width=width;this.height=width*4/9;this.progress=progress;
        if(!directory.isDirectory() && !directory.mkdirs()) throw new IOException("ساخت پوشهٔ موقت زیرنویس ممکن نشد");
        blank=render("");
    }
    private File render(String text) throws IOException {
        File cached=frames.get(text);if(cached!=null) return cached;
        if(progress.stopped()) throw new java.io.InterruptedIOException();
        File file=new File(directory,String.format(Locale.ROOT,"caption-%05d.png",frames.size()));
        Bitmap bitmap=Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888);
        try {
            if(!text.isEmpty()) {
                Canvas canvas=new Canvas(bitmap);TextPaint paint=new TextPaint(Paint.ANTI_ALIAS_FLAG|Paint.SUBPIXEL_TEXT_FLAG);
                paint.setColor(Color.WHITE);paint.setTypeface(Typeface.create("sans-serif",Typeface.NORMAL));
                float scale=width/720f;paint.setTextSize(34*scale);
                int padding=Math.round(20*scale),available=width-Math.round(100*scale);
                StaticLayout layout=StaticLayout.Builder.obtain(text,0,text.length(),paint,available)
                    .setAlignment(Layout.Alignment.ALIGN_CENTER).setTextDirection(TextDirectionHeuristics.FIRSTSTRONG_RTL)
                    .setLineSpacing(4*scale,1f).setIncludePad(true).build();
                // Keep every word visible even for an unusually long unbroken caption.
                while(layout.getHeight()+2*padding>height && paint.getTextSize()>18*scale) {
                    paint.setTextSize(paint.getTextSize()-2*scale);
                    layout=StaticLayout.Builder.obtain(text,0,text.length(),paint,available)
                        .setAlignment(Layout.Alignment.ALIGN_CENTER).setTextDirection(TextDirectionHeuristics.FIRSTSTRONG_RTL)
                        .setLineSpacing(3*scale,1f).setIncludePad(true).build();
                }
                if(layout.getHeight()+2*padding>height) throw new IOException("متن یک سطر زیرنویس بیش از فضای خوانا است");
                float left=(width-available)/2f,top=(height-layout.getHeight())/2f;
                Paint background=new Paint(Paint.ANTI_ALIAS_FLAG);background.setColor(0xDB111827);
                canvas.drawRoundRect(left-padding,top-padding,left+available+padding,top+layout.getHeight()+padding,14*scale,14*scale,background);
                canvas.save();canvas.translate(left,top);layout.draw(canvas);canvas.restore();
            }
            try(OutputStream out=new FileOutputStream(file)) {
                if(!bitmap.compress(Bitmap.CompressFormat.PNG,100,out)) throw new IOException("ساخت تصویر زیرنویس ممکن نشد");
            }
        } finally {bitmap.recycle();}
        frames.put(text,file);return file;
    }
    public File timeline(List<Captions.Cue> captions,ClipPlan.Part part) throws IOException {
        File script=new File(directory,"part.ffconcat");StringBuilder text=new StringBuilder("ffconcat version 1.0\n");long cursor=0;
        for(Captions.Cue cue:Captions.slice(captions,part)) {
            if(cue.startMs>cursor) append(text,blank,cue.startMs-cursor);
            append(text,render(cue.text),cue.endMs-cue.startMs);cursor=cue.endMs;
        }
        if(cursor<part.durationMs) append(text,blank,part.durationMs-cursor);
        // The final file makes the last explicit duration effective in the concat demuxer.
        text.append("file '").append(escape(blank.getAbsolutePath())).append("'\noption framerate 1000\n");
        java.nio.file.Files.write(script.toPath(),text.toString().getBytes(StandardCharsets.UTF_8));return script;
    }
    private static void append(StringBuilder text,File frame,long ms) {
        if(ms<=0) return;
        text.append("file '").append(escape(frame.getAbsolutePath())).append("'\noption framerate 1000\nduration ")
            .append(String.format(Locale.ROOT,"%.3f",ms/1000.0)).append('\n');
    }
    private static String escape(String value) {return value.replace("'","'\\''");}
    public void clear() {File[] files=directory.listFiles();if(files!=null) for(File file:files) file.delete();directory.delete();}
}
