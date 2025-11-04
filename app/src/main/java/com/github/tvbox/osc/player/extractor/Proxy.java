package com.github.tvbox.osc.player.extractor;

import com.github.tvbox.osc.player.Source;
import com.github.tvbox.osc.server.Server;

public class Proxy implements Source.Extractor {

    @Override
    public boolean match(String scheme, String host) {
        return "proxy".equals(scheme);
    }

    @Override
    public String fetch(String url) throws Exception {
        return url.replace("proxy://", Server.get().getAddress("/proxy?"));
    }

    @Override
    public void stop() {
    }

    @Override
    public void exit() {
    }
}
