package com.github.tvbox.osc.utils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 线路过滤和排序工具类
 * 用于过滤和排序外部spider返回的播放线路
 */
public class FlagSorter {

    // 需要过滤掉的线路关键字
    private static final String[] FILTER_KEYWORDS = {
        "百度", "度盘", "天翼", "123", "迅雷", "采集"
    };

    // 线路排序规则：包含"预览" > 包含"原画" > 其他

    /**
     * 过滤和排序结果
     */
    public static class FilterResult {
        public List<String> playFromList;
        public List<String> playUrlList;

        public FilterResult(List<String> playFromList, List<String> playUrlList) {
            this.playFromList = playFromList;
            this.playUrlList = playUrlList;
        }
    }

    /**
     * 对线路进行过滤和排序
     * @param playFrom 线路名称（用$$$分隔）
     * @param playUrl 线路URL（用$$$分隔）
     * @return 过滤并排序后的结果
     */
    public static FilterResult filterAndSort(String playFrom, String playUrl) {
        if (playFrom == null || playFrom.isEmpty() || playUrl == null || playUrl.isEmpty()) {
            return new FilterResult(new ArrayList<>(), new ArrayList<>());
        }

        String[] playFromArray = playFrom.split("\\$\\$\\$");
        String[] playUrlArray = playUrl.split("\\$\\$\\$");

        // 创建线路列表（保持playFrom和playUrl的对应关系）
        List<FlagItem> flagItems = new ArrayList<>();
        for (int i = 0; i < playFromArray.length; i++) {
            if (i >= playUrlArray.length) break;

            String flagName = playFromArray[i].trim();
            String flagUrl = playUrlArray[i];

            // 过滤掉包含关键字的线路
            if (!shouldFilter(flagName)) {
                flagItems.add(new FlagItem(flagName, flagUrl, i));
            }
        }

        // 如果过滤后为空，返回空列表
        if (flagItems.isEmpty()) {
            return new FilterResult(new ArrayList<>(), new ArrayList<>());
        }

        // 按优先级排序
        Collections.sort(flagItems, (item1, item2) -> {
            int priority1 = getFlagPriority(item1.name);
            int priority2 = getFlagPriority(item2.name);

            // 如果优先级相同，保持原有顺序
            if (priority1 == priority2) {
                return Integer.compare(item1.originalIndex, item2.originalIndex);
            }

            return Integer.compare(priority1, priority2);
        });

        // 构建结果
        List<String> resultPlayFrom = new ArrayList<>();
        List<String> resultPlayUrl = new ArrayList<>();

        for (FlagItem item : flagItems) {
            resultPlayFrom.add(item.name);
            resultPlayUrl.add(item.url);
        }

        return new FilterResult(resultPlayFrom, resultPlayUrl);
    }

    /**
     * 判断线路是否应该被过滤
     */
    private static boolean shouldFilter(String flagName) {
        if (flagName == null || flagName.isEmpty()) {
            return true;
        }

        for (String keyword : FILTER_KEYWORDS) {
            if (flagName.contains(keyword)) {
                return true;
            }
        }

        return false;
    }

    /**
     * 获取线路优先级
     * @param flagName 线路名称
     * @return 优先级数字（越小优先级越高）
     */
    private static int getFlagPriority(String flagName) {
        if (flagName == null || flagName.isEmpty()) {
            return 999;
        }

        // 按优先级顺序检查（优先级高的先检查）
        // 优先级1：包含"预览"
        if (flagName.contains("预览") || flagName.toLowerCase().contains("preview")) {
            return 1;
        }

        // 优先级2：包含"原画"或"原"
        if (flagName.contains("原画") || flagName.contains("原")) {
            return 2;
        }

        // 未知线路，放在最后
        return 999;
    }

    /**
     * 线路项（用于排序时保持playFrom和playUrl的对应关系）
     */
    private static class FlagItem {
        String name;
        String url;
        int originalIndex;

        FlagItem(String name, String url, int originalIndex) {
            this.name = name;
            this.url = url;
            this.originalIndex = originalIndex;
        }
    }
}
