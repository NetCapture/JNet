package com.jnet.core;

import java.util.Iterator;
import java.util.Map;

/** Shared validation for HTTP tokens and field values. */
final class HttpValidation {
    private HttpValidation() {
    }

    static String requireToken(String value, String label) {
        if (value == null || value.isEmpty()) {
            throw new IllegalArgumentException(label + " cannot be null or empty");
        }
        for (int i = 0; i < value.length(); i++) {
            if (!isTokenCharacter(value.charAt(i))) {
                throw new IllegalArgumentException("Invalid " + label);
            }
        }
        return value;
    }

    static String normalizeHeaderValue(String value) {
        String normalized = value == null ? "" : value;
        for (int i = 0; i < normalized.length(); i++) {
            char current = normalized.charAt(i);
            if (current > 0xff || current == '\r' || current == '\n' || current == 0x7f
                    || (current < 0x20 && current != '\t')) {
                throw new IllegalArgumentException("Header value contains a prohibited control character");
            }
        }
        return normalized;
    }

    static <T> void putCaseInsensitive(Map<String, T> fields, String name, T value) {
        for (Iterator<String> iterator = fields.keySet().iterator(); iterator.hasNext();) {
            if (iterator.next().equalsIgnoreCase(name)) {
                iterator.remove();
            }
        }
        fields.put(name, value);
    }

    private static boolean isTokenCharacter(char value) {
        if ((value >= '0' && value <= '9') || (value >= 'A' && value <= 'Z')
                || (value >= 'a' && value <= 'z')) {
            return true;
        }
        switch (value) {
            case '!':
            case '#':
            case '$':
            case '%':
            case '&':
            case '\'':
            case '*':
            case '+':
            case '-':
            case '.':
            case '^':
            case '_':
            case '`':
            case '|':
            case '~':
                return true;
            default:
                return false;
        }
    }
}
