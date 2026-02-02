package com.jnet.graphql;

import com.jnet.core.JNet;
import java.util.HashMap;
import java.util.Map;

/**
 * GraphQL Client Facade
 * Provides a simple, fluent API for sending GraphQL queries and mutations.
 *
 * <p>Usage:</p>
 * <pre>{@code
 * String response = JGraphQL.query("https://api.example.com/graphql",
 *     "{ user(id: 1) { name } }");
 *
 * String response = JGraphQL.builder()
 *     .url("https://api.example.com/graphql")
 *     .query("query getUser($id: ID!) { user(id: $id) { name } }")
 *     .variable("id", "1")
 *     .execute();
 * }</pre>
 */
public final class JGraphQL {

    private JGraphQL() {}

    /**
     * Send a simple GraphQL query
     */
    public static String query(String url, String query) {
        return builder().url(url).query(query).execute();
    }

    /**
     * Create a new GraphQL request builder
     */
    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String url;
        private String query;
        private String operationName;
        private final Map<String, Object> variables = new HashMap<>();
        private final Map<String, String> headers = new HashMap<>();

        public Builder url(String url) {
            this.url = url;
            return this;
        }

        public Builder query(String query) {
            this.query = query;
            return this;
        }

        public Builder operationName(String operationName) {
            this.operationName = operationName;
            return this;
        }

        public Builder variable(String key, Object value) {
            this.variables.put(key, value);
            return this;
        }

        public Builder variables(Map<String, Object> variables) {
            if (variables != null) {
                this.variables.putAll(variables);
            }
            return this;
        }

        public Builder header(String key, String value) {
            this.headers.put(key, value);
            return this;
        }

        public String execute() {
            if (url == null || query == null) {
                throw new IllegalArgumentException("URL and Query are required");
            }

            Map<String, Object> payload = new HashMap<>();
            payload.put("query", query);

            if (operationName != null) {
                payload.put("operationName", operationName);
            }

            if (!variables.isEmpty()) {
                payload.put("variables", variables);
            }

            return JNet.postJson(url, payload, headers.isEmpty() ? null : headers);
        }
    }
}
