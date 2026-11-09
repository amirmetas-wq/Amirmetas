package ir.amirsobhani.clipyar;

import android.content.*;

public final class Config {
    private final SharedPreferences p;
    public Config(Context c) { p=c.getSharedPreferences("settings",Context.MODE_PRIVATE); }
    public boolean enabled() { return p.getBoolean("enabled",false); }
    public void enabled(boolean value) { p.edit().putBoolean("enabled",value).apply(); }
    public int count() { return p.getInt("count",5); }
    public int hour() { return p.getInt("hour",10); }
    public int minute() { return p.getInt("minute",0); }
    public int width() { return p.getInt("width",720); }
    public int maxMinutes() { return p.getInt("maxMinutes",30); }
    public boolean wifiOnly() { return p.getBoolean("wifi",false); }
    public boolean ccOnly() { return p.getBoolean("cc",false); }
    public String queries() { return p.getString("queries","viral\nfunny\nnews\nmusic\nsports\ntrending\nviral video\nvídeos virales\n動画\n인기 영상"); }
    public void save(int count,int hour,int minute,int width,int maxMinutes,boolean wifi,boolean cc,String queries) {
        p.edit().putInt("count",count).putInt("hour",hour).putInt("minute",minute).putInt("width",width)
            .putInt("maxMinutes",maxMinutes).putBoolean("wifi",wifi).putBoolean("cc",cc).putString("queries",queries).apply();
    }
}
