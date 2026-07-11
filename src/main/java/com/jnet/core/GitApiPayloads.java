package com.jnet.core;

import com.jnet.core.org.json.JSONObject;

/** Shared JSON payload construction for Git repository content APIs. */
final class GitApiPayloads {
    private GitApiPayloads() {
    }

    static String content(String content, String message, String sha, String username, String email) {
        JSONObject payload = new JSONObject()
                .put("content", valueOrEmpty(content))
                .put("message", valueOrEmpty(message));
        if (!JNetUtils.isEmpty(sha)) {
            payload.put("sha", sha);
        }
        addCommitter(payload, username, email);
        return payload.toString();
    }

    static String delete(String message, String sha, String username, String email) {
        JSONObject payload = new JSONObject()
                .put("message", valueOrEmpty(message))
                .put("sha", valueOrEmpty(sha));
        addCommitter(payload, username, email);
        return payload.toString();
    }

    private static void addCommitter(JSONObject payload, String username, String email) {
        if (!JNetUtils.isEmpty(username) && !JNetUtils.isEmpty(email)) {
            payload.put("committer", new JSONObject()
                    .put("name", username)
                    .put("email", email));
        }
    }

    private static String valueOrEmpty(String value) {
        return value == null ? "" : value;
    }
}
