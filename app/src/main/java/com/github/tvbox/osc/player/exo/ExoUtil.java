package com.github.tvbox.osc.player.exo;

import android.content.Context;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.accessibility.CaptioningManager;

import androidx.media3.common.MediaItem;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.TrackSelectionOverride;
import androidx.media3.common.Tracks;
import androidx.media3.exoplayer.DefaultLoadControl;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.LoadControl;
import androidx.media3.exoplayer.RenderersFactory;
import androidx.media3.exoplayer.source.MediaSource;
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector;
import androidx.media3.exoplayer.trackselection.TrackSelector;
import androidx.media3.ui.CaptionStyleCompat;
import androidx.media3.ui.PlayerView;

import com.github.tvbox.osc.App;
import com.github.tvbox.osc.Setting;
import com.github.tvbox.osc.bean.Drm;
import com.github.tvbox.osc.bean.Sub;
import com.github.tvbox.osc.player.Players;
import com.github.tvbox.osc.utils.Sniffer;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class ExoUtil {

    /**
     * 增强的预加载配置
     * - minBufferMs: 用户设置的最小缓冲时间（默认8秒）
     * - maxBufferMs: 最大缓冲时间增加到120秒（2分钟），支持持续预加载
     * - bufferForPlaybackMs: 开始播放需要的缓冲时间（1秒）
     * - bufferForPlaybackAfterRebufferMs: 重新缓冲后开始播放的时间（2秒）
     *
     * 这样配置可以实现：
     * 1. 播放时持续缓存前方约1-2分钟的内容
     * 2. 暂停时可以继续累积更多缓存
     * 3. 网络不好时有足够缓冲避免卡顿
     */
    public static LoadControl buildLoadControl() {
        int minBufferMs = Math.max(Setting.getBuffer() * 1000, DefaultLoadControl.DEFAULT_BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS);
        int maxBufferMs = 120 * 1000; // 120秒（2分钟），支持持续预加载
        int bufferForPlaybackMs = DefaultLoadControl.DEFAULT_BUFFER_FOR_PLAYBACK_MS; // 1秒
        int bufferForPlaybackAfterRebufferMs = DefaultLoadControl.DEFAULT_BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS; // 2秒
        return new DefaultLoadControl.Builder()
                .setBufferDurationsMs(minBufferMs, maxBufferMs, bufferForPlaybackMs, bufferForPlaybackAfterRebufferMs)
                .build();
    }

    public static TrackSelector buildTrackSelector() {
        DefaultTrackSelector trackSelector = new DefaultTrackSelector(App.get());
        trackSelector.setParameters(trackSelector.buildUponParameters().setPreferredTextLanguage(Locale.getDefault().getISO3Language()).setForceHighestSupportedBitrate(true).setTunnelingEnabled(Setting.isTunnel()));
        return trackSelector;
    }

    public static RenderersFactory buildRenderersFactory(int decode) {
        return new NextRenderersFactory(App.get(), decode);
    }

    public static MediaSource.Factory buildMediaSourceFactory() {
        return new MediaSourceFactory();
    }

    public static CaptionStyleCompat getCaptionStyle() {
        return Setting.isCaption() ? CaptionStyleCompat.createFromCaptionStyle(((CaptioningManager) App.get().getSystemService(Context.CAPTIONING_SERVICE)).getUserStyle()) : new CaptionStyleCompat(Color.WHITE, Color.TRANSPARENT, Color.TRANSPARENT, CaptionStyleCompat.EDGE_TYPE_OUTLINE, Color.BLACK, null);
    }

    public static boolean haveTrack(Tracks tracks, int type) {
        int count = 0;
        for (Tracks.Group trackGroup : tracks.getGroups()) if (trackGroup.getType() == type) count += trackGroup.length;
        return count > 0;
    }

    public static void selectTrack(ExoPlayer player, int group, int track) {
        List<Integer> trackIndices = new ArrayList<>();
        selectTrack(player, group, track, trackIndices);
        setTrackParameters(player, group, trackIndices);
    }

    public static void deselectTrack(ExoPlayer player, int group, int track) {
        List<Integer> trackIndices = new ArrayList<>();
        deselectTrack(player, group, track, trackIndices);
        setTrackParameters(player, group, trackIndices);
    }

    public static void setSubtitleView(PlayerView exo) {
        exo.getSubtitleView().setStyle(getCaptionStyle());
        exo.getSubtitleView().setApplyEmbeddedFontSizes(false);
        exo.getSubtitleView().setApplyEmbeddedStyles(!Setting.isCaption());
        if (Setting.getSubtitleTextSize() != 0) exo.getSubtitleView().setFractionalTextSize(Setting.getSubtitleTextSize());
        if (Setting.getSubtitleBottomPadding() != 0) exo.getSubtitleView().setBottomPaddingFraction(Setting.getSubtitleBottomPadding());
    }

    public static String getMimeType(String path) {
        if (TextUtils.isEmpty(path)) return "";
        if (path.endsWith(".vtt")) return MimeTypes.TEXT_VTT;
        if (path.endsWith(".ssa") || path.endsWith(".ass")) return MimeTypes.TEXT_SSA;
        if (path.endsWith(".ttml") || path.endsWith(".xml") || path.endsWith(".dfxp")) return MimeTypes.APPLICATION_TTML;
        return MimeTypes.APPLICATION_SUBRIP;
    }

    public static String getMimeType(int errorCode) {
        // Note: APPLICATION_OCTET was removed in Media3 1.9.2, using APPLICATION_M3U8 as fallback
        if (errorCode == PlaybackException.ERROR_CODE_PARSING_MANIFEST_UNSUPPORTED || errorCode == PlaybackException.ERROR_CODE_PARSING_MANIFEST_MALFORMED) return MimeTypes.APPLICATION_M3U8;
        if (errorCode == PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED || errorCode == PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED || errorCode == PlaybackException.ERROR_CODE_IO_UNSPECIFIED) return MimeTypes.APPLICATION_M3U8;
        return null;
    }

    public static int getRetry(int errorCode) {
        if (errorCode == PlaybackException.ERROR_CODE_IO_UNSPECIFIED) return 2;
        if (errorCode == PlaybackException.ERROR_CODE_DECODER_INIT_FAILED) return 2;
        if (errorCode >= PlaybackException.ERROR_CODE_DECODER_QUERY_FAILED && errorCode <= PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED) return 2;
        if (errorCode >= PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED && errorCode <= PlaybackException.ERROR_CODE_PARSING_MANIFEST_UNSUPPORTED) return 2;
        return 1;
    }

    public static MediaItem getMediaItem(Map<String, String> headers, Uri uri, String mimeType, Drm drm, List<Sub> subs, int decode) {
        MediaItem.Builder builder = new MediaItem.Builder().setUri(uri);
        builder.setRequestMetadata(getRequestMetadata(headers, uri));
        builder.setSubtitleConfigurations(getSubtitleConfigs(subs));
        if (drm != null) builder.setDrmConfiguration(drm.get());
        if (mimeType != null) builder.setMimeType(mimeType);
        // Note: setAds() was removed in Media3 1.9.2
        // Ad detection regex is now handled differently - see Sniffer.getRegex(uri)
        builder.setMediaId(uri.toString());
        return builder.build();
    }

    private static MediaItem.RequestMetadata getRequestMetadata(Map<String, String> headers, Uri uri) {
        Bundle extras = new Bundle();
        for (Map.Entry<String, String> header : headers.entrySet()) extras.putString(header.getKey(), header.getValue());
        return new MediaItem.RequestMetadata.Builder().setMediaUri(uri).setExtras(extras).build();
    }

    private static List<MediaItem.SubtitleConfiguration> getSubtitleConfigs(List<Sub> subs) {
        List<MediaItem.SubtitleConfiguration> configs = new ArrayList<>();
        for (Sub sub : subs) configs.add(sub.getConfig());
        return configs;
    }

    private static void selectTrack(ExoPlayer player, int group, int track, List<Integer> trackIndices) {
        if (group >= player.getCurrentTracks().getGroups().size()) return;
        Tracks.Group trackGroup = player.getCurrentTracks().getGroups().get(group);
        for (int i = 0; i < trackGroup.length; i++) {
            if (i == track || trackGroup.isTrackSelected(i)) trackIndices.add(i);
        }
    }

    private static void deselectTrack(ExoPlayer player, int group, int track, List<Integer> trackIndices) {
        if (group >= player.getCurrentTracks().getGroups().size()) return;
        Tracks.Group trackGroup = player.getCurrentTracks().getGroups().get(group);
        for (int i = 0; i < trackGroup.length; i++) {
            if (i != track && trackGroup.isTrackSelected(i)) trackIndices.add(i);
        }
    }

    private static void setTrackParameters(ExoPlayer player, int group, List<Integer> trackIndices) {
        if (group >= player.getCurrentTracks().getGroups().size()) return;
        player.setTrackSelectionParameters(player.getTrackSelectionParameters().buildUpon().setOverrideForType(new TrackSelectionOverride(player.getCurrentTracks().getGroups().get(group).getMediaTrackGroup(), trackIndices)).build());
    }
}
