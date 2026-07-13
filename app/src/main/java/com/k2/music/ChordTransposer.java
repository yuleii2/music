package com.k2.music;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Offline chord-symbol and progression transposition with slash-bass support. */
public final class ChordTransposer {
    private static final Pattern CHORD = Pattern.compile(
            "^([A-Ga-g])([#b♯♭]?)([^/]*?)(?:/([A-Ga-g])([#b♯♭]?))?$"
    );

    public String transposeChord(String rawSymbol, int semitones, MusicTheoryUtils.AccidentalPreference preference) {
        if (rawSymbol == null || rawSymbol.trim().isEmpty()) {
            throw new IllegalArgumentException("请输入和弦名称");
        }
        if (semitones < -11 || semitones > 11) {
            throw new IllegalArgumentException("移调范围必须在 -11 到 +11 个半音之间");
        }
        String symbol = rawSymbol.trim().replace(" ", "");
        Matcher matcher = CHORD.matcher(symbol);
        if (!matcher.matches()) {
            throw new IllegalArgumentException("无法识别和弦：" + rawSymbol);
        }
        String root = matcher.group(1).toUpperCase(Locale.US) + normalizeAccidental(matcher.group(2));
        String suffix = matcher.group(3) == null ? "" : matcher.group(3);
        String bass = matcher.group(4) == null
                ? ""
                : matcher.group(4).toUpperCase(Locale.US) + normalizeAccidental(matcher.group(5));

        MusicTheoryUtils.AccidentalPreference resolved = preference == MusicTheoryUtils.AccidentalPreference.AUTO
                ? MusicTheoryUtils.resolvePreference(preference, root + bass)
                : preference;
        StringBuilder result = new StringBuilder(MusicTheoryUtils.transposeNote(root, semitones, resolved));
        result.append(suffix);
        if (!bass.isEmpty()) {
            result.append('/').append(MusicTheoryUtils.transposeNote(bass, semitones, resolved));
        }
        return result.toString();
    }

    public String transposeProgression(String progression, int semitones, MusicTheoryUtils.AccidentalPreference preference) {
        if (progression == null || progression.trim().isEmpty()) {
            throw new IllegalArgumentException("请输入一个或多个和弦");
        }
        String cleaned = progression.trim()
                .replace('，', ' ')
                .replace(',', ' ')
                .replace("→", " ")
                .replace("->", " ");
        String[] tokens = cleaned.split("\\s+");
        List<String> result = new ArrayList<>();
        for (String token : tokens) {
            if (!token.isEmpty()) {
                result.add(transposeChord(token, semitones, preference));
            }
        }
        return String.join(" ", result);
    }

    public List<String> splitProgression(String progression) {
        String normalized = progression == null ? "" : progression.trim()
                .replace('，', ' ')
                .replace(',', ' ')
                .replace("→", " ")
                .replace("->", " ");
        List<String> result = new ArrayList<>();
        if (!normalized.isEmpty()) {
            for (String token : normalized.split("\\s+")) {
                if (!token.isEmpty()) {
                    result.add(token);
                }
            }
        }
        return result;
    }

    private static String normalizeAccidental(String accidental) {
        if (accidental == null) {
            return "";
        }
        return accidental.replace("♯", "#").replace("♭", "b");
    }
}
