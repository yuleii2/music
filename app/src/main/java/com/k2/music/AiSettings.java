package com.k2.music;

/** Non-secret AI configuration. The API key is stored separately in Android Keystore. */
public final class AiSettings {
    public final boolean enabled;
    public final String serviceName;
    public final String baseUrl;
    public final String model;
    public final double temperature;
    public final int timeoutSeconds;

    public AiSettings(boolean enabled, String serviceName, String baseUrl, String model, double temperature, int timeoutSeconds) {
        this.enabled = enabled;
        this.serviceName = serviceName == null || serviceName.trim().isEmpty() ? "OpenAI Compatible" : serviceName.trim();
        this.baseUrl = baseUrl == null ? "" : baseUrl.trim();
        this.model = model == null ? "" : model.trim();
        this.temperature = Math.max(0d, Math.min(2d, temperature));
        this.timeoutSeconds = Math.max(5, Math.min(120, timeoutSeconds));
    }

    public static AiSettings defaults() {
        return new AiSettings(false, "OpenAI Compatible", "", "", 0.4d, 30);
    }
}
