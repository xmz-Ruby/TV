package com.github.tvbox.osc.player.exo;

import android.content.Context;
import android.os.Build;
import android.os.Handler;

import androidx.annotation.NonNull;
import androidx.media3.common.util.Log;
import androidx.media3.exoplayer.DefaultRenderersFactory;
import androidx.media3.exoplayer.Renderer;
import androidx.media3.exoplayer.audio.AudioRendererEventListener;
import androidx.media3.exoplayer.audio.AudioSink;
import androidx.media3.exoplayer.audio.DefaultAudioSink;
import androidx.media3.exoplayer.mediacodec.DefaultMediaCodecAdapterFactory;
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector;
import androidx.media3.exoplayer.video.MediaCodecVideoRenderer;
import androidx.media3.exoplayer.video.VideoRendererEventListener;

import com.github.tvbox.osc.player.Players;

import java.util.ArrayList;

import io.github.anilbeesetti.nextlib.media3ext.ffdecoder.FfmpegAudioRenderer;
import io.github.anilbeesetti.nextlib.media3ext.ffdecoder.FfmpegVideoRenderer;

public class NextRenderersFactory extends DefaultRenderersFactory {

    private static final String TAG = NextRenderersFactory.class.getSimpleName();
    private static final int DROPPED_FRAME_NOTIFY_THRESHOLD = 5;
    private static final long LOW_PERF_TV_LATE_THRESHOLD_US = 10_000L;

    private final boolean lowPerfTv;
    private final boolean forceStereo;

    public NextRenderersFactory(@NonNull Context context, int decode, boolean forceStereo) {
        super(context);
        lowPerfTv = ExoUtil.isLowPerformanceTv();
        this.forceStereo = forceStereo;
        setEnableDecoderFallback(true);
        setExtensionRendererMode(Players.isHard(decode) ? EXTENSION_RENDERER_MODE_ON : EXTENSION_RENDERER_MODE_PREFER);
        if (lowPerfTv) {
            experimentalSetLateThresholdToDropDecoderInputUs(LOW_PERF_TV_LATE_THRESHOLD_US);
        }
    }

    /**
     * 「强制立体声」开关：
     *
     * <p>关闭时（默认）走 {@code super}，行为完全不变，环绕声/直通零回归。
     *
     * <p>开启时返回一个无 {@link Context} 的 {@link DefaultAudioSink.Builder}——其音频能力为
     * {@code DEFAULT_AUDIO_CAPABILITIES}（仅立体声 PCM、禁直通），从而强制 AC3/EAC3/DTS 等走解码
     * （{@link #buildAudioRenderers} 在 forceStereo 时已让 FFmpeg 音频渲染器优先于 MediaCodec，确保解出真正 PCM）；
     * 并在处理链最前挂一个
     * {@link ExoUtil#buildStereoDownmixProcessor() 下混处理器}，{@link DefaultAudioSink.DefaultAudioProcessorChain}
     * 会自动在其后补回 SilenceSkipping 与 Sonic 处理器，倍速播放不丢失。
     */
    @SuppressWarnings("deprecation") // 无 context 的 Builder() 已弃用，但正是借其默认能力（禁直通）来强制下混
    @Override
    protected AudioSink buildAudioSink(@NonNull Context context, boolean enableFloatOutput, boolean enableAudioOutputPlaybackParams) {
        if (!forceStereo) return super.buildAudioSink(context, enableFloatOutput, enableAudioOutputPlaybackParams);
        return new DefaultAudioSink.Builder()
                .setAudioProcessorChain(new DefaultAudioSink.DefaultAudioProcessorChain(ExoUtil.buildStereoDownmixProcessor()))
                .setEnableFloatOutput(enableFloatOutput)
                .setEnableAudioOutputPlaybackParameters(enableAudioOutputPlaybackParams)
                .build();
    }

    @Override
    protected void buildAudioRenderers(@NonNull Context context, int extensionRendererMode, @NonNull MediaCodecSelector mediaCodecSelector, boolean enableDecoderFallback, @NonNull AudioSink audioSink, @NonNull Handler eventHandler, @NonNull AudioRendererEventListener eventListener, @NonNull ArrayList<Renderer> out) {
        super.buildAudioRenderers(context, extensionRendererMode, mediaCodecSelector, enableDecoderFallback, audioSink, eventHandler, eventListener, out);
        int extensionRendererIndex = out.size();
        // forceStereo 时让 ffmpeg 音频渲染器排在 MediaCodecAudioRenderer 之前（取得优先）：
        // 部分盒子的 AC3/EAC3/DTS「MediaCodec 解码器」实为直通(IEC61937)用途，禁直通后仍会抢轨并吐出非 PCM →
        // 喂给立体声 AudioTrack 即静音。让 ffmpeg 软解这些编码为真正 PCM，再由 sink 的下混链路转立体声。视频不受影响。
        if (extensionRendererMode == EXTENSION_RENDERER_MODE_PREFER || forceStereo) {
            extensionRendererIndex--;
        }
        try {
            Renderer renderer = new FfmpegAudioRenderer(eventHandler, eventListener, audioSink);
            out.add(extensionRendererIndex++, renderer);
            Log.i(TAG, "Loaded FfmpegAudioRenderer.");
        } catch (Exception e) {
            throw new RuntimeException("Error instantiating Ffmpeg extension", e);
        }
    }

    @Override
    protected void buildVideoRenderers(@NonNull Context context, int extensionRendererMode, @NonNull MediaCodecSelector mediaCodecSelector, boolean enableDecoderFallback, @NonNull Handler eventHandler, @NonNull VideoRendererEventListener eventListener, long allowedVideoJoiningTimeMs, @NonNull ArrayList<Renderer> out) {
        MediaCodecVideoRenderer.Builder videoRendererBuilder = new MediaCodecVideoRenderer.Builder(context)
                .setCodecAdapterFactory(buildCodecAdapterFactory(context))
                .setMediaCodecSelector(mediaCodecSelector)
                .setAllowedJoiningTimeMs(allowedVideoJoiningTimeMs)
                .setEnableDecoderFallback(enableDecoderFallback)
                .setEventHandler(eventHandler)
                .setEventListener(eventListener)
                .setMaxDroppedFramesToNotify(DROPPED_FRAME_NOTIFY_THRESHOLD);
        if (lowPerfTv) {
            videoRendererBuilder.experimentalSetLateThresholdToDropDecoderInputUs(LOW_PERF_TV_LATE_THRESHOLD_US);
        }
        out.add(videoRendererBuilder.build());
        if (extensionRendererMode == EXTENSION_RENDERER_MODE_ON) return;
        int extensionRendererIndex = out.size();
        try {
            Renderer renderer = new FfmpegVideoRenderer(allowedVideoJoiningTimeMs, eventHandler, eventListener, DROPPED_FRAME_NOTIFY_THRESHOLD);
            out.add(extensionRendererIndex++, renderer);
            Log.i(TAG, "Loaded FfmpegVideoRenderer.");
        } catch (Exception e) {
            throw new RuntimeException("Error instantiating Ffmpeg extension", e);
        }
    }

    private DefaultMediaCodecAdapterFactory buildCodecAdapterFactory(Context context) {
        DefaultMediaCodecAdapterFactory factory = new DefaultMediaCodecAdapterFactory(context);
        if (lowPerfTv && Build.VERSION.SDK_INT >= 23) {
            factory.forceEnableAsynchronous();
        }
        return factory;
    }
}
