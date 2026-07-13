package com.k2.music;

import java.util.Objects;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/** Monotonic-clock scheduling primitives shared by playback and the metronome. */
public final class AbsoluteTimeScheduler {
    public interface Clock {
        long nanoTime();
    }

    public interface Handle {
        void cancel();

        boolean isCancelled();
    }

    public interface Scheduler extends AutoCloseable {
        Handle schedule(Runnable task, long delayNanos);

        @Override
        void close();
    }

    private AbsoluteTimeScheduler() {
    }

    public static Clock systemClock() {
        return System::nanoTime;
    }

    public static Scheduler singleThread(String threadName) {
        return new ExecutorScheduler(threadName);
    }

    public static long delayUntil(Clock clock, long deadlineNanos) {
        return Math.max(0L, deadlineNanos - clock.nanoTime());
    }

    private static final class ExecutorScheduler implements Scheduler {
        private final ScheduledThreadPoolExecutor executor;
        private final AtomicBoolean closed = new AtomicBoolean();

        ExecutorScheduler(String threadName) {
            String safeName = threadName == null || threadName.trim().isEmpty()
                    ? "k2-music-timing"
                    : threadName.trim();
            ThreadFactory factory = runnable -> {
                Thread thread = new Thread(runnable, safeName);
                thread.setDaemon(true);
                return thread;
            };
            executor = new ScheduledThreadPoolExecutor(1, factory);
            executor.setRemoveOnCancelPolicy(true);
            executor.setExecuteExistingDelayedTasksAfterShutdownPolicy(false);
        }

        @Override
        public Handle schedule(Runnable task, long delayNanos) {
            Objects.requireNonNull(task, "task");
            if (closed.get()) {
                throw new IllegalStateException("Scheduler has been closed.");
            }
            ScheduledFuture<?> future = executor.schedule(task, Math.max(0L, delayNanos), TimeUnit.NANOSECONDS);
            return new Handle() {
                @Override
                public void cancel() {
                    future.cancel(false);
                }

                @Override
                public boolean isCancelled() {
                    return future.isCancelled();
                }
            };
        }

        @Override
        public void close() {
            if (closed.compareAndSet(false, true)) {
                executor.shutdownNow();
            }
        }
    }
}
