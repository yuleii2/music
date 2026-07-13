package com.k2.music;

import java.util.HashSet;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/** Scores physical movement between two six-string guitar voicings. Lower is easier. */
public final class VoicingTransitionScorer {
    public static final class Score {
        public final double totalCost;
        public final boolean hasSourceVoicing;
        public final int fretMovement;
        public final int fingerCountChange;
        public final boolean barreChange;
        public final int commonPitchClassCount;
        public final int retainedFingerCount;
        public final int destinationFretSpan;
        public final boolean destinationOpenChord;
        public final int destinationDifficulty;
        public final boolean familiarDestination;
        public final boolean exceedsMaxFret;

        Score(
                double totalCost,
                boolean hasSourceVoicing,
                int fretMovement,
                int fingerCountChange,
                boolean barreChange,
                int commonPitchClassCount,
                int retainedFingerCount,
                int destinationFretSpan,
                boolean destinationOpenChord,
                int destinationDifficulty,
                boolean familiarDestination,
                boolean exceedsMaxFret
        ) {
            this.totalCost = totalCost;
            this.hasSourceVoicing = hasSourceVoicing;
            this.fretMovement = fretMovement;
            this.fingerCountChange = fingerCountChange;
            this.barreChange = barreChange;
            this.commonPitchClassCount = commonPitchClassCount;
            this.retainedFingerCount = retainedFingerCount;
            this.destinationFretSpan = destinationFretSpan;
            this.destinationOpenChord = destinationOpenChord;
            this.destinationDifficulty = destinationDifficulty;
            this.familiarDestination = familiarDestination;
            this.exceedsMaxFret = exceedsMaxFret;
        }
    }

    public Score score(Voicing from, Voicing to) {
        return score(from, to, VoicingRecommendationMode.AUTO, PracticePreferences.defaults(), "");
    }

    public Score score(
            Voicing from,
            Voicing to,
            VoicingRecommendationMode mode,
            PracticePreferences preferences,
            String destinationVoicingId
    ) {
        Objects.requireNonNull(to, "to");
        Objects.requireNonNull(mode, "mode");
        Objects.requireNonNull(preferences, "preferences");

        int movement = from == null ? 0 : fretMovement(from, to);
        int fingerChange = from == null ? fingerCount(to) : Math.abs(fingerCount(from) - fingerCount(to));
        boolean barreChange = from != null && isBarre(from) != isBarre(to);
        int commonTones = from == null ? 0 : commonPitchClasses(from, to);
        int retainedFingers = from == null ? 0 : retainedFingers(from, to);
        int span = fretSpan(to);
        boolean openChord = isOpenChord(to);
        int difficulty = difficulty(to);
        boolean familiar = destinationVoicingId != null
                && preferences.familiarVoicingIds.contains(destinationVoicingId);
        boolean exceedsMaxFret = maxFret(to) > preferences.maxFret;

        VoicingRecommendationMode effectiveMode = effectiveMode(mode, preferences.proficiency);
        double cost = movement * 2.0
                + fingerChange * 3.0
                + (barreChange ? 3.0 : 0.0)
                - commonTones * 1.5
                - retainedFingers * 4.0
                + span * 1.25
                + difficulty * 2.0;

        switch (effectiveMode) {
            case BEGINNER:
                cost += difficulty * 5.0;
                cost += isBarre(to) ? 36.0 : 0.0;
                cost += to.simplified ? -14.0 : 0.0;
                cost += openChord ? -8.0 : 0.0;
                cost += averageFret(to) * 0.8;
                break;
            case MINIMUM_MOVEMENT:
                cost += movement * 2.5;
                cost -= retainedFingers * 3.0;
                cost -= commonTones;
                break;
            case OPEN_CHORDS:
                cost += openChord ? -25.0 : 12.0;
                cost += isBarre(to) ? 12.0 : 0.0;
                break;
            case HIGH_POSITION_TONE:
                cost -= averageFret(to) * 2.5;
                cost += openChord ? 10.0 : 0.0;
                break;
            case AUTO:
            default:
                break;
        }
        if (familiar) {
            cost -= 10.0;
        }
        if (exceedsMaxFret) {
            cost += 10_000.0;
        }
        if (!preferences.allowBarre && isBarre(to)) {
            cost += 10_000.0;
        }
        return new Score(
                cost,
                from != null,
                movement,
                fingerChange,
                barreChange,
                commonTones,
                retainedFingers,
                span,
                openChord,
                difficulty,
                familiar,
                exceedsMaxFret
        );
    }

    public static boolean isBarre(Voicing voicing) {
        return voicing != null && (voicing.barre || (voicing.sourceShape != null && voicing.sourceShape.isBarre()));
    }

    public static int maxFret(Voicing voicing) {
        int max = 0;
        for (int fret : voicing.frets) {
            max = Math.max(max, fret);
        }
        return max;
    }

    public static boolean isOpenChord(Voicing voicing) {
        boolean hasOpen = false;
        for (int fret : voicing.frets) {
            if (fret == 0) {
                hasOpen = true;
            }
        }
        return hasOpen && maxFret(voicing) <= 5;
    }

    private static VoicingRecommendationMode effectiveMode(
            VoicingRecommendationMode mode,
            PracticePreferences.Proficiency proficiency
    ) {
        if (mode != VoicingRecommendationMode.AUTO) {
            return mode;
        }
        switch (proficiency) {
            case BEGINNER:
                return VoicingRecommendationMode.BEGINNER;
            case ADVANCED:
                return VoicingRecommendationMode.MINIMUM_MOVEMENT;
            case INTERMEDIATE:
            default:
                return VoicingRecommendationMode.MINIMUM_MOVEMENT;
        }
    }

    private static int fretMovement(Voicing from, Voicing to) {
        int movement = 0;
        for (int i = 0; i < 6; i++) {
            int fromFret = from.frets[i];
            int toFret = to.frets[i];
            if (fromFret == Voicing.MUTED && toFret == Voicing.MUTED) {
                continue;
            }
            if (fromFret == Voicing.MUTED || toFret == Voicing.MUTED) {
                movement += 3;
            } else {
                movement += Math.abs(fromFret - toFret);
            }
        }
        return movement;
    }

    private static int fingerCount(Voicing voicing) {
        Set<Integer> fingers = new HashSet<>();
        for (int finger : voicing.fingers) {
            if (finger > 0) {
                fingers.add(finger);
            }
        }
        return fingers.size();
    }

    private static int retainedFingers(Voicing from, Voicing to) {
        int retained = 0;
        for (int i = 0; i < 6; i++) {
            if (from.frets[i] > 0
                    && from.frets[i] == to.frets[i]
                    && from.fingers[i] > 0
                    && from.fingers[i] == to.fingers[i]) {
                retained++;
            }
        }
        return retained;
    }

    private static int commonPitchClasses(Voicing from, Voicing to) {
        Set<Integer> fromPitchClasses = pitchClasses(from);
        fromPitchClasses.retainAll(pitchClasses(to));
        return fromPitchClasses.size();
    }

    private static Set<Integer> pitchClasses(Voicing voicing) {
        Set<Integer> result = new HashSet<>();
        for (int midi : voicing.midiNotes) {
            if (midi > 0) {
                result.add(midi % 12);
            }
        }
        return result;
    }

    private static int fretSpan(Voicing voicing) {
        int min = Integer.MAX_VALUE;
        int max = 0;
        for (int fret : voicing.frets) {
            if (fret > 0) {
                min = Math.min(min, fret);
                max = Math.max(max, fret);
            }
        }
        return min == Integer.MAX_VALUE ? 0 : max - min;
    }

    private static int difficulty(Voicing voicing) {
        if (voicing.sourceShape != null) {
            return voicing.sourceShape.difficulty;
        }
        if (voicing.simplified) {
            return 1;
        }
        String value = voicing.difficulty == null ? "" : voicing.difficulty.toLowerCase(Locale.US);
        if (value.contains("beginner") || value.contains("easy") || value.contains("1")) {
            return 1;
        }
        if (value.contains("advanced") || value.contains("hard") || value.contains("5")) {
            return 5;
        }
        if (isBarre(voicing)) {
            return 4;
        }
        return voicing.recommended ? 2 : 3;
    }

    private static double averageFret(Voicing voicing) {
        int total = 0;
        int count = 0;
        for (int fret : voicing.frets) {
            if (fret > 0) {
                total += fret;
                count++;
            }
        }
        return count == 0 ? 0.0 : total / (double) count;
    }
}
