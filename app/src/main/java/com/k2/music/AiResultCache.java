package com.k2.music;

import android.content.Context;
import android.content.SharedPreferences;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/** Optional local cache keyed only by task/input/model/data version; API keys are never cached. */
public final class AiResultCache {
    private static final String PREFS = "ai_result_cache";
    private static final int MAX_ENTRY_LENGTH = 100_000;
    private final SharedPreferences preferences;

    public AiResultCache(Context context) {
        preferences = context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public String key(String taskType, String userInput, String model, String chordDataVersion) {
        String source = safe(taskType) + '\n' + safe(userInput) + '\n' + safe(model) + '\n' + safe(chordDataVersion);
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(source.getBytes(StandardCharsets.UTF_8));
            StringBuilder value = new StringBuilder();
            for (byte item : digest) value.append(String.format(java.util.Locale.US, "%02x", item & 0xff));
            return value.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }

    public String get(String key) {
        return preferences.getString(key, "");
    }

    public void put(String key, String json) {
        if (json == null || json.length() > MAX_ENTRY_LENGTH) return;
        preferences.edit().putString(key, json).apply();
    }

    public void clear() {
        preferences.edit().clear().apply();
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }
}
