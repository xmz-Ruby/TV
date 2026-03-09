package com.github.tvbox.osc.utils;

import android.os.Build;

import com.github.tvbox.osc.bean.Value;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 画质排序工具类
 * 用于过滤和排序外部spider返回的画质列表
 */
public class QualitySorter {

    // x86/arm64 架构的画质优先级映射表
    private static final Map<String, Integer> QUALITY_PRIORITY_HIGH_PERFORMANCE = new HashMap<>();

    // arm 架构的画质优先级映射表
    private static final Map<String, Integer> QUALITY_PRIORITY_LOW_PERFORMANCE = new HashMap<>();

    static {
        // x86/arm64: 原画/蓝光 -> 4K -> 超清 -> 高清 -> 流畅/标清
        QUALITY_PRIORITY_HIGH_PERFORMANCE.put("原画", 1);
        QUALITY_PRIORITY_HIGH_PERFORMANCE.put("蓝光", 1);
        QUALITY_PRIORITY_HIGH_PERFORMANCE.put("Blu-ray", 1);
        QUALITY_PRIORITY_HIGH_PERFORMANCE.put("BluRay", 1);
        QUALITY_PRIORITY_HIGH_PERFORMANCE.put("BD", 1);
        QUALITY_PRIORITY_HIGH_PERFORMANCE.put("bd", 1);

        QUALITY_PRIORITY_HIGH_PERFORMANCE.put("4K", 2);
        QUALITY_PRIORITY_HIGH_PERFORMANCE.put("4k", 2);
        QUALITY_PRIORITY_HIGH_PERFORMANCE.put("UHD", 2);
        QUALITY_PRIORITY_HIGH_PERFORMANCE.put("uhd", 2);

        QUALITY_PRIORITY_HIGH_PERFORMANCE.put("超清", 3);
        QUALITY_PRIORITY_HIGH_PERFORMANCE.put("super", 3);
        QUALITY_PRIORITY_HIGH_PERFORMANCE.put("FHD", 3);
        QUALITY_PRIORITY_HIGH_PERFORMANCE.put("fhd", 3);
        QUALITY_PRIORITY_HIGH_PERFORMANCE.put("全高清", 3);

        QUALITY_PRIORITY_HIGH_PERFORMANCE.put("高清", 4);
        QUALITY_PRIORITY_HIGH_PERFORMANCE.put("high", 4);
        QUALITY_PRIORITY_HIGH_PERFORMANCE.put("HD", 4);
        QUALITY_PRIORITY_HIGH_PERFORMANCE.put("hd", 4);

        QUALITY_PRIORITY_HIGH_PERFORMANCE.put("流畅", 5);
        QUALITY_PRIORITY_HIGH_PERFORMANCE.put("low", 5);
        QUALITY_PRIORITY_HIGH_PERFORMANCE.put("标清", 5);
        QUALITY_PRIORITY_HIGH_PERFORMANCE.put("普清", 5);

        // arm: 超清 -> 高清 -> 流畅/标清 -> 4K -> 原画/蓝光
        QUALITY_PRIORITY_LOW_PERFORMANCE.put("超清", 1);
        QUALITY_PRIORITY_LOW_PERFORMANCE.put("super", 1);
        QUALITY_PRIORITY_LOW_PERFORMANCE.put("FHD", 1);
        QUALITY_PRIORITY_LOW_PERFORMANCE.put("fhd", 1);
        QUALITY_PRIORITY_LOW_PERFORMANCE.put("全高清", 1);

        QUALITY_PRIORITY_LOW_PERFORMANCE.put("高清", 2);
        QUALITY_PRIORITY_LOW_PERFORMANCE.put("high", 2);
        QUALITY_PRIORITY_LOW_PERFORMANCE.put("HD", 2);
        QUALITY_PRIORITY_LOW_PERFORMANCE.put("hd", 2);

        QUALITY_PRIORITY_LOW_PERFORMANCE.put("流畅", 3);
        QUALITY_PRIORITY_LOW_PERFORMANCE.put("low", 3);
        QUALITY_PRIORITY_LOW_PERFORMANCE.put("标清", 3);
        QUALITY_PRIORITY_LOW_PERFORMANCE.put("普清", 3);

        QUALITY_PRIORITY_LOW_PERFORMANCE.put("4K", 4);
        QUALITY_PRIORITY_LOW_PERFORMANCE.put("4k", 4);
        QUALITY_PRIORITY_LOW_PERFORMANCE.put("UHD", 4);
        QUALITY_PRIORITY_LOW_PERFORMANCE.put("uhd", 4);

        QUALITY_PRIORITY_LOW_PERFORMANCE.put("原画", 5);
        QUALITY_PRIORITY_LOW_PERFORMANCE.put("蓝光", 5);
        QUALITY_PRIORITY_LOW_PERFORMANCE.put("Blu-ray", 5);
        QUALITY_PRIORITY_LOW_PERFORMANCE.put("BluRay", 5);
        QUALITY_PRIORITY_LOW_PERFORMANCE.put("BD", 5);
        QUALITY_PRIORITY_LOW_PERFORMANCE.put("bd", 5);
    }

    private static final String[] INVALID_QUALITY_NAMES = {
        "原代服", "原代本", "代服", "代本"
    };

    /**
     * 检测是否为高性能架构 (x86 或 arm64)
     */
    private static boolean isHighPerformanceArch() {
        String[] abis = Build.SUPPORTED_ABIS;
        for (String abi : abis) {
            if (abi.contains("x86") || abi.contains("arm64")) {
                return true;
            }
        }
        return false;
    }

    /**
     * 获取当前架构对应的优先级映射表
     */
    private static Map<String, Integer> getQualityPriorityMap() {
        return isHighPerformanceArch() ? QUALITY_PRIORITY_HIGH_PERFORMANCE : QUALITY_PRIORITY_LOW_PERFORMANCE;
    }

    /**
     * 对画质列表进行过滤和排序
     * @param values 原始画质列表
     * @return 过滤并排序后的画质列表
     */
    public static List<Value> sortAndFilter(List<Value> values) {
        if (values == null || values.isEmpty()) {
            return values;
        }

        List<Value> filtered = new ArrayList<>();
        for (Value value : values) {
            if (!isInvalidQuality(value.getN())) {
                filtered.add(value);
            }
        }

        if (filtered.isEmpty()) {
            return values;
        }

        Collections.sort(filtered, new Comparator<Value>() {
            @Override
            public int compare(Value v1, Value v2) {
                int priority1 = getQualityPriority(v1.getN());
                int priority2 = getQualityPriority(v2.getN());
                return Integer.compare(priority1, priority2);
            }
        });

        return filtered;
    }

    /**
     * 判断是否为无效画质名称
     */
    private static boolean isInvalidQuality(String qualityName) {
        if (qualityName == null || qualityName.trim().isEmpty()) {
            return true;
        }

        for (String invalid : INVALID_QUALITY_NAMES) {
            if (qualityName.contains(invalid)) {
                return true;
            }
        }

        return false;
    }

    /**
     * 获取画质优先级
     * @param qualityName 画质名称
     * @return 优先级数字（越小优先级越高）
     */
    private static int getQualityPriority(String qualityName) {
        if (qualityName == null || qualityName.trim().isEmpty()) {
            return 999;
        }

        Map<String, Integer> priorityMap = getQualityPriorityMap();

        if (priorityMap.containsKey(qualityName)) {
            return priorityMap.get(qualityName);
        }

        for (Map.Entry<String, Integer> entry : priorityMap.entrySet()) {
            if (qualityName.contains(entry.getKey())) {
                return entry.getValue();
            }
        }

        return 999;
    }

    /**
     * 获取画质名称的显示文本（用于调试）
     */
    public static String getQualityDisplayName(String qualityName) {
        int priority = getQualityPriority(qualityName);
        return qualityName + " (优先级: " + priority + ")";
    }
}
