package com.k2.music;

import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Small strict JSON reader used to keep the offline chord data dependency-free. */
final class SimpleJsonParser {
    private final String source;
    private int position;

    private SimpleJsonParser(String source) {
        this.source = source;
    }

    static Object parse(Reader reader) throws IOException {
        StringBuilder text = new StringBuilder();
        char[] buffer = new char[4096];
        int count;
        while ((count = reader.read(buffer)) >= 0) {
            text.append(buffer, 0, count);
        }
        SimpleJsonParser parser = new SimpleJsonParser(text.toString());
        parser.skipWhitespace();
        if (parser.peek('\uFEFF')) {
            parser.position++;
        }
        Object value = parser.readValue();
        parser.skipWhitespace();
        if (parser.position != parser.source.length()) {
            throw parser.error("Unexpected trailing content");
        }
        return value;
    }

    private Object readValue() throws IOException {
        skipWhitespace();
        if (position >= source.length()) {
            throw error("Unexpected end of JSON");
        }
        char next = source.charAt(position);
        switch (next) {
            case '{':
                return readObject();
            case '[':
                return readArray();
            case '"':
                return readString();
            case 't':
                readLiteral("true");
                return Boolean.TRUE;
            case 'f':
                readLiteral("false");
                return Boolean.FALSE;
            case 'n':
                readLiteral("null");
                return null;
            default:
                if (next == '-' || Character.isDigit(next)) {
                    return readNumber();
                }
                throw error("Unexpected character '" + next + "'");
        }
    }

    private Map<String, Object> readObject() throws IOException {
        expect('{');
        Map<String, Object> object = new LinkedHashMap<>();
        skipWhitespace();
        if (take('}')) {
            return object;
        }
        while (true) {
            skipWhitespace();
            if (!peek('"')) {
                throw error("Object keys must be strings");
            }
            String key = readString();
            skipWhitespace();
            expect(':');
            if (object.containsKey(key)) {
                throw error("Duplicate object key '" + key + "'");
            }
            object.put(key, readValue());
            skipWhitespace();
            if (take('}')) {
                return object;
            }
            expect(',');
        }
    }

    private List<Object> readArray() throws IOException {
        expect('[');
        List<Object> array = new ArrayList<>();
        skipWhitespace();
        if (take(']')) {
            return array;
        }
        while (true) {
            array.add(readValue());
            skipWhitespace();
            if (take(']')) {
                return array;
            }
            expect(',');
        }
    }

    private String readString() throws IOException {
        expect('"');
        StringBuilder value = new StringBuilder();
        while (position < source.length()) {
            char character = source.charAt(position++);
            if (character == '"') {
                return value.toString();
            }
            if (character == '\\') {
                if (position >= source.length()) {
                    throw error("Unterminated escape sequence");
                }
                char escaped = source.charAt(position++);
                switch (escaped) {
                    case '"':
                    case '\\':
                    case '/':
                        value.append(escaped);
                        break;
                    case 'b':
                        value.append('\b');
                        break;
                    case 'f':
                        value.append('\f');
                        break;
                    case 'n':
                        value.append('\n');
                        break;
                    case 'r':
                        value.append('\r');
                        break;
                    case 't':
                        value.append('\t');
                        break;
                    case 'u':
                        value.append(readUnicodeEscape());
                        break;
                    default:
                        throw error("Unsupported escape sequence \\" + escaped + "'");
                }
            } else {
                if (character < 0x20) {
                    throw error("Control character in string");
                }
                value.append(character);
            }
        }
        throw error("Unterminated string");
    }

    private char readUnicodeEscape() throws IOException {
        if (position + 4 > source.length()) {
            throw error("Incomplete Unicode escape");
        }
        String digits = source.substring(position, position + 4);
        position += 4;
        try {
            return (char) Integer.parseInt(digits, 16);
        } catch (NumberFormatException exception) {
            throw error("Invalid Unicode escape " + digits);
        }
    }

    private Number readNumber() throws IOException {
        int start = position;
        take('-');
        if (take('0')) {
            // A leading zero is valid only as the complete integer part.
        } else {
            readDigits(true);
        }
        boolean decimal = false;
        if (take('.')) {
            decimal = true;
            readDigits(true);
        }
        if (take('e') || take('E')) {
            decimal = true;
            if (!take('+')) {
                take('-');
            }
            readDigits(true);
        }
        String token = source.substring(start, position);
        try {
            return decimal ? Double.parseDouble(token) : Long.parseLong(token);
        } catch (NumberFormatException exception) {
            throw error("Invalid number " + token);
        }
    }

    private void readDigits(boolean requireOne) throws IOException {
        int start = position;
        while (position < source.length() && Character.isDigit(source.charAt(position))) {
            position++;
        }
        if (requireOne && start == position) {
            throw error("Expected a digit");
        }
    }

    private void readLiteral(String literal) throws IOException {
        if (!source.regionMatches(position, literal, 0, literal.length())) {
            throw error("Expected " + literal);
        }
        position += literal.length();
    }

    private void skipWhitespace() {
        while (position < source.length() && Character.isWhitespace(source.charAt(position))) {
            position++;
        }
    }

    private boolean peek(char expected) {
        return position < source.length() && source.charAt(position) == expected;
    }

    private boolean take(char expected) {
        if (peek(expected)) {
            position++;
            return true;
        }
        return false;
    }

    private void expect(char expected) throws IOException {
        if (!take(expected)) {
            throw error("Expected '" + expected + "'");
        }
    }

    private IOException error(String message) {
        return new IOException(message + " at character " + position + ".");
    }
}
