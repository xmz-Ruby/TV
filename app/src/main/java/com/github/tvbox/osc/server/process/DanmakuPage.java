package com.github.tvbox.osc.server.process;

import com.github.tvbox.osc.App;
import com.github.tvbox.osc.api.DanmakuApi;
import com.github.tvbox.osc.bean.DanmakuAnime;
import com.github.tvbox.osc.bean.DanmakuEpisode;
import com.github.tvbox.osc.event.RefreshEvent;
import com.github.tvbox.osc.server.Nano;
import com.google.gson.Gson;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import fi.iki.elonen.NanoHTTPD;

/**
 * 弹幕投送页面处理器
 * 提供手机端弹幕搜索和投送功能
 */
public class DanmakuPage implements Process {

    private static final Gson gson = new Gson();

    @Override
    public boolean isRequest(NanoHTTPD.IHTTPSession session, String url) {
        return url.startsWith("/danmaku");
    }

    @Override
    public NanoHTTPD.Response doResponse(NanoHTTPD.IHTTPSession session, String url, Map<String, String> files) {
        // 处理 API 请求
        if (url.startsWith("/danmaku/api/")) {
            return handleApiRequest(session, url);
        }

        // 返回弹幕投送页面
        return getDanmakuPage(session);
    }

    /**
     * 处理 API 请求
     */
    private NanoHTTPD.Response handleApiRequest(NanoHTTPD.IHTTPSession session, String url) {
        Map<String, String> params = session.getParms();

        // 搜索番剧
        if (url.startsWith("/danmaku/api/search")) {
            return handleSearch(params);
        }

        // 获取剧集列表
        if (url.startsWith("/danmaku/api/episodes")) {
            return handleGetEpisodes(params);
        }

        // 投送弹幕
        if (url.startsWith("/danmaku/api/cast")) {
            return handleCast(params);
        }

        // 获取/设置弹幕配置
        if (url.startsWith("/danmaku/api/settings")) {
            return handleSettings(params);
        }

        return Nano.error("Unknown API endpoint");
    }

    /**
     * 处理搜索请求
     */
    private NanoHTTPD.Response handleSearch(Map<String, String> params) {
        String keyword = params.get("keyword");
        if (keyword == null || keyword.trim().isEmpty()) {
            return jsonResponse(createErrorResponse("关键词不能为空"));
        }

        try {
            keyword = URLDecoder.decode(keyword, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            // 忽略解码错误
        }

        // 使用 CountDownLatch 等待异步结果
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<List<DanmakuAnime>> resultRef = new AtomicReference<>();
        AtomicReference<String> errorRef = new AtomicReference<>();

        DanmakuApi.searchAnime(keyword, new DanmakuApi.DanmakuCallback<List<DanmakuAnime>>() {
            @Override
            public void onSuccess(List<DanmakuAnime> data) {
                resultRef.set(data);
                latch.countDown();
            }

            @Override
            public void onError(String message) {
                errorRef.set(message);
                latch.countDown();
            }
        });

        try {
            // 等待最多10秒
            if (!latch.await(10, TimeUnit.SECONDS)) {
                return jsonResponse(createErrorResponse("搜索超时"));
            }

            if (errorRef.get() != null) {
                return jsonResponse(createErrorResponse(errorRef.get()));
            }

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("data", resultRef.get());
            return jsonResponse(response);

        } catch (InterruptedException e) {
            return jsonResponse(createErrorResponse("搜索被中断"));
        }
    }

    /**
     * 处理获取剧集列表请求
     */
    private NanoHTTPD.Response handleGetEpisodes(Map<String, String> params) {
        String animeIdStr = params.get("animeId");
        if (animeIdStr == null || animeIdStr.trim().isEmpty()) {
            return jsonResponse(createErrorResponse("番剧ID不能为空"));
        }

        int animeId;
        try {
            animeId = Integer.parseInt(animeIdStr);
        } catch (NumberFormatException e) {
            return jsonResponse(createErrorResponse("番剧ID格式错误"));
        }

        // 使用 CountDownLatch 等待异步结果
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<List<DanmakuEpisode>> resultRef = new AtomicReference<>();
        AtomicReference<String> errorRef = new AtomicReference<>();

        DanmakuApi.getBangumiEpisodes(animeId, new DanmakuApi.DanmakuCallback<List<DanmakuEpisode>>() {
            @Override
            public void onSuccess(List<DanmakuEpisode> data) {
                resultRef.set(data);
                latch.countDown();
            }

            @Override
            public void onError(String message) {
                errorRef.set(message);
                latch.countDown();
            }
        });

        try {
            // 等待最多10秒
            if (!latch.await(10, TimeUnit.SECONDS)) {
                return jsonResponse(createErrorResponse("获取剧集超时"));
            }

            if (errorRef.get() != null) {
                return jsonResponse(createErrorResponse(errorRef.get()));
            }

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("data", resultRef.get());
            return jsonResponse(response);

        } catch (InterruptedException e) {
            return jsonResponse(createErrorResponse("获取剧集被中断"));
        }
    }

    /**
     * 处理投送弹幕请求
     */
    private NanoHTTPD.Response handleCast(Map<String, String> params) {
        String episodeIdStr = params.get("episodeId");
        String episodeName = params.get("episodeName");

        if (episodeIdStr == null || episodeIdStr.trim().isEmpty()) {
            return jsonResponse(createErrorResponse("剧集ID不能为空"));
        }

        int episodeId;
        try {
            episodeId = Integer.parseInt(episodeIdStr);
        } catch (NumberFormatException e) {
            return jsonResponse(createErrorResponse("剧集ID格式错误"));
        }

        try {
            if (episodeName != null) {
                episodeName = URLDecoder.decode(episodeName, "UTF-8");
            }
        } catch (UnsupportedEncodingException e) {
            // 忽略解码错误
        }

        // 获取弹幕URL
        String danmakuUrl = DanmakuApi.getDanmakuUrl(episodeId);

        // 投送到播放器
        App.post(() -> RefreshEvent.danmaku(danmakuUrl));

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", "弹幕投送成功");
        return jsonResponse(response);
    }

    /**
     * 处理弹幕设置请求
     */
    private NanoHTTPD.Response handleSettings(Map<String, String> params) {
        String key = params.get("key");
        String value = params.get("value");

        // 如果有 key 和 value，则保存设置
        if (key != null && value != null) {
            try {
                switch (key) {
                    case "load":
                        boolean load = Integer.parseInt(value) != 0;
                        com.github.tvbox.osc.Setting.putDanmuLoad(load);
                        break;
                    case "speed":
                        int speed = Integer.parseInt(value);
                        com.github.tvbox.osc.Setting.putDanmuSpeed(speed);
                        break;
                    case "size":
                        float size = Float.parseFloat(value);
                        com.github.tvbox.osc.Setting.putDanmuSize(size);
                        break;
                    case "line":
                        int line = Integer.parseInt(value);
                        com.github.tvbox.osc.Setting.putDanmuLine(line);
                        break;
                    case "alpha":
                        int alpha = Integer.parseInt(value);
                        com.github.tvbox.osc.Setting.putDanmuAlpha(alpha);
                        break;
                    default:
                        return jsonResponse(createErrorResponse("未知的设置项"));
                }

                // 通知播放器更新弹幕设置
                App.post(() -> com.github.tvbox.osc.event.RefreshEvent.danmakuSetting());

                Map<String, Object> response = new HashMap<>();
                response.put("success", true);
                response.put("message", "设置已保存");
                return jsonResponse(response);

            } catch (NumberFormatException e) {
                return jsonResponse(createErrorResponse("设置值格式错误"));
            }
        }

        // 否则返回当前设置
        Map<String, Object> settings = new HashMap<>();
        settings.put("load", com.github.tvbox.osc.Setting.isDanmuLoad());
        settings.put("speed", com.github.tvbox.osc.Setting.getDanmuSpeed());
        settings.put("size", com.github.tvbox.osc.Setting.getDanmuSize());
        settings.put("line", com.github.tvbox.osc.Setting.getDanmuLine(3));
        settings.put("alpha", com.github.tvbox.osc.Setting.getDanmuAlpha());

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("data", settings);
        return jsonResponse(response);
    }

    /**
     * 创建错误响应
     */
    private Map<String, Object> createErrorResponse(String message) {
        Map<String, Object> response = new HashMap<>();
        response.put("success", false);
        response.put("message", message);
        return response;
    }

    /**
     * 返回 JSON 响应
     */
    private NanoHTTPD.Response jsonResponse(Object data) {
        String json = gson.toJson(data);
        NanoHTTPD.Response response = NanoHTTPD.newFixedLengthResponse(
            NanoHTTPD.Response.Status.OK,
            "application/json; charset=utf-8",
            json
        );
        addCorsHeaders(response);
        return response;
    }

    /**
     * 添加 CORS 头
     */
    private void addCorsHeaders(NanoHTTPD.Response response) {
        response.addHeader("Access-Control-Allow-Origin", "*");
        response.addHeader("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
        response.addHeader("Access-Control-Allow-Headers", "Content-Type");
    }

    /**
     * 返回弹幕投送页面 HTML
     */
    private NanoHTTPD.Response getDanmakuPage(NanoHTTPD.IHTTPSession session) {
        String html = "<!DOCTYPE html>\n" +
            "<html lang=\"zh-CN\">\n" +
            "<head>\n" +
            "    <meta charset=\"UTF-8\">\n" +
            "    <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no\">\n" +
            "    <title>弹幕投送</title>\n" +
            "    <style>\n" +
            "        * {\n" +
            "            margin: 0;\n" +
            "            padding: 0;\n" +
            "            box-sizing: border-box;\n" +
            "        }\n" +
            "        body {\n" +
            "            font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, 'Helvetica Neue', Arial, sans-serif;\n" +
            "            background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);\n" +
            "            min-height: 100vh;\n" +
            "            padding: 20px;\n" +
            "        }\n" +
            "        .container {\n" +
            "            max-width: 600px;\n" +
            "            margin: 0 auto;\n" +
            "        }\n" +
            "        .header {\n" +
            "            text-align: center;\n" +
            "            color: white;\n" +
            "            margin-bottom: 30px;\n" +
            "        }\n" +
            "        .header h1 {\n" +
            "            font-size: 28px;\n" +
            "            margin-bottom: 10px;\n" +
            "        }\n" +
            "        .header p {\n" +
            "            font-size: 14px;\n" +
            "            opacity: 0.9;\n" +
            "        }\n" +
            "        .search-box {\n" +
            "            background: white;\n" +
            "            border-radius: 12px;\n" +
            "            padding: 20px;\n" +
            "            box-shadow: 0 10px 30px rgba(0,0,0,0.2);\n" +
            "            margin-bottom: 20px;\n" +
            "        }\n" +
            "        .search-input-group {\n" +
            "            display: flex;\n" +
            "            gap: 10px;\n" +
            "        }\n" +
            "        .search-input {\n" +
            "            flex: 1;\n" +
            "            padding: 12px 16px;\n" +
            "            border: 2px solid #e0e0e0;\n" +
            "            border-radius: 8px;\n" +
            "            font-size: 16px;\n" +
            "            transition: border-color 0.3s;\n" +
            "        }\n" +
            "        .search-input:focus {\n" +
            "            outline: none;\n" +
            "            border-color: #667eea;\n" +
            "        }\n" +
            "        .search-btn {\n" +
            "            padding: 12px 24px;\n" +
            "            background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);\n" +
            "            color: white;\n" +
            "            border: none;\n" +
            "            border-radius: 8px;\n" +
            "            font-size: 16px;\n" +
            "            cursor: pointer;\n" +
            "            transition: transform 0.2s;\n" +
            "        }\n" +
            "        .search-btn:active {\n" +
            "            transform: scale(0.95);\n" +
            "        }\n" +
            "        .results {\n" +
            "            background: white;\n" +
            "            border-radius: 12px;\n" +
            "            padding: 20px;\n" +
            "            box-shadow: 0 10px 30px rgba(0,0,0,0.2);\n" +
            "            display: none;\n" +
            "        }\n" +
            "        .results.show {\n" +
            "            display: block;\n" +
            "        }\n" +
            "        .result-item {\n" +
            "            padding: 15px;\n" +
            "            border-bottom: 1px solid #f0f0f0;\n" +
            "            cursor: pointer;\n" +
            "            transition: background 0.2s;\n" +
            "        }\n" +
            "        .result-item:last-child {\n" +
            "            border-bottom: none;\n" +
            "        }\n" +
            "        .result-item:active {\n" +
            "            background: #f5f5f5;\n" +
            "        }\n" +
            "        .result-title {\n" +
            "            font-size: 16px;\n" +
            "            font-weight: 500;\n" +
            "            color: #333;\n" +
            "            margin-bottom: 5px;\n" +
            "        }\n" +
            "        .result-info {\n" +
            "            font-size: 12px;\n" +
            "            color: #999;\n" +
            "        }\n" +
            "        .loading {\n" +
            "            text-align: center;\n" +
            "            padding: 40px;\n" +
            "            color: #999;\n" +
            "        }\n" +
            "        .empty {\n" +
            "            text-align: center;\n" +
            "            padding: 40px;\n" +
            "            color: #999;\n" +
            "        }\n" +
            "        .back-btn {\n" +
            "            display: inline-block;\n" +
            "            padding: 8px 16px;\n" +
            "            background: #f0f0f0;\n" +
            "            color: #666;\n" +
            "            border-radius: 6px;\n" +
            "            font-size: 14px;\n" +
            "            cursor: pointer;\n" +
            "            margin-bottom: 15px;\n" +
            "        }\n" +
            "        .back-btn:active {\n" +
            "            background: #e0e0e0;\n" +
            "        }\n" +
            "        .toast {\n" +
            "            position: fixed;\n" +
            "            top: 50%;\n" +
            "            left: 50%;\n" +
            "            transform: translate(-50%, -50%);\n" +
            "            background: rgba(0,0,0,0.8);\n" +
            "            color: white;\n" +
            "            padding: 15px 30px;\n" +
            "            border-radius: 8px;\n" +
            "            font-size: 14px;\n" +
            "            z-index: 9999;\n" +
            "            display: none;\n" +
            "        }\n" +
            "        .toast.show {\n" +
            "            display: block;\n" +
            "        }\n" +
            "        .fab-container {\n" +
            "            position: fixed;\n" +
            "            right: 20px;\n" +
            "            bottom: 90px;\n" +
            "            display: none;\n" +
            "            flex-direction: column;\n" +
            "            gap: 10px;\n" +
            "            z-index: 998;\n" +
            "        }\n" +
            "        .fab-container.show {\n" +
            "            display: flex;\n" +
            "        }\n" +
            "        .fab-btn {\n" +
            "            width: 56px;\n" +
            "            height: 56px;\n" +
            "            border-radius: 50%;\n" +
            "            background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);\n" +
            "            color: white;\n" +
            "            border: none;\n" +
            "            box-shadow: 0 4px 12px rgba(0,0,0,0.3);\n" +
            "            cursor: pointer;\n" +
            "            font-size: 24px;\n" +
            "            display: flex;\n" +
            "            align-items: center;\n" +
            "            justify-content: center;\n" +
            "            transition: transform 0.2s, box-shadow 0.2s;\n" +
            "        }\n" +
            "        .fab-btn:active {\n" +
            "            transform: scale(0.9);\n" +
            "            box-shadow: 0 2px 8px rgba(0,0,0,0.3);\n" +
            "        }\n" +
            "        .fab-btn.secondary {\n" +
            "            width: 48px;\n" +
            "            height: 48px;\n" +
            "            font-size: 20px;\n" +
            "            background: rgba(255,255,255,0.9);\n" +
            "            color: #667eea;\n" +
            "        }\n" +
            "        .settings-modal {\n" +
            "            display: none;\n" +
            "            position: fixed;\n" +
            "            top: 0;\n" +
            "            left: 0;\n" +
            "            right: 0;\n" +
            "            bottom: 0;\n" +
            "            background: rgba(0,0,0,0.5);\n" +
            "            z-index: 2000;\n" +
            "            align-items: center;\n" +
            "            justify-content: center;\n" +
            "        }\n" +
            "        .settings-modal.show {\n" +
            "            display: flex;\n" +
            "        }\n" +
            "        .settings-content {\n" +
            "            background: white;\n" +
            "            border-radius: 12px;\n" +
            "            padding: 24px;\n" +
            "            max-width: 400px;\n" +
            "            width: 90%;\n" +
            "            max-height: 80vh;\n" +
            "            overflow-y: auto;\n" +
            "        }\n" +
            "        .settings-header {\n" +
            "            font-size: 20px;\n" +
            "            font-weight: bold;\n" +
            "            margin-bottom: 20px;\n" +
            "            color: #333;\n" +
            "        }\n" +
            "        .setting-item {\n" +
            "            margin-bottom: 20px;\n" +
            "        }\n" +
            "        .setting-label {\n" +
            "            font-size: 14px;\n" +
            "            color: #666;\n" +
            "            margin-bottom: 8px;\n" +
            "            display: flex;\n" +
            "            justify-content: space-between;\n" +
            "        }\n" +
            "        .setting-value {\n" +
            "            font-weight: 500;\n" +
            "            color: #667eea;\n" +
            "        }\n" +
            "        .slider-container {\n" +
            "            width: 100%;\n" +
            "        }\n" +
            "        .slider {\n" +
            "            width: 100%;\n" +
            "            height: 6px;\n" +
            "            border-radius: 3px;\n" +
            "            background: #e0e0e0;\n" +
            "            outline: none;\n" +
            "            -webkit-appearance: none;\n" +
            "        }\n" +
            "        .slider::-webkit-slider-thumb {\n" +
            "            -webkit-appearance: none;\n" +
            "            appearance: none;\n" +
            "            width: 20px;\n" +
            "            height: 20px;\n" +
            "            border-radius: 50%;\n" +
            "            background: #667eea;\n" +
            "            cursor: pointer;\n" +
            "        }\n" +
            "        .slider::-moz-range-thumb {\n" +
            "            width: 20px;\n" +
            "            height: 20px;\n" +
            "            border-radius: 50%;\n" +
            "            background: #667eea;\n" +
            "            cursor: pointer;\n" +
            "            border: none;\n" +
            "        }\n" +
            "        .close-btn {\n" +
            "            width: 100%;\n" +
            "            padding: 12px;\n" +
            "            background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);\n" +
            "            color: white;\n" +
            "            border: none;\n" +
            "            border-radius: 8px;\n" +
            "            font-size: 16px;\n" +
            "            cursor: pointer;\n" +
            "            margin-top: 10px;\n" +
            "        }\n" +
            "    </style>\n" +
            "</head>\n" +
            "<body>\n" +
            "    <div class=\"container\">\n" +
            "        <div class=\"header\">\n" +
            "            <h1>🎬 弹幕投送</h1>\n" +
            "            <p id=\"headerSubtitle\">搜索并投送弹幕到电视</p>\n" +
            "        </div>\n" +
            "        \n" +
            "        <!-- 弹幕关闭提示 -->\n" +
            "        <div id=\"danmuOffContainer\" style=\"display: none; text-align: center; padding: 40px;\">\n" +
            "            <div style=\"background: white; border-radius: 12px; padding: 40px; box-shadow: 0 10px 30px rgba(0,0,0,0.2);\">\n" +
            "                <h2 style=\"color: #333; margin-bottom: 20px;\">弹幕功能已关闭</h2>\n" +
            "                <p style=\"color: #666; margin-bottom: 30px;\">请先开启弹幕功能</p>\n" +
            "                <button class=\"search-btn\" id=\"enableDanmuBtn\" style=\"padding: 15px 40px; font-size: 18px;\">开启弹幕</button>\n" +
            "            </div>\n" +
            "        </div>\n" +
            "        \n" +
            "        <!-- 搜索和结果区域 -->\n" +
            "        <div id=\"danmuOnContainer\">\n" +
            "            <div class=\"search-box\">\n" +
            "                <div class=\"search-input-group\">\n" +
            "                    <input type=\"text\" class=\"search-input\" id=\"searchInput\" placeholder=\"输入剧名搜索...\">\n" +
            "                    <button class=\"search-btn\" id=\"searchBtn\">搜索</button>\n" +
            "                </div>\n" +
            "            </div>\n" +
            "            \n" +
            "            <div class=\"results\" id=\"results\">\n" +
            "                <div id=\"resultsContent\"></div>\n" +
            "            </div>\n" +
            "        </div>\n" +
            "    </div>\n" +
            "    \n" +
            "    <div class=\"toast\" id=\"toast\"></div>\n" +
            "    \n" +
            "    <!-- 悬浮按钮 -->\n" +
            "    <div class=\"fab-container\" id=\"fabContainer\">\n" +
            "        <button class=\"fab-btn secondary\" id=\"topBtn\" title=\"回到顶部\">↑</button>\n" +
            "        <button class=\"fab-btn secondary\" id=\"bottomBtn\" title=\"跳到底部\">↓</button>\n" +
            "        <button class=\"fab-btn secondary\" id=\"jumpBtn\" title=\"跳转到指定集\">#</button>\n" +
            "        <button class=\"fab-btn\" id=\"reverseBtn\" title=\"反转列表\">⇅</button>\n" +
            "    </div>\n" +
            "    \n" +
            "    <!-- 设置按钮（始终显示） -->\n" +
            "    <div style=\"position: fixed; right: 20px; bottom: 20px; z-index: 999; display: flex; flex-direction: column; gap: 10px;\">\n" +
            "        <button class=\"fab-btn\" id=\"settingsBtn\" title=\"弹幕设置\">⚙</button>\n" +
            "    </div>\n" +
            "    \n" +
            "    <!-- 弹幕设置弹窗 -->\n" +
            "    <div class=\"settings-modal\" id=\"settingsModal\">\n" +
            "        <div class=\"settings-content\">\n" +
            "            <div class=\"settings-header\">弹幕设置</div>\n" +
            "            \n" +
            "            <div class=\"setting-item\">\n" +
            "                <div class=\"setting-label\">\n" +
            "                    <span>弹幕开关</span>\n" +
            "                    <span class=\"setting-value\" id=\"loadValue\">开</span>\n" +
            "                </div>\n" +
            "                <button class=\"close-btn\" id=\"loadToggleBtn\" style=\"background: #667eea; margin-top: 10px;\">切换</button>\n" +
            "            </div>\n" +
            "            \n" +
            "            <div class=\"setting-item\">\n" +
            "                <div class=\"setting-label\">\n" +
            "                    <span>速度</span>\n" +
            "                    <span class=\"setting-value\" id=\"speedValue\">正常</span>\n" +
            "                </div>\n" +
            "                <input type=\"range\" class=\"slider\" id=\"speedSlider\" min=\"0\" max=\"3\" step=\"1\" value=\"2\">\n" +
            "            </div>\n" +
            "            \n" +
            "            <div class=\"setting-item\">\n" +
            "                <div class=\"setting-label\">\n" +
            "                    <span>大小</span>\n" +
            "                    <span class=\"setting-value\" id=\"sizeValue\">1.0倍</span>\n" +
            "                </div>\n" +
            "                <input type=\"range\" class=\"slider\" id=\"sizeSlider\" min=\"0.6\" max=\"2\" step=\"0.1\" value=\"1\">\n" +
            "            </div>\n" +
            "            \n" +
            "            <div class=\"setting-item\">\n" +
            "                <div class=\"setting-label\">\n" +
            "                    <span>行数</span>\n" +
            "                    <span class=\"setting-value\" id=\"lineValue\">3行</span>\n" +
            "                </div>\n" +
            "                <input type=\"range\" class=\"slider\" id=\"lineSlider\" min=\"1\" max=\"15\" step=\"1\" value=\"3\">\n" +
            "            </div>\n" +
            "            \n" +
            "            <div class=\"setting-item\">\n" +
            "                <div class=\"setting-label\">\n" +
            "                    <span>透明度</span>\n" +
            "                    <span class=\"setting-value\" id=\"alphaValue\">90%</span>\n" +
            "                </div>\n" +
            "                <input type=\"range\" class=\"slider\" id=\"alphaSlider\" min=\"10\" max=\"100\" step=\"5\" value=\"90\">\n" +
            "            </div>\n" +
            "            \n" +
            "            <button class=\"close-btn\" id=\"closeSettingsBtn\">关闭</button>\n" +
            "        </div>\n" +
            "    </div>\n" +
            "    \n" +
            "    <!-- 跳转弹窗 -->\n" +
            "    <div class=\"settings-modal\" id=\"jumpModal\">\n" +
            "        <div class=\"settings-content\" style=\"max-width: 300px;\">\n" +
            "            <div class=\"settings-header\">跳转到指定集</div>\n" +
            "            <div style=\"margin-bottom: 20px;\">\n" +
            "                <input type=\"number\" id=\"jumpInput\" class=\"search-input\" placeholder=\"输入集数...\" style=\"width: 100%; margin: 0;\">\n" +
            "            </div>\n" +
            "            <button class=\"close-btn\" id=\"jumpConfirmBtn\">跳转</button>\n" +
            "            <button class=\"close-btn\" id=\"closeJumpBtn\" style=\"background: #f0f0f0; color: #666; margin-top: 10px;\">取消</button>\n" +
            "        </div>\n" +
            "    </div>\n" +
            "    \n" +
            "    <script>\n" +
            "        let currentAnimeId = null;\n" +
            "        let currentAnimeName = '';\n" +
            "        let targetEpisodeNumber = null;\n" +
            "        let currentEpisodes = null;\n" +
            "        let isReversed = false;\n" +
            "        let highlightedEpisodeId = null;\n" +
            "        \n" +
            "        // 更新页面显示状态\n" +
            "        function updatePageDisplay() {\n" +
            "            if (danmakuSettings.load) {\n" +
            "                document.getElementById('danmuOffContainer').style.display = 'none';\n" +
            "                document.getElementById('danmuOnContainer').style.display = 'block';\n" +
            "            } else {\n" +
            "                document.getElementById('danmuOffContainer').style.display = 'block';\n" +
            "                document.getElementById('danmuOnContainer').style.display = 'none';\n" +
            "            }\n" +
            "        }\n" +
            "        \n" +
            "        // 初始化\n" +
            "        window.onload = function() {\n" +
            "            // 先加载设置，然后根据设置决定是否自动搜索\n" +
            "            loadSettings().then(() => {\n" +
            "                updatePageDisplay();\n" +
            "                \n" +
            "                // 只有在弹幕开启时才自动搜索\n" +
            "                if (danmakuSettings.load) {\n" +
            "                    // 从服务器获取当前播放信息\n" +
            "                    fetch('/media')\n" +
            "                        .then(res => res.json())\n" +
            "                        .then(data => {\n" +
            "                            if (data && data.title) {\n" +
            "                                const name = data.title;\n" +
            "                                const episode = data.artist || '';\n" +
            "                                \n" +
            "                                // 显示当前剧名和集名\n" +
            "                                if (name || episode) {\n" +
            "                                    let subtitle = '当前: ';\n" +
            "                                    if (name) subtitle += name;\n" +
            "                                    if (episode) subtitle += ' - ' + episode;\n" +
            "                                    document.getElementById('headerSubtitle').textContent = subtitle;\n" +
            "                                }\n" +
            "                                \n" +
            "                                if (episode) {\n" +
            "                                    targetEpisodeNumber = parseEpisodeNumber(episode);\n" +
            "                                }\n" +
            "                                \n" +
            "                                if (name) {\n" +
            "                                    const cleanName = cleanTitle(name);\n" +
            "                                    document.getElementById('searchInput').value = cleanName;\n" +
            "                                    performSearch();\n" +
            "                                }\n" +
            "                            }\n" +
            "                        })\n" +
            "                        .catch(err => {\n" +
            "                            console.log('获取播放信息失败:', err);\n" +
            "                        });\n" +
            "                }\n" +
            "            });\n" +
            "        };\n" +
            "        \n" +
            "        // 清理标题\n" +
            "        function cleanTitle(title) {\n" +
            "            return title.replace(/第\\d+集|\\d+集|EP?\\d+|S\\d+E\\d+|\\d{8}|\\d{4}-\\d{2}-\\d{2}/gi, '').trim();\n" +
            "        }\n" +
            "        \n" +
            "        // 解析集数\n" +
            "        function parseEpisodeNumber(episode) {\n" +
            "            const patterns = [\n" +
            "                /S\\d+E(\\d+)/i,\n" +
            "                /EP?(\\d+)/i,\n" +
            "                /第(\\d+)[话集]/,\n" +
            "                /\\[(\\d+)\\]/\n" +
            "            ];\n" +
            "            for (let pattern of patterns) {\n" +
            "                const match = episode.match(pattern);\n" +
            "                if (match) return parseInt(match[1]);\n" +
            "            }\n" +
            "            return null;\n" +
            "        }\n" +
            "        \n" +
            "        // 自动匹配剧集（参考播放器的多级匹配策略）\n" +
            "        function autoMatchEpisode(episodes, episodeName, episodeNumber) {\n" +
            "            if (!episodes || episodes.length === 0) return -1;\n" +
            "            \n" +
            "            // 规则0: 标准格式解析（最高优先级）\n" +
            "            if (episodeName) {\n" +
            "                const parsedFromName = parseEpisodeNumber(episodeName);\n" +
            "                if (parsedFromName) {\n" +
            "                    for (let i = 0; i < episodes.length; i++) {\n" +
            "                        const ep = episodes[i];\n" +
            "                        if (ep.episodeNumber && parseInt(ep.episodeNumber) === parsedFromName) {\n" +
            "                            return i;\n" +
            "                        }\n" +
            "                        const title = ep.episodeTitle || '';\n" +
            "                        const parsedFromTitle = parseEpisodeNumber(title);\n" +
            "                        if (parsedFromTitle === parsedFromName) {\n" +
            "                            return i;\n" +
            "                        }\n" +
            "                    }\n" +
            "                }\n" +
            "            }\n" +
            "            \n" +
            "            // 规则1: 精准匹配剧集名\n" +
            "            if (episodeName) {\n" +
            "                for (let i = 0; i < episodes.length; i++) {\n" +
            "                    const ep = episodes[i];\n" +
            "                    const epTitle = ep.episodeTitle || '';\n" +
            "                    if (episodeName === epTitle) {\n" +
            "                        return i;\n" +
            "                    }\n" +
            "                }\n" +
            "            }\n" +
            "            \n" +
            "            // 规则2: 使用集数匹配\n" +
            "            if (episodeNumber) {\n" +
            "                for (let i = 0; i < episodes.length; i++) {\n" +
            "                    const ep = episodes[i];\n" +
            "                    if (ep.episodeNumber && parseInt(ep.episodeNumber) === episodeNumber) {\n" +
            "                        return i;\n" +
            "                    }\n" +
            "                    const title = ep.episodeTitle || '';\n" +
            "                    const parsedNumber = parseEpisodeNumber(title);\n" +
            "                    if (parsedNumber === episodeNumber) {\n" +
            "                        return i;\n" +
            "                    }\n" +
            "                }\n" +
            "            }\n" +
            "            \n" +
            "            return -1;\n" +
            "        }\n" +
            "        \n" +
            "        // 搜索按钮点击\n" +
            "        document.getElementById('searchBtn').addEventListener('click', performSearch);\n" +
            "        \n" +
            "        // 回车搜索\n" +
            "        document.getElementById('searchInput').addEventListener('keypress', function(e) {\n" +
            "            if (e.key === 'Enter') {\n" +
            "                performSearch();\n" +
            "            }\n" +
            "        });\n" +
            "        \n" +
            "        // 执行搜索\n" +
            "        function performSearch() {\n" +
            "            const keyword = document.getElementById('searchInput').value.trim();\n" +
            "            if (!keyword) {\n" +
            "                showToast('请输入搜索关键词');\n" +
            "                return;\n" +
            "            }\n" +
            "            \n" +
            "            showLoading();\n" +
            "            \n" +
            "            fetch('/danmaku/api/search?keyword=' + encodeURIComponent(keyword))\n" +
            "                .then(res => res.json())\n" +
            "                .then(data => {\n" +
            "                    if (data.success) {\n" +
            "                        showAnimeList(data.data);\n" +
            "                        // 自动选择第一个\n" +
            "                        if (data.data && data.data.length > 0) {\n" +
            "                            selectAnime(data.data[0].animeId, data.data[0].animeTitle);\n" +
            "                        }\n" +
            "                    } else {\n" +
            "                        showEmpty(data.message || '搜索失败');\n" +
            "                    }\n" +
            "                })\n" +
            "                .catch(err => {\n" +
            "                    showEmpty('网络错误: ' + err.message);\n" +
            "                });\n" +
            "        }\n" +
            "        \n" +
            "        // 显示番剧列表\n" +
            "        function showAnimeList(animes) {\n" +
            "            if (!animes || animes.length === 0) {\n" +
            "                showEmpty('未找到相关番剧');\n" +
            "                return;\n" +
            "            }\n" +
            "            \n" +
            "            let html = '';\n" +
            "            animes.forEach(anime => {\n" +
            "                html += `\n" +
            "                    <div class=\"result-item\" onclick=\"selectAnime(${anime.animeId}, '${escapeHtml(anime.animeTitle)}')\">\n" +
            "                        <div class=\"result-title\">${escapeHtml(anime.animeTitle)}</div>\n" +
            "                        <div class=\"result-info\">${anime.type || ''} | ${anime.episodeCount || 0}集</div>\n" +
            "                    </div>\n" +
            "                `;\n" +
            "            });\n" +
            "            \n" +
            "            document.getElementById('resultsContent').innerHTML = html;\n" +
            "            document.getElementById('results').classList.add('show');\n" +
            "            \n" +
            "            // 隐藏悬浮按钮\n" +
            "            document.getElementById('fabContainer').classList.remove('show');\n" +
            "        }\n" +
            "        \n" +
            "        // 选择番剧\n" +
            "        function selectAnime(animeId, animeName) {\n" +
            "            currentAnimeId = animeId;\n" +
            "            currentAnimeName = animeName;\n" +
            "            \n" +
            "            showLoading();\n" +
            "            \n" +
            "            fetch('/danmaku/api/episodes?animeId=' + animeId)\n" +
            "                .then(res => res.json())\n" +
            "                .then(data => {\n" +
            "                    if (data.success) {\n" +
            "                        showEpisodeList(data.data);\n" +
            "                    } else {\n" +
            "                        showEmpty(data.message || '获取剧集失败');\n" +
            "                    }\n" +
            "                })\n" +
            "                .catch(err => {\n" +
            "                    showEmpty('网络错误: ' + err.message);\n" +
            "                });\n" +
            "        }\n" +
            "        \n" +
            "        // 显示剧集列表\n" +
            "        function showEpisodeList(episodes) {\n" +
            "            if (!episodes || episodes.length === 0) {\n" +
            "                showEmpty('该番剧暂无剧集');\n" +
            "                document.getElementById('fabContainer').classList.remove('show');\n" +
            "                return;\n" +
            "            }\n" +
            "            \n" +
            "            // 保存当前剧集列表（如果不是反转操作，则保存原始列表）\n" +
            "            if (!currentEpisodes || currentEpisodes.length !== episodes.length) {\n" +
            "                currentEpisodes = episodes;\n" +
            "                isReversed = false;\n" +
            "            }\n" +
            "            \n" +
            "            let html = '<div class=\"back-btn\" onclick=\"performSearch()\">← 返回搜索结果</div>';\n" +
            "            episodes.forEach((episode, index) => {\n" +
            "                const title = episode.episodeTitle || '第' + episode.episodeNumber + '集';\n" +
            "                html += `\n" +
            "                    <div class=\"result-item\" data-index=\"${index}\" onclick=\"castEpisode(${episode.episodeId}, '${escapeHtml(title)}')\">\n" +
            "                        <div class=\"result-title\">${escapeHtml(title)}</div>\n" +
            "                    </div>\n" +
            "                `;\n" +
            "            });\n" +
            "            \n" +
            "            document.getElementById('resultsContent').innerHTML = html;\n" +
            "            document.getElementById('results').classList.add('show');\n" +
            "            \n" +
            "            // 显示悬浮按钮\n" +
            "            document.getElementById('fabContainer').classList.add('show');\n" +
            "            \n" +
            "            // 使用改进的自动匹配功能（仅在首次加载时）\n" +
            "            if (!isReversed) {\n" +
            "                const urlParams = new URLSearchParams(window.location.search);\n" +
            "                const episodeName = urlParams.get('episode');\n" +
            "                const matchedIndex = autoMatchEpisode(episodes, episodeName, targetEpisodeNumber);\n" +
            "                \n" +
            "                if (matchedIndex >= 0) {\n" +
            "                    highlightedEpisodeId = episodes[matchedIndex].episodeId;\n" +
            "                    setTimeout(() => {\n" +
            "                        const items = document.querySelectorAll('.result-item');\n" +
            "                        const matchedItem = items[matchedIndex];\n" +
            "                        if (matchedItem) {\n" +
            "                            matchedItem.scrollIntoView({ behavior: 'smooth', block: 'center' });\n" +
            "                            matchedItem.style.background = '#fff3cd';\n" +
            "                            const epTitle = episodes[matchedIndex].episodeTitle || '第' + episodes[matchedIndex].episodeNumber + '集';\n" +
            "                            showToast('已自动定位到: ' + epTitle);\n" +
            "                        }\n" +
            "                    }, 200);\n" +
            "                }\n" +
            "            }\n" +
            "        }\n" +
            "        \n" +
            "        // 仅渲染剧集列表（不修改 currentEpisodes）\n" +
            "        function renderEpisodeList(episodes, highlightEpisodeId) {\n" +
            "            let html = '<div class=\"back-btn\" onclick=\"performSearch()\">← 返回搜索结果</div>';\n" +
            "            let highlightIndex = -1;\n" +
            "            episodes.forEach((episode, index) => {\n" +
            "                const title = episode.episodeTitle || '第' + episode.episodeNumber + '集';\n" +
            "                const isHighlight = highlightEpisodeId && episode.episodeId === highlightEpisodeId;\n" +
            "                if (isHighlight) highlightIndex = index;\n" +
            "                const bgStyle = isHighlight ? ' style=\"background: #fff3cd;\"' : '';\n" +
            "                html += `\n" +
            "                    <div class=\"result-item\" data-index=\"${index}\" data-episode-id=\"${episode.episodeId}\" onclick=\"castEpisode(${episode.episodeId}, '${escapeHtml(title)}')\"${bgStyle}>\n" +
            "                        <div class=\"result-title\">${escapeHtml(title)}</div>\n" +
            "                    </div>\n" +
            "                `;\n" +
            "            });\n" +
            "            \n" +
            "            document.getElementById('resultsContent').innerHTML = html;\n" +
            "            \n" +
            "            // 滚动到高亮项\n" +
            "            if (highlightIndex >= 0) {\n" +
            "                setTimeout(() => {\n" +
            "                    const items = document.querySelectorAll('.result-item');\n" +
            "                    if (items[highlightIndex]) {\n" +
            "                        items[highlightIndex].scrollIntoView({ behavior: 'smooth', block: 'center' });\n" +
            "                    }\n" +
            "                }, 100);\n" +
            "            }\n" +
            "        }\n" +
            "        \n" +
            "        // 投送剧集\n" +
            "        function castEpisode(episodeId, episodeName) {\n" +
            "            showToast('正在投送...');\n" +
            "            \n" +
            "            fetch('/danmaku/api/cast?episodeId=' + episodeId + '&episodeName=' + encodeURIComponent(episodeName))\n" +
            "                .then(res => res.json())\n" +
            "                .then(data => {\n" +
            "                    if (data.success) {\n" +
            "                        showToast('✓ ' + (data.message || '投送成功'));\n" +
            "                    } else {\n" +
            "                        showToast('✗ ' + (data.message || '投送失败'));\n" +
            "                    }\n" +
            "                })\n" +
            "                .catch(err => {\n" +
            "                    showToast('✗ 网络错误');\n" +
            "                });\n" +
            "        }\n" +
            "        \n" +
            "        // 显示加载中\n" +
            "        function showLoading() {\n" +
            "            document.getElementById('resultsContent').innerHTML = '<div class=\"loading\">加载中...</div>';\n" +
            "            document.getElementById('results').classList.add('show');\n" +
            "        }\n" +
            "        \n" +
            "        // 显示空状态\n" +
            "        function showEmpty(message) {\n" +
            "            document.getElementById('resultsContent').innerHTML = '<div class=\"empty\">' + escapeHtml(message) + '</div>';\n" +
            "            document.getElementById('results').classList.add('show');\n" +
            "        }\n" +
            "        \n" +
            "        // 显示提示\n" +
            "        function showToast(message) {\n" +
            "            const toast = document.getElementById('toast');\n" +
            "            toast.textContent = message;\n" +
            "            toast.classList.add('show');\n" +
            "            setTimeout(() => {\n" +
            "                toast.classList.remove('show');\n" +
            "            }, 2000);\n" +
            "        }\n" +
            "        \n" +
            "        // HTML 转义\n" +
            "        function escapeHtml(text) {\n" +
            "            const div = document.createElement('div');\n" +
            "            div.textContent = text;\n" +
            "            return div.innerHTML;\n" +
            "        }\n" +
            "        \n" +
            "        // 悬浮按钮功能\n" +
            "        document.getElementById('topBtn').addEventListener('click', function() {\n" +
            "            window.scrollTo({ top: 0, behavior: 'smooth' });\n" +
            "        });\n" +
            "        \n" +
            "        document.getElementById('bottomBtn').addEventListener('click', function() {\n" +
            "            window.scrollTo({ top: document.body.scrollHeight, behavior: 'smooth' });\n" +
            "        });\n" +
            "        \n" +
            "        document.getElementById('reverseBtn').addEventListener('click', function() {\n" +
            "            if (!currentEpisodes || currentEpisodes.length === 0) {\n" +
            "                showToast('没有可反转的列表');\n" +
            "                return;\n" +
            "            }\n" +
            "            \n" +
            "            isReversed = !isReversed;\n" +
            "            // 根据 isReversed 状态决定显示顺序\n" +
            "            const displayEpisodes = isReversed ? [...currentEpisodes].reverse() : currentEpisodes;\n" +
            "            renderEpisodeList(displayEpisodes, highlightedEpisodeId);\n" +
            "            showToast(isReversed ? '已反转列表' : '已恢复顺序');\n" +
            "        });\n" +
            "        \n" +
            "        // 跳转按钮\n" +
            "        document.getElementById('jumpBtn').addEventListener('click', function() {\n" +
            "            if (!currentEpisodes || currentEpisodes.length === 0) {\n" +
            "                showToast('没有可跳转的列表');\n" +
            "                return;\n" +
            "            }\n" +
            "            document.getElementById('jumpModal').classList.add('show');\n" +
            "            document.getElementById('jumpInput').value = '';\n" +
            "            document.getElementById('jumpInput').focus();\n" +
            "        });\n" +
            "        \n" +
            "        // 关闭跳转弹窗\n" +
            "        document.getElementById('closeJumpBtn').addEventListener('click', function() {\n" +
            "            document.getElementById('jumpModal').classList.remove('show');\n" +
            "        });\n" +
            "        \n" +
            "        // 点击背景关闭跳转弹窗\n" +
            "        document.getElementById('jumpModal').addEventListener('click', function(e) {\n" +
            "            if (e.target === this) {\n" +
            "                this.classList.remove('show');\n" +
            "            }\n" +
            "        });\n" +
            "        \n" +
            "        // 跳转确认\n" +
            "        function performJump() {\n" +
            "            const episodeNum = parseInt(document.getElementById('jumpInput').value);\n" +
            "            if (!episodeNum || episodeNum < 1) {\n" +
            "                showToast('请输入有效的集数');\n" +
            "                return;\n" +
            "            }\n" +
            "            \n" +
            "            // 在当前显示的列表中查找\n" +
            "            const displayEpisodes = isReversed ? [...currentEpisodes].reverse() : currentEpisodes;\n" +
            "            let foundIndex = -1;\n" +
            "            \n" +
            "            for (let i = 0; i < displayEpisodes.length; i++) {\n" +
            "                const ep = displayEpisodes[i];\n" +
            "                // 尝试从 episodeNumber 或标题中匹配\n" +
            "                if (ep.episodeNumber && parseInt(ep.episodeNumber) === episodeNum) {\n" +
            "                    foundIndex = i;\n" +
            "                    break;\n" +
            "                }\n" +
            "                const title = ep.episodeTitle || '';\n" +
            "                const parsedNum = parseEpisodeNumber(title);\n" +
            "                if (parsedNum === episodeNum) {\n" +
            "                    foundIndex = i;\n" +
            "                    break;\n" +
            "                }\n" +
            "            }\n" +
            "            \n" +
            "            if (foundIndex >= 0) {\n" +
            "                highlightedEpisodeId = displayEpisodes[foundIndex].episodeId;\n" +
            "                renderEpisodeList(displayEpisodes, highlightedEpisodeId);\n" +
            "                document.getElementById('jumpModal').classList.remove('show');\n" +
            "                const epTitle = displayEpisodes[foundIndex].episodeTitle || '第' + displayEpisodes[foundIndex].episodeNumber + '集';\n" +
            "                showToast('已跳转到: ' + epTitle);\n" +
            "            } else {\n" +
            "                showToast('未找到第 ' + episodeNum + ' 集');\n" +
            "            }\n" +
            "        }\n" +
            "        \n" +
            "        document.getElementById('jumpConfirmBtn').addEventListener('click', performJump);\n" +
            "        \n" +
            "        // 回车跳转\n" +
            "        document.getElementById('jumpInput').addEventListener('keypress', function(e) {\n" +
            "            if (e.key === 'Enter') {\n" +
            "                performJump();\n" +
            "            }\n" +
            "        });\n" +
            "        \n" +
            "        // 弹幕设置功能\n" +
            "        const speedTexts = ['慢', '正常', '快', '最快'];\n" +
            "        let danmakuSettings = {\n" +
            "            load: true,\n" +
            "            speed: 2,\n" +
            "            size: 1.0,\n" +
            "            line: 3,\n" +
            "            alpha: 90\n" +
            "        };\n" +
            "        \n" +
            "        // 加载设置\n" +
            "        function loadSettings() {\n" +
            "            return fetch('/danmaku/api/settings')\n" +
            "                .then(res => res.json())\n" +
            "                .then(data => {\n" +
            "                    if (data.success) {\n" +
            "                        danmakuSettings = data.data;\n" +
            "                        updateSettingsUI();\n" +
            "                    }\n" +
            "                })\n" +
            "                .catch(err => console.log('加载设置失败:', err));\n" +
            "        }\n" +
            "        \n" +
            "        // 更新设置 UI\n" +
            "        function updateSettingsUI() {\n" +
            "            document.getElementById('speedSlider').value = danmakuSettings.speed;\n" +
            "            document.getElementById('sizeSlider').value = danmakuSettings.size;\n" +
            "            document.getElementById('lineSlider').value = danmakuSettings.line;\n" +
            "            document.getElementById('alphaSlider').value = danmakuSettings.alpha;\n" +
            "            \n" +
            "            document.getElementById('loadValue').textContent = danmakuSettings.load ? '开' : '关';\n" +
            "            document.getElementById('speedValue').textContent = speedTexts[danmakuSettings.speed];\n" +
            "            document.getElementById('sizeValue').textContent = danmakuSettings.size.toFixed(1) + '倍';\n" +
            "            document.getElementById('lineValue').textContent = danmakuSettings.line + '行';\n" +
            "            document.getElementById('alphaValue').textContent = danmakuSettings.alpha + '%';\n" +
            "        }\n" +
            "        \n" +
            "        // 保存设置\n" +
            "        function saveSetting(key, value) {\n" +
            "            fetch('/danmaku/api/settings?key=' + key + '&value=' + value)\n" +
            "                .then(res => res.json())\n" +
            "                .then(data => {\n" +
            "                    if (data.success) {\n" +
            "                        danmakuSettings[key] = parseFloat(value);\n" +
            "                    }\n" +
            "                })\n" +
            "                .catch(err => console.log('保存设置失败:', err));\n" +
            "        }\n" +
            "        \n" +
            "        // 打开设置弹窗\n" +
            "        document.getElementById('settingsBtn').addEventListener('click', function() {\n" +
            "            document.getElementById('settingsModal').classList.add('show');\n" +
            "        });\n" +
            "        \n" +
            "        // 关闭设置弹窗\n" +
            "        document.getElementById('closeSettingsBtn').addEventListener('click', function() {\n" +
            "            document.getElementById('settingsModal').classList.remove('show');\n" +
            "        });\n" +
            "        \n" +
            "        // 点击背景关闭\n" +
            "        document.getElementById('settingsModal').addEventListener('click', function(e) {\n" +
            "            if (e.target === this) {\n" +
            "                this.classList.remove('show');\n" +
            "            }\n" +
            "        });\n" +
            "        \n" +
            "        // 速度滑块\n" +
            "        document.getElementById('speedSlider').addEventListener('input', function() {\n" +
            "            const value = parseInt(this.value);\n" +
            "            document.getElementById('speedValue').textContent = speedTexts[value];\n" +
            "            saveSetting('speed', value);\n" +
            "        });\n" +
            "        \n" +
            "        // 大小滑块\n" +
            "        document.getElementById('sizeSlider').addEventListener('input', function() {\n" +
            "            const value = parseFloat(this.value);\n" +
            "            document.getElementById('sizeValue').textContent = value.toFixed(1) + '倍';\n" +
            "            saveSetting('size', value);\n" +
            "        });\n" +
            "        \n" +
            "        // 行数滑块\n" +
            "        document.getElementById('lineSlider').addEventListener('input', function() {\n" +
            "            const value = parseInt(this.value);\n" +
            "            document.getElementById('lineValue').textContent = value + '行';\n" +
            "            saveSetting('line', value);\n" +
            "        });\n" +
            "        \n" +
            "        // 透明度滑块\n" +
            "        document.getElementById('alphaSlider').addEventListener('input', function() {\n" +
            "            const value = parseInt(this.value);\n" +
            "            document.getElementById('alphaValue').textContent = value + '%';\n" +
            "            saveSetting('alpha', value);\n" +
            "        });\n" +
            "        \n" +
            "        // 弹幕开关按钮\n" +
            "        document.getElementById('loadToggleBtn').addEventListener('click', function() {\n" +
            "            const wasOff = !danmakuSettings.load;\n" +
            "            danmakuSettings.load = !danmakuSettings.load;\n" +
            "            document.getElementById('loadValue').textContent = danmakuSettings.load ? '开' : '关';\n" +
            "            saveSetting('load', danmakuSettings.load ? 1 : 0);\n" +
            "            // 关闭设置弹窗，让用户看到页面变化\n" +
            "            document.getElementById('settingsModal').classList.remove('show');\n" +
            "            updatePageDisplay();\n" +
            "            showToast(danmakuSettings.load ? '弹幕已开启' : '弹幕已关闭');\n" +
            "            \n" +
            "            // 如果从关闭切换到开启，且有搜索内容，自动执行搜索\n" +
            "            if (wasOff && danmakuSettings.load) {\n" +
            "                const searchInput = document.getElementById('searchInput').value.trim();\n" +
            "                if (searchInput) {\n" +
            "                    setTimeout(() => performSearch(), 300);\n" +
            "                }\n" +
            "            }\n" +
            "        });\n" +
            "        \n" +
            "        // 开启弹幕按钮\n" +
            "        document.getElementById('enableDanmuBtn').addEventListener('click', function() {\n" +
            "            danmakuSettings.load = true;\n" +
            "            saveSetting('load', 1);\n" +
            "            updatePageDisplay();\n" +
            "            showToast('弹幕已开启');\n" +
            "            // 刷新页面以加载搜索功能\n" +
            "            setTimeout(() => location.reload(), 500);\n" +
            "        });\n" +
            "        \n" +
            "        // 页面加载时加载设置\n" +
            "        loadSettings();\n" +
            "    </script>\n" +
            "</body>\n" +
            "</html>";

        NanoHTTPD.Response response = NanoHTTPD.newFixedLengthResponse(
            NanoHTTPD.Response.Status.OK,
            "text/html; charset=utf-8",
            html
        );
        addCorsHeaders(response);
        return response;
    }
}
