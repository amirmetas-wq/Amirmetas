package ir.amirsobhani.clipyar;

import android.content.Context;
import com.yausername.youtubedl_android.YoutubeDL;
import com.yausername.ffmpeg.FFmpeg;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;

public final class NativeTools {
    private final Context context;
    private volatile Process current;
    public NativeTools(Context c) { context=c; }
    public synchronized void init() throws Exception {
        YoutubeDL.getInstance().init(context);
        FFmpeg.getInstance().init(context);
        File directory=new File(context.getNoBackupFilesDir(),"youtubedl-android/yt-dlp");
        File marker=new File(directory,"clipyar-version");
        String installed=marker.exists()?new String(Files.readAllBytes(marker.toPath()),StandardCharsets.UTF_8):"";
        if(!BuildConfig.EXTRACTOR_VERSION.equals(installed)) {
            File temp=new File(directory,"yt-dlp.clipyar.tmp");
            try(InputStream in=context.getAssets().open("yt-dlp");OutputStream out=new FileOutputStream(temp)) {
                byte[] buffer=new byte[64*1024];int n;while((n=in.read(buffer))!=-1) out.write(buffer,0,n);
            }
            Files.move(temp.toPath(),new File(directory,"yt-dlp").toPath(),StandardCopyOption.REPLACE_EXISTING);
            Files.write(marker.toPath(),BuildConfig.EXTRACTOR_VERSION.getBytes(StandardCharsets.UTF_8));
        }
    }
    public String ffmpeg() { return new File(context.getApplicationInfo().nativeLibraryDir,"libffmpeg.so").getAbsolutePath(); }
    public String run(List<String> command,long timeoutMinutes) throws Exception {
        ProcessBuilder builder=new ProcessBuilder(command).redirectErrorStream(true);
        String base=new File(context.getNoBackupFilesDir(),"youtubedl-android/packages").getAbsolutePath();
        builder.environment().put("LD_LIBRARY_PATH",base+"/ffmpeg/usr/lib:"+base+"/python/usr/lib");
        builder.environment().put("TMPDIR",context.getCacheDir().getAbsolutePath());
        Process process=builder.start();current=process;
        ScheduledExecutorService timer=Executors.newSingleThreadScheduledExecutor();
        timer.schedule(process::destroyForcibly,timeoutMinutes,TimeUnit.MINUTES);
        StringBuilder tail=new StringBuilder();
        try(BufferedReader reader=new BufferedReader(new InputStreamReader(process.getInputStream(),StandardCharsets.UTF_8))) {
            String line;
            while((line=reader.readLine())!=null) {
                if(Thread.currentThread().isInterrupted()) throw new InterruptedException();
                tail.append(line).append('\n');if(tail.length()>6000) tail.delete(0,tail.length()-4000);
            }
            int exit=process.waitFor();
            if(exit!=0) throw new IOException("FFmpeg "+exit+": "+tail);
            return tail.toString();
        } finally { timer.shutdownNow();process.destroy();current=null; }
    }
    public void cancel() { Process p=current;if(p!=null) p.destroyForcibly(); }
}
