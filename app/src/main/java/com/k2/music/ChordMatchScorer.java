package com.k2.music;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
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
        LinkedHashSet<Integer> missing = difference(expected, actual);
        LinkedHashSet<Integer> required = pitchesForIntervals(root, chord.requiredIntervals);
        if (required.isEmpty()) {
            required.addAll(expected);
        }
        LinkedHashSet<Integer> missingRequired = difference(required, actual);
        int missingRequiredGroups = 0;
        for (List<String> group : chord.requiredAnyOf) {
            Set<Integer> groupPitches = pitchesForIntervals(root, group);
            boolean groupPresent = false;
            for (Integer pitch : groupPitches) {
                if (actual.contains(pitch)) {
                    groupPresent = true;
                    break;
                }
            }
            if (!groupPresent) {
                missingRequiredGroups++;
            }
        }
        LinkedHashSet<Integer> missingOptional = new LinkedHashSet<>(missing);
        missingOptional.removeAll(missingRequired);
        LinkedHashSet<Integer> extra = new LinkedHashSet<>(actual);
        extra.removeAll(expected);
        boolean inversion = bassPitchClass >= 0 && bassPitchClass != root;

        int value = 100;
        for (Integer pitch : missingRequired) {
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
        value -= missingRequiredGroups * 18;
        value -= missingOptional.size() * 2;
        value -= extra.size() * 13;
        if (inversion) {
            value -= 3;
        }
        value = Math.max(0, Math.min(100, value));

        ChordMatch.MatchType type;
        boolean requiredSatisfied = missingRequired.isEmpty() && missingRequiredGroups == 0;
        if (missing.isEmpty() && extra.isEmpty()) {
            type = inversion
                    ? ChordMatch.MatchType.EXACT_INVERSION
                    : hasDuplicateTones ? ChordMatch.MatchType.DUPLICATED_TONES : ChordMatch.MatchType.EXACT;
        } else if (requiredSatisfied && extra.isEmpty() && missing.size() == 1
                && Math.floorMod(missing.iterator().next() - root, 12) == 7) {
            type = ChordMatch.MatchType.OMITTED_FIFTH;
        } else if (requiredSatisfied && extra.isEmpty() && !missingOptional.isEmpty()) {
            type = ChordMatch.MatchType.INCOMPLETE_EXTENSION;
        } else if (extra.isEmpty() && missingRequired.size() == 1 && missingRequired.contains(root)) {
            type = ChordMatch.MatchType.OMITTED_ROOT;
        } else if (!extra.isEmpty() && requiredSatisfied && missingOptional.isEmpty()) {
            type = ChordMatch.MatchType.EXTRA_TONES;
        } else {
            type = ChordMatch.MatchType.SIMILAR;
        }
        return new Score(value, type, missing, extra, inversion);
    }

    private static LinkedHashSet<Integer> pitchesForIntervals(int root, List<String> intervals) {
        LinkedHashSet<Integer> pitches = new LinkedHashSet<>();
        for (String interval : intervals == null ? new ArrayList<String>() : intervals) {
            pitches.add(Math.floorMod(root + NoteUtils.intervalToSemitones(interval), 12));
        }
        return pitches;
    }

    private static LinkedHashSet<Integer> difference(Set<Integer> expected, Set<Integer> actual) {
        LinkedHashSet<Integer> result = new LinkedHashSet<>(expected);
        result.removeAll(actual);
        return result;
    }
}
