package com.k2.music;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class AiProgressionOptimizationResult {
    public final List<AiProgressionResult.Step> proposedChords;
    public final String explanation;
    public final String key;
    public final int tempoSuggestion;
    public final String localAnalysis;

    AiProgressionOptimizationResult(List<AiProgressionResult.Step> proposedChords, String explanation) {
        this(proposedChords, explanation, "", 80, "");
    }

    AiProgressionOptimizationResult(
            List<AiProgressionResult.Step> proposedChords,
            String explanation,
            String key,
            int tempoSuggestion,
            String localAnalysis
    ) {
        this.proposedChords = Collections.unmodifiableList(new ArrayList<>(proposedChords));
        this.explanation = explanation;
        this.key = key;
        this.tempoSuggestion = tempoSuggestion;
        this.localAnalysis = localAnalysis;
    }
}
