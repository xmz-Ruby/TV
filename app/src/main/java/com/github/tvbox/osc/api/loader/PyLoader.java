package com.github.tvbox.osc.api.loader;

import android.content.Context;

import com.github.tvbox.osc.App;
import com.github.catvod.crawler.Spider;
import com.github.catvod.crawler.SpiderNull;
import com.github.catvod.utils.Logger;
import com.github.catvod.utils.Util;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.ConcurrentHashMap;

public class PyLoader {

    private final ConcurrentHashMap<String, Spider> spiders;
    private final ConcurrentHashMap<String, Object> loadingLocks;
    private final AtomicInteger generation;
    private Object loader;
    private Method spiderMethod;
    private Method warmupMethod;
    private String recent;

    public PyLoader() {
        this.spiders = new ConcurrentHashMap<>();
        this.loadingLocks = new ConcurrentHashMap<>();
        this.generation = new AtomicInteger(0);
        init();
    }

    private void init() {
        try {
            Class<?> loaderClass = Class.forName("com.undcover.freedom.pyramid.Loader");
            loader = loaderClass.newInstance();
            warmupMethod = loaderClass.getMethod("warmup", Context.class);
            spiderMethod = loaderClass.getMethod("spider", Context.class, String.class, String.class);
            Logger.i("PyLoader: Pyramid loader initialized successfully");
        } catch (ClassNotFoundException e) {
            Logger.e("PyLoader: Loader class not found - pyramid module not included?", e);
        } catch (Throwable e) {
            Logger.e("PyLoader: Failed to initialize pyramid loader", e);
        }
    }

    public void clear() {
        generation.incrementAndGet();
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
        int currentGeneration = generation.get();
        String compositeKey = api + "|" + ext;
        String moduleKey = Util.md5(key + "|" + compositeKey);
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
                if (isStale(currentGeneration)) {
                    Logger.w("PyLoader: Loading cancelled before init - key=" + key);
                    return new SpiderNull();
                }
                Logger.i("PyLoader: Loading Python spider - key=" + key + ", api=" + api + ", extHash=" + ext.hashCode());
                Spider spider = (Spider) spiderMethod.invoke(loader, App.get(), api, moduleKey);
                spider.init(App.get(), ext);
                if (isStale(currentGeneration)) {
                    Logger.w("PyLoader: Discard stale Python spider - key=" + key);
                    destroyQuietly(spider);
                    return new SpiderNull();
                }
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

    private boolean isStale(int currentGeneration) {
        return currentGeneration != generation.get() || Thread.currentThread().isInterrupted();
    }

    private void destroyQuietly(Spider spider) {
        if (spider == null) return;
        try {
            spider.destroy();
        } catch (Throwable e) {
            Logger.w("PyLoader: destroy stale spider failed", e);
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
