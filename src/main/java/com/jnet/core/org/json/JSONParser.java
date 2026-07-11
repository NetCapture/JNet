package com.jnet.core.org.json;

import java.math.BigDecimal;
import java.math.BigInteger;

/** Shared strict JSON parser for {@link JSONObject} and {@link JSONArray}. */
final class JSONParser {
    private static final int MAX_NUMBER_LENGTH = 10_000;
    private static final BigInteger INTEGER_MIN = BigInteger.valueOf(Integer.MIN_VALUE);
    private static final BigInteger INTEGER_MAX = BigInteger.valueOf(Integer.MAX_VALUE);
    private static final BigInteger LONG_MIN = BigInteger.valueOf(Long.MIN_VALUE);
    private static final BigInteger LONG_MAX = BigInteger.valueOf(Long.MAX_VALUE);

    private final String source;
    private int index;

    private JSONParser(String source) {
        if (source == null) {
            throw new JSONException("JSON source must not be null");
        }
        this.source = source;
    }

    static void parseObject(String source, JSONObject target) {
        JSONParser parser = new JSONParser(source);
        parser.skipWhitespace();
        if (parser.peek() != '{') {
            throw parser.error("A JSONObject must start with '{'");
        }
        parser.readObject(target, 0);
        parser.requireEnd();
    }

    static void parseArray(String source, JSONArray target) {
        JSONParser parser = new JSONParser(source);
        parser.skipWhitespace();
        if (parser.peek() != '[') {
            throw parser.error("A JSONArray must start with '['");
        }
        parser.readArray(target, 0);
        parser.requireEnd();
    }

    private void readObject(JSONObject target, int depth) {
        checkDepth(depth);
        expect('{');
        skipWhitespace();
        if (consume('}')) {
            return;
        }

        while (true) {
            skipWhitespace();
            if (peek() != '"') {
                throw error("Object keys must be strings");
            }
            String key = readString();
            if (target.has(key)) {
                throw error("Duplicate object key: " + key);
            }
            skipWhitespace();
            expect(':');
            target.putParsed(key, readValue(depth + 1));
            skipWhitespace();
            if (consume('}')) {
                return;
            }
            expect(',');
        }
    }

    private void readArray(JSONArray target, int depth) {
        checkDepth(depth);
        expect('[');
        skipWhitespace();
        if (consume(']')) {
            return;
        }

        while (true) {
            target.addParsed(readValue(depth + 1));
            skipWhitespace();
            if (consume(']')) {
                return;
            }
            expect(',');
        }
    }

    private Object readValue(int depth) {
        checkDepth(depth);
        skipWhitespace();
        char current = peek();
        switch (current) {
            case '"':
                return readString();
            case '{':
                JSONObject object = new JSONObject();
                readObject(object, depth);
                return object;
            case '[':
                JSONArray array = new JSONArray();
                readArray(array, depth);
                return array;
            case 't':
                readLiteral("true");
                return Boolean.TRUE;
            case 'f':
                readLiteral("false");
                return Boolean.FALSE;
            case 'n':
                readLiteral("null");
                return JSONObject.NULL;
            default:
                if (current == '-' || isDigit(current)) {
                    return readNumber();
                }
                throw error("Unexpected character '" + printable(current) + "'");
        }
    }

    private String readString() {
        expect('"');
        StringBuilder result = new StringBuilder();
        while (index < source.length()) {
            char current = source.charAt(index++);
            if (current == '"') {
                return result.toString();
            }
            if (current < 0x20) {
                throw error("Unescaped control character in string");
            }
            if (current != '\\') {
                result.append(current);
                continue;
            }

            if (index >= source.length()) {
                throw error("Unterminated escape sequence");
            }
            char escaped = source.charAt(index++);
            switch (escaped) {
                case '"':
                case '\\':
                case '/':
                    result.append(escaped);
                    break;
                case 'b':
                    result.append('\b');
                    break;
                case 'f':
                    result.append('\f');
                    break;
                case 'n':
                    result.append('\n');
                    break;
                case 'r':
                    result.append('\r');
                    break;
                case 't':
                    result.append('\t');
                    break;
                case 'u':
                    result.append(readUnicodeEscape());
                    break;
                default:
                    throw error("Invalid escape sequence: \\" + escaped);
            }
        }
        throw error("Unterminated string");
    }

    private char readUnicodeEscape() {
        if (index + 4 > source.length()) {
            throw error("Incomplete unicode escape");
        }
        int value = 0;
        for (int i = 0; i < 4; i++) {
            char digit = source.charAt(index++);
            int hex = Character.digit(digit, 16);
            if (hex < 0) {
                throw error("Invalid unicode escape");
            }
            value = (value << 4) | hex;
        }
        return (char) value;
    }

    private Number readNumber() {
        int start = index;
        consume('-');

        if (consume('0')) {
            if (isDigit(peek())) {
                throw error("Leading zero is not allowed in a number");
            }
        } else {
            requireDigits("Expected digit in number");
        }

        boolean decimal = false;
        if (consume('.')) {
            decimal = true;
            requireDigits("Expected digit after decimal point");
        }

        char exponent = peek();
        if (exponent == 'e' || exponent == 'E') {
            decimal = true;
            index++;
            if (peek() == '+' || peek() == '-') {
                index++;
            }
            requireDigits("Expected digit in exponent");
        }

        int numberLength = index - start;
        if (numberLength > MAX_NUMBER_LENGTH) {
            throw new JSONException("JSON number exceeds " + MAX_NUMBER_LENGTH
                    + " characters at index " + start);
        }
        String number = source.substring(start, index);
        try {
            if (decimal) {
                return new BigDecimal(number);
            }
            BigInteger integer = new BigInteger(number);
            if (integer.compareTo(INTEGER_MIN) >= 0 && integer.compareTo(INTEGER_MAX) <= 0) {
                return integer.intValue();
            }
            if (integer.compareTo(LONG_MIN) >= 0 && integer.compareTo(LONG_MAX) <= 0) {
                return integer.longValue();
            }
            return integer;
        } catch (NumberFormatException e) {
            throw new JSONException("Invalid number at index " + start, e);
        }
    }

    private void requireDigits(String message) {
        int start = index;
        while (isDigit(peek())) {
            index++;
        }
        if (start == index) {
            throw error(message);
        }
    }

    private void readLiteral(String literal) {
        if (!source.regionMatches(index, literal, 0, literal.length())) {
            throw error("Expected '" + literal + "'");
        }
        index += literal.length();
    }

    private void requireEnd() {
        skipWhitespace();
        if (index != source.length()) {
            throw error("Trailing characters after JSON value");
        }
    }

    private void checkDepth(int depth) {
        if (depth > JSONObject.MAX_NESTING_DEPTH) {
            throw error("JSON nesting exceeds " + JSONObject.MAX_NESTING_DEPTH);
        }
    }

    private void skipWhitespace() {
        while (index < source.length()) {
            char current = source.charAt(index);
            if (current == ' ' || current == '\t' || current == '\n' || current == '\r') {
                index++;
            } else {
                return;
            }
        }
    }

    private void expect(char expected) {
        skipWhitespace();
        if (!consume(expected)) {
            throw error("Expected '" + expected + "'");
        }
    }

    private boolean consume(char expected) {
        if (peek() == expected) {
            index++;
            return true;
        }
        return false;
    }

    private char peek() {
        return index < source.length() ? source.charAt(index) : '\0';
    }

    private JSONException error(String message) {
        return new JSONException(message + " at index " + index);
    }

    private static boolean isDigit(char value) {
        return value >= '0' && value <= '9';
    }

    private static String printable(char value) {
        return value == '\0' ? "end of input" : String.valueOf(value);
    }
}
