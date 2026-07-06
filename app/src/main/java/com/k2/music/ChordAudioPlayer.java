package com.k2.music;

import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioTrack;

public final class ChordAudioPlayer {
    private static final int SAMPLE_RATE = 44100;
    private static final double TWO_PI = Math.PI * 2.0;

    private AudioTrack currentTrack;

    public boolean play(int[] midiNotes) {
        stop();
        if (midiNotes == null || midiNotes.length == 0) {
            return false;
        }
        try {
            short[] samples = renderChord(midiNotes, 1.8);
            AudioTrack track = new AudioTrack.Builder()
                    .setAudioAttributes(new AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                            .build())
                    .setAudioFormat(new AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(SAMPLE_RATE)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build())
                    .setBufferSizeInBytes(samples.length * 2)
                    .setTransferMode(AudioTrack.MODE_STATIC)
                    .build();
            track.write(samples, 0, samples.length);
            track.setNotificationMarkerPosition(samples.length);
            track.setPlaybackPositionUpdateListener(new AudioTrack.OnPlaybackPositionUpdateListener() {
                @Override
                public void onMarkerReached(AudioTrack audioTrack) {
                    audioTrack.release();
                    if (currentTrack == audioTrack) {
                        currentTrack = null;
                    }
                }

                @Override
                public void onPeriodicNotification(AudioTrack audioTrack) {
                }
            });
            currentTrack = track;
            track.play();
            return true;
        } catch (RuntimeException ignored) {
            stop();
            return false;
        }
    }

    public void stop() {
        if (currentTrack == null) {
            return;
        }
        try {
            currentTrack.stop();
        } catch (IllegalStateException ignored) {
            // The track may have already finished playback.
        }
        currentTrack.release();
        currentTrack = null;
    }

    private static short[] renderChord(int[] midiNotes, double seconds) {
        int totalSamples = (int) (SAMPLE_RATE * seconds);
        short[] samples = new short[totalSamples];
        double gain = 0.62 / Math.max(1, midiNotes.length);
        for (int i = 0; i < totalSamples; i++) {
            double t = i / (double) SAMPLE_RATE;
            double value = 0.0;
            for (int noteIndex = 0; noteIndex < midiNotes.length; noteIndex++) {
                int midi = midiNotes[noteIndex];
                if (midi <= 0) {
                    continue;
                }
                double start = noteIndex * 0.018;
                if (t < start) {
                    continue;
                }
                double localT = t - start;
                double freq = 440.0 * Math.pow(2.0, (midi - 69) / 12.0);
                double envelope = Math.exp(-2.25 * localT);
                double attack = Math.min(1.0, localT / 0.018);
                double tone = Math.sin(TWO_PI * freq * localT)
                        + 0.35 * Math.sin(TWO_PI * freq * 2.01 * localT)
                        + 0.16 * Math.sin(TWO_PI * freq * 3.02 * localT);
                value += tone * envelope * attack * gain;
            }
            value = Math.max(-1.0, Math.min(1.0, value));
            samples[i] = (short) (value * Short.MAX_VALUE);
        }
        return samples;
    }
}
