package com.k2.music;

import java.util.ArrayList;
import java.util.List;

/** Centralizes bounded, structured prompts; Activities never assemble system instructions. */
public final class AiPromptFactory {
    public static final String TASK_CHORD_RECOMMENDATION = "chord_recommendation";
    public static final String TASK_PROGRESSION = "progression";
    public static final String TASK_CHORD_EXPLANATION = "chord_explanation";
    public static final String TASK_PROGRESSION_OPTIMIZATION = "progression_optimization";
    public static final String TASK_PRACTICE_PLAN = "practice_plan";
    public static final String TASK_TRANSITION_EXPLANATION = "transition_explanation";

    private AiPromptFactory() {
    }

    public static AiRequest chordRecommendation(String input, AiSettings settings) {
        return request(TASK_CHORD_RECOMMENDATION, input,
                "Return {\"intent\":\"chord_recommendation\",\"candidates\":[{\"symbol\":\"...\",\"reason\":\"...\"}]}. "
                        + "Return at most 6 candidates and never output frets or finger numbers.", settings);
    }

    public static AiRequest progression(String input, AiSettings settings) {
        return request(TASK_PROGRESSION, input,
                "Return {\"intent\":\"progression\",\"key\":\"...\",\"tempoSuggestion\":86,"
                        + "\"chords\":[{\"symbol\":\"Am\",\"beats\":4}],\"explanation\":\"...\"}. "
                        + "Use 1 to 16 chords, beats 1 to 16, and BPM 40 to 240.", settings);
    }

    public static AiRequest explainChord(Chord chord, String proficiency, AiSettings settings) {
        if (chord == null) throw new IllegalArgumentException("chord is required");
        String local = "Local verified data: symbol=" + chord.symbol
                + "; notes=" + String.join(",", chord.notes)
                + "; intervals=" + String.join(",", chord.intervals)
                + "; quality=" + chord.quality
                + "; currentVoicingDifficulty=" + (chord.voicings.isEmpty() ? "unavailable" : chord.voicings.get(0).difficulty)
                + "; currentVoicingHasBarre=" + (!chord.voicings.isEmpty() && chord.voicings.get(0).barre)
                + "; proficiency=" + safe(proficiency, 40) + ".";
        return request(TASK_CHORD_EXPLANATION, local,
                "Return {\"intent\":\"chord_explanation\",\"symbol\":\"...\",\"explanation\":\"...\"}. "
                        + "Explain only the supplied local theory; do not infer different notes.", settings);
    }

    public static AiRequest practicePlan(String verifiedStatistics, AiSettings settings) {
        return request(TASK_PRACTICE_PLAN, verifiedStatistics,
                "Return {\"intent\":\"practice_plan\",\"days\":[{\"title\":\"...\","
                        + "\"durationMinutes\":8,\"bpm\":60,\"chords\":[\"Am\",\"F\"]}]}. "
                        + "Return at most 14 days, 1-120 minutes per item, and BPM 40-240.", settings);
    }

    public static AiRequest progressionOptimization(String verifiedProgressionAndGoal, AiSettings settings) {
        return request(TASK_PROGRESSION_OPTIMIZATION, verifiedProgressionAndGoal,
                "Return {\"intent\":\"progression_optimization\",\"key\":\"...\",\"tempoSuggestion\":80,"
                        + "\"chords\":[{\"symbol\":\"...\",\"beats\":4}],"
                        + "\"explanation\":\"...\"}. Do not output voicing fret data.", settings);
    }

    public static AiRequest transitionExplanation(String verifiedTransitionMetrics, AiSettings settings) {
        return request(TASK_TRANSITION_EXPLANATION, verifiedTransitionMetrics,
                "Return {\"intent\":\"transition_explanation\",\"explanation\":\"...\"}. "
                        + "Explain only the supplied local transition metrics.", settings);
    }

    public static AiRequest repairJson(AiRequest original, String invalidOutput) {
        List<AiMessage> messages = new ArrayList<>(original.messages);
        messages.add(new AiMessage(AiMessage.ASSISTANT, safe(invalidOutput, 6_000)));
        messages.add(new AiMessage(AiMessage.USER,
                "The prior answer was not valid for the required JSON schema. Return one corrected JSON object only."
                        + " Do not add Markdown fences or commentary."));
        return original.withMessages(messages);
    }

    private static AiRequest request(String task, String input, String schema, AiSettings settings) {
        AiSettings actual = settings == null ? AiSettings.defaults() : settings;
        String userInput = safe(input, 4_000);
        if (userInput.isEmpty()) throw new IllegalArgumentException("输入不能为空");
        String system = "You are the optional explanation layer of an offline guitar chord app. "
                + "Output exactly one JSON object matching the requested schema. Never output Markdown. "
                + "Never invent guitar frets, finger numbers, local database entries, or chord spellings. "
                + "Use standard chord symbols. If uncertain, return an empty candidate list with a short explanation. "
                + "Keep text fields under 800 characters. " + schema;
        List<AiMessage> messages = new ArrayList<>();
        messages.add(new AiMessage(AiMessage.SYSTEM, system));
        messages.add(new AiMessage(AiMessage.USER, userInput));
        return new AiRequest(task, messages, actual.model, actual.temperature, true);
    }

    private static String safe(String value, int maxLength) {
        if (value == null) return "";
        String cleaned = value.trim();
        return cleaned.length() <= maxLength ? cleaned : cleaned.substring(0, maxLength);
    }
}
