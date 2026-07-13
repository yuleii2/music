package com.k2.music;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/** Generates correctly spelled diatonic triads for major keys. */
public final class DiatonicChordGenerator {
    public String chordFor(KeySignature key, ScaleDegree degree) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(degree, "degree");
        return key.majorScaleNote(degree) + degree.majorKeySuffix;
    }

    public List<String> generateMajor(KeySignature key) {
        Objects.requireNonNull(key, "key");
        List<String> result = new ArrayList<>(ScaleDegree.values().length);
        for (ScaleDegree degree : ScaleDegree.values()) {
            result.add(chordFor(key, degree));
        }
        return Collections.unmodifiableList(result);
    }

    public List<String> generate(KeySignature key, List<ScaleDegree> degrees) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(degrees, "degrees");
        List<String> result = new ArrayList<>(degrees.size());
        for (ScaleDegree degree : degrees) {
            result.add(chordFor(key, degree));
        }
        return Collections.unmodifiableList(result);
    }
}
