package ir.amirsobhani.clipyar;

import android.app.Application;

public final class ClipYarApp extends Application {
    @Override public void onCreate() {
        super.onCreate();
        Notices.channels(this);
        if(new Config(this).enabled()) Scheduler.schedule(this);
    }
}
