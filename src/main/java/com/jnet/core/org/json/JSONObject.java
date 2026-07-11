package com.jnet.core.org.json;

import java.lang.reflect.Array;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

/** Minimal, dependency-free JSON object with strict parsing. */
public class JSONObject {
    static final int MAX_NESTING_DEPTH = 100;

    /** Sentinel used to represent a JSON {@code null} value. */
    public static final Object NULL = new Object() {
        @Override
        public boolean equals(Object object) {
            return object == null || object == this;
        }

        @Override
        public int hashCode() {
            return 0;
        }

        @Override
        public String toString() {
            return "null";
        }
    };

    private final Map<String, Object> map;

    public JSONObject() {
        this.map = new LinkedHashMap<>();
    }

    public JSONObject(String source) throws JSONException {
        this();
        JSONParser.parseObject(source, this);
    }

    public JSONObject(Map<?, ?> source) {
        this();
        if (source != null) {
            IdentityHashMap<Object, Boolean> seen = new IdentityHashMap<>();
            enterContainer(source, seen, 0);
            try {
                for (Map.Entry<?, ?> entry : source.entrySet()) {
                    String key = String.valueOf(entry.getKey());
                    map.put(key, wrap(entry.getValue(), seen, 1));
                }
            } finally {
                seen.remove(source);
            }
        }
    }

    public JSONObject put(String key, Object value) throws JSONException {
        if (key == null) {
            throw new JSONException("Null key.");
        }
        if (value == null) {
            map.remove(key);
        } else {
            map.put(key, wrap(value));
        }
        return this;
    }

    void putParsed(String key, Object value) {
        map.put(key, value == null ? NULL : value);
    }

    public Object get(String key) throws JSONException {
        if (key == null) {
            throw new JSONException("Null key.");
        }
        if (!map.containsKey(key)) {
            throw new JSONException("JSONObject[" + quote(key) + "] not found.");
        }
        return map.get(key);
    }

    public Object opt(String key) {
        return key == null ? null : map.get(key);
    }

    public String getString(String key) throws JSONException {
        Object value = get(key);
        if (value instanceof String) {
            return (String) value;
        }
        throw new JSONException("JSONObject[" + quote(key) + "] not a string.");
    }

    public String optString(String key) {
        return optString(key, "");
    }

    public String optString(String key, String defaultValue) {
        Object value = map.get(key);
        return value == null || value == NULL ? defaultValue : value.toString();
    }

    public long optLong(String key, long defaultValue) {
        Object value = map.get(key);
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        try {
            return value == null || value == NULL ? defaultValue : Long.parseLong(value.toString());
        } catch (RuntimeException ignored) {
            return defaultValue;
        }
    }

    public long optLong(String key) {
        return optLong(key, 0L);
    }

    public int optInt(String key, int defaultValue) {
        Object value = map.get(key);
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        try {
            return value == null || value == NULL ? defaultValue : Integer.parseInt(value.toString());
        } catch (RuntimeException ignored) {
            return defaultValue;
        }
    }

    public boolean optBoolean(String key, boolean defaultValue) {
        Object value = map.get(key);
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        if (value instanceof String) {
            if ("true".equalsIgnoreCase((String) value)) {
                return true;
            }
            if ("false".equalsIgnoreCase((String) value)) {
                return false;
            }
        }
        return defaultValue;
    }

    public JSONObject optJSONObject(String key) {
        Object value = map.get(key);
        return value instanceof JSONObject ? (JSONObject) value : null;
    }

    public JSONArray optJSONArray(String key) {
        Object value = map.get(key);
        return value instanceof JSONArray ? (JSONArray) value : null;
    }

    public JSONObject getJSONObject(String key) throws JSONException {
        Object value = get(key);
        if (value instanceof JSONObject) {
            return (JSONObject) value;
        }
        throw new JSONException("JSONObject[" + quote(key) + "] is not a JSONObject.");
    }

    public boolean has(String key) {
        return map.containsKey(key);
    }

    public boolean isNull(String key) {
        return NULL.equals(opt(key));
    }

    public int length() {
        return map.size();
    }

    public JSONObject put(String key, boolean value) {
        return put(key, Boolean.valueOf(value));
    }

    public JSONObject put(String key, double value) {
        return put(key, Double.valueOf(value));
    }

    public JSONObject put(String key, int value) {
        return put(key, Integer.valueOf(value));
    }

    public JSONObject put(String key, long value) {
        return put(key, Long.valueOf(value));
    }

    public Iterator<String> keys() {
        return Collections.unmodifiableSet(map.keySet()).iterator();
    }

    @Override
    public String toString() {
        StringBuilder result = new StringBuilder(map.size() * 16 + 2);
        appendTo(result, new IdentityHashMap<>(), 0);
        return result.toString();
    }

    public String toString(int indentFactor) {
        return toString();
    }

    public static String escape(String string) {
        if (string == null) {
            return "";
        }
        String quoted = quote(string);
        return quoted.substring(1, quoted.length() - 1);
    }

    public static String quote(String string) {
        if (string == null || string.isEmpty()) {
            return "\"\"";
        }

        StringBuilder result = new StringBuilder(string.length() + 4).append('"');
        for (int i = 0; i < string.length(); i++) {
            char current = string.charAt(i);
            switch (current) {
                case '\\':
                case '"':
                    result.append('\\').append(current);
                    break;
                case '\b':
                    result.append("\\b");
                    break;
                case '\t':
                    result.append("\\t");
                    break;
                case '\n':
                    result.append("\\n");
                    break;
                case '\f':
                    result.append("\\f");
                    break;
                case '\r':
                    result.append("\\r");
                    break;
                default:
                    if (current < 0x20) {
                        String hex = "000" + Integer.toHexString(current);
                        result.append("\\u").append(hex.substring(hex.length() - 4));
                    } else {
                        result.append(current);
                    }
            }
        }
        return result.append('"').toString();
    }

    public static String valueToString(Object value) {
        StringBuilder result = new StringBuilder(32);
        appendValue(value, result, new IdentityHashMap<>(), 0);
        return result.toString();
    }

    void appendTo(StringBuilder result, IdentityHashMap<Object, Boolean> seen, int depth) {
        enterContainer(this, seen, depth);
        try {
            result.append('{');
            boolean first = true;
            for (Map.Entry<String, Object> entry : map.entrySet()) {
                if (!first) {
                    result.append(',');
                }
                result.append(quote(entry.getKey())).append(':');
                appendValue(entry.getValue(), result, seen, depth + 1);
                first = false;
            }
            result.append('}');
        } finally {
            seen.remove(this);
        }
    }

    static void appendValue(
            Object value,
            StringBuilder result,
            IdentityHashMap<Object, Boolean> seen,
            int depth) {
        requireDepth(depth);
        if (value == null || value == NULL) {
            result.append("null");
            return;
        }
        if (value instanceof Number) {
            result.append(numberToString((Number) value));
            return;
        }
        if (value instanceof Boolean) {
            result.append(value);
            return;
        }
        if (value instanceof JSONObject) {
            ((JSONObject) value).appendTo(result, seen, depth);
            return;
        }
        if (value instanceof JSONArray) {
            ((JSONArray) value).appendTo(result, seen, depth);
            return;
        }
        if (value instanceof Map) {
            appendMap((Map<?, ?>) value, result, seen, depth);
            return;
        }
        if (value instanceof Iterable) {
            appendIterable((Iterable<?>) value, result, seen, depth);
            return;
        }
        if (value.getClass().isArray()) {
            appendArray(value, result, seen, depth);
            return;
        }
        result.append(quote(value.toString()));
    }

    public static String numberToString(Number number) {
        testValidity(number);
        String value = number.toString();
        if (value.indexOf('.') > 0 && value.indexOf('e') < 0 && value.indexOf('E') < 0) {
            while (value.endsWith("0")) {
                value = value.substring(0, value.length() - 1);
            }
            if (value.endsWith(".")) {
                value = value.substring(0, value.length() - 1);
            }
        }
        return value;
    }

    static Object wrap(Object value) {
        return wrap(value, new IdentityHashMap<>(), 0);
    }

    static Object wrap(Object value, IdentityHashMap<Object, Boolean> seen, int depth) {
        requireDepth(depth);
        if (value == null || value == NULL) {
            return NULL;
        }
        if (value instanceof JSONObject || value instanceof JSONArray || value instanceof String
                || value instanceof Boolean) {
            return value;
        }
        if (value instanceof Number) {
            testValidity(value);
            return value;
        }
        if (value instanceof Character) {
            return value.toString();
        }
        if (value instanceof Map) {
            Map<?, ?> source = (Map<?, ?>) value;
            enterContainer(source, seen, depth);
            try {
                JSONObject object = new JSONObject();
                for (Map.Entry<?, ?> entry : source.entrySet()) {
                    object.map.put(
                            String.valueOf(entry.getKey()),
                            wrap(entry.getValue(), seen, depth + 1));
                }
                return object;
            } finally {
                seen.remove(source);
            }
        }
        if (value instanceof Iterable) {
            Iterable<?> source = (Iterable<?>) value;
            enterContainer(source, seen, depth);
            try {
                JSONArray array = new JSONArray();
                for (Object item : source) {
                    array.addParsed(wrap(item, seen, depth + 1));
                }
                return array;
            } finally {
                seen.remove(source);
            }
        }
        if (value.getClass().isArray()) {
            enterContainer(value, seen, depth);
            int length = Array.getLength(value);
            try {
                JSONArray array = new JSONArray();
                for (int i = 0; i < length; i++) {
                    array.addParsed(wrap(Array.get(value, i), seen, depth + 1));
                }
                return array;
            } finally {
                seen.remove(value);
            }
        }
        return value.toString();
    }

    static void enterContainer(Object value, IdentityHashMap<Object, Boolean> seen, int depth) {
        requireDepth(depth);
        if (seen.put(value, Boolean.TRUE) != null) {
            throw new JSONException("Circular reference detected in JSON value.");
        }
    }

    private static void appendMap(
            Map<?, ?> value,
            StringBuilder result,
            IdentityHashMap<Object, Boolean> seen,
            int depth) {
        enterContainer(value, seen, depth);
        try {
            result.append('{');
            boolean first = true;
            for (Map.Entry<?, ?> entry : value.entrySet()) {
                if (!first) {
                    result.append(',');
                }
                result.append(quote(String.valueOf(entry.getKey()))).append(':');
                appendValue(entry.getValue(), result, seen, depth + 1);
                first = false;
            }
            result.append('}');
        } finally {
            seen.remove(value);
        }
    }

    private static void appendIterable(
            Iterable<?> value,
            StringBuilder result,
            IdentityHashMap<Object, Boolean> seen,
            int depth) {
        enterContainer(value, seen, depth);
        try {
            result.append('[');
            boolean first = true;
            for (Object item : value) {
                if (!first) {
                    result.append(',');
                }
                appendValue(item, result, seen, depth + 1);
                first = false;
            }
            result.append(']');
        } finally {
            seen.remove(value);
        }
    }

    private static void appendArray(
            Object value,
            StringBuilder result,
            IdentityHashMap<Object, Boolean> seen,
            int depth) {
        enterContainer(value, seen, depth);
        try {
            result.append('[');
            int length = Array.getLength(value);
            for (int i = 0; i < length; i++) {
                if (i > 0) {
                    result.append(',');
                }
                appendValue(Array.get(value, i), result, seen, depth + 1);
            }
            result.append(']');
        } finally {
            seen.remove(value);
        }
    }

    private static void requireDepth(int depth) {
        if (depth > MAX_NESTING_DEPTH) {
            throw new JSONException("JSON nesting depth exceeds " + MAX_NESTING_DEPTH + ".");
        }
    }

    private static void testValidity(Object value) {
        if (value instanceof Double) {
            double number = (Double) value;
            if (Double.isInfinite(number) || Double.isNaN(number)) {
                throw new JSONException("JSON does not allow non-finite numbers.");
            }
        } else if (value instanceof Float) {
            float number = (Float) value;
            if (Float.isInfinite(number) || Float.isNaN(number)) {
                throw new JSONException("JSON does not allow non-finite numbers.");
            }
        }
    }
}
