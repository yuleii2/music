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
        require(loaded.formulas.getAll().size() == 26, "All Phase 1 formulas should load from JSON.");
        require(loaded.voicings.getAll().size() >= 200, "The JSON voicing library should be broad.");

        ChordNameParser parser = new ChordNameParser(loaded.formulas);
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

        ChordNameParser.ParseResult bbm7 = parser.parse("Bbm7");
        require(theory.chordTones(bbm7.root, bbm7.formula)
                        .equals(Arrays.asList("Bb", "Db", "F", "Ab")),
                "Bbm7 should retain flat spelling.");

        ChordNameParser.ParseResult altered = parser.parse("C7b9");
        require(theory.chordTones(altered.root, altered.formula)
                        .equals(Arrays.asList("C", "E", "G", "Bb", "Db")),
                "C7b9 should contain C E G Bb Db.");

        ChordNameParser.ParseResult halfDiminished = parser.parse("Cm7b5");
        require(theory.chordTones(halfDiminished.root, halfDiminished.formula)
                        .equals(Arrays.asList("C", "Eb", "Gb", "Bb")),
                "Cm7b5 should contain C Eb Gb Bb.");

        List<String> sharpTones = theory.chordTones("C#", loaded.formulas.findById("maj7"));
        List<String> flatTones = theory.chordTones("Db", loaded.formulas.findById("maj7"));
        require(pitchClasses(sharpTones).equals(pitchClasses(flatTones)),
                "C#maj7 and Dbmaj7 should map to the same pitch-class set.");
        require(flatTones.equals(Arrays.asList("Db", "F", "Ab", "C")),
                "Dbmaj7 should use readable flat spelling.");

        require(NoteUtils.intervalToSemitones("bb7") == 9, "bb7 should be nine semitones.");
        require(NoteUtils.intervalToSemitones("#9") == 15, "#9 should retain compound distance.");
        require(NoteUtils.semitone("Bbb") == NoteUtils.semitone("A"),
                "Double-flat theory spellings should normalize for playback.");
        require(!parser.parse("H13x").recognized, "Invalid roots should return a clear parse error.");
        require(parser.parse("H13x").error != null && !parser.parse("H13x").error.isEmpty(),
                "Invalid parse errors should not be blank.");

        ChordRepository repository = new ChordRepository();
        require(!repository.isUsingFallbackData(), "Bundled JSON should be the primary repository source.");
        ChordRepository.LookupResult theoretical = repository.find("B13");
        require(theoretical.recognized, "A formula-valid chord must exist without a recorded guitar shape.");
        require(!theoretical.chord.notes.isEmpty(), "A theory-only chord should expose notes.");
        if (theoretical.chord.voicings.isEmpty()) {
            require(theoretical.message != null && theoretical.message.contains("暂无收录指法"),
                    "Theory-only chords should explain that no voicing is recorded.");
        }

        ChordRepository fallback = new ChordRepository(path -> {
            throw new java.io.IOException("deliberate test failure");
        });
        require(fallback.isUsingFallbackData(), "Unreadable JSON should activate the safe fallback.");
        require(fallback.find("C").recognized, "Fallback data should keep core lookups usable.");
        require(fallback.getDataLoadMessage().contains("safe built-in fallback"),
                "Fallback state should expose a diagnostic message.");
    }

    private static void assertParsed(
            ChordNameParser parser,
            String input,
            String canonicalRoot,
            String formulaId,
            String canonicalBass
    ) {
        ChordNameParser.ParseResult result = parser.parse(input);
        require(result.recognized, input + " should parse: " + result.error);
        require(canonicalRoot.equals(result.canonicalRoot), input + " should normalize its root.");
        require(formulaId.equals(result.formulaId), input + " should map to formula " + formulaId + ".");
        require(canonicalBass.equals(result.canonicalBassNote), input + " should parse its slash bass.");
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
