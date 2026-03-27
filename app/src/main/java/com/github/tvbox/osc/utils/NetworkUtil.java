package com.github.tvbox.osc.utils;

import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;

import com.github.tvbox.osc.App;
import com.github.tvbox.osc.Setting;

public class NetworkUtil {

    private NetworkUtil() {
    }

    public static int getConfigLoadMode() {
        ConnectivityManager manager = App.get().getSystemService(ConnectivityManager.class);
        if (manager == null) return Setting.CONFIG_LOAD_MODE_WIFI;
        Network network = manager.getActiveNetwork();
        if (network == null) return Setting.CONFIG_LOAD_MODE_WIFI;
        NetworkCapabilities capabilities = manager.getNetworkCapabilities(network);
        if (capabilities == null) return Setting.CONFIG_LOAD_MODE_WIFI;
        return capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ? Setting.CONFIG_LOAD_MODE_MOBILE : Setting.CONFIG_LOAD_MODE_WIFI;
    }
}
