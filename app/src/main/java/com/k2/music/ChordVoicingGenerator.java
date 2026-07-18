package com.k2.music;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Deterministically derives one playable standard-tuning guitar voicing from a
 * chord formula. Bundled hand-authored shapes remain preferred; this generator
 * fills formula/root gaps and supports arbitrary slash-bass queries offline.
 */
public final class ChordVoicingGenerator {
    private static final int[] STANDARD_TUNING_MIDI = {40, 45, 50, 55, 59, 64};
    private static final int MAX_FRET = 12;
    private static final int MAX_POSITIVE_FRET_SPAN = 4;
    private static final int MAX_DISTINCT_POSITIVE_FRETS = 4;

    public ChordShape generate(String root, String slashBass, ChordFormula formula) {
        if (formula == null) {
            throw new IllegalArgumentException("Chord formula must not be null.");
        }
        String writtenRoot = NoteUtils.normalizeNoteName(root);
        String canonicalRoot = NoteUtils.canonicalPitchClass(root);
        String writtenBass = slashBass == null ? "" : NoteUtils.normalizeNoteName(slashBass);
        String canonicalBass = writtenBass.isEmpty()
                ? canonicalRoot
                : NoteUtils.canonicalPitchClass(writtenBass);
        if (canonicalRoot.isEmpty() || canonicalBass.isEmpty()) {
            throw new IllegalArgumentException("Unsupported chord root or slash bass.");
        }

        Search search = new Search(canonicalRoot, canonicalBass, formula);
        Candidate best = search.run();
        return best == null ? null : createShape(writtenRoot, writtenBass, formula, best);
    }

    private static ChordShape createShape(
            String root,
            String slashBass,
            ChordFormula formula,
            Candidate candidate
    ) {
        int[] fingers = assignFingers(candidate.frets);
        int minPositive = Integer.MAX_VALUE;
        int maxPositive = 0;
        Map<Integer, Integer> fretCounts = new LinkedHashMap<>();
        for (int fret : candidate.frets) {
            if (fret > 0) {
                minPositive = Math.min(minPositive, fret);
                maxPositive = Math.max(maxPositive, fret);
                fretCounts.put(fret, fretCounts.containsKey(fret) ? fretCounts.get(fret) + 1 : 1);
            }
        }
        int startFret = maxPositive <= 5 || minPositive == Integer.MAX_VALUE ? 1 : minPositive;
        int visibleFrets = Math.max(4, maxPositive - startFret + 1);
        boolean barre = false;
        for (Integer count : fretCounts.values()) {
            if (count >= 2) {
                barre = true;
                break;
            }
        }
        String symbol = root + formula.suffix + (slashBass.isEmpty() ? "" : "/" + slashBass);
        String id = "generated-runtime-" + root.toLowerCase(Locale.US).replace("#", "s")
                + "-" + formula.id.toLowerCase(Locale.US).replace("#", "sharp")
                + (slashBass.isEmpty() ? "" : "-over-" + slashBass.toLowerCase(Locale.US).replace("#", "s"));
        List<String> tags = new ArrayList<>();
        tags.add("generated");
        tags.add("rule-based");
        if (!formula.category.isEmpty()) {
            tags.add(formula.category);
        }
        if (!slashBass.isEmpty()) {
            tags.add("slash");
        }
        String omissionText = candidate.omittedIntervals.isEmpty()
                ? "未省略公式音"
                : "按规则省略 " + String.join("、", candidate.omittedIntervals);
        return new ChordShape(
                id,
                symbol + " 规则生成按法",
                root,
                formula.id,
                slashBass,
                candidate.frets,
                fingers,
                startFret,
                visibleFrets,
                formula.difficulty,
                slashBass.isEmpty() ? "generated" : "slash",
                true,
                false,
                barre,
                "标准调弦下由和弦公式生成；最低音为 "
                        + (slashBass.isEmpty() ? root : slashBass) + "；" + omissionText + "。",
                tags,
                candidate.omittedIntervals
        );
    }

    private static int[] assignFingers(int[] frets) {
        Set<Integer> distinct = new LinkedHashSet<>();
        for (int fret : frets) {
            if (fret > 0) {
                distinct.add(fret);
            }
        }
        List<Integer> sorted = new ArrayList<>(distinct);
        Collections.sort(sorted);
        Map<Integer, Integer> fingerByFret = new LinkedHashMap<>();
        for (int index = 0; index < sorted.size(); index++) {
            fingerByFret.put(sorted.get(index), Math.min(4, index + 1));
        }
        int[] fingers = new int[6];
        for (int index = 0; index < frets.length; index++) {
            if (frets[index] > 0) {
                fingers[index] = fingerByFret.get(frets[index]);
            }
        }
        return fingers;
    }

    private static final class Search {
        private final int rootPitch;
        private final int bassPitch;
        private final ChordFormula formula;
        private final Set<Integer> allowedPitches = new LinkedHashSet<>();
        private final Set<Integer> requiredPitches = new LinkedHashSet<>();
        private final List<Set<Integer>> requiredAnyPitches = new ArrayList<>();
        private Candidate best;

        Search(String root, String bass, ChordFormula formula) {
            this.rootPitch = NoteUtils.semitone(root);
            this.bassPitch = NoteUtils.semitone(bass);
            this.formula = formula;
            for (String interval : formula.intervals) {
                allowedPitches.add(pitchFor(interval));
            }
            allowedPitches.add(bassPitch);
            for (String interval : formula.requiredIntervals) {
                requiredPitches.add(pitchFor(interval));
            }
            for (List<String> group : formula.requiredAnyOf) {
                Set<Integer> pitches = new LinkedHashSet<>();
                for (String interval : group) {
                    pitches.add(pitchFor(interval));
                }
                requiredAnyPitches.add(pitches);
            }
        }

        Candidate run() {
            for (int bassString = 0; bassString <= 2; bassString++) {
                for (int bassFret = 0; bassFret <= MAX_FRET; bassFret++) {
                    int bassMidi = STANDARD_TUNING_MIDI[bassString] + bassFret;
                    if (Math.floorMod(bassMidi, 12) != bassPitch) {
                        continue;
                    }
                    int[] frets = {-1, -1, -1, -1, -1, -1};
                    frets[bassString] = bassFret;
                    LinkedHashSet<Integer> actual = new LinkedHashSet<>();
                    actual.add(bassPitch);
                    LinkedHashSet<Integer> positiveFrets = new LinkedHashSet<>();
                    if (bassFret > 0) {
                        positiveFrets.add(bassFret);
                    }
                    searchString(bassString + 1, bassMidi, frets, actual, positiveFrets, 1);
                }
            }
            return best;
        }

        private void searchString(
                int stringIndex,
                int bassMidi,
                int[] frets,
                LinkedHashSet<Integer> actual,
                LinkedHashSet<Integer> positiveFrets,
                int soundingCount
        ) {
            if (stringIndex == 6) {
                consider(frets, actual, soundingCount);
                return;
            }

            int remainingStrings = 6 - stringIndex;
            int missingRequired = countMissing(requiredPitches, actual);
            int missingGroups = countMissingGroups(actual);
            if (Math.max(missingRequired, missingGroups) > remainingStrings) {
                return;
            }

            frets[stringIndex] = -1;
            searchString(stringIndex + 1, bassMidi, frets, actual, positiveFrets, soundingCount);

            for (int fret = 0; fret <= MAX_FRET; fret++) {
                int midi = STANDARD_TUNING_MIDI[stringIndex] + fret;
                int pitch = Math.floorMod(midi, 12);
                if (midi < bassMidi || !allowedPitches.contains(pitch)) {
                    continue;
                }
                boolean addedFret = fret > 0 && positiveFrets.add(fret);
                if (!validFretShape(positiveFrets)) {
                    if (addedFret) {
                        positiveFrets.remove(fret);
                    }
                    continue;
                }
                boolean addedPitch = actual.add(pitch);
                frets[stringIndex] = fret;
                searchString(stringIndex + 1, bassMidi, frets, actual, positiveFrets, soundingCount + 1);
                if (addedPitch) {
                    actual.remove(pitch);
                }
                if (addedFret) {
                    positiveFrets.remove(fret);
                }
            }
            frets[stringIndex] = -1;
        }

        private void consider(int[] frets, Set<Integer> actual, int soundingCount) {
            int minimumSounding = formula.intervals.size() <= 2 ? 2 : 3;
            if (soundingCount < minimumSounding || !actual.containsAll(requiredPitches)
                    || countMissingGroups(actual) > 0) {
                return;
            }
            List<String> omitted = new ArrayList<>();
            for (String interval : formula.intervals) {
                if (!actual.contains(pitchFor(interval))) {
                    if (!formula.isOmittable(interval)) {
                        return;
                    }
                    omitted.add(interval);
                }
            }
            int score = score(frets, actual, omitted, soundingCount);
            Candidate candidate = new Candidate(Arrays.copyOf(frets, frets.length), omitted, score);
            if (best == null || Candidate.ORDER.compare(candidate, best) < 0) {
                best = candidate;
            }
        }

        private int score(int[] frets, Set<Integer> actual, List<String> omitted, int soundingCount) {
            int minPositive = Integer.MAX_VALUE;
            int maxPositive = 0;
            int muted = 0;
            int fretTotal = 0;
            for (int fret : frets) {
                if (fret < 0) {
                    muted++;
                } else if (fret > 0) {
                    minPositive = Math.min(minPositive, fret);
                    maxPositive = Math.max(maxPositive, fret);
                    fretTotal += fret;
                }
            }
            int span = minPositive == Integer.MAX_VALUE ? 0 : maxPositive - minPositive;
            int duplicatePenalty = Math.max(0, soundingCount - actual.size());
            return omitted.size() * 1000 + span * 45 + maxPositive * 10 + fretTotal
                    + muted * 8 + duplicatePenalty * 3;
        }

        private int pitchFor(String interval) {
            return Math.floorMod(rootPitch + NoteUtils.intervalToSemitones(interval), 12);
        }

        private static int countMissing(Set<Integer> expected, Set<Integer> actual) {
            int count = 0;
            for (Integer pitch : expected) {
                if (!actual.contains(pitch)) {
                    count++;
                }
            }
            return count;
        }

        private int countMissingGroups(Set<Integer> actual) {
            int count = 0;
            for (Set<Integer> group : requiredAnyPitches) {
                boolean present = false;
                for (Integer pitch : group) {
                    if (actual.contains(pitch)) {
                        present = true;
                        break;
                    }
                }
                if (!present) {
                    count++;
                }
            }
            return count;
        }

        private static boolean validFretShape(Set<Integer> positiveFrets) {
            if (positiveFrets.size() > MAX_DISTINCT_POSITIVE_FRETS) {
                return false;
            }
            int min = Integer.MAX_VALUE;
            int max = 0;
            for (Integer fret : positiveFrets) {
                min = Math.min(min, fret);
                max = Math.max(max, fret);
            }
            return positiveFrets.isEmpty() || max - min <= MAX_POSITIVE_FRET_SPAN;
        }
    }

    private static final class Candidate {
        static final Comparator<Candidate> ORDER = new Comparator<Candidate>() {
            @Override
            public int compare(Candidate left, Candidate right) {
                int scoreOrder = Integer.compare(left.score, right.score);
                if (scoreOrder != 0) {
                    return scoreOrder;
                }
                for (int index = 0; index < left.frets.length; index++) {
                    int fretOrder = Integer.compare(left.frets[index], right.frets[index]);
                    if (fretOrder != 0) {
                        return fretOrder;
                    }
                }
                return 0;
            }
        };

        final int[] frets;
        final List<String> omittedIntervals;
        final int score;

        Candidate(int[] frets, List<String> omittedIntervals, int score) {
            this.frets = frets;
            this.omittedIntervals = Collections.unmodifiableList(new ArrayList<>(omittedIntervals));
            this.score = score;
        }
    }
}
