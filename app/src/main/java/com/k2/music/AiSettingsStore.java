package com.k2.music;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;

import java.nio.charset.StandardCharsets;
import java.security.KeyStore;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/** Stores ordinary settings in preferences and the API key as Keystore AES-GCM ciphertext. */
public final class AiSettingsStore {
    private static final String PREFS = "ai_settings";
    private static final String KEY_ALIAS = "com.k2.music.ai_api_key";
    private static final String SECRET_BLOB = "api_key_ciphertext_v1";
    private static final String ENABLED = "enabled";
    private static final String SERVICE = "service_name";
    private static final String BASE_URL = "base_url";
    private static final String MODEL = "model";
    private static final String TEMPERATURE = "temperature";
    private static final String TIMEOUT = "timeout";
    private final SharedPreferences preferences;

    public AiSettingsStore(Context context) {
        preferences = context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public synchronized AiSettings load() {
        return new AiSettings(
                preferences.getBoolean(ENABLED, false),
                preferences.getString(SERVICE, "OpenAI Compatible"),
                preferences.getString(BASE_URL, ""),
                preferences.getString(MODEL, ""),
                Double.longBitsToDouble(preferences.getLong(TEMPERATURE, Double.doubleToLongBits(0.4d))),
                preferences.getInt(TIMEOUT, 30)
        );
    }

    /** A null key preserves the current secret; a non-empty key replaces it. */
    public synchronized void save(AiSettings settings, String apiKey) {
        if (settings == null) throw new IllegalArgumentException("settings are required");
        SharedPreferences.Editor editor = preferences.edit()
                .putBoolean(ENABLED, settings.enabled)
                .putString(SERVICE, settings.serviceName)
                .putString(BASE_URL, settings.baseUrl)
                .putString(MODEL, settings.model)
                .putLong(TEMPERATURE, Double.doubleToLongBits(settings.temperature))
                .putInt(TIMEOUT, settings.timeoutSeconds);
        if (apiKey != null && !apiKey.trim().isEmpty()) {
            editor.putString(SECRET_BLOB, encrypt(apiKey.trim()));
        }
        editor.apply();
    }

    public synchronized boolean hasApiKey() {
        return !getApiKey().isEmpty();
    }

    /** Returns an in-memory secret only to the provider; callers must never log it. */
    public synchronized String getApiKey() {
        String blob = preferences.getString(SECRET_BLOB, "");
        if (blob == null || blob.isEmpty()) return "";
        try {
            return decrypt(blob);
        } catch (RuntimeException exception) {
            return "";
        }
    }

    public synchronized void clear() {
        preferences.edit().clear().apply();
        try {
            KeyStore keyStore = KeyStore.getInstance("AndroidKeyStore");
            keyStore.load(null);
            if (keyStore.containsAlias(KEY_ALIAS)) keyStore.deleteEntry(KEY_ALIAS);
        } catch (Exception ignored) {
            // Preferences are already cleared, so the secret is no longer addressable.
        }
    }

    private String encrypt(String plaintext) {
        try {
            SecretKey key = getOrCreateKey();
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key);
            byte[] encrypted = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            return Base64.encodeToString(cipher.getIV(), Base64.NO_WRAP)
                    + "." + Base64.encodeToString(encrypted, Base64.NO_WRAP);
        } catch (Exception exception) {
            throw new IllegalStateException("无法安全保存 API Key", exception);
        }
    }

    private String decrypt(String blob) {
        try {
            String[] parts = blob.split("\\.", 2);
            if (parts.length != 2) return "";
            KeyStore keyStore = KeyStore.getInstance("AndroidKeyStore");
            keyStore.load(null);
            SecretKey key = (SecretKey) keyStore.getKey(KEY_ALIAS, null);
            if (key == null) return "";
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(128, Base64.decode(parts[0], Base64.NO_WRAP)));
            byte[] plaintext = cipher.doFinal(Base64.decode(parts[1], Base64.NO_WRAP));
            return new String(plaintext, StandardCharsets.UTF_8);
        } catch (Exception exception) {
            return "";
        }
    }

    private SecretKey getOrCreateKey() throws Exception {
        KeyStore keyStore = KeyStore.getInstance("AndroidKeyStore");
        keyStore.load(null);
        if (keyStore.containsAlias(KEY_ALIAS)) {
            return (SecretKey) keyStore.getKey(KEY_ALIAS, null);
        }
        KeyGenerator generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
        generator.init(new KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT
        ).setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build());
        return generator.generateKey();
    }
}
