package com.github.tvbox.osc.api.loader;

import android.content.Context;

import com.github.tvbox.osc.App;
import com.github.catvod.crawler.Spider;
import com.github.catvod.crawler.SpiderNull;
import com.github.catvod.utils.Logger;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class PyLoader {

    private final ConcurrentHashMap<String, Spider> spiders;
    private Object loader;
    private String recent;

    public PyLoader() {
        this.spiders = new ConcurrentHashMap<>();
        init();
    }

    private void init() {
        try {
            Class<?> loaderClass = Class.forName("com.undcover.freedom.pyramid.Loader");
            loader = loaderClass.newInstance();
            Logger.i("PyLoader: Pyramid loader initialized successfully");
        } catch (ClassNotFoundException e) {
            Logger.e("PyLoader: Loader class not found - pyramid module not included?", e);
        } catch (Throwable e) {
            Logger.e("PyLoader: Failed to initialize pyramid loader", e);
        }
    }

    public void clear() {
        for (Spider spider : spiders.values()) App.execute(spider::destroy);
        spiders.clear();
    }

    public void setRecent(String recent) {
        this.recent = recent;
    }

    public Spider getSpider(String key, String api, String ext) {
        try {
            if (loader == null) {
                Logger.e("PyLoader: Loader not initialized");
                return new SpiderNull();
            }
            // 使用 api + ext 作为组合键，确保相同脚本不同配置独立
            // 例如：多个站点使用 ./python/emby.py，但 ext 配置不同，需要独立实例
            String compositeKey = api + "|" + ext;
            if (spiders.containsKey(compositeKey)) {
                Spider spider = spiders.get(compositeKey);
                Logger.d("PyLoader: Reusing cached spider - key=" + key + ", compositeKey=" + compositeKey.hashCode());
                return spider;
            }
            Logger.i("PyLoader: Loading Python spider - key=" + key + ", api=" + api + ", extHash=" + ext.hashCode());
            Method method = loader.getClass().getMethod("spider", Context.class, String.class);
            Spider spider = (Spider) method.invoke(loader, App.get(), api);
            spider.init(App.get(), ext);
            spiders.put(compositeKey, spider);
            Logger.i("PyLoader: Python spider loaded successfully - key=" + key + ", compositeKey=" + compositeKey.hashCode());
            return spider;
        } catch (Throwable e) {
            Logger.e("PyLoader: Failed to load Python spider - " + key, e);
            return new SpiderNull();
        }
    }

    public Object[] proxyInvoke(Map<String, String> params) {
        try {
            if (!params.containsKey("siteKey")) {
                // 如果没有指定 siteKey，尝试从 params 中构造 compositeKey 来查找 spider
                String api = params.get("api");
                String ext = params.get("ext");

                if (api != null && ext != null) {
                    String compositeKey = api + "|" + ext;
                    Spider targetSpider = spiders.get(compositeKey);
                    if (targetSpider != null) {
                        Logger.d("PyLoader: proxyInvoke using compositeKey, hash=" + compositeKey.hashCode());
                        return targetSpider.proxyLocal(params);
                    }
                }

                // 回退到使用 recent 变量（向后兼容）
                if (recent != null && !recent.isEmpty() && !spiders.isEmpty()) {
                    Spider targetSpider = spiders.values().iterator().next();
                    if (targetSpider != null) {
                        Logger.d("PyLoader: proxyInvoke using fallback spider, recent=" + recent);
                        return targetSpider.proxyLocal(params);
                    }
                }
                Logger.w("PyLoader: proxyInvoke called without siteKey and no valid spider found");
                return null;
            }
            // 使用指定的 siteKey 获取 spider
            return BaseLoader.get().getSpider(params).proxyLocal(params);
        } catch (Throwable e) {
            Logger.e("PyLoader: proxyInvoke failed", e);
            return null;
        }
    }
}
