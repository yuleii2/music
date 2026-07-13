package com.k2.music;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** One explicitly accepted AI practice-plan item waiting to open in the offline practice page. */
public final class PracticePlanDraftStore {
    public static final class Draft {
        public final String title;
        public final List<String> chordSymbols;
        public final int bpm;
        public final int durationSeconds;

        Draft(String title, List<String> chordSymbols, int bpm, int durationSeconds) {
            this.title = title;
            this.chordSymbols = Collections.unmodifiableList(new ArrayList<>(chordSymbols));
            this.bpm = bpm;
            this.durationSeconds = durationSeconds;
        }
    }

    private static final String PREFS = "accepted_practice_plan_draft";
    private final SharedPreferences preferences;

    public PracticePlanDraftStore(Context context) {
        preferences = context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public void save(AiPracticePlanResult.Day day) {
        StringBuilder symbols = new StringBuilder();
        for (Chord chord : day.chords) {
            if (symbols.length() > 0) symbols.append(' ');
            symbols.append(chord.symbol);
        }
        preferences.edit()
                .putString("title", day.title)
                .putString("chords", symbols.toString())
                .putInt("bpm", day.bpm)
                .putInt("duration_seconds", Math.max(5, Math.min(3600, day.durationMinutes * 60)))
                .apply();
    }

    public Draft consume() {
        String symbols = preferences.getString("chords", "");
        if (symbols == null || symbols.trim().isEmpty()) return null;
        List<String> chordSymbols = new ArrayList<>();
        for (String symbol : symbols.trim().split("\\s+")) {
            if (!symbol.isEmpty()) chordSymbols.add(symbol);
        }
        Draft result = new Draft(
                preferences.getString("title", "AI 练习计划"),
                chordSymbols,
                preferences.getInt("bpm", 60),
                preferences.getInt("duration_seconds", 300)
        );
        preferences.edit().clear().apply();
        return result;
    }
}
