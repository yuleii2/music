package com.k2.music;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/** Small package-private helpers shared by the versioned local stores. */
final class BinaryStoreSupport {
    private static final int MAX_STRING_BYTES = 1_048_576;

    interface OutputAction {
        void write(DataOutputStream output) throws IOException;
    }

    interface InputAction<T> {
        T read(DataInputStream input) throws IOException;
    }

    private BinaryStoreSupport() {
    }

    static void writeAtomically(File target, OutputAction action) throws IOException {
        File absoluteTarget = target.getAbsoluteFile();
        File parent = absoluteTarget.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs() && !parent.isDirectory()) {
            throw new IOException("Cannot create local store directory: " + parent);
        }

        File temporary = new File(absoluteTarget.getPath() + ".tmp");
        File backup = backupFor(absoluteTarget);
        if (temporary.exists() && !temporary.delete()) {
            throw new IOException("Cannot replace stale local-store temporary file.");
        }

        try (FileOutputStream fileOutput = new FileOutputStream(temporary);
             DataOutputStream output = new DataOutputStream(fileOutput)) {
            action.write(output);
            output.flush();
            fileOutput.getFD().sync();
        } catch (IOException | RuntimeException exception) {
            temporary.delete();
            throw exception;
        }

        if (backup.exists() && !backup.delete()) {
            temporary.delete();
            throw new IOException("Cannot rotate local-store backup.");
        }
        boolean movedOriginal = false;
        if (absoluteTarget.exists()) {
            movedOriginal = absoluteTarget.renameTo(backup);
            if (!movedOriginal) {
                temporary.delete();
                throw new IOException("Cannot rotate current local-store file.");
            }
        }
        if (!temporary.renameTo(absoluteTarget)) {
            if (movedOriginal) {
                backup.renameTo(absoluteTarget);
            }
            temporary.delete();
            throw new IOException("Cannot install new local-store file.");
        }
    }

    static <T> T readWithBackup(File target, InputAction<T> action) throws IOException {
        File absoluteTarget = target.getAbsoluteFile();
        IOException primaryFailure = null;
        if (absoluteTarget.isFile()) {
            try {
                return read(absoluteTarget, action);
            } catch (IOException exception) {
                primaryFailure = exception;
            }
        }
        File backup = backupFor(absoluteTarget);
        if (backup.isFile()) {
            try {
                return read(backup, action);
            } catch (IOException backupFailure) {
                if (primaryFailure != null) {
                    backupFailure.addSuppressed(primaryFailure);
                }
                throw backupFailure;
            }
        }
        if (primaryFailure != null) {
            throw primaryFailure;
        }
        return null;
    }

    static void writeString(DataOutputStream output, String value) throws IOException {
        byte[] bytes = (value == null ? "" : value).getBytes(StandardCharsets.UTF_8);
        if (bytes.length > MAX_STRING_BYTES) {
            throw new IOException("Local-store string exceeds the supported size.");
        }
        output.writeInt(bytes.length);
        output.write(bytes);
    }

    static String readString(DataInputStream input) throws IOException {
        int length = input.readInt();
        if (length < 0 || length > MAX_STRING_BYTES) {
            throw new IOException("Invalid string length in local store: " + length);
        }
        byte[] bytes = new byte[length];
        try {
            input.readFully(bytes);
        } catch (EOFException exception) {
            throw new IOException("Truncated local-store string.", exception);
        }
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private static <T> T read(File source, InputAction<T> action) throws IOException {
        try (DataInputStream input = new DataInputStream(new FileInputStream(source))) {
            T value;
            try {
                value = action.read(input);
            } catch (RuntimeException exception) {
                throw new IOException("Invalid value in local store.", exception);
            }
            if (input.read() != -1) {
                throw new IOException("Unexpected trailing data in local store.");
            }
            return value;
        }
    }

    private static File backupFor(File target) {
        return new File(target.getPath() + ".bak");
    }
}
