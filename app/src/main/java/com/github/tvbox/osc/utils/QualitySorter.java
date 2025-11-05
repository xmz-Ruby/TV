package com.github.tvbox.osc.utils;

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

    // 画质优先级映射表（数字越小排序后越靠前）
    // 排序规则：高清 -> 流畅/标清 -> 超清 -> 4K -> 原画/蓝光
    private static final Map<String, Integer> QUALITY_PRIORITY = new HashMap<>();

    static {
        // 优先级1：高清（最常用，排第一）
        QUALITY_PRIORITY.put("高清", 1);
        QUALITY_PRIORITY.put("high", 1);
        QUALITY_PRIORITY.put("HD", 1);
        QUALITY_PRIORITY.put("hd", 1);

        // 优先级2：流畅/标清（低画质，排第二）
        QUALITY_PRIORITY.put("流畅", 2);
        QUALITY_PRIORITY.put("low", 2);
        QUALITY_PRIORITY.put("标清", 2);
        QUALITY_PRIORITY.put("普清", 2);

        // 优先级3：超清（高画质，排第三）
        QUALITY_PRIORITY.put("超清", 3);
        QUALITY_PRIORITY.put("super", 3);
        QUALITY_PRIORITY.put("FHD", 3);
        QUALITY_PRIORITY.put("fhd", 3);
        QUALITY_PRIORITY.put("全高清", 3);

        // 优先级4：4K（超高画质，排第四）
        QUALITY_PRIORITY.put("4K", 4);
        QUALITY_PRIORITY.put("4k", 4);
        QUALITY_PRIORITY.put("UHD", 4);
        QUALITY_PRIORITY.put("uhd", 4);

        // 优先级5：原画/蓝光（最高画质，排最后）
        QUALITY_PRIORITY.put("原画", 5);
        QUALITY_PRIORITY.put("蓝光", 5);
        QUALITY_PRIORITY.put("Blu-ray", 5);
        QUALITY_PRIORITY.put("BluRay", 5);
        QUALITY_PRIORITY.put("BD", 5);
        QUALITY_PRIORITY.put("bd", 5);
    }

    // 需要过滤掉的无效画质名称
    private static final String[] INVALID_QUALITY_NAMES = {
        "原代服", "原代本", "代服", "代本"
    };

    /**
     * 对画质列表进行过滤和排序
     * @param values 原始画质列表
     * @return 过滤并排序后的画质列表
     */
    public static List<Value> sortAndFilter(List<Value> values) {
        if (values == null || values.isEmpty()) {
            return values;
        }

        // 过滤无效画质
        List<Value> filtered = new ArrayList<>();
        for (Value value : values) {
            if (!isInvalidQuality(value.getN())) {
                filtered.add(value);
            }
        }

        // 如果过滤后为空，返回原列表
        if (filtered.isEmpty()) {
            return values;
        }

        // 按画质优先级排序（从低到高）
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
     * @return 优先级数字（越小优先级越低）
     */
    private static int getQualityPriority(String qualityName) {
        if (qualityName == null || qualityName.trim().isEmpty()) {
            return 0;
        }

        // 精确匹配
        if (QUALITY_PRIORITY.containsKey(qualityName)) {
            return QUALITY_PRIORITY.get(qualityName);
        }

        // 模糊匹配（包含关键词）
        for (Map.Entry<String, Integer> entry : QUALITY_PRIORITY.entrySet()) {
            if (qualityName.contains(entry.getKey())) {
                return entry.getValue();
            }
        }

        // 未知画质，默认放在高清之后
        return 1;
    }

    /**
     * 获取画质名称的显示文本（用于调试）
     */
    public static String getQualityDisplayName(String qualityName) {
        int priority = getQualityPriority(qualityName);
        return qualityName + " (优先级: " + priority + ")";
    }
}
