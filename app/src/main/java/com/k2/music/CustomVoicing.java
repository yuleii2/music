package com.k2.music;

import java.util.Arrays;
import java.util.UUID;

/** A user-owned local voicing. Built-in JSON entries are never mutated. */
public final class CustomVoicing {
    public final String id;
    public final String chordSymbol;
    public final String name;
    public final int[] frets;
    public final int[] fingers;
    public final int startFret;
    public final String note;
    public final long createdAt;

    public CustomVoicing(
            String id,
            String chordSymbol,
            String name,
            int[] frets,
            int[] fingers,
            int startFret,
            String note,
            long createdAt
    ) {
        if (chordSymbol == null || chordSymbol.trim().isEmpty()) {
            throw new IllegalArgumentException("所属和弦不能为空");
        }
        if (frets == null || frets.length != 6) {
            throw new IllegalArgumentException("自定义指法必须包含六根弦");
        }
        if (fingers != null && fingers.length != 6) {
            throw new IllegalArgumentException("指法编号必须包含六根弦或留空");
        }
        this.id = id == null || id.trim().isEmpty() ? UUID.randomUUID().toString() : id;
        this.chordSymbol = chordSymbol.trim();
        this.name = name == null || name.trim().isEmpty() ? this.chordSymbol + " 自定义指法" : name.trim();
        this.frets = Arrays.copyOf(frets, frets.length);
        this.fingers = fingers == null ? new int[6] : Arrays.copyOf(fingers, fingers.length);
        this.startFret = Math.max(1, startFret);
        this.note = note == null ? "" : note.trim();
        this.createdAt = createdAt <= 0 ? System.currentTimeMillis() : createdAt;
    }

    public Voicing toVoicing() {
        int max = startFret;
        for (int fret : frets) {
            max = Math.max(max, fret);
        }
        return new Voicing(
                name,
                frets,
                fingers,
                startFret,
                Math.max(5, max - startFret + 1),
                "自定义",
                false,
                false,
                detectsBarre(),
                note
        );
    }

    private boolean detectsBarre() {
        for (int i = 0; i < fingers.length; i++) {
            if (fingers[i] <= 0 || frets[i] <= 0) continue;
            for (int j = i + 1; j < fingers.length; j++) {
                if (fingers[i] == fingers[j] && frets[i] == frets[j]) return true;
            }
        }
        return false;
    }
}
