package com.jnet.multipart;

/** Internal validation and escaping for multipart header values. */
final class MultipartValidation {
    private static final String HTTP_TOKEN_CHARS = "!#$%&'*+-.^_`|~";
    private static final String MIME_BOUNDARY_CHARS = "'()+_,-./:=?";

    private MultipartValidation() {
    }

    static String quoteParameter(String value, String label) {
        String checked = requireHeaderValue(value, label);
        if (checked.isEmpty()) {
            throw new IllegalArgumentException(label + " cannot be empty");
        }
        return checked.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    static String requireHeaderValue(String value, String label) {
        if (value == null) {
            throw new IllegalArgumentException(label + " cannot be null");
        }
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            if (Character.isISOControl(ch)) {
                throw new IllegalArgumentException(label + " contains a control character");
            }
        }
        return value;
    }

    static String requireBoundary(String boundary) {
        if (boundary == null || boundary.isEmpty()) {
            throw new IllegalArgumentException("Boundary cannot be null or empty");
        }
        if (boundary.length() > 70) {
            throw new IllegalArgumentException("Boundary cannot exceed 70 characters");
        }
        for (int i = 0; i < boundary.length(); i++) {
            char ch = boundary.charAt(i);
            if (!isBoundaryCharacter(ch) || ch == ' ' && i == boundary.length() - 1) {
                throw new IllegalArgumentException("Boundary contains an invalid character");
            }
        }
        return boundary;
    }

    static boolean isHttpToken(String value) {
        for (int i = 0; i < value.length(); i++) {
            if (!isHttpTokenCharacter(value.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    private static boolean isBoundaryCharacter(char ch) {
        return ch == ' '
                || ch >= '0' && ch <= '9'
                || ch >= 'A' && ch <= 'Z'
                || ch >= 'a' && ch <= 'z'
                || MIME_BOUNDARY_CHARS.indexOf(ch) >= 0;
    }

    private static boolean isHttpTokenCharacter(char ch) {
        return ch >= '0' && ch <= '9'
                || ch >= 'A' && ch <= 'Z'
                || ch >= 'a' && ch <= 'z'
                || HTTP_TOKEN_CHARS.indexOf(ch) >= 0;
    }
}
