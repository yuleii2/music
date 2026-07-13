package com.k2.music;

import java.util.Locale;
import java.util.Objects;

/** Immutable musical time signature. */
public final class TimeSignature {
    public static final TimeSignature TWO_FOUR = new TimeSignature(2, 4);
    public static final TimeSignature THREE_FOUR = new TimeSignature(3, 4);
    public static final TimeSignature FOUR_FOUR = new TimeSignature(4, 4);
    public static final TimeSignature SIX_EIGHT = new TimeSignature(6, 8);

    public final int numerator;
    public final int denominator;

    public TimeSignature(int numerator, int denominator) {
        if (numerator < 1 || numerator > 32) {
            throw new IllegalArgumentException("Time-signature numerator must be between 1 and 32.");
        }
        if (denominator < 1 || denominator > 32 || (denominator & (denominator - 1)) != 0) {
            throw new IllegalArgumentException("Time-signature denominator must be a power of two up to 32.");
        }
        this.numerator = numerator;
        this.denominator = denominator;
    }

    public static TimeSignature parse(String value) {
        if (value == null) {
            throw new IllegalArgumentException("Time signature is required.");
        }
        String[] parts = value.trim().split("/");
        if (parts.length != 2) {
            throw new IllegalArgumentException("Time signature must use the form 4/4.");
        }
        try {
            return new TimeSignature(Integer.parseInt(parts[0].trim()), Integer.parseInt(parts[1].trim()));
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Time signature must contain numbers.", exception);
        }
    }

    public boolean isSupportedByMetronome() {
        return equals(TWO_FOUR) || equals(THREE_FOUR) || equals(FOUR_FOUR) || equals(SIX_EIGHT);
    }

    @Override
    public String toString() {
        return String.format(Locale.US, "%d/%d", numerator, denominator);
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof TimeSignature)) {
            return false;
        }
        TimeSignature that = (TimeSignature) other;
        return numerator == that.numerator && denominator == that.denominator;
    }

    @Override
    public int hashCode() {
        return Objects.hash(numerator, denominator);
    }
}
