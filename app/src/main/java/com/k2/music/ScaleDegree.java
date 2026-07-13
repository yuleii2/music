package com.k2.music;

import java.util.Locale;

/** Diatonic degrees and triad qualities of a major key. */
public enum ScaleDegree {
    I(1, 0, "I", ""),
    II(2, 2, "ii", "m"),
    III(3, 4, "iii", "m"),
    IV(4, 5, "IV", ""),
    V(5, 7, "V", ""),
    VI(6, 9, "vi", "m"),
    VII(7, 11, "vii\u00b0", "dim");

    public final int number;
    public final int semitonesFromTonic;
    public final String romanNumeral;
    public final String majorKeySuffix;

    ScaleDegree(int number, int semitonesFromTonic, String romanNumeral, String majorKeySuffix) {
        this.number = number;
        this.semitonesFromTonic = semitonesFromTonic;
        this.romanNumeral = romanNumeral;
        this.majorKeySuffix = majorKeySuffix;
    }

    public static ScaleDegree fromNumber(int number) {
        for (ScaleDegree degree : values()) {
            if (degree.number == number) {
                return degree;
            }
        }
        throw new IllegalArgumentException("Scale degree must be between 1 and 7.");
    }

    public static ScaleDegree parse(String value) {
        if (value == null) {
            throw new IllegalArgumentException("Scale degree is required.");
        }
        String normalized = value.trim()
                .replace("\u00b0", "")
                .replace("\u00ba", "")
                .toLowerCase(Locale.US);
        if (normalized.endsWith("dim")) {
            normalized = normalized.substring(0, normalized.length() - 3);
        }
        switch (normalized) {
            case "1":
            case "i":
                return I;
            case "2":
            case "ii":
                return II;
            case "3":
            case "iii":
                return III;
            case "4":
            case "iv":
                return IV;
            case "5":
            case "v":
                return V;
            case "6":
            case "vi":
                return VI;
            case "7":
            case "vii":
                return VII;
            default:
                throw new IllegalArgumentException("Unknown scale degree: " + value);
        }
    }
}
