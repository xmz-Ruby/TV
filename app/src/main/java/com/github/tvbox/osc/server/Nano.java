package com.github.tvbox.osc.server;

import com.github.tvbox.osc.api.config.LiveConfig;
import com.github.tvbox.osc.bean.Device;
import com.github.tvbox.osc.server.process.Action;
import com.github.tvbox.osc.server.process.Cache;
import com.github.tvbox.osc.server.process.DanmakuPage;
import com.github.tvbox.osc.server.process.Local;
import com.github.tvbox.osc.server.process.Media;
import com.github.tvbox.osc.server.process.Parse;
import com.github.tvbox.osc.server.process.Process;
import com.github.tvbox.osc.server.process.Proxy;
import com.github.catvod.utils.Asset;
import com.google.common.net.HttpHeaders;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import fi.iki.elonen.NanoHTTPD;

public class Nano extends NanoHTTPD {

    private List<Process> process;
    private com.github.tvbox.osc.server.process.CastProxy castProxy;

    public Nano(int port) {
        // 绑定到0.0.0.0以同时支持127.0.0.1和局域网IP访问
        // 通过IP白名单来限制只允许本机和局域网访问
        super("0.0.0.0", port);
        addProcess();
    }

    private void addProcess() {
        process = new ArrayList<>();
        process.add(new Action());
        process.add(new Cache());
        castProxy = new com.github.tvbox.osc.server.process.CastProxy();
        process.add(castProxy);
        process.add(new DanmakuPage());
        process.add(new Local());
        process.add(new Media());
        process.add(new Parse());
        process.add(new Proxy());
    }

    /**
     * 获取 CastProxy 实例
     */
    public com.github.tvbox.osc.server.process.CastProxy getCastProxy() {
        return castProxy;
    }

    public static Response success() {
        return success("OK");
    }

    public static Response success(String text) {
        return newFixedLengthResponse(Response.Status.OK, MIME_PLAINTEXT, text);
    }

    public static Response error(String text) {
        return error(Response.Status.INTERNAL_ERROR, text);
    }

    public static Response error(Response.IStatus status, String text) {
        return newFixedLengthResponse(status, MIME_PLAINTEXT, text);
    }

    public static Response redirect(String url, Map<String, String> headers) {
        Response response = newFixedLengthResponse(Response.Status.REDIRECT, MIME_HTML, "");
        for (Map.Entry<String, String> entry : headers.entrySet()) response.addHeader(entry.getKey(), entry.getValue());
        response.addHeader(HttpHeaders.LOCATION, url);
        return response;
    }

    @Override
    public Response serve(IHTTPSession session) {
        // IP访问控制检查
        String remoteIp = session.getRemoteIpAddress();
        if (!isAllowedIp(remoteIp)) {
            return error(Response.Status.FORBIDDEN, "Access denied from IP: " + remoteIp);
        }

        String url = session.getUri().trim();
        Map<String, String> files = new HashMap<>();
        if (session.getMethod() == Method.POST) parse(session, files);
        if (url.contains("?")) url = url.substring(0, url.indexOf('?'));
        if (url.startsWith("/tvbus")) return success(LiveConfig.getResp());
        if (url.startsWith("/device")) return success(Device.get().toString());
        for (Process process : process) if (process.isRequest(session, url)) return process.doResponse(session, url, files);
        return getAssets(url.substring(1));
    }

    /**
     * 检查IP是否允许访问
     * 只允许localhost和局域网IP访问
     */
    private boolean isAllowedIp(String remoteIp) {
        if (remoteIp == null || remoteIp.isEmpty()) {
            return false;
        }

        // 允许localhost访问
        if (remoteIp.equals("127.0.0.1") || remoteIp.equals("0:0:0:0:0:0:0:1") || remoteIp.equals("::1")) {
            return true;
        }

        // 允许局域网IP访问
        // 10.0.0.0/8
        if (remoteIp.startsWith("10.")) {
            return true;
        }
        // 172.16.0.0/12
        if (remoteIp.startsWith("172.")) {
            try {
                int second = Integer.parseInt(remoteIp.split("\\.")[1]);
                if (second >= 16 && second <= 31) {
                    return true;
                }
            } catch (Exception ignored) {
            }
        }
        // 192.168.0.0/16
        if (remoteIp.startsWith("192.168.")) {
            return true;
        }
        // 169.254.0.0/16 (link-local)
        if (remoteIp.startsWith("169.254.")) {
            return true;
        }

        return false;
    }

    private void parse(IHTTPSession session, Map<String, String> files) {
        String ct = session.getHeaders().get("content-type");
        if (ct != null && ct.toLowerCase().contains("multipart/form-data") && !ct.toLowerCase().contains("charset=")) {
            Matcher matcher = Pattern.compile("[ |\t]*(boundary[ |\t]*=[ |\t]*['|\"]?[^\"^'^;^,]*['|\"]?)", Pattern.CASE_INSENSITIVE).matcher(ct);
            String boundary = matcher.find() ? matcher.group(1) : null;
            if (boundary != null) session.getHeaders().put("content-type", "multipart/form-data; charset=utf-8; " + boundary);
        }
        try {
            session.parseBody(files);
        } catch (Exception ignored) {
        }
    }

    private Response getAssets(String path) {
        try {
            if (path.isEmpty()) path = "index.html";
            InputStream is = Asset.open(path);
            return newFixedLengthResponse(Response.Status.OK, getMimeTypeForFile(path), is, is.available());
        } catch (Exception e) {
            return newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_HTML, null);
        }
    }

    @Override
    public void start() throws IOException {
        super.start();
    }

    @Override
    public void stop() {
        super.stop();
    }
}
