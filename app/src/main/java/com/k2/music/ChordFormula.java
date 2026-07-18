package com.k2.music;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

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
    /** Intervals that every valid voicing must contain. */
    public final List<String> requiredIntervals;
    /** Theoretical chord tones that a practical guitar voicing may omit. */
    public final List<String> optionalIntervals;
    /** Explicitly omittable intervals; kept separate for UI and validation. */
    public final List<String> omittableIntervals;
    /** Each nested group requires at least one member (used by 7alt). */
    public final List<List<String>> requiredAnyOf;
    public final List<String> extensions;
    public final List<String> alterations;
    public final List<String> additions;

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
        this(id, suffix, chineseName, englishName, intervals, aliases, description, category,
                difficulty, null, null, null, null);
    }

    public ChordFormula(
            String id,
            String suffix,
            String chineseName,
            String englishName,
            List<String> intervals,
            List<String> aliases,
            String description,
            String category,
            int difficulty,
            List<String> requiredIntervals,
            List<String> optionalIntervals,
            List<String> omittableIntervals,
            List<List<String>> requiredAnyOf
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

        IntervalRules defaults = deriveRules(this.intervals);
        this.requiredIntervals = immutableStrings(
                requiredIntervals == null ? defaults.required : requiredIntervals
        );
        this.optionalIntervals = immutableStrings(
                optionalIntervals == null ? defaults.optional : optionalIntervals
        );
        this.omittableIntervals = immutableStrings(
                omittableIntervals == null ? this.optionalIntervals : omittableIntervals
        );
        this.requiredAnyOf = immutableGroups(requiredAnyOf);
        validateRules();
        this.extensions = deriveExtensions(this.intervals);
        this.alterations = deriveAlterations(this.intervals);
        this.additions = deriveAdditions(this.id, this.intervals);
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
                suffix,
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
                quality.symbolSuffix,
                quality.chineseName,
                quality.displayName,
                labels,
                Collections.emptyList(),
                quality.description,
                quality.category,
                quality.difficulty
        );
    }

    public boolean isRequired(String interval) {
        return requiredIntervals.contains(interval);
    }

    public boolean isOmittable(String interval) {
        return omittableIntervals.contains(interval);
    }

    private void validateRules() {
        Set<String> intervalSet = new LinkedHashSet<>(intervals);
        validateSubset("requiredIntervals", requiredIntervals, intervalSet);
        validateSubset("optionalIntervals", optionalIntervals, intervalSet);
        validateSubset("omittableIntervals", omittableIntervals, intervalSet);
        Set<String> overlap = new LinkedHashSet<>(requiredIntervals);
        overlap.retainAll(optionalIntervals);
        if (!overlap.isEmpty()) {
            throw new IllegalArgumentException("Chord formula " + id
                    + " has intervals that are both required and optional: " + overlap);
        }
        Set<String> classified = new LinkedHashSet<>(requiredIntervals);
        classified.addAll(optionalIntervals);
        if (!classified.equals(intervalSet)) {
            Set<String> missing = new LinkedHashSet<>(intervalSet);
            missing.removeAll(classified);
            throw new IllegalArgumentException("Chord formula " + id
                    + " has unclassified intervals: " + missing);
        }
        if (!optionalIntervals.containsAll(omittableIntervals)) {
            throw new IllegalArgumentException("Chord formula " + id
                    + " may only mark optional intervals as omittable.");
        }
        for (List<String> group : requiredAnyOf) {
            if (group.isEmpty()) {
                throw new IllegalArgumentException("Chord formula " + id + " has an empty requiredAnyOf group.");
            }
            validateSubset("requiredAnyOf", group, intervalSet);
        }
    }

    private static void validateSubset(String label, List<String> values, Set<String> intervals) {
        for (String value : values) {
            if (!intervals.contains(value)) {
                throw new IllegalArgumentException(label + " contains unknown interval " + value + ".");
            }
        }
    }

    private static IntervalRules deriveRules(List<String> intervals) {
        int highestDegree = 1;
        for (String interval : intervals) {
            highestDegree = Math.max(highestDegree, intervalDegree(interval));
        }
        List<String> required = new ArrayList<>();
        List<String> optional = new ArrayList<>();
        for (String interval : intervals) {
            int degree = intervalDegree(interval);
            boolean mayOmit = ("5".equals(interval) && intervals.size() > 2)
                    || (degree == 9 && highestDegree > 9)
                    || (degree == 11 && highestDegree > 11);
            (mayOmit ? optional : required).add(interval);
        }
        return new IntervalRules(required, optional);
    }

    private static List<String> deriveExtensions(List<String> intervals) {
        List<String> result = new ArrayList<>();
        for (String interval : intervals) {
            if (intervalDegree(interval) >= 6) {
                result.add(interval);
            }
        }
        return Collections.unmodifiableList(result);
    }

    private static List<String> deriveAlterations(List<String> intervals) {
        List<String> result = new ArrayList<>();
        for (String interval : intervals) {
            int degree = intervalDegree(interval);
            boolean alteredDegree = degree == 5 || degree == 9 || degree == 11 || degree == 13;
            if (alteredDegree && (interval.startsWith("b") || interval.startsWith("#"))) {
                result.add(interval);
            }
        }
        return Collections.unmodifiableList(result);
    }

    private static List<String> deriveAdditions(String id, List<String> intervals) {
        if (id == null || !id.toLowerCase(Locale.US).contains("add")) {
            return Collections.emptyList();
        }
        List<String> result = new ArrayList<>();
        for (String interval : intervals) {
            if (intervalDegree(interval) > 7) {
                result.add(interval);
            }
        }
        return Collections.unmodifiableList(result);
    }

    public static int intervalDegree(String interval) {
        if (interval == null) {
            throw new IllegalArgumentException("Interval must not be null.");
        }
        String digits = interval.replace("#", "").replace("b", "").replace("B", "")
                .replace("♯", "").replace("♭", "").trim();
        try {
            return Integer.parseInt(digits);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Invalid interval: " + interval, exception);
        }
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

    private static List<List<String>> immutableGroups(List<List<String>> values) {
        if (values == null || values.isEmpty()) {
            return Collections.emptyList();
        }
        List<List<String>> copy = new ArrayList<>();
        for (List<String> value : values) {
            copy.add(immutableStrings(value == null ? Collections.emptyList() : value));
        }
        return Collections.unmodifiableList(copy);
    }

    private static final class IntervalRules {
        final List<String> required;
        final List<String> optional;

        IntervalRules(List<String> required, List<String> optional) {
            this.required = required;
            this.optional = optional;
        }
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
