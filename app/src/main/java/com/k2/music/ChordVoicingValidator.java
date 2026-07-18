package com.k2.music;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Strict musical and structural validation for the bundled guitar-voicing asset. */
public final class ChordVoicingValidator {
    private static final int[] STANDARD_TUNING_MIDI = {40, 45, 50, 55, 59, 64};

    private ChordVoicingValidator() {
    }

    public static void requireValid(
            ChordFormulaRepository formulas,
            List<GuitarVoicingDefinition> voicings
    ) {
        if (formulas == null || voicings == null) {
            throw new IllegalArgumentException("Chord formulas and voicings are required.");
        }
        Set<String> ids = new HashSet<>();
        ChordSymbolParser parser = new ChordSymbolParser(formulas);
        for (GuitarVoicingDefinition voicing : voicings) {
            if (!ids.add(voicing.id)) {
                fail(voicing.id, "duplicate id");
            }
            ChordFormula formula = formulas.findById(voicing.formulaId);
            if (formula == null) {
                fail(voicing.id, "unknown formula " + voicing.formulaId);
            }
            validateStrings(voicing);
            validatePitchContent(voicing, formula);
            ChordSymbolParser.ParseResult parsed = parser.parse(voicing.chordSymbol);
            if (!parsed.recognized) {
                fail(voicing.id, "invalid chord symbol " + voicing.chordSymbol);
            }
        }
    }

    private static void validateStrings(GuitarVoicingDefinition voicing) {
        if (voicing.frets.length != 6 || voicing.fingers.length != 6) {
            fail(voicing.id, "must describe exactly six strings");
        }
        int visibleLastFret = voicing.startFret + voicing.visibleFretCount - 1;
        for (int index = 0; index < 6; index++) {
            int fret = voicing.frets[index];
            int finger = voicing.fingers[index];
            if (fret < -1 || fret > 24) {
                fail(voicing.id, "fret must be -1..24");
            }
            if (finger < 0 || finger > 4) {
                fail(voicing.id, "finger must be 0..4");
            }
            if ((fret <= 0 && finger != 0) || (fret > 0 && finger == 0)) {
                fail(voicing.id, "finger assignment does not match the fret state");
            }
            if (fret > 0 && (fret < voicing.startFret || fret > visibleLastFret)) {
                fail(voicing.id, "pressed fret lies outside the displayed fret range");
            }
        }
    }

    private static void validatePitchContent(GuitarVoicingDefinition voicing, ChordFormula formula) {
        int rootPitch = NoteUtils.semitone(voicing.root);
        Set<Integer> expected = new LinkedHashSet<>();
        for (String interval : formula.intervals) {
            expected.add(Math.floorMod(rootPitch + NoteUtils.intervalToSemitones(interval), 12));
        }
        Set<Integer> actual = new LinkedHashSet<>();
        int lowestMidi = Integer.MAX_VALUE;
        for (int index = 0; index < voicing.frets.length; index++) {
            int fret = voicing.frets[index];
            if (fret < 0) {
                continue;
            }
            int midi = STANDARD_TUNING_MIDI[index] + fret;
            lowestMidi = Math.min(lowestMidi, midi);
            actual.add(Math.floorMod(midi, 12));
        }
        if (actual.isEmpty()) {
            fail(voicing.id, "must sound at least one string");
        }
        Set<Integer> nonChordTones = new LinkedHashSet<>(actual);
        nonChordTones.removeAll(expected);
        if (!voicing.chordBassNote.isEmpty()) {
            nonChordTones.remove(NoteUtils.semitone(voicing.chordBassNote));
        }
        if (!nonChordTones.isEmpty()) {
            fail(voicing.id, "sounds non-chord pitch classes " + nonChordTones);
        }
        for (String interval : formula.requiredIntervals) {
            int pitch = Math.floorMod(rootPitch + NoteUtils.intervalToSemitones(interval), 12);
            if (!actual.contains(pitch)) {
                fail(voicing.id, "omits required interval " + interval);
            }
        }
        for (List<String> group : formula.requiredAnyOf) {
            boolean present = false;
            for (String interval : group) {
                int pitch = Math.floorMod(rootPitch + NoteUtils.intervalToSemitones(interval), 12);
                if (actual.contains(pitch)) {
                    present = true;
                    break;
                }
            }
            if (!present) {
                fail(voicing.id, "must sound one of " + group);
            }
        }
        if (!voicing.bassNote.isEmpty()
                && Math.floorMod(lowestMidi, 12) != NoteUtils.semitone(voicing.bassNote)) {
            fail(voicing.id, "lowest sounding note does not match bassNote");
        }
        if (!voicing.chordBassNote.isEmpty()
                && Math.floorMod(lowestMidi, 12) != NoteUtils.semitone(voicing.chordBassNote)) {
            fail(voicing.id, "lowest sounding note does not match slash-chord bass");
        }
        List<String> calculatedOmissions = new ArrayList<>();
        for (String interval : formula.intervals) {
            int pitch = Math.floorMod(rootPitch + NoteUtils.intervalToSemitones(interval), 12);
            if (!actual.contains(pitch)) {
                calculatedOmissions.add(interval);
            }
        }
        for (String interval : calculatedOmissions) {
            if (!formula.isOmittable(interval)) {
                fail(voicing.id, "omits non-omittable interval " + interval);
            }
        }
        if (!calculatedOmissions.equals(voicing.omittedIntervals)) {
            fail(
                    voicing.id,
                    "omittedIntervals must be " + Arrays.toString(calculatedOmissions.toArray())
                            + " but was " + Arrays.toString(voicing.omittedIntervals.toArray())
            );
        }
    }

    private static void fail(String id, String message) {
        throw new IllegalArgumentException("Invalid voicing " + id + ": " + message + ".");
    }
}
