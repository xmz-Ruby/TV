package com.github.tvbox.osc.api.config;

import android.app.ActivityManager;
import android.os.Build;
import android.text.TextUtils;

import com.github.tvbox.osc.App;
import com.github.tvbox.osc.R;
import com.github.tvbox.osc.Setting;
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
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

public class VodConfig {

    private static final List<String> MOBILE_SKIP_KEYWORDS = Arrays.asList("4K", "超清");

    private List<Doh> doh;
    private List<Rule> rules;
    private List<Site> sites;
    private List<Parse> parses;
    private List<String> flags;
    private List<String> ads;
    private boolean loadLive;
    private boolean hasShortDramaSites;
    private boolean shortDramaMode;
    private Config config;
    private Parse parse;
    private Site home;
    private final AtomicInteger loadGeneration = new AtomicInteger(0);
    private volatile ExecutorService preloadExecutor;

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
        get().startLoad(config, callback);
    }

    public static void switchContentMode(Callback callback) {
        get().startContentModeSwitch(callback);
    }

    public VodConfig init() {
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
        this.hasShortDramaSites = false;
        this.shortDramaMode = false;
        return this;
    }

    public VodConfig config(Config config) {
        this.config = config;
        return this;
    }

    public VodConfig clear() {
        loadGeneration.incrementAndGet();
        return clearState();
    }

    private VodConfig clearState() {
        resetState(true, true);
        return this;
    }

    private void resetState(boolean clearLoader, boolean resetLive) {
        cancelPythonPreload();
        this.home = null;
        this.parse = null;
        this.ads.clear();
        this.doh.clear();
        this.rules.clear();
        this.sites.clear();
        this.flags.clear();
        this.parses.clear();
        this.loadLive = resetLive;
        this.hasShortDramaSites = false;
        this.shortDramaMode = false;
        pythonSitesPreloaded = false;
        if (clearLoader) BaseLoader.get().clear();
    }

    private void startLoad(Config config, Callback callback) {
        int generation;
        synchronized (this) {
            generation = loadGeneration.incrementAndGet();
            clearState();
            config(config);
        }
        load(callback, false, generation, config);
    }

    private void startContentModeSwitch(Callback callback) {
        Config currentConfig;
        int generation;
        synchronized (this) {
            currentConfig = getConfig();
            generation = loadGeneration.incrementAndGet();
            resetState(false, false);
            config(currentConfig);
        }
        if (TextUtils.isEmpty(currentConfig.getJson())) {
            load(callback, true, generation, currentConfig);
            return;
        }
        App.execute(() -> {
            try {
                if (isStale(generation)) return;
                checkJson(Json.parse(currentConfig.getJson()).getAsJsonObject(), callback, generation, currentConfig, false, false);
            } catch (Throwable e) {
                if (isStale(generation)) return;
                App.post(() -> {
                    if (!isStale(generation)) callback.error(Notify.getError(R.string.error_config_parse, e));
                });
            }
        });
    }

    public void load(Callback callback) {
        load(callback, false);
    }

    public void load(Callback callback, boolean cache) {
        int generation = loadGeneration.get();
        Config currentConfig = getConfig();
        load(callback, cache, generation, currentConfig);
    }

    private void load(Callback callback, boolean cache, int generation, Config targetConfig) {
        if (cache) App.execute(() -> loadConfigCache(callback, generation, targetConfig));
        else App.execute(() -> loadConfig(callback, generation, targetConfig));
    }

    private void loadConfig(Callback callback, int generation, Config targetConfig) {
        try {
            if (isStale(generation)) return;
            checkJson(Json.parse(Decoder.getJson(targetConfig.getUrl())).getAsJsonObject(), callback, generation, targetConfig);
        } catch (Throwable e) {
            if (isStale(generation)) return;
            if (TextUtils.isEmpty(targetConfig.getUrl())) {
                App.post(() -> {
                    if (!isStale(generation)) callback.error("");
                });
            } else {
                loadCache(callback, e, generation, targetConfig);
            }
            e.printStackTrace();
        }
    }

    private void loadCache(Callback callback, Throwable e, int generation, Config targetConfig) {
        if (isStale(generation)) return;
        if (!TextUtils.isEmpty(targetConfig.getJson())) {
            checkJson(Json.parse(targetConfig.getJson()).getAsJsonObject(), callback, generation, targetConfig);
        } else {
            App.post(() -> {
                if (!isStale(generation)) callback.error(Notify.getError(R.string.error_config_get, e));
            });
        }
    }

    private void loadConfigCache(Callback callback, int generation, Config targetConfig) {
        if (isStale(generation)) return;
        if (!TextUtils.isEmpty(targetConfig.getJson()) && targetConfig.isCache()) {
            checkJson(Json.parse(targetConfig.getJson()).getAsJsonObject(), callback, generation, targetConfig);
        } else {
            loadConfig(callback, generation, targetConfig);
        }
    }

    private void checkJson(JsonObject object, Callback callback, int generation, Config targetConfig) {
        checkJson(object, callback, generation, targetConfig, true, true);
    }

    private void checkJson(JsonObject object, Callback callback, int generation, Config targetConfig, boolean preloadPython, boolean eagerLoadSpider) {
        if (isStale(generation)) return;
        if (object.has("msg") && callback != null) {
            App.post(() -> {
                if (!isStale(generation)) callback.error(object.get("msg").getAsString());
            });
        } else if (object.has("urls")) {
            parseDepot(object, callback, generation, targetConfig);
        } else {
            parseConfig(object, callback, generation, targetConfig, preloadPython, eagerLoadSpider);
        }
    }

    private void parseDepot(JsonObject object, Callback callback, int generation, Config targetConfig) {
        if (isStale(generation)) return;
        List<Depot> items = Depot.arrayFrom(object.getAsJsonArray("urls").toString());
        List<Config> configs = new ArrayList<>();
        for (Depot item : items) configs.add(Config.find(item, 0));
        Config.delete(targetConfig.getUrl());
        if (configs.isEmpty()) return;
        synchronized (this) {
            if (isStale(generation)) return;
            config = configs.get(0);
        }
        loadConfig(callback, generation, configs.get(0));
    }

    private void parseConfig(JsonObject object, Callback callback, int generation, Config targetConfig) {
        parseConfig(object, callback, generation, targetConfig, true, true);
    }

    private void parseConfig(JsonObject object, Callback callback, int generation, Config targetConfig, boolean preloadPython, boolean eagerLoadSpider) {
        try {
            if (isStale(generation)) return;
            android.util.Log.d("VodConfig", "parseConfig 开始");
            initSite(object, eagerLoadSpider);
            initParse(object);
            initOther(object);
            if (eagerLoadSpider) BaseLoader.get().parseJar(Json.safeString(object, "spider"));
            if (loadLive && object.has("lives")) initLive(object);
            String notice = Json.safeString(object, "notice");
            if (isStale(generation)) return;
            targetConfig.logo(Json.safeString(object, "logo"));
            android.util.Log.d("VodConfig", "准备调用 callback.success(notice)");
            App.post(() -> {
                if (!isStale(generation)) callback.success(notice);
            });
            targetConfig.json(object.toString()).update();
            android.util.Log.d("VodConfig", "准备调用 callback.success()");
            App.post(() -> {
                if (!isStale(generation)) callback.success();
            });
            android.util.Log.d("VodConfig", "parseConfig 完成");

            if (preloadPython) {
                android.util.Log.d("VodConfig", "parseConfig 完成后，准备预加载 Python 站点");
                preloadPythonSites(generation);
            } else {
                android.util.Log.d("VodConfig", "parseConfig 完成后，跳过 Python 预加载（内容模式切换）");
            }
        } catch (Throwable e) {
            e.printStackTrace();
            App.post(() -> {
                if (!isStale(generation)) callback.error(Notify.getError(R.string.error_config_parse, e));
            });
        }
    }

    private void initSite(JsonObject object, boolean eagerLoadSpider) {
        JsonObject video = object.has("video") ? object.getAsJsonObject("video") : object;
        String spider = Json.safeString(video, "spider");
        if (TextUtils.isEmpty(spider)) spider = Json.safeString(object, "spider");
        hasShortDramaSites = !Json.safeListElement(video, "sites_duanju").isEmpty();
        shortDramaMode = hasShortDramaSites && Setting.isVodContentShortDramaMode();
        String key = shortDramaMode ? "sites_duanju" : "sites";
        for (JsonElement element : Json.safeListElement(video, key)) {
            Site site = Site.objectFrom(element);
            if (skipSite(site)) continue;
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
        if (eagerLoadSpider) loadAllSiteJars();
    }

    public boolean hasShortDramaSites() {
        return hasShortDramaSites;
    }

    public boolean isShortDramaMode() {
        return shortDramaMode;
    }

    public int getVodContentMode() {
        return isShortDramaMode() ? Setting.VOD_CONTENT_MODE_SHORT_DRAMA : Setting.VOD_CONTENT_MODE_FILM;
    }

    private boolean skipSite(Site site) {
        if (!Setting.isConfigLoadMobileMode()) return false;
        for (String keyword : MOBILE_SKIP_KEYWORDS) {
            if (containsKeyword(site.getName(), keyword) || containsKeyword(site.getKey(), keyword)) {
                android.util.Log.d("VodConfig", "流量模式跳过站点: " + site.getName() + " (" + site.getKey() + ")");
                return true;
            }
        }
        return false;
    }

    private boolean containsKeyword(String value, String keyword) {
        if (TextUtils.isEmpty(value)) return false;
        return value.toUpperCase(Locale.US).contains(keyword.toUpperCase(Locale.US));
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

    private void setDanmuHost(String danmuHost) {
        if (!TextUtils.isEmpty(danmuHost)) {
            com.github.tvbox.osc.Setting.putDanmuHost(danmuHost);
        }
    }

    // 标记当前配置加载周期内是否已经预加载过 Python 站点
    private static volatile boolean pythonSitesPreloaded = false;

    private boolean isStale(int generation) {
        return loadGeneration.get() != generation || Thread.currentThread().isInterrupted();
    }

    private void cancelPythonPreload() {
        ExecutorService executor;
        synchronized (this) {
            executor = preloadExecutor;
            preloadExecutor = null;
            pythonSitesPreloaded = false;
        }
        if (executor != null) executor.shutdownNow();
        PythonPreload.cancel();
    }

    private int getPythonPreloadParallelism(int totalCount) {
        boolean isArmeabiV7aOnly = Build.SUPPORTED_64_BIT_ABIS.length == 0 && Arrays.asList(Build.SUPPORTED_ABIS).contains("armeabi-v7a");
        int target = isArmeabiV7aOnly ? 6 : 8;
        return Math.max(1, Math.min(totalCount, target));
    }

    private String getPythonPreloadDeviceProfile() {
        ActivityManager activityManager = App.get().getSystemService(ActivityManager.class);
        int memoryClass = activityManager != null ? activityManager.getMemoryClass() : -1;
        int largeMemoryClass = activityManager != null ? activityManager.getLargeMemoryClass() : -1;
        long maxMemoryMb = Runtime.getRuntime().maxMemory() / 1024 / 1024;
        return "brand=" + Build.BRAND
                + ", model=" + Build.MODEL
                + ", sdk=" + Build.VERSION.SDK_INT
                + ", abis=" + Arrays.toString(Build.SUPPORTED_ABIS)
                + ", cpu=" + Runtime.getRuntime().availableProcessors()
                + ", memoryClass=" + memoryClass
                + ", largeMemoryClass=" + largeMemoryClass
                + ", maxHeapMb=" + maxMemoryMb;
    }

    private String formatPythonPreloadElapsed(long elapsedMs) {
        return String.format(Locale.US, "%.3fs", elapsedMs / 1000f);
    }

    /**
     * 预加载所有 Python 站点的 init 方法
     * 在首页加载完成后调用，提前初始化 Python 站点，提升用户体验
     * 使用多线程并行加载，每次配置重新加载后触发一次
     */
    public void preloadPythonSites(int generation) {
        if (isStale(generation)) return;
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
        final int parallelism = getPythonPreloadParallelism(totalCount);
        final long preloadStartedAt = System.currentTimeMillis();
        android.util.Log.i("VodConfig", "Python 预加载开始: total=" + totalCount + ", parallelism=" + parallelism + ", " + getPythonPreloadDeviceProfile());
        BaseLoader.get().warmupPython();
        final ExecutorService executor = Executors.newFixedThreadPool(parallelism);
        synchronized (this) {
            if (isStale(generation)) {
                executor.shutdownNow();
                PythonPreload.cancel();
                return;
            }
            preloadExecutor = executor;
        }
        final AtomicInteger successCount = new AtomicInteger(0);
        final AtomicInteger failCount = new AtomicInteger(0);
        final AtomicInteger completedCount = new AtomicInteger(0);

        for (Site site : pythonSites) {
            executor.execute(() -> {
                long siteStartedAt = System.currentTimeMillis();
                boolean success = false;
                String failureType = "";
                try {
                    if (isStale(generation)) return;
                    android.util.Log.d("VodConfig", "预加载 Python 站点: " + site.getName() + " (" + site.getKey() + ")");
                    Spider spider = BaseLoader.get().getSpider(site.getKey(), site.getApi(), site.getExt(), site.getJar());
                    if (isStale(generation)) return;
                    if (spider instanceof SpiderNull) {
                        android.util.Log.e("VodConfig", "预加载失败: " + site.getName() + " - SpiderNull");
                        failCount.incrementAndGet();
                        failureType = "SpiderNull";
                    } else {
                        android.util.Log.d("VodConfig", "成功预加载: " + site.getName());
                        successCount.incrementAndGet();
                        success = true;
                    }
                } catch (Throwable e) {
                    android.util.Log.e("VodConfig", "预加载失败: " + site.getName() + " - " + e.getMessage());
                    failCount.incrementAndGet();
                    failureType = e.getClass().getSimpleName();
                    e.printStackTrace();
                } finally {
                    long siteElapsed = System.currentTimeMillis() - siteStartedAt;
                    android.util.Log.i("VodConfig", "Python 预加载站点完成: site=" + site.getName()
                            + ", success=" + success
                            + ", elapsed=" + formatPythonPreloadElapsed(siteElapsed)
                            + ", thread=" + Thread.currentThread().getName()
                            + (failureType.isEmpty() ? "" : ", failure=" + failureType));
                    if (isStale(generation)) return;
                    int completed = completedCount.incrementAndGet();
                    PythonPreload.progress(token, totalCount, completed, successCount.get(), failCount.get(), site.getName());
                    if (completed == totalCount) {
                        long totalElapsed = System.currentTimeMillis() - preloadStartedAt;
                        android.util.Log.i("VodConfig", "Python 预加载完成: success=" + successCount.get()
                                + ", fail=" + failCount.get()
                                + ", total=" + totalCount
                                + ", parallelism=" + parallelism
                                + ", elapsed=" + formatPythonPreloadElapsed(totalElapsed)
                                + ", " + getPythonPreloadDeviceProfile());
                        PythonPreload.finish(token, totalCount, completed, successCount.get(), failCount.get());
                        if (successCount.get() == 0) {
                            App.post(() -> {
                                if (!isStale(generation)) Notify.show(R.string.python_preload_all_failed);
                            });
                        } else if (failCount.get() > 0) {
                            App.post(() -> {
                                if (!isStale(generation)) Notify.show(App.get().getString(R.string.python_preload_partial_failed, failCount.get(), totalCount));
                            });
                        }
                        synchronized (VodConfig.this) {
                            if (preloadExecutor == executor) preloadExecutor = null;
                        }
                        executor.shutdown();
                    }
                }
            });
        }
    }
}
