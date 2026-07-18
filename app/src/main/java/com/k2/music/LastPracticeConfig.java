package com.k2.music;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/** Complete, restorable configuration for the most recently started practice. */
public final class LastPracticeConfig {
    public final PracticeSession.Type mode;
    public final List<String> chordSymbols;
    public final int durationSeconds;
    public final int bpm;
    public final String timeSignature;
    public final PracticeSession.SwitchMode switchMode;
    public final boolean accentFirstBeat;
    public final boolean allowBarre;
    public final int maxFret;
    public final String sourceProgressionId;
    public final boolean useProgressionRhythm;

    public LastPracticeConfig(
            PracticeSession.Type mode,
            List<String> chordSymbols,
            int durationSeconds,
            int bpm,
            String timeSignature,
            PracticeSession.SwitchMode switchMode,
            boolean accentFirstBeat,
            boolean allowBarre,
            int maxFret,
            String sourceProgressionId,
            boolean useProgressionRhythm
    ) {
        this.mode = Objects.requireNonNull(mode, "mode");
        if (chordSymbols == null || chordSymbols.size() < 2 || chordSymbols.size() > 256) {
            throw new IllegalArgumentException("Last practice needs between 2 and 256 chords.");
        }
        List<String> normalized = new ArrayList<>(chordSymbols.size());
        for (String symbol : chordSymbols) {
            normalized.add(requireText(symbol, "Chord symbol", 64));
        }
        this.chordSymbols = Collections.unmodifiableList(normalized);
        if (durationSeconds < 5 || durationSeconds > 86_400) {
            throw new IllegalArgumentException("Practice duration is outside the supported range.");
        }
        this.durationSeconds = durationSeconds;
        if (bpm < 40 || bpm > 240) {
            throw new IllegalArgumentException("Practice BPM must be between 40 and 240.");
        }
        this.bpm = bpm;
        TimeSignature signature = TimeSignature.parse(timeSignature);
        if (!signature.isSupportedByMetronome()) {
            throw new IllegalArgumentException("Unsupported practice time signature.");
        }
        this.timeSignature = signature.toString();
        this.switchMode = Objects.requireNonNull(switchMode, "switchMode");
        this.accentFirstBeat = accentFirstBeat;
        this.allowBarre = allowBarre;
        if (maxFret < 1 || maxFret > 24) {
            throw new IllegalArgumentException("Maximum fret must be between 1 and 24.");
        }
        this.maxFret = maxFret;
        this.sourceProgressionId = optionalText(sourceProgressionId, 128);
        this.useProgressionRhythm = useProgressionRhythm && !this.sourceProgressionId.isEmpty();
    }

    private static String requireText(String value, String label, int maxLength) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isEmpty()) throw new IllegalArgumentException(label + " is required.");
        if (normalized.length() > maxLength) throw new IllegalArgumentException(label + " is too long.");
        return normalized;
    }

    private static String optionalText(String value, int maxLength) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.length() > maxLength) throw new IllegalArgumentException("Optional text is too long.");
        return normalized;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof LastPracticeConfig)) return false;
        LastPracticeConfig that = (LastPracticeConfig) other;
        return durationSeconds == that.durationSeconds
                && bpm == that.bpm
                && accentFirstBeat == that.accentFirstBeat
                && allowBarre == that.allowBarre
                && maxFret == that.maxFret
                && useProgressionRhythm == that.useProgressionRhythm
                && mode == that.mode
                && chordSymbols.equals(that.chordSymbols)
                && timeSignature.equals(that.timeSignature)
                && switchMode == that.switchMode
                && sourceProgressionId.equals(that.sourceProgressionId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(mode, chordSymbols, durationSeconds, bpm, timeSignature, switchMode,
                accentFirstBeat, allowBarre, maxFret, sourceProgressionId, useProgressionRhythm);
    }
}
