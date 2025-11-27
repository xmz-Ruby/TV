package com.github.tvbox.osc.bean;

import android.net.Uri;

import com.github.tvbox.osc.server.Server;
import com.github.catvod.utils.Path;
import com.github.catvod.utils.Util;

public class CastVideo {

    private final long position;
    private final String name;
    private final String url;

    public static CastVideo get(String name, String url) {
        return new CastVideo(name, url, 0);
    }

    public static CastVideo get(String name, String url, long position) {
        return new CastVideo(name, url, position);
    }

    private CastVideo(String name, String url, long position) {
        if (url.startsWith("file")) url = Server.get().getAddress() + "/" + url.replace(Path.rootPath(), "").replace("://", "");
        if (url.startsWith("http://127.0.0.1:7777")) url = Uri.parse(url).getQueryParameter("url");
        if (url.contains("127.0.0.1")) url = url.replace("127.0.0.1", Util.getIp());
        this.position = position;
        this.name = name;
        this.url = url;
    }

    public String getName() {
        return name;
    }

    public String getUrl() {
        return url;
    }

    public long getPosition() {
        return position;
    }
}