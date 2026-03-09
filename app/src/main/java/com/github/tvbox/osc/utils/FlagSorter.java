package com.github.tvbox.osc.utils;

import java.util.ArrayList;
import java.util.List;

/**
 * 线路过滤工具类
 * 用于过滤外部spider返回的播放线路
 */
public class FlagSorter {

    // 需要过滤掉的线路关键字
    private static final String[] FILTER_KEYWORDS = {
        "采集", "不可用", "广告",
        "错误", "无效", "失效", "error", "invalid", "expired", "unavailable"
    };

    /**
     * 过滤结果
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
     * 对线路进行过滤
     * @param playFrom 线路名称（用$$$分隔）
     * @param playUrl 线路URL（用$$$分隔）
     * @return 过滤后的结果
     */
    public static FilterResult filterAndSort(String playFrom, String playUrl) {
        if (playFrom == null || playFrom.isEmpty() || playUrl == null || playUrl.isEmpty()) {
            return new FilterResult(new ArrayList<>(), new ArrayList<>());
        }

        String[] playFromArray = playFrom.split("\\$\\$\\$");
        String[] playUrlArray = playUrl.split("\\$\\$\\$");

        List<String> resultPlayFrom = new ArrayList<>();
        List<String> resultPlayUrl = new ArrayList<>();

        for (int i = 0; i < playFromArray.length; i++) {
            if (i >= playUrlArray.length) break;

            String flagName = playFromArray[i].trim();
            String flagUrl = playUrlArray[i];

            if (!shouldFilter(flagName)) {
                resultPlayFrom.add(flagName);
                resultPlayUrl.add(flagUrl);
            }
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
}
