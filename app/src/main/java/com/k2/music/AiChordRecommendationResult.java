package com.k2.music;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class AiChordRecommendationResult {
    public static final class Candidate {
        public final String symbol;
        public final String reason;
        public final Chord localChord;
        public final List<Voicing> localVoicings;

        Candidate(String symbol, String reason, Chord localChord, List<Voicing> localVoicings) {
            this.symbol = symbol;
            this.reason = reason;
            this.localChord = localChord;
            this.localVoicings = Collections.unmodifiableList(new ArrayList<>(localVoicings));
        }
    }

    public final List<Candidate> candidates;
    public final List<String> rejectedSymbols;

    AiChordRecommendationResult(List<Candidate> candidates, List<String> rejectedSymbols) {
        this.candidates = Collections.unmodifiableList(new ArrayList<>(candidates));
        this.rejectedSymbols = Collections.unmodifiableList(new ArrayList<>(rejectedSymbols));
    }
}
