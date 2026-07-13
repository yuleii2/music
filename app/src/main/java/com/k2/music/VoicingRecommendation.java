package com.k2.music;

import java.util.Objects;

/** One selected voicing in an optimized progression path. */
public final class VoicingRecommendation {
    public final int stepIndex;
    public final String chordSymbol;
    public final String voicingId;
    public final Voicing voicing;
    public final VoicingTransitionScorer.Score transitionScore;
    public final double cumulativeCost;
    public final String reason;

    public VoicingRecommendation(
            int stepIndex,
            String chordSymbol,
            String voicingId,
            Voicing voicing,
            VoicingTransitionScorer.Score transitionScore,
            double cumulativeCost,
            String reason
    ) {
        if (stepIndex < 0) {
            throw new IllegalArgumentException("Step index cannot be negative.");
        }
        this.stepIndex = stepIndex;
        this.chordSymbol = Objects.requireNonNull(chordSymbol, "chordSymbol");
        this.voicingId = Objects.requireNonNull(voicingId, "voicingId");
        this.voicing = Objects.requireNonNull(voicing, "voicing");
        this.transitionScore = Objects.requireNonNull(transitionScore, "transitionScore");
        this.cumulativeCost = cumulativeCost;
        this.reason = Objects.requireNonNull(reason, "reason");
    }
}
