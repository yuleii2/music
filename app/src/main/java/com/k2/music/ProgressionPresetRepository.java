package com.k2.music;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Built-in, key-independent progression presets. */
public final class ProgressionPresetRepository {
    public static final String POP_1564 = "pop_1564";
    public static final String ONE_FOUR_FIVE = "one_four_five";
    public static final String TWO_FIVE_ONE = "two_five_one";
    public static final String ONE_SIX_FOUR_FIVE = "one_six_four_five";
    public static final String SIX_FOUR_ONE_FIVE = "six_four_one_five";
    public static final String TWELVE_BAR_BLUES = "twelve_bar_blues";
    public static final String CANON = "canon";
    public static final String FOUR_FIVE_THREE_SIX_TWO_FIVE_ONE = "4536251";

    private final Map<String, ProgressionPreset> presets;
    private final DiatonicChordGenerator generator;

    public ProgressionPresetRepository() {
        this(new DiatonicChordGenerator());
    }

    public ProgressionPresetRepository(DiatonicChordGenerator generator) {
        this.generator = Objects.requireNonNull(generator, "generator");
        Map<String, ProgressionPreset> builtIns = new LinkedHashMap<>();
        add(builtIns, preset(POP_1564, "I-V-vi-IV", ScaleDegree.I, ScaleDegree.V, ScaleDegree.VI, ScaleDegree.IV));
        add(builtIns, preset(ONE_FOUR_FIVE, "I-IV-V", ScaleDegree.I, ScaleDegree.IV, ScaleDegree.V));
        add(builtIns, preset(TWO_FIVE_ONE, "ii-V-I", ScaleDegree.II, ScaleDegree.V, ScaleDegree.I));
        add(builtIns, preset(ONE_SIX_FOUR_FIVE, "I-vi-IV-V (1645)", ScaleDegree.I, ScaleDegree.VI, ScaleDegree.IV, ScaleDegree.V));
        add(builtIns, preset(SIX_FOUR_ONE_FIVE, "vi-IV-I-V", ScaleDegree.VI, ScaleDegree.IV, ScaleDegree.I, ScaleDegree.V));
        add(builtIns, dominantPreset(
                TWELVE_BAR_BLUES,
                "12-bar blues",
                ScaleDegree.I, ScaleDegree.I, ScaleDegree.I, ScaleDegree.I,
                ScaleDegree.IV, ScaleDegree.IV, ScaleDegree.I, ScaleDegree.I,
                ScaleDegree.V, ScaleDegree.IV, ScaleDegree.I, ScaleDegree.V
        ));
        add(builtIns, preset(
                CANON,
                "Canon progression",
                ScaleDegree.I, ScaleDegree.V, ScaleDegree.VI, ScaleDegree.III,
                ScaleDegree.IV, ScaleDegree.I, ScaleDegree.IV, ScaleDegree.V
        ));
        add(builtIns, preset(
                FOUR_FIVE_THREE_SIX_TWO_FIVE_ONE,
                "IV-V-iii-vi-ii-V-I (4536251)",
                ScaleDegree.IV, ScaleDegree.V, ScaleDegree.III, ScaleDegree.VI,
                ScaleDegree.II, ScaleDegree.V, ScaleDegree.I
        ));
        this.presets = Collections.unmodifiableMap(builtIns);
    }

    public Collection<ProgressionPreset> all() {
        return presets.values();
    }

    public ProgressionPreset get(String id) {
        ProgressionPreset preset = presets.get(id);
        if (preset == null) {
            throw new IllegalArgumentException("Unknown progression preset: " + id);
        }
        return preset;
    }

    public List<String> generateChordSymbols(String presetId, KeySignature key) {
        ProgressionPreset preset = get(presetId);
        List<String> result = new ArrayList<>(preset.degrees.size());
        for (int i = 0; i < preset.degrees.size(); i++) {
            ScaleDegree degree = preset.degrees.get(i);
            String override = preset.qualitySuffixOverrides.get(i);
            result.add(override == null
                    ? generator.chordFor(key, degree)
                    : key.majorScaleNote(degree) + override);
        }
        return Collections.unmodifiableList(result);
    }

    public ChordProgression instantiate(String presetId, KeySignature key, int bpm, long nowEpochMillis) {
        ProgressionPreset preset = get(presetId);
        List<String> symbols = generateChordSymbols(presetId, key);
        List<ProgressionStep> steps = new ArrayList<>(symbols.size());
        for (int i = 0; i < symbols.size(); i++) {
            steps.add(new ProgressionStep(symbols.get(i), "", preset.beatsPerChord, "", i));
        }
        return ChordProgression.create(
                preset.name + " in " + key.tonic,
                key.tonic,
                TimeSignature.FOUR_FOUR,
                bpm,
                true,
                steps,
                "Built from the " + preset.name + " local preset.",
                nowEpochMillis
        );
    }

    private static ProgressionPreset preset(String id, String name, ScaleDegree... degrees) {
        return new ProgressionPreset(id, name, Arrays.asList(degrees), 4.0);
    }

    private static ProgressionPreset dominantPreset(String id, String name, ScaleDegree... degrees) {
        List<String> suffixes = new ArrayList<>(degrees.length);
        for (int i = 0; i < degrees.length; i++) {
            suffixes.add("7");
        }
        return new ProgressionPreset(id, name, Arrays.asList(degrees), suffixes, 4.0);
    }

    private static void add(Map<String, ProgressionPreset> target, ProgressionPreset preset) {
        target.put(preset.id, preset);
    }
}
