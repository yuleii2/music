package com.k2.music;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Immutable, locally persistable chord progression. */
public final class ChordProgression {
    public final String id;
    public final String name;
    public final String keySignature;
    public final TimeSignature timeSignature;
    public final int bpm;
    public final boolean loop;
    public final List<ProgressionStep> steps;
    public final long createdAtEpochMillis;
    public final long updatedAtEpochMillis;
    public final String notes;

    public ChordProgression(
            String id,
            String name,
            String keySignature,
            TimeSignature timeSignature,
            int bpm,
            boolean loop,
            List<ProgressionStep> steps,
            long createdAtEpochMillis,
            long updatedAtEpochMillis,
            String notes
    ) {
        this.id = requireText(id, "Progression id", 128);
        this.name = requireText(name, "Progression name", 160);
        this.keySignature = optionalText(keySignature, 32);
        this.timeSignature = Objects.requireNonNull(timeSignature, "timeSignature");
        if (bpm < 40 || bpm > 240) {
            throw new IllegalArgumentException("Progression BPM must be between 40 and 240.");
        }
        if (createdAtEpochMillis < 0 || updatedAtEpochMillis < 0) {
            throw new IllegalArgumentException("Progression timestamps cannot be negative.");
        }
        this.bpm = bpm;
        this.loop = loop;
        this.steps = normalizeSteps(steps);
        this.createdAtEpochMillis = createdAtEpochMillis;
        this.updatedAtEpochMillis = updatedAtEpochMillis;
        this.notes = optionalText(notes, 16_384);
    }

    public static ChordProgression create(
            String name,
            String keySignature,
            TimeSignature timeSignature,
            int bpm,
            boolean loop,
            List<ProgressionStep> steps,
            String notes,
            long nowEpochMillis
    ) {
        return new ChordProgression(
                UUID.randomUUID().toString(),
                name,
                keySignature,
                timeSignature,
                bpm,
                loop,
                steps,
                nowEpochMillis,
                nowEpochMillis,
                notes
        );
    }

    public ChordProgression withName(String newName, long updatedAt) {
        return copy(id, newName, keySignature, timeSignature, bpm, loop, steps, createdAtEpochMillis, updatedAt, notes);
    }

    public ChordProgression withSteps(List<ProgressionStep> newSteps, long updatedAt) {
        return copy(id, name, keySignature, timeSignature, bpm, loop, newSteps, createdAtEpochMillis, updatedAt, notes);
    }

    public ChordProgression withPlayback(int newBpm, boolean newLoop, TimeSignature newTimeSignature, long updatedAt) {
        return copy(id, name, keySignature, newTimeSignature, newBpm, newLoop, steps, createdAtEpochMillis, updatedAt, notes);
    }

    public ChordProgression duplicate(String newId, String newName, long nowEpochMillis) {
        return copy(newId, newName, keySignature, timeSignature, bpm, loop, steps, nowEpochMillis, nowEpochMillis, notes);
    }

    ChordProgression withStoreTimestamps(long createdAt, long updatedAt) {
        return copy(id, name, keySignature, timeSignature, bpm, loop, steps, createdAt, updatedAt, notes);
    }

    private ChordProgression copy(
            String newId,
            String newName,
            String newKey,
            TimeSignature newTimeSignature,
            int newBpm,
            boolean newLoop,
            List<ProgressionStep> newSteps,
            long newCreatedAt,
            long newUpdatedAt,
            String newNotes
    ) {
        return new ChordProgression(
                newId,
                newName,
                newKey,
                newTimeSignature,
                newBpm,
                newLoop,
                newSteps,
                newCreatedAt,
                newUpdatedAt,
                newNotes
        );
    }

    private static List<ProgressionStep> normalizeSteps(List<ProgressionStep> source) {
        if (source == null) {
            throw new IllegalArgumentException("Progression steps are required.");
        }
        List<ProgressionStep> sorted = new ArrayList<>(source);
        if (sorted.size() > 512) {
            throw new IllegalArgumentException("A progression cannot contain more than 512 steps.");
        }
        Collections.sort(sorted, (left, right) -> Integer.compare(left.order, right.order));
        List<ProgressionStep> normalized = new ArrayList<>(sorted.size());
        for (int i = 0; i < sorted.size(); i++) {
            ProgressionStep step = Objects.requireNonNull(sorted.get(i), "Progression step");
            normalized.add(step.order == i ? step : step.withOrder(i));
        }
        return Collections.unmodifiableList(normalized);
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
        if (!(other instanceof ChordProgression)) {
            return false;
        }
        ChordProgression that = (ChordProgression) other;
        return bpm == that.bpm
                && loop == that.loop
                && createdAtEpochMillis == that.createdAtEpochMillis
                && updatedAtEpochMillis == that.updatedAtEpochMillis
                && id.equals(that.id)
                && name.equals(that.name)
                && keySignature.equals(that.keySignature)
                && timeSignature.equals(that.timeSignature)
                && steps.equals(that.steps)
                && notes.equals(that.notes);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, name, keySignature, timeSignature, bpm, loop, steps,
                createdAtEpochMillis, updatedAtEpochMillis, notes);
    }
}
