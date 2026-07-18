package com.k2.music;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public final class Voicing {
    public static final int MUTED = -1;
    private static final int[] STANDARD_TUNING_MIDI = {40, 45, 50, 55, 59, 64};

    public final String name;
    public final int[] frets;
    public final int[] fingers;
    public final int startFret;
    public final int displayFrets;
    public final String difficulty;
    public final boolean recommended;
    public final boolean simplified;
    public final boolean barre;
    public final String description;
    public final String[] stringNotes;
    public final int[] midiNotes;
    public final ChordShape sourceShape;
    public final List<String> omittedIntervals;

    public Voicing(
            String name,
            int[] frets,
            int[] fingers,
            int startFret,
            int displayFrets,
            String difficulty,
            boolean recommended,
            boolean simplified,
            boolean barre,
            String description
    ) {
        this(name, frets, fingers, startFret, displayFrets, difficulty, recommended, simplified, barre, description, null);
    }

    public Voicing(
            String name,
            int[] frets,
            int[] fingers,
            int startFret,
            int displayFrets,
            String difficulty,
            boolean recommended,
            boolean simplified,
            boolean barre,
            String description,
            ChordShape sourceShape
    ) {
        this(name, frets, fingers, startFret, displayFrets, difficulty, recommended, simplified,
                barre, description, sourceShape,
                sourceShape == null ? Collections.emptyList() : sourceShape.omittedIntervals);
    }

    public Voicing(
            String name,
            int[] frets,
            int[] fingers,
            int startFret,
            int displayFrets,
            String difficulty,
            boolean recommended,
            boolean simplified,
            boolean barre,
            String description,
            ChordShape sourceShape,
            List<String> omittedIntervals
    ) {
        if (frets.length != 6 || fingers.length != 6) {
            throw new IllegalArgumentException("Voicing must describe six strings.");
        }
        this.name = name;
        this.frets = Arrays.copyOf(frets, frets.length);
        this.fingers = Arrays.copyOf(fingers, fingers.length);
        this.startFret = startFret;
        this.displayFrets = displayFrets;
        this.difficulty = difficulty;
        this.recommended = recommended;
        this.simplified = simplified;
        this.barre = barre;
        this.description = description;
        this.sourceShape = sourceShape;
        this.omittedIntervals = Collections.unmodifiableList(new ArrayList<>(
                omittedIntervals == null ? Collections.emptyList() : omittedIntervals
        ));
        this.midiNotes = buildMidiNotes(this.frets);
        this.stringNotes = buildStringNotes(this.midiNotes);
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

    public int[] playableMidiNotes() {
        int count = 0;
        for (int midi : midiNotes) {
            if (midi > 0) {
                count++;
            }
        }
        int[] notes = new int[count];
        int index = 0;
        for (int midi : midiNotes) {
            if (midi > 0) {
                notes[index++] = midi;
            }
        }
        return notes;
    }

    private static int[] buildMidiNotes(int[] frets) {
        int[] notes = new int[6];
        for (int i = 0; i < frets.length; i++) {
            notes[i] = frets[i] == MUTED ? 0 : STANDARD_TUNING_MIDI[i] + frets[i];
        }
        return notes;
    }

    private static String[] buildStringNotes(int[] midiNotes) {
        String[] notes = new String[midiNotes.length];
        for (int i = 0; i < midiNotes.length; i++) {
            notes[i] = midiNotes[i] == 0 ? null : NoteUtils.midiToNoteName(midiNotes[i]);
        }
        return notes;
    }
}
