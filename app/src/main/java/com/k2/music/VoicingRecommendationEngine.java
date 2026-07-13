package com.k2.music;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Chooses a deterministic minimum-cost voicing path across a progression. Constraints are hard
 * filters; dynamic programming is used after filtering rather than making a random/greedy pick.
 */
public final class VoicingRecommendationEngine {
    private static final double EPSILON = 0.000_000_1;

    private final VoicingTransitionScorer scorer;

    public VoicingRecommendationEngine() {
        this(new VoicingTransitionScorer());
    }

    public VoicingRecommendationEngine(VoicingTransitionScorer scorer) {
        this.scorer = Objects.requireNonNull(scorer, "scorer");
    }

    public List<VoicingRecommendation> recommend(
            ChordProgression progression,
            Map<String, ? extends List<Voicing>> candidatesByChord,
            VoicingRecommendationMode mode,
            PracticePreferences preferences
    ) {
        Objects.requireNonNull(progression, "progression");
        List<String> chordSymbols = new ArrayList<>(progression.steps.size());
        for (ProgressionStep step : progression.steps) {
            chordSymbols.add(step.chordSymbol);
        }
        return recommend(chordSymbols, candidatesByChord, mode, preferences);
    }

    public List<VoicingRecommendation> recommend(
            List<String> chordSymbols,
            Map<String, ? extends List<Voicing>> candidatesByChord,
            VoicingRecommendationMode mode,
            PracticePreferences preferences
    ) {
        Objects.requireNonNull(chordSymbols, "chordSymbols");
        Objects.requireNonNull(candidatesByChord, "candidatesByChord");
        Objects.requireNonNull(mode, "mode");
        Objects.requireNonNull(preferences, "preferences");
        if (chordSymbols.isEmpty()) {
            return Collections.emptyList();
        }

        List<List<Candidate>> layers = new ArrayList<>(chordSymbols.size());
        for (String symbol : chordSymbols) {
            List<Voicing> source = candidatesByChord.get(symbol);
            List<Candidate> candidates = eligibleCandidates(symbol, source, preferences);
            if (candidates.isEmpty()) {
                return Collections.emptyList();
            }
            layers.add(candidates);
        }

        double[][] costs = new double[layers.size()][];
        int[][] previous = new int[layers.size()][];
        for (int layer = 0; layer < layers.size(); layer++) {
            costs[layer] = new double[layers.get(layer).size()];
            previous[layer] = new int[layers.get(layer).size()];
        }

        for (int candidateIndex = 0; candidateIndex < layers.get(0).size(); candidateIndex++) {
            Candidate candidate = layers.get(0).get(candidateIndex);
            costs[0][candidateIndex] = scorer.score(null, candidate.voicing, mode, preferences, candidate.id).totalCost;
            previous[0][candidateIndex] = -1;
        }

        for (int layer = 1; layer < layers.size(); layer++) {
            List<Candidate> currentLayer = layers.get(layer);
            List<Candidate> priorLayer = layers.get(layer - 1);
            for (int currentIndex = 0; currentIndex < currentLayer.size(); currentIndex++) {
                Candidate current = currentLayer.get(currentIndex);
                double bestCost = Double.POSITIVE_INFINITY;
                int bestPrevious = -1;
                for (int priorIndex = 0; priorIndex < priorLayer.size(); priorIndex++) {
                    Candidate prior = priorLayer.get(priorIndex);
                    double transition = scorer.score(
                            prior.voicing,
                            current.voicing,
                            mode,
                            preferences,
                            current.id
                    ).totalCost;
                    double total = costs[layer - 1][priorIndex] + transition;
                    if (total < bestCost - EPSILON) {
                        bestCost = total;
                        bestPrevious = priorIndex;
                    }
                }
                costs[layer][currentIndex] = bestCost;
                previous[layer][currentIndex] = bestPrevious;
            }
        }

        int finalLayer = layers.size() - 1;
        int bestFinal = 0;
        for (int i = 1; i < costs[finalLayer].length; i++) {
            if (costs[finalLayer][i] < costs[finalLayer][bestFinal] - EPSILON) {
                bestFinal = i;
            }
        }

        int[] path = new int[layers.size()];
        path[finalLayer] = bestFinal;
        for (int layer = finalLayer; layer > 0; layer--) {
            path[layer - 1] = previous[layer][path[layer]];
        }

        List<VoicingRecommendation> result = new ArrayList<>(layers.size());
        Voicing priorVoicing = null;
        for (int layer = 0; layer < layers.size(); layer++) {
            Candidate candidate = layers.get(layer).get(path[layer]);
            VoicingTransitionScorer.Score transition = scorer.score(
                    priorVoicing,
                    candidate.voicing,
                    mode,
                    preferences,
                    candidate.id
            );
            result.add(new VoicingRecommendation(
                    layer,
                    chordSymbols.get(layer),
                    candidate.id,
                    candidate.voicing,
                    transition,
                    costs[layer][path[layer]],
                    reasonFor(candidate.voicing, transition, mode, preferences)
            ));
            priorVoicing = candidate.voicing;
        }
        return Collections.unmodifiableList(result);
    }

    public VoicingRecommendation recommendNext(
            String chordSymbol,
            List<Voicing> candidates,
            Voicing previousVoicing,
            VoicingRecommendationMode mode,
            PracticePreferences preferences
    ) {
        List<Candidate> eligible = eligibleCandidates(chordSymbol, candidates, preferences);
        if (eligible.isEmpty()) {
            return null;
        }
        Candidate best = eligible.get(0);
        VoicingTransitionScorer.Score bestScore = scorer.score(previousVoicing, best.voicing, mode, preferences, best.id);
        for (int i = 1; i < eligible.size(); i++) {
            Candidate candidate = eligible.get(i);
            VoicingTransitionScorer.Score score = scorer.score(previousVoicing, candidate.voicing, mode, preferences, candidate.id);
            if (score.totalCost < bestScore.totalCost - EPSILON) {
                best = candidate;
                bestScore = score;
            }
        }
        return new VoicingRecommendation(
                0,
                chordSymbol,
                best.id,
                best.voicing,
                bestScore,
                bestScore.totalCost,
                reasonFor(best.voicing, bestScore, mode, preferences)
        );
    }

    public static String voicingId(String chordSymbol, Voicing voicing) {
        Objects.requireNonNull(voicing, "voicing");
        String symbol = chordSymbol == null ? "" : chordSymbol.trim();
        String name = voicing.name == null ? "" : voicing.name.trim();
        return symbol + "|" + voicing.fretPattern() + "|" + name;
    }

    private static List<Candidate> eligibleCandidates(
            String chordSymbol,
            List<Voicing> source,
            PracticePreferences preferences
    ) {
        if (source == null || source.isEmpty()) {
            return Collections.emptyList();
        }
        List<Candidate> result = new ArrayList<>();
        for (Voicing voicing : source) {
            if (voicing == null) {
                continue;
            }
            if (!preferences.allowBarre && VoicingTransitionScorer.isBarre(voicing)) {
                continue;
            }
            if (VoicingTransitionScorer.maxFret(voicing) > preferences.maxFret) {
                continue;
            }
            result.add(new Candidate(voicingId(chordSymbol, voicing), voicing));
        }
        Collections.sort(result, (left, right) -> left.id.compareTo(right.id));
        return result;
    }

    private static String reasonFor(
            Voicing voicing,
            VoicingTransitionScorer.Score score,
            VoicingRecommendationMode mode,
            PracticePreferences preferences
    ) {
        List<String> reasons = new ArrayList<>();
        if (voicing.simplified && (mode == VoicingRecommendationMode.BEGINNER
                || (mode == VoicingRecommendationMode.AUTO
                && preferences.proficiency == PracticePreferences.Proficiency.BEGINNER))) {
            reasons.add("简化按法更适合初学者");
        }
        if (score.hasSourceVoicing && score.retainedFingerCount > 0) {
            reasons.add("可保留 " + score.retainedFingerCount + " 个手指位置");
        }
        if (score.hasSourceVoicing && score.fretMovement <= 8) {
            reasons.add("品位移动距离较短");
        }
        if (score.hasSourceVoicing && score.commonPitchClassCount > 0) {
            reasons.add("包含 " + score.commonPitchClassCount + " 个共同音");
        }
        if (score.destinationOpenChord) {
            reasons.add("包含开放弦");
        }
        if (!VoicingTransitionScorer.isBarre(voicing)) {
            reasons.add("不需要完整大横按");
        }
        if (score.familiarDestination) {
            reasons.add("符合已标记的熟悉按法");
        }
        if (reasons.isEmpty()) {
            reasons.add("在最高品位限制内具有最低的确定性切换成本");
        }
        StringBuilder joinedBuilder = new StringBuilder();
        for (String reason : reasons) {
            if (joinedBuilder.length() > 0) {
                joinedBuilder.append("；");
            }
            joinedBuilder.append(reason);
        }
        return joinedBuilder.append('。').toString();
    }

    private static final class Candidate {
        final String id;
        final Voicing voicing;

        Candidate(String id, Voicing voicing) {
            this.id = id;
            this.voicing = voicing;
        }
    }
}
