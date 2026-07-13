package com.k2.music;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Local key/chord relationship analysis used after structured AI validation. */
public final class ChordProgressionAnalyzer {
    private static final Pattern KEY = Pattern.compile("(?i)^([A-G])([#b♯♭]?)(?:\\s+(major|maj|minor|min|m))?$");
    private static final int[] MAJOR_OFFSETS = {0, 2, 4, 5, 7, 9, 11};
    private static final String[] MAJOR_QUALITIES = {"maj", "m", "m", "maj", "maj", "m", "dim"};
    private static final int[] MINOR_OFFSETS = {0, 2, 3, 5, 7, 8, 10};
    private static final String[] MINOR_QUALITIES = {"m", "dim", "maj", "m", "m", "maj", "maj"};
    private static final String[] DEGREES = {"I", "II", "III", "IV", "V", "VI", "VII"};

    public static final class Relation {
        public final String symbol;
        public final boolean diatonic;
        public final String degree;

        Relation(String symbol, boolean diatonic, String degree) {
            this.symbol = symbol;
            this.diatonic = diatonic;
            this.degree = degree;
        }
    }

    public static final class Analysis {
        public final String normalizedKey;
        public final List<Relation> relations;
        public final String summary;

        Analysis(String normalizedKey, List<Relation> relations, String summary) {
            this.normalizedKey = normalizedKey;
            this.relations = Collections.unmodifiableList(new ArrayList<>(relations));
            this.summary = summary;
        }
    }

    public Analysis analyze(String rawKey, List<Chord> chords) {
        String key = rawKey == null ? "" : rawKey.trim();
        if (key.isEmpty()) {
            return new Analysis("", Collections.emptyList(), "未指定调性；已完成和弦名称与本地理论数据校验。");
        }
        Matcher matcher = KEY.matcher(key);
        if (!matcher.matches()) {
            throw new IllegalArgumentException("无法识别调性：" + rawKey);
        }
        String tonic = matcher.group(1).toUpperCase(Locale.US) + normalizeAccidental(matcher.group(2));
        String modeToken = matcher.group(3) == null ? "major" : matcher.group(3).toLowerCase(Locale.US);
        boolean minor = modeToken.equals("minor") || modeToken.equals("min") || modeToken.equals("m");
        int tonicPitch = MusicTheoryUtils.noteToPitchClass(tonic);
        int[] offsets = minor ? MINOR_OFFSETS : MAJOR_OFFSETS;
        String[] expectedQualities = minor ? MINOR_QUALITIES : MAJOR_QUALITIES;
        List<Relation> relations = new ArrayList<>();
        int diatonicCount = 0;
        for (Chord chord : chords) {
            int pitch = MusicTheoryUtils.noteToPitchClass(chord.root);
            int relative = Math.floorMod(pitch - tonicPitch, 12);
            int degreeIndex = indexOf(offsets, relative);
            boolean diatonic = degreeIndex >= 0
                    && compatibleQuality(expectedQualities[degreeIndex], chord.qualityId);
            if (diatonic) diatonicCount++;
            relations.add(new Relation(
                    chord.symbol,
                    diatonic,
                    degreeIndex < 0 ? "非调内根音" : degreeLabel(DEGREES[degreeIndex], expectedQualities[degreeIndex])
            ));
        }
        String normalizedKey = tonic + (minor ? " minor" : " major");
        return new Analysis(
                normalizedKey,
                relations,
                "本地调性分析：" + chords.size() + " 个和弦中有 " + diatonicCount
                        + " 个符合 " + normalizedKey + " 的自然调内关系；其余标记为借用或色彩和弦，不会被静默删除。"
        );
    }

    private static boolean compatibleQuality(String expected, String actual) {
        if (expected.equals(actual)) return true;
        if (expected.equals("maj")) {
            return actual.equals("maj7") || actual.equals("6") || actual.equals("add9") || actual.equals("maj9");
        }
        if (expected.equals("m")) {
            return actual.equals("m7") || actual.equals("m6") || actual.equals("m9") || actual.equals("mMaj7");
        }
        return expected.equals("dim") && (actual.equals("dim7") || actual.equals("m7b5"));
    }

    private static String degreeLabel(String roman, String quality) {
        if (quality.equals("m")) return roman.toLowerCase(Locale.US);
        if (quality.equals("dim")) return roman.toLowerCase(Locale.US) + "°";
        return roman;
    }

    private static int indexOf(int[] values, int target) {
        for (int i = 0; i < values.length; i++) if (values[i] == target) return i;
        return -1;
    }

    private static String normalizeAccidental(String accidental) {
        return accidental == null ? "" : accidental.replace("♯", "#").replace("♭", "b");
    }
}
