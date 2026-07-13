package com.k2.music;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public final class UserChordStore {
    private static final String PREFS_NAME = "user_chord_store";
    private static final String KEY_FAVORITES = "favorites";
    private static final String KEY_HISTORY = "history";
    private static final String KEY_HISTORY_TIMESTAMPS = "history_timestamps_v1";
    private static final int MAX_HISTORY = 12;
    private static final String SEPARATOR = "|";

    private final SharedPreferences preferences;
    private final ChordRepository repository;

    public UserChordStore(Context context) {
        this(context, null);
    }

    public UserChordStore(Context context, ChordRepository repository) {
        this(context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE), repository);
    }

    UserChordStore(SharedPreferences preferences, ChordRepository repository) {
        this.preferences = preferences;
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

    /** Timestamped history used by complete backup merge. Legacy ordered history is migrated lazily. */
    public synchronized List<HistoryEntry> historyEntries() {
        List<String> symbols = readList(KEY_HISTORY);
        List<Long> timestamps = readTimestamps();
        if (timestamps.size() != symbols.size()) {
            long anchor = System.currentTimeMillis();
            timestamps = new ArrayList<>();
            for (int index = 0; index < symbols.size(); index++) timestamps.add(anchor - index);
            writeTimestamps(timestamps);
        }
        List<HistoryEntry> result = new ArrayList<>();
        for (int index = 0; index < symbols.size(); index++) {
            result.add(new HistoryEntry(symbols.get(index), timestamps.get(index)));
        }
        return result;
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
        List<HistoryEntry> history = historyEntries();
        for (int index = history.size() - 1; index >= 0; index--) {
            if (history.get(index).symbol.equals(symbol)) history.remove(index);
        }
        history.add(0, new HistoryEntry(symbol, System.currentTimeMillis()));
        replaceHistoryEntries(history);
    }

    public synchronized void replaceFavorites(List<String> symbols) {
        writeList(KEY_FAVORITES, normalizeDistinct(symbols, 0));
    }

    public synchronized void replaceHistory(List<String> symbols) {
        Map<String, Long> existing = new LinkedHashMap<>();
        for (HistoryEntry entry : historyEntries()) existing.put(entry.symbol, entry.timestampEpochMillis);
        List<String> normalized = normalizeDistinct(symbols, MAX_HISTORY);
        long anchor = System.currentTimeMillis();
        List<HistoryEntry> values = new ArrayList<>();
        for (int index = 0; index < normalized.size(); index++) {
            String symbol = normalized.get(index);
            Long timestamp = existing.get(symbol);
            values.add(new HistoryEntry(symbol, timestamp == null ? anchor - index : timestamp));
        }
        replaceHistoryEntries(values);
    }

    public synchronized void replaceHistoryEntries(List<HistoryEntry> entries) {
        List<String> symbols = new ArrayList<>();
        List<Long> timestamps = new ArrayList<>();
        if (entries != null) {
            for (HistoryEntry entry : entries) {
                if (entry == null) continue;
                String normalized = normalizeSymbol(entry.symbol);
                if (normalized.isEmpty() || symbols.contains(normalized)) continue;
                symbols.add(normalized);
                timestamps.add(Math.max(0L, entry.timestampEpochMillis));
                if (symbols.size() >= MAX_HISTORY) break;
            }
        }
        preferences.edit()
                .putString(KEY_HISTORY, join(symbols))
                .putString(KEY_HISTORY_TIMESTAMPS, joinLongs(timestamps))
                .apply();
    }

    /** Removes one normalized history entry without changing the persisted format. */
    public boolean removeHistory(String symbol) {
        symbol = normalizeSymbol(symbol);
        if (symbol.isEmpty()) {
            return false;
        }
        List<HistoryEntry> history = historyEntries();
        boolean removed = false;
        for (int index = history.size() - 1; index >= 0; index--) {
            if (history.get(index).symbol.equals(symbol)) {
                history.remove(index);
                removed = true;
            }
        }
        if (removed) {
            replaceHistoryEntries(history);
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
            if (KEY_HISTORY.equals(key)) replaceHistory(migrated);
            else writeList(key, migrated);
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

    private List<String> normalizeDistinct(List<String> values, int limit) {
        List<String> result = new ArrayList<>();
        if (values != null) {
            for (String value : values) {
                String normalized = normalizeSymbol(value);
                if (!normalized.isEmpty() && !result.contains(normalized)) result.add(normalized);
                if (limit > 0 && result.size() >= limit) break;
            }
        }
        return result;
    }

    private List<Long> readTimestamps() {
        String value = preferences.getString(KEY_HISTORY_TIMESTAMPS, "");
        List<Long> result = new ArrayList<>();
        if (value == null || value.trim().isEmpty()) return result;
        for (String item : value.split("\\|")) {
            try {
                result.add(Long.parseLong(item));
            } catch (NumberFormatException ignored) {
                return new ArrayList<>();
            }
        }
        return result;
    }

    private void writeTimestamps(List<Long> values) {
        preferences.edit().putString(KEY_HISTORY_TIMESTAMPS, joinLongs(values)).apply();
    }

    private String joinLongs(List<Long> values) {
        StringBuilder builder = new StringBuilder();
        for (Long value : values) {
            if (builder.length() > 0) builder.append(SEPARATOR);
            builder.append(value == null ? 0L : value);
        }
        return builder.toString();
    }

    public static final class HistoryEntry {
        public final String symbol;
        public final long timestampEpochMillis;

        public HistoryEntry(String symbol, long timestampEpochMillis) {
            this.symbol = Objects.requireNonNull(symbol, "symbol");
            this.timestampEpochMillis = timestampEpochMillis;
        }
    }
}
