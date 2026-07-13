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

    public enum SwitchMode {
        EACH_BEAT,
        EACH_MEASURE
    }

    public final String id;
    public final long startedAtEpochMillis;
    public final long endedAtEpochMillis;
    public final Type type;
    public final List<String> chordSymbols;
    public final int bpm;
    public final String timeSignature;
    public final SwitchMode switchMode;
    public final int plannedDurationSeconds;
    public final int actualDurationSeconds;
    public final int attemptCount;
    public final int successCount;
    public final int failureCount;
    public final int durationSeconds;
    public final int completionCount;
    public final int bestStreak;
    public final boolean legacy;
    public final String sourceProgressionId;
    public final boolean useProgressionRhythm;

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
        this(
                id,
                startedAtEpochMillis,
                Math.max(startedAtEpochMillis, startedAtEpochMillis + durationSeconds * 1_000L),
                type,
                chordSymbols,
                bpm,
                "4/4",
                SwitchMode.EACH_MEASURE,
                durationSeconds,
                durationSeconds,
                0,
                0,
                0,
                bestStreak,
                completionCount,
                true,
                "",
                false
        );
    }

    public PracticeSession(
            String id,
            long startedAtEpochMillis,
            long endedAtEpochMillis,
            Type type,
            List<String> chordSymbols,
            int bpm,
            String timeSignature,
            SwitchMode switchMode,
            int plannedDurationSeconds,
            int actualDurationSeconds,
            int attemptCount,
            int successCount,
            int failureCount,
            int bestStreak,
            int legacyCompletionCount,
            boolean legacy
    ) {
        this(id, startedAtEpochMillis, endedAtEpochMillis, type, chordSymbols, bpm, timeSignature,
                switchMode, plannedDurationSeconds, actualDurationSeconds, attemptCount, successCount,
                failureCount, bestStreak, legacyCompletionCount, legacy, "", false);
    }

    public PracticeSession(
            String id,
            long startedAtEpochMillis,
            long endedAtEpochMillis,
            Type type,
            List<String> chordSymbols,
            int bpm,
            String timeSignature,
            SwitchMode switchMode,
            int plannedDurationSeconds,
            int actualDurationSeconds,
            int attemptCount,
            int successCount,
            int failureCount,
            int bestStreak,
            int legacyCompletionCount,
            boolean legacy,
            String sourceProgressionId,
            boolean useProgressionRhythm
    ) {
        this.id = requireText(id, "Practice session id", 128);
        if (startedAtEpochMillis < 0) {
            throw new IllegalArgumentException("Practice date cannot be negative.");
        }
        if (endedAtEpochMillis < startedAtEpochMillis) {
            throw new IllegalArgumentException("Practice end cannot precede its start.");
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
        if (plannedDurationSeconds < 0 || plannedDurationSeconds > 86_400
                || actualDurationSeconds < 0 || actualDurationSeconds > 86_400) {
            throw new IllegalArgumentException("Practice duration must be between 0 and 86400 seconds.");
        }
        if (attemptCount < 0 || successCount < 0 || failureCount < 0
                || attemptCount != successCount + failureCount) {
            throw new IllegalArgumentException("Practice attempt counts are inconsistent.");
        }
        if (legacyCompletionCount < 0 || bestStreak < 0
                || (!legacy && bestStreak > successCount)
                || (legacy && bestStreak > legacyCompletionCount)) {
            throw new IllegalArgumentException("Practice counts are inconsistent.");
        }
        this.startedAtEpochMillis = startedAtEpochMillis;
        this.endedAtEpochMillis = endedAtEpochMillis;
        this.chordSymbols = Collections.unmodifiableList(copy);
        this.bpm = bpm;
        this.timeSignature = requireText(timeSignature, "Time signature", 16);
        this.switchMode = Objects.requireNonNull(switchMode, "switchMode");
        this.plannedDurationSeconds = plannedDurationSeconds;
        this.actualDurationSeconds = actualDurationSeconds;
        this.attemptCount = attemptCount;
        this.successCount = successCount;
        this.failureCount = failureCount;
        this.durationSeconds = actualDurationSeconds;
        this.completionCount = legacy ? legacyCompletionCount : successCount;
        this.bestStreak = bestStreak;
        this.legacy = legacy;
        this.sourceProgressionId = sourceProgressionId == null ? "" : sourceProgressionId.trim();
        if (this.sourceProgressionId.length() > 128) {
            throw new IllegalArgumentException("Source progression id is too long.");
        }
        this.useProgressionRhythm = useProgressionRhythm && !this.sourceProgressionId.isEmpty();
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

    public static PracticeSession recorded(
            String id,
            long startedAtEpochMillis,
            long endedAtEpochMillis,
            Type type,
            List<String> chordSymbols,
            int bpm,
            String timeSignature,
            SwitchMode switchMode,
            int plannedDurationSeconds,
            int actualDurationSeconds,
            int attemptCount,
            int successCount,
            int failureCount,
            int bestStreak
    ) {
        return recorded(id, startedAtEpochMillis, endedAtEpochMillis, type, chordSymbols, bpm,
                timeSignature, switchMode, plannedDurationSeconds, actualDurationSeconds, attemptCount,
                successCount, failureCount, bestStreak, "", false);
    }

    public static PracticeSession recorded(
            String id,
            long startedAtEpochMillis,
            long endedAtEpochMillis,
            Type type,
            List<String> chordSymbols,
            int bpm,
            String timeSignature,
            SwitchMode switchMode,
            int plannedDurationSeconds,
            int actualDurationSeconds,
            int attemptCount,
            int successCount,
            int failureCount,
            int bestStreak,
            String sourceProgressionId,
            boolean useProgressionRhythm
    ) {
        return new PracticeSession(
                id,
                startedAtEpochMillis,
                endedAtEpochMillis,
                type,
                chordSymbols,
                bpm,
                timeSignature,
                switchMode,
                plannedDurationSeconds,
                actualDurationSeconds,
                attemptCount,
                successCount,
                failureCount,
                bestStreak,
                0,
                false,
                sourceProgressionId,
                useProgressionRhythm
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
                && endedAtEpochMillis == that.endedAtEpochMillis
                && bpm == that.bpm
                && plannedDurationSeconds == that.plannedDurationSeconds
                && actualDurationSeconds == that.actualDurationSeconds
                && attemptCount == that.attemptCount
                && successCount == that.successCount
                && failureCount == that.failureCount
                && durationSeconds == that.durationSeconds
                && completionCount == that.completionCount
                && bestStreak == that.bestStreak
                && legacy == that.legacy
                && useProgressionRhythm == that.useProgressionRhythm
                && id.equals(that.id)
                && type == that.type
                && timeSignature.equals(that.timeSignature)
                && switchMode == that.switchMode
                && sourceProgressionId.equals(that.sourceProgressionId)
                && chordSymbols.equals(that.chordSymbols);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, startedAtEpochMillis, endedAtEpochMillis, type, chordSymbols, bpm,
                timeSignature, switchMode, plannedDurationSeconds, actualDurationSeconds, attemptCount,
                successCount, failureCount, durationSeconds, completionCount, bestStreak, legacy,
                sourceProgressionId, useProgressionRhythm);
    }
}
