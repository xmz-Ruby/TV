package com.github.tvbox.osc.bean;

import android.net.Uri;
import android.util.Base64;

import com.github.tvbox.osc.server.Server;
import com.github.catvod.utils.Path;
import com.github.catvod.utils.Util;

import java.util.HashMap;
import java.util.Map;

public class CastVideo {

    private final long position;
    private final long duration;
    private final String name;
    private final String url;
    private final String originalUrl;
    private final Map<String, String> headers;

    public static CastVideo get(String name, String url) {
        return new CastVideo(name, url, 0, 0, null);
    }

    public static CastVideo get(String name, String url, long position) {
        return new CastVideo(name, url, position, 0, null);
    }

    public static CastVideo get(String name, String url, long position, long duration) {
        return new CastVideo(name, url, position, duration, null);
    }

    public static CastVideo get(String name, String url, long position, Map<String, String> headers) {
        return new CastVideo(name, url, position, 0, headers);
    }

    public static CastVideo get(String name, String url, long position, long duration, Map<String, String> headers) {
        return new CastVideo(name, url, position, duration, headers);
    }

    private CastVideo(String name, String url, long position, long duration, Map<String, String> headers) {
        this.originalUrl = url;
        this.headers = headers;
        this.position = position;
        this.duration = duration;
        this.name = name;

        // 处理本地文件
        if (url.startsWith("file")) {
            url = Server.get().getAddress() + "/" + url.replace(Path.rootPath(), "").replace("://", "");
        }
        // 处理特殊代理 URL
        else if (url.startsWith("http://127.0.0.1:7777")) {
            url = Uri.parse(url).getQueryParameter("url");
        }

        // 检查是否已经是本地代理 URL
        boolean isLocalProxy = url.contains("127.0.0.1:" + Server.get().getPort()) ||
                              url.contains(Util.getIp() + ":" + Server.get().getPort());

        // 如果有 headers 且是网络 URL，且不是本地代理，通过 cast_proxy
        if (headers != null && !headers.isEmpty() &&
            (url.startsWith("http://") || url.startsWith("https://"))) {
            url = buildProxyUrl(url, headers);
        }

        // 替换 127.0.0.1 为实际 IP（确保 DLNA 设备可以访问）
        if (url.contains("127.0.0.1")) {
            url = url.replace("127.0.0.1", Util.getIp());
        }

        this.url = url;
    }

    private String buildProxyUrl(String originalUrl, Map<String, String> headers) {
        try {
            // 将 headers 编码为 Base64
            StringBuilder headerStr = new StringBuilder();
            for (Map.Entry<String, String> entry : headers.entrySet()) {
                if (headerStr.length() > 0) headerStr.append("&");
                headerStr.append(entry.getKey()).append("=").append(entry.getValue());
            }
            String headersBase64 = Base64.encodeToString(headerStr.toString().getBytes(), Base64.URL_SAFE | Base64.NO_WRAP);

            // 构造代理 URL: http://本机IP:9978/cast_proxy?url=xxx&token=xxx&headers=xxx
            StringBuilder proxyUrl = new StringBuilder();
            proxyUrl.append(Server.get().getAddress());
            proxyUrl.append("/cast_proxy?url=");
            proxyUrl.append(Uri.encode(originalUrl));

            // 添加 token 参数
            String token = Server.get().getCastProxyToken();
            if (token != null && !token.isEmpty()) {
                proxyUrl.append("&token=");
                proxyUrl.append(Uri.encode(token));
            }

            // 添加 headers 参数
            proxyUrl.append("&headers=");
            proxyUrl.append(headersBase64);

            return proxyUrl.toString();
        } catch (Exception e) {
            android.util.Log.e("CastVideo", "Failed to build proxy URL", e);
            return originalUrl;
        }
    }

    public String getName() {
        return name;
    }

    public String getUrl() {
        return url;
    }

    public String getOriginalUrl() {
        return originalUrl;
    }

    public Map<String, String> getHeaders() {
        return headers;
    }

    public long getPosition() {
        return position;
    }

    public long getDuration() {
        return duration;
    }
}