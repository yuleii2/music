package com.k2.music;

import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.TimeZone;
import java.util.concurrent.atomic.AtomicLong;

/** Plain-JVM tests for the Android-independent Phase 3 core. */
public final class Phase3CoreSmokeTest {
    private static final long MILLIS_PER_DAY = 86_400_000L;

    public static void main(String[] args) throws Exception {
        testDiatonicGenerationAndPresets();
        testProgressionStoreCrud();
        testProgressionPlayerPauseAndLoop();
        testMetronomeAbsoluteTiming();
        testPracticePreferencesStore();
        testPracticeRecordSummaries();
        testVoicingRecommendations();
        System.out.println("Phase 3 core smoke test passed.");
    }

    private static void testDiatonicGenerationAndPresets() {
        DiatonicChordGenerator generator = new DiatonicChordGenerator();
        requireEquals(
                Arrays.asList("C", "Dm", "Em", "F", "G", "Am", "Bdim"),
                generator.generateMajor(KeySignature.major("C")),
                "C major diatonic triads"
        );
        requireEquals(
                Arrays.asList("G", "Am", "Bm", "C", "D", "Em", "F#dim"),
                generator.generateMajor(KeySignature.major("G")),
                "G major diatonic triads"
        );
        requireEquals("E#dim", generator.chordFor(KeySignature.major("F#"), ScaleDegree.VII),
                "F# major should retain theoretical E# spelling");

        ProgressionPresetRepository presets = new ProgressionPresetRepository(generator);
        requireEquals(
                Arrays.asList("C", "G", "Am", "F"),
                presets.generateChordSymbols(ProgressionPresetRepository.POP_1564, KeySignature.major("C")),
                "C I-V-vi-IV"
        );
        requireEquals(
                Arrays.asList("G", "D", "Em", "C"),
                presets.generateChordSymbols(ProgressionPresetRepository.POP_1564, KeySignature.major("G")),
                "G I-V-vi-IV"
        );
        requireEquals(
                Arrays.asList("C7", "C7", "C7", "C7", "F7", "F7", "C7", "C7", "G7", "F7", "C7", "G7"),
                presets.generateChordSymbols(ProgressionPresetRepository.TWELVE_BAR_BLUES, KeySignature.major("C")),
                "12-bar blues should use dominant-seventh harmony without being hard-coded to C"
        );
    }

    private static void testProgressionStoreCrud() throws Exception {
        File directory = Files.createTempDirectory("k2-progressions").toFile();
        AtomicLong now = new AtomicLong(1_000L);
        ProgressionStore store = new ProgressionStore(new File(directory, "progressions.bin"), now::get);
        ChordProgression draft = progression("progression-1", "Pop loop", false, 120, 0L,
                step("C", 2.0, 0), step("G", 2.0, 1));

        ChordProgression created = store.create(draft);
        require(created.createdAtEpochMillis == 1_000L, "Create should apply the store clock.");
        requireEquals(created, store.read(created.id), "Stored progression round trip");
        require(store.list().size() == 1, "Progression list should contain the created record.");

        now.set(2_000L);
        ChordProgression renamed = created.withName("Renamed loop", 123L);
        ChordProgression updated = store.update(renamed);
        requireEquals("Renamed loop", updated.name, "Progression update should persist the new name.");
        require(updated.createdAtEpochMillis == 1_000L, "Update should preserve creation time.");
        require(updated.updatedAtEpochMillis == 2_000L, "Update should apply a fresh modification time.");

        now.set(3_000L);
        ChordProgression duplicate = store.duplicate(updated.id, "Copy");
        require(!duplicate.id.equals(updated.id), "Duplicate should receive a new id.");
        require(store.list().size() == 2, "Duplicate should be saved.");
        require(store.delete(updated.id), "Delete should report a removed progression.");
        require(store.read(updated.id) == null, "Deleted progression should no longer be readable.");
        require(!store.delete("missing"), "Deleting an unknown progression should be a no-op.");
    }

    private static void testProgressionPlayerPauseAndLoop() {
        FakeScheduler scheduler = new FakeScheduler();
        RecordingAudio audio = new RecordingAudio();
        ProgressionPlayer player = new ProgressionPlayer(audio, scheduler, scheduler);
        player.setProgression(progression("player-1", "Player", false, 120, 0L,
                step("C", 2.0, 0), step("G", 2.0, 1)));

        player.play();
        requireEquals(Collections.singletonList("C"), audio.played, "Playback should start on the first chord.");
        scheduler.advanceBy(500_000_000L);
        requireNear(1.0, player.position().elapsedBeats, 0.000_001, "One beat should elapse in 500 ms at 120 BPM.");
        player.pause();
        scheduler.advanceBy(2_000_000_000L);
        require(player.position().stepIndex == 0, "Paused transport must preserve its step.");
        requireNear(1.0, player.position().elapsedBeats, 0.000_001, "Paused transport must preserve beat position.");

        player.play();
        scheduler.advanceBy(499_999_999L);
        require(player.position().stepIndex == 0, "Resume should honor the remaining step duration.");
        scheduler.advanceBy(1L);
        require(player.position().stepIndex == 1, "Resume should change chord at the original position.");
        require(player.position().measureNumber == 1 && player.position().beatNumber == 3,
                "Two two-beat chords should share one 4/4 measure.");
        requireEquals("G", audio.played.get(audio.played.size() - 1), "Second chord should sound after resume.");
        player.stop();

        audio.played.clear();
        player.setProgression(progression("player-2", "Loop", true, 120, 0L,
                step("C", 1.0, 0), step("G", 1.0, 1)));
        player.play();
        scheduler.advanceBy(500_000_000L);
        scheduler.advanceBy(500_000_000L);
        requireEquals(Arrays.asList("C", "G", "C"), audio.played,
                "Loop playback should return deterministically to the first chord.");

        player.stop();
        audio.played.clear();
        long sharedAnchor = scheduler.nanoTime() + 100_000_000L;
        player.playAt(sharedAnchor);
        player.setBpm(60);
        require(audio.played.isEmpty(), "Absolute future start should not play early.");
        scheduler.advanceBy(100_000_000L);
        requireEquals(Collections.singletonList("C"), audio.played, "Absolute start should fire on its anchor.");
        scheduler.advanceBy(999_999_999L);
        requireEquals(Collections.singletonList("C"), audio.played,
                "Changing BPM before an absolute start should update duration without moving the anchor.");
        scheduler.advanceBy(1L);
        requireEquals(Arrays.asList("C", "G"), audio.played,
                "Pending-start BPM change should use the new one-second beat.");
        player.close();
    }

    private static void testMetronomeAbsoluteTiming() {
        FakeScheduler scheduler = new FakeScheduler();
        List<Long> scheduledTicks = new ArrayList<>();
        List<Integer> beatNumbers = new ArrayList<>();
        MetronomeEngine metronome = new MetronomeEngine(accented -> { }, scheduler, scheduler);
        metronome.setBpm(120);
        metronome.setTimeSignature(TimeSignature.FOUR_FOUR);
        metronome.setListener(new MetronomeEngine.Listener() {
            @Override
            public void onTick(int beatNumber, boolean accented, long scheduledTimeNanos) {
                scheduledTicks.add(scheduledTimeNanos);
                beatNumbers.add(beatNumber);
            }
        });

        long anchor = scheduler.nanoTime();
        metronome.startAt(anchor);
        long interval = 500_000_000L;
        scheduler.advanceBy(interval * 1_000L);
        require(scheduledTicks.size() == 1_001, "A 1000-beat interval should include the anchor tick.");
        for (int i = 0; i < scheduledTicks.size(); i++) {
            require(scheduledTicks.get(i) == anchor + interval * i,
                    "Metronome deadline drift at tick " + i + ".");
            require(beatNumbers.get(i) == (i % 4) + 1, "Metronome beat cycle should remain stable.");
        }

        metronome.pause();
        int pausedCount = scheduledTicks.size();
        scheduler.advanceBy(2_000_000_000L);
        require(scheduledTicks.size() == pausedCount, "Paused metronome must not tick.");
        metronome.start();
        scheduler.advanceBy(interval - 1L);
        require(scheduledTicks.size() == pausedCount, "Resume should preserve time remaining to the next tick.");
        scheduler.advanceBy(1L);
        require(scheduledTicks.size() == pausedCount + 1, "Resumed metronome should tick at the preserved deadline.");
        metronome.close();

        FakeScheduler delayedScheduler = new FakeScheduler();
        List<Long> delayedTicks = new ArrayList<>();
        MetronomeEngine delayed = new MetronomeEngine(accented -> { }, delayedScheduler, delayedScheduler);
        delayed.setBpm(120);
        delayed.setListener(new MetronomeEngine.Listener() {
            @Override
            public void onTick(int beatNumber, boolean accented, long scheduledTimeNanos) {
                delayedTicks.add(scheduledTimeNanos);
            }
        });
        delayed.start();
        delayedScheduler.advanceLateBy(1_300_000_000L);
        delayedScheduler.advanceBy(200_000_000L);
        requireEquals(Arrays.asList(0L, 500_000_000L, 1_500_000_000L), delayedTicks,
                "A late callback should skip stale ticks but keep the original absolute grid.");
        delayed.stop();

        delayedTicks.clear();
        long futureAnchor = delayedScheduler.nanoTime() + 2_000_000_000L;
        delayed.startAt(futureAnchor);
        delayed.setBpm(60);
        delayedScheduler.advanceBy(1_999_999_999L);
        require(delayedTicks.isEmpty(), "Changing BPM must not pull a scheduled first tick before its anchor.");
        delayedScheduler.advanceBy(1L);
        requireEquals(Collections.singletonList(futureAnchor), delayedTicks,
                "Scheduled first tick should retain its shared transport anchor.");
        delayed.close();
    }

    private static void testPracticePreferencesStore() throws Exception {
        File directory = Files.createTempDirectory("k2-preferences").toFile();
        PracticePreferencesStore store = new PracticePreferencesStore(new File(directory, "preferences.bin"));
        requireEquals(PracticePreferences.defaults(), store.load(), "Missing preferences file should use defaults.");

        PracticePreferences preferences = new PracticePreferences(
                PracticePreferences.Proficiency.INTERMEDIATE,
                false,
                9,
                96,
                TimeSignature.SIX_EIGHT,
                PracticePreferences.PlaybackMode.ARPEGGIO,
                false,
                Collections.singleton("F|x-x-3-2-1-1|F easy")
        );
        store.save(preferences);
        requireEquals(preferences, store.load(), "Practice preferences should round trip.");
        requireEquals(PracticePreferences.defaults(), store.reset(), "Reset should restore defaults.");
    }

    private static void testPracticeRecordSummaries() throws Exception {
        File directory = Files.createTempDirectory("k2-practice").toFile();
        PracticeRecordStore store = new PracticeRecordStore(new File(directory, "records.bin"));
        TimeZone utc = TimeZone.getTimeZone("UTC");
        Calendar nowCalendar = Calendar.getInstance(utc);
        nowCalendar.clear();
        nowCalendar.set(2026, Calendar.JULY, 9, 12, 0, 0);
        long now = nowCalendar.getTimeInMillis();

        PracticeSession today = session("today", now - 60_000L, Arrays.asList("C", "G"), 600, 20, 8);
        PracticeSession recent = session("recent", now - 3 * MILLIS_PER_DAY, Arrays.asList("C", "F"), 300, 30, 10);
        PracticeSession old = session("old", now - 8 * MILLIS_PER_DAY, Collections.singletonList("G"), 120, 5, 5);
        store.add(today);
        store.add(recent);
        store.add(old);

        requireEquals(today, store.read("today"), "Practice record should round trip.");
        PracticeSummary summary = store.summarize(now, utc);
        require(summary.todayPracticeSeconds == 600L, "Today summary should include today's duration only.");
        require(summary.lastSevenDaysSessionCount == 2, "Seven-day summary should include two sessions.");
        require(summary.lastSevenDaysPracticeSeconds == 900L, "Seven-day duration should be summed.");
        require(summary.lastSevenDaysCompletionCount == 50, "Seven-day completions should be summed.");
        requireEquals("C", summary.mostPracticedChord, "Most practiced chord should be deterministic.");
        require(summary.bestCompletionCount == 30 && summary.bestStreak == 10,
                "Best performance should use completions then streak.");
        requireEquals("recent", summary.bestSessionId, "Best-session id should be exposed.");
        require(store.delete("old"), "Practice delete should remove an existing record.");
    }

    private static void testVoicingRecommendations() {
        Voicing amOpen = voicing("Am open", new int[]{-1, 0, 2, 2, 1, 0}, new int[]{0, 0, 2, 3, 1, 0}, false, false, 1);
        Voicing fBarre = voicing("F full barre", new int[]{1, 3, 3, 2, 1, 1}, new int[]{1, 3, 4, 2, 1, 1}, true, false, 4);
        Voicing fEasy = voicing("F easy", new int[]{-1, -1, 3, 2, 1, 1}, new int[]{0, 0, 3, 2, 1, 1}, false, true, 1);
        Voicing fHigh = voicing("F high", new int[]{13, 15, 15, 14, 13, 13}, new int[]{1, 3, 4, 2, 1, 1}, true, false, 5);

        PracticePreferences beginner = new PracticePreferences(
                PracticePreferences.Proficiency.BEGINNER,
                true,
                12,
                70,
                TimeSignature.FOUR_FOUR,
                PracticePreferences.PlaybackMode.WHOLE_CHORD,
                true
        );
        VoicingRecommendationEngine engine = new VoicingRecommendationEngine();
        VoicingRecommendation recommendation = engine.recommendNext(
                "F",
                Arrays.asList(fBarre, fEasy),
                amOpen,
                VoicingRecommendationMode.BEGINNER,
                beginner
        );
        require(recommendation != null && recommendation.voicing == fEasy,
                "Beginner mode should prefer the simplified F over the full barre.");

        PracticePreferences noBarre = beginner.withVoicingConstraints(false, 12);
        recommendation = engine.recommendNext(
                "F",
                Arrays.asList(fBarre, fEasy),
                amOpen,
                VoicingRecommendationMode.MINIMUM_MOVEMENT,
                noBarre
        );
        require(recommendation != null && recommendation.voicing == fEasy,
                "Disabling barre chords should hard-filter the full barre.");
        require(engine.recommendNext(
                "F",
                Collections.singletonList(fHigh),
                amOpen,
                VoicingRecommendationMode.HIGH_POSITION_TONE,
                beginner
        ) == null, "Maximum-fret preference should be a hard constraint.");

        Map<String, List<Voicing>> candidates = new HashMap<>();
        candidates.put("Am", Collections.singletonList(amOpen));
        candidates.put("F", Arrays.asList(fBarre, fEasy));
        List<VoicingRecommendation> first = engine.recommend(
                Arrays.asList("Am", "F", "Am"),
                candidates,
                VoicingRecommendationMode.AUTO,
                beginner
        );
        List<VoicingRecommendation> second = engine.recommend(
                Arrays.asList("Am", "F", "Am"),
                candidates,
                VoicingRecommendationMode.AUTO,
                beginner
        );
        require(first.size() == 3, "Recommendation engine should return one choice per chord.");
        for (int i = 0; i < first.size(); i++) {
            requireEquals(first.get(i).voicingId, second.get(i).voicingId,
                    "Recommendation path must be deterministic at step " + i);
        }
    }

    private static ChordProgression progression(
            String id,
            String name,
            boolean loop,
            int bpm,
            long timestamp,
            ProgressionStep... steps
    ) {
        return new ChordProgression(
                id,
                name,
                "C",
                TimeSignature.FOUR_FOUR,
                bpm,
                loop,
                Arrays.asList(steps),
                timestamp,
                timestamp,
                ""
        );
    }

    private static ProgressionStep step(String chord, double beats, int order) {
        return new ProgressionStep(chord, "", beats, "", order);
    }

    private static PracticeSession session(
            String id,
            long startedAt,
            List<String> chords,
            int duration,
            int completions,
            int streak
    ) {
        return new PracticeSession(
                id,
                startedAt,
                PracticeSession.Type.PROGRESSION_LOOP,
                chords,
                80,
                duration,
                completions,
                streak
        );
    }

    private static Voicing voicing(
            String name,
            int[] frets,
            int[] fingers,
            boolean barre,
            boolean simplified,
            int difficulty
    ) {
        return new Voicing(
                name,
                frets,
                fingers,
                1,
                5,
                String.valueOf(difficulty),
                true,
                simplified,
                barre,
                ""
        );
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }

    private static void requireEquals(Object expected, Object actual, String label) {
        if (expected == null ? actual != null : !expected.equals(actual)) {
            throw new IllegalStateException(label + ": expected " + expected + " but got " + actual + ".");
        }
    }

    private static void requireNear(double expected, double actual, double tolerance, String label) {
        if (Math.abs(expected - actual) > tolerance) {
            throw new IllegalStateException(label + ": expected " + expected + " but got " + actual + ".");
        }
    }

    private static final class RecordingAudio implements ProgressionPlayer.AudioOutput {
        final List<String> played = new ArrayList<>();

        @Override
        public void play(ProgressionStep step, PracticePreferences.PlaybackMode mode) {
            played.add(step.chordSymbol);
        }

        @Override
        public void stop() {
        }
    }

    private static final class FakeScheduler
            implements AbsoluteTimeScheduler.Clock, AbsoluteTimeScheduler.Scheduler {
        private final PriorityQueue<ScheduledTask> tasks = new PriorityQueue<>(
                Comparator.comparingLong((ScheduledTask task) -> task.deadlineNanos)
                        .thenComparingLong(task -> task.sequence)
        );
        private long nowNanos;
        private long sequence;
        private boolean closed;

        @Override
        public long nanoTime() {
            return nowNanos;
        }

        @Override
        public AbsoluteTimeScheduler.Handle schedule(Runnable task, long delayNanos) {
            if (closed) {
                throw new IllegalStateException("Fake scheduler is closed.");
            }
            ScheduledTask scheduled = new ScheduledTask(
                    nowNanos + Math.max(0L, delayNanos),
                    sequence++,
                    task
            );
            tasks.add(scheduled);
            return scheduled;
        }

        void advanceBy(long deltaNanos) {
            require(deltaNanos >= 0L, "Fake time cannot move backwards.");
            long target = nowNanos + deltaNanos;
            while (!tasks.isEmpty() && tasks.peek().deadlineNanos <= target) {
                ScheduledTask next = tasks.remove();
                if (next.cancelled) {
                    continue;
                }
                nowNanos = next.deadlineNanos;
                next.task.run();
            }
            nowNanos = target;
        }

        void advanceLateBy(long deltaNanos) {
            require(deltaNanos >= 0L, "Fake time cannot move backwards.");
            long target = nowNanos + deltaNanos;
            nowNanos = target;
            while (!tasks.isEmpty() && tasks.peek().deadlineNanos <= target) {
                ScheduledTask next = tasks.remove();
                if (!next.cancelled) {
                    next.task.run();
                }
            }
        }

        @Override
        public void close() {
            closed = true;
            tasks.clear();
        }

        private static final class ScheduledTask implements AbsoluteTimeScheduler.Handle {
            final long deadlineNanos;
            final long sequence;
            final Runnable task;
            boolean cancelled;

            ScheduledTask(long deadlineNanos, long sequence, Runnable task) {
                this.deadlineNanos = deadlineNanos;
                this.sequence = sequence;
                this.task = task;
            }

            @Override
            public void cancel() {
                cancelled = true;
            }

            @Override
            public boolean isCancelled() {
                return cancelled;
            }
        }
    }
}
