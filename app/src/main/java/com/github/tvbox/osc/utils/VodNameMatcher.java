package com.github.tvbox.osc.utils;

import android.text.TextUtils;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class VodNameMatcher {

    private static final Pattern SEASON_PATTERN = Pattern.compile("第?([0-9]+|[零〇一二两三四五六七八九十百]+)季");

    public static boolean same(String name, String keyword) {
        String normalizedName = normalize(name);
        String normalizedKeyword = normalize(keyword);
        return !normalizedName.isEmpty() && normalizedName.equals(normalizedKeyword);
    }

    public static boolean contains(String name, String keyword) {
        return normalize(name).contains(normalize(keyword));
    }

    public static String normalize(String text) {
        if (TextUtils.isEmpty(text)) return "";
        String value = text.trim().toLowerCase(Locale.ROOT);
        StringBuilder builder = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c >= '０' && c <= '９') c = (char) ('0' + c - '０');
            if (Character.isWhitespace(c) || isSeparator(c)) continue;
            builder.append(c);
        }
        return normalizeSeason(builder.toString());
    }

    private static boolean isSeparator(char c) {
        return c == '.' || c == '。' || c == '·' || c == '・' || c == '-' || c == '_' || c == '＿' || c == '　';
    }

    private static String normalizeSeason(String text) {
        Matcher matcher = SEASON_PATTERN.matcher(text);
        StringBuffer buffer = new StringBuffer();
        while (matcher.find()) {
            int number = parseSeasonNumber(matcher.group(1));
            if (number <= 0) continue;
            matcher.appendReplacement(buffer, String.valueOf(number));
        }
        matcher.appendTail(buffer);
        return buffer.toString();
    }

    private static int parseSeasonNumber(String text) {
        if (TextUtils.isEmpty(text)) return 0;
        try {
            if (TextUtils.isDigitsOnly(text)) return Integer.parseInt(text);
        } catch (NumberFormatException ignored) {
            return 0;
        }
        return parseChineseNumber(text);
    }

    private static int parseChineseNumber(String text) {
        int section = 0;
        int number = 0;
        for (int i = 0; i < text.length(); i++) {
            int digit = chineseDigit(text.charAt(i));
            if (digit >= 0) {
                number = digit;
                continue;
            }
            int unit = chineseUnit(text.charAt(i));
            if (unit > 0) {
                if (number == 0) number = 1;
                section += number * unit;
                number = 0;
            }
        }
        return section + number;
    }

    private static int chineseDigit(char c) {
        switch (c) {
            case '零':
            case '〇':
                return 0;
            case '一':
                return 1;
            case '二':
            case '两':
                return 2;
            case '三':
                return 3;
            case '四':
                return 4;
            case '五':
                return 5;
            case '六':
                return 6;
            case '七':
                return 7;
            case '八':
                return 8;
            case '九':
                return 9;
            default:
                return -1;
        }
    }

    private static int chineseUnit(char c) {
        switch (c) {
            case '十':
                return 10;
            case '百':
                return 100;
            default:
                return 0;
        }
    }
}
