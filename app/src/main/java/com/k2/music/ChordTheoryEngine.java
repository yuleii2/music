package com.k2.music;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Deterministic twelve-tone chord calculation independent of guitar shapes. */
public final class ChordTheoryEngine {
    public List<String> notesFor(String root, ChordFormula formula) {
        return chordTones(root, formula);
    }

    public List<String> chordTones(String root, ChordFormula formula) {
        if (formula == null) {
            throw new IllegalArgumentException("Chord formula must not be null.");
        }
        String writtenRoot = NoteUtils.normalizeNoteName(root);
        if (writtenRoot.isEmpty() || NoteUtils.trySemitone(writtenRoot) == null) {
            throw new IllegalArgumentException("Unsupported root note: " + root);
        }
        List<String> tones = new ArrayList<>();
        for (String interval : formula.intervals) {
            tones.add(NoteUtils.spellInterval(writtenRoot, interval));
        }
        return Collections.unmodifiableList(tones);
    }

    public Chord buildChord(String root, ChordFormula formula) {
        return buildChord(root, "", formula, Collections.emptyList());
    }

    public Chord buildChord(ChordSymbolParser.ParseResult parsed) {
        if (parsed == null || !parsed.recognized) {
            throw new IllegalArgumentException("A recognized parsed chord is required.");
        }
        return buildChord(parsed.root, parsed.bassNote, parsed.formula, Collections.emptyList());
    }

    public Chord buildChord(String root, String bassNote, ChordFormula formula, List<ChordShape> shapes) {
        String writtenRoot = NoteUtils.normalizeNoteName(root);
        String writtenBass = bassNote == null || bassNote.trim().isEmpty()
                ? ""
                : NoteUtils.normalizeNoteName(bassNote);
        if (writtenRoot.isEmpty() || NoteUtils.trySemitone(writtenRoot) == null) {
            throw new IllegalArgumentException("Unsupported root note: " + root);
        }
        if (!writtenBass.isEmpty() && NoteUtils.trySemitone(writtenBass) == null) {
            throw new IllegalArgumentException("Unsupported bass note: " + bassNote);
        }
        List<ChordShape> safeShapes = shapes == null ? Collections.emptyList() : new ArrayList<>(shapes);
        List<Voicing> voicings = new ArrayList<>();
        ChordQuality quality = formula.toChordQuality();
        for (ChordShape shape : safeShapes) {
            voicings.add(shape.toVoicing(quality));
        }
        String symbol = writtenRoot + formula.suffix + (writtenBass.isEmpty() ? "" : "/" + writtenBass);
        String qualityName = writtenBass.isEmpty() ? formula.chineseName : "分数和弦";
        String description;
        if (writtenBass.isEmpty()) {
            description = symbol + " " + formula.chineseName + "：" + formula.description;
        } else {
            description = symbol + " 表示以 " + writtenBass + " 作为最低音的 "
                    + writtenRoot + formula.suffix + "；" + inversionDescription(
                    writtenRoot,
                    writtenBass,
                    formula
            ) + "。";
        }
        return new Chord(
                symbol,
                symbol + " " + qualityName,
                writtenRoot,
                formula.id,
                qualityName,
                writtenBass,
                formula.intervals,
                chordTones(writtenRoot, formula),
                buildAliases(writtenRoot, writtenBass, formula),
                description,
                safeShapes,
                voicings,
                formula.extensions,
                formula.alterations,
                Collections.emptyList(),
                formula.additions,
                formula.requiredIntervals,
                formula.optionalIntervals,
                formula.omittableIntervals,
                formula.requiredAnyOf
        );
    }

    private static List<String> buildAliases(String root, String bass, ChordFormula formula) {
        String slash = bass.isEmpty() ? "" : "/" + bass;
        Set<String> aliases = new LinkedHashSet<>();
        for (String alias : formula.aliases) {
            aliases.add(root + alias + slash);
        }
        if (!formula.chineseName.isEmpty()) {
            aliases.add(root + formula.chineseName + slash);
            aliases.add(root + formula.chineseName.replace("和弦", "") + slash);
        }
        if (!formula.englishName.isEmpty()) {
            aliases.add(root + formula.englishName + slash);
            aliases.add(root + formula.englishName.replace(" chord", "") + slash);
        }
        switch (formula.id) {
            case "maj":
                aliases.add(root + "maj" + slash);
                aliases.add(root + "major" + slash);
                break;
            case "m":
                aliases.add(root + "min" + slash);
                aliases.add(root + "minor" + slash);
                aliases.add(root + "-" + slash);
                break;
            case "maj7":
                aliases.add(root + "M7" + slash);
                aliases.add(root + "major7" + slash);
                aliases.add(root + "Δ7" + slash);
                aliases.add(root + "△7" + slash);
                break;
            case "m7":
                aliases.add(root + "min7" + slash);
                aliases.add(root + "minor7" + slash);
                aliases.add(root + "-7" + slash);
                break;
            case "dim":
                aliases.add(root + "°" + slash);
                break;
            case "dim7":
                aliases.add(root + "°7" + slash);
                break;
            case "m7b5":
                aliases.add(root + "ø" + slash);
                break;
            case "aug":
                aliases.add(root + "+" + slash);
                break;
            default:
                break;
        }
        aliases.remove(root + formula.suffix + slash);
        return new ArrayList<>(aliases);
    }

    private String inversionDescription(String root, String bass, ChordFormula formula) {
        List<String> tones = chordTones(root, formula);
        int bassPitch = NoteUtils.semitone(bass);
        int toneIndex = -1;
        for (int index = 0; index < tones.size(); index++) {
            if (NoteUtils.semitone(tones.get(index)) == bassPitch) {
                toneIndex = index;
                break;
            }
        }
        if (toneIndex == 1) {
            return "这是第一转位斜杠和弦，常用于平滑连接低音线";
        }
        if (toneIndex == 2) {
            return "这是第二转位斜杠和弦，常用于平滑连接低音线";
        }
        if (toneIndex > 2) {
            return "这是以和弦扩展音为低音的斜杠和弦";
        }
        if (toneIndex == 0) {
            return "指定低音与根音相同，理论主体保持不变";
        }
        return "指定低音不属于主体和弦音，作为独立低音或踏板音使用";
    }
}
