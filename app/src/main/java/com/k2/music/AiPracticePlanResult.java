package com.k2.music;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class AiPracticePlanResult {
    public static final class Day {
        public final String title;
        public final int durationMinutes;
        public final int bpm;
        public final List<Chord> chords;

        Day(String title, int durationMinutes, int bpm, List<Chord> chords) {
            this.title = title;
            this.durationMinutes = durationMinutes;
            this.bpm = bpm;
            this.chords = Collections.unmodifiableList(new ArrayList<>(chords));
        }
    }

    public final List<Day> days;

    AiPracticePlanResult(List<Day> days) {
        this.days = Collections.unmodifiableList(new ArrayList<>(days));
    }
}
