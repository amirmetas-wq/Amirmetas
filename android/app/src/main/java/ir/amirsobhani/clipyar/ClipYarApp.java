package ir.amirsobhani.clipyar;

import android.app.Application;

public final class ClipYarApp extends Application {
    @Override public void onCreate() {
        super.onCreate();
        Notices.channels(this);
        Config config=new Config(this);
        if(config.enabled()) {if(config.scheduleChanged()) Scheduler.reschedule(this);else Scheduler.schedule(this);}
    }
}
