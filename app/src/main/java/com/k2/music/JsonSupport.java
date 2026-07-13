package com.k2.music;

import java.io.IOException;
import java.io.StringReader;
import java.util.List;
import java.util.Map;

/** Dependency-free strict JSON bridge for versioned local backup files. */
public final class JsonSupport {
    private JsonSupport() {}

    public static Object parse(String json) throws IOException {
        return SimpleJsonParser.parse(new StringReader(json == null ? "" : json));
    }

    public static String stringify(Object value) {
        StringBuilder output = new StringBuilder();
        append(output, value);
        return output.toString();
    }

    private static void append(StringBuilder output, Object value) {
        if (value == null) {
            output.append("null");
        } else if (value instanceof String) {
            appendString(output, (String) value);
        } else if (value instanceof Boolean) {
            output.append(value);
        } else if (value instanceof Number) {
            Number number = (Number) value;
            if (number instanceof Double && !Double.isFinite(number.doubleValue())) {
                throw new IllegalArgumentException("JSON number must be finite.");
            }
            if (number instanceof Float && !Float.isFinite(number.floatValue())) {
                throw new IllegalArgumentException("JSON number must be finite.");
            }
            output.append(number);
        } else if (value instanceof Map) {
            output.append('{');
            boolean first = true;
            for (Object rawEntry : ((Map<?, ?>) value).entrySet()) {
                Map.Entry<?, ?> entry = (Map.Entry<?, ?>) rawEntry;
                if (!(entry.getKey() instanceof String)) {
                    throw new IllegalArgumentException("JSON object keys must be strings.");
                }
                if (!first) output.append(',');
                first = false;
                appendString(output, (String) entry.getKey());
                output.append(':');
                append(output, entry.getValue());
            }
            output.append('}');
        } else if (value instanceof Iterable) {
            output.append('[');
            boolean first = true;
            for (Object item : (Iterable<?>) value) {
                if (!first) output.append(',');
                first = false;
                append(output, item);
            }
            output.append(']');
        } else if (value.getClass().isArray()) {
            output.append('[');
            int length = java.lang.reflect.Array.getLength(value);
            for (int index = 0; index < length; index++) {
                if (index > 0) output.append(',');
                append(output, java.lang.reflect.Array.get(value, index));
            }
            output.append(']');
        } else {
            throw new IllegalArgumentException("Unsupported JSON value: " + value.getClass().getName());
        }
    }

    private static void appendString(StringBuilder output, String value) {
        output.append('"');
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            switch (character) {
                case '"': output.append("\\\""); break;
                case '\\': output.append("\\\\"); break;
                case '\b': output.append("\\b"); break;
                case '\f': output.append("\\f"); break;
                case '\n': output.append("\\n"); break;
                case '\r': output.append("\\r"); break;
                case '\t': output.append("\\t"); break;
                default:
                    if (character < 0x20) {
                        output.append(String.format("\\u%04x", (int) character));
                    } else {
                        output.append(character);
                    }
            }
        }
        output.append('"');
    }
}
