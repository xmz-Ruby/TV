package com.github.tvbox.osc.server;

import com.github.tvbox.osc.player.Players;
import com.github.catvod.Proxy;
import com.github.catvod.utils.Util;

public class Server {

    private Players player;
    private Nano nano;
    private int port;

    // 当前播放信息
    private volatile String currentTitle = "";
    private volatile String currentEpisode = "";
    private volatile boolean isCasting = false;
    private volatile String castUrl = "";
    private volatile long castPosition = 0;
    private volatile long castDuration = 0;

    // 投屏代理 token 认证
    private volatile String castProxyToken = null;

    private static class Loader {
        static volatile Server INSTANCE = new Server();
    }

    public static Server get() {
        return Loader.INSTANCE;
    }

    public Server() {
        this.port = 9978;
    }

    public int getPort() {
        return port;
    }

    public Players getPlayer() {
        return player;
    }

    public void setPlayer(Players player) {
        this.player = player;
    }

    public String getAddress() {
        return getAddress(false);
    }

    public String getAddress(int tab) {
        return getAddress(false) + "?tab=" + tab;
    }

    public String getAddress(String path) {
        return getAddress(true) + path;
    }

    public String getAddress(boolean local) {
        return "http://" + (local ? "127.0.0.1" : Util.getIp()) + ":" + getPort();
    }

    public void start() {
        if (nano != null) return;
        do {
            try {
                nano = new Nano(port);
                Proxy.set(port);
                nano.start();
                break;
            } catch (Exception e) {
                ++port;
                nano.stop();
                nano = null;
            }
        } while (port < 9999);
    }

    public void stop() {
        if (nano != null) nano.stop();
        nano = null;
    }

    /**
     * 设置当前播放信息
     */
    public void setCurrentMedia(String title, String episode) {
        this.currentTitle = title != null ? title : "";
        this.currentEpisode = episode != null ? episode : "";
    }

    /**
     * 获取当前播放的剧名
     */
    public String getCurrentTitle() {
        return currentTitle;
    }

    /**
     * 获取当前播放的集名
     */
    public String getCurrentEpisode() {
        return currentEpisode;
    }

    /**
     * 设置投屏状态
     */
    public void setCasting(boolean casting, String url) {
        this.isCasting = casting;
        this.castUrl = url != null ? url : "";
        if (!casting) {
            this.castPosition = 0;
            this.castDuration = 0;
            // 停止投屏时清除 token
            this.castProxyToken = null;
            android.util.Log.d("Server", "Cast stopped, token cleared");
        } else {
            // 开始投屏时不重新生成 token，使用已有的 token
            // token 已经在 CastDialog 构造函数中生成
            android.util.Log.d("Server", "Cast started, using existing token: " + castProxyToken);
        }
    }

    /**
     * 生成投屏代理 token（使用时间戳 + 随机数）
     */
    private String generateCastProxyToken() {
        return System.currentTimeMillis() + "_" + (int)(Math.random() * 1000000);
    }

    /**
     * 提前生成并设置投屏代理 token（用于在构建 CastVideo 之前生成 token）
     */
    public void generateAndSetCastProxyToken() {
        this.castProxyToken = generateCastProxyToken();
        android.util.Log.d("Server", "Cast proxy token generated: " + castProxyToken);
    }

    /**
     * 获取当前投屏代理 token
     */
    public String getCastProxyToken() {
        return castProxyToken;
    }

    /**
     * 验证投屏代理 token
     */
    public boolean validateCastProxyToken(String token) {
        if (castProxyToken == null || token == null) {
            return false;
        }
        return castProxyToken.equals(token);
    }

    /**
     * 更新投屏播放进度
     */
    public void updateCastProgress(long position, long duration) {
        this.castPosition = position;
        this.castDuration = duration;
    }

    /**
     * 是否正在投屏
     */
    public boolean isCasting() {
        return isCasting;
    }

    /**
     * 获取投屏 URL
     */
    public String getCastUrl() {
        return castUrl;
    }

    /**
     * 获取投屏播放位置
     */
    public long getCastPosition() {
        return castPosition;
    }

    /**
     * 获取投屏总时长
     */
    public long getCastDuration() {
        return castDuration;
    }

    /**
     * 检查投屏代理是否活跃（投屏端是否正在播放）
     */
    public boolean isCastProxyActive() {
        if (nano == null) return false;
        com.github.tvbox.osc.server.process.CastProxy castProxy = nano.getCastProxy();
        return castProxy != null && castProxy.isActive();
    }

    /**
     * 获取投屏代理的活跃连接数
     */
    public int getCastProxyActiveConnections() {
        if (nano == null) return 0;
        com.github.tvbox.osc.server.process.CastProxy castProxy = nano.getCastProxy();
        return castProxy != null ? castProxy.getActiveConnections() : 0;
    }

    /**
     * 获取投屏代理最后一次请求时间
     */
    public long getCastProxyLastRequestTime() {
        if (nano == null) return 0;
        com.github.tvbox.osc.server.process.CastProxy castProxy = nano.getCastProxy();
        return castProxy != null ? castProxy.getLastRequestTime() : 0;
    }
}
