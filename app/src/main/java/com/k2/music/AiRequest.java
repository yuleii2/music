package com.k2.music;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Provider-independent request DTO. */
public final class AiRequest {
    public final String taskType;
    public final List<AiMessage> messages;
    public final String model;
    public final double temperature;
    public final boolean requireJson;

    public AiRequest(String taskType, List<AiMessage> messages, String model, double temperature, boolean requireJson) {
        if (taskType == null || taskType.trim().isEmpty()) throw new IllegalArgumentException("taskType is required");
        if (messages == null || messages.isEmpty()) throw new IllegalArgumentException("messages are required");
        if (temperature < 0d || temperature > 2d) throw new IllegalArgumentException("temperature must be 0..2");
        this.taskType = taskType.trim();
        this.messages = Collections.unmodifiableList(new ArrayList<>(messages));
        this.model = model == null ? "" : model.trim();
        this.temperature = temperature;
        this.requireJson = requireJson;
    }

    public AiRequest withMessages(List<AiMessage> replacements) {
        return new AiRequest(taskType, replacements, model, temperature, requireJson);
    }
}
