package com.k2.music;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class AiProgressionResult {
    public static final class Step {
        public final String symbol;
        public final int beats;
        public final Chord localChord;

        Step(String symbol, int beats, Chord localChord) {
            this.symbol = symbol;
            this.beats = beats;
            this.localChord = localChord;
        }
    }

    public final String key;
    public final int tempoSuggestion;
    public final List<Step> chords;
    public final String explanation;
    public final String localAnalysis;
    public final List<String> rejectedSymbols;

    AiProgressionResult(String key, int tempoSuggestion, List<Step> chords, String explanation, List<String> rejectedSymbols) {
        this(key, tempoSuggestion, chords, explanation, rejectedSymbols, "");
    }

    AiProgressionResult(
            String key,
            int tempoSuggestion,
            List<Step> chords,
            String explanation,
            List<String> rejectedSymbols,
            String localAnalysis
    ) {
        this.key = key;
        this.tempoSuggestion = tempoSuggestion;
        this.chords = Collections.unmodifiableList(new ArrayList<>(chords));
        this.explanation = explanation;
        this.localAnalysis = localAnalysis == null ? "" : localAnalysis;
        this.rejectedSymbols = Collections.unmodifiableList(new ArrayList<>(rejectedSymbols));
    }
}
