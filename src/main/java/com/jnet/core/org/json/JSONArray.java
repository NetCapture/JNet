package com.jnet.core.org.json;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Minimal implementation of JSONArray.
 */
public class JSONArray implements Iterable<Object> {
    private final List<Object> list;

    public JSONArray() {
        this.list = new ArrayList<>();
    }

    public JSONArray(String source) throws JSONException {
        this();
        new JSONParser(source).parseArray(this);
    }

    public JSONArray(List<?> list) {
        this.list = new ArrayList<>();
        if (list != null) {
            for (Object o : list) {
                this.list.add(JSONObject.wrap(o));
            }
        }
    }

    public JSONArray(Object array) throws JSONException {
        this();
        if (!array.getClass().isArray()) {
            throw new JSONException("JSONArray initial value should be a string or collection or array.");
        }
        int length = java.lang.reflect.Array.getLength(array);
        for (int i = 0; i < length; i += 1) {
            this.put(JSONObject.wrap(java.lang.reflect.Array.get(array, i)));
        }
    }

    public int length() {
        return this.list.size();
    }

    public JSONArray put(Object value) {
        this.list.add(value);
        return this;
    }

    public Object get(int index) throws JSONException {
        if (index < 0 || index >= this.length()) {
            throw new JSONException("JSONArray[" + index + "] not found.");
        }
        return this.list.get(index);
    }

    public String getString(int index) throws JSONException {
        Object object = get(index);
        if (object instanceof String) {
            return (String) object;
        }
        throw new JSONException("JSONArray[" + index + "] not a string.");
    }

    public String optString(int index) {
        return optString(index, "");
    }

    public String optString(int index, String defaultValue) {
        Object object = this.opt(index);
        return JSONObject.valueToString(object);
    }

    public int optInt(int index) {
        return optInt(index, 0);
    }

    public int optInt(int index, int defaultValue) {
        Object val = this.opt(index);
        if (val instanceof Number) {
            return ((Number) val).intValue();
        }
        try {
            return Integer.parseInt((String) val);
        } catch (Exception e) {
            return defaultValue;
        }
    }

    public JSONObject getJSONObject(int index) throws JSONException {
        Object object = this.get(index);
        if (object instanceof JSONObject) {
            return (JSONObject) object;
        }
        throw new JSONException("JSONArray[" + index + "] is not a JSONObject.");
    }

    public JSONObject optJSONObject(int index) {
        Object val = this.opt(index);
        return val instanceof JSONObject ? (JSONObject) val : null;
    }

    public Object opt(int index) {
        return (index < 0 || index >= this.length()) ? null : this.list.get(index);
    }

    @Override
    public Iterator<Object> iterator() {
        return this.list.iterator();
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("[");
        boolean first = true;
        for (Object o : list) {
            if (!first)
                sb.append(",");
            sb.append(JSONObject.valueToString(o));
            first = false;
        }
        sb.append("]");
        return sb.toString();
    }

    // --- Duplicated Minimal Parser because I hid it in JSONObject ---
    // In a real implementation we would share this code.
    private static class JSONParser {
        private final String source;
        private int index;
        private final int length;

        public JSONParser(String source) {
            this.source = source;
            this.length = source.length();
            this.index = 0;
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

        public void parseObject(JSONObject jsonObject) {
            // simplified for shared logic capability, though mostly unused here
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

        private Object parseValue() {
            skipWhiteSpace();
            char c = peek();
            if (c == '"') {
                return parseString();
            }
            if (c == '{') {
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
