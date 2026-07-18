package com.k2.music;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Versioned atomic storage for the complete last-practice configuration. */
public final class LastPracticeConfigStore {
    private static final int MAGIC = 0x4B324C43; // K2LC
    public static final int SCHEMA_VERSION = 1;

    private final File storageFile;

    public LastPracticeConfigStore(File storageFile) {
        this.storageFile = Objects.requireNonNull(storageFile, "storageFile").getAbsoluteFile();
    }

    public static File defaultFile(File appFilesDirectory) {
        Objects.requireNonNull(appFilesDirectory, "appFilesDirectory");
        return new File(appFilesDirectory, "last-practice-config-v1.bin");
    }

    public synchronized LastPracticeConfig load() {
        try {
            return BinaryStoreSupport.readWithBackup(storageFile, this::readFile);
        } catch (IOException | RuntimeException exception) {
            throw new LocalStoreException("Unable to read the last practice configuration.", exception);
        }
    }

    public synchronized LastPracticeConfig save(LastPracticeConfig config) {
        Objects.requireNonNull(config, "config");
        try {
            BinaryStoreSupport.writeAtomically(storageFile, output -> writeFile(output, config));
            return config;
        } catch (IOException | RuntimeException exception) {
            throw new LocalStoreException("Unable to save the last practice configuration.", exception);
        }
    }

    public File storageFile() {
        return storageFile;
    }

    private LastPracticeConfig readFile(DataInputStream input) throws IOException {
        if (input.readInt() != MAGIC) throw new IOException("Invalid last-practice header.");
        int version = input.readInt();
        if (version != SCHEMA_VERSION) {
            throw new IOException("Unsupported last-practice version: " + version);
        }
        try {
            PracticeSession.Type mode = PracticeSession.Type.valueOf(BinaryStoreSupport.readString(input));
            int count = input.readInt();
            if (count < 2 || count > 256) throw new IOException("Invalid last-practice chord count: " + count);
            List<String> symbols = new ArrayList<>(count);
            for (int index = 0; index < count; index++) symbols.add(BinaryStoreSupport.readString(input));
            int duration = input.readInt();
            int bpm = input.readInt();
            String signature = BinaryStoreSupport.readString(input);
            PracticeSession.SwitchMode switchMode =
                    PracticeSession.SwitchMode.valueOf(BinaryStoreSupport.readString(input));
            boolean accent = input.readBoolean();
            boolean allowBarre = input.readBoolean();
            int maxFret = input.readInt();
            String progressionId = BinaryStoreSupport.readString(input);
            boolean progressionRhythm = input.readBoolean();
            return new LastPracticeConfig(mode, symbols, duration, bpm, signature, switchMode,
                    accent, allowBarre, maxFret, progressionId, progressionRhythm);
        } catch (IllegalArgumentException exception) {
            throw new IOException("Invalid last-practice configuration.", exception);
        }
    }

    private void writeFile(DataOutputStream output, LastPracticeConfig config) throws IOException {
        output.writeInt(MAGIC);
        output.writeInt(SCHEMA_VERSION);
        BinaryStoreSupport.writeString(output, config.mode.name());
        output.writeInt(config.chordSymbols.size());
        for (String symbol : config.chordSymbols) BinaryStoreSupport.writeString(output, symbol);
        output.writeInt(config.durationSeconds);
        output.writeInt(config.bpm);
        BinaryStoreSupport.writeString(output, config.timeSignature);
        BinaryStoreSupport.writeString(output, config.switchMode.name());
        output.writeBoolean(config.accentFirstBeat);
        output.writeBoolean(config.allowBarre);
        output.writeInt(config.maxFret);
        BinaryStoreSupport.writeString(output, config.sourceProgressionId);
        output.writeBoolean(config.useProgressionRhythm);
    }
}
