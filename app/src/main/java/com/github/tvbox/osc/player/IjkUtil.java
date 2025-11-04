package com.github.tvbox.osc.player;

import android.net.Uri;

import com.github.tvbox.osc.Setting;
import com.github.tvbox.osc.bean.Channel;
import com.github.tvbox.osc.bean.Result;
import com.github.tvbox.osc.player.exo.ExoUtil;
import com.github.tvbox.osc.utils.UrlUtil;

import java.util.Map;

import tv.danmaku.ijk.media.player.MediaSource;
import tv.danmaku.ijk.media.player.ui.IjkVideoView;

public class IjkUtil {

    public static MediaSource getSource(Result result) {
        return getSource(result.getHeaders(), result.getRealUrl());
    }

    public static MediaSource getSource(Channel channel) {
        return getSource(channel.getHeaders(), channel.getUrl());
    }

    public static MediaSource getSource(Map<String, String> headers, String url) {
        Uri uri = UrlUtil.uri(url);
        return new MediaSource(Players.checkUa(headers), uri);
    }

    public static void setSubtitleView(IjkVideoView ijk) {
        ijk.getSubtitleView().setStyle(ExoUtil.getCaptionStyle());
        ijk.getSubtitleView().setApplyEmbeddedFontSizes(false);
        ijk.getSubtitleView().setApplyEmbeddedStyles(!Setting.isCaption());
        if (Setting.getSubtitleTextSize() != 0) ijk.getSubtitleView().setFractionalTextSize(Setting.getSubtitleTextSize());
        if (Setting.getSubtitleBottomPadding() != 0) ijk.getSubtitleView().setBottomPaddingFraction(Setting.getSubtitleBottomPadding());
    }
}
