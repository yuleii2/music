package com.k2.music;

import java.util.Objects;

/** One ordered chord event in a progression. */
public final class ProgressionStep {
    public final String chordSymbol;
    public final String voicingId;
    public final double beats;
    public final String strumPattern;
    public final int order;

    public ProgressionStep(String chordSymbol, String voicingId, double beats, String strumPattern, int order) {
        this.chordSymbol = requireText(chordSymbol, "Chord symbol", 64);
        this.voicingId = optionalText(voicingId, 256);
        if (Double.isNaN(beats) || Double.isInfinite(beats) || beats <= 0.0 || beats > 128.0) {
            throw new IllegalArgumentException("Step beats must be greater than zero and no more than 128.");
        }
        if (order < 0) {
            throw new IllegalArgumentException("Step order cannot be negative.");
        }
        this.beats = beats;
        this.strumPattern = optionalText(strumPattern, 512);
        this.order = order;
    }

    public ProgressionStep withOrder(int newOrder) {
        return new ProgressionStep(chordSymbol, voicingId, beats, strumPattern, newOrder);
    }

    public ProgressionStep withDuration(double newBeats) {
        return new ProgressionStep(chordSymbol, voicingId, newBeats, strumPattern, order);
    }

    public ProgressionStep withVoicing(String newVoicingId) {
        return new ProgressionStep(chordSymbol, newVoicingId, beats, strumPattern, order);
    }

    private static String requireText(String value, String label, int maxLength) {
        String result = optionalText(value, maxLength);
        if (result.isEmpty()) {
            throw new IllegalArgumentException(label + " is required.");
        }
        return result;
    }

    private static String optionalText(String value, int maxLength) {
        String result = value == null ? "" : value.trim();
        if (result.length() > maxLength) {
            throw new IllegalArgumentException("Text exceeds " + maxLength + " characters.");
        }
        return result;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof ProgressionStep)) {
            return false;
        }
        ProgressionStep that = (ProgressionStep) other;
        return Double.compare(beats, that.beats) == 0
                && order == that.order
                && chordSymbol.equals(that.chordSymbol)
                && voicingId.equals(that.voicingId)
                && strumPattern.equals(that.strumPattern);
    }

    @Override
    public int hashCode() {
        return Objects.hash(chordSymbol, voicingId, beats, strumPattern, order);
    }
}
