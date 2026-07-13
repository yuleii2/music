package com.k2.music;

/** Aggregate values shown on the local profile/practice overview. */
public final class PracticeSummary {
    public final long todayPracticeSeconds;
    public final int lastSevenDaysSessionCount;
    public final long lastSevenDaysPracticeSeconds;
    public final int lastSevenDaysCompletionCount;
    public final String mostPracticedChord;
    public final int mostPracticedChordSessionCount;
    public final int bestCompletionCount;
    public final int bestStreak;
    public final String bestSessionId;
    public final int totalSessionCount;

    PracticeSummary(
            long todayPracticeSeconds,
            int lastSevenDaysSessionCount,
            long lastSevenDaysPracticeSeconds,
            int lastSevenDaysCompletionCount,
            String mostPracticedChord,
            int mostPracticedChordSessionCount,
            int bestCompletionCount,
            int bestStreak,
            String bestSessionId,
            int totalSessionCount
    ) {
        this.todayPracticeSeconds = todayPracticeSeconds;
        this.lastSevenDaysSessionCount = lastSevenDaysSessionCount;
        this.lastSevenDaysPracticeSeconds = lastSevenDaysPracticeSeconds;
        this.lastSevenDaysCompletionCount = lastSevenDaysCompletionCount;
        this.mostPracticedChord = mostPracticedChord;
        this.mostPracticedChordSessionCount = mostPracticedChordSessionCount;
        this.bestCompletionCount = bestCompletionCount;
        this.bestStreak = bestStreak;
        this.bestSessionId = bestSessionId;
        this.totalSessionCount = totalSessionCount;
    }

    public static PracticeSummary empty() {
        return new PracticeSummary(0L, 0, 0L, 0, "", 0, 0, 0, "", 0);
    }
}
