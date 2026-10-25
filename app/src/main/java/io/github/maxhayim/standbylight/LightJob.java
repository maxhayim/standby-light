package io.github.maxhayim.standbylight;

import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.app.job.JobService;
import android.content.ComponentName;
import android.content.Context;

/** Runs once after boot: a job may run for minutes, enough to wait for adbd to come up. */
public final class LightJob extends JobService {
    private static final int ID = 1;

    static void schedule(Context c) {
        JobInfo job = new JobInfo.Builder(ID, new ComponentName(c, LightJob.class))
                .setOverrideDeadline(0)
                .build();
        c.getSystemService(JobScheduler.class).schedule(job);
    }

    @Override
    public boolean onStartJob(JobParameters params) {
        if (!Light.keepOff(this)) return false;
        new Thread(() -> {
            // Up to about three minutes for adbd; no owner prompt here (the key was accepted in the app).
            Light.apply(this, true, 36, 10_000);
            jobFinished(params, false);
        }, "standby-light").start();
        return true;
    }

    @Override
    public boolean onStopJob(JobParameters params) {
        return false;
    }
}
