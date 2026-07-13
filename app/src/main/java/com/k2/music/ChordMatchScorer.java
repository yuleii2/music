package com.k2.music;

import java.util.LinkedHashSet;
import java.util.Set;

/** Scores pitch-class evidence; duplicate sounding notes never count as errors. */
public final class ChordMatchScorer {
    public static final class Score {
        public final int value;
        public final ChordMatch.MatchType type;
        public final Set<Integer> missing;
        public final Set<Integer> extra;
        public final boolean inversion;

        Score(int value, ChordMatch.MatchType type, Set<Integer> missing, Set<Integer> extra, boolean inversion) {
            this.value = value;
            this.type = type;
            this.missing = missing;
            this.extra = extra;
            this.inversion = inversion;
        }
    }

    public Score score(Chord chord, Set<Integer> actual, int bassPitchClass) {
        return score(chord, actual, bassPitchClass, false);
    }

    public Score score(Chord chord, Set<Integer> actual, int bassPitchClass, boolean hasDuplicateTones) {
        Set<Integer> expected = MusicTheoryUtils.pitchClasses(chord.notes);
        int root = MusicTheoryUtils.noteToPitchClass(chord.root);
        LinkedHashSet<Integer> missing = new LinkedHashSet<>(expected);
        missing.removeAll(actual);
        LinkedHashSet<Integer> extra = new LinkedHashSet<>(actual);
        extra.removeAll(expected);
        boolean inversion = bassPitchClass >= 0 && bassPitchClass != root;

        int value = 100;
        for (Integer pitch : missing) {
            int relative = Math.floorMod(pitch - root, 12);
            if (relative == 0) {
                value -= 31;
            } else if (relative == 3 || relative == 4) {
                value -= 27;
            } else if (relative == 7) {
                value -= 8;
            } else {
                value -= 17;
            }
        }
        value -= extra.size() * 13;
        if (inversion) {
            value -= 3;
        }
        if (actual.contains(root)) {
            value += 2;
        }
        value = Math.max(0, Math.min(100, value));

        ChordMatch.MatchType type;
        if (missing.isEmpty() && extra.isEmpty()) {
            type = inversion
                    ? ChordMatch.MatchType.EXACT_INVERSION
                    : hasDuplicateTones ? ChordMatch.MatchType.DUPLICATED_TONES : ChordMatch.MatchType.EXACT;
        } else if (extra.isEmpty() && missing.size() == 1
                && Math.floorMod(missing.iterator().next() - root, 12) == 7) {
            type = ChordMatch.MatchType.OMITTED_FIFTH;
        } else if (extra.isEmpty() && missing.size() == 1 && missing.contains(root)) {
            type = ChordMatch.MatchType.OMITTED_ROOT;
        } else if (!extra.isEmpty() && missing.isEmpty()) {
            type = ChordMatch.MatchType.EXTRA_TONES;
        } else {
            type = ChordMatch.MatchType.SIMILAR;
        }
        return new Score(value, type, missing, extra, inversion);
    }
}
