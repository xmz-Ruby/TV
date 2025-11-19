package com.github.tvbox.osc.bean;

import com.github.tvbox.osc.App;
import com.google.gson.JsonElement;
import com.google.gson.annotations.SerializedName;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.Collections;
import java.util.List;

public class Header {

    @SerializedName("host")
    private String host;
    @SerializedName("header")
    private JsonElement header;

    public static List<Header> arrayFrom(JsonElement element) {
        Type listType = new TypeToken<List<Header>>() {}.getType();
        List<Header> items = App.gson().fromJson(element, listType);
        return items == null ? Collections.emptyList() : items;
    }

    public String getHost() {
        return host == null ? "" : host;
    }

    public JsonElement getHeader() {
        return header;
    }
}
