package com.k2.music;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Small, dependency-free pitch-class helpers shared by the offline music tools. */
public final class MusicTheoryUtils {
    public enum AccidentalPreference { SHARPS, FLATS, AUTO }

    public static final int[] STANDARD_TUNING_MIDI = {40, 45, 50, 55, 59, 64};
    private static final String[] SHARPS = {"C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B"};
    private static final String[] FLATS = {"C", "Db", "D", "Eb", "E", "F", "Gb", "G", "Ab", "A", "Bb", "B"};

    private MusicTheoryUtils() {
    }

    public static int noteToPitchClass(String rawNote) {
        if (rawNote == null) {
            return -1;
        }
        String note = rawNote.trim()
                .replace("♯", "#")
                .replace("♭", "b")
                .replace("＃", "#");
        if (note.isEmpty()) {
            return -1;
        }
        char letter = Character.toUpperCase(note.charAt(0));
        int pitch;
        switch (letter) {
            case 'C': pitch = 0; break;
            case 'D': pitch = 2; break;
            case 'E': pitch = 4; break;
            case 'F': pitch = 5; break;
            case 'G': pitch = 7; break;
            case 'A': pitch = 9; break;
            case 'B': pitch = 11; break;
            default: return -1;
        }
        for (int i = 1; i < note.length(); i++) {
            char accidental = note.charAt(i);
            if (accidental == '#') {
                pitch++;
            } else if (accidental == 'b' || accidental == 'B') {
                pitch--;
            } else if (accidental == 'x' || accidental == 'X') {
                pitch += 2;
            } else if (Character.isDigit(accidental) || accidental == '-' || accidental == '+') {
                break;
            } else {
                return -1;
            }
        }
        return Math.floorMod(pitch, 12);
    }

    public static String pitchClassName(int pitchClass, AccidentalPreference preference) {
        int normalized = Math.floorMod(pitchClass, 12);
        return preference == AccidentalPreference.FLATS ? FLATS[normalized] : SHARPS[normalized];
    }

    public static String normalizeNote(String note, AccidentalPreference preference) {
        int pitchClass = noteToPitchClass(note);
        if (pitchClass < 0) {
            throw new IllegalArgumentException("无法识别音符：" + note);
        }
        AccidentalPreference resolved = resolvePreference(preference, note);
        return pitchClassName(pitchClass, resolved);
    }

    public static String transposeNote(String note, int semitones, AccidentalPreference preference) {
        int pitchClass = noteToPitchClass(note);
        if (pitchClass < 0) {
            throw new IllegalArgumentException("无法识别音符：" + note);
        }
        return pitchClassName(pitchClass + semitones, resolvePreference(preference, note));
    }

    public static AccidentalPreference resolvePreference(AccidentalPreference preference, String hint) {
        if (preference != null && preference != AccidentalPreference.AUTO) {
            return preference;
        }
        return hint != null && (hint.indexOf('b') >= 0 || hint.indexOf('♭') >= 0)
                ? AccidentalPreference.FLATS
                : AccidentalPreference.SHARPS;
    }

    public static Set<Integer> pitchClasses(Collection<String> notes) {
        LinkedHashSet<Integer> result = new LinkedHashSet<>();
        if (notes == null) {
            return result;
        }
        for (String note : notes) {
            int pitchClass = noteToPitchClass(note);
            if (pitchClass >= 0) {
                result.add(pitchClass);
            }
        }
        return result;
    }

    public static List<String> namesForPitchClasses(Collection<Integer> pitchClasses, AccidentalPreference preference) {
        List<String> result = new ArrayList<>();
        if (pitchClasses != null) {
            for (Integer pitchClass : pitchClasses) {
                if (pitchClass != null) {
                    result.add(pitchClassName(pitchClass, preference));
                }
            }
        }
        return result;
    }

    public static String normalizeToken(String token) {
        return token == null ? "" : token.trim().replace("♯", "#").replace("♭", "b").toUpperCase(Locale.US);
    }
}
