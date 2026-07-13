package com.k2.music;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

/** Atomic local storage for user practice preferences. */
public final class PracticePreferencesStore {
    private static final int MAGIC = 0x4B325046; // K2PF
    private static final int VERSION = 1;

    private final File storageFile;

    public PracticePreferencesStore(File storageFile) {
        this.storageFile = Objects.requireNonNull(storageFile, "storageFile").getAbsoluteFile();
    }

    public static File defaultFile(File appFilesDirectory) {
        Objects.requireNonNull(appFilesDirectory, "appFilesDirectory");
        return new File(appFilesDirectory, "practice-preferences-v1.bin");
    }

    public synchronized PracticePreferences load() {
        try {
            PracticePreferences result = BinaryStoreSupport.readWithBackup(storageFile, this::readFile);
            return result == null ? PracticePreferences.defaults() : result;
        } catch (IOException | RuntimeException exception) {
            throw new LocalStoreException("Unable to read local practice preferences.", exception);
        }
    }

    public synchronized PracticePreferences save(PracticePreferences preferences) {
        Objects.requireNonNull(preferences, "preferences");
        try {
            BinaryStoreSupport.writeAtomically(storageFile, output -> writeFile(output, preferences));
            return preferences;
        } catch (IOException | RuntimeException exception) {
            throw new LocalStoreException("Unable to save local practice preferences.", exception);
        }
    }

    public synchronized PracticePreferences reset() {
        return save(PracticePreferences.defaults());
    }

    public File storageFile() {
        return storageFile;
    }

    private PracticePreferences readFile(DataInputStream input) throws IOException {
        if (input.readInt() != MAGIC) {
            throw new IOException("Invalid practice-preferences header.");
        }
        int version = input.readInt();
        if (version != VERSION) {
            throw new IOException("Unsupported practice-preferences version: " + version);
        }
        PracticePreferences.Proficiency proficiency;
        PracticePreferences.PlaybackMode playbackMode;
        try {
            proficiency = PracticePreferences.Proficiency.valueOf(BinaryStoreSupport.readString(input));
            boolean allowBarre = input.readBoolean();
            int maxFret = input.readInt();
            int defaultBpm = input.readInt();
            TimeSignature signature = new TimeSignature(input.readInt(), input.readInt());
            playbackMode = PracticePreferences.PlaybackMode.valueOf(BinaryStoreSupport.readString(input));
            boolean accent = input.readBoolean();
            int familiarCount = input.readInt();
            if (familiarCount < 0 || familiarCount > 10_000) {
                throw new IOException("Invalid familiarity-record count: " + familiarCount);
            }
            Set<String> familiar = new LinkedHashSet<>();
            for (int i = 0; i < familiarCount; i++) {
                familiar.add(BinaryStoreSupport.readString(input));
            }
            return new PracticePreferences(
                    proficiency,
                    allowBarre,
                    maxFret,
                    defaultBpm,
                    signature,
                    playbackMode,
                    accent,
                    familiar
            );
        } catch (IllegalArgumentException exception) {
            throw new IOException("Invalid value in practice preferences.", exception);
        }
    }

    private void writeFile(DataOutputStream output, PracticePreferences preferences) throws IOException {
        output.writeInt(MAGIC);
        output.writeInt(VERSION);
        BinaryStoreSupport.writeString(output, preferences.proficiency.name());
        output.writeBoolean(preferences.allowBarre);
        output.writeInt(preferences.maxFret);
        output.writeInt(preferences.defaultBpm);
        output.writeInt(preferences.defaultTimeSignature.numerator);
        output.writeInt(preferences.defaultTimeSignature.denominator);
        BinaryStoreSupport.writeString(output, preferences.defaultPlaybackMode.name());
        output.writeBoolean(preferences.accentFirstBeat);
        output.writeInt(preferences.familiarVoicingIds.size());
        for (String id : preferences.familiarVoicingIds) {
            BinaryStoreSupport.writeString(output, id);
        }
    }
}
