package com.k2.music;

import java.util.Arrays;
import java.util.List;

public final class ChordRepositorySmokeTest {
    private static final List<String> REQUIRED_CHORDS = Arrays.asList(
            "C", "D", "E", "F", "G", "A", "B",
            "Cm", "Dm", "Em", "Fm", "Gm", "Am", "Bm",
            "C7", "D7", "E7", "F7", "G7", "A7", "B7",
            "Cmaj7", "Dmaj7", "Emaj7", "Fmaj7", "Gmaj7", "Amaj7", "Bmaj7",
            "Cm7", "Dm7", "Em7", "Fm7", "Gm7", "Am7", "Bm7",
            "Csus2", "Dsus2", "Esus2", "Gsus2", "Asus2",
            "Csus4", "Dsus4", "Esus4", "Gsus4", "Asus4",
            "Cadd9", "Dadd9", "Eadd9", "Gadd9", "Aadd9",
            "Cdim", "Ddim", "Edim", "Fdim", "Gdim", "Adim", "Bdim",
            "Caug", "Daug", "Eaug", "Faug", "Gaug", "Aaug", "Baug",
            "C9", "G9", "D9", "A9",
            "C/E", "C/G", "D/F#", "G/B", "Am/C", "F/A",
            "F#m", "C#maj7"
    );

    public static void main(String[] args) {
        ChordRepository repository = new ChordRepository();
        assertLibraryScale(repository);
        assertSupportedChordData(repository);
        assertAliasesAndInvalidInput(repository);
        assertKnownVoicingPatterns(repository);
        assertShapeQueries(repository);
        assertSearch(repository);
        assertSlashAndExtendedChordData(repository);
        System.out.println("Chord data smoke test passed.");
    }

    private static void assertLibraryScale(ChordRepository repository) {
        require(repository.getAllQualities().size() >= 10, "Repository should expose chord quality formulas.");
        require(repository.getAllShapes().size() >= 100, "Repository should expose at least 100 guitar shapes.");
        require(repository.allChords().size() >= 100, "Repository should expose at least 100 chord symbols.");
    }

    private static void assertSupportedChordData(ChordRepository repository) {
        for (String symbol : REQUIRED_CHORDS) {
            ChordRepository.LookupResult result = repository.find(symbol);
            require(result.recognized, symbol + " should be recognized.");
            Chord chord = result.chord;
            require(!chord.notes.isEmpty(), symbol + " should expose notes.");
            require(!chord.intervals.isEmpty(), symbol + " should expose intervals.");
            require(!chord.shapes.isEmpty(), symbol + " should expose ChordShape data.");
            require(!chord.voicings.isEmpty(), symbol + " should expose compatibility Voicing data.");
            Voicing voicing = chord.voicings.get(0);
            require(voicing.sourceShape != null, symbol + " voicing should link back to ChordShape.");
            require(voicing.playableMidiNotes().length > 0, symbol + " should expose playable MIDI notes.");
            require(voicing.frets.length == 6, symbol + " should describe six strings.");
        }
    }

    private static void assertAliasesAndInvalidInput(ChordRepository repository) {
        assertFindsSymbol(repository, "cmaj7", "Cmaj7");
        assertFindsSymbol(repository, "CM7", "Cmaj7");
        assertFindsSymbol(repository, "fM7", "Fmaj7");
        assertFindsSymbol(repository, "Amin", "Am");
        assertFindsSymbol(repository, "cm7", "Cm7");
        assertFindsSymbol(repository, "B°", "Bdim");
        assertFindsSymbol(repository, "C+", "Caug");
        assertFindsSymbol(repository, "dbmaj7", "Dbmaj7");
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
        require(repository.find("F").chord.voicings.get(0).simplified, "F first voicing should be simplified for beginners.");

        Chord c = repository.find("C").chord;
        require(c.voicings.size() >= 2, "C should expose multiple voicings.");
        require("C 大横按".equals(c.voicings.get(1).name), "C second voicing should be a barre voicing.");
        require(c.voicings.get(1).startFret == 8, "C barre voicing should expose start fret.");
        require(c.voicings.get(1).barre, "C barre voicing should be marked as barre.");

        Chord bm = repository.find("Bm").chord;
        require(bm.voicings.size() >= 2, "Bm should expose full and simplified voicings.");
        require(bm.voicings.get(1).simplified, "Bm second voicing should be simplified.");
    }

    private static void assertShapeQueries(ChordRepository repository) {
        require(!repository.getShapesByRoot("C# / Db").isEmpty(), "Root filter should understand C# / Db.");
        require(!repository.getShapesByQuality("maj7").isEmpty(), "Quality filter should return maj7 shapes.");
        require(!repository.getShapesByQuality("sus").isEmpty(), "Quality filter should return suspended shapes.");
        require(!repository.getShapesByQuality("slash").isEmpty(), "Quality filter should return slash shapes.");
        require(!repository.getShapes("C", "maj7").isEmpty(), "Combined root and quality filter should work.");
        require(!repository.getBeginnerShapes().isEmpty(), "Beginner shape query should return shapes.");
        require(!repository.filteredChords("大七", "C", "maj7", 0).isEmpty(), "Filtered chord query should combine search and filters.");
    }

    private static void assertSearch(ChordRepository repository) {
        require(containsShape(repository.search("Cmaj7"), "Cmaj7"), "Search should find Cmaj7 by symbol.");
        require(containsShape(repository.search("大七"), "Cmaj7"), "Search should find major seventh chords by Chinese keyword.");
        require(containsShape(repository.search("sus4"), "Csus4"), "Search should find sus4 chords by quality id.");
        require(containsShape(repository.search("x-3-2-0-1-0"), "C"), "Search should match fret patterns.");
    }

    private static void assertSlashAndExtendedChordData(ChordRepository repository) {
        Chord c9 = repository.find("C9").chord;
        require(c9.notes.contains("Bb"), "C9 should include flat seventh Bb.");
        require(c9.notes.contains("D"), "C9 should include ninth D.");

        Chord gb = repository.find("G/B").chord;
        require("G".equals(gb.root), "G/B should keep G as the musical chord root.");
        require("B".equals(gb.bassNote), "G/B should expose B as the slash-chord bass note.");
        require(gb.notes.equals(Arrays.asList("G", "B", "D")), "G/B notes should describe the main G chord tones.");
        require("分数和弦".equals(gb.quality), "G/B should be labeled as slash chord.");
    }

    private static boolean containsShape(List<ChordShape> shapes, String expectedSymbol) {
        ChordRepository repository = new ChordRepository();
        for (ChordShape shape : shapes) {
            ChordQuality quality = repository.qualityForId(shape.qualityId);
            if (expectedSymbol.equals(shape.symbol(quality))) {
                return true;
            }
        }
        return false;
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
