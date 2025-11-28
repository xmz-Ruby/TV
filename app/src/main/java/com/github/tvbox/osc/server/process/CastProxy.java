package com.github.tvbox.osc.server.process;

import android.util.Base64;

import com.github.tvbox.osc.server.Nano;
import com.github.catvod.net.OkHttp;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.SocketException;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

import fi.iki.elonen.NanoHTTPD;
import okhttp3.Headers;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class CastProxy implements Process {

    private final OkHttpClient client;
    private volatile long lastRequestTime = 0;
    private volatile int activeConnections = 0;

    public CastProxy() {
        this.client = OkHttp.client();
    }

    /**
     * 获取最后一次请求时间
     */
    public long getLastRequestTime() {
        return lastRequestTime;
    }

    /**
     * 获取当前活跃连接数
     */
    public int getActiveConnections() {
        return activeConnections;
    }

    /**
     * 检查代理是否活跃（最近5秒内有请求或有活跃连接）
     */
    public boolean isActive() {
        long timeSinceLastRequest = System.currentTimeMillis() - lastRequestTime;
        return activeConnections > 0 || timeSinceLastRequest < 5000;
    }

    @Override
    public boolean isRequest(NanoHTTPD.IHTTPSession session, String path) {
        return "/cast_proxy".equals(path);
    }

    @Override
    public NanoHTTPD.Response doResponse(NanoHTTPD.IHTTPSession session, String path, Map<String, String> files) {
        // 记录请求时间和增加活跃连接数
        lastRequestTime = System.currentTimeMillis();
        activeConnections++;
        android.util.Log.d("CastProxy", "Request started, active connections: " + activeConnections);

        Response response = null;
        boolean shouldCloseResponse = false;
        try {
            Map<String, String> params = session.getParms();

            // 验证 token
            String token = params.get("token");
            if (!com.github.tvbox.osc.server.Server.get().validateCastProxyToken(token)) {
                android.util.Log.w("CastProxy", "Invalid or missing token: " + token);
                activeConnections--; // 验证失败，减少连接计数
                return Nano.error(NanoHTTPD.Response.Status.FORBIDDEN, "Invalid or missing cast proxy token");
            }

            // 获取原始 URL
            String url = params.get("url");
            if (url == null || url.isEmpty()) {
                android.util.Log.w("CastProxy", "Missing url parameter");
                return Nano.error("Missing url parameter");
            }

            // 解析 headers
            Map<String, String> headers = new HashMap<>();
            String headersParam = params.get("headers");
            if (headersParam != null && !headersParam.isEmpty()) {
                try {
                    String decoded = new String(Base64.decode(headersParam, Base64.URL_SAFE));
                    String[] pairs = decoded.split("&");
                    for (String pair : pairs) {
                        int idx = pair.indexOf('=');
                        if (idx > 0) {
                            String key = pair.substring(0, idx);
                            String value = pair.substring(idx + 1);
                            headers.put(key, value);
                        }
                    }
                    android.util.Log.d("CastProxy", "Decoded " + headers.size() + " headers");
                } catch (Exception e) {
                    android.util.Log.e("CastProxy", "Failed to decode headers", e);
                }
            }

            // 获取客户端的 Range 请求
            String rangeHeader = session.getHeaders().get("range");
            android.util.Log.d("CastProxy", "Proxying: " + url);
            android.util.Log.d("CastProxy", "Client Range header: " + rangeHeader);
            android.util.Log.d("CastProxy", "Custom headers count: " + headers.size());

            // 构建请求
            Request.Builder builder = new Request.Builder().url(url);

            // 添加自定义 headers
            for (Map.Entry<String, String> entry : headers.entrySet()) {
                builder.addHeader(entry.getKey(), entry.getValue());
            }

            // 转发客户端的 Range 请求
            if (rangeHeader != null && !rangeHeader.isEmpty()) {
                builder.addHeader("Range", rangeHeader);
                android.util.Log.d("CastProxy", "Forwarding Range request: " + rangeHeader);
            }

            // 执行请求
            android.util.Log.d("CastProxy", "Sending request...");
            response = client.newCall(builder.build()).execute();

            int responseCode = response.code();
            android.util.Log.d("CastProxy", "Response code: " + responseCode);
            android.util.Log.d("CastProxy", "Response message: " + response.message());

            // 处理非成功响应（但 206 Partial Content 是成功的）
            if (!response.isSuccessful() && responseCode != 206) {
                android.util.Log.w("CastProxy", "Request failed with code: " + responseCode);
                String errorBody = "";
                try {
                    errorBody = response.body().string();
                    android.util.Log.w("CastProxy", "Error body: " + errorBody.substring(0, Math.min(200, errorBody.length())));
                } catch (Exception e) {
                    android.util.Log.w("CastProxy", "Could not read error body", e);
                }
                return Nano.error(NanoHTTPD.Response.Status.lookup(responseCode),
                                 "Failed to fetch: " + responseCode + " - " + errorBody);
            }

            // 获取响应流和元数据
            InputStream inputStream = response.body().byteStream();
            String contentType = response.header("Content-Type", "application/octet-stream");
            long contentLength = response.body().contentLength();
            String contentRange = response.header("Content-Range");

            android.util.Log.d("CastProxy", "Streaming content: " + contentType +
                              (contentLength > 0 ? " (" + contentLength + " bytes)" : ""));
            if (contentRange != null) {
                android.util.Log.d("CastProxy", "Content-Range: " + contentRange);
            }

            // 如果是 m3u8 播放列表，需要重写其中的 URL
            if (isM3u8Content(contentType, url)) {
                android.util.Log.d("CastProxy", "Detected m3u8 playlist, rewriting URLs");
                String rewrittenContent = rewriteM3u8Playlist(inputStream, url, headersParam);
                byte[] contentBytes = rewrittenContent.getBytes("UTF-8");
                inputStream = new ByteArrayInputStream(contentBytes);
                contentLength = contentBytes.length;
                android.util.Log.d("CastProxy", "Rewritten m3u8 size: " + contentLength + " bytes");
                // m3u8 内容已经完全读取，可以关闭原始 response
                shouldCloseResponse = true;
            }

            // 创建响应 - 根据是否是 Range 请求选择状态码
            NanoHTTPD.Response.Status status = (responseCode == 206) ?
                NanoHTTPD.Response.Status.PARTIAL_CONTENT :
                NanoHTTPD.Response.Status.OK;

            NanoHTTPD.Response nanoResponse;
            if (contentLength > 0) {
                // 如果知道内容长度，使用固定长度响应
                nanoResponse = NanoHTTPD.newFixedLengthResponse(
                    status,
                    contentType,
                    inputStream,
                    contentLength
                );
            } else {
                // 否则使用分块传输
                nanoResponse = NanoHTTPD.newChunkedResponse(
                    status,
                    contentType,
                    inputStream
                );
            }

            // 复制重要的响应头
            Headers responseHeaders = response.headers();
            for (String name : responseHeaders.names()) {
                String lowerName = name.toLowerCase();
                // 跳过已经处理的头
                if (lowerName.equals("content-type") ||
                    lowerName.equals("content-length") ||
                    lowerName.equals("transfer-encoding")) {
                    continue;
                }
                // 转发 Content-Range 等重要头
                nanoResponse.addHeader(name, responseHeaders.get(name));
            }

            // 添加 CORS 头
            nanoResponse.addHeader("Access-Control-Allow-Origin", "*");
            nanoResponse.addHeader("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
            nanoResponse.addHeader("Access-Control-Allow-Headers", "*");

            // 添加 Accept-Ranges 支持断点续传
            nanoResponse.addHeader("Accept-Ranges", "bytes");

            android.util.Log.d("CastProxy", "Response prepared successfully");
            return nanoResponse;

        } catch (SocketException e) {
            // Broken pipe 是正常的，客户端主动断开连接（如 seek 操作）
            android.util.Log.d("CastProxy", "Client disconnected: " + e.getMessage());
            // 客户端断开时需要关闭 response
            if (response != null) {
                try {
                    response.close();
                } catch (Exception ex) {
                    // 忽略关闭异常
                }
            }
            return null; // 返回 null 表示连接已关闭，不需要发送响应
        } catch (Exception e) {
            android.util.Log.e("CastProxy", "Proxy error: " + e.getMessage(), e);
            // 发生错误时需要关闭 response
            if (response != null) {
                try {
                    response.close();
                } catch (Exception ex) {
                    // 忽略关闭异常
                }
            }
            return Nano.error("Proxy error: " + e.getMessage());
        } finally {
            // 减少活跃连接数
            activeConnections--;
            android.util.Log.d("CastProxy", "Request finished, active connections: " + activeConnections);

            // 如果已经读取完内容（如 m3u8 重写），关闭 response
            // 否则不要关闭，因为 inputStream 还在使用中，NanoHTTPD 会在发送完响应后自动关闭流
            if (shouldCloseResponse && response != null) {
                try {
                    response.close();
                    android.util.Log.d("CastProxy", "Response closed after content read");
                } catch (Exception e) {
                    android.util.Log.w("CastProxy", "Error closing response", e);
                }
            }
        }
    }

    /**
     * 检查是否是 m3u8 内容
     */
    private boolean isM3u8Content(String contentType, String url) {
        if (contentType != null) {
            String lowerType = contentType.toLowerCase();
            if (lowerType.contains("application/vnd.apple.mpegurl") ||
                lowerType.contains("application/x-mpegurl") ||
                lowerType.contains("audio/mpegurl")) {
                return true;
            }
        }
        // 也检查 URL 扩展名
        return url.toLowerCase().contains(".m3u8");
    }

    /**
     * 重写 m3u8 播放列表中的 URL
     */
    private String rewriteM3u8Playlist(InputStream inputStream, String baseUrl, String headersParam) throws Exception {
        BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, "UTF-8"));
        StringBuilder result = new StringBuilder();
        String line;

        // 获取基础 URL（用于解析相对路径）
        URL base = new URL(baseUrl);
        String baseUrlStr = base.getProtocol() + "://" + base.getHost() +
                           (base.getPort() != -1 ? ":" + base.getPort() : "") +
                           base.getPath();
        // 移除文件名，只保留目录路径
        int lastSlash = baseUrlStr.lastIndexOf('/');
        if (lastSlash > 0) {
            baseUrlStr = baseUrlStr.substring(0, lastSlash + 1);
        }

        android.util.Log.d("CastProxy", "Base URL for m3u8: " + baseUrlStr);

        while ((line = reader.readLine()) != null) {
            // 跳过注释行和空行
            if (line.trim().isEmpty() || line.trim().startsWith("#")) {
                result.append(line).append("\n");
                continue;
            }

            // 这是一个 URL 行，需要重写
            String segmentUrl = line.trim();

            // 如果是相对 URL，转换为绝对 URL
            if (!segmentUrl.startsWith("http://") && !segmentUrl.startsWith("https://")) {
                if (segmentUrl.startsWith("/")) {
                    // 绝对路径
                    segmentUrl = base.getProtocol() + "://" + base.getHost() +
                               (base.getPort() != -1 ? ":" + base.getPort() : "") + segmentUrl;
                } else {
                    // 相对路径
                    segmentUrl = baseUrlStr + segmentUrl;
                }
                android.util.Log.d("CastProxy", "Converted relative URL to: " + segmentUrl);
            }

            // 如果是 .ts 或其他媒体片段，或者是嵌套的 m3u8，都需要通过代理
            if (needsProxy(segmentUrl)) {
                String proxyUrl = buildProxyUrl(segmentUrl, headersParam);
                android.util.Log.d("CastProxy", "Rewriting segment: " + segmentUrl + " -> " + proxyUrl);
                result.append(proxyUrl).append("\n");
            } else {
                result.append(segmentUrl).append("\n");
            }
        }

        reader.close();
        return result.toString();
    }

    /**
     * 判断 URL 是否需要通过代理
     */
    private boolean needsProxy(String url) {
        String lower = url.toLowerCase();
        // .ts 片段、.m3u8 子播放列表、或其他媒体格式都需要代理
        return lower.contains(".ts") || lower.contains(".m3u8") ||
               lower.contains(".mp4") || lower.contains(".m4s");
    }

    /**
     * 构建代理 URL
     */
    private String buildProxyUrl(String originalUrl, String headersParam) {
        try {
            StringBuilder proxyUrl = new StringBuilder();
            proxyUrl.append(com.github.tvbox.osc.server.Server.get().getAddress());
            proxyUrl.append("/cast_proxy?url=");
            proxyUrl.append(android.net.Uri.encode(originalUrl));

            // 添加 token 参数
            String token = com.github.tvbox.osc.server.Server.get().getCastProxyToken();
            if (token != null && !token.isEmpty()) {
                proxyUrl.append("&token=");
                proxyUrl.append(android.net.Uri.encode(token));
            }

            // 如果有 headers 参数，也添加上
            if (headersParam != null && !headersParam.isEmpty()) {
                proxyUrl.append("&headers=");
                proxyUrl.append(headersParam);
            }

            return proxyUrl.toString();
        } catch (Exception e) {
            android.util.Log.e("CastProxy", "Failed to build proxy URL", e);
            return originalUrl;
        }
    }
}
