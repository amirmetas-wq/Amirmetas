package ir.amirsobhani.clipyar;

import android.content.Context;
import androidx.work.*;
import ir.amirsobhani.clipyar.core.DailyTime;
import java.time.*;
import java.util.concurrent.TimeUnit;

public final class Scheduler {
    public static final String PIPELINE="clipyar-processing", TRIGGER="clipyar-daily";
    private Scheduler() { }
    public static void schedule(Context c) {
        Config config=new Config(c);
        if(!config.enabled()) return;
        Instant now=Instant.now();
        ZonedDateTime next=DailyTime.next(now,config.hour(),config.minute());
        OneTimeWorkRequest request=new OneTimeWorkRequest.Builder(DailyTrigger.class)
            .setInitialDelay(Duration.between(now,next.toInstant()).toMillis(),TimeUnit.MILLISECONDS)
            .addTag(TRIGGER).build();
        WorkManager.getInstance(c).enqueueUniqueWork(TRIGGER+"-"+next.toLocalDate(),ExistingWorkPolicy.KEEP,request);
    }
    public static void reschedule(Context c) {
        WorkManager.getInstance(c).cancelAllWorkByTag(TRIGGER).getResult().addListener(()->schedule(c),Runnable::run);
    }
    public static void run(Context c,Data data) {
        Constraints.Builder constraints=new Constraints.Builder().setRequiresStorageNotLow(true);
        if(!data.hasKeyWithValueOfType("local",String.class))
            constraints.setRequiredNetworkType(new Config(c).wifiOnly()?NetworkType.UNMETERED:NetworkType.CONNECTED);
        OneTimeWorkRequest work=new OneTimeWorkRequest.Builder(ReelWorker.class).setConstraints(constraints.build())
            .setInputData(data).addTag(PIPELINE).build();
        WorkManager.getInstance(c).enqueueUniqueWork(PIPELINE,ExistingWorkPolicy.KEEP,work);
    }
    public static final class DailyTrigger extends Worker {
        public DailyTrigger(Context c,WorkerParameters p) { super(c,p); }
        @Override public Result doWork() {
            if(new Config(getApplicationContext()).enabled()) {
                schedule(getApplicationContext());
                run(getApplicationContext(),Data.EMPTY);
            }
            return Result.success();
        }
    }
}
