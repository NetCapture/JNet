package com.jnet.core.org.json;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Minimal implementation of JSONObject to avoid external dependencies.
 * Wraps a Map<String, Object> and provides standard accessors.
 */
public class JSONObject {
    private final Map<String, Object> map;

    public JSONObject() {
        this.map = new HashMap<>();
    }

    public JSONObject(String source) throws JSONException {
        this();
        // A real parser would be complex. For now, we assume simple JSON or delegate to
        // a simple regex parser if needed.
        // However, since we are replacing a heavy library, we might need a very basic
        // recursive parser.
        // For this "minimal" version, we will try to use a very naive approach or just
        // support empty/simple structures
        // if the usage in JNet is limited.
        // BUT, looking at the code, JNet parses API responses. So we need a REAL (even
        // if simple) parser.
        // Let's implement a recursive descent parser in a separate helper or inline.
        // For simplicity and stability, we will use a simplified parser here.
        new JSONParser(source).parseObject(this);
    }

    public JSONObject(Map<?, ?> map) {
        this.map = new HashMap<>();
        if (map != null) {
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                String key = String.valueOf(entry.getKey());
                Object value = wrap(entry.getValue());
                this.map.put(key, value);
            }
        }
    }

    public JSONObject put(String key, Object value) throws JSONException {
        if (key == null) {
            throw new JSONException("Null key.");
        }
        if (value != null) {
            this.map.put(key, value);
        } else {
            this.map.remove(key);
        }
        return this;
    }

    public Object get(String key) throws JSONException {
        if (key == null) {
            throw new JSONException("Null key.");
        }
        Object value = this.map.get(key);
        if (value == null) {
            throw new JSONException("JSONObject[" + quote(key) + "] not found.");
        }
        return value;
    }

    public String optString(String key) {
        return optString(key, "");
    }

    public String optString(String key, String defaultValue) {
        Object value = this.map.get(key);
        return value != null ? value.toString() : defaultValue;
    }

    public long optLong(String key, long defaultValue) {
        Object value = this.map.get(key);
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        try {
            return Long.parseLong(value.toString());
        } catch (Exception e) {
            return defaultValue;
        }
    }

    public int optInt(String key, int defaultValue) {
        Object value = this.map.get(key);
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        try {
            return Integer.parseInt(value.toString());
        } catch (Exception e) {
            return defaultValue;
        }
    }

    public boolean optBoolean(String key, boolean defaultValue) {
        Object value = this.map.get(key);
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        if (value instanceof String) {
            return Boolean.parseBoolean((String) value);
        }
        return defaultValue;
    }

    public JSONObject optJSONObject(String key) {
        Object value = this.map.get(key);
        if (value instanceof JSONObject) {
            return (JSONObject) value;
        }
        return null;
    }

    public JSONArray optJSONArray(String key) {
        Object value = this.map.get(key);
        if (value instanceof JSONArray) {
            return (JSONArray) value;
        }
        return null;
    }

    public JSONObject getJSONObject(String key) throws JSONException {
        Object value = get(key);
        if (value instanceof JSONObject) {
            return (JSONObject) value;
        }
        throw new JSONException("JSONObject[" + quote(key) + "] is not a JSONObject.");
    }

    public boolean has(String key) {
        return this.map.containsKey(key);
    }

    public Iterator<String> keys() {
        return this.map.keySet().iterator();
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("{");
        boolean first = true;
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            if (!first)
                sb.append(",");
            sb.append(quote(entry.getKey()));
            sb.append(":");
            sb.append(valueToString(entry.getValue()));
            first = false;
        }
        sb.append("}");
        return sb.toString();
    }

    public static String quote(String string) {
        if (string == null || string.length() == 0) {
            return "\"\"";
        }

        char c;
        int i;
        int len = string.length();
        StringBuilder sb = new StringBuilder(len + 4);
        String t;

        sb.append('"');
        for (i = 0; i < len; i += 1) {
            c = string.charAt(i);
            switch (c) {
                case '\\':
                case '"':
                    sb.append('\\');
                    sb.append(c);
                    break;
                case '\b':
                    sb.append("\\b");
                    break;
                case '\t':
                    sb.append("\\t");
                    break;
                case '\n':
                    sb.append("\\n");
                    break;
                case '\f':
                    sb.append("\\f");
                    break;
                case '\r':
                    sb.append("\\r");
                    break;
                default:
                    if (c < ' ') {
                        t = "000" + Integer.toHexString(c);
                        sb.append("\\u" + t.substring(t.length() - 4));
                    } else {
                        sb.append(c);
                    }
            }
        }
        sb.append('"');
        return sb.toString();
    }

    public static String valueToString(Object value) {
        if (value == null || value.equals(null)) {
            return "null";
        }
        if (value instanceof Number) {
            return numberToString((Number) value);
        }
        if (value instanceof Boolean || value instanceof JSONObject || value instanceof JSONArray) {
            return value.toString();
        }
        return quote(value.toString());
    }

    public static String numberToString(Number number) {
        if (number == null) {
            throw new JSONException("Null pointer");
        }
        String string = number.toString();
        if (string.indexOf('.') > 0 && string.indexOf('e') < 0 && string.indexOf('E') < 0) {
            while (string.endsWith("0")) {
                string = string.substring(0, string.length() - 1);
            }
            if (string.endsWith(".")) {
                string = string.substring(0, string.length() - 1);
            }
        }
        return string;
    }

    static Object wrap(Object object) {
        if (object == null) {
            return null; // or NULL object
        }
        if (object instanceof JSONObject || object instanceof JSONArray ||
                object instanceof String || object instanceof Number ||
                object instanceof Boolean || object instanceof Character) {
            return object;
        }
        if (object instanceof Map) {
            return new JSONObject((Map<?, ?>) object);
        }
        if (object instanceof Iterable) {
            return new JSONArray((Iterable<?>) object);
        }
        if (object.getClass().isArray()) {
            return new JSONArray(object);
        }
        return object.toString();
    }

    // --- Minimal Parser ---

    private static class JSONParser {
        private final String source;
        private int index;
        private final int length;

        public JSONParser(String source) {
            this.source = source;
            this.length = source.length();
            this.index = 0;
        }

        public void parseObject(JSONObject jsonObject) {
            skipWhiteSpace();
            if (test('{')) {
                skipWhiteSpace();
                if (test('}')) {
                    return;
                }
                while (true) {
                    Object key = parseValue();
                    skipWhiteSpace();
                    if (!test(':')) {
                        throw new JSONException("Expected ':' at " + index);
                    }
                    Object value = parseValue();
                    jsonObject.put(key.toString(), value);

                    skipWhiteSpace();
                    if (test('}')) {
                        return;
                    }
                    if (!test(',')) {
                        throw new JSONException("Expected ',' or '}' at " + index);
                    }
                }
            }
        }

        public void parseArray(JSONArray jsonArray) {
            skipWhiteSpace();
            if (test('[')) {
                skipWhiteSpace();
                if (test(']')) {
                    return;
                }
                while (true) {
                    Object value = parseValue();
                    jsonArray.put(value);

                    skipWhiteSpace();
                    if (test(']')) {
                        return;
                    }
                    if (!test(',')) {
                        throw new JSONException("Expected ',' or ']' at " + index);
                    }
                }
            }
        }

        private Object parseValue() {
            skipWhiteSpace();
            char c = peek();
            if (c == '"') {
                return parseString();
            }
            if (c == '{') {
                JSONObject obj = new JSONObject();
                // hack to use the same parser instance state? No, recursive logic
                // But JSONObject(source) creates NEW parser.
                // We need to support partial parsing.
                // Let's change parseObject to be static or pass parser?
                // Actually, let's keep it simple: consume chars here
                // We need to implement full logic inside this parser class
                return parseObjectInternal();
            }
            if (c == '[') {
                return parseArrayInternal();
            }
            if (c == 't' && source.startsWith("true", index)) {
                index += 4;
                return Boolean.TRUE;
            }
            if (c == 'f' && source.startsWith("false", index)) {
                index += 5;
                return Boolean.FALSE;
            }
            if (c == 'n' && source.startsWith("null", index)) {
                index += 4;
                return null;
            }
            return parseNumber();
        }

        private JSONObject parseObjectInternal() {
            JSONObject obj = new JSONObject();
            consume('{');
            skipWhiteSpace();
            if (test('}'))
                return obj;

            while (true) {
                skipWhiteSpace();
                String key = parseString();
                skipWhiteSpace();
                consume(':');
                Object val = parseValue();
                obj.put(key, val);
                skipWhiteSpace();
                if (test('}'))
                    return obj;
                consume(',');
            }
        }

        private JSONArray parseArrayInternal() {
            JSONArray arr = new JSONArray();
            consume('[');
            skipWhiteSpace();
            if (test(']'))
                return arr;

            while (true) {
                Object val = parseValue();
                arr.put(val);
                skipWhiteSpace();
                if (test(']'))
                    return arr;
                consume(',');
            }
        }

        private String parseString() {
            consume('"');
            StringBuilder sb = new StringBuilder();
            while (index < length) {
                char c = source.charAt(index++);
                if (c == '"') {
                    return sb.toString();
                }
                if (c == '\\') {
                    if (index >= length)
                        throw new JSONException("Unterminated string");
                    char escape = source.charAt(index++);
                    switch (escape) {
                        case '"':
                            sb.append('"');
                            break;
                        case '\\':
                            sb.append('\\');
                            break;
                        case '/':
                            sb.append('/');
                            break;
                        case 'b':
                            sb.append('\b');
                            break;
                        case 'f':
                            sb.append('\f');
                            break;
                        case 'n':
                            sb.append('\n');
                            break;
                        case 'r':
                            sb.append('\r');
                            break;
                        case 't':
                            sb.append('\t');
                            break;
                        case 'u':
                            if (index + 4 > length)
                                throw new JSONException("Invalid unicode escape");
                            String hex = source.substring(index, index + 4);
                            sb.append((char) Integer.parseInt(hex, 16));
                            index += 4;
                            break;
                        default:
                            sb.append(escape);
                    }
                } else {
                    sb.append(c);
                }
            }
            throw new JSONException("Unterminated string");
        }

        private Number parseNumber() {
            int start = index;
            if (peek() == '-')
                index++;
            while (index < length && Character.isDigit(source.charAt(index)))
                index++;
            if (index < length && source.charAt(index) == '.') {
                index++;
                while (index < length && Character.isDigit(source.charAt(index)))
                    index++;
            }
            if (index < length && (source.charAt(index) == 'e' || source.charAt(index) == 'E')) {
                index++;
                if (index < length && (source.charAt(index) == '+' || source.charAt(index) == '-'))
                    index++;
                while (index < length && Character.isDigit(source.charAt(index)))
                    index++;
            }
            String numStr = source.substring(start, index);
            if (numStr.contains(".") || numStr.contains("e") || numStr.contains("E")) {
                return Double.parseDouble(numStr);
            }
            long l = Long.parseLong(numStr);
            if (l <= Integer.MAX_VALUE && l >= Integer.MIN_VALUE)
                return (int) l;
            return l;
        }

        private void skipWhiteSpace() {
            while (index < length && Character.isWhitespace(source.charAt(index))) {
                index++;
            }
        }

        private boolean test(char c) {
            if (index < length && source.charAt(index) == c) {
                index++;
                return true;
            }
            return false;
        }

        private char peek() {
            if (index < length)
                return source.charAt(index);
            return 0;
        }

        private void consume(char c) {
            if (!test(c)) {
                throw new JSONException("Expected '" + c + "' at " + index);
            }
        }
    }
}
