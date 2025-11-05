package com.jnet.core.org.json;

import java.util.*;

/**
 * A JSONArray is an ordered sequence of values. Its external form is a string
 * wrapped in square brackets with commas between the values. The internal form
 * is an object having <code>get</code> and <code>opt</code> methods for
 * accessing the values by index, and <code>put</code> methods for adding or
 * replacing values. The values can be any of these types:
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
 * Warning: This class contains a method clone(), but does not implement Cloneable.
 * This will cause subclasses to be non-Cloneable unless they override the clone()
 * method.
 * @author JSON.org
 * @version 2010-12-24
 */
public class JSONArray {

    private final List<Object> list;

    /**
     * Create an empty JSONArray.
     */
    public JSONArray() {
        this.list = new ArrayList<>();
    }

    /**
     * Creates a new JSONArray with contents from a string.
     * @param string A string beginning with <code>[</code> and ending with <code>]</code>.
     * @throws JSONException If the string is not a properly formatted array.
     */
    public JSONArray(String string) throws JSONException {
        this();
        // Simplified implementation - in production, would parse properly
    }

    /**
     * Get the object value associated with an index.
     * @param index must be between 0 and length() - 1
     * @return An object value.
     * @throws JSONException If there is no value for the index.
     */
    public Object get(int index) throws JSONException {
        Object o = opt(index);
        if (o == null) {
            throw new JSONException("JSONArray[" + index + "] not found.");
        }
        return o;
    }

    /**
     * Get the optional object value associated with an index.
     * @param index must be between 0 and length() - 1
     * @return      An object value, or null if there is no object at that index.
     */
    public Object opt(int index) {
        if (index < 0 || index >= this.list.size()) {
            return null;
        }
        return this.list.get(index);
    }

    /**
     * Get the JSONArray associated with an index.
     * @param index must be between 0 and length() - 1
     * @return      A JSONArray value.
     * @throws JSONException If the value is not a JSONArray or there is no value.
     */
    public JSONArray getJSONArray(int index) throws JSONException {
        Object o = get(index);
        if (o instanceof JSONArray) {
            return (JSONArray) o;
        }
        throw new JSONException("JSONArray[" + index + "] is not a JSONArray.");
    }

    /**
     * Get the JSONObject associated with an index.
     * @param index must be between 0 and length() - 1
     * @return      A JSONObject value.
     * @throws JSONException If the value is not a JSONObject or there is no value.
     */
    public JSONObject getJSONObject(int index) throws JSONException {
        Object o = get(index);
        if (o instanceof JSONObject) {
            return (JSONObject) o;
        }
        throw new JSONException("JSONArray[" + index + "] is not a JSONObject.");
    }

    /**
     * Get the length of the array.
     * @return The length of the array.
     */
    public int length() {
        return this.list.size();
    }

    /**
     * Append a boolean value.
     * @param value A boolean value.
     * @return this.
     */
    public JSONArray put(boolean value) {
        this.list.add(value ? Boolean.TRUE : Boolean.FALSE);
        return this;
    }

    /**
     * Append a double value.
     * @param value A double value.
     * @return this.
     */
    public JSONArray put(double value) {
        this.list.add(value);
        return this;
    }

    /**
     * Append an int value.
     * @param value An int value.
     * @return this.
     */
    public JSONArray put(int value) {
        this.list.add(value);
        return this;
    }

    /**
     * Append an object value.
     * @param value An object value.
     * @return this.
     */
    public JSONArray put(Object value) {
        this.list.add(value);
        return this;
    }

    @Override
    public String toString() {
        return toString(0);
    }

    /**
     * Make a pretty-printed JSON text of this JSONArray.
     * <p>
     * Warning: This method assumes that the data structure is acyclical.
     * @param indentFactor The number of spaces to add to each level of indentation.
     * @return a printable, displayable, transmittable representation of the object.
     */
    public String toString(int indentFactor) {
        StringBuilder sb = new StringBuilder();
        sb.append("[");
        boolean first = true;
        for (Object value : this.list) {
            if (!first) {
                sb.append(",");
            }
            if (value instanceof JSONObject) {
                sb.append(((JSONObject) value).toString(indentFactor + 1));
            } else if (value instanceof JSONArray) {
                sb.append(((JSONArray) value).toString(indentFactor + 1));
            } else {
                sb.append("\"");
                sb.append(JSONObject.escape(value.toString()));
                sb.append("\"");
            }
            first = false;
        }
        sb.append("]");
        return sb.toString();
    }

    /**
     * Escape a string for JSON.
     */
    private static String escape(String string) {
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
