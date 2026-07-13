package com.k2.music;

/** One OpenAI-compatible chat message. */
public final class AiMessage {
    public static final String SYSTEM = "system";
    public static final String USER = "user";
    public static final String ASSISTANT = "assistant";

    public final String role;
    public final String content;

    public AiMessage(String role, String content) {
        if (!SYSTEM.equals(role) && !USER.equals(role) && !ASSISTANT.equals(role)) {
            throw new IllegalArgumentException("Unsupported AI message role");
        }
        if (content == null || content.trim().isEmpty()) {
            throw new IllegalArgumentException("AI message content cannot be empty");
        }
        if (content.length() > 20_000) {
            throw new IllegalArgumentException("AI message is too long");
        }
        this.role = role;
        this.content = content;
    }
}
