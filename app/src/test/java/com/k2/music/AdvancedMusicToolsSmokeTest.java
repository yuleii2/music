package com.k2.music;

import java.util.List;

/** Plain-JVM coverage for Phase 2 algorithms and Phase 4 local AI validation. */
public final class AdvancedMusicToolsSmokeTest {
    public static void main(String[] args) throws Exception {
        ChordRepository repository = new ChordRepository();
        testFretboardIdentification(repository);
        testNoteIdentification(repository);
        testTranspositionAndCapo();
        testUserInputValidation(repository);
        testAiValidation(repository);
        testAiBoundaries();
        System.out.println("Advanced music tools tests passed.");
    }

    private static void testFretboardIdentification(ChordRepository repository) {
        ChordIdentifier identifier = new ChordIdentifier(repository);
        assertTop(identifier.identifyFrets(new int[]{-1, 3, 2, 0, 1, 0}), "C");
        assertTop(identifier.identifyFrets(new int[]{0, 3, 2, 0, 1, 0}), "C/E");
        assertTop(identifier.identifyFrets(new int[]{0, 2, 2, 0, 0, 0}), "Em");
        assertTop(identifier.identifyFrets(new int[]{3, 2, 0, 0, 0, 3}), "G");
        assertTop(identifier.identifyFrets(new int[]{-1, 0, 2, 2, 1, 0}), "Am");
    }

    private static void testNoteIdentification(ChordRepository repository) {
        ChordIdentifier identifier = new ChordIdentifier(repository);
        assertTop(identifier.identifyNotes("C E G"), "C");
        assertTop(identifier.identifyNotes("G，C\nE"), "C/G");
        assertTop(identifier.identifyNotes("F# A C E"), "F#m7b5");
        ChordRepository.LookupResult flatSpelling = repository.find("Bbm7");
        require(flatSpelling.recognized && "Bbm7".equals(flatSpelling.chord.symbol),
                "Displayed chord spelling should respect a valid flat-root user input.");
        List<String> migrated = ChordSymbolMigration.normalize(
                java.util.Arrays.asList(" C ", "Cmaj", "dbmaj7", "C△", "unknown"),
                repository,
                12
        );
        require(migrated.equals(java.util.Arrays.asList("C", "Dbmaj7", "Cmaj7", "unknown")),
                "Legacy favorites/history must normalize recognized aliases without dropping unknown values.");
        boolean rejected = false;
        try {
            identifier.identifyNotes("C H G");
        } catch (IllegalArgumentException expected) {
            rejected = expected.getMessage().contains("H");
        }
        require(rejected, "Unknown note input must produce a clear error.");
    }

    private static void testTranspositionAndCapo() {
        ChordTransposer transposer = new ChordTransposer();
        require("D A Bm G".equals(transposer.transposeProgression(
                "C G Am F", 2, MusicTheoryUtils.AccidentalPreference.SHARPS
        )), "Progression transposition must preserve chord qualities.");
        require("D/A".equals(transposer.transposeChord(
                "C/G", 2, MusicTheoryUtils.AccidentalPreference.SHARPS
        )), "Slash chord bass must transpose with the root.");
        CapoAssistant capo = new CapoAssistant(transposer);
        require("D".equals(capo.soundingChord(
                "C", 2, MusicTheoryUtils.AccidentalPreference.SHARPS
        )), "Capo 2 + C must sound D.");
        List<CapoAssistant.Suggestion> suggestions = capo.findMatchingCapos("E B C#m A", "C G Am F");
        require(!suggestions.isEmpty() && suggestions.get(0).capoFret == 4,
                "C G Am F shapes must match E B C#m A at capo 4.");
    }

    private static void testUserInputValidation(ChordRepository repository) {
        require(InputValidators.integerInRange(" 120 ", 40, 240, "invalid") == 120,
                "Whitespace-trimmed BPM input should be accepted.");
        boolean rejected = false;
        try {
            InputValidators.integerInRange("999", 40, 240, "BPM 必须在 40 到 240 之间");
        } catch (IllegalArgumentException expected) {
            rejected = expected.getMessage().contains("40") && expected.getMessage().contains("240");
        }
        require(rejected, "Out-of-range BPM must return a user-facing validation error.");

        Chord major = repository.find("C").chord;
        Chord minor = repository.find("Am").chord;
        require("C 大三和弦".equals(StringUtils.displayName(major)),
                "A chord type must not be mislabeled as a major key.");
        require("Am 小三和弦".equals(StringUtils.displayName(minor)),
                "A chord type must not be mislabeled as a minor key.");
    }

    private static void testAiValidation(ChordRepository repository) throws Exception {
        AiResultValidator validator = new AiResultValidator(repository);
        AiChordRecommendationResult recommendation = validator.validateChordRecommendations(
                "{\"intent\":\"chord_recommendation\",\"candidates\":["
                        + "{\"symbol\":\"Fmaj7\",\"reason\":\"Smooth voice leading\"},"
                        + "{\"symbol\":\"NotAChord\",\"reason\":\"invalid\"}]}",
                true
        );
        require(recommendation.candidates.size() == 1, "Only locally supported AI candidates may pass.");
        require(recommendation.rejectedSymbols.contains("NotAChord"), "Invalid AI chords must be marked rejected.");
        for (Voicing voicing : recommendation.candidates.get(0).localVoicings) {
            require(!voicing.barre, "No-barre AI requests must filter barre voicings locally.");
        }

        boolean invalidJsonRejected = false;
        try {
            validator.validateChordRecommendations("{not valid json}", false);
        } catch (AiResultValidator.ValidationException expected) {
            invalidJsonRejected = true;
        }
        require(invalidJsonRejected, "Malformed AI JSON must fail closed without crashing.");

        AiProgressionResult progression = validator.validateProgression(
                "{\"intent\":\"progression\",\"key\":\"A minor\",\"tempoSuggestion\":86,"
                        + "\"chords\":[{\"symbol\":\"Am\",\"beats\":4},{\"symbol\":\"Fmaj7\",\"beats\":4},"
                        + "{\"symbol\":\"C\",\"beats\":4},{\"symbol\":\"G\",\"beats\":4}],"
                        + "\"explanation\":\"verified\"}"
        );
        require(progression.chords.size() == 4, "Valid AI progression must map to local chords.");
        require(progression.localAnalysis.contains("本地调性分析"),
                "AI progression must include local key/chord relationship analysis.");

        boolean keyRejected = false;
        try {
            validator.validateProgression(
                    "{\"intent\":\"progression\",\"key\":\"H mystery\",\"tempoSuggestion\":86,"
                            + "\"chords\":[{\"symbol\":\"C\",\"beats\":4}],\"explanation\":\"bad key\"}"
            );
        } catch (AiResultValidator.ValidationException expected) {
            keyRejected = true;
        }
        require(keyRejected, "Invalid AI key labels must fail local validation.");

        boolean bpmRejected = false;
        try {
            validator.validatePracticePlan("{\"intent\":\"practice_plan\",\"days\":[{"
                    + "\"title\":\"bad\",\"durationMinutes\":8,\"bpm\":999,\"chords\":[\"Am\",\"F\"]}]}");
        } catch (AiResultValidator.ValidationException expected) {
            bpmRejected = true;
        }
        require(bpmRejected, "Out-of-range AI practice BPM must be rejected.");
    }

    private static void testAiBoundaries() throws Exception {
        require(OpenAiCompatibleProvider.validateBaseUrl("not a url") != null, "Invalid Base URL must be rejected.");
        require(OpenAiCompatibleProvider.validateBaseUrl("http://127.0.0.1/v1") != null,
                "Cleartext Base URLs must be rejected.");
        require(OpenAiCompatibleProvider.validateBaseUrl("https://api.example.com/v1") == null, "HTTPS Base URL must pass.");
        require(AiError.forHttp(401).type == AiError.Type.UNAUTHORIZED, "401 classification failed.");
        require(AiError.forHttp(403).type == AiError.Type.FORBIDDEN, "403 classification failed.");
        require(AiError.forHttp(429).type == AiError.Type.RATE_LIMITED, "429 classification failed.");
        require(AiError.forHttp(500).type == AiError.Type.SERVER, "500 classification failed.");
        String content = OpenAiCompatibleProvider.parseAssistantContent(
                "{\"choices\":[{\"message\":{\"content\":\"{\\\"ok\\\":true}\"}}]}"
        );
        require("{\"ok\":true}".equals(content), "Chat Completions content parsing failed.");
        AiRequest request = AiPromptFactory.chordRecommendation("不用横按的 F", AiSettings.defaults());
        require(request.requireJson && request.messages.get(0).content.contains("Never invent guitar frets"),
                "Structured prompt safety boundary is missing.");

        java.util.concurrent.ExecutorService executor = java.util.concurrent.Executors.newSingleThreadExecutor();
        try {
            OpenAiCompatibleProvider disabledProvider = new OpenAiCompatibleProvider(
                    AiSettings.defaults(),
                    "",
                    executor,
                    Runnable::run
            );
            java.util.concurrent.atomic.AtomicReference<AiError> disabledError = new java.util.concurrent.atomic.AtomicReference<>();
            disabledProvider.send(request, new AiProvider.Callback() {
                @Override public void onSuccess(AiResponse response) {
                    throw new AssertionError("Disabled AI must never return a network response.");
                }

                @Override public void onError(AiError error) {
                    disabledError.set(error);
                }
            });
            require(disabledError.get() != null && disabledError.get().type == AiError.Type.DISABLED,
                    "Disabled AI must be rejected before any network task is submitted.");
            disabledProvider.close();
        } finally {
            executor.shutdownNow();
        }

        QueuedExecutor queuedExecutor = new QueuedExecutor();
        OpenAiCompatibleProvider cancellableProvider = new OpenAiCompatibleProvider(
                new AiSettings(true, "OpenAI Compatible", "https://api.example.com/v1", "test-model", 0.2d, 5),
                "test-secret",
                queuedExecutor,
                Runnable::run
        );
        java.util.concurrent.atomic.AtomicBoolean callbackCalled = new java.util.concurrent.atomic.AtomicBoolean();
        AiProvider.RequestHandle handle = cancellableProvider.send(request, new AiProvider.Callback() {
            @Override public void onSuccess(AiResponse response) {
                callbackCalled.set(true);
            }

            @Override public void onError(AiError error) {
                callbackCalled.set(true);
            }
        });
        handle.cancel();
        queuedExecutor.runQueued();
        require(handle.isCancelled() && !callbackCalled.get(),
                "A cancelled AI request must not invoke a stale page callback.");
        cancellableProvider.close();
    }

    private static final class QueuedExecutor extends java.util.concurrent.AbstractExecutorService {
        private final java.util.List<Runnable> queued = new java.util.ArrayList<>();
        private boolean shutdown;

        @Override public void shutdown() {
            shutdown = true;
        }

        @Override public java.util.List<Runnable> shutdownNow() {
            shutdown = true;
            java.util.List<Runnable> pending = new java.util.ArrayList<>(queued);
            queued.clear();
            return pending;
        }

        @Override public boolean isShutdown() {
            return shutdown;
        }

        @Override public boolean isTerminated() {
            return shutdown && queued.isEmpty();
        }

        @Override public boolean awaitTermination(long timeout, java.util.concurrent.TimeUnit unit) {
            return isTerminated();
        }

        @Override public void execute(Runnable command) {
            if (shutdown) throw new java.util.concurrent.RejectedExecutionException();
            queued.add(command);
        }

        void runQueued() {
            java.util.List<Runnable> pending = new java.util.ArrayList<>(queued);
            queued.clear();
            for (Runnable command : pending) command.run();
        }
    }

    private static void assertTop(List<ChordMatch> matches, String expected) {
        require(!matches.isEmpty(), "Expected a chord candidate for " + expected);
        require(expected.equals(matches.get(0).symbol),
                "Expected " + expected + " first but got " + matches.get(0).symbol + " (score " + matches.get(0).score + ")");
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
