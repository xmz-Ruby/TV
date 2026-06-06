package com.github.tvbox.osc.player.exo;

import androidx.media3.common.C;
import androidx.media3.common.audio.AudioProcessor;
import androidx.media3.common.audio.BaseAudioProcessor;

import java.nio.ByteBuffer;

/**
 * Downmixes multichannel PCM to stereo with dialogue gain compensation and a soft limiter.
 */
public final class StereoDownmixAudioProcessor extends BaseAudioProcessor {

    private static final int MAX_INPUT_CHANNELS = 8;
    private static final float CENTER_WEIGHT = 1.0f;
    private static final float SURROUND_WEIGHT = 0.5f;
    private static final float LFE_WEIGHT = 0.25f;
    private static final float POST_GAIN = 1.5f;
    private static final float LIMITER_THRESHOLD = 0.92f;

    private final float[] frame = new float[MAX_INPUT_CHANNELS];

    @Override
    protected AudioFormat onConfigure(AudioFormat inputAudioFormat) throws AudioProcessor.UnhandledAudioFormatException {
        if (inputAudioFormat.sampleRate <= 0
                || inputAudioFormat.channelCount <= 0
                || (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT && inputAudioFormat.encoding != C.ENCODING_PCM_FLOAT)
                || inputAudioFormat.channelCount > MAX_INPUT_CHANNELS) {
            throw new AudioProcessor.UnhandledAudioFormatException(inputAudioFormat);
        }
        if (inputAudioFormat.channelCount <= 2) return AudioFormat.NOT_SET;
        return new AudioFormat(inputAudioFormat.sampleRate, 2, inputAudioFormat.encoding);
    }

    @Override
    public void queueInput(ByteBuffer inputBuffer) {
        int frames = inputBuffer.remaining() / inputAudioFormat.bytesPerFrame;
        ByteBuffer outputBuffer = replaceOutputBuffer(frames * outputAudioFormat.bytesPerFrame);
        boolean floatPcm = inputAudioFormat.encoding == C.ENCODING_PCM_FLOAT;
        for (int i = 0; i < frames; i++) {
            readFrame(inputBuffer, floatPcm, inputAudioFormat.channelCount);
            writeSample(outputBuffer, mixLeft(inputAudioFormat.channelCount), floatPcm);
            writeSample(outputBuffer, mixRight(inputAudioFormat.channelCount), floatPcm);
        }
        outputBuffer.flip();
    }

    private void readFrame(ByteBuffer inputBuffer, boolean floatPcm, int channelCount) {
        for (int channel = 0; channel < channelCount; channel++) {
            frame[channel] = floatPcm ? inputBuffer.getFloat() : pcm16ToFloat(inputBuffer.getShort());
        }
    }

    private float mixLeft(int channelCount) {
        float left = frame[0];
        switch (channelCount) {
            case 3: // FL, FR, FC
                left += frame[2] * CENTER_WEIGHT;
                break;
            case 4: // FL, FR, BL, BR
                left += frame[2] * SURROUND_WEIGHT;
                break;
            case 5: // FL, FR, FC, BL, BR
                left += frame[2] * CENTER_WEIGHT + frame[3] * SURROUND_WEIGHT;
                break;
            case 6: // FL, FR, FC, LFE, BL, BR
                left += frame[2] * CENTER_WEIGHT + frame[3] * LFE_WEIGHT + frame[4] * SURROUND_WEIGHT;
                break;
            case 7: // FL, FR, FC, LFE, BC, SL, SR
                left += frame[2] * CENTER_WEIGHT + frame[3] * LFE_WEIGHT + frame[4] * SURROUND_WEIGHT + frame[5] * SURROUND_WEIGHT;
                break;
            default: // 8: FL, FR, FC, LFE, BL, BR, SL, SR
                left += frame[2] * CENTER_WEIGHT + frame[3] * LFE_WEIGHT + frame[4] * SURROUND_WEIGHT + frame[6] * SURROUND_WEIGHT;
                break;
        }
        return softLimit(left * POST_GAIN);
    }

    private float mixRight(int channelCount) {
        float right = frame[1];
        switch (channelCount) {
            case 3: // FL, FR, FC
                right += frame[2] * CENTER_WEIGHT;
                break;
            case 4: // FL, FR, BL, BR
                right += frame[3] * SURROUND_WEIGHT;
                break;
            case 5: // FL, FR, FC, BL, BR
                right += frame[2] * CENTER_WEIGHT + frame[4] * SURROUND_WEIGHT;
                break;
            case 6: // FL, FR, FC, LFE, BL, BR
                right += frame[2] * CENTER_WEIGHT + frame[3] * LFE_WEIGHT + frame[5] * SURROUND_WEIGHT;
                break;
            case 7: // FL, FR, FC, LFE, BC, SL, SR
                right += frame[2] * CENTER_WEIGHT + frame[3] * LFE_WEIGHT + frame[4] * SURROUND_WEIGHT + frame[6] * SURROUND_WEIGHT;
                break;
            default: // 8: FL, FR, FC, LFE, BL, BR, SL, SR
                right += frame[2] * CENTER_WEIGHT + frame[3] * LFE_WEIGHT + frame[5] * SURROUND_WEIGHT + frame[7] * SURROUND_WEIGHT;
                break;
        }
        return softLimit(right * POST_GAIN);
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
}
