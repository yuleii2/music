package com.k2.music;

import java.util.Objects;

/** A major-key tonic plus its preferred enharmonic spelling. */
public final class KeySignature {
    public enum AccidentalPreference {
        SHARPS,
        FLATS
    }

    private static final String LETTERS = "CDEFGAB";
    private static final int[] NATURAL_PITCH_CLASSES = {0, 2, 4, 5, 7, 9, 11};

    public final String tonic;
    public final AccidentalPreference accidentalPreference;

    public KeySignature(String tonic) {
        this(tonic, inferredPreference(tonic));
    }

    public KeySignature(String tonic, AccidentalPreference accidentalPreference) {
        this.tonic = normalizeTonic(tonic);
        this.accidentalPreference = Objects.requireNonNull(accidentalPreference, "accidentalPreference");
    }

    public static KeySignature major(String tonic) {
        return new KeySignature(tonic);
    }

    public static KeySignature parse(String value) {
        if (value == null) {
            throw new IllegalArgumentException("Key signature is required.");
        }
        String normalized = value.trim()
                .replaceAll("(?i)\\s*(major|maj)\\s*$", "")
                .trim();
        return new KeySignature(normalized);
    }

    public int tonicPitchClass() {
        return pitchClass(tonic);
    }

    public String majorScaleNote(ScaleDegree degree) {
        Objects.requireNonNull(degree, "degree");
        int tonicLetter = LETTERS.indexOf(tonic.charAt(0));
        int letterIndex = positiveMod(tonicLetter + degree.number - 1, LETTERS.length());
        char letter = LETTERS.charAt(letterIndex);
        int naturalPitch = NATURAL_PITCH_CLASSES[letterIndex];
        int targetPitch = positiveMod(tonicPitchClass() + degree.semitonesFromTonic, 12);
        int difference = positiveMod(targetPitch - naturalPitch + 6, 12) - 6;
        return letter + accidentalForDifference(difference);
    }

    public String displayName() {
        return tonic + " major";
    }

    static int pitchClass(String note) {
        String normalized = normalizeTonic(note);
        int letterIndex = LETTERS.indexOf(normalized.charAt(0));
        int pitch = NATURAL_PITCH_CLASSES[letterIndex];
        if (normalized.length() == 2) {
            pitch += normalized.charAt(1) == '#' ? 1 : -1;
        }
        return positiveMod(pitch, 12);
    }

    private static String accidentalForDifference(int difference) {
        if (difference == 0) {
            return "";
        }
        if (difference == 1) {
            return "#";
        }
        if (difference == -1) {
            return "b";
        }
        if (difference == 2) {
            return "##";
        }
        if (difference == -2) {
            return "bb";
        }
        throw new IllegalArgumentException("Key requires an unsupported accidental spelling.");
    }

    private static String normalizeTonic(String value) {
        if (value == null) {
            throw new IllegalArgumentException("Key tonic is required.");
        }
        String normalized = value.trim()
                .replace('\u266f', '#')
                .replace('\u266d', 'b');
        if (!normalized.matches("(?i)[A-G](?:#|b)?")) {
            throw new IllegalArgumentException("Unsupported key tonic: " + value);
        }
        char letter = Character.toUpperCase(normalized.charAt(0));
        if (normalized.length() == 1) {
            return String.valueOf(letter);
        }
        char accidental = normalized.charAt(1) == '#' ? '#' : 'b';
        return String.valueOf(letter) + accidental;
    }

    private static AccidentalPreference inferredPreference(String tonic) {
        String normalized = normalizeTonic(tonic);
        if (normalized.endsWith("b") || "F".equals(normalized)) {
            return AccidentalPreference.FLATS;
        }
        return AccidentalPreference.SHARPS;
    }

    private static int positiveMod(int value, int modulus) {
        int result = value % modulus;
        return result < 0 ? result + modulus : result;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof KeySignature)) {
            return false;
        }
        KeySignature that = (KeySignature) other;
        return tonic.equals(that.tonic) && accidentalPreference == that.accidentalPreference;
    }

    @Override
    public int hashCode() {
        return Objects.hash(tonic, accidentalPreference);
    }

    @Override
    public String toString() {
        return displayName();
    }
}
