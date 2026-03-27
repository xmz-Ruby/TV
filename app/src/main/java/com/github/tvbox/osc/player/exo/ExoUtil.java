package com.github.tvbox.osc.player.exo;

import android.app.ActivityManager;
import android.content.Context;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Build;
import android.text.TextUtils;
import android.view.accessibility.CaptioningManager;

import androidx.media3.common.C;
import androidx.media3.common.Format;
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
import com.github.tvbox.osc.bean.Track;
import com.github.tvbox.osc.player.Players;
import com.github.tvbox.osc.utils.Sniffer;
import com.orhanobut.logger.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class ExoUtil {

    public static final int MIN_TARGET_BUFFER_BYTES = 48 * 1024 * 1024;
    public static final int MAX_TARGET_BUFFER_BYTES = 256 * 1024 * 1024;
    public static final long MAX_DISK_CACHE_BYTES = 256L * 1024 * 1024;
    private static final int MIN_BUFFER_FLOOR_MS = 15_000;
    private static final int MIN_REBUFFER_FLOOR_MS = 3_000;
    private static final int LOW_PERF_STARTUP_BUFFER_CAP_MS = 2_500;
    private static final int LOW_PERF_REBUFFER_CAP_MS = 2_000;
    private static final int LOW_MEMORY_CLASS_MB = 128;
    private static final int MID_MEMORY_CLASS_MB = 192;
    private static final int HIGH_MEMORY_CLASS_MB = 256;
    private static final String[] CHINESE_AUDIO_KEYWORDS = {
            "zh", "chi", "zho", "cmn", "yue",
            "中文", "汉语", "国语", "国配", "普通话", "华语",
            "粤语", "粤配", "台配", "台语",
            "mandarin", "chinese", "cantonese"
    };

    /**
     * Exo 缓冲策略:
     *
     * - 起播/重缓冲门槛继续沿用用户设置的秒数
     * - 持续缓冲的总时长上限按设备内存限制在 45~90 秒
     * - 内存缓冲字节上限按设备内存分档限制在 48~256MB
     * - 中高内存设备优先按缓冲时长决策，避免高码率视频只预缓冲几秒就停
     */
    public static LoadControl buildLoadControl() {
        int playbackBufferMs = Setting.getBuffer() * 1000;
        if (isArmeabiV7aOnly()) playbackBufferMs = Math.min(playbackBufferMs, LOW_PERF_STARTUP_BUFFER_CAP_MS);
        int minBufferMs = Math.max(MIN_BUFFER_FLOOR_MS, playbackBufferMs * 2);
        int bufferForPlaybackAfterRebufferMs = Math.max(MIN_REBUFFER_FLOOR_MS, playbackBufferMs);
        if (isArmeabiV7aOnly()) bufferForPlaybackAfterRebufferMs = Math.min(bufferForPlaybackAfterRebufferMs, LOW_PERF_REBUFFER_CAP_MS);
        int memoryClassMb = getMemoryClassMb();
        int maxBufferMs = Math.max(getTieredMaxBufferMs(memoryClassMb), minBufferMs);
        int targetBufferBytes = getTargetBufferBytes();
        boolean prioritizeTime = shouldPrioritizeTimeOverSizeThresholds(memoryClassMb);
        Logger.i(
                "Exo load control: memClass=%dMB, playback=%dms, min=%dms, max=%dms, rebuffer=%dms, targetBytes=%dMB, prioritizeTime=%s",
                memoryClassMb,
                playbackBufferMs,
                minBufferMs,
                maxBufferMs,
                bufferForPlaybackAfterRebufferMs,
                targetBufferBytes / (1024 * 1024),
                prioritizeTime
        );
        return new DefaultLoadControl.Builder()
                .setBufferDurationsMs(
                        minBufferMs,
                        maxBufferMs,
                        playbackBufferMs,
                        bufferForPlaybackAfterRebufferMs
                )
                .setTargetBufferBytes(targetBufferBytes)
                .setPrioritizeTimeOverSizeThresholds(prioritizeTime)
                .build();
    }

    public static int getTargetBufferBytes() {
        int memoryClassMb = getMemoryClassMb();
        if (memoryClassMb <= LOW_MEMORY_CLASS_MB) return MIN_TARGET_BUFFER_BYTES;
        if (memoryClassMb <= MID_MEMORY_CLASS_MB) return 96 * 1024 * 1024;
        if (memoryClassMb <= HIGH_MEMORY_CLASS_MB) return 160 * 1024 * 1024;
        return MAX_TARGET_BUFFER_BYTES;
    }

    private static boolean shouldPrioritizeTimeOverSizeThresholds(int memoryClassMb) {
        return memoryClassMb > LOW_MEMORY_CLASS_MB;
    }

    private static int getMemoryClassMb() {
        ActivityManager manager = (ActivityManager) App.get().getSystemService(Context.ACTIVITY_SERVICE);
        return manager == null ? HIGH_MEMORY_CLASS_MB : manager.getMemoryClass();
    }

    private static int getTieredMaxBufferMs(int memoryClassMb) {
        if (memoryClassMb <= LOW_MEMORY_CLASS_MB) return (int) TimeUnit.SECONDS.toMillis(45);
        if (memoryClassMb <= MID_MEMORY_CLASS_MB) return (int) TimeUnit.MINUTES.toMillis(1);
        if (memoryClassMb <= HIGH_MEMORY_CLASS_MB) return (int) TimeUnit.SECONDS.toMillis(75);
        return (int) TimeUnit.SECONDS.toMillis(90);
    }

    public static TrackSelector buildTrackSelector() {
        DefaultTrackSelector trackSelector = new DefaultTrackSelector(App.get());
        trackSelector.setParameters(trackSelector.buildUponParameters()
                .setPreferredTextLanguage(Locale.getDefault().getISO3Language())
                .setForceHighestSupportedBitrate(!isArmeabiV7aOnly())
                .setTunnelingEnabled(Setting.isTunnel() && !isArmeabiV7aOnly()));
        return trackSelector;
    }

    public static boolean isArmeabiV7aOnly() {
        if (Build.SUPPORTED_64_BIT_ABIS.length > 0) return false;
        for (String abi : Build.SUPPORTED_ABIS) {
            if ("armeabi-v7a".equals(abi)) return true;
        }
        return false;
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

    public static Track findDefaultAudioTrack(Tracks tracks, int player, int preferredChannelCount) {
        int targetChannelCount = getTargetAudioChannelCount(tracks, preferredChannelCount);
        AudioCandidate bestChinese = null;
        AudioCandidate bestFallback = null;
        AudioCandidate bestOther = null;
        for (int i = 0; i < tracks.getGroups().size(); i++) {
            Tracks.Group group = tracks.getGroups().get(i);
            if (group.getType() != C.TRACK_TYPE_AUDIO) continue;
            for (int j = 0; j < group.length; j++) {
                Format format = group.getTrackFormat(j);
                Track track = new Track(C.TRACK_TYPE_AUDIO, "Audio");
                track.setPlayer(player);
                track.setGroup(i);
                track.setTrack(j);
                track.setSelected(true);
                AudioCandidate candidate = new AudioCandidate(track, format, i, j);
                if (isTargetChannelCandidate(format, targetChannelCount)) {
                    if (isChineseAudioTrack(format)) {
                        if (isBetterAudioCandidate(candidate, bestChinese)) bestChinese = candidate;
                    } else if (isBetterAudioCandidate(candidate, bestFallback)) {
                        bestFallback = candidate;
                    }
                } else if (isBetterAudioCandidate(candidate, bestOther)) {
                    bestOther = candidate;
                }
            }
        }
        AudioCandidate selected = bestChinese != null ? bestChinese : (bestFallback != null ? bestFallback : bestOther);
        return selected == null ? null : selected.track;
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

    private static boolean isChineseAudioTrack(Format format) {
        String text = ((format.language == null ? "" : format.language) + " " + (format.label == null ? "" : format.label))
                .toLowerCase(Locale.US);
        for (String keyword : CHINESE_AUDIO_KEYWORDS) if (text.contains(keyword)) return true;
        return false;
    }

    private static int getTargetAudioChannelCount(Tracks tracks, int preferredChannelCount) {
        int maxAllowedChannelCount = Integer.MIN_VALUE;
        int minAboveChannelCount = Integer.MAX_VALUE;
        for (int i = 0; i < tracks.getGroups().size(); i++) {
            Tracks.Group group = tracks.getGroups().get(i);
            if (group.getType() != C.TRACK_TYPE_AUDIO) continue;
            for (int j = 0; j < group.length; j++) {
                int channelCount = group.getTrackFormat(j).channelCount;
                if (channelCount == Format.NO_VALUE || channelCount <= 0) continue;
                if (channelCount <= preferredChannelCount) {
                    maxAllowedChannelCount = Math.max(maxAllowedChannelCount, channelCount);
                } else {
                    minAboveChannelCount = Math.min(minAboveChannelCount, channelCount);
                }
            }
        }
        if (maxAllowedChannelCount != Integer.MIN_VALUE) return maxAllowedChannelCount;
        if (minAboveChannelCount != Integer.MAX_VALUE) return minAboveChannelCount;
        return Format.NO_VALUE;
    }

    private static boolean isTargetChannelCandidate(Format format, int targetChannelCount) {
        if (targetChannelCount == Format.NO_VALUE) return true;
        return format.channelCount == targetChannelCount;
    }

    private static boolean isBetterAudioCandidate(AudioCandidate candidate, AudioCandidate currentBest) {
        if (candidate == null) return false;
        if (currentBest == null) return true;

        int candidateFlagScore = getTrackFlagScore(candidate.format);
        int currentFlagScore = getTrackFlagScore(currentBest.format);
        if (candidateFlagScore != currentFlagScore) return candidateFlagScore > currentFlagScore;

        return candidate.groupIndex < currentBest.groupIndex || (candidate.groupIndex == currentBest.groupIndex && candidate.trackIndex < currentBest.trackIndex);
    }

    private static int getTrackFlagScore(Format format) {
        int score = 0;
        if ((format.selectionFlags & C.SELECTION_FLAG_DEFAULT) != 0) score += 8;
        if ((format.roleFlags & C.ROLE_FLAG_MAIN) != 0) score += 4;
        if ((format.selectionFlags & C.SELECTION_FLAG_AUTOSELECT) != 0) score += 2;
        return score;
    }

    private static final class AudioCandidate {
        private final Track track;
        private final Format format;
        private final int groupIndex;
        private final int trackIndex;

        private AudioCandidate(Track track, Format format, int groupIndex, int trackIndex) {
            this.track = track;
            this.format = format;
            this.groupIndex = groupIndex;
            this.trackIndex = trackIndex;
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
