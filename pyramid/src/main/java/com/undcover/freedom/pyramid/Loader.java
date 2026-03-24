package com.undcover.freedom.pyramid;

import android.content.Context;

import androidx.annotation.Keep;

import com.chaquo.python.PyObject;
import com.chaquo.python.Python;
import com.chaquo.python.android.AndroidPlatform;
import com.github.catvod.utils.Path;

public class Loader {

    private PyObject app;
    private volatile boolean initialized = false;
    private final Object lock = new Object();

    @Keep
    private void init(Context context) {
        if (!initialized) {
            synchronized (lock) {
                if (!initialized) {
                    if (!Python.isStarted()) {
                        Python.start(new AndroidPlatform(context));
                    }
                    app = Python.getInstance().getModule("app");
                    initialized = true;
                }
            }
        }
    }

    @Keep
    public void warmup(Context context) {
        if (!initialized) init(context);
    }

    @Keep
    public Spider spider(Context context, String api) {
        if (!initialized) init(context);
        PyObject obj = app.callAttr("spider", Path.py().getAbsolutePath(), api);
        return new Spider(app, obj, api);
    }
}
