package ir.amirsobhani.clipyar;

import android.content.*;
import android.database.Cursor;
import android.database.sqlite.*;
import java.util.*;

public final class Store extends SQLiteOpenHelper {
    private static Store instance;
    public static synchronized Store get(Context c) {
        if(instance==null) instance=new Store(c.getApplicationContext());
        return instance;
    }
    private Store(Context c) { super(c,"clipyar.db",null,1); }
    @Override public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE clips(id INTEGER PRIMARY KEY,day TEXT NOT NULL,seq INTEGER NOT NULL,source TEXT NOT NULL,start INTEGER NOT NULL,duration INTEGER NOT NULL,title TEXT,url TEXT,channel TEXT,uri TEXT,state TEXT NOT NULL,UNIQUE(source,start),UNIQUE(day,seq))");
        db.execSQL("CREATE TABLE batches(day TEXT PRIMARY KEY,json TEXT NOT NULL)");
        db.execSQL("CREATE TABLE logs(id INTEGER PRIMARY KEY,time INTEGER,message TEXT)");
        db.execSQL("CREATE TABLE sources_done(source TEXT PRIMARY KEY)");
    }
    @Override public void onUpgrade(SQLiteDatabase db,int oldVersion,int newVersion) { }
    public synchronized Clip reserve(String day,String source,long start,long duration,String title,String url,String channel) {
        SQLiteDatabase db=getWritableDatabase(); db.beginTransaction();
        try {
            Clip result=find(db,source,start);
            if(result==null) {
                int seq=1;
                try(Cursor cursor=db.rawQuery("SELECT COALESCE(MAX(seq),0)+1 FROM clips WHERE day=?",new String[]{day})) {
                    if(cursor.moveToFirst()) seq=cursor.getInt(0);
                }
                ContentValues v=new ContentValues(); v.put("day",day);v.put("seq",seq);v.put("source",source);v.put("start",start);
                v.put("duration",duration);v.put("title",title);v.put("url",url);v.put("channel",channel);v.put("state","pending");
                db.insertOrThrow("clips",null,v); result=find(db,source,start);
            }
            db.setTransactionSuccessful(); return result;
        } finally { db.endTransaction(); }
    }
    private Clip find(SQLiteDatabase db,String source,long start) {
        try(Cursor c=db.query("clips",null,"source=? AND start=?",new String[]{source,Long.toString(start)},null,null,null)) {
            return c.moveToFirst()?new Clip(c):null;
        }
    }
    public synchronized void state(long id,String state,String uri) {
        ContentValues v=new ContentValues();v.put("state",state);v.put("uri",uri);
        getWritableDatabase().update("clips",v,"id=?",new String[]{Long.toString(id)});
    }
    public synchronized List<Clip> clips(int limit) {
        List<Clip> result=new ArrayList<>();
        try(Cursor c=getReadableDatabase().query("clips",null,"state='done'",null,null,null,"day DESC,seq DESC",Integer.toString(limit))) {
            while(c.moveToNext()) result.add(new Clip(c));
        }
        return result;
    }
    public synchronized String credits(String day) {
        StringBuilder text=new StringBuilder("ClipYar Rev01 · "+day+"\n");
        try(Cursor c=getReadableDatabase().query("clips",null,"day=? AND state='done'",new String[]{day},null,null,"seq ASC")) {
            while(c.moveToNext()) { Clip v=new Clip(c);text.append(v.name()).append(" | ").append(v.title).append("\n")
                .append(v.channel).append("\n").append(v.url).append("\n")
                .append(v.start/1000.0).append("–").append((v.start+v.duration)/1000.0).append(" seconds\n\n"); }
        }
        return text.toString();
    }
    public synchronized String batch(String day) {
        try(Cursor c=getReadableDatabase().rawQuery("SELECT json FROM batches WHERE day=?",new String[]{day})) {
            return c.moveToFirst()?c.getString(0):null;
        }
    }
    public synchronized boolean completed(String source) {
        try(Cursor c=getReadableDatabase().rawQuery("SELECT 1 FROM sources_done WHERE source=?",new String[]{source})) { return c.moveToFirst(); }
    }
    public synchronized void complete(String source) {
        ContentValues v=new ContentValues();v.put("source",source);
        getWritableDatabase().insertWithOnConflict("sources_done",null,v,SQLiteDatabase.CONFLICT_IGNORE);
    }
    public synchronized void batch(String day,String json) {
        ContentValues v=new ContentValues();v.put("day",day);v.put("json",json);
        getWritableDatabase().insertWithOnConflict("batches",null,v,SQLiteDatabase.CONFLICT_REPLACE);
    }
    public synchronized void log(String message) {
        ContentValues v=new ContentValues();v.put("time",System.currentTimeMillis());v.put("message",message);
        SQLiteDatabase db=getWritableDatabase();db.insert("logs",null,v);
        db.execSQL("DELETE FROM logs WHERE id NOT IN (SELECT id FROM logs ORDER BY id DESC LIMIT 200)");
    }
    public synchronized String logs() {
        StringBuilder text=new StringBuilder();
        java.time.format.DateTimeFormatter f=java.time.format.DateTimeFormatter.ofPattern("MM-dd HH:mm").withZone(ir.amirsobhani.clipyar.core.DailyTime.TEHRAN);
        try(Cursor c=getReadableDatabase().rawQuery("SELECT time,message FROM logs ORDER BY id DESC LIMIT 30",null)) {
            while(c.moveToNext()) text.append(f.format(java.time.Instant.ofEpochMilli(c.getLong(0)))).append("  ").append(c.getString(1)).append("\n\n");
        }
        return text.toString();
    }
    public static final class Clip {
        public final long id,start,duration;
        public final int seq;
        public final String day,source,title,url,channel,uri,state;
        Clip(Cursor c) {
            id=c.getLong(c.getColumnIndexOrThrow("id"));start=c.getLong(c.getColumnIndexOrThrow("start"));
            duration=c.getLong(c.getColumnIndexOrThrow("duration"));seq=c.getInt(c.getColumnIndexOrThrow("seq"));
            day=string(c,"day");source=string(c,"source");title=string(c,"title");url=string(c,"url");
            channel=string(c,"channel");uri=string(c,"uri");state=string(c,"state");
        }
        private static String string(Cursor c,String name) { return c.getString(c.getColumnIndexOrThrow(name)); }
        public String name() { return String.format(Locale.ROOT,"%04d.mp4",seq); }
    }
}
