package com.k2.music;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public final class NoteUtils {
    private static final String[] SHARP_NAMES = {
            "C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B"
    };
    private static final Map<String, Integer> NOTE_TO_SEMITONE = new HashMap<>();

    static {
        NOTE_TO_SEMITONE.put("C", 0);
        NOTE_TO_SEMITONE.put("B#", 0);
        NOTE_TO_SEMITONE.put("C#", 1);
        NOTE_TO_SEMITONE.put("DB", 1);
        NOTE_TO_SEMITONE.put("D", 2);
        NOTE_TO_SEMITONE.put("D#", 3);
        NOTE_TO_SEMITONE.put("EB", 3);
        NOTE_TO_SEMITONE.put("E", 4);
        NOTE_TO_SEMITONE.put("FB", 4);
        NOTE_TO_SEMITONE.put("E#", 5);
        NOTE_TO_SEMITONE.put("F", 5);
        NOTE_TO_SEMITONE.put("F#", 6);
        NOTE_TO_SEMITONE.put("GB", 6);
        NOTE_TO_SEMITONE.put("G", 7);
        NOTE_TO_SEMITONE.put("G#", 8);
        NOTE_TO_SEMITONE.put("AB", 8);
        NOTE_TO_SEMITONE.put("A", 9);
        NOTE_TO_SEMITONE.put("A#", 10);
        NOTE_TO_SEMITONE.put("BB", 10);
        NOTE_TO_SEMITONE.put("B", 11);
        NOTE_TO_SEMITONE.put("CB", 11);
    }

    private NoteUtils() {
    }

    public static String midiToNoteName(int midi) {
        int semitone = Math.floorMod(midi, 12);
        return SHARP_NAMES[semitone];
    }

    public static int noteNameToMiddleMidi(String noteName) {
        String normalized = noteName.replace("♯", "#")
                .replace("♭", "b")
                .toUpperCase(Locale.US);
        Integer semitone = NOTE_TO_SEMITONE.get(normalized);
        if (semitone == null) {
            return 60;
        }
        return 60 + semitone;
    }
}

