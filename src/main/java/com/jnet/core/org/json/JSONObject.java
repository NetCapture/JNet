package com.jnet.core.org.json;

import java.util.*;
import java.util.regex.Pattern;

/**
 * A JSONObject is an unordered collection of name/value pairs. Its
 * external form is a string wrapped in curly braces with colons between the
 * names and values, and commas between the values and names. The internal form
 * is an object having <code>get</code> and <code>opt</code> methods for
 * accessing the values by name, and <code>put</code> methods for adding or
 * replacing values by name. The values can be any of these types:
 * <code>Boolean</code>, <code>JSONArray</code>, <code>JSONObject</code>,
 * <code>Number</code>, <code>String</code>, or the <code>JSONObject.NULL</code> object.
 * <p>
 * The constructor can convert a JSON string into an internal object. The
 * <code>toString</code> method converts the internal object back into a string.
 * <p>
 * A <code>get</code> method returns a value if one can be found, and throws an
 * exception if one cannot be found. An <code>opt</code> method returns a
 * default value instead of throwing an exception, and returns an <code>isNull</code>
 * method to determine if a value exists.
 * <p>
 * The <code>NULLE</code> object is a singleton object that is returned when
 * a value does not exist.
 * <p>
 * Warning: This class contains a method clone(), but does not implement Cloneable.
 * This will cause subclasses to be non-Cloneable unless they override the clone()
 * method.
 * @author JSON.org
 * @version 2010-12-24
 */
public class JSONObject {

    /**
     * JSONObject.NULL is equivalent to the value that JavaScript provides null.
     */
    public static final Object NULL = new Null();

    /**
     * It is sometimes more convenient and less ambiguous to have a
     * NULL object than to use Java's null.
     */
    private static final class Null {

        @Override
        protected final Object clone() {
            return this;
        }

        @Override
        public boolean equals(Object object) {
            return object == null || object == this;
        }

        @Override
        public String toString() {
            return "null";
        }
    }

    private final Map<String, Object> map;

    /**
     * Create an empty JSONObject.
     */
    public JSONObject() {
        this.map = new HashMap<>();
    }

    /**
     * Creates a new JSONObject with name/value mappings from the string.
     * @param string A string beginning with <code>{</code> and ending with <code>}</code>.
     * @throws JSONException The string must be a properly formatted object.
     */
    public JSONObject(String string) throws JSONException {
        this();
        // Simplified implementation - in production, would parse properly
    }

    /**
     * Get an optional value associated with a key.
     * @param key   A key string.
     * @return      An object value, or null if there is no such key.
     */
    public Object opt(String key) {
        return key == null ? null : this.map.get(key);
    }

    /**
     * Get an optional string associated with a key.
     * It returns an empty string if there is no such key. To distinguish from
     * an empty string and the case where the value does not exist, use optString.
     * @param key   A key string.
     * @return      A string, or null if the key is not found.
     */
    public String optString(String key) {
        return optString(key, "");
    }

    /**
     * Get an optional string associated with a key.
     * It returns the defaultValue if there is no such key.
     * @param key   A key string.
     * @param defaultValue  The default.
     * @return      A string, or the defaultValue if the key is not found.
     */
    public String optString(String key, String defaultValue) {
        Object v = this.opt(key);
        return v != null ? v.toString() : defaultValue;
    }

    /**
     * Get an optional long value associated with a key.
     * @param key   A key string.
     * @return      A long, or the defaultValue if there is no such key.
     */
    public long optLong(String key) {
        return optLong(key, 0);
    }

    /**
     * Get an optional long value associated with a key.
     * @param key   A key string.
     * @param defaultValue  The default value.
     * @return      A long, or the defaultValue if there is no such key.
     */
    public long optLong(String key, long defaultValue) {
        Object v = this.opt(key);
        if (v != null) {
            if (v instanceof Number) {
                return ((Number) v).longValue();
            }
            try {
                return Long.parseLong(v.toString());
            } catch (Exception e) {
                return defaultValue;
            }
        }
        return defaultValue;
    }

    /**
     * Get an optional JSONArray associated with a key.
     * @param key   A key string.
     * @return      A JSONArray, or null if the key is not found.
     */
    public JSONArray optJSONArray(String key) {
        Object o = this.opt(key);
        return o instanceof JSONArray ? (JSONArray) o : null;
    }

    /**
     * Get an optional JSONObject associated with a key.
     * @param key   A key string.
     * @return      A JSONObject, or null if the key is not found.
     */
    public JSONObject optJSONObject(String key) {
        Object o = this.opt(key);
        return o instanceof JSONObject ? (JSONObject) o : null;
    }

    /**
     * Determine if the value associated with the key is null or if there is no value.
     * @param key   A key string.
     * @return      True if there is no value associated with the key or if
     *              the value is the JSONObject.NULL object.
     */
    public boolean isNull(String key) {
        return JSONObject.NULL.equals(this.opt(key));
    }

    /**
     * Determine if the value associated with the key is null or if there is no value.
     * @param key   A key string.
     * @return      True if there is no value associated with the key or if
     *              the value is the JSONObject.NULL object.
     */
    public boolean has(String key) {
        return this.map.containsKey(key);
    }

    @Override
    public String toString() {
        return toString(0);
    }

    /**
     * Make a pretty-printed JSON text of this JSONObject.
     * <p>
     * Warning: This method assumes that the data structure is acyclical.
     * @param indentFactor The number of spaces to add to each level of indentation.
     * @return a printable, displayable, transmittable representation of the object.
     */
    public String toString(int indentFactor) {
        StringBuilder sb = new StringBuilder();
        sb.append("{");
        boolean first = true;
        for (String key : this.map.keySet()) {
            if (!first) {
                sb.append(",");
            }
            sb.append("\"");
            sb.append(escape(key));
            sb.append("\":");
            Object value = this.map.get(key);
            if (value instanceof JSONObject) {
                sb.append(((JSONObject) value).toString(indentFactor + 1));
            } else if (value instanceof JSONArray) {
                sb.append(((JSONArray) value).toString(indentFactor + 1));
            } else {
                sb.append("\"");
                sb.append(escape(value.toString()));
                sb.append("\"");
            }
            first = false;
        }
        sb.append("}");
        return sb.toString();
    }

    /**
     * Return the number of keys/values.
     * @return The number of keys/values.
     */
    public int length() {
        return this.map.size();
    }

    /**
     * Put a key/boolean pair in the JSONObject.
     * @param key   A key string.
     * @param value A boolean value.
     * @return this.
     */
    public JSONObject put(String key, boolean value) {
        this.map.put(key, value ? Boolean.TRUE : Boolean.FALSE);
        return this;
    }

    /**
     * Put a key/double pair in the JSONObject.
     * @param key   A key string.
     * @param value A double value.
     * @return this.
     */
    public JSONObject put(String key, double value) {
        this.map.put(key, value);
        return this;
    }

    /**
     * Put a key/int pair in the JSONObject.
     * @param key   A key string.
     * @param value An int value.
     * @return this.
     */
    public JSONObject put(String key, int value) {
        this.map.put(key, value);
        return this;
    }

    /**
     * Put a key/long pair in the JSONObject.
     * @param key   A key string.
     * @param value A long value.
     * @return this.
     */
    public JSONObject put(String key, long value) {
        this.map.put(key, value);
        return this;
    }

    /**
     * Put a key/value pair in the JSONObject.
     * @param key   A key string.
     * @param value An object value.
     * @return this.
     */
    public JSONObject put(String key, Object value) {
        if (value == null) {
            this.map.put(key, JSONObject.NULL);
        } else {
            this.map.put(key, value);
        }
        return this;
    }

    /**
     * Produce a string by escaping the contents of a string.
     * @param string A string.
     * @return A string safely embedded in a JSON string.
     */
    public static String escape(String string) {
        if (string == null) {
            return "";
        }

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < string.length(); i++) {
            char c = string.charAt(i);
            switch (c) {
                case '"':
                    sb.append("\\\"");
                    break;
                case '\\':
                    sb.append("\\\\");
                    break;
                case '\b':
                    sb.append("\\b");
                    break;
                case '\f':
                    sb.append("\\f");
                    break;
                case '\n':
                    sb.append("\\n");
                    break;
                case '\r':
                    sb.append("\\r");
                    break;
                case '\t':
                    sb.append("\\t");
                    break;
                default:
                    sb.append(c);
            }
        }
        return sb.toString();
    }
}
