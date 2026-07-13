package com.k2.music;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

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
            JSONArray array = new JSONArray(raw == null ? "[]" : raw);
            for (int i = 0; i < array.length(); i++) {
                JSONObject object = array.optJSONObject(i);
                if (object == null) continue;
                int[] frets = readInts(object.optJSONArray("frets"));
                int[] fingers = readInts(object.optJSONArray("fingers"));
                if (frets.length != 6) continue;
                if (fingers.length != 6) fingers = new int[6];
                result.add(new CustomVoicing(
                        object.optString("id"),
                        object.optString("chordSymbol"),
                        object.optString("name"),
                        frets,
                        fingers,
                        object.optInt("startFret", 1),
                        object.optString("note"),
                        object.optLong("createdAt", System.currentTimeMillis())
                ));
            }
        } catch (JSONException | IllegalArgumentException ignored) {
            // Corrupt local data is ignored safely; built-in JSON remains available.
        }
        return result;
    }

    private void write(List<CustomVoicing> entries) {
        JSONArray array = new JSONArray();
        for (CustomVoicing entry : entries) {
            try {
                JSONObject object = new JSONObject();
                object.put("id", entry.id);
                object.put("chordSymbol", entry.chordSymbol);
                object.put("name", entry.name);
                object.put("frets", intArray(entry.frets));
                object.put("fingers", intArray(entry.fingers));
                object.put("startFret", entry.startFret);
                object.put("note", entry.note);
                object.put("createdAt", entry.createdAt);
                array.put(object);
            } catch (JSONException ignored) {
                // Values are primitive and should not fail; skip one bad entry defensively.
            }
        }
        preferences.edit().putString(KEY_DATA, array.toString()).apply();
    }

    private static JSONArray intArray(int[] values) {
        JSONArray array = new JSONArray();
        for (int value : values) array.put(value);
        return array;
    }

    private static int[] readInts(JSONArray array) {
        if (array == null) return new int[0];
        int[] result = new int[array.length()];
        for (int i = 0; i < result.length; i++) result[i] = array.optInt(i, 0);
        return result;
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
