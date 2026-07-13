package com.k2.music;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** One deterministic reverse-identification candidate. */
public final class ChordMatch {
    public enum MatchType {
        EXACT,
        EXACT_INVERSION,
        OMITTED_FIFTH,
        OMITTED_ROOT,
        DUPLICATED_TONES,
        EXTRA_TONES,
        SIMILAR
    }

    public final Chord chord;
    public final String symbol;
    public final String chineseName;
    public final int score;
    public final MatchType matchType;
    public final List<String> chordNotes;
    public final List<String> actualNotes;
    public final List<String> missingNotes;
    public final List<String> extraNotes;
    public final boolean inversion;
    public final String bassNote;

    public ChordMatch(
            Chord chord,
            String symbol,
            int score,
            MatchType matchType,
            List<String> chordNotes,
            List<String> actualNotes,
            List<String> missingNotes,
            List<String> extraNotes,
            boolean inversion,
            String bassNote
    ) {
        this.chord = chord;
        this.symbol = symbol;
        this.chineseName = chord == null ? "" : chord.chineseName;
        this.score = Math.max(0, Math.min(100, score));
        this.matchType = matchType;
        this.chordNotes = immutable(chordNotes);
        this.actualNotes = immutable(actualNotes);
        this.missingNotes = immutable(missingNotes);
        this.extraNotes = immutable(extraNotes);
        this.inversion = inversion;
        this.bassNote = bassNote == null ? "" : bassNote;
    }

    public String matchLabel() {
        switch (matchType) {
            case EXACT: return "完全匹配";
            case EXACT_INVERSION: return "完全匹配（转位）";
            case OMITTED_FIFTH: return "省略五音";
            case OMITTED_ROOT: return "省略根音";
            case DUPLICATED_TONES: return "完全匹配（包含重复音）";
            case EXTRA_TONES: return "包含额外音";
            default: return "相似和弦";
        }
    }

    private static List<String> immutable(List<String> source) {
        return Collections.unmodifiableList(new ArrayList<>(source == null ? Collections.emptyList() : source));
    }
}
