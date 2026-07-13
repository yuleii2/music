package com.k2.music;

import android.content.Context;

import java.util.ArrayList;
import java.util.List;

/** Coordinates one user-initiated request, one JSON repair attempt, and explicit cancellation. */
public final class AiService {
    public interface StructuredParser<T> {
        T parse(String json) throws AiResultValidator.ValidationException;
    }

    public interface ResultCallback<T> {
        void onSuccess(T result, String rawExplanationJson);
        void onError(AiError error);
    }

    private final AiSettingsStore settingsStore;
    private Session<?> active;

    public AiService(Context context) {
        this.settingsStore = new AiSettingsStore(context);
    }

    public AiSettings settings() {
        return settingsStore.load();
    }

    public boolean isConfigured() {
        AiSettings settings = settingsStore.load();
        return settings.enabled && !settings.baseUrl.isEmpty() && !settings.model.isEmpty() && settingsStore.hasApiKey();
    }

    public synchronized boolean hasActiveRequest() {
        return active != null;
    }

    public <T> void executeStructured(AiRequest request, StructuredParser<T> parser, ResultCallback<T> callback) {
        AiSettings settings = settingsStore.load();
        String apiKey = settingsStore.getApiKey();
        if (!settings.enabled) {
            callback.onError(new AiError(AiError.Type.DISABLED, "AI 功能尚未启用", 0));
            return;
        }
        if (settings.baseUrl.isEmpty() || settings.model.isEmpty() || apiKey.isEmpty()) {
            callback.onError(new AiError(AiError.Type.NOT_CONFIGURED, "请先完成 AI 服务配置", 0));
            return;
        }
        String urlError = OpenAiCompatibleProvider.validateBaseUrl(settings.baseUrl);
        if (urlError != null) {
            callback.onError(new AiError(AiError.Type.INVALID_URL, urlError, 0));
            return;
        }
        synchronized (this) {
            if (active != null) {
                callback.onError(new AiError(AiError.Type.UNKNOWN, "已有 AI 请求正在进行，请稍候", 0));
                return;
            }
            OpenAiCompatibleProvider provider = new OpenAiCompatibleProvider(settings, apiKey);
            Session<T> session = new Session<>(provider, request, parser, callback);
            active = session;
            send(session, request, false);
        }
    }

    public void testConnection(ResultCallback<String> callback) {
        AiSettings settings = settingsStore.load();
        AiRequest request = new AiRequest(
                "connection_test",
                java.util.Arrays.asList(
                        new AiMessage(AiMessage.SYSTEM, "Return exactly {\"ok\":true} as JSON."),
                        new AiMessage(AiMessage.USER, "Test connection")
                ),
                settings.model,
                0d,
                true
        );
        executeStructured(request, json -> {
            String compact = json == null ? "" : json.replace(" ", "");
            if (!compact.contains("\"ok\":true")) {
                throw new AiResultValidator.ValidationException("连接响应无效");
            }
            return "连接成功";
        }, callback);
    }

    public synchronized void cancelActive() {
        Session<?> session = active;
        active = null;
        if (session != null) {
            session.cancelled = true;
            if (session.handle != null) session.handle.cancel();
            session.provider.close();
        }
    }

    private <T> void send(Session<T> session, AiRequest request, boolean repairAttempt) {
        if (session.cancelled) {
            return;
        }
        session.handle = session.provider.send(request, new AiProvider.Callback() {
            @Override
            public void onSuccess(AiResponse response) {
                if (session.cancelled) return;
                try {
                    T parsed = session.parser.parse(response.content);
                    finishSuccess(session, parsed, response.content);
                } catch (AiResultValidator.ValidationException exception) {
                    if (!repairAttempt && !session.cancelled) {
                        send(session, AiPromptFactory.repairJson(session.originalRequest, response.content), true);
                    } else {
                        finishError(session, new AiError(
                                AiError.Type.INVALID_RESPONSE,
                                "AI 返回内容未通过本地校验：" + safeValidationMessage(exception.getMessage()),
                                response.statusCode
                        ));
                    }
                } catch (RuntimeException exception) {
                    finishError(session, new AiError(AiError.Type.INVALID_RESPONSE, "AI 返回内容无法处理", response.statusCode));
                }
            }

            @Override
            public void onError(AiError error) {
                if (!session.cancelled) finishError(session, error);
            }
        });
    }

    private <T> void finishSuccess(Session<T> session, T result, String raw) {
        synchronized (this) {
            if (active != session || session.cancelled) return;
            active = null;
        }
        session.provider.close();
        session.callback.onSuccess(result, raw);
    }

    private <T> void finishError(Session<T> session, AiError error) {
        synchronized (this) {
            if (active != session || session.cancelled) return;
            active = null;
        }
        session.provider.close();
        session.callback.onError(error);
    }

    private static String safeValidationMessage(String message) {
        if (message == null || message.isEmpty()) return "格式无效";
        return message.length() <= 160 ? message : message.substring(0, 160);
    }

    private static final class Session<T> {
        final OpenAiCompatibleProvider provider;
        final AiRequest originalRequest;
        final StructuredParser<T> parser;
        final ResultCallback<T> callback;
        volatile AiProvider.RequestHandle handle;
        volatile boolean cancelled;

        Session(OpenAiCompatibleProvider provider, AiRequest originalRequest, StructuredParser<T> parser, ResultCallback<T> callback) {
            this.provider = provider;
            this.originalRequest = originalRequest;
            this.parser = parser;
            this.callback = callback;
        }
    }
}
