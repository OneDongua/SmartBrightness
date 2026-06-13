package com.onedongua.smartbrightness.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class ScreenOnReceiver extends BroadcastReceiver {
    private final Runnable onScreenOn;
    private final Runnable onScreenOff;

    public ScreenOnReceiver(Runnable onScreenOn, Runnable onScreenOff) {
        this.onScreenOn = onScreenOn;
        this.onScreenOff = onScreenOff;
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        if (Intent.ACTION_SCREEN_ON.equals(intent.getAction())) {
            onScreenOn.run();
        } else if (Intent.ACTION_SCREEN_OFF.equals(intent.getAction())) {
            onScreenOff.run();
        }
    }
}
