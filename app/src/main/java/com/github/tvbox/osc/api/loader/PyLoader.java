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
    private final ConcurrentHashMap<String, Object> loadingLocks;
    private Object loader;
    private Method spiderMethod;
    private Method warmupMethod;
    private String recent;

    public PyLoader() {
        this.spiders = new ConcurrentHashMap<>();
        this.loadingLocks = new ConcurrentHashMap<>();
        init();
    }

    private void init() {
        try {
            Class<?> loaderClass = Class.forName("com.undcover.freedom.pyramid.Loader");
            loader = loaderClass.newInstance();
            warmupMethod = loaderClass.getMethod("warmup", Context.class);
            spiderMethod = loaderClass.getMethod("spider", Context.class, String.class);
            Logger.i("PyLoader: Pyramid loader initialized successfully");
        } catch (ClassNotFoundException e) {
            Logger.e("PyLoader: Loader class not found - pyramid module not included?", e);
        } catch (Throwable e) {
            Logger.e("PyLoader: Failed to initialize pyramid loader", e);
        }
    }

    public void clear() {
        for (Spider spider : spiders.values()) {
            final Spider currentSpider = spider;
            App.execute(new Runnable() {
                @Override
                public void run() {
                    currentSpider.destroy();
                }
            });
        }
        spiders.clear();
        loadingLocks.clear();
    }

    public void setRecent(String recent) {
        this.recent = recent;
    }

    public void warmup() {
        try {
            if (loader == null || warmupMethod == null) {
                Logger.w("PyLoader: warmup skipped because loader not initialized");
                return;
            }
            warmupMethod.invoke(loader, App.get());
        } catch (Throwable e) {
            Logger.e("PyLoader: warmup failed", e);
        }
    }

    public Spider getSpider(String key, String api, String ext) {
        String compositeKey = api + "|" + ext;
        try {
            if (loader == null) {
                Logger.e("PyLoader: Loader not initialized");
                return new SpiderNull();
            }
            if (spiderMethod == null) {
                Logger.e("PyLoader: spider method not initialized");
                return new SpiderNull();
            }
            Spider cachedSpider = spiders.get(compositeKey);
            if (cachedSpider != null) {
                Logger.d("PyLoader: Reusing cached spider - key=" + key + ", compositeKey=" + compositeKey.hashCode());
                return cachedSpider;
            }
            Object loadingLock = getLoadingLock(compositeKey);
            synchronized (loadingLock) {
                cachedSpider = spiders.get(compositeKey);
                if (cachedSpider != null) {
                    Logger.d("PyLoader: Reusing cached spider after lock - key=" + key + ", compositeKey=" + compositeKey.hashCode());
                    return cachedSpider;
                }
                Logger.i("PyLoader: Loading Python spider - key=" + key + ", api=" + api + ", extHash=" + ext.hashCode());
                Spider spider = (Spider) spiderMethod.invoke(loader, App.get(), api);
                spider.init(App.get(), ext);
                spiders.put(compositeKey, spider);
                Logger.i("PyLoader: Python spider loaded successfully - key=" + key + ", compositeKey=" + compositeKey.hashCode());
                return spider;
            }
        } catch (Throwable e) {
            Logger.e("PyLoader: Failed to load Python spider - " + key, e);
            return new SpiderNull();
        } finally {
            loadingLocks.remove(compositeKey);
        }
    }

    private Object getLoadingLock(String compositeKey) {
        Object existingLock = loadingLocks.get(compositeKey);
        if (existingLock != null) return existingLock;
        Object newLock = new Object();
        Object racingLock = loadingLocks.putIfAbsent(compositeKey, newLock);
        return racingLock != null ? racingLock : newLock;
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
