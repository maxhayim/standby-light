package io.github.maxhayim.standbylight;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** After a boot or an update of this app, switch the light off again if the owner asked for that. */
public final class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context c, Intent i) {
        String a = i.getAction();
        if (!Intent.ACTION_BOOT_COMPLETED.equals(a) && !Intent.ACTION_MY_PACKAGE_REPLACED.equals(a)) return;
        if (Light.keepOff(c)) LightJob.schedule(c);
    }
}
