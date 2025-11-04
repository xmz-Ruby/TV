package com.github.catvod.utils;

import android.util.Log;

public class Logger {
    private static final String TAG = "让我看看";
    private static Object logMonitor;

    static {
        try {
            Class<?> clazz = Class.forName("com.fongmi.android.tv.utils.LogMonitor");
            logMonitor = clazz.getMethod("get").invoke(null);
        } catch (Exception e) {
            logMonitor = null;
        }
    }

    private static void addToMonitor(String level, String msg) {
        if (logMonitor != null) {
            try {
                logMonitor.getClass().getMethod("addLog", String.class, String.class, String.class)
                    .invoke(logMonitor, level, TAG, msg);
            } catch (Exception ignored) {
            }
        }
    }

    private static void addToMonitor(String level, String msg, Throwable tr) {
        if (logMonitor != null) {
            try {
                String fullMsg = msg + "\n" + Log.getStackTraceString(tr);
                logMonitor.getClass().getMethod("addLog", String.class, String.class, String.class)
                    .invoke(logMonitor, level, TAG, fullMsg);
            } catch (Exception ignored) {
            }
        }
    }

    public static void d(String msg) {
        Log.d(TAG, msg);
        addToMonitor("DEBUG", msg);
    }

    public static void e(String msg) {
        Log.e(TAG, msg);
        addToMonitor("ERROR", msg);
    }

    public static void e(String msg, Throwable tr) {
        Log.e(TAG, msg, tr);
        addToMonitor("ERROR", msg, tr);
    }

    public static void i(String msg) {
        Log.i(TAG, msg);
        addToMonitor("INFO", msg);
    }

    public static void v(String msg) {
        Log.v(TAG, msg);
        addToMonitor("VERBOSE", msg);
    }

    public static void w(String msg) {
        Log.w(TAG, msg);
        addToMonitor("WARN", msg);
    }

    public static void w(String msg, Throwable tr) {
        Log.w(TAG, msg, tr);
        addToMonitor("WARN", msg, tr);
    }
}
