package com.jnet.core;

import com.jnet.core.org.json.JSONArray;
import com.jnet.core.org.json.JSONException;
import com.jnet.core.org.json.JSONObject;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

/** Shared implementation behind the GitHub and Gitee compatibility facades. */
final class GitContentsClient {
    private static final int MAX_DIRECTORY_ENTRIES = 10_000;

    interface Exchange {
        Response execute(String method, String url, Map<String, String> headers, String body) throws IOException;
    }

    static final class Item {
        final String name;
        final String path;
        final String sha;
        final long size;
        final String url;
        final String htmlUrl;
        final String gitUrl;
        final String downloadUrl;
        final String type;
        final JSONObject links;
        final String linkSelf;
        final String linkGit;
        final String linkHtml;
        final String content;
        final String encoding;

        Item(String name, String path, String sha, long size, String url, String htmlUrl,
                String gitUrl, String downloadUrl, String type, JSONObject links,
                String linkSelf, String linkGit, String linkHtml, String content, String encoding) {
            this.name = name;
            this.path = path;
            this.sha = sha;
            this.size = size;
            this.url = url;
            this.htmlUrl = htmlUrl;
            this.gitUrl = gitUrl;
            this.downloadUrl = downloadUrl;
            this.type = type;
            this.links = links;
            this.linkSelf = linkSelf;
            this.linkGit = linkGit;
            this.linkHtml = linkHtml;
            this.content = content;
            this.encoding = encoding;
        }
    }

    private static final Exchange DEFAULT_EXCHANGE = (method, url, headers, body) -> {
        Request.Builder request;
        if ("PUT".equals(method)) {
            request = JNetClient.getInstance().newPut(url);
        } else if ("DELETE".equals(method)) {
            request = JNetClient.getInstance().newDelete(url);
        } else {
            request = JNetClient.getInstance().newGet(url);
        }
        request.headers(headers);
        if (body != null) {
            request.body(body);
        }
        return request.build().newCall().execute();
    };

    private final Provider provider;
    private final Exchange exchange;

    static GitContentsClient github() {
        return github(DEFAULT_EXCHANGE);
    }

    static GitContentsClient github(Exchange exchange) {
        return new GitContentsClient(Provider.GITHUB, exchange);
    }

    static GitContentsClient gitee() {
        return gitee(DEFAULT_EXCHANGE);
    }

    static GitContentsClient gitee(Exchange exchange) {
        return new GitContentsClient(Provider.GITEE, exchange);
    }

    private GitContentsClient(Provider provider, Exchange exchange) {
        this.provider = provider;
        this.exchange = exchange;
    }

    String append(String owner, String repo, String path, String token, String content,
            String message, String username, String email) {
        if (JNetUtils.isBlank(token)) {
            return "";
        }
        try {
            Map<String, Item> items = getItems(owner, repo, path, token);
            Item item = items.get(path);
            String current = item != null && "base64".equalsIgnoreCase(item.encoding)
                    ? JNetUtils.decodeBase64(item.content)
                    : "";
            return putContent(owner, repo, path, token, current + "\r\n" + content,
                    message, username, email, items);
        } catch (IOException | RuntimeException ignored) {
            return "";
        }
    }

    String updateContent(String owner, String repo, String path, String token, String content,
            String message, String username, String email) {
        if (JNetUtils.isBlank(token)) {
            return "";
        }
        try {
            Map<String, Item> items = getItems(owner, repo, path, token);
            return putContent(owner, repo, path, token, content, message, username, email, items);
        } catch (IOException | RuntimeException ignored) {
            return "";
        }
    }

    String deleteFiles(String owner, String repo, String[] paths, String token, String message) {
        if (paths == null || paths.length == 0 || JNetUtils.isBlank(token)) {
            return "";
        }
        StringBuilder result = new StringBuilder();
        for (String path : paths) {
            if (result.length() > 0) {
                result.append(',');
            }
            result.append(deleteFile(owner, repo, path, token, message, "", ""));
        }
        return result.toString();
    }

    String deleteFile(String owner, String repo, String path, String token, String message,
            String username, String email) {
        if (JNetUtils.isBlank(token)) {
            return "";
        }
        Item item = getItems(owner, repo, path, token).get(path);
        return item == null ? "" : deleteFileBySha(
                provider.rawContentsUrl(owner, repo, path), item.sha, token, message, username, email);
    }

    void deleteDirectories(String owner, String repo, String[] paths, String token, String message) {
        if (paths == null) {
            return;
        }
        for (String path : paths) {
            deleteDirectory(owner, repo, path, token, message, "", "");
        }
    }

    void deleteDirectory(String owner, String repo, String path, String token, String message,
            String username, String email) {
        if (JNetUtils.isBlank(token)) {
            return;
        }
        Deque<String> pending = new ArrayDeque<>();
        Set<String> visited = new HashSet<>();
        pending.add(path == null ? "" : path);
        int entries = 0;

        while (!pending.isEmpty()) {
            String current = pending.removeLast();
            if (!visited.add(normalizeTraversalPath(current))) {
                continue;
            }
            Map<String, Item> items = getItems(owner, repo, current, token);
            for (Item item : items.values()) {
                if (item == null || item.path == null || ++entries > MAX_DIRECTORY_ENTRIES) {
                    if (entries > MAX_DIRECTORY_ENTRIES) {
                        return;
                    }
                    continue;
                }
                String childPath = normalizeTraversalPath(item.path);
                if (isLeafEntry(item.type)) {
                    deleteFileBySha(provider.rawContentsUrl(owner, repo, childPath), item.sha,
                            token, message, username, email);
                } else if ("dir".equalsIgnoreCase(item.type) && !visited.contains(childPath)) {
                    pending.addLast(childPath);
                }
            }
        }
    }

    String createFile(boolean encodeContent, String owner, String repo, String path, String token,
            String content, String message, String username, String email) {
        if (JNetUtils.isBlank(token) || Thread.currentThread().isInterrupted()) {
            return "";
        }
        try {
            Item existing = getItems(owner, repo, path, token).get(path);
            if (Thread.currentThread().isInterrupted()) {
                return "";
            }
            if (existing != null && !JNetUtils.isEmpty(existing.downloadUrl)) {
                return existing.downloadUrl;
            }
            return createFileDirect(encodeContent, owner, repo, path, token,
                    content, message, username, email);
        } catch (RuntimeException ignored) {
            return "";
        }
    }

    String getContent(String owner, String repo, String path, String token) {
        Item item = getItems(owner, repo, path, token).get(path);
        if (item != null && "base64".equalsIgnoreCase(item.encoding)) {
            return JNetUtils.decodeBase64(item.content);
        }
        return "";
    }

    Map<String, Item> getItems(String owner, String repo, String path, String token) {
        Map<String, Item> result = new HashMap<>();
        try {
            Response response = exchange.execute("GET", provider.contentsUrl(owner, repo, path, token),
                    provider.headers(token), null);
            if (!isSuccessful(response.getCode())) {
                return result;
            }
            String body = response.getBody();
            if (JNetUtils.isEmpty(body) || JNetUtils.isEmpty(body.trim())) {
                return result;
            }
            try {
                result.put(path, item(new JSONObject(body)));
            } catch (JSONException objectFailure) {
                JSONArray array = new JSONArray(body);
                for (int i = 0; i < array.length(); i++) {
                    Object value = array.opt(i);
                    if (value instanceof JSONObject) {
                        Item item = item((JSONObject) value);
                        result.put(item.path, item);
                    }
                }
            }
        } catch (IOException | RuntimeException ignored) {
            return result;
        }
        return result;
    }

    List<Item> listDirectory(String owner, String repo, String path, String token) {
        Map<String, Item> items = getItems(owner, repo, path, token);
        List<Item> result = new ArrayList<>();
        Item single = items.get(path);
        if (single != null) {
            result.add(single);
        } else {
            result.addAll(items.values());
        }
        return result;
    }

    String getRepositoryInfo(String owner, String repo, String token) {
        return getBody(provider.repositoryUrl(owner, repo, token), token);
    }

    String getCommits(String owner, String repo, String path, String token, int page, int perPage) {
        return getBody(provider.commitsUrl(owner, repo, path, token, page, perPage), token);
    }

    String getBranches(String owner, String repo, String token) {
        return getBody(provider.branchesUrl(owner, repo, token), token);
    }

    boolean fileExists(String owner, String repo, String path, String token) {
        try {
            return exchange.execute("GET", provider.contentsUrl(owner, repo, path, token),
                    provider.headers(token), null).getCode() == 200;
        } catch (IOException ignored) {
            return false;
        }
    }

    String getRateLimit(String token) {
        return getBody(provider.rateLimitUrl(token), token);
    }

    CompletableFuture<List<String>> batchCreateFiles(String owner, String repo,
            List<Map<String, String>> files, String token, String message) {
        if (files == null) {
            throw new IllegalArgumentException("files cannot be null");
        }
        List<BatchFile> batch = new ArrayList<>(files.size());
        for (int i = 0; i < files.size(); i++) {
            Map<String, String> file = files.get(i);
            if (file == null) {
                throw new IllegalArgumentException("files[" + i + "] cannot be null");
            }
            String path = file.get("path");
            String content = file.get("content");
            if (JNetUtils.isBlank(path)) {
                throw new IllegalArgumentException("files[" + i + "].path cannot be blank");
            }
            if (content == null) {
                throw new IllegalArgumentException("files[" + i + "].content cannot be null");
            }
            batch.add(new BatchFile(path, content));
        }
        return AsyncExecutor.submit(() -> {
            List<String> created = new ArrayList<>(batch.size());
            for (BatchFile file : batch) {
                if (Thread.currentThread().isInterrupted()) {
                    throw new InterruptedException("Git batch creation canceled");
                }
                created.add(createFile(true, owner, repo, file.path, token, file.content,
                        message, "", ""));
            }
            return created;
        });
    }

    Map<String, String> headers(String token) {
        return provider.headers(token);
    }

    private String createFileDirect(boolean encodeContent, String owner, String repo, String path,
            String token, String content, String message, String username, String email) {
        String encoded = encodeContent ? JNetUtils.encodeBase64(content) : content;
        String payload = GitApiPayloads.content(encoded, message, null, username, email);
        try {
            Response response = exchange.execute("PUT", provider.contentsUrl(owner, repo, path, token),
                    provider.headers(token), payload);
            String body = response.getBody();
            if (JNetUtils.isEmpty(body)) {
                return "";
            }
            JSONObject object = new JSONObject(body);
            JSONObject responseContent = object.optJSONObject("content");
            return responseContent == null ? "" : responseContent.optString("download_url", "");
        } catch (IOException | JSONException ignored) {
            return "";
        }
    }

    private String putContent(String owner, String repo, String path, String token, String content,
            String message, String username, String email, Map<String, Item> items) throws IOException {
        if (items.isEmpty()) {
            return createFileDirect(true, owner, repo, path, token, content, message, username, email);
        }
        Item item = items.get(path);
        if (item == null || JNetUtils.isEmpty(item.sha)) {
            return "";
        }
        String payload = GitApiPayloads.content(
                JNetUtils.encodeBase64(content), message, item.sha, username, email);
        return exchange.execute("PUT", provider.contentsUrl(owner, repo, path, token),
                provider.headers(token), payload).getBody();
    }

    private String deleteFileBySha(String url, String sha, String token, String message,
            String username, String email) {
        if (JNetUtils.isEmpty(url) || JNetUtils.isEmpty(sha) || JNetUtils.isEmpty(token)) {
            return "";
        }
        try {
            Response response = exchange.execute("DELETE", provider.authenticate(url, token),
                    provider.headers(token), GitApiPayloads.delete(message, sha, username, email));
            return response.getBody();
        } catch (IOException ignored) {
            return "";
        }
    }

    private String getBody(String url, String token) {
        try {
            return exchange.execute("GET", url, provider.headers(token), null).getBody();
        } catch (IOException ignored) {
            return "";
        }
    }

    private Item item(JSONObject object) {
        JSONObject links = provider.parseLinks ? object.optJSONObject("_links") : null;
        return new Item(
                object.optString("name", null),
                object.optString("path", null),
                object.optString("sha", null),
                object.optLong("size", -1L),
                object.optString("url", null),
                object.optString("html_url", null),
                object.optString("git_url", null),
                object.optString("download_url", null),
                object.optString("type", null),
                links,
                links == null ? "" : links.optString("self", null),
                links == null ? "" : links.optString("git", null),
                links == null ? "" : links.optString("html", null),
                object.optString("content", null),
                object.optString("encoding", null));
    }

    private static boolean isLeafEntry(String type) {
        return "file".equalsIgnoreCase(type)
                || "symlink".equalsIgnoreCase(type)
                || "submodule".equalsIgnoreCase(type);
    }

    private static boolean isSuccessful(int statusCode) {
        return statusCode >= 200 && statusCode < 300;
    }

    private static final class BatchFile {
        private final String path;
        private final String content;

        private BatchFile(String path, String content) {
            this.path = path;
            this.content = content;
        }
    }

    private static String normalizeTraversalPath(String path) {
        if (path == null || path.isEmpty()) {
            return "";
        }
        return path.startsWith("/") ? path : "/" + path;
    }

    private enum Provider {
        GITHUB("https://api.github.com/repos", "https://api.github.com/rate_limit", false, true),
        GITEE("https://gitee.com/api/v5/repos", "https://gitee.com/api/v5/rate_limit", true, false);

        private final String repositoriesBase;
        private final String rateLimit;
        private final boolean tokenInQuery;
        private final boolean parseLinks;

        Provider(String repositoriesBase, String rateLimit, boolean tokenInQuery,
                boolean parseLinks) {
            this.repositoriesBase = repositoriesBase;
            this.rateLimit = rateLimit;
            this.tokenInQuery = tokenInQuery;
            this.parseLinks = parseLinks;
        }

        String contentsUrl(String owner, String repo, String path, String token) {
            return authenticate(rawContentsUrl(owner, repo, path), token);
        }

        String rawContentsUrl(String owner, String repo, String path) {
            return repositoryBase(owner, repo) + "/contents" + encodePath(path);
        }

        String repositoryUrl(String owner, String repo, String token) {
            return authenticate(repositoryBase(owner, repo), token);
        }

        String commitsUrl(String owner, String repo, String path, String token, int page, int perPage) {
            String queryPath = tokenInQuery ? nullToEmpty(path) : normalizePath(path);
            String requestPath = JNetUtils.urlEncode(queryPath);
            return authenticate(String.format("%s/commits?path=%s&page=%d&per_page=%d",
                    repositoryBase(owner, repo), requestPath, page, perPage), token);
        }

        String branchesUrl(String owner, String repo, String token) {
            return authenticate(repositoryBase(owner, repo) + "/branches", token);
        }

        String rateLimitUrl(String token) {
            return authenticate(rateLimit, token);
        }

        String authenticate(String url, String token) {
            if (!tokenInQuery || JNetUtils.isBlank(token)) {
                return url;
            }
            return url + (url.indexOf('?') >= 0 ? '&' : '?')
                    + "access_token=" + JNetUtils.urlEncode(token);
        }

        Map<String, String> headers(String token) {
            Map<String, String> headers = new HashMap<>();
            headers.put("User-Agent", tokenInQuery ? "Gitee createFile By Java" : "Github createFile By Java");
            headers.put("Content-Type", "application/json; charset=UTF-8");
            headers.put("Accept", tokenInQuery ? "application/json" : "application/vnd.github.v3+json");
            headers.put("accept-language", "zh-CN,zh;q=0.9,en;q=0.8");
            if (!tokenInQuery && !JNetUtils.isBlank(token)) {
                headers.put("Authorization", "token " + token);
            }
            return headers;
        }

        private static String normalizePath(String path) {
            if (path == null || path.isEmpty()) {
                return "";
            }
            return path.startsWith("/") ? path : "/" + path;
        }

        private String repositoryBase(String owner, String repo) {
            return repositoriesBase + '/' + encodePathSegment(owner) + '/' + encodePathSegment(repo);
        }

        private static String nullToEmpty(String value) {
            return value == null ? "" : value;
        }

        private static String encodePath(String path) {
            String normalized = normalizePath(path);
            if (normalized.isEmpty()) {
                return "";
            }
            StringBuilder encoded = new StringBuilder(normalized.length() + 8);
            for (String segment : normalized.substring(1).split("/", -1)) {
                if (segment.isEmpty()) {
                    continue;
                }
                encoded.append('/').append(encodePathSegment(segment));
            }
            return encoded.toString();
        }

        private static String encodePathSegment(String value) {
            if (value == null || value.isEmpty()) {
                throw new IllegalArgumentException("Repository path segments cannot be null or empty");
            }
            byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
            StringBuilder encoded = new StringBuilder(bytes.length);
            char[] hex = "0123456789ABCDEF".toCharArray();
            for (byte current : bytes) {
                int unsigned = current & 0xff;
                if ((unsigned >= 'a' && unsigned <= 'z')
                        || (unsigned >= 'A' && unsigned <= 'Z')
                        || (unsigned >= '0' && unsigned <= '9')
                        || unsigned == '-' || unsigned == '.' || unsigned == '_'
                        || unsigned == '~') {
                    encoded.append((char) unsigned);
                } else {
                    encoded.append('%')
                            .append(hex[unsigned >>> 4])
                            .append(hex[unsigned & 0x0f]);
                }
            }
            return encoded.toString();
        }
    }
}
