package com.github.tvbox.osc.api.config;

import android.text.TextUtils;

import com.github.tvbox.osc.App;
import com.github.tvbox.osc.R;
import com.github.tvbox.osc.api.Decoder;
import com.github.tvbox.osc.api.loader.BaseLoader;
import com.github.tvbox.osc.bean.Config;
import com.github.tvbox.osc.bean.Depot;
import com.github.tvbox.osc.bean.Parse;
import com.github.tvbox.osc.bean.Rule;
import com.github.tvbox.osc.bean.Site;
import com.github.tvbox.osc.impl.Callback;
import com.github.tvbox.osc.utils.Notify;
import com.github.tvbox.osc.utils.UrlUtil;
import com.github.catvod.crawler.Spider;
import com.github.catvod.crawler.SpiderNull;
import com.github.catvod.bean.Doh;
import com.github.catvod.net.OkHttp;
import com.github.catvod.utils.Json;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

public class VodConfig {

    private List<Doh> doh;
    private List<Rule> rules;
    private List<Site> sites;
    private List<Parse> parses;
    private List<String> flags;
    private List<String> ads;
    private boolean loadLive;
    private Config config;
    private Parse parse;
    private String wall;
    private Site home;

    private static class Loader {
        static volatile VodConfig INSTANCE = new VodConfig();
    }

    public static VodConfig get() {
        return Loader.INSTANCE;
    }

    public static int getCid() {
        return get().getConfig().getId();
    }

    public static String getUrl() {
        return get().getConfig().getUrl();
    }

    public static String getDesc() {
        return get().getConfig().getDesc();
    }

    public static int getHomeIndex() {
        return get().getSites().indexOf(get().getHome());
    }

    public static boolean hasUrl() {
        return getUrl() != null && getUrl().length() > 0;
    }

    public static boolean hasParse() {
        return !get().getParses().isEmpty();
    }

    public static void load(Config config, Callback callback) {
        get().clear().config(config).load(callback);
    }

    public VodConfig init() {
        this.wall = null;
        this.home = null;
        this.parse = null;
        this.config = Config.vod();
        this.ads = new ArrayList<>();
        this.doh = new ArrayList<>();
        this.rules = new ArrayList<>();
        this.sites = new ArrayList<>();
        this.flags = new ArrayList<>();
        this.parses = new ArrayList<>();
        this.loadLive = false;
        return this;
    }

    public VodConfig config(Config config) {
        this.config = config;
        return this;
    }

    public VodConfig clear() {
        this.wall = null;
        this.home = null;
        this.parse = null;
        this.ads.clear();
        this.doh.clear();
        this.rules.clear();
        this.sites.clear();
        this.flags.clear();
        this.parses.clear();
        this.loadLive = true;
        BaseLoader.get().clear();
        return this;
    }

    public void load(Callback callback) {
        load(callback, false);
    }

    public void load(Callback callback, boolean cache) {
        if (cache) App.execute(() -> loadConfigCache(callback));
        else App.execute(() -> loadConfig(callback));
    }

    private void loadConfig(Callback callback) {
        try {
            checkJson(Json.parse(Decoder.getJson(config.getUrl())).getAsJsonObject(), callback);
        } catch (Throwable e) {
            if (TextUtils.isEmpty(config.getUrl())) App.post(() -> callback.error(""));
            else loadCache(callback, e);
            e.printStackTrace();
        }
    }

    private void loadCache(Callback callback, Throwable e) {
        if (!TextUtils.isEmpty(config.getJson())) checkJson(Json.parse(config.getJson()).getAsJsonObject(), callback);
        else App.post(() -> callback.error(Notify.getError(R.string.error_config_get, e)));
    }

    private void loadConfigCache(Callback callback) {
        if (!TextUtils.isEmpty(config.getJson()) && config.isCache()) checkJson(Json.parse(config.getJson()).getAsJsonObject(), callback);
        else loadConfig(callback);
    }

    private void checkJson(JsonObject object, Callback callback) {
        if (object.has("msg") && callback != null) {
            App.post(() -> callback.error(object.get("msg").getAsString()));
        } else if (object.has("urls")) {
            parseDepot(object, callback);
        } else {
            parseConfig(object, callback);
        }
    }

    private void parseDepot(JsonObject object, Callback callback) {
        List<Depot> items = Depot.arrayFrom(object.getAsJsonArray("urls").toString());
        List<Config> configs = new ArrayList<>();
        for (Depot item : items) configs.add(Config.find(item, 0));
        Config.delete(config.getUrl());
        config = configs.get(0);
        loadConfig(callback);
    }

    private void parseConfig(JsonObject object, Callback callback) {
        try {
            android.util.Log.d("VodConfig", "parseConfig 开始");
            initSite(object);
            initParse(object);
            initOther(object);
            BaseLoader.get().parseJar(Json.safeString(object, "spider"));
            if (loadLive && object.has("lives")) initLive(object);
            String notice = Json.safeString(object, "notice");
            config.logo(Json.safeString(object, "logo"));
            android.util.Log.d("VodConfig", "准备调用 callback.success(notice)");
            App.post(() -> callback.success(notice));
            config.json(object.toString()).update();
            android.util.Log.d("VodConfig", "准备调用 callback.success()");
            App.post(callback::success);
            android.util.Log.d("VodConfig", "parseConfig 完成");

            // 在配置解析完成后，直接预加载 Python 站点
            android.util.Log.d("VodConfig", "parseConfig 完成后，准备预加载 Python 站点");
            preloadPythonSites();
        } catch (Throwable e) {
            e.printStackTrace();
            App.post(() -> callback.error(Notify.getError(R.string.error_config_parse, e)));
        }
    }

    private void initSite(JsonObject object) {
        if (object.has("video")) {
            initSite(object.getAsJsonObject("video"));
            return;
        }
        String spider = Json.safeString(object, "spider");
        for (JsonElement element : Json.safeListElement(object, "sites")) {
            Site site = Site.objectFrom(element);
            if (sites.contains(site)) continue;
            site.setApi(parseApi(site.getApi()));
            site.setExt(parseExt(site.getExt()));
            site.setJar(parseJar(site, spider));
            sites.add(site.trans().sync());
        }
        for (Site site : sites) {
            if (site.getKey().equals(config.getHome())) {
                setHome(site);
            }
        }
        // Eagerly load all site-specific JARs
        loadAllSiteJars();
    }

    private void loadAllSiteJars() {
        for (Site site : sites) {
            String jar = site.getJar();
            if (!jar.isEmpty() && site.getApi().startsWith("csp_")) {
                BaseLoader.get().parseJar(jar);
            }
        }
    }

    private void initLive(JsonObject object) {
        Config temp = Config.find(config, 1).save();
        boolean sync = LiveConfig.get().needSync(config.getUrl());
        if (sync) LiveConfig.get().clear().config(temp).parse(object);
    }

    private void initParse(JsonObject object) {
        for (JsonElement element : Json.safeListElement(object, "parses")) {
            Parse parse = Parse.objectFrom(element);
            if (parse.getName().equals(config.getParse()) && parse.getType() > 1) setParse(parse);
            if (!parses.contains(parse)) parses.add(parse);
        }
    }

    private void initOther(JsonObject object) {
        if (parses.size() > 0) parses.add(0, Parse.god());
        if (home == null) setHome(sites.isEmpty() ? new Site() : sites.get(0));
        if (parse == null) setParse(parses.isEmpty() ? new Parse() : parses.get(0));
        setRules(Rule.arrayFrom(object.getAsJsonArray("rules")));
        setDoh(Doh.arrayFrom(object.getAsJsonArray("doh")));
        setFlags(Json.safeListString(object, "flags"));
        setWall(Json.safeString(object, "wallpaper"));
        setAds(Json.safeListString(object, "ads"));
        setDanmuHost(Json.safeString(object, "danmu_host"));
    }

    private String parseApi(String api) {
        if (api.startsWith("file") || api.startsWith("clan") || api.startsWith("assets")) return UrlUtil.convert(api);
        return api;
    }

    private String parseExt(String ext) {
        if (ext.startsWith("file") || ext.startsWith("clan") || ext.startsWith("assets")) return UrlUtil.convert(ext);
        if (ext.startsWith("img+")) return Decoder.getExt(ext);
        return ext;
    }

    private String parseJar(Site site, String spider) {
        if (site.getJar().isEmpty() && site.getApi().startsWith("csp_")) return spider;
        return site.getJar();
    }

    public List<Doh> getDoh() {
        List<Doh> items = Doh.get(App.get());
        if (doh == null) return items;
        items.removeAll(doh);
        items.addAll(doh);
        return items;
    }

    public void setDoh(List<Doh> doh) {
        this.doh = doh;
    }

    public List<Rule> getRules() {
        return rules == null ? Collections.emptyList() : rules;
    }

    public void setRules(List<Rule> rules) {
        for (Rule rule : rules) if ("proxy".equals(rule.getName())) OkHttp.selector().addAll(rule.getHosts());
        rules.remove(Rule.create("proxy"));
        this.rules = rules;
    }

    public List<Site> getSites() {
        return sites == null ? Collections.emptyList() : sites;
    }

    public List<Parse> getParses() {
        return parses == null ? Collections.emptyList() : parses;
    }

    public List<Parse> getParses(int type) {
        List<Parse> items = new ArrayList<>();
        for (Parse item : getParses()) if (item.getType() == type) items.add(item);
        return items;
    }

    public List<Parse> getParses(int type, String flag) {
        List<Parse> items = new ArrayList<>();
        for (Parse item : getParses(type)) if (item.getExt().getFlag().isEmpty() || item.getExt().getFlag().contains(flag)) items.add(item);
        if (items.isEmpty()) items.addAll(getParses(type));
        return items;
    }

    public List<String> getFlags() {
        return flags == null ? Collections.emptyList() : flags;
    }

    private void setFlags(List<String> flags) {
        this.flags.addAll(flags);
    }

    public List<String> getAds() {
        return ads == null ? Collections.emptyList() : ads;
    }

    private void setAds(List<String> ads) {
        this.ads = ads;
    }

    public Config getConfig() {
        return config == null ? Config.vod() : config;
    }

    public Parse getParse() {
        return parse == null ? new Parse() : parse;
    }

    public Site getHome() {
        return home == null ? new Site() : home;
    }

    public String getWall() {
        return TextUtils.isEmpty(wall) ? "" : wall;
    }

    public Parse getParse(String name) {
        int index = getParses().indexOf(Parse.get(name));
        return index == -1 ? null : getParses().get(index);
    }

    public Site getSite(String key) {
        int index = getSites().indexOf(Site.get(key));
        return index == -1 ? new Site() : getSites().get(index);
    }

    public void setParse(Parse parse) {
        this.parse = parse;
        this.parse.setActivated(true);
        config.parse(parse.getName()).save();
        for (Parse item : getParses()) item.setActivated(parse);
    }

    public void setHome(Site home) {
        this.home = home;
        this.home.setActivated(true);
        config.home(home.getKey()).save();
        for (Site item : getSites()) item.setActivated(home);
    }

    private void setWall(String wall) {
        this.wall = wall;
        boolean load = !TextUtils.isEmpty(wall) && WallConfig.get().needSync(wall);
        if (load) WallConfig.get().config(Config.find(wall, config.getName(), 2).update());
    }

    private void setDanmuHost(String danmuHost) {
        if (!TextUtils.isEmpty(danmuHost)) {
            com.github.tvbox.osc.Setting.putDanmuHost(danmuHost);
        }
    }

    // 标记是否已经预加载过 Python 站点
    private static volatile boolean pythonSitesPreloaded = false;

    /**
     * 预加载所有 Python 站点的 init 方法
     * 在首页加载完成后调用，提前初始化 Python 站点，提升用户体验
     * 使用多线程并行加载，只在应用启动时加载一次
     */
    public void preloadPythonSites() {
        // 如果已经预加载过，直接返回
        if (pythonSitesPreloaded) {
            android.util.Log.d("VodConfig", "Python 站点已预加载过，跳过本次预加载");
            return;
        }

        // 标记为已预加载
        pythonSitesPreloaded = true;

        android.util.Log.d("VodConfig", "开始预加载 Python 站点，总站点数: " + sites.size());

        // 收集所有需要预加载的 Python 站点
        List<Site> pythonSites = new ArrayList<>();
        for (Site site : sites) {
            if (site.getType() == 3 && site.getApi().endsWith(".py")) {
                pythonSites.add(site);
            }
        }

        if (pythonSites.isEmpty()) {
            android.util.Log.d("VodConfig", "没有需要预加载的 Python 站点");
            PythonPreload.hide();
            return;
        }

        android.util.Log.d("VodConfig", "找到 " + pythonSites.size() + " 个 Python 站点，开始并行预加载");

        final int totalCount = pythonSites.size();
        final int token = PythonPreload.start(totalCount);
        final int parallelism = Math.max(1, Math.min(2, Runtime.getRuntime().availableProcessors()));
        final ExecutorService preloadExecutor = Executors.newFixedThreadPool(parallelism);
        final AtomicInteger successCount = new AtomicInteger(0);
        final AtomicInteger failCount = new AtomicInteger(0);
        final AtomicInteger completedCount = new AtomicInteger(0);

        for (Site site : pythonSites) {
            preloadExecutor.execute(() -> {
                try {
                    android.util.Log.d("VodConfig", "预加载 Python 站点: " + site.getName() + " (" + site.getKey() + ")");
                    Spider spider = BaseLoader.get().getSpider(site.getKey(), site.getApi(), site.getExt(), site.getJar());
                    if (spider instanceof SpiderNull) {
                        android.util.Log.e("VodConfig", "预加载失败: " + site.getName() + " - SpiderNull");
                        failCount.incrementAndGet();
                    } else {
                        android.util.Log.d("VodConfig", "成功预加载: " + site.getName());
                        successCount.incrementAndGet();
                    }
                } catch (Throwable e) {
                    android.util.Log.e("VodConfig", "预加载失败: " + site.getName() + " - " + e.getMessage());
                    failCount.incrementAndGet();
                    e.printStackTrace();
                } finally {
                    int completed = completedCount.incrementAndGet();
                    PythonPreload.progress(token, totalCount, completed, successCount.get(), failCount.get(), site.getName());
                    if (completed == totalCount) {
                        android.util.Log.d("VodConfig", "Python 站点预加载完成，成功: " + successCount.get() + "，失败: " + failCount.get());
                        PythonPreload.finish(token, totalCount, completed, successCount.get(), failCount.get());
                        if (successCount.get() == 0) {
                            App.post(() -> Notify.show(R.string.python_preload_all_failed));
                        } else if (failCount.get() > 0) {
                            App.post(() -> Notify.show(App.get().getString(R.string.python_preload_partial_failed, failCount.get(), totalCount)));
                        }
                        preloadExecutor.shutdown();
                    }
                }
            });
        }
    }
}
