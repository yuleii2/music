package com.k2.music;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class Chord {
    public final String symbol;
    public final String displayName;
    public final String chineseName;
    public final String root;
    public final String qualityId;
    public final String quality;
    public final String bassNote;
    public final List<String> intervals;
    public final List<String> notes;
    public final List<String> aliases;
    public final List<String> extensions;
    public final List<String> alterations;
    public final List<String> omissions;
    public final List<String> additions;
    public final List<String> requiredIntervals;
    public final List<String> optionalIntervals;
    public final List<String> omittableIntervals;
    public final List<List<String>> requiredAnyOf;
    public final List<Integer> pitchClasses;
    public final String description;
    public final List<ChordShape> shapes;
    public final List<Voicing> voicings;

    public Chord(
            String symbol,
            String chineseName,
            String root,
            String quality,
            List<String> intervals,
            List<String> notes,
            List<String> aliases,
            String description,
            List<Voicing> voicings
    ) {
        this(symbol, chineseName, root, quality, "", intervals, notes, aliases, description, voicings);
    }

    public Chord(
            String symbol,
            String chineseName,
            String root,
            String quality,
            String bassNote,
            List<String> intervals,
            List<String> notes,
            List<String> aliases,
            String description,
            List<Voicing> voicings
    ) {
        this(symbol, chineseName, root, "", quality, bassNote, intervals, notes, aliases, description, Collections.emptyList(), voicings);
    }

    public Chord(
            String symbol,
            String chineseName,
            String root,
            String qualityId,
            String quality,
            String bassNote,
            List<String> intervals,
            List<String> notes,
            List<String> aliases,
            String description,
            List<ChordShape> shapes,
            List<Voicing> voicings
    ) {
        this(symbol, chineseName, root, qualityId, quality, bassNote, intervals, notes, aliases,
                description, shapes, voicings, Collections.emptyList(), Collections.emptyList(),
                Collections.emptyList(), Collections.emptyList(), intervals, Collections.emptyList(),
                Collections.emptyList(), Collections.emptyList());
    }

    public Chord(
            String symbol,
            String chineseName,
            String root,
            String qualityId,
            String quality,
            String bassNote,
            List<String> intervals,
            List<String> notes,
            List<String> aliases,
            String description,
            List<ChordShape> shapes,
            List<Voicing> voicings,
            List<String> extensions,
            List<String> alterations,
            List<String> omissions,
            List<String> additions,
            List<String> requiredIntervals,
            List<String> optionalIntervals,
            List<String> omittableIntervals,
            List<List<String>> requiredAnyOf
    ) {
        this.symbol = symbol;
        this.chineseName = chineseName;
        this.displayName = chineseName;
        this.root = root;
        this.qualityId = qualityId == null ? "" : qualityId;
        this.quality = quality;
        this.bassNote = bassNote;
        this.intervals = immutableCopy(intervals);
        this.notes = immutableCopy(notes);
        this.aliases = immutableCopy(aliases);
        this.extensions = immutableCopy(extensions);
        this.alterations = immutableCopy(alterations);
        this.omissions = immutableCopy(omissions);
        this.additions = immutableCopy(additions);
        this.requiredIntervals = immutableCopy(requiredIntervals);
        this.optionalIntervals = immutableCopy(optionalIntervals);
        this.omittableIntervals = immutableCopy(omittableIntervals);
        this.requiredAnyOf = immutableNestedCopy(requiredAnyOf);
        List<Integer> pitchClassValues = new ArrayList<>();
        for (String note : this.notes) {
            Integer pitchClass = NoteUtils.trySemitone(note);
            if (pitchClass != null && !pitchClassValues.contains(pitchClass)) {
                pitchClassValues.add(pitchClass);
            }
        }
        this.pitchClasses = Collections.unmodifiableList(pitchClassValues);
        this.description = description;
        this.shapes = immutableCopy(shapes);
        this.voicings = immutableCopy(voicings);
    }

    public int[] fallbackMidiNotes() {
        int[] result = new int[notes.size()];
        for (int i = 0; i < notes.size(); i++) {
            result[i] = NoteUtils.noteNameToMiddleMidi(notes.get(i));
        }
        return result;
    }

    private static <T> List<T> immutableCopy(List<T> source) {
        return Collections.unmodifiableList(new ArrayList<>(source == null ? Collections.emptyList() : source));
    }

    private static <T> List<List<T>> immutableNestedCopy(List<List<T>> source) {
        List<List<T>> copy = new ArrayList<>();
        for (List<T> values : source == null ? Collections.<List<T>>emptyList() : source) {
            copy.add(immutableCopy(values));
        }
        return Collections.unmodifiableList(copy);
    }
}
