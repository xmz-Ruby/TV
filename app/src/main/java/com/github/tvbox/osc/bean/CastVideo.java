package com.github.tvbox.osc.bean;

import android.net.Uri;
import android.text.TextUtils;
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
    private final String format;
    private final Map<String, String> headers;

    public static CastVideo get(String name, String url) {
        return new CastVideo(name, url, 0, 0, null, null);
    }

    public static CastVideo get(String name, String url, long position) {
        return new CastVideo(name, url, position, 0, null, null);
    }

    public static CastVideo get(String name, String url, long position, long duration) {
        return new CastVideo(name, url, position, duration, null, null);
    }

    public static CastVideo get(String name, String url, long position, Map<String, String> headers) {
        return new CastVideo(name, url, position, 0, headers, null);
    }

    public static CastVideo get(String name, String url, long position, long duration, Map<String, String> headers) {
        return new CastVideo(name, url, position, duration, headers, null);
    }

    public static CastVideo get(String name, String url, long position, long duration, Map<String, String> headers, String format) {
        return new CastVideo(name, url, position, duration, headers, format);
    }

    private CastVideo(String name, String url, long position, long duration, Map<String, String> headers, String format) {
        this.originalUrl = url;
        this.format = inferFormat(format, url);
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

        // 有些本地代理 URL 会把请求头塞进 query，例如 :1314?url=...&header=...
        // DLNA 设备直接消费这种 URL 时元数据和回传都不稳定，统一再套一层 cast_proxy。
        if (shouldUseCastProxy(url, headers)) {
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
            if (headers != null) {
                for (Map.Entry<String, String> entry : headers.entrySet()) {
                    if (headerStr.length() > 0) headerStr.append("&");
                    headerStr.append(entry.getKey()).append("=").append(entry.getValue());
                }
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

    private boolean shouldUseCastProxy(String url, Map<String, String> headers) {
        if (!isNetworkUrl(url) || isCastProxyUrl(url)) return false;
        if (headers != null && !headers.isEmpty()) return true;
        if (hasEmbeddedHeaders(url)) return true;
        return isLocalHelperProxy(url);
    }

    private boolean isNetworkUrl(String url) {
        return url != null && (url.startsWith("http://") || url.startsWith("https://"));
    }

    private boolean isCastProxyUrl(String url) {
        try {
            Uri uri = Uri.parse(url);
            return "/cast_proxy".equals(uri.getPath());
        } catch (Exception e) {
            return false;
        }
    }

    private boolean hasEmbeddedHeaders(String url) {
        try {
            Uri uri = Uri.parse(url);
            return !TextUtils.isEmpty(uri.getQueryParameter("header")) || !TextUtils.isEmpty(uri.getQueryParameter("headers"));
        } catch (Exception e) {
            return url != null && (url.contains("&header=") || url.contains("?header=") || url.contains("&headers=") || url.contains("?headers="));
        }
    }

    private boolean isLocalHelperProxy(String url) {
        try {
            Uri uri = Uri.parse(url);
            String host = uri.getHost();
            int port = uri.getPort();
            if (TextUtils.isEmpty(host) || port <= 0) return false;
            boolean isLocalHost = "127.0.0.1".equals(host) || "localhost".equalsIgnoreCase(host) || Util.getIp().equals(host);
            return isLocalHost && port != Server.get().getPort();
        } catch (Exception e) {
            return false;
        }
    }

    private String inferFormat(String format, String url) {
        if (format != null && !format.isEmpty()) return format;
        if (url == null) return "";
        String lower = url.toLowerCase();
        if (lower.contains(".mpd") || lower.contains("type=dash")) return "application/dash+xml";
        if (lower.contains(".m3u8") || lower.contains("type=hls")) return "application/x-mpegURL";
        if (lower.contains(".ts") || lower.contains("type=ts")) return "video/mp2t";
        if (lower.contains(".mp4") || lower.contains("type=mp4")) return "video/mp4";
        return "";
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

    public String getFormat() {
        return format;
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
