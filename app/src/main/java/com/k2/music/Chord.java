package com.k2.music;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class Chord {
    public final String symbol;
    public final String chineseName;
    public final String root;
    public final String qualityId;
    public final String quality;
    public final String bassNote;
    public final List<String> intervals;
    public final List<String> notes;
    public final List<String> aliases;
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
        this.symbol = symbol;
        this.chineseName = chineseName;
        this.root = root;
        this.qualityId = qualityId == null ? "" : qualityId;
        this.quality = quality;
        this.bassNote = bassNote;
        this.intervals = immutableCopy(intervals);
        this.notes = immutableCopy(notes);
        this.aliases = immutableCopy(aliases);
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
        return Collections.unmodifiableList(new ArrayList<>(source));
    }
}
