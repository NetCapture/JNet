package com.jnet.core.org.json;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.IdentityHashMap;
import java.util.List;

/** Minimal, dependency-free JSON array with strict parsing. */
public class JSONArray implements Iterable<Object> {
    private final List<Object> list;

    public JSONArray() {
        this.list = new ArrayList<>();
    }

    public JSONArray(String source) throws JSONException {
        this();
        JSONParser.parseArray(source, this);
    }

    public JSONArray(List<?> values) {
        this((Iterable<?>) values);
    }

    public JSONArray(Iterable<?> values) {
        this();
        if (values != null) {
            IdentityHashMap<Object, Boolean> seen = new IdentityHashMap<>();
            JSONObject.enterContainer(values, seen, 0);
            try {
                for (Object value : values) {
                    list.add(JSONObject.wrap(value, seen, 1));
                }
            } finally {
                seen.remove(values);
            }
        }
    }

    public JSONArray(Object array) throws JSONException {
        this();
        if (array == null || !array.getClass().isArray()) {
            throw new JSONException("JSONArray initial value should be an array.");
        }
        IdentityHashMap<Object, Boolean> seen = new IdentityHashMap<>();
        JSONObject.enterContainer(array, seen, 0);
        try {
            int length = Array.getLength(array);
            for (int i = 0; i < length; i++) {
                list.add(JSONObject.wrap(Array.get(array, i), seen, 1));
            }
        } finally {
            seen.remove(array);
        }
    }

    public int length() {
        return list.size();
    }

    public JSONArray put(Object value) {
        list.add(JSONObject.wrap(value));
        return this;
    }

    public JSONArray put(boolean value) {
        return put(Boolean.valueOf(value));
    }

    public JSONArray put(double value) {
        return put(Double.valueOf(value));
    }

    public JSONArray put(int value) {
        return put(Integer.valueOf(value));
    }

    void addParsed(Object value) {
        list.add(value == null ? JSONObject.NULL : value);
    }

    public Object get(int index) throws JSONException {
        if (index < 0 || index >= list.size()) {
            throw new JSONException("JSONArray[" + index + "] not found.");
        }
        return list.get(index);
    }

    public String getString(int index) throws JSONException {
        Object value = get(index);
        if (value instanceof String) {
            return (String) value;
        }
        throw new JSONException("JSONArray[" + index + "] not a string.");
    }

    public String optString(int index) {
        return optString(index, "");
    }

    public String optString(int index, String defaultValue) {
        Object value = opt(index);
        return value == null || value == JSONObject.NULL ? defaultValue : value.toString();
    }

    public int optInt(int index) {
        return optInt(index, 0);
    }

    public int optInt(int index, int defaultValue) {
        Object value = opt(index);
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        try {
            return value == null || value == JSONObject.NULL ? defaultValue : Integer.parseInt(value.toString());
        } catch (RuntimeException ignored) {
            return defaultValue;
        }
    }

    public JSONObject getJSONObject(int index) throws JSONException {
        Object value = get(index);
        if (value instanceof JSONObject) {
            return (JSONObject) value;
        }
        throw new JSONException("JSONArray[" + index + "] is not a JSONObject.");
    }

    public JSONArray getJSONArray(int index) throws JSONException {
        Object value = get(index);
        if (value instanceof JSONArray) {
            return (JSONArray) value;
        }
        throw new JSONException("JSONArray[" + index + "] is not a JSONArray.");
    }

    public JSONObject optJSONObject(int index) {
        Object value = opt(index);
        return value instanceof JSONObject ? (JSONObject) value : null;
    }

    public Object opt(int index) {
        return index < 0 || index >= list.size() ? null : list.get(index);
    }

    @Override
    public Iterator<Object> iterator() {
        return Collections.unmodifiableList(list).iterator();
    }

    @Override
    public String toString() {
        StringBuilder result = new StringBuilder(list.size() * 12 + 2);
        appendTo(result, new IdentityHashMap<>(), 0);
        return result.toString();
    }

    public String toString(int indentFactor) {
        return toString();
    }

    void appendTo(StringBuilder result, IdentityHashMap<Object, Boolean> seen, int depth) {
        JSONObject.enterContainer(this, seen, depth);
        try {
            result.append('[');
            for (int i = 0; i < list.size(); i++) {
                if (i > 0) {
                    result.append(',');
                }
                JSONObject.appendValue(list.get(i), result, seen, depth + 1);
            }
            result.append(']');
        } finally {
            seen.remove(this);
        }
    }
}
