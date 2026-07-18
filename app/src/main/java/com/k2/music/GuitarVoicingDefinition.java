package com.k2.music;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/** Full-fidelity JSON model for one recorded six-string guitar voicing. */
public final class GuitarVoicingDefinition {
    public final String id;
    public final String chordSymbol;
    public final String name;
    public final String root;
    public final String formulaId;
    /** Lowest sounding pitch-class metadata from JSON, when supplied. */
    public final String bassNote;
    /** Slash-chord bass from the chord symbol; empty for ordinary root-position symbols. */
    public final String chordBassNote;
    public final int[] frets;
    public final int[] fingers;
    public final int startFret;
    public final int visibleFretCount;
    public final int difficulty;
    public final boolean common;
    public final boolean simplified;
    public final boolean barre;
    public final String description;
    public final List<String> tags;
    /** Formula intervals intentionally omitted to keep an advanced guitar shape playable. */
    public final List<String> omittedIntervals;

    public GuitarVoicingDefinition(
            String id,
            String chordSymbol,
            String name,
            String root,
            String formulaId,
            String bassNote,
            int[] frets,
            int[] fingers,
            int startFret,
            int visibleFretCount,
            int difficulty,
            boolean common,
            boolean simplified,
            boolean barre,
            String description,
            List<String> tags
    ) {
        this(id, chordSymbol, name, root, formulaId, bassNote, bassNote, frets, fingers, startFret,
                visibleFretCount, difficulty, common, simplified, barre, description, tags,
                Collections.emptyList());
    }

    public GuitarVoicingDefinition(
            String id,
            String chordSymbol,
            String name,
            String root,
            String formulaId,
            String bassNote,
            String chordBassNote,
            int[] frets,
            int[] fingers,
            int startFret,
            int visibleFretCount,
            int difficulty,
            boolean common,
            boolean simplified,
            boolean barre,
            String description,
            List<String> tags
    ) {
        this(id, chordSymbol, name, root, formulaId, bassNote, chordBassNote, frets, fingers, startFret,
                visibleFretCount, difficulty, common, simplified, barre, description, tags,
                Collections.emptyList());
    }

    public GuitarVoicingDefinition(
            String id,
            String chordSymbol,
            String name,
            String root,
            String formulaId,
            String bassNote,
            String chordBassNote,
            int[] frets,
            int[] fingers,
            int startFret,
            int visibleFretCount,
            int difficulty,
            boolean common,
            boolean simplified,
            boolean barre,
            String description,
            List<String> tags,
            List<String> omittedIntervals
    ) {
        if (frets == null || fingers == null || frets.length != 6 || fingers.length != 6) {
            throw new IllegalArgumentException("A guitar voicing must describe exactly six strings.");
        }
        this.id = text(id);
        this.chordSymbol = text(chordSymbol);
        this.name = text(name);
        this.root = text(root);
        this.formulaId = text(formulaId);
        this.bassNote = text(bassNote);
        this.chordBassNote = text(chordBassNote);
        this.frets = Arrays.copyOf(frets, frets.length);
        this.fingers = Arrays.copyOf(fingers, fingers.length);
        this.startFret = Math.max(1, startFret);
        this.visibleFretCount = Math.max(1, visibleFretCount);
        this.difficulty = Math.max(1, Math.min(5, difficulty));
        this.common = common;
        this.simplified = simplified;
        this.barre = barre;
        this.description = text(description);
        this.tags = Collections.unmodifiableList(new ArrayList<>(tags == null ? Collections.emptyList() : tags));
        this.omittedIntervals = Collections.unmodifiableList(new ArrayList<>(
                omittedIntervals == null ? Collections.emptyList() : omittedIntervals
        ));
    }

    public ChordShape toChordShape() {
        return new ChordShape(
                id,
                name,
                root,
                formulaId,
                chordBassNote,
                frets,
                fingers,
                startFret,
                visibleFretCount,
                difficulty,
                shapeType(),
                common,
                simplified,
                barre,
                description,
                tags,
                omittedIntervals
        );
    }

    public static GuitarVoicingDefinition fromChordShape(ChordShape shape, ChordQuality quality, int index) {
        String symbol = shape.symbol(quality);
        return new GuitarVoicingDefinition(
                shape.id.isEmpty() ? symbol.toLowerCase(Locale.US).replace("#", "s").replace("/", "-") + "-" + index : shape.id,
                symbol,
                shape.displayName(quality),
                shape.root,
                shape.qualityId,
                shape.bassNote,
                shape.bassNote,
                shape.frets,
                shape.fingers,
                shape.baseFret,
                shape.displayFrets(),
                shape.difficulty,
                shape.common,
                shape.isSimplified(),
                shape.isBarre(),
                shape.note,
                shape.tags,
                shape.omittedIntervals
        );
    }

    private String shapeType() {
        if (!chordBassNote.isEmpty() || containsTag("slash") || containsTag("inversion")) {
            return "slash";
        }
        if (simplified || containsTag("simplified")) {
            return "simplified";
        }
        if (barre || containsTag("barre")) {
            return "barre";
        }
        String[] knownTypes = {"open", "caged", "jazz", "triad", "inversion"};
        for (String type : knownTypes) {
            if (containsTag(type)) {
                return type;
            }
        }
        return "common";
    }

    private boolean containsTag(String expected) {
        for (String tag : tags) {
            if (expected.equalsIgnoreCase(tag)) {
                return true;
            }
        }
        return false;
    }

    private static String text(String value) {
        return value == null ? "" : value;
    }
}
