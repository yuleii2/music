package com.k2.music;

import java.util.Arrays;
import java.util.List;

public final class ChordRepositorySmokeTest {
    private static final List<String> REQUIRED_V1_CHORDS = Arrays.asList(
            "C", "D", "E", "F", "G", "A", "B",
            "Am", "Dm", "Em",
            "G7", "A7", "E7",
            "Cmaj7", "Fmaj7", "Am7", "Dm7", "Bm7",
            "Csus2", "Dsus4", "Bdim", "Caug", "Cadd9"
    );
    private static final List<String> REQUIRED_V12_CHORDS = Arrays.asList(
            "C7", "D7", "Bm", "F#m",
            "C9", "G9", "D9", "A9",
            "C/E", "G/B", "D/F#"
    );

    public static void main(String[] args) {
        ChordRepository repository = new ChordRepository();
        assertSupportedChordData(repository);
        assertSupportedChordData(repository, REQUIRED_V12_CHORDS);
        assertAliasesAndInvalidInput(repository);
        assertKnownVoicingPatterns(repository);
        assertV11VoicingData(repository);
        assertV12ChordData(repository);
        System.out.println("Chord data smoke test passed.");
    }

    private static void assertSupportedChordData(ChordRepository repository) {
        assertSupportedChordData(repository, REQUIRED_V1_CHORDS);
    }

    private static void assertSupportedChordData(ChordRepository repository, List<String> requiredChords) {
        for (String symbol : requiredChords) {
            ChordRepository.LookupResult result = repository.find(symbol);
            require(result.recognized, symbol + " should be recognized.");
            Chord chord = result.chord;
            require(!chord.notes.isEmpty(), symbol + " should expose notes.");
            require(!chord.intervals.isEmpty(), symbol + " should expose intervals.");
            require(!chord.voicings.isEmpty(), symbol + " should expose at least one voicing.");
            Voicing voicing = chord.voicings.get(0);
            require(voicing.playableMidiNotes().length > 0, symbol + " should expose playable MIDI notes.");
            require(voicing.frets.length == 6, symbol + " should describe six strings.");
        }
    }

    private static void assertAliasesAndInvalidInput(ChordRepository repository) {
        assertFindsSymbol(repository, "cmaj7", "Cmaj7");
        assertFindsSymbol(repository, "CM7", "Cmaj7");
        assertFindsSymbol(repository, "fM7", "Fmaj7");
        assertFindsSymbol(repository, "Amin", "Am");
        assertFindsSymbol(repository, "B°", "Bdim");
        assertFindsSymbol(repository, "C+", "Caug");
        require(!repository.find("cm7").recognized, "cm7 should not be misread as Cmaj7.");
        require(!repository.find("H13x").recognized, "H13x should not be recognized.");
        require(repository.find("Cb").message.contains("非常见"), "Cb should explain uncommon enharmonic spelling.");
        require(repository.find("E#").message.contains("非常见"), "E# should explain uncommon enharmonic spelling.");
        require(!repository.find("").recognized, "Blank input should not be recognized.");
        assertFindsSymbol(repository, "g/b", "G/B");
        assertFindsSymbol(repository, "d/f#", "D/F#");
    }

    private static void assertKnownVoicingPatterns(ChordRepository repository) {
        require("x-3-2-0-1-0".equals(repository.find("C").chord.voicings.get(0).fretPattern()), "C should use x-3-2-0-1-0.");
        require("3-2-0-0-0-1".equals(repository.find("G7").chord.voicings.get(0).fretPattern()), "G7 should use 3-2-0-0-0-1.");
        require(repository.find("F").chord.voicings.get(0).barre, "F should mark barre difficulty.");
    }

    private static void assertV11VoicingData(ChordRepository repository) {
        Chord c = repository.find("C").chord;
        require(c.voicings.size() >= 2, "C should expose multiple voicings for V1.1 switching.");
        require("C 大横按".equals(c.voicings.get(1).name), "C second voicing should be a barre voicing.");
        require(c.voicings.get(1).startFret == 8, "C barre voicing should expose start fret.");
        require(c.voicings.get(1).barre, "C barre voicing should be marked as barre.");

        Chord am = repository.find("Am").chord;
        require(am.voicings.size() >= 2, "Am should expose multiple voicings for V1.1 switching.");
        require(am.voicings.get(1).midiNotes.length == 6, "Am high-position voicing should expose MIDI notes.");

        Chord f = repository.find("F").chord;
        require(f.voicings.size() >= 2, "F should expose simplified and full barre voicings.");
        require(f.voicings.get(0).simplified, "F first voicing should be simplified for beginners.");
        require(f.voicings.get(0).stringNotes[0] == null, "F simplified voicing should mark muted strings.");
    }

    private static void assertV12ChordData(ChordRepository repository) {
        Chord c9 = repository.find("C9").chord;
        require(c9.notes.contains("Bb"), "C9 should include flat seventh Bb.");
        require(c9.notes.contains("D"), "C9 should include ninth D.");

        Chord gb = repository.find("G/B").chord;
        require("G".equals(gb.root), "G/B should keep G as the musical chord root.");
        require("B".equals(gb.bassNote), "G/B should expose B as the slash-chord bass note.");
        require(gb.notes.equals(Arrays.asList("G", "B", "D")), "G/B notes should describe the main G chord tones.");
        require("分数和弦".equals(gb.quality), "G/B should be labeled as slash chord.");

        Chord bm = repository.find("Bm").chord;
        require(bm.voicings.size() >= 2, "Bm should expose full and simplified voicings.");
        require(bm.voicings.get(1).simplified, "Bm second voicing should be simplified.");
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }

    private static void assertFindsSymbol(ChordRepository repository, String input, String expectedSymbol) {
        ChordRepository.LookupResult result = repository.find(input);
        require(result.recognized, input + " should be recognized.");
        require(expectedSymbol.equals(result.chord.symbol), input + " should normalize to " + expectedSymbol + ".");
    }
}
