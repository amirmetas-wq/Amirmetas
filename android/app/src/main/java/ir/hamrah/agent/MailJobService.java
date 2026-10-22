package ir.hamrah.agent;

import android.app.job.JobParameters;
import android.app.job.JobService;
import android.content.Context;
import android.content.SharedPreferences;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;

/** Gmail requests run off the main thread and stop applying results after cancellation. */
public final class MailJobService extends JobService {
    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor();
    private final Map<Integer, Running> running = new ConcurrentHashMap<>();

    private static final class Running {
        final AtomicBoolean cancelled = new AtomicBoolean(false);
        Future<?> future;
    }

    @Override public boolean onStartJob(JobParameters params) {
        if (!AgentScheduler.hasMailConnection(this)) return false;
        if (params.getJobId() == AgentScheduler.PERIODIC_MAIL_JOB && !new AgentStore(this).mailEnabled()) return false;
        Running run = new Running();
        running.put(params.getJobId(), run);
        run.future = EXECUTOR.submit(() -> execute(params, run));
        return true;
    }

    private void execute(JobParameters params, Running run) {
        try {
            if (run.cancelled.get()) return;
            AgentStore store = new AgentStore(this);
            SharedPreferences state = getSharedPreferences(AgentScheduler.STATE, Context.MODE_PRIVATE);
            long generation = state.getLong("mail_generation", 0);
            long now = System.currentTimeMillis();
            long lastSuccess = state.getLong("last_mail_fetch_at", 0);
            // A periodic check and the daily report may be queued together.
            if (now - lastSuccess > 60_000 || now < lastSuccess) {
                String summary = summarizeConnectedAccounts(store);
                synchronized (AgentScheduler.class) {
                    if (run.cancelled.get() || Thread.currentThread().isInterrupted() ||
                        generation != state.getLong("mail_generation", 0)) return;
                    store.putLastMail(summary);
                    state.edit().putLong("last_mail_fetch_at", now).apply();
                    store.recordLog("گزارش ایمیل‌ها به‌روز شد.");
                }
            }
            if (!run.cancelled.get() && generation == state.getLong("mail_generation", 0) &&
                params.getExtras().getBoolean("notify", false)) {
                boolean daily = params.getExtras().getBoolean("daily", false);
                String today = LocalDate.now(ZoneId.systemDefault()).toString();
                if (!daily || !today.equals(state.getString("last_mail_notification_date", ""))) {
                    if (Notifications.mail(this) && daily) {
                        state.edit().putString("last_mail_notification_date", today).apply();
                    }
                }
            }
        } catch (Exception ignored) {
            if (!run.cancelled.get()) {
                SharedPreferences state = getSharedPreferences(AgentScheduler.STATE, Context.MODE_PRIVATE);
                long lastError = state.getLong("last_mail_error_at", 0);
                if (System.currentTimeMillis() - lastError > 3_600_000) {
                    new AgentStore(this).recordLog("گزارش ایمیل آماده نشد؛ اینترنت و اتصال حساب ایمیل را بررسی کنید.");
                    state.edit().putLong("last_mail_error_at", System.currentTimeMillis()).apply();
                }
            }
        } finally {
            running.remove(params.getJobId(), run);
            if (!run.cancelled.get()) jobFinished(params, false);
        }
    }

    private String summarizeConnectedAccounts(AgentStore store) throws Exception {
        StringBuilder digest = new StringBuilder();
        boolean succeeded = false;
        if (MailClient.isConnected(this)) {
            try {
                digest.append("جیمیل\n").append(MailClient.summarize(getApplicationContext())).append("\n\n");
                succeeded = true;
            } catch (Exception ignored) {
                digest.append("جیمیل: دریافت گزارش کامل نشد؛ اتصال این حساب را بررسی کنید.\n\n");
            }
        }
        if (YahooClient.isConnected(this)) {
            try {
                digest.append("یاهو\n").append(YahooClient.summarize(getApplicationContext())).append("\n\n");
                succeeded = true;
            } catch (Exception ignored) {
                digest.append("یاهو: دریافت گزارش کامل نشد؛ اتصال این حساب را بررسی کنید.\n\n");
            }
        }
        if (!succeeded) throw new java.io.IOException("No connected mailbox could be read");
        String local = digest.toString().trim();
        if (!store.aiMailEnabled()) return local;
        try {
            String ai = AiClient.summarizeMail(getApplicationContext(), local);
            if (ai != null && !ai.trim().isEmpty()) return ai + "\n\n—— گزارش اولیه ——\n" + local;
        } catch (Exception ignored) {
            // Keep the useful local digest when the optional AI service is unavailable.
        }
        return "خلاصهٔ هوشمند آماده نشد؛ گزارش اولیهٔ ایمیل‌ها:\n\n" + local;
    }

    @Override public boolean onStopJob(JobParameters params) {
        Running run = running.remove(params.getJobId());
        if (run != null) {
            run.cancelled.set(true);
            if (run.future != null) run.future.cancel(true);
        }
        return params.getJobId() != AgentScheduler.PERIODIC_MAIL_JOB;
    }
}
