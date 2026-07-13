package com.k2.music;

import java.util.Objects;

/** Drift-resistant metronome driven by absolute monotonic deadlines. */
public final class MetronomeEngine implements AutoCloseable {
    public enum State {
        STOPPED,
        RUNNING,
        PAUSED,
        RELEASED
    }

    public interface ClickOutput {
        void click(boolean accented);
    }

    public interface Listener {
        default void onTick(int beatNumber, boolean accented, long scheduledTimeNanos) {
        }

        default void onStateChanged(State state) {
        }

        default void onError(RuntimeException error) {
        }
    }

    private static final ClickOutput NO_CLICK = accented -> {
    };
    private static final Listener NO_LISTENER = new Listener() {
    };

    private final Object lock = new Object();
    private final ClickOutput clickOutput;
    private final AbsoluteTimeScheduler.Clock clock;
    private final AbsoluteTimeScheduler.Scheduler scheduler;
    private final boolean ownsScheduler;

    private Listener listener = NO_LISTENER;
    private State state = State.STOPPED;
    private int bpm = 120;
    private TimeSignature timeSignature = TimeSignature.FOUR_FOUR;
    private boolean accentFirstBeat = true;
    private int nextBeatIndex;
    private long nextTickNanos;
    private long pausedRemainingNanos;
    private boolean awaitingFirstTick;
    private AbsoluteTimeScheduler.Handle scheduledHandle;
    private long scheduleGeneration;

    public MetronomeEngine(ClickOutput clickOutput) {
        this(
                clickOutput,
                AbsoluteTimeScheduler.systemClock(),
                AbsoluteTimeScheduler.singleThread("metronome-engine"),
                true
        );
    }

    public MetronomeEngine(
            ClickOutput clickOutput,
            AbsoluteTimeScheduler.Clock clock,
            AbsoluteTimeScheduler.Scheduler scheduler
    ) {
        this(clickOutput, clock, scheduler, false);
    }

    private MetronomeEngine(
            ClickOutput clickOutput,
            AbsoluteTimeScheduler.Clock clock,
            AbsoluteTimeScheduler.Scheduler scheduler,
            boolean ownsScheduler
    ) {
        this.clickOutput = clickOutput == null ? NO_CLICK : clickOutput;
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

    public void start() {
        startAt(clock.nanoTime());
    }

    /** Starts on an absolute monotonic anchor that can also be passed to ProgressionPlayer.playAt. */
    public void startAt(long anchorNanos) {
        synchronized (lock) {
            ensureNotReleased();
            if (state == State.RUNNING) {
                return;
            }
            cancelScheduledLocked();
            long now = clock.nanoTime();
            if (state == State.PAUSED) {
                nextTickNanos = Math.max(now, anchorNanos) + pausedRemainingNanos;
            } else {
                nextBeatIndex = 0;
                nextTickNanos = Math.max(now, anchorNanos);
                awaitingFirstTick = true;
            }
            state = State.RUNNING;
            notifyStateLocked();
            if (nextTickNanos <= now) {
                emitTickLocked(nextTickNanos);
            } else {
                scheduleTickLocked();
            }
        }
    }

    public void pause() {
        synchronized (lock) {
            ensureNotReleased();
            if (state != State.RUNNING) {
                return;
            }
            pausedRemainingNanos = Math.max(0L, nextTickNanos - clock.nanoTime());
            cancelScheduledLocked();
            state = State.PAUSED;
            notifyStateLocked();
        }
    }

    public void stop() {
        synchronized (lock) {
            ensureNotReleased();
            stopLocked();
            notifyStateLocked();
        }
    }

    public void setBpm(int bpm) {
        if (bpm < 40 || bpm > 240) {
            throw new IllegalArgumentException("Metronome BPM must be between 40 and 240.");
        }
        synchronized (lock) {
            ensureNotReleased();
            if (this.bpm == bpm) {
                return;
            }
            long oldInterval = intervalNanos(this.bpm);
            long newInterval = intervalNanos(bpm);
            long now = clock.nanoTime();
            if (state == State.RUNNING) {
                cancelScheduledLocked();
                if (!awaitingFirstTick) {
                    double remainingFraction = Math.max(0.0, Math.min(1.0,
                            (nextTickNanos - now) / (double) oldInterval));
                    nextTickNanos = now + Math.round(remainingFraction * newInterval);
                }
            } else if (state == State.PAUSED && !awaitingFirstTick) {
                double remainingFraction = Math.max(0.0, Math.min(1.0,
                        pausedRemainingNanos / (double) oldInterval));
                pausedRemainingNanos = Math.round(remainingFraction * newInterval);
            }
            this.bpm = bpm;
            if (state == State.RUNNING) {
                scheduleTickLocked();
            }
        }
    }

    public void setTimeSignature(TimeSignature timeSignature) {
        Objects.requireNonNull(timeSignature, "timeSignature");
        if (!timeSignature.isSupportedByMetronome()) {
            throw new IllegalArgumentException("Metronome supports 2/4, 3/4, 4/4, and 6/8.");
        }
        synchronized (lock) {
            ensureNotReleased();
            this.timeSignature = timeSignature;
            nextBeatIndex %= timeSignature.numerator;
        }
    }

    public void setAccentFirstBeat(boolean accentFirstBeat) {
        synchronized (lock) {
            ensureNotReleased();
            this.accentFirstBeat = accentFirstBeat;
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

    public TimeSignature timeSignature() {
        synchronized (lock) {
            return timeSignature;
        }
    }

    public int nextBeatNumber() {
        synchronized (lock) {
            return nextBeatIndex + 1;
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

    private void emitTickLocked(long scheduledTimeNanos) {
        if (state != State.RUNNING) {
            return;
        }
        awaitingFirstTick = false;
        boolean accented = accentFirstBeat && nextBeatIndex == 0;
        try {
            clickOutput.click(accented);
        } catch (RuntimeException exception) {
            notifyErrorLocked(exception);
        }
        if (state != State.RUNNING) {
            return;
        }
        try {
            listener.onTick(nextBeatIndex + 1, accented, scheduledTimeNanos);
        } catch (RuntimeException ignored) {
            // A visual observer cannot interrupt metronome timing.
        }
        if (state != State.RUNNING) {
            return;
        }
        nextBeatIndex = (nextBeatIndex + 1) % timeSignature.numerator;
        long interval = intervalNanos(bpm);
        nextTickNanos = scheduledTimeNanos + interval;
        long now = clock.nanoTime();
        while (nextTickNanos < now) {
            nextBeatIndex = (nextBeatIndex + 1) % timeSignature.numerator;
            nextTickNanos += interval;
        }
        scheduleTickLocked();
    }

    private void scheduleTickLocked() {
        final long generation = scheduleGeneration;
        final long deadline = nextTickNanos;
        scheduledHandle = scheduler.schedule(() -> {
            synchronized (lock) {
                if (generation != scheduleGeneration || state != State.RUNNING) {
                    return;
                }
                scheduledHandle = null;
                emitTickLocked(deadline);
            }
        }, Math.max(0L, deadline - clock.nanoTime()));
    }

    private void cancelScheduledLocked() {
        scheduleGeneration++;
        if (scheduledHandle != null) {
            scheduledHandle.cancel();
            scheduledHandle = null;
        }
    }

    private void stopLocked() {
        cancelScheduledLocked();
        nextBeatIndex = 0;
        nextTickNanos = 0L;
        pausedRemainingNanos = 0L;
        awaitingFirstTick = false;
        if (state != State.RELEASED) {
            state = State.STOPPED;
        }
    }

    private void notifyStateLocked() {
        try {
            listener.onStateChanged(state);
        } catch (RuntimeException ignored) {
            // UI state observers do not own the timing thread.
        }
    }

    private void notifyErrorLocked(RuntimeException error) {
        try {
            listener.onError(error);
        } catch (RuntimeException ignored) {
            // Preserve scheduling even if the error observer fails.
        }
    }

    private void ensureNotReleased() {
        if (state == State.RELEASED) {
            throw new IllegalStateException("MetronomeEngine has been released.");
        }
    }

    private static long intervalNanos(int bpm) {
        return Math.round(60_000_000_000.0 / bpm);
    }
}
