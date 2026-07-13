package com.k2.music;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Atomic, idempotent store for directional transition attempts. */
public final class TransitionAttemptStore {
    private static final int MAGIC = 0x4B325441; // K2TA
    public static final int SCHEMA_VERSION = 1;
    private static final int MAX_RECORDS = 1_000_000;

    private final File storageFile;

    public TransitionAttemptStore(File storageFile) {
        this.storageFile = Objects.requireNonNull(storageFile, "storageFile").getAbsoluteFile();
    }

    public static File defaultFile(File appFilesDirectory) {
        Objects.requireNonNull(appFilesDirectory, "appFilesDirectory");
        return new File(appFilesDirectory, "transition-attempts-v1.bin");
    }

    /** Saves by stable id; importing or retrying the same attempt never doubles statistics. */
    public synchronized TransitionAttempt save(TransitionAttempt attempt) {
        Objects.requireNonNull(attempt, "attempt");
        Map<String, TransitionAttempt> records = loadRecords();
        ensureCapacity(records.containsKey(attempt.id) ? records.size() : records.size() + 1);
        records.put(attempt.id, attempt);
        persist(records);
        return attempt;
    }

    public synchronized List<TransitionAttempt> list() {
        List<TransitionAttempt> result = new ArrayList<>(loadRecords().values());
        Collections.sort(result, (left, right) -> {
            int byTime = Long.compare(right.timestampEpochMillis, left.timestampEpochMillis);
            return byTime != 0 ? byTime : left.id.compareTo(right.id);
        });
        return Collections.unmodifiableList(result);
    }

    public synchronized List<TransitionAttempt> forSession(String sessionId) {
        String wanted = sessionId == null ? "" : sessionId.trim();
        if (wanted.isEmpty()) return Collections.emptyList();
        List<TransitionAttempt> result = new ArrayList<>();
        for (TransitionAttempt attempt : list()) {
            if (attempt.sessionId.equals(wanted)) result.add(attempt);
        }
        Collections.reverse(result);
        return Collections.unmodifiableList(result);
    }

    public synchronized void replaceAll(List<TransitionAttempt> attempts) {
        Map<String, TransitionAttempt> records = new LinkedHashMap<>();
        if (attempts != null) {
            for (TransitionAttempt attempt : attempts) {
                Objects.requireNonNull(attempt, "attempt");
                records.put(attempt.id, attempt);
            }
        }
        ensureCapacity(records.size());
        persist(records);
    }

    public synchronized int deleteSession(String sessionId) {
        String wanted = sessionId == null ? "" : sessionId.trim();
        if (wanted.isEmpty()) return 0;
        Map<String, TransitionAttempt> records = loadRecords();
        int before = records.size();
        Iterator<Map.Entry<String, TransitionAttempt>> iterator = records.entrySet().iterator();
        while (iterator.hasNext()) {
            if (iterator.next().getValue().sessionId.equals(wanted)) iterator.remove();
        }
        int removed = before - records.size();
        if (removed > 0) persist(records);
        return removed;
    }

    public synchronized void clear() {
        persist(new LinkedHashMap<>());
    }

    public File storageFile() {
        return storageFile;
    }

    private Map<String, TransitionAttempt> loadRecords() {
        try {
            Map<String, TransitionAttempt> records = BinaryStoreSupport.readWithBackup(storageFile, this::readFile);
            return records == null ? new LinkedHashMap<>() : records;
        } catch (IOException | RuntimeException exception) {
            throw new LocalStoreException("Unable to read transition attempts.", exception);
        }
    }

    private Map<String, TransitionAttempt> readFile(DataInputStream input) throws IOException {
        if (input.readInt() != MAGIC) throw new IOException("Invalid transition-attempt header.");
        int version = input.readInt();
        if (version != SCHEMA_VERSION) throw new IOException("Unsupported transition-attempt version: " + version);
        int count = input.readInt();
        if (count < 0 || count > MAX_RECORDS) throw new IOException("Invalid transition-attempt count: " + count);
        Map<String, TransitionAttempt> records = new LinkedHashMap<>();
        for (int index = 0; index < count; index++) {
            TransitionAttempt attempt = readAttempt(input);
            if (records.put(attempt.id, attempt) != null) {
                throw new IOException("Duplicate transition-attempt id: " + attempt.id);
            }
        }
        return records;
    }

    private TransitionAttempt readAttempt(DataInputStream input) throws IOException {
        try {
            String id = BinaryStoreSupport.readString(input);
            String sessionId = BinaryStoreSupport.readString(input);
            long timestamp = input.readLong();
            String from = BinaryStoreSupport.readString(input);
            String to = BinaryStoreSupport.readString(input);
            String fromVoicing = BinaryStoreSupport.readString(input);
            String toVoicing = BinaryStoreSupport.readString(input);
            int bpm = input.readInt();
            String signature = BinaryStoreSupport.readString(input);
            PracticeSession.SwitchMode switchMode = PracticeSession.SwitchMode.valueOf(BinaryStoreSupport.readString(input));
            boolean success = input.readBoolean();
            Long offset = input.readBoolean() ? input.readLong() : null;
            PracticeSession.Type practiceMode = PracticeSession.Type.valueOf(BinaryStoreSupport.readString(input));
            return new TransitionAttempt(id, sessionId, timestamp, from, to, fromVoicing, toVoicing,
                    bpm, signature, switchMode, success, offset, practiceMode);
        } catch (IllegalArgumentException exception) {
            throw new IOException("Invalid transition attempt.", exception);
        }
    }

    private void persist(Map<String, TransitionAttempt> records) {
        try {
            BinaryStoreSupport.writeAtomically(storageFile, output -> writeFile(output, records));
        } catch (IOException | RuntimeException exception) {
            throw new LocalStoreException("Unable to save transition attempts.", exception);
        }
    }

    private void writeFile(DataOutputStream output, Map<String, TransitionAttempt> records) throws IOException {
        output.writeInt(MAGIC);
        output.writeInt(SCHEMA_VERSION);
        output.writeInt(records.size());
        for (TransitionAttempt attempt : records.values()) {
            BinaryStoreSupport.writeString(output, attempt.id);
            BinaryStoreSupport.writeString(output, attempt.sessionId);
            output.writeLong(attempt.timestampEpochMillis);
            BinaryStoreSupport.writeString(output, attempt.fromChord);
            BinaryStoreSupport.writeString(output, attempt.toChord);
            BinaryStoreSupport.writeString(output, attempt.fromVoicingId);
            BinaryStoreSupport.writeString(output, attempt.toVoicingId);
            output.writeInt(attempt.bpm);
            BinaryStoreSupport.writeString(output, attempt.timeSignature);
            BinaryStoreSupport.writeString(output, attempt.switchMode.name());
            output.writeBoolean(attempt.success);
            output.writeBoolean(attempt.confirmationOffsetMillis != null);
            if (attempt.confirmationOffsetMillis != null) output.writeLong(attempt.confirmationOffsetMillis);
            BinaryStoreSupport.writeString(output, attempt.practiceMode.name());
        }
    }

    private static void ensureCapacity(int count) {
        if (count > MAX_RECORDS) throw new IllegalStateException("Transition-attempt limit reached.");
    }
}
