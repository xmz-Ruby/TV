package com.github.tvbox.osc.gson;

import com.github.tvbox.osc.bean.Url;
import com.google.gson.JsonArray;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;

import java.lang.reflect.Type;

public class UrlAdapter implements JsonDeserializer<Url> {

    @Override
    public Url deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
        Url url;
        if (json.isJsonArray()) {
            url = convert(json.getAsJsonArray());
        } else if (json.isJsonObject()) {
            url = Url.objectFrom(json);
        } else {
            url = Url.create().add(json.getAsString());
        }
        // 对画质列表进行排序和过滤
        return url.sortAndFilterQualities();
    }

    private Url convert(JsonArray array) {
        Url url = Url.create();
        for (int i = 0; i < array.size(); i += 2) url.add(array.get(i).getAsString(), array.get(i + 1).getAsString());
        return url;
    }
}
