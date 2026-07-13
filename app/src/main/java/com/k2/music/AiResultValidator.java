package com.k2.music;

import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/** Turns untrusted model JSON into locally verified chords and bounded DTOs. */
public final class AiResultValidator {
    public static final class ValidationException extends Exception {
        public ValidationException(String message) {
            super(message);
        }
    }

    private final ChordRepository repository;

    public AiResultValidator(ChordRepository repository) {
        if (repository == null) throw new IllegalArgumentException("repository is required");
        this.repository = repository;
    }

    public AiChordRecommendationResult validateChordRecommendations(String rawJson, boolean disallowBarre)
            throws ValidationException {
        Map<String, Object> root = object(rawJson);
        requireIntent(root, AiPromptFactory.TASK_CHORD_RECOMMENDATION);
        List<Object> array = array(root, "candidates", true);
        if (array.size() > 8) throw invalid("候选和弦数量超过限制");
        List<AiChordRecommendationResult.Candidate> accepted = new ArrayList<>();
        List<String> rejected = new ArrayList<>();
        for (Object rawItem : array) {
            Map<String, Object> item = asObject(rawItem);
            if (item == null) continue;
            String symbol = bounded(string(item, "symbol", ""), 32, "和弦名称");
            String reason = bounded(string(item, "reason", ""), 800, "推荐理由");
            ChordRepository.LookupResult lookup = repository.find(symbol);
            if (!lookup.recognized) {
                rejected.add(symbol);
                continue;
            }
            List<Voicing> voicings = new ArrayList<>();
            for (Voicing voicing : lookup.chord.voicings) {
                if (!disallowBarre || !voicing.barre) voicings.add(voicing);
            }
            if (disallowBarre && !lookup.chord.voicings.isEmpty() && voicings.isEmpty()) {
                rejected.add(symbol);
                continue;
            }
            accepted.add(new AiChordRecommendationResult.Candidate(
                    lookup.chord.symbol, reason, lookup.chord, voicings
            ));
        }
        return new AiChordRecommendationResult(accepted, rejected);
    }

    public AiProgressionResult validateProgression(String rawJson) throws ValidationException {
        return parseProgression(object(rawJson), AiPromptFactory.TASK_PROGRESSION);
    }

    public AiProgressionOptimizationResult validateProgressionOptimization(String rawJson) throws ValidationException {
        AiProgressionResult parsed = parseProgression(object(rawJson), AiPromptFactory.TASK_PROGRESSION_OPTIMIZATION);
        return new AiProgressionOptimizationResult(
                parsed.chords,
                parsed.explanation,
                parsed.key,
                parsed.tempoSuggestion,
                parsed.localAnalysis
        );
    }

    public AiPracticePlanResult validatePracticePlan(String rawJson) throws ValidationException {
        Map<String, Object> root = object(rawJson);
        requireIntent(root, AiPromptFactory.TASK_PRACTICE_PLAN);
        List<Object> daysArray = array(root, "days", true);
        if (daysArray.size() > 14) throw invalid("练习计划天数超过限制");
        List<AiPracticePlanResult.Day> days = new ArrayList<>();
        for (Object rawItem : daysArray) {
            Map<String, Object> item = asObject(rawItem);
            if (item == null) continue;
            String title = bounded(string(item, "title", "练习"), 120, "练习标题");
            int duration = integer(item, "durationMinutes", 0);
            int bpm = integer(item, "bpm", 0);
            if (duration < 1 || duration > 120) throw invalid("练习时长必须在 1 到 120 分钟之间");
            if (bpm < 40 || bpm > 240) throw invalid("BPM 必须在 40 到 240 之间");
            List<Object> chordArray = array(item, "chords", true);
            if (chordArray.isEmpty() || chordArray.size() > 12) {
                throw invalid("每项练习必须包含 1 到 12 个和弦");
            }
            List<Chord> chords = new ArrayList<>();
            for (Object rawChord : chordArray) {
                String symbol = bounded(rawChord instanceof String ? (String) rawChord : "", 32, "和弦名称");
                ChordRepository.LookupResult lookup = repository.find(symbol);
                if (!lookup.recognized) throw invalid("练习计划包含本地无法验证的和弦：" + symbol);
                chords.add(lookup.chord);
            }
            days.add(new AiPracticePlanResult.Day(title, duration, bpm, chords));
        }
        return new AiPracticePlanResult(days);
    }

    public String validateExplanation(String rawJson, String expectedIntent, int maxLength) throws ValidationException {
        Map<String, Object> root = object(rawJson);
        requireIntent(root, expectedIntent);
        return bounded(string(root, "explanation", ""), Math.max(1, maxLength), "解释");
    }

    private AiProgressionResult parseProgression(Map<String, Object> root, String expectedIntent)
            throws ValidationException {
        requireIntent(root, expectedIntent);
        List<Object> array = array(root, "chords", true);
        if (array.isEmpty()) throw invalid("和弦进行不能为空");
        if (array.size() > 16) throw invalid("和弦进行超过 16 个步骤");
        String key = bounded(string(root, "key", ""), 80, "调性");
        int tempo = integer(root, "tempoSuggestion", 80);
        if (tempo < 40 || tempo > 240) throw invalid("建议 BPM 必须在 40 到 240 之间");
        String explanation = bounded(string(root, "explanation", ""), 1_200, "说明");
        List<AiProgressionResult.Step> accepted = new ArrayList<>();
        List<String> rejected = new ArrayList<>();
        for (Object rawItem : array) {
            Map<String, Object> item = asObject(rawItem);
            if (item == null) continue;
            String symbol = bounded(string(item, "symbol", ""), 32, "和弦名称");
            int beats = integer(item, "beats", 4);
            if (beats < 1 || beats > 16) throw invalid("每个和弦的拍数必须在 1 到 16 之间");
            ChordRepository.LookupResult lookup = repository.find(symbol);
            if (lookup.recognized) {
                accepted.add(new AiProgressionResult.Step(lookup.chord.symbol, beats, lookup.chord));
            } else {
                rejected.add(symbol);
            }
        }
        if (accepted.isEmpty()) throw invalid("模型未返回任何可由本地和弦库验证的和弦");
        List<Chord> acceptedChords = new ArrayList<>();
        for (AiProgressionResult.Step step : accepted) acceptedChords.add(step.localChord);
        final ChordProgressionAnalyzer.Analysis analysis;
        try {
            analysis = new ChordProgressionAnalyzer().analyze(key, acceptedChords);
        } catch (IllegalArgumentException exception) {
            throw invalid(exception.getMessage());
        }
        return new AiProgressionResult(
                analysis.normalizedKey.isEmpty() ? key : analysis.normalizedKey,
                tempo,
                accepted,
                explanation,
                rejected,
                analysis.summary
        );
    }

    private static Map<String, Object> object(String rawJson) throws ValidationException {
        if (rawJson == null || rawJson.trim().isEmpty()) throw invalid("模型返回为空");
        if (rawJson.length() > 100_000) throw invalid("模型返回内容过长");
        String trimmed = rawJson.trim();
        if (!trimmed.startsWith("{") || !trimmed.endsWith("}")) {
            throw invalid("模型未返回单一 JSON 对象");
        }
        try {
            Object parsed = SimpleJsonParser.parse(new StringReader(trimmed));
            Map<String, Object> object = asObject(parsed);
            if (object == null) throw invalid("JSON 顶层必须是对象");
            return object;
        } catch (IOException exception) {
            throw invalid("JSON 格式无效");
        }
    }

    private static void requireIntent(Map<String, Object> root, String expected) throws ValidationException {
        if (!expected.equals(string(root, "intent", ""))) {
            throw invalid("AI 结果类型不匹配");
        }
    }

    private static String string(Map<String, Object> object, String key, String fallback) {
        Object value = object.get(key);
        return value instanceof String ? (String) value : fallback;
    }

    private static int integer(Map<String, Object> object, String key, int fallback) throws ValidationException {
        Object value = object.get(key);
        if (value == null) return fallback;
        if (!(value instanceof Number)) throw invalid(key + " 必须是数字");
        Number number = (Number) value;
        double decimal = number.doubleValue();
        if (!Double.isFinite(decimal) || decimal != Math.rint(decimal)) throw invalid(key + " 必须是整数");
        if (decimal < Integer.MIN_VALUE || decimal > Integer.MAX_VALUE) throw invalid(key + " 超出范围");
        return (int) decimal;
    }

    private static List<Object> array(Map<String, Object> object, String key, boolean required)
            throws ValidationException {
        Object value = object.get(key);
        if (value == null) {
            if (required) throw invalid("缺少 " + key + " 数组");
            return Collections.emptyList();
        }
        if (!(value instanceof List)) throw invalid(key + " 必须是数组");
        @SuppressWarnings("unchecked")
        List<Object> result = (List<Object>) value;
        return result;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asObject(Object value) {
        return value instanceof Map ? (Map<String, Object>) value : null;
    }

    private static String bounded(String value, int max, String label) throws ValidationException {
        String trimmed = value == null ? "" : value.trim();
        if (trimmed.length() > max) throw invalid(label + "超过长度限制");
        return trimmed;
    }

    private static ValidationException invalid(String message) {
        return new ValidationException(message);
    }
}
