package com.k2.music;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Deterministic capo calculations; a capo raises every played shape by its fret count. */
public final class CapoAssistant {
    public static final class Suggestion {
        public final int capoFret;
        public final List<String> shapes;
        public final List<String> soundingChords;

        Suggestion(int capoFret, List<String> shapes, List<String> soundingChords) {
            this.capoFret = capoFret;
            this.shapes = Collections.unmodifiableList(new ArrayList<>(shapes));
            this.soundingChords = Collections.unmodifiableList(new ArrayList<>(soundingChords));
        }
    }

    private final ChordTransposer transposer;

    public CapoAssistant() {
        this(new ChordTransposer());
    }

    public CapoAssistant(ChordTransposer transposer) {
        this.transposer = transposer;
    }

    public String soundingChord(String shape, int capoFret, MusicTheoryUtils.AccidentalPreference preference) {
        validateCapo(capoFret);
        return transposeAcrossOctave(shape, capoFret, preference);
    }

    public String shapeForSoundingChord(String soundingChord, int capoFret, MusicTheoryUtils.AccidentalPreference preference) {
        validateCapo(capoFret);
        return transposeAcrossOctave(soundingChord, -capoFret, preference);
    }

    public String soundingProgression(String shapes, int capoFret, MusicTheoryUtils.AccidentalPreference preference) {
        validateCapo(capoFret);
        return transposeProgressionAcrossOctave(shapes, capoFret, preference);
    }

    public List<Suggestion> findMatchingCapos(String actualProgression, String preferredShapes) {
        List<String> actual = transposer.splitProgression(actualProgression);
        List<String> shapes = transposer.splitProgression(preferredShapes);
        if (actual.isEmpty() || actual.size() != shapes.size()) {
            throw new IllegalArgumentException("实际和弦与希望使用的指法数量必须一致");
        }
        List<Suggestion> suggestions = new ArrayList<>();
        for (int capo = 0; capo <= 12; capo++) {
            List<String> sounding = new ArrayList<>();
            boolean match = true;
            for (int i = 0; i < shapes.size(); i++) {
                String transposed = transposeAcrossOctave(
                        shapes.get(i), capo, MusicTheoryUtils.AccidentalPreference.AUTO
                );
                sounding.add(transposed);
                if (!enharmonicallyEquivalent(transposed, actual.get(i))) {
                    match = false;
                    break;
                }
            }
            if (match) {
                suggestions.add(new Suggestion(capo, shapes, sounding));
            }
        }
        return suggestions;
    }

    private String transposeAcrossOctave(String chord, int semitones, MusicTheoryUtils.AccidentalPreference preference) {
        int normalized = semitones % 12;
        if (normalized > 11) normalized -= 12;
        if (normalized < -11) normalized += 12;
        return transposer.transposeChord(chord, normalized, preference);
    }

    private String transposeProgressionAcrossOctave(String progression, int semitones, MusicTheoryUtils.AccidentalPreference preference) {
        int normalized = semitones % 12;
        if (normalized > 11) normalized -= 12;
        if (normalized < -11) normalized += 12;
        return transposer.transposeProgression(progression, normalized, preference);
    }

    private static boolean enharmonicallyEquivalent(String first, String second) {
        ParsedChord a = ParsedChord.parse(first);
        ParsedChord b = ParsedChord.parse(second);
        return a != null && b != null
                && a.rootPitch == b.rootPitch
                && a.suffix.equalsIgnoreCase(b.suffix)
                && a.bassPitch == b.bassPitch;
    }

    private static void validateCapo(int capoFret) {
        if (capoFret < 0 || capoFret > 12) {
            throw new IllegalArgumentException("变调夹品位必须在 0 到 12 之间");
        }
    }

    private static final class ParsedChord {
        final int rootPitch;
        final String suffix;
        final int bassPitch;

        ParsedChord(int rootPitch, String suffix, int bassPitch) {
            this.rootPitch = rootPitch;
            this.suffix = suffix;
            this.bassPitch = bassPitch;
        }

        static ParsedChord parse(String symbol) {
            if (symbol == null) return null;
            String cleaned = symbol.trim().replace("♯", "#").replace("♭", "b");
            int slash = cleaned.indexOf('/');
            String main = slash < 0 ? cleaned : cleaned.substring(0, slash);
            String bass = slash < 0 ? "" : cleaned.substring(slash + 1);
            if (main.isEmpty()) return null;
            int rootLength = main.length() > 1 && (main.charAt(1) == '#' || main.charAt(1) == 'b') ? 2 : 1;
            int root = MusicTheoryUtils.noteToPitchClass(main.substring(0, rootLength));
            int bassPitch = bass.isEmpty() ? root : MusicTheoryUtils.noteToPitchClass(bass);
            if (root < 0 || bassPitch < 0) return null;
            return new ParsedChord(root, main.substring(rootLength), bassPitch);
        }
    }
}
