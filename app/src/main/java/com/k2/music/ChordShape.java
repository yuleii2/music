package com.k2.music;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * 吉他上的一个具体按法。它通过 qualityId 指向和弦类型公式，同时保存六根弦的品位和指法。
 */
public final class ChordShape {
    public static final int MUTED = -1;

    public final String id;
    public final String name;
    public final String root;
    public final String qualityId;
    public final String bassNote;
    public final int[] frets;
    public final int[] fingers;
    public final int baseFret;
    public final int visibleFretCount;
    public final int difficulty;
    public final String shapeType;
    public final boolean common;
    public final boolean simplified;
    public final boolean barre;
    public final String note;
    public final List<String> tags;
    public final List<String> omittedIntervals;

    public ChordShape(
            String root,
            String qualityId,
            int[] frets,
            int[] fingers,
            int baseFret,
            int difficulty,
            String shapeType,
            String note
    ) {
        this("", "", root, qualityId, "", frets, fingers, baseFret, 0, difficulty, shapeType,
                difficulty <= 3, "simplified".equalsIgnoreCase(shapeType), false, note, Collections.emptyList());
    }

    public ChordShape(
            String name,
            String root,
            String qualityId,
            String bassNote,
            int[] frets,
            int[] fingers,
            int baseFret,
            int difficulty,
            String shapeType,
            String note
    ) {
        this("", name, root, qualityId, bassNote, frets, fingers, baseFret, 0, difficulty, shapeType,
                difficulty <= 3, "simplified".equalsIgnoreCase(shapeType), false, note, Collections.emptyList());
    }

    public ChordShape(
            String id,
            String name,
            String root,
            String qualityId,
            String bassNote,
            int[] frets,
            int[] fingers,
            int baseFret,
            int visibleFretCount,
            int difficulty,
            String shapeType,
            boolean common,
            boolean simplified,
            boolean barre,
            String note,
            List<String> tags
    ) {
        this(id, name, root, qualityId, bassNote, frets, fingers, baseFret, visibleFretCount,
                difficulty, shapeType, common, simplified, barre, note, tags, Collections.emptyList());
    }

    public ChordShape(
            String id,
            String name,
            String root,
            String qualityId,
            String bassNote,
            int[] frets,
            int[] fingers,
            int baseFret,
            int visibleFretCount,
            int difficulty,
            String shapeType,
            boolean common,
            boolean simplified,
            boolean barre,
            String note,
            List<String> tags,
            List<String> omittedIntervals
    ) {
        if (frets == null || fingers == null || frets.length != 6 || fingers.length != 6) {
            throw new IllegalArgumentException("ChordShape must describe exactly six strings.");
        }
        this.id = id == null ? "" : id;
        this.name = name == null ? "" : name;
        this.root = root;
        this.qualityId = qualityId;
        this.bassNote = bassNote == null ? "" : bassNote;
        this.frets = Arrays.copyOf(frets, frets.length);
        this.fingers = Arrays.copyOf(fingers, fingers.length);
        this.baseFret = Math.max(1, baseFret);
        this.visibleFretCount = visibleFretCount > 0 ? visibleFretCount : calculateDisplayFrets(this.frets, this.baseFret);
        this.difficulty = clamp(difficulty, 1, 5);
        this.shapeType = shapeType == null ? "" : shapeType;
        this.common = common;
        this.simplified = simplified;
        this.barre = barre;
        this.note = note == null ? "" : note;
        this.tags = Collections.unmodifiableList(new ArrayList<>(tags == null ? Collections.emptyList() : tags));
        this.omittedIntervals = Collections.unmodifiableList(new ArrayList<>(
                omittedIntervals == null ? Collections.emptyList() : omittedIntervals
        ));
    }

    public String symbol(ChordQuality quality) {
        String suffix = quality == null ? suffixForQuality(qualityId) : quality.symbolSuffix;
        String symbol = root + suffix;
        return bassNote.isEmpty() ? symbol : symbol + "/" + bassNote;
    }

    public String displayName(ChordQuality quality) {
        if (!name.trim().isEmpty()) {
            return name;
        }
        String type = shapeType == null || shapeType.isEmpty() ? "常见按法" : shapeTypeLabel();
        return symbol(quality) + " " + type;
    }

    public boolean isSlash() {
        return !bassNote.isEmpty() || "slash".equalsIgnoreCase(shapeType);
    }

    public boolean isBarre() {
        if (barre) {
            return true;
        }
        if ("barre".equalsIgnoreCase(shapeType) || "caged".equalsIgnoreCase(shapeType)) {
            return true;
        }
        for (int i = 0; i < fingers.length; i++) {
            if (fingers[i] != 1 || frets[i] <= 0) {
                continue;
            }
            for (int j = i + 1; j < fingers.length; j++) {
                if (fingers[j] == 1 && frets[j] == frets[i]) {
                    return true;
                }
            }
        }
        return false;
    }

    public boolean isSimplified() {
        return simplified || "simplified".equalsIgnoreCase(shapeType);
    }

    public String difficultyLabel() {
        switch (difficulty) {
            case 1:
                return "入门";
            case 2:
                return "常见";
            case 3:
                return "中级";
            case 4:
                return "进阶";
            case 5:
            default:
                return "高级";
        }
    }

    public int displayFrets() {
        return visibleFretCount;
    }

    public Voicing toVoicing(ChordQuality quality) {
        return new Voicing(
                displayName(quality),
                frets,
                fingers,
                baseFret,
                displayFrets(),
                difficultyLabel(),
                common,
                isSimplified(),
                isBarre(),
                note,
                this,
                omittedIntervals
        );
    }

    public String fretPattern() {
        StringBuilder builder = new StringBuilder();
        for (int fret : frets) {
            if (builder.length() > 0) {
                builder.append('-');
            }
            if (fret == MUTED) {
                builder.append('x');
            } else {
                builder.append(fret);
            }
        }
        return builder.toString();
    }

    private String shapeTypeLabel() {
        if ("open".equalsIgnoreCase(shapeType)) {
            return "开放按法";
        }
        if ("barre".equalsIgnoreCase(shapeType)) {
            return "横按";
        }
        if ("caged".equalsIgnoreCase(shapeType)) {
            return "CAGED";
        }
        if ("jazz".equalsIgnoreCase(shapeType)) {
            return "爵士按法";
        }
        if ("inversion".equalsIgnoreCase(shapeType) || "slash".equalsIgnoreCase(shapeType)) {
            return "转位按法";
        }
        if ("triad".equalsIgnoreCase(shapeType)) {
            return "三和弦小形状";
        }
        if ("simplified".equalsIgnoreCase(shapeType)) {
            return "简化按法";
        }
        return shapeType;
    }

    private static String suffixForQuality(String qualityId) {
        if ("maj".equals(qualityId)) {
            return "";
        }
        return qualityId == null ? "" : qualityId;
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static int calculateDisplayFrets(int[] frets, int baseFret) {
        int max = baseFret;
        for (int fret : frets) {
            if (fret > max) {
                max = fret;
            }
        }
        return Math.max(4, max - baseFret + 1);
    }
}
