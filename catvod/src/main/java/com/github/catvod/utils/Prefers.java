package com.github.catvod.utils;

import android.content.SharedPreferences;

import androidx.preference.PreferenceManager;

import com.github.catvod.Init;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.ToNumberPolicy;
import com.google.gson.internal.LazilyParsedNumber;
import com.google.gson.reflect.TypeToken;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.File;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class Prefers {

    private static SharedPreferences getPrefers() {
        return PreferenceManager.getDefaultSharedPreferences(Init.context());
    }

    public static String getString(String key) {
        return getString(key, "");
    }

    public static String getString(String key, String defaultValue) {
        try {
            return getPrefers().getString(key, defaultValue);
        } catch (Exception e) {
            return defaultValue;
        }
    }

    public static int getInt(String key) {
        return getInt(key, 0);
    }

    public static int getInt(String key, int defaultValue) {
        try {
            return getPrefers().getInt(key, defaultValue);
        } catch (Exception e) {
            return defaultValue;
        }
    }

    public static float getFloat(String key) {
        return getFloat(key, 0f);
    }

    public static float getFloat(String key, float defaultValue) {
        try {
            return getPrefers().getFloat(key, defaultValue);
        } catch (Exception e) {
            return defaultValue;
        }
    }

    public static boolean getBoolean(String key) {
        return getBoolean(key, false);
    }

    public static boolean getBoolean(String key, boolean defaultValue) {
        try {
            return getPrefers().getBoolean(key, defaultValue);
        } catch (Exception e) {
            return defaultValue;
        }
    }

    public static void put(String key, Object obj) {
        if (obj == null) return;
        if (obj instanceof String) {
            getPrefers().edit().putString(key, (String) obj).apply();
        } else if (obj instanceof Boolean) {
            getPrefers().edit().putBoolean(key, (Boolean) obj).apply();
        } else if (obj instanceof Float) {
            getPrefers().edit().putFloat(key, (Float) obj).apply();
        } else if (obj instanceof Integer) {
            getPrefers().edit().putInt(key, (Integer) obj).apply();
        } else if (obj instanceof Long) {
            getPrefers().edit().putLong(key, (Long) obj).apply();
        } else if (obj instanceof LazilyParsedNumber) {
            getPrefers().edit().putInt(key, ((LazilyParsedNumber) obj).intValue()).apply();
        }
    }

    public static void remove(String key) {
        getPrefers().edit().remove(key).apply();
    }

    public static boolean contains(String key) {
        try {
            return getPrefers().contains(key);
        } catch (Exception e) {
            return false;
        }
    }

    public static int cleanupExpiredCache() {
        long nowSeconds = System.currentTimeMillis() / 1000;
        SharedPreferences prefers = getPrefers();
        Map<String, ?> all = prefers.getAll();
        if (all == null || all.isEmpty()) return 0;

        Set<String> expiredKeys = new HashSet<>();
        for (Map.Entry<String, ?> entry : all.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();
            if (key == null || !key.startsWith("cache_")) continue;

            if (key.endsWith("_expire")) {
                long expiresAt = parseExpires(value);
                if (expiresAt > 0 && expiresAt <= nowSeconds) {
                    expiredKeys.add(key);
                    String baseKey = key.substring(0, key.length() - "_expire".length());
                    expiredKeys.add(baseKey);
                    expiredKeys.add(baseKey + "_Token");
                }
                continue;
            }

            long expiresAt = parseExpiresFromJson(value);
            if (expiresAt > 0 && expiresAt <= nowSeconds) {
                expiredKeys.add(key);
            }
        }

        if (expiredKeys.isEmpty()) return 0;

        SharedPreferences.Editor editor = prefers.edit();
        for (String key : expiredKeys) editor.remove(key);
        editor.apply();
        return expiredKeys.size();
    }

    public static void backup(File file) {
        Path.write(file, new Gson().toJson(getPrefers().getAll()).getBytes());
    }

    public static void restore(File file) {
        try {
            Gson gson = new GsonBuilder().setObjectToNumberStrategy(ToNumberPolicy.LAZILY_PARSED_NUMBER).create();
            Map<String, Object> map = gson.fromJson(Path.read(file), new TypeToken<Map<String, Object>>() {}.getType());
            for (Map.Entry<String, ?> entry : map.entrySet()) Prefers.put(entry.getKey(), convert(entry));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static Object convert(Map.Entry<String, ?> entry) {
        if ("danmu_size".equals(entry.getKey())) {
            return Float.parseFloat(entry.getValue().toString());
        } else {
            return entry.getValue();
        }
    }

    private static long parseExpires(Object value) {
        if (value == null) return -1;
        try {
            return (long) Double.parseDouble(String.valueOf(value));
        } catch (Exception e) {
            return -1;
        }
    }

    private static long parseExpiresFromJson(Object value) {
        if (!(value instanceof String)) return -1;
        String text = ((String) value).trim();
        if (!text.startsWith("{") || !text.contains("expires")) return -1;
        try {
            JsonObject object = JsonParser.parseString(text).getAsJsonObject();
            if (object.has("expiresAt")) return parseExpires(object.get("expiresAt").getAsString());
            if (object.has("expire")) return parseExpires(object.get("expire").getAsString());
            if (object.has("expireAt")) return parseExpires(object.get("expireAt").getAsString());
            if (object.has("expires_at")) return parseExpires(object.get("expires_at").getAsString());
        } catch (Exception e) {
            return -1;
        }
        return -1;
    }
}
