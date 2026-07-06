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

    public UserChordStore(Context context) {
        this.preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public List<String> favorites() {
        return readList(KEY_FAVORITES);
    }

    public List<String> history() {
        return readList(KEY_HISTORY);
    }

    public boolean isFavorite(String symbol) {
        return favorites().contains(symbol);
    }

    public boolean toggleFavorite(String symbol) {
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
        List<String> history = history();
        history.remove(symbol);
        history.add(0, symbol);
        if (history.size() > MAX_HISTORY) {
            history = new ArrayList<>(history.subList(0, MAX_HISTORY));
        }
        writeList(KEY_HISTORY, history);
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
