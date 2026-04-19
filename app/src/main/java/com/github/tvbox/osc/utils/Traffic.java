package com.github.tvbox.osc.utils;

import android.net.TrafficStats;
import android.view.View;
import android.widget.TextView;

import com.github.tvbox.osc.App;

import java.text.DecimalFormat;

public class Traffic {

    private static final DecimalFormat format = new DecimalFormat("#.0");
    private static final String UNIT_KB = " KB/s";
    private static final String UNIT_MB = " MB/s";
    private static final String DEFAULT_SPEED = "0 KB/s";
    private static final long MIN_SAMPLE_INTERVAL_MS = 800L;
    private static long lastTotalRxBytes = -1L;
    private static long lastTimeStamp;
    private static long lastSpeedKiloBytesPerSecond;
    private static String lastSpeed = DEFAULT_SPEED;

    public static synchronized void setSpeed(TextView view) {
        if (unsupported()) return;
        view.setText(getSpeed());
        view.setVisibility(View.VISIBLE);
    }

    private static boolean unsupported() {
        return TrafficStats.getUidRxBytes(App.get().getApplicationInfo().uid) == TrafficStats.UNSUPPORTED;
    }

    private static long getUidRxBytes() {
        long uidBytes = TrafficStats.getUidRxBytes(App.get().getApplicationInfo().uid);
        return uidBytes == TrafficStats.UNSUPPORTED ? 0L : uidBytes / 1024;
    }

    private static synchronized String getSpeed() {
        long nowTimeStamp = System.currentTimeMillis();
        long nowTotalRxBytes = getUidRxBytes();
        if (lastTimeStamp <= 0 || lastTotalRxBytes < 0) {
            lastTimeStamp = nowTimeStamp;
            lastTotalRxBytes = nowTotalRxBytes;
            lastSpeed = DEFAULT_SPEED;
            return lastSpeed;
        }
        long elapsed = nowTimeStamp - lastTimeStamp;
        if (elapsed < MIN_SAMPLE_INTERVAL_MS) return lastSpeed;
        long speed = Math.max(0, nowTotalRxBytes - lastTotalRxBytes) * 1000 / Math.max(elapsed, 1);
        lastTimeStamp = nowTimeStamp;
        lastTotalRxBytes = nowTotalRxBytes;
        lastSpeedKiloBytesPerSecond = speed;
        lastSpeed = speed < 1000 ? speed + UNIT_KB : format.format(speed / 1024f) + UNIT_MB;
        return lastSpeed;
    }

    public static synchronized long getLastSpeedKiloBytesPerSecond() {
        return lastSpeedKiloBytesPerSecond;
    }

    public static synchronized void reset() {
        lastTotalRxBytes = getUidRxBytes();
        lastTimeStamp = System.currentTimeMillis();
        lastSpeedKiloBytesPerSecond = 0L;
        lastSpeed = DEFAULT_SPEED;
    }
}
