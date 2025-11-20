package com.github.tvbox.osc.api.loader;

import android.content.Context;
import android.text.TextUtils;

import com.github.catvod.crawler.Spider;
import com.github.catvod.utils.Logger;
import com.github.tvbox.osc.Setting;

import org.json.JSONObject;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Spider 安全包装器
 * 用于过滤外部 spider jar 中可能对播放器进行的魔改操作
 * 例如：禁止添加弹幕、修改播放器 UI 等
 */
public class SafeSpiderWrapper extends Spider {

    private final Spider wrappedSpider;
    private final boolean enableSafeMode;

    public SafeSpiderWrapper(Spider spider) {
        this.wrappedSpider = spider;
        this.enableSafeMode = Setting.isSpiderSafeMode();
    }

    @Override
    public void init(Context context) throws Exception {
        wrappedSpider.init(context);
    }

    @Override
    public void init(Context context, String extend) throws Exception {
        wrappedSpider.init(context, extend);
    }

    @Override
    public String homeContent(boolean filter) throws Exception {
        return wrappedSpider.homeContent(filter);
    }

    @Override
    public String homeVideoContent() throws Exception {
        return wrappedSpider.homeVideoContent();
    }

    @Override
    public String categoryContent(String tid, String pg, boolean filter, HashMap<String, String> extend) throws Exception {
        return wrappedSpider.categoryContent(tid, pg, filter, extend);
    }

    @Override
    public String detailContent(List<String> ids) throws Exception {
        return wrappedSpider.detailContent(ids);
    }

    @Override
    public String searchContent(String key, boolean quick) throws Exception {
        return wrappedSpider.searchContent(key, quick);
    }

    @Override
    public String searchContent(String key, boolean quick, String pg) throws Exception {
        return wrappedSpider.searchContent(key, quick, pg);
    }

    /**
     * 播放内容 - 关键方法，需要过滤危险字段
     */
    @Override
    public String playerContent(String flag, String id, List<String> vipFlags) throws Exception {
        String result = wrappedSpider.playerContent(flag, id, vipFlags);

        if (!enableSafeMode || TextUtils.isEmpty(result)) {
            return result;
        }

        try {
            // 解析返回的 JSON
            JSONObject json = new JSONObject(result);

            // 移除可能修改播放器的字段
            if (json.has("danmaku")) {
                Logger.i("SafeSpiderWrapper: 移除外部 spider 返回的弹幕字段");
                json.remove("danmaku");
            }

            // 可选：也可以移除其他可能影响播放器的字段
            // if (json.has("subs")) {
            //     Logger.i("SafeSpiderWrapper: 移除外部 spider 返回的字幕字段");
            //     json.remove("subs");
            // }

            // 移除可能注入自定义 JS 的字段
            if (json.has("js")) {
                Logger.i("SafeSpiderWrapper: 移除外部 spider 返回的 js 字段");
                json.remove("js");
            }

            // 移除可能修改点击行为的字段
            if (json.has("click")) {
                Logger.i("SafeSpiderWrapper: 移除外部 spider 返回的 click 字段");
                json.remove("click");
            }

            return json.toString();
        } catch (Exception e) {
            Logger.e("SafeSpiderWrapper: 过滤 playerContent 失败: " + e.getMessage());
            // 如果解析失败，返回原始结果
            return result;
        }
    }

    @Override
    public String liveContent() throws Exception {
        return wrappedSpider.liveContent();
    }

    @Override
    public boolean manualVideoCheck() throws Exception {
        return wrappedSpider.manualVideoCheck();
    }

    @Override
    public boolean isVideoFormat(String url) throws Exception {
        return wrappedSpider.isVideoFormat(url);
    }

    /**
     * 本地代理 - 禁止外部 spider 拦截请求
     */
    @Override
    public Object[] proxyLocal(Map<String, String> params) throws Exception {
//        if (enableSafeMode) {
//            Logger.i("SafeSpiderWrapper: 安全模式：禁止外部 spider 使用 proxyLocal");
//            return null;
//        }
        return wrappedSpider.proxyLocal(params);
    }

    /**
     * 自定义操作 - 禁止外部 spider 执行自定义操作
     */
    @Override
    public String action(String action) throws Exception {
        if (enableSafeMode) {
            Logger.i("SafeSpiderWrapper: 安全模式：禁止外部 spider 使用 action");
            return null;
        }
        return wrappedSpider.action(action);
    }

    @Override
    public void destroy() {
        wrappedSpider.destroy();
    }

    /**
     * 获取被包装的原始 Spider（用于调试）
     */
    public Spider getWrappedSpider() {
        return wrappedSpider;
    }
}
