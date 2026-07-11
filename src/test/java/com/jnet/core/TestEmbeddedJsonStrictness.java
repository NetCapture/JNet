package com.jnet.core;

import com.jnet.core.org.json.JSONArray;
import com.jnet.core.org.json.JSONException;
import com.jnet.core.org.json.JSONObject;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestEmbeddedJsonStrictness {

    @Test
    void rejectsWrongRootTypeAndTrailingInput() {
        assertThrows(JSONException.class, () -> new JSONObject("[1,2]"));
        assertThrows(JSONException.class, () -> new JSONArray("{\"a\":1}"));
        assertThrows(JSONException.class, () -> new JSONObject("{\"a\":1} trailing"));
        assertThrows(JSONException.class, () -> new JSONArray("[1] trailing"));
    }

    @Test
    void rejectsInvalidObjectKeysStringsEscapesAndNumbers() {
        assertThrows(JSONException.class, () -> new JSONObject("{1:2}"));
        assertThrows(JSONException.class, () -> new JSONObject("{\"a\":\"\\x\"}"));
        assertThrows(JSONException.class, () -> new JSONObject("{\"a\":\"line\nfeed\"}"));

        for (String invalid : Arrays.asList("[01]", "[1.]", "[.1]", "[1e]", "[+1]", "[NaN]")) {
            assertThrows(JSONException.class, () -> new JSONArray(invalid), invalid);
        }

        String oversizedNumber = "[" + "9".repeat(10_001) + "]";
        assertThrows(JSONException.class, () -> new JSONArray(oversizedNumber));
    }

    @Test
    void rejectsDuplicateObjectKeysInsteadOfSilentlyChangingTheirMeaning() {
        assertThrows(JSONException.class,
                () -> new JSONObject("{\"role\":\"user\",\"role\":\"admin\"}"));
    }

    @Test
    void preservesJsonNullAndSerializesItBack() {
        JSONObject object = new JSONObject("{\"a\":null}");

        assertTrue(object.has("a"));
        assertEquals("null", JSONObject.valueToString(object.get("a")));
        assertEquals("{\"a\":null}", object.toString());
    }

    @Test
    void optStringReturnsRawStringAndHonorsDefault() {
        JSONArray array = new JSONArray("[\"x\",null]");

        assertEquals("x", array.optString(0, "fallback"));
        assertEquals("fallback", array.optString(1, "fallback"));
        assertEquals("fallback", array.optString(99, "fallback"));
    }

    @Test
    void wrapsNestedMapsIterablesAndPrimitiveArrays() {
        Map<String, Object> nested = new LinkedHashMap<>();
        nested.put("list", Arrays.asList("x", 2));
        nested.put("array", new int[] { 3, 4 });

        JSONObject object = new JSONObject().put("nested", nested);
        JSONObject reparsed = new JSONObject(object.toString());

        JSONObject nestedObject = reparsed.getJSONObject("nested");
        assertEquals("x", nestedObject.optJSONArray("list").optString(0));
        assertEquals(4, nestedObject.optJSONArray("array").optInt(1));

        JSONArray fromIterable = new JSONArray(Collections.singleton("value"));
        assertEquals("value", fromIterable.optString(0));
    }

    @Test
    void collectionViewsCannotMutateJsonContainers() {
        JSONArray array = new JSONArray("[1]");
        Iterator<Object> iterator = array.iterator();
        iterator.next();
        assertThrows(UnsupportedOperationException.class, iterator::remove);

        JSONObject object = new JSONObject("{\"a\":1}");
        Iterator<String> keys = object.keys();
        keys.next();
        assertThrows(UnsupportedOperationException.class, keys::remove);
    }

    @Test
    void rejectsNonFiniteNumbersDuringSerialization() {
        assertThrows(JSONException.class, () -> new JSONObject().put("value", Double.NaN));
        assertThrows(JSONException.class, () -> new JSONArray().put(Double.POSITIVE_INFINITY));
    }

    @Test
    void parsesIntegralBoundaryValuesIntoTheSmallestExactJdkType() {
        JSONArray values = new JSONArray(
                "[2147483647,-2147483648,9223372036854775807,-9223372036854775808]");

        assertEquals(Integer.class, values.get(0).getClass());
        assertEquals(Integer.class, values.get(1).getClass());
        assertEquals(Long.class, values.get(2).getClass());
        assertEquals(Long.class, values.get(3).getClass());
    }
}
