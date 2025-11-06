package com.github.tvbox.osc.bean;

import android.os.Parcel;
import android.os.Parcelable;
import android.text.TextUtils;

import androidx.annotation.NonNull;

import com.github.tvbox.osc.App;
import com.github.tvbox.osc.utils.Util;
import com.github.catvod.utils.Trans;
import com.google.gson.annotations.SerializedName;

import org.simpleframework.xml.Attribute;
import org.simpleframework.xml.Text;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public class Flag implements Parcelable {

    @Attribute(name = "flag", required = false)
    @SerializedName("flag")
    private String flag;
    private String show;

    @Text
    private String urls;

    @SerializedName("episodes")
    private List<Episode> episodes;

    private boolean activated;
    private int position;

    public static Flag create(String flag) {
        return new Flag(flag);
    }

    public Flag() {
        this.episodes = new ArrayList<>();
        this.position = -1;
    }

    public Flag(String flag) {
        this.episodes = new ArrayList<>();
        this.show = Trans.s2t(flag);
        this.flag = flag;
        this.position = -1;
    }

    public String getShow() {
        return TextUtils.isEmpty(show) ? getFlag() : show;
    }

    public String getFlag() {
        return TextUtils.isEmpty(flag) ? "" : flag;
    }

    public void setFlag(String flag) {
        this.flag = flag;
    }

    public boolean isInvalid() {
        String flagName = getFlag().toLowerCase();
        android.util.Log.d("Flag.isInvalid", "检查线路: " + getFlag() + " (小写: " + flagName + ")");
        String[] invalidKeywords = {"错误", "无效", "失效", "error", "invalid", "expired", "unavailable"};
        for (String keyword : invalidKeywords) {
            if (flagName.contains(keyword)) {
                android.util.Log.d("Flag.isInvalid", "线路 [" + getFlag() + "] 包含无效关键字: " + keyword);
                return true;
            }
        }
        android.util.Log.d("Flag.isInvalid", "线路 [" + getFlag() + "] 是有效线路");
        return false;
    }

    public String getUrls() {
        return urls;
    }

    public List<Episode> getEpisodes() {
        return episodes;
    }

    public boolean isActivated() {
        return activated;
    }

    public void setActivated(Flag item) {
        this.activated = item.equals(this);
        if (activated) item.episodes = episodes;
    }

    public int getPosition() {
        return position;
    }

    public void setPosition(int position) {
        this.position = position;
    }

    public void createEpisode(String data) {
        String[] urls = data.contains("#") ? data.split("#") : new String[]{data};
        for (int i = 0; i < urls.length; i++) {
            String[] split = urls[i].split("\\$");
            String number = String.format(Locale.getDefault(), "%02d", i + 1);
            Episode episode = split.length > 1 ? Episode.create(split[0].isEmpty() ? number : split[0].trim(), split[1]) : Episode.create(number, urls[i]);
            if (!getEpisodes().contains(episode)) getEpisodes().add(episode);
        }
    }

    public void toggle(boolean activated, Episode episode) {
        if (activated) setActivated(episode);
        else for (Episode item : getEpisodes()) item.deactivated();
    }

    private void setActivated(Episode episode) {
        setPosition(getEpisodes().indexOf(episode));
        for (int i = 0; i < getEpisodes().size(); i++) getEpisodes().get(i).setActivated(i == getPosition());
    }

    public Episode find(String remarks, boolean strict) {
        int number = Util.getDigit(remarks);
        if (getEpisodes().size() == 0) return null;
        if (getEpisodes().size() == 1) return getEpisodes().get(0);
        for (Episode item : getEpisodes()) if (item.rule1(remarks)) return item;
        for (Episode item : getEpisodes()) if (item.rule2(number)) return item;
        if (number == -1) for (Episode item : getEpisodes()) if (item.rule3(remarks)) return item;
        if (number == -1) for (Episode item : getEpisodes()) if (item.rule4(remarks)) return item;
        if (getPosition() != -1) return getEpisodes().get(getPosition());
        return strict ? null : getEpisodes().get(0);
    }

    public Episode find(String remarks, int episodeIndex, boolean strict) {
        if (getEpisodes().size() == 0) return null;
        if (getEpisodes().size() == 1) return getEpisodes().get(0);

        android.util.Log.d("Flag.find", "====== 开始匹配剧集 ======");
        android.util.Log.d("Flag.find", "remarks: " + remarks);
        android.util.Log.d("Flag.find", "episodeIndex: " + episodeIndex);
        android.util.Log.d("Flag.find", "剧集总数: " + getEpisodes().size());

        // 规则0: 优先从remarks中解析标准集数格式（最重要！）
        int parsedFromRemarks = -1;
        if (!TextUtils.isEmpty(remarks)) {
            parsedFromRemarks = parseEpisodeNumber(remarks);
            android.util.Log.d("Flag.find", "从remarks解析到集数: " + parsedFromRemarks);

            if (parsedFromRemarks > 0) {
                // 遍历所有episode，从名称中解析集数进行匹配
                for (int i = 0; i < getEpisodes().size(); i++) {
                    Episode ep = getEpisodes().get(i);
                    String epName = ep.getName();
                    int epParsedNumber = parseEpisodeNumber(epName);

                    android.util.Log.d("Flag.find", "检查episode[" + i + "]: name=" + epName +
                                     ", parsedNumber=" + epParsedNumber);

                    if (epParsedNumber == parsedFromRemarks) {
                        android.util.Log.d("Flag.find", "✓ 通过remarks解析匹配成功 - 位置: " + i);
                        return ep;
                    }
                }
                android.util.Log.d("Flag.find", "remarks解析匹配失败");
            }
        }

        // 规则1: 精准匹配剧集名
        if (!TextUtils.isEmpty(remarks)) {
            for (int i = 0; i < getEpisodes().size(); i++) {
                Episode item = getEpisodes().get(i);
                if (item.rule1(remarks)) {
                    android.util.Log.d("Flag.find", "✓ 精准名称匹配成功 - 位置: " + i + ", name: " + item.getName());
                    return item;
                }
            }
            android.util.Log.d("Flag.find", "精准名称匹配失败");
        }

        // 规则2: 使用数字匹配（原有的Util.getDigit）
        int number = Util.getDigit(remarks);
        android.util.Log.d("Flag.find", "Util.getDigit从remarks提取数字: " + number);
        if (number != -1) {
            for (int i = 0; i < getEpisodes().size(); i++) {
                Episode item = getEpisodes().get(i);
                if (item.rule2(number)) {
                    android.util.Log.d("Flag.find", "✓ 数字匹配成功 - 位置: " + i);
                    return item;
                }
            }
            android.util.Log.d("Flag.find", "数字匹配失败");
        }

        // 规则3: 模糊匹配
        if (number == -1 && !TextUtils.isEmpty(remarks)) {
            for (int i = 0; i < getEpisodes().size(); i++) {
                Episode item = getEpisodes().get(i);
                if (item.rule3(remarks)) {
                    android.util.Log.d("Flag.find", "✓ 模糊匹配1成功 - 位置: " + i);
                    return item;
                }
            }
            for (int i = 0; i < getEpisodes().size(); i++) {
                Episode item = getEpisodes().get(i);
                if (item.rule4(remarks)) {
                    android.util.Log.d("Flag.find", "✓ 模糊匹配2成功 - 位置: " + i);
                    return item;
                }
            }
            android.util.Log.d("Flag.find", "模糊匹配失败");
        }

        // 使用上次位置
        if (getPosition() != -1 && getPosition() < getEpisodes().size()) {
            android.util.Log.d("Flag.find", "✓ 使用上次位置: " + getPosition());
            return getEpisodes().get(getPosition());
        }

        android.util.Log.d("Flag.find", "所有匹配失败，返回" + (strict ? "null" : "第一集"));
        return strict ? null : getEpisodes().get(0);
    }

    /**
     * 从剧集名称中解析集数（参考弹幕搜索的parseEpisodeNumber）
     */
    private int parseEpisodeNumber(String title) {
        if (TextUtils.isEmpty(title)) return -1;

        // 按优先级顺序尝试匹配
        String[] patterns = {
            "S\\d+E(\\d+)",      // S01E16 - 最高优先级
            "EP?(\\d+)",         // EP16 或 E16
            "第(\\d+)[话集]",    // 第16集
            "\\[(\\d+)\\]"       // [16] - 最低优先级
        };

        for (String patternStr : patterns) {
            java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(patternStr, java.util.regex.Pattern.CASE_INSENSITIVE);
            java.util.regex.Matcher matcher = pattern.matcher(title);
            if (matcher.find()) {
                try {
                    return Integer.parseInt(matcher.group(1));
                } catch (NumberFormatException ignored) {
                }
            }
        }

        return -1;
    }

    public static List<Flag> create(String flag, String name, String url) {
        Flag item = Flag.create(flag);
        item.getEpisodes().add(Episode.create(name, url));
        return Arrays.asList(item);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof Flag)) return false;
        Flag it = (Flag) obj;
        return getFlag().equals(it.getFlag());
    }

    @NonNull
    @Override
    public String toString() {
        return App.gson().toJson(this);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(this.flag);
        dest.writeString(this.show);
        dest.writeString(this.urls);
        dest.writeTypedList(this.episodes);
        dest.writeByte(this.activated ? (byte) 1 : (byte) 0);
        dest.writeInt(this.position);
    }

    protected Flag(Parcel in) {
        this.flag = in.readString();
        this.show = in.readString();
        this.urls = in.readString();
        this.episodes = in.createTypedArrayList(Episode.CREATOR);
        this.activated = in.readByte() != 0;
        this.position = in.readInt();
    }

    public static final Creator<Flag> CREATOR = new Creator<>() {
        @Override
        public Flag createFromParcel(Parcel source) {
            return new Flag(source);
        }

        @Override
        public Flag[] newArray(int size) {
            return new Flag[size];
        }
    };
}
