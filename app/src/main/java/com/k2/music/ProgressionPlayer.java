package com.k2.music;

import java.util.Objects;

/**
 * Lifecycle-friendly progression transport. Deadlines are derived from an absolute monotonic
 * timeline, so a late callback does not add its lateness to every following chord.
 */
public final class ProgressionPlayer implements AutoCloseable {
    public enum State {
        STOPPED,
        PLAYING,
        PAUSED,
        RELEASED
    }

    public interface AudioOutput {
        void play(ProgressionStep step, PracticePreferences.PlaybackMode mode);

        void stop();
    }

    public interface Listener {
        default void onStateChanged(State state) {
        }

        default void onPositionChanged(Position position) {
        }

        default void onError(RuntimeException error) {
        }
    }

    public static final class Position {
        public final State state;
        public final int stepIndex;
        public final int measureNumber;
        public final int beatNumber;
        public final int stepBeatNumber;
        public final double elapsedBeats;
        public final ProgressionStep currentStep;
        public final ProgressionStep nextStep;
        public final boolean pendingScheduledStart;

        Position(
                State state,
                int stepIndex,
                int measureNumber,
                int beatNumber,
                int stepBeatNumber,
                double elapsedBeats,
                ProgressionStep currentStep,
                ProgressionStep nextStep,
                boolean pendingScheduledStart
        ) {
            this.state = state;
            this.stepIndex = stepIndex;
            this.measureNumber = measureNumber;
            this.beatNumber = beatNumber;
            this.stepBeatNumber = stepBeatNumber;
            this.elapsedBeats = elapsedBeats;
            this.currentStep = currentStep;
            this.nextStep = nextStep;
            this.pendingScheduledStart = pendingScheduledStart;
        }
    }

    private static final AudioOutput NO_AUDIO = new AudioOutput() {
        @Override
        public void play(ProgressionStep step, PracticePreferences.PlaybackMode mode) {
        }

        @Override
        public void stop() {
        }
    };
    private static final Listener NO_LISTENER = new Listener() {
    };
    private static final int MAX_LATE_ADVANCES = 16_384;

    private final Object lock = new Object();
    private final AudioOutput audioOutput;
    private final AbsoluteTimeScheduler.Clock clock;
    private final AbsoluteTimeScheduler.Scheduler scheduler;
    private final boolean ownsScheduler;

    private ChordProgression progression;
    private State state = State.STOPPED;
    private Listener listener = NO_LISTENER;
    private PracticePreferences.PlaybackMode playbackMode = PracticePreferences.PlaybackMode.WHOLE_CHORD;
    private int bpm = 120;
    private boolean loop;
    private int currentIndex;
    private double pausedElapsedBeats;
    private long stepStartNanos;
    private long nextDeadlineNanos;
    private boolean pendingStart;
    private AbsoluteTimeScheduler.Handle scheduledHandle;
    private long scheduleGeneration;

    public ProgressionPlayer(AudioOutput audioOutput) {
        this(
                audioOutput,
                AbsoluteTimeScheduler.systemClock(),
                AbsoluteTimeScheduler.singleThread("progression-player"),
                true
        );
    }

    public ProgressionPlayer(
            AudioOutput audioOutput,
            AbsoluteTimeScheduler.Clock clock,
            AbsoluteTimeScheduler.Scheduler scheduler
    ) {
        this(audioOutput, clock, scheduler, false);
    }

    private ProgressionPlayer(
            AudioOutput audioOutput,
            AbsoluteTimeScheduler.Clock clock,
            AbsoluteTimeScheduler.Scheduler scheduler,
            boolean ownsScheduler
    ) {
        this.audioOutput = audioOutput == null ? NO_AUDIO : audioOutput;
        this.clock = Objects.requireNonNull(clock, "clock");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.ownsScheduler = ownsScheduler;
    }

    public void setListener(Listener listener) {
        synchronized (lock) {
            ensureNotReleased();
            this.listener = listener == null ? NO_LISTENER : listener;
        }
    }

    public void setProgression(ChordProgression progression) {
        Objects.requireNonNull(progression, "progression");
        synchronized (lock) {
            ensureNotReleased();
            stopLocked();
            this.progression = progression;
            this.bpm = progression.bpm;
            this.loop = progression.loop;
            this.currentIndex = 0;
            this.pausedElapsedBeats = 0.0;
            notifyPositionLocked();
        }
    }

    public void play() {
        playAt(clock.nanoTime());
    }

    /** Starts or resumes on a caller-supplied absolute anchor shared with a metronome. */
    public void playAt(long anchorNanos) {
        synchronized (lock) {
            ensurePlayableLocked();
            if (state == State.PLAYING) {
                return;
            }
            state = State.PLAYING;
            cancelScheduledLocked();
            long now = clock.nanoTime();
            long safeAnchor = Math.max(now, anchorNanos);
            stepStartNanos = safeAnchor - nanosForBeats(pausedElapsedBeats, bpm);
            nextDeadlineNanos = stepStartNanos + nanosForBeats(currentStepLocked().beats, bpm);
            pendingStart = safeAnchor > now;
            notifyStateLocked();
            if (state != State.PLAYING) {
                return;
            }
            if (pendingStart) {
                scheduleLocked(safeAnchor, this::handlePendingStartLocked);
                notifyPositionLocked();
            } else {
                startCurrentAudioLocked();
                if (state == State.PLAYING) {
                    scheduleLocked(nextDeadlineNanos, this::handleDeadlineLocked);
                    notifyPositionLocked();
                }
            }
        }
    }

    public void pause() {
        synchronized (lock) {
            ensureNotReleased();
            if (state != State.PLAYING) {
                return;
            }
            if (!pendingStart) {
                pausedElapsedBeats = elapsedBeatsLocked(clock.nanoTime());
            }
            cancelScheduledLocked();
            pendingStart = false;
            safeStopAudioLocked();
            state = State.PAUSED;
            notifyStateLocked();
            notifyPositionLocked();
        }
    }

    public void stop() {
        synchronized (lock) {
            ensureNotReleased();
            stopLocked();
            notifyStateLocked();
            notifyPositionLocked();
        }
    }

    /** Intended for Activity/Fragment onStop when preserving the practice position is desired. */
    public void pauseForLifecycle() {
        pause();
    }

    /** Intended for a page that should always reset when it leaves the foreground. */
    public void stopForLifecycle() {
        stop();
    }

    public void next() {
        synchronized (lock) {
            ensurePlayableLocked();
            int size = progression.steps.size();
            int target = currentIndex + 1;
            if (target >= size) {
                target = loop ? 0 : size - 1;
            }
            seekLocked(target);
        }
    }

    public void previous() {
        synchronized (lock) {
            ensurePlayableLocked();
            int target = currentIndex - 1;
            if (target < 0) {
                target = loop ? progression.steps.size() - 1 : 0;
            }
            seekLocked(target);
        }
    }

    public void seekToStep(int index) {
        synchronized (lock) {
            ensurePlayableLocked();
            if (index < 0 || index >= progression.steps.size()) {
                throw new IndexOutOfBoundsException("Progression step index: " + index);
            }
            seekLocked(index);
        }
    }

    public void setBpm(int bpm) {
        if (bpm < 40 || bpm > 240) {
            throw new IllegalArgumentException("BPM must be between 40 and 240.");
        }
        synchronized (lock) {
            ensureNotReleased();
            if (this.bpm == bpm) {
                return;
            }
            long now = clock.nanoTime();
            double elapsed = state == State.PLAYING && !pendingStart
                    ? elapsedBeatsLocked(now)
                    : pausedElapsedBeats;
            int previousBpm = this.bpm;
            long pendingAnchor = pendingStart
                    ? stepStartNanos + nanosForBeats(pausedElapsedBeats, previousBpm)
                    : 0L;
            this.bpm = bpm;
            pausedElapsedBeats = elapsed;
            if (state == State.PLAYING && pendingStart) {
                cancelScheduledLocked();
                stepStartNanos = pendingAnchor - nanosForBeats(elapsed, bpm);
                nextDeadlineNanos = stepStartNanos + nanosForBeats(currentStepLocked().beats, bpm);
                scheduleLocked(pendingAnchor, this::handlePendingStartLocked);
            } else if (state == State.PLAYING) {
                cancelScheduledLocked();
                stepStartNanos = now - nanosForBeats(elapsed, bpm);
                nextDeadlineNanos = stepStartNanos + nanosForBeats(currentStepLocked().beats, bpm);
                scheduleLocked(nextDeadlineNanos, this::handleDeadlineLocked);
            }
            notifyPositionLocked();
        }
    }

    public void setLoop(boolean loop) {
        synchronized (lock) {
            ensureNotReleased();
            this.loop = loop;
        }
    }

    public void setPlaybackMode(PracticePreferences.PlaybackMode playbackMode) {
        synchronized (lock) {
            ensureNotReleased();
            this.playbackMode = Objects.requireNonNull(playbackMode, "playbackMode");
        }
    }

    public Position position() {
        synchronized (lock) {
            return positionLocked();
        }
    }

    public State state() {
        synchronized (lock) {
            return state;
        }
    }

    public int bpm() {
        synchronized (lock) {
            return bpm;
        }
    }

    public boolean isLooping() {
        synchronized (lock) {
            return loop;
        }
    }

    @Override
    public void close() {
        synchronized (lock) {
            if (state == State.RELEASED) {
                return;
            }
            stopLocked();
            state = State.RELEASED;
            notifyStateLocked();
            listener = NO_LISTENER;
        }
        if (ownsScheduler) {
            scheduler.close();
        }
    }

    private void handlePendingStartLocked() {
        if (state != State.PLAYING || !pendingStart) {
            return;
        }
        pendingStart = false;
        startCurrentAudioLocked();
        if (state != State.PLAYING) {
            return;
        }
        long now = clock.nanoTime();
        if (nextDeadlineNanos <= now) {
            handleDeadlineLocked();
        } else {
            scheduleLocked(nextDeadlineNanos, this::handleDeadlineLocked);
            notifyPositionLocked();
        }
    }

    private void handleDeadlineLocked() {
        if (state != State.PLAYING || pendingStart) {
            return;
        }
        long now = clock.nanoTime();
        long timeline = nextDeadlineNanos;
        int advances = 0;
        do {
            if (!advanceIndexLocked()) {
                stopLocked();
                notifyStateLocked();
                notifyPositionLocked();
                return;
            }
            stepStartNanos = timeline;
            pausedElapsedBeats = 0.0;
            nextDeadlineNanos = stepStartNanos + nanosForBeats(currentStepLocked().beats, bpm);
            timeline = nextDeadlineNanos;
            advances++;
        } while (nextDeadlineNanos < now && advances < MAX_LATE_ADVANCES);

        if (nextDeadlineNanos < now) {
            stepStartNanos = now;
            nextDeadlineNanos = now + nanosForBeats(currentStepLocked().beats, bpm);
        }
        startCurrentAudioLocked();
        if (state == State.PLAYING) {
            scheduleLocked(nextDeadlineNanos, this::handleDeadlineLocked);
            notifyPositionLocked();
        }
    }

    private boolean advanceIndexLocked() {
        currentIndex++;
        if (currentIndex < progression.steps.size()) {
            return true;
        }
        if (loop) {
            currentIndex = 0;
            return true;
        }
        currentIndex = 0;
        return false;
    }

    private void seekLocked(int target) {
        boolean wasPlaying = state == State.PLAYING;
        cancelScheduledLocked();
        pendingStart = false;
        currentIndex = target;
        pausedElapsedBeats = 0.0;
        safeStopAudioLocked();
        if (wasPlaying) {
            long now = clock.nanoTime();
            stepStartNanos = now;
            nextDeadlineNanos = now + nanosForBeats(currentStepLocked().beats, bpm);
            startCurrentAudioLocked();
            if (state == State.PLAYING) {
                scheduleLocked(nextDeadlineNanos, this::handleDeadlineLocked);
            }
        }
        notifyPositionLocked();
    }

    private void stopLocked() {
        cancelScheduledLocked();
        pendingStart = false;
        safeStopAudioLocked();
        currentIndex = 0;
        pausedElapsedBeats = 0.0;
        stepStartNanos = 0L;
        nextDeadlineNanos = 0L;
        if (state != State.RELEASED) {
            state = State.STOPPED;
        }
    }

    private ProgressionStep currentStepLocked() {
        return progression.steps.get(currentIndex);
    }

    private double elapsedBeatsLocked(long nowNanos) {
        if (state != State.PLAYING || pendingStart) {
            return pausedElapsedBeats;
        }
        double elapsed = (nowNanos - stepStartNanos) / (double) beatNanos(bpm);
        return Math.max(0.0, Math.min(currentStepLocked().beats, elapsed));
    }

    private Position positionLocked() {
        if (progression == null || progression.steps.isEmpty()) {
            return new Position(state, -1, 0, 0, 0, 0.0, null, null, pendingStart);
        }
        ProgressionStep current = currentStepLocked();
        double elapsed = elapsedBeatsLocked(clock.nanoTime());
        int stepBeat = Math.min(
                Math.max(1, (int) Math.floor(elapsed) + 1),
                Math.max(1, (int) Math.ceil(current.beats))
        );
        double beatsBeforeCurrent = 0.0;
        for (int i = 0; i < currentIndex; i++) {
            beatsBeforeCurrent += progression.steps.get(i).beats;
        }
        long completedMeterBeats = (long) Math.floor(beatsBeforeCurrent + elapsed + 0.000_000_001);
        int beatsPerMeasure = progression.timeSignature.numerator;
        int measure = (int) (completedMeterBeats / beatsPerMeasure) + 1;
        int beat = (int) (completedMeterBeats % beatsPerMeasure) + 1;
        int nextIndex = currentIndex + 1;
        ProgressionStep next = nextIndex < progression.steps.size()
                ? progression.steps.get(nextIndex)
                : loop ? progression.steps.get(0) : null;
        return new Position(state, currentIndex, measure, beat, stepBeat, elapsed, current, next, pendingStart);
    }

    private void scheduleLocked(long deadlineNanos, Runnable action) {
        final long generation = scheduleGeneration;
        scheduledHandle = scheduler.schedule(() -> {
            synchronized (lock) {
                if (generation != scheduleGeneration || state == State.RELEASED) {
                    return;
                }
                scheduledHandle = null;
                try {
                    action.run();
                } catch (RuntimeException exception) {
                    safeNotifyErrorLocked(exception);
                    stopLocked();
                    notifyStateLocked();
                    notifyPositionLocked();
                }
            }
        }, Math.max(0L, deadlineNanos - clock.nanoTime()));
    }

    private void cancelScheduledLocked() {
        scheduleGeneration++;
        if (scheduledHandle != null) {
            scheduledHandle.cancel();
            scheduledHandle = null;
        }
    }

    private void startCurrentAudioLocked() {
        safeStopAudioLocked();
        if (state != State.PLAYING) {
            return;
        }
        try {
            audioOutput.play(currentStepLocked(), playbackMode);
        } catch (RuntimeException exception) {
            safeNotifyErrorLocked(exception);
        }
    }

    private void safeStopAudioLocked() {
        try {
            audioOutput.stop();
        } catch (RuntimeException exception) {
            safeNotifyErrorLocked(exception);
        }
    }

    private void notifyStateLocked() {
        try {
            listener.onStateChanged(state);
        } catch (RuntimeException ignored) {
            // UI observers must not be able to stop the transport thread.
        }
    }

    private void notifyPositionLocked() {
        try {
            listener.onPositionChanged(positionLocked());
        } catch (RuntimeException ignored) {
            // UI observers must not be able to stop the transport thread.
        }
    }

    private void safeNotifyErrorLocked(RuntimeException error) {
        try {
            listener.onError(error);
        } catch (RuntimeException ignored) {
            // Preserve transport cleanup even when an error observer fails.
        }
    }

    private void ensurePlayableLocked() {
        ensureNotReleased();
        if (progression == null || progression.steps.isEmpty()) {
            throw new IllegalStateException("A non-empty progression must be set before playback.");
        }
    }

    private void ensureNotReleased() {
        if (state == State.RELEASED) {
            throw new IllegalStateException("ProgressionPlayer has been released.");
        }
    }

    private static long beatNanos(int bpm) {
        return Math.round(60_000_000_000.0 / bpm);
    }

    private static long nanosForBeats(double beats, int bpm) {
        return Math.max(0L, Math.round(beats * beatNanos(bpm)));
    }
}
