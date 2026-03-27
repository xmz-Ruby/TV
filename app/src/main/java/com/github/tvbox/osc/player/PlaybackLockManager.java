package com.github.tvbox.osc.player;

import android.content.Context;
import android.net.wifi.WifiManager;
import android.os.PowerManager;

import androidx.annotation.Nullable;

import com.orhanobut.logger.Logger;

/**
 * 屏幕常亮只能阻止显示休眠，不能保证 CPU / Wi-Fi 在长时间无交互时持续活跃。
 * 统一在播放器层持有 PARTIAL_WAKE_LOCK + WifiLock，避免播放一段时间后网络/解码被系统降速。
 */
public class PlaybackLockManager {

    private static final String TAG = "TV:PlaybackLock";

    @Nullable
    private final PowerManager.WakeLock wakeLock;
    @Nullable
    private final WifiManager.WifiLock wifiLock;

    public PlaybackLockManager(Context context) {
        Context appContext = context.getApplicationContext();
        PowerManager powerManager = (PowerManager) appContext.getSystemService(Context.POWER_SERVICE);
        WifiManager wifiManager = (WifiManager) appContext.getSystemService(Context.WIFI_SERVICE);
        wakeLock = powerManager == null ? null : powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, TAG);
        wifiLock = wifiManager == null ? null : wifiManager.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, TAG);
        if (wakeLock != null) wakeLock.setReferenceCounted(false);
        if (wifiLock != null) wifiLock.setReferenceCounted(false);
    }

    public void acquire() {
        try {
            boolean wakeChanged = false;
            boolean wifiChanged = false;
            if (wakeLock != null && !wakeLock.isHeld()) {
                wakeLock.acquire();
                wakeChanged = true;
            }
            if (wifiLock != null && !wifiLock.isHeld()) {
                wifiLock.acquire();
                wifiChanged = true;
            }
            if (wakeChanged || wifiChanged) Logger.t(TAG).i("acquired wake=%s wifi=%s", wakeChanged, wifiChanged);
        } catch (Throwable e) {
            Logger.w("Acquire playback locks failed: " + e.getMessage());
        }
    }

    public void release() {
        try {
            boolean wifiChanged = false;
            boolean wakeChanged = false;
            if (wifiLock != null && wifiLock.isHeld()) {
                wifiLock.release();
                wifiChanged = true;
            }
            if (wakeLock != null && wakeLock.isHeld()) {
                wakeLock.release();
                wakeChanged = true;
            }
            if (wakeChanged || wifiChanged) Logger.t(TAG).i("released wake=%s wifi=%s", wakeChanged, wifiChanged);
        } catch (Throwable e) {
            Logger.w("Release playback locks failed: " + e.getMessage());
        }
    }
}
