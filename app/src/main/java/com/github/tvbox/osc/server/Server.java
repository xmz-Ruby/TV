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
}
