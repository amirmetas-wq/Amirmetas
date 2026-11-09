package ir.amirsobhani.clipyar;

import android.content.*;

public final class Config {
    private final SharedPreferences p;
    public Config(Context c) {
        p=c.getSharedPreferences("settings",Context.MODE_PRIVATE);
        if(p.getInt("chartslist_revision",0)<2)
            p.edit().putInt("hour",14).putInt("minute",0).putInt("chartslist_revision",2).putBoolean("schedule_changed",true).apply();
    }
    public boolean enabled() { return p.getBoolean("enabled",false); }
    public void enabled(boolean value) { p.edit().putBoolean("enabled",value).apply(); }
    public int count() { return p.getInt("count",5); }
    public int hour() { return p.getInt("hour",14); }
    public int minute() { return p.getInt("minute",0); }
    public int width() { return p.getInt("width",720); }
    public int maxMinutes() { return p.getInt("maxMinutes",30); }
    public boolean wifiOnly() { return p.getBoolean("wifi",false); }
    public boolean ccOnly() { return p.getBoolean("cc",false); }
    public boolean scheduleChanged() {return p.getBoolean("schedule_changed",false);}
    public void scheduled() {p.edit().putBoolean("schedule_changed",false).apply();}
    public void save(int count,int hour,int minute,int width,int maxMinutes,boolean wifi,boolean cc) {
        p.edit().putInt("count",count).putInt("hour",hour).putInt("minute",minute).putInt("width",width)
            .putInt("maxMinutes",maxMinutes).putBoolean("wifi",wifi).putBoolean("cc",cc).apply();
    }
}
