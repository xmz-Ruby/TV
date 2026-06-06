package com.github.tvbox.osc.player.exo;

import androidx.media3.common.C;
import androidx.media3.common.audio.AudioProcessor;
import androidx.media3.common.audio.BaseAudioProcessor;

import java.nio.ByteBuffer;

/**
 * Smooth real-time loudness normalization for decoded PCM.
 */
public final class LoudnessNormalizerAudioProcessor extends BaseAudioProcessor {

    private static final float TARGET_RMS = 0.12f;
    private static final float GATE_RMS = 0.003f;
    private static final float MIN_GAIN = 0.5f;
    private static final float MAX_GAIN = 4.0f;
    private static final float RMS_WINDOW_SECONDS = 1.8f;
    private static final float GAIN_RAISE_SECONDS = 1.2f;
    private static final float GAIN_LOWER_SECONDS = 0.08f;
    private static final float LIMITER_THRESHOLD = 0.96f;

    private float currentGain = 1.0f;
    private float smoothedRms = -1.0f;

    @Override
    protected AudioFormat onConfigure(AudioFormat inputAudioFormat) throws AudioProcessor.UnhandledAudioFormatException {
        if (inputAudioFormat.sampleRate <= 0
                || inputAudioFormat.channelCount <= 0
                || (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT && inputAudioFormat.encoding != C.ENCODING_PCM_FLOAT)) {
            throw new AudioProcessor.UnhandledAudioFormatException(inputAudioFormat);
        }
        return inputAudioFormat;
    }

    @Override
    public void queueInput(ByteBuffer inputBuffer) {
        int frames = inputBuffer.remaining() / inputAudioFormat.bytesPerFrame;
        ByteBuffer outputBuffer = replaceOutputBuffer(frames * outputAudioFormat.bytesPerFrame);
        if (frames == 0) {
            outputBuffer.flip();
            return;
        }

        boolean floatPcm = inputAudioFormat.encoding == C.ENCODING_PCM_FLOAT;
        float blockRms = calculateRms(inputBuffer, frames, floatPcm);
        float targetGain = calculateTargetGain(blockRms, frames);
        float nextGain = smoothGain(targetGain, frames);
        float gainStep = (nextGain - currentGain) / frames;
        float gain = currentGain;

        for (int frame = 0; frame < frames; frame++) {
            gain += gainStep;
            for (int channel = 0; channel < inputAudioFormat.channelCount; channel++) {
                float sample = floatPcm ? inputBuffer.getFloat() : pcm16ToFloat(inputBuffer.getShort());
                writeSample(outputBuffer, softLimit(sample * gain), floatPcm);
            }
        }
        currentGain = nextGain;
        outputBuffer.flip();
    }

    @Override
    protected void onFlush(AudioProcessor.StreamMetadata streamMetadata) {
        resetState();
    }

    @Override
    protected void onReset() {
        resetState();
    }

    private float calculateRms(ByteBuffer inputBuffer, int frames, boolean floatPcm) {
        ByteBuffer scanBuffer = inputBuffer.duplicate().order(inputBuffer.order());
        int samples = frames * inputAudioFormat.channelCount;
        double sumSquares = 0.0;
        for (int i = 0; i < samples; i++) {
            float sample = floatPcm ? scanBuffer.getFloat() : pcm16ToFloat(scanBuffer.getShort());
            sumSquares += sample * sample;
        }
        return (float) Math.sqrt(sumSquares / samples);
    }

    private float calculateTargetGain(float blockRms, int frames) {
        if (blockRms <= GATE_RMS) return currentGain;
        float alpha = smoothingAlpha(frames, RMS_WINDOW_SECONDS);
        smoothedRms = smoothedRms < 0 ? blockRms : smoothedRms + alpha * (blockRms - smoothedRms);
        return constrain(TARGET_RMS / smoothedRms, MIN_GAIN, MAX_GAIN);
    }

    private float smoothGain(float targetGain, int frames) {
        float seconds = targetGain > currentGain ? GAIN_RAISE_SECONDS : GAIN_LOWER_SECONDS;
        float alpha = smoothingAlpha(frames, seconds);
        return currentGain + alpha * (targetGain - currentGain);
    }

    private float smoothingAlpha(int frames, float seconds) {
        return 1.0f - (float) Math.exp(-frames / (inputAudioFormat.sampleRate * seconds));
    }

    private void writeSample(ByteBuffer outputBuffer, float sample, boolean floatPcm) {
        if (floatPcm) {
            outputBuffer.putFloat(sample);
        } else {
            outputBuffer.putShort(floatToPcm16(sample));
        }
    }

    private float pcm16ToFloat(short sample) {
        return sample / (float) (sample < 0 ? -Short.MIN_VALUE : Short.MAX_VALUE);
    }

    private short floatToPcm16(float sample) {
        int pcm = Math.round(sample * (sample < 0 ? -Short.MIN_VALUE : Short.MAX_VALUE));
        if (pcm > Short.MAX_VALUE) return Short.MAX_VALUE;
        if (pcm < Short.MIN_VALUE) return Short.MIN_VALUE;
        return (short) pcm;
    }

    private float softLimit(float sample) {
        float sign = Math.signum(sample);
        float abs = Math.abs(sample);
        if (abs <= LIMITER_THRESHOLD) return sample;
        float range = 1.0f - LIMITER_THRESHOLD;
        float limited = LIMITER_THRESHOLD + range * (float) Math.tanh((abs - LIMITER_THRESHOLD) / range);
        return sign * Math.min(limited, 1.0f);
    }

    private float constrain(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    private void resetState() {
        currentGain = 1.0f;
        smoothedRms = -1.0f;
    }
}
