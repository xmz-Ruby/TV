package com.github.tvbox.osc.player.exo;

import android.content.Context;
import android.os.Handler;

import androidx.annotation.NonNull;
import androidx.media3.common.util.Log;
import androidx.media3.exoplayer.DefaultRenderersFactory;
import androidx.media3.exoplayer.Renderer;
import androidx.media3.exoplayer.audio.AudioRendererEventListener;
import androidx.media3.exoplayer.audio.AudioSink;
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

    public NextRenderersFactory(@NonNull Context context, int decode) {
        super(context);
        setEnableDecoderFallback(true);
        setExtensionRendererMode(Players.isHard(decode) ? EXTENSION_RENDERER_MODE_ON : EXTENSION_RENDERER_MODE_PREFER);
        if (ExoUtil.isLowPerformanceTv()) {
            setEnableMediaCodecVideoRendererDurationToProgressUs(true);
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
        MediaCodecVideoRenderer videoRenderer = new MediaCodecVideoRenderer.Builder(context)
                .setCodecAdapterFactory(getCodecAdapterFactory())
                .setMediaCodecSelector(mediaCodecSelector)
                .setAllowedJoiningTimeMs(allowedVideoJoiningTimeMs)
                .setEnableDecoderFallback(enableDecoderFallback)
                .setEventHandler(eventHandler)
                .setEventListener(eventListener)
                .setMaxDroppedFramesToNotify(DROPPED_FRAME_NOTIFY_THRESHOLD)
                .build();
        out.add(videoRenderer);
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
}
