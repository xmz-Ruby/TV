package com.github.tvbox.osc.player.extractor;

import android.os.SystemClock;

import com.github.tvbox.osc.App;
import com.github.tvbox.osc.player.Source;
import com.github.tvbox.osc.ui.activity.VideoActivity;

public class Push implements Source.Extractor {

    @Override
    public boolean match(String scheme, String host) {
        return "push".equals(scheme);
    }

    @Override
    public String fetch(String url) throws Exception {
        if (App.activity() != null) VideoActivity.start(App.activity(), url.substring(7));
        SystemClock.sleep(500);
        return "";
    }

    @Override
    public void stop() {
    }

    @Override
    public void exit() {
    }
}
