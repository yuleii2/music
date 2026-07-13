package com.k2.music;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Versioned, atomic, file-backed CRUD store for chord progressions. */
public final class ProgressionStore {
    public interface WallClock {
        long currentTimeMillis();
    }

    private static final int MAGIC = 0x4B325047; // K2PG
    private static final int VERSION = 1;
    private static final int MAX_RECORDS = 10_000;

    private final File storageFile;
    private final WallClock wallClockMillis;

    public ProgressionStore(File storageFile) {
        this(storageFile, System::currentTimeMillis);
    }

    public ProgressionStore(File storageFile, WallClock wallClockMillis) {
        this.storageFile = Objects.requireNonNull(storageFile, "storageFile").getAbsoluteFile();
        this.wallClockMillis = Objects.requireNonNull(wallClockMillis, "wallClockMillis");
    }

    public static File defaultFile(File appFilesDirectory) {
        Objects.requireNonNull(appFilesDirectory, "appFilesDirectory");
        return new File(appFilesDirectory, "progressions-v1.bin");
    }

    public synchronized ChordProgression create(ChordProgression progression) {
        Objects.requireNonNull(progression, "progression");
        Map<String, ChordProgression> records = loadRecords();
        if (records.containsKey(progression.id)) {
            throw new IllegalArgumentException("Progression already exists: " + progression.id);
        }
        ensureCapacity(records.size() + 1);
        long now = safeNow();
        ChordProgression stored = progression.withStoreTimestamps(now, now);
        records.put(stored.id, stored);
        persist(records);
        return stored;
    }

    /** Returns the stored progression, or {@code null} when the id is unknown. */
    public synchronized ChordProgression read(String id) {
        if (id == null || id.trim().isEmpty()) {
            return null;
        }
        return loadRecords().get(id.trim());
    }

    public synchronized List<ChordProgression> list() {
        List<ChordProgression> result = new ArrayList<>(loadRecords().values());
        Collections.sort(result, (left, right) -> {
            int byUpdated = Long.compare(right.updatedAtEpochMillis, left.updatedAtEpochMillis);
            if (byUpdated != 0) {
                return byUpdated;
            }
            int byName = left.name.compareTo(right.name);
            return byName != 0 ? byName : left.id.compareTo(right.id);
        });
        return Collections.unmodifiableList(result);
    }

    public synchronized ChordProgression update(ChordProgression progression) {
        Objects.requireNonNull(progression, "progression");
        Map<String, ChordProgression> records = loadRecords();
        ChordProgression previous = records.get(progression.id);
        if (previous == null) {
            throw new IllegalArgumentException("Progression does not exist: " + progression.id);
        }
        long updatedAt = Math.max(previous.createdAtEpochMillis, safeNow());
        ChordProgression stored = progression.withStoreTimestamps(previous.createdAtEpochMillis, updatedAt);
        records.put(stored.id, stored);
        persist(records);
        return stored;
    }

    public synchronized ChordProgression upsert(ChordProgression progression) {
        Objects.requireNonNull(progression, "progression");
        return read(progression.id) != null ? update(progression) : create(progression);
    }

    public synchronized ChordProgression duplicate(String sourceId, String newName) {
        ChordProgression source = read(sourceId);
        if (source == null) {
            throw new IllegalArgumentException("Progression does not exist: " + sourceId);
        }
        long now = safeNow();
        ChordProgression copy = source.duplicate(UUID.randomUUID().toString(), newName, now);
        return create(copy);
    }

    public synchronized boolean delete(String id) {
        if (id == null || id.trim().isEmpty()) {
            return false;
        }
        Map<String, ChordProgression> records = loadRecords();
        if (records.remove(id.trim()) == null) {
            return false;
        }
        persist(records);
        return true;
    }

    public synchronized void clear() {
        persist(new LinkedHashMap<>());
    }

    /** Exact timestamp-preserving replacement after a backup has been fully validated. */
    public synchronized void replaceAll(List<ChordProgression> progressions) {
        Map<String, ChordProgression> records = new LinkedHashMap<>();
        if (progressions != null) {
            for (ChordProgression progression : progressions) {
                Objects.requireNonNull(progression, "progression");
                records.put(progression.id, progression);
            }
        }
        ensureCapacity(records.size());
        persist(records);
    }

    public File storageFile() {
        return storageFile;
    }

    private Map<String, ChordProgression> loadRecords() {
        try {
            Map<String, ChordProgression> result = BinaryStoreSupport.readWithBackup(storageFile, this::readFile);
            return result == null ? new LinkedHashMap<>() : result;
        } catch (IOException | RuntimeException exception) {
            throw new LocalStoreException("Unable to read local chord progressions.", exception);
        }
    }

    private Map<String, ChordProgression> readFile(DataInputStream input) throws IOException {
        if (input.readInt() != MAGIC) {
            throw new IOException("Invalid progression-store header.");
        }
        int version = input.readInt();
        if (version != VERSION) {
            throw new IOException("Unsupported progression-store version: " + version);
        }
        int count = input.readInt();
        if (count < 0 || count > MAX_RECORDS) {
            throw new IOException("Invalid progression record count: " + count);
        }
        Map<String, ChordProgression> records = new LinkedHashMap<>();
        for (int i = 0; i < count; i++) {
            ChordProgression progression = readProgression(input);
            if (records.put(progression.id, progression) != null) {
                throw new IOException("Duplicate progression id: " + progression.id);
            }
        }
        return records;
    }

    private ChordProgression readProgression(DataInputStream input) throws IOException {
        String id = BinaryStoreSupport.readString(input);
        String name = BinaryStoreSupport.readString(input);
        String key = BinaryStoreSupport.readString(input);
        TimeSignature signature = new TimeSignature(input.readInt(), input.readInt());
        int bpm = input.readInt();
        boolean loop = input.readBoolean();
        long createdAt = input.readLong();
        long updatedAt = input.readLong();
        String notes = BinaryStoreSupport.readString(input);
        int stepCount = input.readInt();
        if (stepCount < 0 || stepCount > 512) {
            throw new IOException("Invalid progression step count: " + stepCount);
        }
        List<ProgressionStep> steps = new ArrayList<>(stepCount);
        for (int i = 0; i < stepCount; i++) {
            steps.add(new ProgressionStep(
                    BinaryStoreSupport.readString(input),
                    BinaryStoreSupport.readString(input),
                    input.readDouble(),
                    BinaryStoreSupport.readString(input),
                    input.readInt()
            ));
        }
        return new ChordProgression(id, name, key, signature, bpm, loop, steps, createdAt, updatedAt, notes);
    }

    private void persist(Map<String, ChordProgression> records) {
        ensureCapacity(records.size());
        try {
            BinaryStoreSupport.writeAtomically(storageFile, output -> writeFile(output, records));
        } catch (IOException | RuntimeException exception) {
            throw new LocalStoreException("Unable to save local chord progressions.", exception);
        }
    }

    private void writeFile(DataOutputStream output, Map<String, ChordProgression> records) throws IOException {
        output.writeInt(MAGIC);
        output.writeInt(VERSION);
        output.writeInt(records.size());
        for (ChordProgression progression : records.values()) {
            BinaryStoreSupport.writeString(output, progression.id);
            BinaryStoreSupport.writeString(output, progression.name);
            BinaryStoreSupport.writeString(output, progression.keySignature);
            output.writeInt(progression.timeSignature.numerator);
            output.writeInt(progression.timeSignature.denominator);
            output.writeInt(progression.bpm);
            output.writeBoolean(progression.loop);
            output.writeLong(progression.createdAtEpochMillis);
            output.writeLong(progression.updatedAtEpochMillis);
            BinaryStoreSupport.writeString(output, progression.notes);
            output.writeInt(progression.steps.size());
            for (ProgressionStep step : progression.steps) {
                BinaryStoreSupport.writeString(output, step.chordSymbol);
                BinaryStoreSupport.writeString(output, step.voicingId);
                output.writeDouble(step.beats);
                BinaryStoreSupport.writeString(output, step.strumPattern);
                output.writeInt(step.order);
            }
        }
    }

    private long safeNow() {
        return Math.max(0L, wallClockMillis.currentTimeMillis());
    }

    private static void ensureCapacity(int count) {
        if (count > MAX_RECORDS) {
            throw new IllegalStateException("Local progression limit reached.");
        }
    }
}
