package com.k2.music;

import android.os.Handler;
import android.os.Looper;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.StringReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.List;
import java.util.Map;

/** Minimal OpenAI-compatible Chat Completions provider with cancellation and safe errors. */
public final class OpenAiCompatibleProvider implements AiProvider, AutoCloseable {
    private static final int MAX_RESPONSE_CHARS = 1_000_000;
    private final AiSettings settings;
    private final String apiKey;
    private final ExecutorService networkExecutor;
    private final Executor callbackExecutor;

    public OpenAiCompatibleProvider(AiSettings settings, String apiKey) {
        this(settings, apiKey, Executors.newCachedThreadPool(), mainThreadExecutor());
    }

    OpenAiCompatibleProvider(AiSettings settings, String apiKey, ExecutorService networkExecutor, Executor callbackExecutor) {
        this.settings = settings == null ? AiSettings.defaults() : settings;
        this.apiKey = apiKey == null ? "" : apiKey;
        this.networkExecutor = networkExecutor;
        this.callbackExecutor = callbackExecutor;
    }

    @Override
    public RequestHandle send(AiRequest request, Callback callback) {
        RequestTask task = new RequestTask(callback);
        if (!settings.enabled) {
            deliverError(task, new AiError(AiError.Type.DISABLED, "AI 功能尚未启用", 0));
            return task;
        }
        if (apiKey.isEmpty() || settings.model.isEmpty() || settings.baseUrl.isEmpty()) {
            deliverError(task, new AiError(AiError.Type.NOT_CONFIGURED, "请先完成 AI 服务配置", 0));
            return task;
        }
        String urlError = validateBaseUrl(settings.baseUrl);
        if (urlError != null) {
            deliverError(task, new AiError(AiError.Type.INVALID_URL, urlError, 0));
            return task;
        }
        task.future = networkExecutor.submit(() -> execute(task, request));
        return task;
    }

    private void execute(RequestTask task, AiRequest request) {
        HttpURLConnection connection = null;
        try {
            URL endpoint = new URL(chatCompletionsUrl(settings.baseUrl));
            connection = (HttpURLConnection) endpoint.openConnection();
            task.connection = connection;
            int timeout = settings.timeoutSeconds * 1000;
            connection.setConnectTimeout(timeout);
            connection.setReadTimeout(timeout);
            connection.setRequestMethod("POST");
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            connection.setRequestProperty("Accept", "application/json");
            connection.setRequestProperty("Authorization", "Bearer " + apiKey);
            connection.setRequestProperty("User-Agent", "K2-Music-Android/1.0");

            byte[] body = createBody(request).getBytes(StandardCharsets.UTF_8);
            connection.setFixedLengthStreamingMode(body.length);
            try (OutputStream output = connection.getOutputStream()) {
                output.write(body);
            }
            if (task.cancelled.get()) return;

            int status = connection.getResponseCode();
            String requestId = safeHeader(connection, "x-request-id");
            if (status < 200 || status >= 300) {
                drain(connection.getErrorStream());
                deliverError(task, AiError.forHttp(status));
                return;
            }
            String responseBody = readLimited(connection.getInputStream());
            String content = parseAssistantContent(responseBody);
            deliverSuccess(task, new AiResponse(content, status, requestId));
        } catch (SocketTimeoutException exception) {
            deliverError(task, new AiError(AiError.Type.TIMEOUT, "AI 请求超时，请稍后重试", 0));
        } catch (UnknownHostException exception) {
            deliverError(task, new AiError(AiError.Type.OFFLINE, "当前网络不可用或服务地址无法解析", 0));
        } catch (MalformedURLException exception) {
            deliverError(task, new AiError(AiError.Type.INVALID_URL, "Base URL 格式无效", 0));
        } catch (IOException exception) {
            if (!task.cancelled.get()) {
                deliverError(task, new AiError(AiError.Type.OFFLINE, "网络请求失败，请检查连接", 0));
            }
        } finally {
            task.connection = null;
            if (connection != null) connection.disconnect();
        }
    }

    private String createBody(AiRequest request) {
        StringBuilder json = new StringBuilder(512);
        json.append('{')
                .append("\"model\":").append(jsonString(request.model.isEmpty() ? settings.model : request.model))
                .append(",\"temperature\":").append(request.temperature)
                .append(",\"messages\":[");
        for (int i = 0; i < request.messages.size(); i++) {
            AiMessage message = request.messages.get(i);
            if (i > 0) json.append(',');
            json.append('{')
                    .append("\"role\":").append(jsonString(message.role))
                    .append(",\"content\":").append(jsonString(message.content))
                    .append('}');
        }
        json.append(']');
        if (request.requireJson) {
            json.append(",\"response_format\":{\"type\":\"json_object\"}");
        }
        return json.append('}').toString();
    }

    static String parseAssistantContent(String responseBody) throws IOException {
        Object parsed = SimpleJsonParser.parse(new StringReader(responseBody));
        if (!(parsed instanceof Map)) throw new IOException("Missing response object");
        Object choicesValue = ((Map<?, ?>) parsed).get("choices");
        if (!(choicesValue instanceof List) || ((List<?>) choicesValue).isEmpty()) {
            throw new IOException("Missing choices");
        }
        Object first = ((List<?>) choicesValue).get(0);
        if (!(first instanceof Map)) throw new IOException("Missing choice object");
        Object messageValue = ((Map<?, ?>) first).get("message");
        if (!(messageValue instanceof Map)) throw new IOException("Missing message");
        Object contentValue = ((Map<?, ?>) messageValue).get("content");
        String content = contentValue instanceof String ? ((String) contentValue).trim() : "";
        if (content.isEmpty()) throw new IOException("Missing content");
        return content;
    }

    public static String validateBaseUrl(String raw) {
        if (raw == null || raw.trim().isEmpty()) return "请输入 Base URL";
        try {
            URL url = new URL(raw.trim());
            String protocol = url.getProtocol();
            String host = url.getHost();
            if (host == null || host.isEmpty()) return "Base URL 缺少主机名";
            if (!"https".equalsIgnoreCase(protocol)) {
                return "Base URL 必须使用 HTTPS";
            }
            return null;
        } catch (MalformedURLException exception) {
            return "Base URL 格式无效";
        }
    }

    public static String chatCompletionsUrl(String raw) {
        String base = raw.trim();
        while (base.endsWith("/")) base = base.substring(0, base.length() - 1);
        return base.endsWith("/chat/completions") ? base : base + "/chat/completions";
    }

    @Override
    public void close() {
        networkExecutor.shutdownNow();
    }

    private void deliverSuccess(RequestTask task, AiResponse response) {
        if (task.cancelled.get() || !task.finished.compareAndSet(false, true)) return;
        callbackExecutor.execute(() -> {
            if (!task.cancelled.get()) task.callback.onSuccess(response);
        });
    }

    private void deliverError(RequestTask task, AiError error) {
        if (!task.finished.compareAndSet(false, true)) return;
        callbackExecutor.execute(() -> task.callback.onError(task.cancelled.get()
                ? new AiError(AiError.Type.CANCELLED, "请求已取消", 0)
                : error));
    }

    private static String safeHeader(HttpURLConnection connection, String name) {
        String value = connection.getHeaderField(name);
        return value == null || value.length() > 200 ? "" : value;
    }

    private static String readLimited(InputStream stream) throws IOException {
        if (stream == null) return "";
        StringBuilder result = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            char[] buffer = new char[4096];
            int read;
            while ((read = reader.read(buffer)) >= 0) {
                if (result.length() + read > MAX_RESPONSE_CHARS) throw new IOException("Response too large");
                result.append(buffer, 0, read);
            }
        }
        return result.toString();
    }

    private static void drain(InputStream stream) {
        if (stream == null) return;
        try (InputStream ignored = stream) {
            byte[] buffer = new byte[1024];
            while (ignored.read(buffer) >= 0) { /* discard without exposing provider text */ }
        } catch (IOException ignored) {
        }
    }

    private static Executor mainThreadExecutor() {
        Handler handler = new Handler(Looper.getMainLooper());
        return command -> handler.post(command);
    }

    private static String jsonString(String value) {
        StringBuilder escaped = new StringBuilder(value.length() + 16).append('"');
        for (int i = 0; i < value.length(); i++) {
            char character = value.charAt(i);
            switch (character) {
                case '"': escaped.append("\\\""); break;
                case '\\': escaped.append("\\\\"); break;
                case '\b': escaped.append("\\b"); break;
                case '\f': escaped.append("\\f"); break;
                case '\n': escaped.append("\\n"); break;
                case '\r': escaped.append("\\r"); break;
                case '\t': escaped.append("\\t"); break;
                default:
                    if (character < 0x20) {
                        escaped.append(String.format(java.util.Locale.US, "\\u%04x", (int) character));
                    } else {
                        escaped.append(character);
                    }
            }
        }
        return escaped.append('"').toString();
    }

    private static final class RequestTask implements RequestHandle {
        final Callback callback;
        final AtomicBoolean cancelled = new AtomicBoolean(false);
        final AtomicBoolean finished = new AtomicBoolean(false);
        volatile Future<?> future;
        volatile HttpURLConnection connection;

        RequestTask(Callback callback) {
            if (callback == null) throw new IllegalArgumentException("callback is required");
            this.callback = callback;
        }

        @Override
        public void cancel() {
            if (!cancelled.compareAndSet(false, true)) return;
            HttpURLConnection current = connection;
            if (current != null) current.disconnect();
            Future<?> pending = future;
            if (pending != null) pending.cancel(true);
        }

        @Override
        public boolean isCancelled() {
            return cancelled.get();
        }
    }
}
