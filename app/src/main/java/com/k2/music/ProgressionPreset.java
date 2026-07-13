package com.k2.music;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/** Key-independent progression recipe expressed as scale degrees. */
public final class ProgressionPreset {
    public final String id;
    public final String name;
    public final List<ScaleDegree> degrees;
    /** Null entries use the normal major-key diatonic quality; non-null entries override it. */
    public final List<String> qualitySuffixOverrides;
    public final double beatsPerChord;

    public ProgressionPreset(String id, String name, List<ScaleDegree> degrees, double beatsPerChord) {
        this(id, name, degrees, null, beatsPerChord);
    }

    public ProgressionPreset(
            String id,
            String name,
            List<ScaleDegree> degrees,
            List<String> qualitySuffixOverrides,
            double beatsPerChord
    ) {
        this.id = requireText(id, "Preset id");
        this.name = requireText(name, "Preset name");
        if (degrees == null || degrees.isEmpty()) {
            throw new IllegalArgumentException("A preset needs at least one scale degree.");
        }
        if (Double.isNaN(beatsPerChord) || Double.isInfinite(beatsPerChord)
                || beatsPerChord <= 0.0 || beatsPerChord > 32.0) {
            throw new IllegalArgumentException("Preset beats must be greater than zero and no more than 32.");
        }
        List<ScaleDegree> copy = new ArrayList<>(degrees.size());
        for (ScaleDegree degree : degrees) {
            copy.add(Objects.requireNonNull(degree, "degree"));
        }
        this.degrees = Collections.unmodifiableList(copy);
        List<String> suffixes = new ArrayList<>(degrees.size());
        if (qualitySuffixOverrides == null) {
            for (int i = 0; i < degrees.size(); i++) {
                suffixes.add(null);
            }
        } else {
            if (qualitySuffixOverrides.size() != degrees.size()) {
                throw new IllegalArgumentException("Quality overrides must align with preset degrees.");
            }
            for (String suffix : qualitySuffixOverrides) {
                if (suffix != null && suffix.length() > 16) {
                    throw new IllegalArgumentException("Preset quality suffix is too long.");
                }
                suffixes.add(suffix);
            }
        }
        this.qualitySuffixOverrides = Collections.unmodifiableList(suffixes);
        this.beatsPerChord = beatsPerChord;
    }

    private static String requireText(String value, String label) {
        String result = value == null ? "" : value.trim();
        if (result.isEmpty()) {
            throw new IllegalArgumentException(label + " is required.");
        }
        return result;
    }
}
