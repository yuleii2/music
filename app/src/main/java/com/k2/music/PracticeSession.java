package com.k2.music;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Completed offline practice record. */
public final class PracticeSession {
    public enum Type {
        TWO_CHORD_TRANSITION,
        PROGRESSION_LOOP,
        RANDOM_CHALLENGE
    }

    public final String id;
    public final long startedAtEpochMillis;
    public final Type type;
    public final List<String> chordSymbols;
    public final int bpm;
    public final int durationSeconds;
    public final int completionCount;
    public final int bestStreak;

    public PracticeSession(
            String id,
            long startedAtEpochMillis,
            Type type,
            List<String> chordSymbols,
            int bpm,
            int durationSeconds,
            int completionCount,
            int bestStreak
    ) {
        this.id = requireText(id, "Practice session id", 128);
        if (startedAtEpochMillis < 0) {
            throw new IllegalArgumentException("Practice date cannot be negative.");
        }
        this.type = Objects.requireNonNull(type, "type");
        if (chordSymbols == null || chordSymbols.isEmpty() || chordSymbols.size() > 256) {
            throw new IllegalArgumentException("Practice session must contain 1 to 256 chords.");
        }
        List<String> copy = new ArrayList<>(chordSymbols.size());
        for (String chord : chordSymbols) {
            copy.add(requireText(chord, "Chord symbol", 64));
        }
        if (bpm < 40 || bpm > 240) {
            throw new IllegalArgumentException("Practice BPM must be between 40 and 240.");
        }
        if (durationSeconds < 0 || durationSeconds > 86_400) {
            throw new IllegalArgumentException("Practice duration must be between 0 and 86400 seconds.");
        }
        if (completionCount < 0 || bestStreak < 0 || bestStreak > completionCount) {
            throw new IllegalArgumentException("Practice counts are inconsistent.");
        }
        this.startedAtEpochMillis = startedAtEpochMillis;
        this.chordSymbols = Collections.unmodifiableList(copy);
        this.bpm = bpm;
        this.durationSeconds = durationSeconds;
        this.completionCount = completionCount;
        this.bestStreak = bestStreak;
    }

    public static PracticeSession completed(
            long startedAtEpochMillis,
            Type type,
            List<String> chordSymbols,
            int bpm,
            int durationSeconds,
            int completionCount,
            int bestStreak
    ) {
        return new PracticeSession(
                UUID.randomUUID().toString(),
                startedAtEpochMillis,
                type,
                chordSymbols,
                bpm,
                durationSeconds,
                completionCount,
                bestStreak
        );
    }

    private static String requireText(String value, String label, int maxLength) {
        String result = value == null ? "" : value.trim();
        if (result.isEmpty()) {
            throw new IllegalArgumentException(label + " is required.");
        }
        if (result.length() > maxLength) {
            throw new IllegalArgumentException(label + " is too long.");
        }
        return result;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof PracticeSession)) {
            return false;
        }
        PracticeSession that = (PracticeSession) other;
        return startedAtEpochMillis == that.startedAtEpochMillis
                && bpm == that.bpm
                && durationSeconds == that.durationSeconds
                && completionCount == that.completionCount
                && bestStreak == that.bestStreak
                && id.equals(that.id)
                && type == that.type
                && chordSymbols.equals(that.chordSymbols);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, startedAtEpochMillis, type, chordSymbols, bpm, durationSeconds,
                completionCount, bestStreak);
    }
}
