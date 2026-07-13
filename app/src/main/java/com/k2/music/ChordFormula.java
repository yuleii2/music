package com.k2.music;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A chord-quality definition loaded from {@code assets/chords/chord_formulas.json}.
 * It describes pitch relationships only; guitar-specific shapes live in the
 * voicing repository.
 */
public final class ChordFormula {
    public final String id;
    public final String suffix;
    public final String chineseName;
    public final String englishName;
    public final List<String> intervals;
    public final List<String> aliases;
    public final String description;
    public final String category;
    public final int difficulty;

    public ChordFormula(
            String id,
            String suffix,
            String chineseName,
            String englishName,
            List<String> intervals,
            List<String> aliases,
            String description,
            String category,
            int difficulty
    ) {
        this.id = requireText(id, "id");
        this.suffix = suffix == null ? "" : suffix;
        this.chineseName = emptyIfNull(chineseName);
        this.englishName = emptyIfNull(englishName);
        if (intervals == null || intervals.isEmpty()) {
            throw new IllegalArgumentException("Chord formula " + id + " must contain intervals.");
        }
        this.intervals = immutableStrings(intervals);
        this.aliases = immutableStrings(aliases == null ? Collections.emptyList() : aliases);
        this.description = emptyIfNull(description);
        this.category = emptyIfNull(category);
        this.difficulty = Math.max(1, Math.min(5, difficulty));
    }

    public int[] semitoneIntervals() {
        int[] values = new int[intervals.size()];
        for (int index = 0; index < intervals.size(); index++) {
            values[index] = NoteUtils.intervalToSemitones(intervals.get(index));
        }
        return values;
    }

    public ChordQuality toChordQuality() {
        return new ChordQuality(
                id,
                englishName,
                chineseName,
                semitoneIntervals(),
                intervals.toArray(new String[0]),
                category,
                difficulty,
                description
        );
    }

    public static ChordFormula fromQuality(ChordQuality quality) {
        List<String> labels = new ArrayList<>();
        Collections.addAll(labels, quality.intervalLabels);
        return new ChordFormula(
                quality.id,
                "maj".equals(quality.id) ? "" : quality.id,
                quality.chineseName,
                quality.displayName,
                labels,
                Collections.emptyList(),
                quality.description,
                quality.category,
                quality.difficulty
        );
    }

    private static List<String> immutableStrings(List<String> values) {
        List<String> copy = new ArrayList<>();
        for (String value : values) {
            if (value != null && !value.trim().isEmpty()) {
                copy.add(value.trim());
            }
        }
        return Collections.unmodifiableList(copy);
    }

    private static String requireText(String value, String field) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException("Chord formula " + field + " must not be empty.");
        }
        return value.trim();
    }

    private static String emptyIfNull(String value) {
        return value == null ? "" : value;
    }
}
