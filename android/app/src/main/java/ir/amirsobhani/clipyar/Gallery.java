package ir.amirsobhani.clipyar;

import android.content.*;
import android.net.Uri;
import android.os.Environment;
import android.provider.MediaStore;
import java.io.*;
import java.nio.charset.StandardCharsets;

public final class Gallery {
    private final Context context;
    private final Store store;
    public Gallery(Context context,Store store) { this.context=context;this.store=store; }
    public Uri save(Store.Clip clip,File file) throws IOException {
        ContentResolver resolver=context.getContentResolver();
        // A stopped transfer owns only this pending entry. Never delete completed clips.
        if(clip.uri!=null && !"done".equals(clip.state)) {
            try { resolver.delete(Uri.parse(clip.uri),null,null); } catch(Exception ignored) { }
        }
        ContentValues v=new ContentValues();v.put(MediaStore.Video.Media.DISPLAY_NAME,clip.name());
        v.put(MediaStore.Video.Media.MIME_TYPE,"video/mp4");
        v.put(MediaStore.Video.Media.RELATIVE_PATH,Environment.DIRECTORY_MOVIES+"/ClipYar/"+clip.day+"/");
        v.put(MediaStore.Video.Media.IS_PENDING,1);
        Uri uri=resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI,v);
        if(uri==null) throw new IOException("ساخت فایل در گالری ممکن نشد");
        store.state(clip.id,"writing",uri.toString());
        try {
            try(InputStream in=new FileInputStream(file);OutputStream out=resolver.openOutputStream(uri,"w")) {
                if(out==null) throw new IOException("فایل گالری قابل نوشتن نیست");
                byte[] buffer=new byte[1024*1024];int n;
                while((n=in.read(buffer))!=-1) {
                    if(Thread.currentThread().isInterrupted()) throw new java.io.InterruptedIOException();
                    out.write(buffer,0,n);
                }
            }
            ContentValues ready=new ContentValues();ready.put(MediaStore.Video.Media.IS_PENDING,0);
            if(resolver.update(uri,ready,null,null)!=1) throw new IOException("انتشار فایل در گالری کامل نشد");
            store.state(clip.id,"done",uri.toString());return uri;
        } catch(Exception e) {
            try { resolver.delete(uri,null,null); } catch(Exception ignored) { }
            store.state(clip.id,"pending",null);
            if(e instanceof IOException) throw (IOException)e;
            throw new IOException(e);
        }
    }
    public void credits(String day) throws IOException {
        ContentResolver resolver=context.getContentResolver();
        android.content.SharedPreferences p=context.getSharedPreferences("credits",Context.MODE_PRIVATE);
        String saved=p.getString(day,null);Uri uri=saved==null?null:Uri.parse(saved);
        if(uri!=null) {
            try { resolver.delete(uri,null,null); } catch(Exception ignored) { }
        }
        ContentValues v=new ContentValues();v.put(MediaStore.Downloads.DISPLAY_NAME,"sources-"+day+".txt");
        v.put(MediaStore.Downloads.MIME_TYPE,"text/plain");
        v.put(MediaStore.Downloads.RELATIVE_PATH,Environment.DIRECTORY_DOWNLOADS+"/ClipYar/");
        v.put(MediaStore.Downloads.IS_PENDING,1);
        uri=resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI,v);
        if(uri==null) throw new IOException("ذخیرهٔ فهرست منابع ممکن نشد");
        try(OutputStream out=resolver.openOutputStream(uri,"w")) {
            if(out==null) throw new IOException("فایل منابع قابل نوشتن نیست");
            out.write(store.credits(day).getBytes(StandardCharsets.UTF_8));
        }
        ContentValues ready=new ContentValues();ready.put(MediaStore.Downloads.IS_PENDING,0);resolver.update(uri,ready,null,null);
        p.edit().putString(day,uri.toString()).apply();
    }
}
