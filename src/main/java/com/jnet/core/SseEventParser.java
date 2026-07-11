package com.jnet.core;

/** Incremental parser for the event-stream format defined by HTML EventSource. */
final class SseEventParser {
    interface Sink {
        void onEvent(String id, String event, String data);

        default void onRetry(long retryMillis) {}
    }

    private final Sink sink;
    private final StringBuilder data = new StringBuilder();
    private String event;
    private String lastEventId;
    private boolean hasData;
    private boolean firstLine = true;

    SseEventParser(Sink sink) {
        this(sink, null);
    }

    SseEventParser(Sink sink, String initialLastEventId) {
        this.sink = sink;
        this.lastEventId = initialLastEventId;
    }

    void accept(String rawLine) {
        String line = rawLine == null ? "" : rawLine;
        if (firstLine) {
            firstLine = false;
            if (!line.isEmpty() && line.charAt(0) == '\ufeff') {
                line = line.substring(1);
            }
        }
        if (line.isEmpty()) {
            dispatch();
            return;
        }
        if (line.charAt(0) == ':') {
            return;
        }

        int colon = line.indexOf(':');
        String field = colon < 0 ? line : line.substring(0, colon);
        String value = colon < 0 ? "" : line.substring(colon + 1);
        if (!value.isEmpty() && value.charAt(0) == ' ') {
            value = value.substring(1);
        }

        switch (field) {
            case "data":
                if (hasData) {
                    data.append('\n');
                }
                data.append(value);
                hasData = true;
                break;
            case "event":
                event = value;
                break;
            case "id":
                if (value.indexOf('\0') < 0) {
                    lastEventId = value;
                }
                break;
            case "retry":
                parseRetry(value);
                break;
            default:
                break;
        }
    }

    void finish() {
        dispatch();
    }

    private void dispatch() {
        if (hasData) {
            sink.onEvent(lastEventId, event == null || event.isEmpty() ? null : event, data.toString());
        }
        data.setLength(0);
        hasData = false;
        event = null;
    }

    private void parseRetry(String value) {
        if (value.isEmpty()) {
            return;
        }
        for (int i = 0; i < value.length(); i++) {
            if (value.charAt(i) < '0' || value.charAt(i) > '9') {
                return;
            }
        }
        try {
            sink.onRetry(Long.parseLong(value));
        } catch (NumberFormatException ignored) {
            // Values outside the long range are ignored by EventSource.
        }
    }
}
