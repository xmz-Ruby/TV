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
    private final boolean enableVideoDurationToProgressUs;

    public NextRenderersFactory(@NonNull Context context, int decode) {
        super(context);
        lowPerfTv = ExoUtil.isLowPerformanceTv();
        // Mobile benefits on API 31+ where async MediaCodec is the default; old phones keep the safer sync path.
        enableVideoDurationToProgressUs = lowPerfTv || ExoUtil.isMobileMode();
        setEnableDecoderFallback(true);
        setExtensionRendererMode(Players.isHard(decode) ? EXTENSION_RENDERER_MODE_ON : EXTENSION_RENDERER_MODE_PREFER);
        if (enableVideoDurationToProgressUs) {
            setEnableMediaCodecVideoRendererDurationToProgressUs(true);
        }
        if (lowPerfTv) {
            experimentalSetLateThresholdToDropDecoderInputUs(LOW_PERF_TV_LATE_THRESHOLD_US);
        }
    }

    @Override
    protected void buildAudioRenderers(@NonNull Context context, int extensionRendererMode, @NonNull MediaCodecSelector mediaCodecSelector, boolean enableDecoderFallback, @NonNull AudioSink audioSink, @NonNull Handler eventHandler, @NonNull AudioRendererEventListener eventListener, @NonNull ArrayList<Renderer> out) {
        super.buildAudioRenderers(context, extensionRendererMode, mediaCodecSelector, enableDecoderFallback, audioSink, eventHandler, eventListener, out);
        int extensionRendererIndex = out.size();
        if (extensionRendererMode == EXTENSION_RENDERER_MODE_PREFER) {
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
        if (enableVideoDurationToProgressUs) {
            videoRendererBuilder.setEnableDurationToProgressUs(true);
        }
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
