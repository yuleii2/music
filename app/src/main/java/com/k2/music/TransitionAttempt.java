package com.k2.music;

import java.util.Objects;
import java.util.UUID;

/** One user-confirmed result for a concrete, directional chord transition. */
public final class TransitionAttempt {
    public final String id;
    public final String sessionId;
    public final long timestampEpochMillis;
    public final String fromChord;
    public final String toChord;
    public final String fromVoicingId;
    public final String toVoicingId;
    public final int bpm;
    public final String timeSignature;
    public final PracticeSession.SwitchMode switchMode;
    public final boolean success;
    public final Long confirmationOffsetMillis;
    public final PracticeSession.Type practiceMode;

    public TransitionAttempt(
            String id,
            String sessionId,
            long timestampEpochMillis,
            String fromChord,
            String toChord,
            String fromVoicingId,
            String toVoicingId,
            int bpm,
            String timeSignature,
            PracticeSession.SwitchMode switchMode,
            boolean success,
            Long confirmationOffsetMillis,
            PracticeSession.Type practiceMode
    ) {
        this.id = requireText(id, "Attempt id", 128);
        this.sessionId = requireText(sessionId, "Session id", 128);
        if (timestampEpochMillis < 0L) {
            throw new IllegalArgumentException("Attempt timestamp cannot be negative.");
        }
        this.timestampEpochMillis = timestampEpochMillis;
        this.fromChord = requireText(fromChord, "Source chord", 64);
        this.toChord = requireText(toChord, "Target chord", 64);
        this.fromVoicingId = optionalText(fromVoicingId, 512);
        this.toVoicingId = optionalText(toVoicingId, 512);
        if (bpm < 40 || bpm > 240) {
            throw new IllegalArgumentException("Attempt BPM must be between 40 and 240.");
        }
        this.bpm = bpm;
        this.timeSignature = requireText(timeSignature, "Time signature", 16);
        this.switchMode = Objects.requireNonNull(switchMode, "switchMode");
        this.success = success;
        if (confirmationOffsetMillis != null && Math.abs(confirmationOffsetMillis) > 86_400_000L) {
            throw new IllegalArgumentException("Confirmation offset is outside the supported range.");
        }
        this.confirmationOffsetMillis = confirmationOffsetMillis;
        this.practiceMode = Objects.requireNonNull(practiceMode, "practiceMode");
    }

    public static TransitionAttempt create(
            String sessionId,
            long timestampEpochMillis,
            String fromChord,
            String toChord,
            String fromVoicingId,
            String toVoicingId,
            int bpm,
            String timeSignature,
            PracticeSession.SwitchMode switchMode,
            boolean success,
            Long confirmationOffsetMillis,
            PracticeSession.Type practiceMode
    ) {
        return new TransitionAttempt(
                UUID.randomUUID().toString(),
                sessionId,
                timestampEpochMillis,
                fromChord,
                toChord,
                fromVoicingId,
                toVoicingId,
                bpm,
                timeSignature,
                switchMode,
                success,
                confirmationOffsetMillis,
                practiceMode
        );
    }

    private static String requireText(String value, String label, int maxLength) {
        String result = value == null ? "" : value.trim();
        if (result.isEmpty()) throw new IllegalArgumentException(label + " is required.");
        if (result.length() > maxLength) throw new IllegalArgumentException(label + " is too long.");
        return result;
    }

    private static String optionalText(String value, int maxLength) {
        String result = value == null ? "" : value.trim();
        if (result.length() > maxLength) throw new IllegalArgumentException("Optional text is too long.");
        return result;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof TransitionAttempt)) return false;
        TransitionAttempt that = (TransitionAttempt) other;
        return timestampEpochMillis == that.timestampEpochMillis
                && bpm == that.bpm
                && success == that.success
                && id.equals(that.id)
                && sessionId.equals(that.sessionId)
                && fromChord.equals(that.fromChord)
                && toChord.equals(that.toChord)
                && fromVoicingId.equals(that.fromVoicingId)
                && toVoicingId.equals(that.toVoicingId)
                && timeSignature.equals(that.timeSignature)
                && switchMode == that.switchMode
                && Objects.equals(confirmationOffsetMillis, that.confirmationOffsetMillis)
                && practiceMode == that.practiceMode;
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, sessionId, timestampEpochMillis, fromChord, toChord,
                fromVoicingId, toVoicingId, bpm, timeSignature, switchMode, success,
                confirmationOffsetMillis, practiceMode);
    }
}
