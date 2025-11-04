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
            if (spiders.containsKey(key)) return spiders.get(key);
            Logger.i("PyLoader: Loading Python spider - key=" + key + ", api=" + api);
            Method method = loader.getClass().getMethod("spider", Context.class, String.class);
            Spider spider = (Spider) method.invoke(loader, App.get(), api);
            spider.init(App.get(), ext);
            spiders.put(key, spider);
            Logger.i("PyLoader: Python spider loaded successfully - " + key);
            return spider;
        } catch (Throwable e) {
            Logger.e("PyLoader: Failed to load Python spider - " + key, e);
            return new SpiderNull();
        }
    }

    public Object[] proxyInvoke(Map<String, String> params) {
        try {
            if (!params.containsKey("siteKey")) return spiders.get(recent).proxyLocal(params);
            return BaseLoader.get().getSpider(params).proxyLocal(params);
        } catch (Throwable e) {
            Logger.e("PyLoader: proxyInvoke failed", e);
            return null;
        }
    }
}
