package com.github.tvbox.osc;

import android.app.Activity;

public class Updater {

    private static class Loader {
        static volatile Updater INSTANCE = new Updater();
    }

    public static Updater get() {
        return Loader.INSTANCE;
    }

    public Updater force() {
        return this;
    }

    public Updater release() {
        return this;
    }

    public Updater dev() {
        return this;
    }

    public void start(Activity activity) {
    }
}
