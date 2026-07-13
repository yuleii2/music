package com.k2.music;

import android.content.Context;
import android.content.SharedPreferences;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Local CRUD store kept separate from the read-only built-in voicing assets. */
public final class CustomVoicingStore {
    private static final String PREFS = "custom_voicings";
    private static final String KEY_DATA = "entries_v1";
    private final SharedPreferences preferences;

    public CustomVoicingStore(Context context) {
        this(context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE));
    }

    CustomVoicingStore(SharedPreferences preferences) {
        this.preferences = preferences;
    }

    public synchronized List<CustomVoicing> all() {
        return Collections.unmodifiableList(read());
    }

    public synchronized List<CustomVoicing> forChord(String symbol) {
        List<CustomVoicing> result = new ArrayList<>();
        if (symbol == null) return result;
        for (CustomVoicing voicing : read()) {
            if (voicing.chordSymbol.equalsIgnoreCase(symbol.trim())) {
                result.add(voicing);
            }
        }
        return result;
    }

    public synchronized CustomVoicing save(CustomVoicing entry) {
        List<CustomVoicing> entries = read();
        removeById(entries, entry.id);
        entries.add(entry);
        write(entries);
        return entry;
    }

    public synchronized boolean delete(String id) {
        List<CustomVoicing> entries = read();
        boolean removed = removeById(entries, id);
        if (removed) write(entries);
        return removed;
    }

    public synchronized void replaceAll(List<CustomVoicing> values) {
        List<CustomVoicing> normalized = new ArrayList<>();
        if (values != null) {
            for (CustomVoicing value : values) {
                if (value != null) removeById(normalized, value.id);
                if (value != null) normalized.add(value);
            }
        }
        write(normalized);
    }

    public List<Voicing> mergeWithBuiltIns(String chordSymbol, List<Voicing> builtIns) {
        List<Voicing> merged = new ArrayList<>(builtIns == null ? Collections.emptyList() : builtIns);
        for (CustomVoicing custom : forChord(chordSymbol)) {
            merged.add(custom.toVoicing());
        }
        return merged;
    }

    private List<CustomVoicing> read() {
        List<CustomVoicing> result = new ArrayList<>();
        String raw = preferences.getString(KEY_DATA, "[]");
        try {
            Object parsed = JsonSupport.parse(raw == null ? "[]" : raw);
            if (!(parsed instanceof List)) return result;
            for (Object item : (List<?>) parsed) {
                if (!(item instanceof Map)) continue;
                Map<?, ?> object = (Map<?, ?>) item;
                int[] frets = readInts(object.get("frets"));
                int[] fingers = readInts(object.get("fingers"));
                if (frets.length != 6) continue;
                if (fingers.length != 6) fingers = new int[6];
                result.add(new CustomVoicing(
                        text(object.get("id")),
                        text(object.get("chordSymbol")),
                        text(object.get("name")),
                        frets,
                        fingers,
                        integer(object.get("startFret"), 1),
                        text(object.get("note")),
                        longValue(object.get("createdAt"), System.currentTimeMillis())
                ));
            }
        } catch (IOException | IllegalArgumentException ignored) {
            // Corrupt local data is ignored safely; built-in JSON remains available.
        }
        return result;
    }

    private void write(List<CustomVoicing> entries) {
        List<Map<String, Object>> array = new ArrayList<>();
        for (CustomVoicing entry : entries) {
            Map<String, Object> object = new LinkedHashMap<>();
            object.put("id", entry.id);
            object.put("chordSymbol", entry.chordSymbol);
            object.put("name", entry.name);
            object.put("frets", intArray(entry.frets));
            object.put("fingers", intArray(entry.fingers));
            object.put("startFret", entry.startFret);
            object.put("note", entry.note);
            object.put("createdAt", entry.createdAt);
            array.add(object);
        }
        preferences.edit().putString(KEY_DATA, JsonSupport.stringify(array)).apply();
    }

    private static List<Integer> intArray(int[] values) {
        List<Integer> array = new ArrayList<>(values.length);
        for (int value : values) array.add(value);
        return array;
    }

    private static int[] readInts(Object raw) {
        if (!(raw instanceof List)) return new int[0];
        List<?> array = (List<?>) raw;
        int[] result = new int[array.size()];
        for (int i = 0; i < result.length; i++) result[i] = integer(array.get(i), 0);
        return result;
    }

    private static String text(Object value) {
        return value instanceof String ? (String) value : "";
    }

    private static int integer(Object value, int fallback) {
        return value instanceof Number ? ((Number) value).intValue() : fallback;
    }

    private static long longValue(Object value, long fallback) {
        return value instanceof Number ? ((Number) value).longValue() : fallback;
    }

    private static boolean removeById(List<CustomVoicing> entries, String id) {
        boolean removed = false;
        for (int index = entries.size() - 1; index >= 0; index--) {
            if (entries.get(index).id.equals(id)) {
                entries.remove(index);
                removed = true;
            }
        }
        return removed;
    }
}
