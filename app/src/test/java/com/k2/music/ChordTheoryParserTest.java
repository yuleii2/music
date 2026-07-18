package com.k2.music;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class ChordTheoryParserTest {
    public static void main(String[] args) throws Exception {
        run();
        System.out.println("Chord parser/theory tests passed.");
    }

    static void run() throws Exception {
        ChordDataLoader.LoadedData loaded = new ChordDataLoader().loadDefault();
        require(loaded.formulas.getAll().size() == 48, "All complex-chord formulas should load from JSON.");
        require(loaded.voicingSchemaVersion == 2, "The complete voicing library should use schema version 2.");
        require(loaded.voicings.getAll().size() >= 405, "The reviewed JSON voicing library should remain available.");
        ChordVoicingValidator.requireValid(loaded.formulas, loaded.voicings.getAll());
        assertCompleteFormulaCatalog(loaded.formulas);

        ChordSymbolParser parser = new ChordSymbolParser(loaded.formulas);
        ChordTheoryEngine theory = new ChordTheoryEngine();

        assertParsed(parser, "C", "C", "maj", "");
        assertParsed(parser, "Am", "A", "m", "");
        assertParsed(parser, "  F♯ maj7 ", "F#", "maj7", "");
        assertParsed(parser, "Bbm7", "A#", "m7", "");
        assertParsed(parser, "C7b9", "C", "7b9", "");
        assertParsed(parser, "Cm7b5", "C", "m7b5", "");
        assertParsed(parser, "C/G", "C", "maj", "G");
        assertParsed(parser, "C△", "C", "maj7", "");
        assertParsed(parser, "C°", "C", "dim", "");
        assertParsed(parser, "Cø", "C", "m7b5", "");
        assertParsed(parser, "Cmin7", "C", "m7", "");
        assertParsed(parser, "C-", "C", "m", "");
        assertParsed(parser, "C major 7", "C", "maj7", "");
        assertParsed(parser, "C大七和弦", "C", "maj7", "");
        assertParsed(parser, "C9", "C", "9", "");
        assertParsed(parser, "Cmaj9", "C", "maj9", "");
        assertParsed(parser, "Cm9", "C", "m9", "");
        assertParsed(parser, "Cadd9", "C", "add9", "");
        assertParsed(parser, "Csus2", "C", "sus2", "");
        assertParsed(parser, "Csus4", "C", "sus4", "");
        assertParsed(parser, "C7sus4", "C", "7sus4", "");
        assertParsed(parser, "C挂四七", "C", "7sus4", "");
        assertParsed(parser, "C11", "C", "11", "");
        assertParsed(parser, "Cm11", "C", "m11", "");
        assertParsed(parser, "C13", "C", "13", "");
        assertParsed(parser, "C7#9", "C", "7#9", "");
        assertParsed(parser, "Cmaj7#11", "C", "maj7#11", "");
        assertParsed(parser, "Cø7", "C", "m7b5", "");
        assertParsed(parser, "Cdim7", "C", "dim7", "");
        assertParsed(parser, "Caug", "C", "aug", "");
        assertParsed(parser, "C+", "C", "aug", "");
        assertParsed(parser, "Cmaj7/E", "C", "maj7", "E");
        assertParsed(parser, "F#m7b5", "F#", "m7b5", "");
        assertParsed(parser, "升F小七降五", "F#", "m7b5", "");
        assertParsed(parser, "Bbmaj9/D", "A#", "maj9", "D");
        assertParsed(parser, "降B大九", "A#", "maj9", "");
        assertParsed(parser, "C#7#9/G#", "C#", "7#9", "G#");
        assertParsed(parser, "C(b5)", "C", "b5", "");
        assertParsed(parser, "C(#9)", "C", "sharp9", "");
        assertParsed(parser, "C(#11)", "C", "sharp11", "");

        ChordSymbolParser.ParseResult bbm7 = parser.parse("Bbm7");
        require(theory.chordTones(bbm7.root, bbm7.formula)
                        .equals(Arrays.asList("Bb", "Db", "F", "Ab")),
                "Bbm7 should retain flat spelling.");

        ChordSymbolParser.ParseResult altered = parser.parse("C7b9");
        require(theory.chordTones(altered.root, altered.formula)
                        .equals(Arrays.asList("C", "E", "G", "Bb", "Db")),
                "C7b9 should contain C E G Bb Db.");

        ChordSymbolParser.ParseResult halfDiminished = parser.parse("Cm7b5");
        require(theory.chordTones(halfDiminished.root, halfDiminished.formula)
                        .equals(Arrays.asList("C", "Eb", "Gb", "Bb")),
                "Cm7b5 should contain C Eb Gb Bb.");

        List<String> sharpTones = theory.chordTones("C#", loaded.formulas.findById("maj7"));
        List<String> flatTones = theory.chordTones("Db", loaded.formulas.findById("maj7"));
        require(pitchClasses(sharpTones).equals(pitchClasses(flatTones)),
                "C#maj7 and Dbmaj7 should map to the same pitch-class set.");
        require(flatTones.equals(Arrays.asList("Db", "F", "Ab", "C")),
                "Dbmaj7 should use readable flat spelling.");

        assertTones(theory, loaded, "maj7", Arrays.asList("C", "E", "G", "B"));
        assertTones(theory, loaded, "m7", Arrays.asList("C", "Eb", "G", "Bb"));
        assertTones(theory, loaded, "9", Arrays.asList("C", "E", "G", "Bb", "D"));
        assertTones(theory, loaded, "add9", Arrays.asList("C", "E", "G", "D"));
        assertTones(theory, loaded, "sus4", Arrays.asList("C", "F", "G"));
        assertTones(theory, loaded, "7sus4", Arrays.asList("C", "F", "G", "Bb"));
        assertTones(theory, loaded, "dim7", Arrays.asList("C", "Eb", "Gb", "Bbb"));
        assertTones(theory, loaded, "13", Arrays.asList("C", "E", "G", "Bb", "D", "F", "A"));
        require(theory.chordTones("F#", loaded.formulas.findById("m7b5"))
                        .equals(Arrays.asList("F#", "A", "C", "E")),
                "F#m7b5 should contain F# A C E.");

        ChordFormula dominantThirteenth = loaded.formulas.findById("13");
        require(dominantThirteenth.isRequired("3") && dominantThirteenth.isRequired("b7")
                        && dominantThirteenth.isRequired("13"),
                "Dominant thirteenth voicings must preserve quality-defining intervals.");
        require(dominantThirteenth.isOmittable("5") && dominantThirteenth.isOmittable("11"),
                "Dominant thirteenth guitar voicings may omit fifth and eleventh.");
        require(loaded.formulas.findById("7alt").requiredAnyOf.size() == 2,
                "Altered dominant formulas should require altered fifth and ninth choices.");

        require(NoteUtils.intervalToSemitones("bb7") == 9, "bb7 should be nine semitones.");
        require(NoteUtils.intervalToSemitones("#9") == 15, "#9 should retain compound distance.");
        require(NoteUtils.semitone("Bbb") == NoteUtils.semitone("A"),
                "Double-flat theory spellings should normalize for playback.");
        require(!parser.parse("H13x").recognized, "Invalid roots should return a clear parse error.");
        require(parser.parse("H13x").error != null && !parser.parse("H13x").error.isEmpty(),
                "Invalid parse errors should not be blank.");

        ChordRepository repository = new ChordRepository();
        require(!repository.isUsingFallbackData(), "Bundled JSON should be the primary repository source.");
        require(repository.allChords().size() == 582, "The catalog should contain 576 formula/root chords plus six recorded slash chords.");
        Set<String> catalogRoots = new HashSet<>();
        for (Chord chord : repository.allChords()) {
            catalogRoots.add(NoteUtils.canonicalPitchClass(chord.root));
            require(!chord.voicings.isEmpty(), chord.symbol + " should expose at least one guitar voicing.");
        }
        require(catalogRoots.size() == 12, "The catalog should expose all twelve chromatic roots.");
        for (ChordFormula formula : loaded.formulas.getAll()) {
            for (String root : Arrays.asList("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")) {
                String symbol = root + formula.suffix;
                ChordRepository.LookupResult result = repository.find(symbol);
                require(result.recognized && !result.chord.voicings.isEmpty(), symbol + " should have a playable voicing.");
                assertVoicingRules(result.chord, formula);
            }
        }
        ChordRepository.LookupResult advanced = repository.find("B13");
        require(advanced.recognized, "An advanced formula-valid chord should be recognized.");
        require(!advanced.chord.notes.isEmpty(), "An advanced chord should expose its chord tones.");
        require(!advanced.chord.voicings.isEmpty(), "B13 should now expose a validated guitar voicing.");

        assertSlashBass(repository.find("C/E").chord, "E");
        assertSlashBass(repository.find("Bbmaj9/D").chord, "D");
        assertSlashBass(repository.find("C#7#9/G#").chord, "G#");
        require("Bbmaj9/D".equals(repository.find("Bbmaj9/D").chord.symbol),
                "Dynamic slash chords should preserve valid flat spelling.");
        Chord modeledSlash = repository.find("Cmaj9/E").chord;
        require("C".equals(modeledSlash.root) && "maj9".equals(modeledSlash.qualityId)
                        && "E".equals(modeledSlash.bassNote),
                "Slash chords must remain structured as root + quality + bassNote.");
        require(modeledSlash.extensions.equals(Arrays.asList("7", "9"))
                        && modeledSlash.pitchClasses.size() == 5 && !modeledSlash.displayName.isEmpty(),
                "Chord models should expose extensions, pitch classes, and a display name.");
        require(repository.find("C7alt").chord.alterations.containsAll(Arrays.asList("b5", "#5", "b9", "#9")),
                "Altered chords should expose their alterations structurally.");
        require(repository.find("Cadd13").chord.additions.equals(Arrays.asList("13")),
                "Added-tone chords should expose additions structurally.");
        require(!repository.filteredChords("升F小七降五", "", "", 0).isEmpty(),
                "Chinese sharp-root aliases should participate in search.");
        require(!repository.filteredChords("C major 7", "", "", 0).isEmpty(),
                "Whitespace-insensitive English aliases should participate in search.");

        ChordRepository fallback = new ChordRepository(path -> {
            throw new java.io.IOException("deliberate test failure");
        });
        require(fallback.isUsingFallbackData(), "Unreadable JSON should activate the safe fallback.");
        require(fallback.find("C").recognized, "Fallback data should keep core lookups usable.");
        require(fallback.getDataLoadMessage().contains("safe built-in fallback"),
                "Fallback state should expose a diagnostic message.");
    }

    private static void assertParsed(
            ChordSymbolParser parser,
            String input,
            String canonicalRoot,
            String formulaId,
            String canonicalBass
    ) {
        ChordSymbolParser.ParseResult result = parser.parse(input);
        require(result.recognized, input + " should parse: " + result.error);
        require(canonicalRoot.equals(result.canonicalRoot), input + " should normalize its root.");
        require(formulaId.equals(result.formulaId), input + " should map to formula " + formulaId + ".");
        require(canonicalBass.equals(result.canonicalBassNote), input + " should parse its slash bass.");
    }

    private static void assertTones(
            ChordTheoryEngine theory,
            ChordDataLoader.LoadedData loaded,
            String formulaId,
            List<String> expected
    ) {
        require(theory.chordTones("C", loaded.formulas.findById(formulaId)).equals(expected),
                formulaId + " should use its declared theoretical intervals.");
    }

    private static void assertCompleteFormulaCatalog(ChordFormulaRepository formulas) {
        String[][] expected = {
                {"maj", "1 3 5"}, {"m", "1 b3 5"}, {"dim", "1 b3 b5"},
                {"aug", "1 3 #5"}, {"5", "1 5"}, {"6", "1 3 5 6"},
                {"m6", "1 b3 5 6"}, {"7", "1 3 5 b7"}, {"maj7", "1 3 5 7"},
                {"m7", "1 b3 5 b7"}, {"mMaj7", "1 b3 5 7"},
                {"dim7", "1 b3 b5 bb7"}, {"m7b5", "1 b3 b5 b7"},
                {"7#5", "1 3 #5 b7"}, {"maj7#5", "1 3 #5 7"},
                {"9", "1 3 5 b7 9"}, {"maj9", "1 3 5 7 9"},
                {"m9", "1 b3 5 b7 9"}, {"mMaj9", "1 b3 5 7 9"},
                {"add9", "1 3 5 9"}, {"madd9", "1 b3 5 9"},
                {"7b9", "1 3 5 b7 b9"}, {"7#9", "1 3 5 b7 #9"},
                {"maj7#9", "1 3 5 7 #9"}, {"m7b9", "1 b3 5 b7 b9"},
                {"sus2", "1 2 5"}, {"sus4", "1 4 5"}, {"7sus2", "1 2 5 b7"},
                {"7sus4", "1 4 5 b7"}, {"9sus4", "1 4 5 b7 9"},
                {"11", "1 3 5 b7 9 11"}, {"maj11", "1 3 5 7 9 11"},
                {"m11", "1 b3 5 b7 9 11"}, {"13", "1 3 5 b7 9 11 13"},
                {"maj13", "1 3 5 7 9 11 13"}, {"m13", "1 b3 5 b7 9 11 13"},
                {"add11", "1 3 5 11"}, {"add13", "1 3 5 13"},
                {"b5", "1 3 b5"}, {"b9", "1 3 5 b9"}, {"sharp9", "1 3 5 #9"},
                {"sharp11", "1 3 5 #11"}, {"b13", "1 3 5 b13"},
                {"7b5", "1 3 b5 b7"}, {"maj7#11", "1 3 5 7 #11"},
                {"7#11", "1 3 5 b7 #11"}, {"7b13", "1 3 5 b7 b13"},
                {"7alt", "1 3 b5 #5 b7 b9 #9"}
        };
        require(expected.length == formulas.getAll().size(), "Formula expectation table must cover every formula.");
        for (String[] entry : expected) {
            ChordFormula formula = formulas.findById(entry[0]);
            require(formula != null && String.join(" ", formula.intervals).equals(entry[1]),
                    entry[0] + " must use interval formula " + entry[1] + ".");
        }
    }

    private static void assertSlashBass(Chord chord, String expectedBass) {
        require(chord != null && !chord.voicings.isEmpty(), "Slash chord should expose a generated voicing.");
        Voicing voicing = chord.voicings.get(0);
        int lowestMidi = Integer.MAX_VALUE;
        for (int index = 0; index < voicing.frets.length; index++) {
            if (voicing.frets[index] >= 0) {
                lowestMidi = Math.min(lowestMidi, MusicTheoryUtils.STANDARD_TUNING_MIDI[index] + voicing.frets[index]);
            }
        }
        require(lowestMidi != Integer.MAX_VALUE
                        && Math.floorMod(lowestMidi, 12) == NoteUtils.semitone(expectedBass),
                chord.symbol + " must sound " + expectedBass + " as its actual lowest note.");
    }

    private static void assertVoicingRules(Chord chord, ChordFormula formula) {
        Voicing voicing = chord.voicings.get(0);
        Set<Integer> actual = new HashSet<>();
        int lowestMidi = Integer.MAX_VALUE;
        for (int index = 0; index < voicing.frets.length; index++) {
            if (voicing.frets[index] >= 0) {
                int midi = MusicTheoryUtils.STANDARD_TUNING_MIDI[index] + voicing.frets[index];
                actual.add(Math.floorMod(midi, 12));
                lowestMidi = Math.min(lowestMidi, midi);
            }
        }
        int rootPitch = NoteUtils.semitone(chord.root);
        for (String interval : formula.requiredIntervals) {
            int pitch = Math.floorMod(rootPitch + NoteUtils.intervalToSemitones(interval), 12);
            require(actual.contains(pitch), chord.symbol + " voicing must contain required interval " + interval + ".");
        }
        for (List<String> group : formula.requiredAnyOf) {
            boolean present = false;
            for (String interval : group) {
                int pitch = Math.floorMod(rootPitch + NoteUtils.intervalToSemitones(interval), 12);
                present |= actual.contains(pitch);
            }
            require(present, chord.symbol + " voicing must contain one of " + group + ".");
        }
        for (String omitted : voicing.omittedIntervals) {
            require(formula.isOmittable(omitted), chord.symbol + " may not omit required interval " + omitted + ".");
        }
        if (voicing.sourceShape != null && voicing.sourceShape.id.startsWith("generated-runtime-")) {
            require(lowestMidi != Integer.MAX_VALUE && Math.floorMod(lowestMidi, 12) == rootPitch,
                    chord.symbol + " generated root-position voicing must sound its root as the lowest pitch.");
        }
    }

    private static Set<Integer> pitchClasses(List<String> notes) {
        Set<Integer> result = new HashSet<>();
        for (String note : notes) {
            result.add(NoteUtils.semitone(note));
        }
        return result;
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }
}
