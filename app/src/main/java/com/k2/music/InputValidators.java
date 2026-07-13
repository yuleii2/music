package com.k2.music;

/** Shared, side-effect-free validation helpers for user-entered numeric values. */
public final class InputValidators {
    private InputValidators() {
    }

    public static int integerInRange(String raw, int minimum, int maximum, String errorMessage) {
        try {
            int value = Integer.parseInt(raw == null ? "" : raw.trim());
            if (value < minimum || value > maximum) {
                throw new NumberFormatException();
            }
            return value;
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(errorMessage);
        }
    }
}
