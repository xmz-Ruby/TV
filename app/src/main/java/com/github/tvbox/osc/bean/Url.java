package com.github.tvbox.osc.bean;

import android.text.TextUtils;

import com.github.tvbox.osc.App;
import com.github.tvbox.osc.utils.QualitySorter;
import com.google.gson.JsonElement;
import com.google.gson.annotations.SerializedName;

import java.util.ArrayList;
import java.util.List;

public class Url {

    @SerializedName("values")
    private List<Value> values;
    @SerializedName("position")
    private int position;

    public static Url objectFrom(JsonElement element) {
        try {
            Url url = App.gson().fromJson(element, Url.class);
            // 对画质列表进行排序和过滤
            return url.sortAndFilterQualities();
        } catch (Exception e) {
            return create();
        }
    }

    public static Url create() {
        return new Url();
    }

    public List<Value> getValues() {
        if (values == null) {
            values = new ArrayList<>();
        }
        return values;
    }

    /**
     * 对画质列表进行排序和过滤
     * 应该在解析完成后调用此方法
     */
    public Url sortAndFilterQualities() {
        if (values != null && !values.isEmpty()) {
            List<Value> sorted = QualitySorter.sortAndFilter(values);
            if (sorted != null && !sorted.isEmpty()) {
                values = sorted;
                // 重置position，确保不越界
                if (position >= values.size()) {
                    position = 0;
                }
            }
        }
        return this;
    }

    public int getPosition() {
        return position;
    }

    public String v() {
        return v(getPosition());
    }

    public String v(int position) {
        return position >= getValues().size() ? "" : getValues().get(position).getV();
    }

    public String n(int position) {
        return position >= getValues().size() ? "" : getValues().get(position).getN();
    }

    public Url add(String v) {
        getValues().add(new Value("", v));
        return this;
    }

    public Url add(String n, String v) {
        getValues().add(new Value(n, v));
        return this;
    }

    public Url replace(String url) {
        getValues().get(getPosition()).setV(url);
        return this;
    }

    public Url set(int position) {
        this.position = Math.min(position, getValues().size() - 1);
        return this;
    }

    public boolean isEmpty() {
        return getValues().isEmpty() || TextUtils.isEmpty(v());
    }

    public boolean isMulti() {
        return getValues().size() > 1;
    }
}
