package com.k2.music;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public final class UserChordStore {
    private static final String PREFS_NAME = "user_chord_store";
    private static final String KEY_FAVORITES = "favorites";
    private static final String KEY_HISTORY = "history";
    private static final int MAX_HISTORY = 12;
    private static final String SEPARATOR = "|";

    private final SharedPreferences preferences;
    private final ChordRepository repository;

    public UserChordStore(Context context) {
        this(context, null);
    }

    public UserChordStore(Context context, ChordRepository repository) {
        this.preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        this.repository = repository;
        if (repository != null) {
            migrateStoredSymbols();
        }
    }

    public List<String> favorites() {
        return readList(KEY_FAVORITES);
    }

    public List<String> history() {
        return readList(KEY_HISTORY);
    }

    public boolean isFavorite(String symbol) {
        return favorites().contains(normalizeSymbol(symbol));
    }

    public boolean toggleFavorite(String symbol) {
        symbol = normalizeSymbol(symbol);
        if (symbol.isEmpty()) {
            return false;
        }
        List<String> favorites = favorites();
        boolean nowFavorite;
        if (favorites.contains(symbol)) {
            favorites.remove(symbol);
            nowFavorite = false;
        } else {
            favorites.add(0, symbol);
            nowFavorite = true;
        }
        writeList(KEY_FAVORITES, favorites);
        return nowFavorite;
    }

    public void addHistory(String symbol) {
        symbol = normalizeSymbol(symbol);
        if (symbol.isEmpty()) {
            return;
        }
        List<String> history = history();
        history.remove(symbol);
        history.add(0, symbol);
        if (history.size() > MAX_HISTORY) {
            history = new ArrayList<>(history.subList(0, MAX_HISTORY));
        }
        writeList(KEY_HISTORY, history);
    }

    /** Removes one normalized history entry without changing the persisted format. */
    public boolean removeHistory(String symbol) {
        symbol = normalizeSymbol(symbol);
        if (symbol.isEmpty()) {
            return false;
        }
        List<String> history = history();
        boolean removed = history.remove(symbol);
        if (removed) {
            writeList(KEY_HISTORY, history);
        }
        return removed;
    }

    private List<String> readList(String key) {
        String value = preferences.getString(key, "");
        if (value == null || value.trim().isEmpty()) {
            return new ArrayList<>();
        }
        List<String> result = new ArrayList<>(Arrays.asList(value.split("\\|")));
        result.removeAll(Collections.singleton(""));
        return result;
    }

    private void writeList(String key, List<String> values) {
        preferences.edit().putString(key, join(values)).apply();
    }

    public void migrateStoredSymbols() {
        if (repository == null) {
            return;
        }
        migrateList(KEY_FAVORITES, false);
        migrateList(KEY_HISTORY, true);
    }

    private void migrateList(String key, boolean limitHistory) {
        List<String> source = readList(key);
        List<String> migrated = ChordSymbolMigration.normalize(
                source, repository, limitHistory ? MAX_HISTORY : 0
        );
        if (!source.equals(migrated)) {
            writeList(key, migrated);
        }
    }

    private String normalizeSymbol(String symbol) {
        String value = symbol == null ? "" : symbol.trim();
        if (value.isEmpty() || repository == null) {
            return value;
        }
        ChordRepository.LookupResult result = repository.find(value);
        return result.recognized ? result.chord.symbol : value;
    }

    private String join(List<String> values) {
        StringBuilder builder = new StringBuilder();
        for (String value : values) {
            if (builder.length() > 0) {
                builder.append(SEPARATOR);
            }
            builder.append(value);
        }
        return builder.toString();
    }
}
