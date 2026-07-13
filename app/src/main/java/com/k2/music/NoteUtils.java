package com.k2.music;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public final class NoteUtils {
    private static final String[] SHARP_NAMES = {
            "C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B"
    };
    private static final String[] FLAT_NAMES = {
            "C", "Db", "D", "Eb", "E", "F", "Gb", "G", "Ab", "A", "Bb", "B"
    };
    private static final int[] NATURAL_DEGREE_SEMITONES = {0, 2, 4, 5, 7, 9, 11};
    private static final Map<String, Integer> NOTE_TO_SEMITONE = new HashMap<>();

    static {
        NOTE_TO_SEMITONE.put("C", 0);
        NOTE_TO_SEMITONE.put("B#", 0);
        NOTE_TO_SEMITONE.put("C#", 1);
        NOTE_TO_SEMITONE.put("DB", 1);
        NOTE_TO_SEMITONE.put("D", 2);
        NOTE_TO_SEMITONE.put("D#", 3);
        NOTE_TO_SEMITONE.put("EB", 3);
        NOTE_TO_SEMITONE.put("E", 4);
        NOTE_TO_SEMITONE.put("FB", 4);
        NOTE_TO_SEMITONE.put("E#", 5);
        NOTE_TO_SEMITONE.put("F", 5);
        NOTE_TO_SEMITONE.put("F#", 6);
        NOTE_TO_SEMITONE.put("GB", 6);
        NOTE_TO_SEMITONE.put("G", 7);
        NOTE_TO_SEMITONE.put("G#", 8);
        NOTE_TO_SEMITONE.put("AB", 8);
        NOTE_TO_SEMITONE.put("A", 9);
        NOTE_TO_SEMITONE.put("A#", 10);
        NOTE_TO_SEMITONE.put("BB", 10);
        NOTE_TO_SEMITONE.put("B", 11);
        NOTE_TO_SEMITONE.put("CB", 11);
    }

    private NoteUtils() {
    }

    public static String midiToNoteName(int midi) {
        int semitone = Math.floorMod(midi, 12);
        return SHARP_NAMES[semitone];
    }

    public static int noteNameToMiddleMidi(String noteName) {
        Integer semitone = trySemitone(noteName);
        if (semitone == null) {
            return 60;
        }
        return 60 + semitone;
    }

    /** Returns the pitch class in the range 0..11, or {@code null} for invalid input. */
    public static Integer trySemitone(String noteName) {
        String written = normalizeNoteName(noteName);
        if (written.isEmpty()) {
            return null;
        }
        String normalized = written.toUpperCase(Locale.US);
        Integer direct = NOTE_TO_SEMITONE.get(normalized);
        if (direct != null) {
            return direct;
        }
        int value = naturalSemitone(written.charAt(0));
        for (int index = 1; index < written.length(); index++) {
            value += written.charAt(index) == '#' ? 1 : -1;
        }
        return Math.floorMod(value, 12);
    }

    public static int semitone(String noteName) {
        Integer value = trySemitone(noteName);
        if (value == null) {
            throw new IllegalArgumentException("Unsupported note name: " + noteName);
        }
        return value;
    }

    /**
     * Normalizes capitalization and Unicode accidentals while retaining the
     * user's enharmonic spelling (for example, Db remains Db).
     */
    public static String normalizeNoteName(String noteName) {
        if (noteName == null) {
            return "";
        }
        String compact = noteName.trim()
                .replace("♯", "#")
                .replace("♭", "b")
                .replace("\uFF03", "#");
        if (compact.isEmpty()) {
            return "";
        }
        char letter = Character.toUpperCase(compact.charAt(0));
        if ("ABCDEFG".indexOf(letter) < 0) {
            return "";
        }
        StringBuilder normalized = new StringBuilder().append(letter);
        for (int index = 1; index < compact.length(); index++) {
            char accidental = compact.charAt(index);
            if (accidental == '#') {
                normalized.append('#');
            } else if (accidental == 'b' || accidental == 'B') {
                normalized.append('b');
            } else {
                return "";
            }
        }
        return normalized.toString();
    }

    /** Returns a stable sharp-spelled name for map keys. */
    public static String canonicalPitchClass(String noteName) {
        Integer semitone = trySemitone(noteName);
        return semitone == null ? "" : SHARP_NAMES[semitone];
    }

    public static String noteNameForSemitone(int semitone, boolean preferFlats) {
        return (preferFlats ? FLAT_NAMES : SHARP_NAMES)[Math.floorMod(semitone, 12)];
    }

    public static String transpose(String noteName, int semitones, boolean preferFlats) {
        return noteNameForSemitone(semitone(noteName) + semitones, preferFlats);
    }

    /** Converts labels such as b3, #9 and bb7 into semitone distances. */
    public static int intervalToSemitones(String intervalLabel) {
        if (intervalLabel == null) {
            throw new IllegalArgumentException("Interval must not be null.");
        }
        String label = intervalLabel.trim().replace("♯", "#").replace("♭", "b");
        int accidental = 0;
        int index = 0;
        while (index < label.length()) {
            char character = label.charAt(index);
            if (character == '#') {
                accidental++;
            } else if (character == 'b' || character == 'B') {
                accidental--;
            } else {
                break;
            }
            index++;
        }
        if (index >= label.length()) {
            throw new IllegalArgumentException("Invalid interval: " + intervalLabel);
        }
        final int degree;
        try {
            degree = Integer.parseInt(label.substring(index));
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Invalid interval: " + intervalLabel, exception);
        }
        if (degree < 1) {
            throw new IllegalArgumentException("Interval degree must be positive: " + intervalLabel);
        }
        int zeroBased = degree - 1;
        return NATURAL_DEGREE_SEMITONES[zeroBased % 7] + (zeroBased / 7) * 12 + accidental;
    }

    /**
     * Spells a chord tone diatonically relative to the written root, so Db + 3
     * becomes F rather than the enharmonically equivalent E#.
     */
    public static String spellInterval(String root, String intervalLabel) {
        String normalizedRoot = normalizeNoteName(root);
        if (normalizedRoot.isEmpty() || trySemitone(normalizedRoot) == null) {
            throw new IllegalArgumentException("Unsupported root note: " + root);
        }
        int degree = intervalDegree(intervalLabel);
        char targetLetter = advanceLetter(normalizedRoot.charAt(0), degree - 1);
        int targetSemitone = Math.floorMod(semitone(normalizedRoot) + intervalToSemitones(intervalLabel), 12);
        int difference = targetSemitone - naturalSemitone(targetLetter);
        while (difference > 6) {
            difference -= 12;
        }
        while (difference < -6) {
            difference += 12;
        }
        StringBuilder result = new StringBuilder().append(targetLetter);
        if (difference > 0 && difference <= 2) {
            for (int index = 0; index < difference; index++) {
                result.append('#');
            }
            return result.toString();
        }
        if (difference < 0 && difference >= -2) {
            for (int index = 0; index > difference; index--) {
                result.append('b');
            }
            return result.toString();
        }
        return noteNameForSemitone(targetSemitone, normalizedRoot.contains("b"));
    }

    private static int intervalDegree(String label) {
        String normalized = label == null ? "" : label.trim()
                .replace("♯", "#")
                .replace("♭", "b")
                .replace("#", "")
                .replace("b", "")
                .replace("B", "");
        try {
            return Integer.parseInt(normalized);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Invalid interval: " + label, exception);
        }
    }

    private static char advanceLetter(char root, int steps) {
        String letters = "CDEFGAB";
        int index = letters.indexOf(Character.toUpperCase(root));
        return letters.charAt(Math.floorMod(index + steps, letters.length()));
    }

    private static int naturalSemitone(char note) {
        switch (note) {
            case 'D':
                return 2;
            case 'E':
                return 4;
            case 'F':
                return 5;
            case 'G':
                return 7;
            case 'A':
                return 9;
            case 'B':
                return 11;
            case 'C':
            default:
                return 0;
        }
    }

}
