package com.k2.music;

/** Sanitized provider response; it never contains request headers or an API key. */
public final class AiResponse {
    public final String content;
    public final int statusCode;
    public final String requestId;

    public AiResponse(String content, int statusCode, String requestId) {
        this.content = content == null ? "" : content;
        this.statusCode = statusCode;
        this.requestId = requestId == null ? "" : requestId;
    }
}
