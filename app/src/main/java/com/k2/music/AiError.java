package com.k2.music;

/** User-safe error classification. Sensitive request data is deliberately absent. */
public final class AiError {
    public enum Type {
        DISABLED,
        NOT_CONFIGURED,
        INVALID_URL,
        UNAUTHORIZED,
        FORBIDDEN,
        RATE_LIMITED,
        SERVER,
        TIMEOUT,
        OFFLINE,
        INVALID_RESPONSE,
        CANCELLED,
        UNKNOWN
    }

    public final Type type;
    public final String message;
    public final int statusCode;

    public AiError(Type type, String message, int statusCode) {
        this.type = type == null ? Type.UNKNOWN : type;
        this.message = message == null ? "AI 请求失败" : message;
        this.statusCode = statusCode;
    }

    public static AiError forHttp(int status) {
        if (status == 401) return new AiError(Type.UNAUTHORIZED, "认证失败，请检查 API Key", status);
        if (status == 403) return new AiError(Type.FORBIDDEN, "服务拒绝访问，请检查模型权限", status);
        if (status == 429) return new AiError(Type.RATE_LIMITED, "请求过于频繁或额度不足", status);
        if (status >= 500) return new AiError(Type.SERVER, "模型服务暂时不可用", status);
        return new AiError(Type.UNKNOWN, "模型服务返回错误（" + status + "）", status);
    }
}
