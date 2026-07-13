package com.k2.music;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TimeZone;

/** Versioned local practice-history store with dashboard summaries. */
public final class PracticeRecordStore {
    private static final int MAGIC = 0x4B325052; // K2PR
    public static final int SCHEMA_VERSION = 3;
    private static final int PREVIOUS_VERSION = 2;
    private static final int LEGACY_VERSION = 1;
    private static final int MAX_RECORDS = 100_000;

    private final File storageFile;
    private boolean olderVersionRead;

    public PracticeRecordStore(File storageFile) {
        this.storageFile = Objects.requireNonNull(storageFile, "storageFile").getAbsoluteFile();
    }

    public static File defaultFile(File appFilesDirectory) {
        Objects.requireNonNull(appFilesDirectory, "appFilesDirectory");
        return new File(appFilesDirectory, "practice-records-v1.bin");
    }

    public synchronized PracticeSession add(PracticeSession session) {
        Objects.requireNonNull(session, "session");
        Map<String, PracticeSession> records = loadRecords();
        if (records.containsKey(session.id)) {
            throw new IllegalArgumentException("Practice session already exists: " + session.id);
        }
        ensureCapacity(records.size() + 1);
        records.put(session.id, session);
        persist(records);
        return session;
    }

    public synchronized PracticeSession save(PracticeSession session) {
        Objects.requireNonNull(session, "session");
        Map<String, PracticeSession> records = loadRecords();
        ensureCapacity(records.containsKey(session.id) ? records.size() : records.size() + 1);
        records.put(session.id, session);
        persist(records);
        return session;
    }

    /** Returns the stored session, or {@code null} when the id is unknown. */
    public synchronized PracticeSession read(String id) {
        if (id == null || id.trim().isEmpty()) {
            return null;
        }
        return loadRecords().get(id.trim());
    }

    public synchronized List<PracticeSession> list() {
        List<PracticeSession> result = new ArrayList<>(loadRecords().values());
        Collections.sort(result, (left, right) -> {
            int byDate = Long.compare(right.startedAtEpochMillis, left.startedAtEpochMillis);
            return byDate != 0 ? byDate : left.id.compareTo(right.id);
        });
        return Collections.unmodifiableList(result);
    }

    public synchronized boolean delete(String id) {
        if (id == null || id.trim().isEmpty()) {
            return false;
        }
        Map<String, PracticeSession> records = loadRecords();
        if (records.remove(id.trim()) == null) {
            return false;
        }
        persist(records);
        return true;
    }

    public synchronized void clear() {
        persist(new LinkedHashMap<>());
    }

    /** Transactional replacement used by verified full-backup restore. */
    public synchronized void replaceAll(List<PracticeSession> sessions) {
        Map<String, PracticeSession> records = new LinkedHashMap<>();
        if (sessions != null) {
            for (PracticeSession session : sessions) {
                Objects.requireNonNull(session, "session");
                records.put(session.id, session);
            }
        }
        ensureCapacity(records.size());
        persist(records);
    }

    public synchronized PracticeSummary summarize(long nowEpochMillis, TimeZone timeZone) {
        Objects.requireNonNull(timeZone, "timeZone");
        if (nowEpochMillis < 0) {
            throw new IllegalArgumentException("Summary time cannot be negative.");
        }
        List<PracticeSession> sessions = new ArrayList<>(loadRecords().values());
        if (sessions.isEmpty()) {
            return PracticeSummary.empty();
        }

        Calendar startOfToday = Calendar.getInstance(timeZone);
        startOfToday.setTimeInMillis(nowEpochMillis);
        startOfToday.set(Calendar.HOUR_OF_DAY, 0);
        startOfToday.set(Calendar.MINUTE, 0);
        startOfToday.set(Calendar.SECOND, 0);
        startOfToday.set(Calendar.MILLISECOND, 0);
        long todayStart = startOfToday.getTimeInMillis();
        Calendar sevenDayWindow = (Calendar) startOfToday.clone();
        sevenDayWindow.add(Calendar.DAY_OF_YEAR, -6);
        long sevenDayStart = sevenDayWindow.getTimeInMillis();

        long todaySeconds = 0L;
        int recentSessions = 0;
        long recentSeconds = 0L;
        int recentCompletions = 0;
        Map<String, Integer> chordSessionCounts = new HashMap<>();
        PracticeSession best = null;

        for (PracticeSession session : sessions) {
            if (session.startedAtEpochMillis >= todayStart && session.startedAtEpochMillis <= nowEpochMillis) {
                todaySeconds += session.durationSeconds;
            }
            if (session.startedAtEpochMillis >= sevenDayStart && session.startedAtEpochMillis <= nowEpochMillis) {
                recentSessions++;
                recentSeconds += session.durationSeconds;
                if (!session.legacy) recentCompletions += session.successCount;
            }
            Set<String> uniqueChords = new HashSet<>(session.chordSymbols);
            for (String chord : uniqueChords) {
                Integer priorCount = chordSessionCounts.get(chord);
                chordSessionCounts.put(chord, priorCount == null ? 1 : priorCount + 1);
            }
            if (!session.legacy && (best == null || isBetter(session, best))) {
                best = session;
            }
        }

        String mostChord = "";
        int mostCount = 0;
        for (Map.Entry<String, Integer> entry : chordSessionCounts.entrySet()) {
            if (entry.getValue() > mostCount
                    || (entry.getValue() == mostCount && entry.getKey().compareTo(mostChord) < 0)) {
                mostChord = entry.getKey();
                mostCount = entry.getValue();
            }
        }
        return new PracticeSummary(
                todaySeconds,
                recentSessions,
                recentSeconds,
                recentCompletions,
                mostChord,
                mostCount,
                best == null ? 0 : best.completionCount,
                best == null ? 0 : best.bestStreak,
                best == null ? "" : best.id,
                sessions.size()
        );
    }

    public synchronized PracticeSummary summarizeNow() {
        return summarize(System.currentTimeMillis(), TimeZone.getDefault());
    }

    public File storageFile() {
        return storageFile;
    }

    private Map<String, PracticeSession> loadRecords() {
        try {
            olderVersionRead = false;
            Map<String, PracticeSession> records = BinaryStoreSupport.readWithBackup(storageFile, this::readFile);
            Map<String, PracticeSession> result = records == null ? new LinkedHashMap<>() : records;
            if (olderVersionRead) {
                olderVersionRead = false;
                persist(result);
            }
            return result;
        } catch (IOException | RuntimeException exception) {
            throw new LocalStoreException("Unable to read local practice records.", exception);
        }
    }

    private Map<String, PracticeSession> readFile(DataInputStream input) throws IOException {
        if (input.readInt() != MAGIC) {
            throw new IOException("Invalid practice-record header.");
        }
        int version = input.readInt();
        if (version != LEGACY_VERSION && version != PREVIOUS_VERSION && version != SCHEMA_VERSION) {
            throw new IOException("Unsupported practice-record version: " + version);
        }
        olderVersionRead = version < SCHEMA_VERSION;
        int count = input.readInt();
        if (count < 0 || count > MAX_RECORDS) {
            throw new IOException("Invalid practice-record count: " + count);
        }
        Map<String, PracticeSession> records = new LinkedHashMap<>();
        for (int i = 0; i < count; i++) {
            PracticeSession session = version == LEGACY_VERSION ? readLegacySession(input) : readSession(input, version);
            if (records.put(session.id, session) != null) {
                throw new IOException("Duplicate practice session id: " + session.id);
            }
        }
        return records;
    }

    private PracticeSession readLegacySession(DataInputStream input) throws IOException {
        String id = BinaryStoreSupport.readString(input);
        long startedAt = input.readLong();
        PracticeSession.Type type;
        try {
            type = PracticeSession.Type.valueOf(BinaryStoreSupport.readString(input));
        } catch (IllegalArgumentException exception) {
            throw new IOException("Invalid practice type.", exception);
        }
        int bpm = input.readInt();
        int durationSeconds = input.readInt();
        int completions = input.readInt();
        int bestStreak = input.readInt();
        int chordCount = input.readInt();
        if (chordCount < 1 || chordCount > 256) {
            throw new IOException("Invalid practice chord count: " + chordCount);
        }
        List<String> chords = new ArrayList<>(chordCount);
        for (int i = 0; i < chordCount; i++) {
            chords.add(BinaryStoreSupport.readString(input));
        }
        try {
            return new PracticeSession(id, startedAt, type, chords, bpm, durationSeconds, completions, bestStreak);
        } catch (IllegalArgumentException exception) {
            throw new IOException("Invalid practice record.", exception);
        }
    }

    private PracticeSession readSession(DataInputStream input, int version) throws IOException {
        try {
            String id = BinaryStoreSupport.readString(input);
            long startedAt = input.readLong();
            long endedAt = input.readLong();
            PracticeSession.Type type = PracticeSession.Type.valueOf(BinaryStoreSupport.readString(input));
            int bpm = input.readInt();
            String signature = BinaryStoreSupport.readString(input);
            PracticeSession.SwitchMode switchMode = PracticeSession.SwitchMode.valueOf(BinaryStoreSupport.readString(input));
            int plannedDuration = input.readInt();
            int actualDuration = input.readInt();
            int attemptCount = input.readInt();
            int successCount = input.readInt();
            int failureCount = input.readInt();
            int bestStreak = input.readInt();
            int legacyCompletionCount = input.readInt();
            boolean legacy = input.readBoolean();
            int chordCount = input.readInt();
            if (chordCount < 1 || chordCount > 256) {
                throw new IOException("Invalid practice chord count: " + chordCount);
            }
            List<String> chords = new ArrayList<>(chordCount);
            for (int index = 0; index < chordCount; index++) {
                chords.add(BinaryStoreSupport.readString(input));
            }
            String sourceProgressionId = version >= SCHEMA_VERSION ? BinaryStoreSupport.readString(input) : "";
            boolean useProgressionRhythm = version >= SCHEMA_VERSION && input.readBoolean();
            return new PracticeSession(
                    id,
                    startedAt,
                    endedAt,
                    type,
                    chords,
                    bpm,
                    signature,
                    switchMode,
                    plannedDuration,
                    actualDuration,
                    attemptCount,
                    successCount,
                    failureCount,
                    bestStreak,
                    legacyCompletionCount,
                    legacy,
                    sourceProgressionId,
                    useProgressionRhythm
            );
        } catch (IllegalArgumentException exception) {
            throw new IOException("Invalid practice record.", exception);
        }
    }

    private void persist(Map<String, PracticeSession> records) {
        ensureCapacity(records.size());
        try {
            BinaryStoreSupport.writeAtomically(storageFile, output -> writeFile(output, records));
        } catch (IOException | RuntimeException exception) {
            throw new LocalStoreException("Unable to save local practice records.", exception);
        }
    }

    private void writeFile(DataOutputStream output, Map<String, PracticeSession> records) throws IOException {
        output.writeInt(MAGIC);
        output.writeInt(SCHEMA_VERSION);
        output.writeInt(records.size());
        for (PracticeSession session : records.values()) {
            BinaryStoreSupport.writeString(output, session.id);
            output.writeLong(session.startedAtEpochMillis);
            output.writeLong(session.endedAtEpochMillis);
            BinaryStoreSupport.writeString(output, session.type.name());
            output.writeInt(session.bpm);
            BinaryStoreSupport.writeString(output, session.timeSignature);
            BinaryStoreSupport.writeString(output, session.switchMode.name());
            output.writeInt(session.plannedDurationSeconds);
            output.writeInt(session.actualDurationSeconds);
            output.writeInt(session.attemptCount);
            output.writeInt(session.successCount);
            output.writeInt(session.failureCount);
            output.writeInt(session.bestStreak);
            output.writeInt(session.legacy ? session.completionCount : 0);
            output.writeBoolean(session.legacy);
            output.writeInt(session.chordSymbols.size());
            for (String chord : session.chordSymbols) {
                BinaryStoreSupport.writeString(output, chord);
            }
            BinaryStoreSupport.writeString(output, session.sourceProgressionId);
            output.writeBoolean(session.useProgressionRhythm);
        }
    }

    private static boolean isBetter(PracticeSession candidate, PracticeSession current) {
        if (candidate.completionCount != current.completionCount) {
            return candidate.completionCount > current.completionCount;
        }
        if (candidate.bestStreak != current.bestStreak) {
            return candidate.bestStreak > current.bestStreak;
        }
        if (candidate.durationSeconds != current.durationSeconds) {
            return candidate.durationSeconds < current.durationSeconds;
        }
        if (candidate.startedAtEpochMillis != current.startedAtEpochMillis) {
            return candidate.startedAtEpochMillis > current.startedAtEpochMillis;
        }
        return candidate.id.compareTo(current.id) < 0;
    }

    private static void ensureCapacity(int count) {
        if (count > MAX_RECORDS) {
            throw new IllegalStateException("Local practice-record limit reached.");
        }
    }
}
