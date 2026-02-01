package com.jnet.core;

import com.jnet.core.org.json.JSONArray;
import com.jnet.core.org.json.JSONException;
import com.jnet.core.org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * github API操作工具类.
 * <p>
 * github api: https://developer.github.com/v3/repos/contents/
 * </p>
 * <p>
 * Copyright © 2020 sanbo Inc. All rights reserved.
 * </p>
 * <p>
 * Create: 2020-12-09 15:13:01
 * </p>
 *
 * @version 3.0.0
 * @author sanbo
 */
public class GithubHelper {

    private static final int DEF_TIMEOUT = 50 * 1000;
    private static String token = System.getenv("GITHUB_TOKEN");

    public static void setGlobalToken(String _token) {
        token = _token;
    }

    /**
     * 获取项目信息 - 已由 getRepositoryInfo 方法实现
     */

    public static String append(String owner, String repo, String path, String contentWillBase64, String commitMsg) {
        return append(owner, repo, path, token, contentWillBase64, commitMsg);
    }

    public static String append(String owner, String repo, String path, String token, String contentWillBase64,
            String commitMsg) {
        return append(owner, repo, path, token, contentWillBase64, commitMsg, "", "");
    }

    public static String append(String owner, String repo, String path, String token, String contentWillBase64,
            String commitMsg, String username, String email) {
        StringBuilder sb = new StringBuilder();
        sb.append(getContent(owner, repo, path, token)).append("\r\n").append(contentWillBase64);
        return updateContent(owner, repo, path, token, sb.toString(), commitMsg, username, email);
    }

    public static String updateContent(String owner, String repo, String path, String contentWillBase64,
            String commitMsg) {
        return updateContent(owner, repo, path, token, contentWillBase64, commitMsg);
    }

    public static String updateContent(String owner, String repo, String path, String token, String contentWillBase64,
            String commitMsg) {
        return updateContent(owner, repo, path, token, contentWillBase64, commitMsg, "", "");
    }

    public static String updateContent(String owner, String repo, String path, String token, String contentWillBase64,
            String commitMsg, String username, String email) {
        try {
            String content = JNetUtils.encodeBase64(contentWillBase64);
            String normalizedPath = normalizePath(path);
            String base = "https://api.github.com/repos/%s/%s/contents%s";
            String uploadUrl = String.format(base, owner, repo, normalizedPath);
            Map<String, ShaInfo> shas = getSha(owner, repo, path, token);

            if (shas == null || shas.isEmpty()) {
                return realCreateFileInternal(owner, repo, path, token, contentWillBase64, commitMsg, username,
                        email);
            }
            ShaInfo s = shas.get(path);
            if (s == null) {
                return "";
            }
            String sha = s.sha;
            if (!JNetUtils.isEmpty(sha)) {
                String hasUserInfoBase = "{\"content\":\"%s\",\"message\":\"%s\" ,\"sha\":\"%s\" ,\"committer\":{ \"name\":\"%s\",\"email\":\"%s\" }}";
                String hasNoUserInfoBase = "{\"content\":\"%s\",\"message\":\"%s\", \"sha\":\"%s\" }";
                String data = String.format(hasNoUserInfoBase, content, commitMsg, sha);
                if (!JNetUtils.isEmpty(username) && !JNetUtils.isEmpty(email)) {
                    data = String.format(hasUserInfoBase, content, commitMsg, sha, username, email);
                }
                Response resp = JNetClient.getInstance()
                        .newPut(uploadUrl)
                        .headers(getHttpHeader(token))
                        .body(data)
                        .build()
                        .newCall()
                        .execute();

                return resp.getBody();
            }
        } catch (IOException e) {
            System.err.println("IO error in updateContent: " + e.getMessage());
        } catch (Throwable e) {
            System.err.println("Unexpected error in updateContent: " + e.getMessage());
        }
        return "";
    }

    public static String deleteFile(String owner, String repo, String[] paths, String commitMsg) {
        return deleteFile(owner, repo, paths, token, commitMsg);
    }

    public static String deleteFile(String owner, String repo, String[] paths, String token, String commitMsg) {
        if (paths == null || paths.length < 1) {
            return "";
        }
        StringBuilder results = new StringBuilder();
        for (String path : paths) {
            String result = deleteFile(owner, repo, path, token, commitMsg);
            if (results.length() > 0) {
                results.append(",");
            }
            results.append(result);
        }
        return results.toString();
    }

    public static String deleteFile(String owner, String repo, String path, String commitMsg) {
        return deleteFile(owner, repo, path, token, commitMsg);
    }

    public static String deleteFile(String owner, String repo, String path, String token, String commitMsg) {
        return deleteFile(owner, repo, path, token, commitMsg, "", "");
    }

    public static String deleteFile(String owner, String repo, String path, String token, String commitMsg,
            String username, String email) {
        String normalizedPath = normalizePath(path);
        String base = "https://api.github.com/repos/%s/%s/contents%s";
        String uploadUrl = String.format(base, owner, repo, normalizedPath);
        Map<String, ShaInfo> shas = getSha(owner, repo, path, token);
        if (shas == null || shas.isEmpty()) {
            return "";
        }
        ShaInfo s = shas.get(path);
        if (s == null) {
            return "";
        }
        return realDelFileBySha(uploadUrl, s.sha, token, commitMsg, username, email);
    }

    public static void deleteDir(String owner, String repo, String[] paths, String token, String commitMsg) {
        if (paths == null || paths.length < 1) {
            return;
        }
        for (String path : paths) {
            deleteDir(owner, repo, path, token, commitMsg, "", "");
        }
    }

    public static void deleteDir(String owner, String repo, String path, String commitMsg) {
        deleteDir(owner, repo, path, token, commitMsg);
    }

    public static void deleteDir(String owner, String repo, String path, String token, String commitMsg) {
        deleteDir(owner, repo, path, token, commitMsg, "", "");
    }

    public static void deleteDir(String owner, String repo, String path, String token, String commitMsg,
            String username, String email) {
        String normalizedPath = normalizePath(path);
        String base = "https://api.github.com/repos/%s/%s/contents%s";
        Map<String, ShaInfo> shas = getSha(owner, repo, path, token);
        if (shas == null || shas.isEmpty()) {
            System.out.println("shas is null,will return");
            return;
        }

        for (Map.Entry<String, ShaInfo> entry : shas.entrySet()) {
            ShaInfo s = entry.getValue();
            if (s != null) {
                String ps = s.path.startsWith("/") ? (s.path) : ("/" + s.path);
                if ("file".equalsIgnoreCase(s.type)) {
                    realDelFileBySha(String.format(base, owner, repo, ps), s.sha, token, commitMsg, username,
                            email);
                } else {
                    deleteDir(owner, repo, ps, token, commitMsg, username, email);
                }
            }
        }
    }

    private static String realDelFileBySha(String url, String sha, String token, String commitMsg, String username,
            String email) {
        if (JNetUtils.isEmpty(url) || JNetUtils.isEmpty(sha) || JNetUtils.isEmpty(token)) {
            return "";
        }
        String hasUserInfoBase = "{\"message\":\"%s\" ,\"sha\":\"%s\" ,\"committer\":{ \"name\":\"%s\",\"email\":\"%s\" }}";
        String hasNoUserInfoBase = "{\"message\":\"%s\", \"sha\":\"%s\" }";
        String data = String.format(hasNoUserInfoBase, commitMsg, sha);
        if (!JNetUtils.isEmpty(username) && !JNetUtils.isEmpty(email)) {
            data = String.format(hasUserInfoBase, commitMsg, sha, username, email);
        }
        try {
            Response resp = JNetClient.getInstance()
                    .newDelete(url)
                    .headers(getHttpHeader(token))
                    .body(data)
                    .build()
                    .newCall()
                    .execute();

            return resp.getBody();
        } catch (IOException e) {
            System.err.println("IO error: " + e.getMessage());
        }
        return "";
    }

    public static String createFile(String owner, String repo, String path, String token, String contentWillBase64,
            String commitMsg) {
        return createFile(true, owner, repo, path, token, contentWillBase64, commitMsg, "", "");
    }

    public static String createFile(String owner, String repo, String path, String contentWillBase64,
            String commitMsg) {
        return createFile(true, owner, repo, path, token, contentWillBase64, commitMsg, "", "");
    }

    public static String createFile(String owner, String repo, String path, File file, String commitMsg) {
        return createFile(false, owner, repo, path, token, FileUtils.getBase64FromFile(file.getAbsolutePath()),
                commitMsg, "", "");
    }

    public static String createFile(String owner, String repo, String path, String token, File file, String commitMsg) {
        return createFile(false, owner, repo, path, token, FileUtils.getBase64FromFile(file.getAbsolutePath()),
                commitMsg, "", "");
    }

    public static String createFile(boolean isNeedBase64, String owner, String repo, String path, String uploadContent,
            String commitMsg, String username, String email) {
        return createFile(isNeedBase64, owner, repo, path, token, uploadContent, commitMsg, username, email);
    }

    public static String createFile(boolean isNeedBase64, String owner, String repo, String path, String token,
            String uploadContent, String commitMsg, String username, String email) {
        try {
            Map<String, ShaInfo> shas = getSha(owner, repo, path, token);
            if (shas != null && !shas.isEmpty()) {
                ShaInfo s = shas.get(path);
                if (s != null && !JNetUtils.isEmpty(s.download_url)) {
                    System.out.println("已经有了文件,路径: " + s.download_url);
                    return s.download_url;
                }
            }

            return realCreateFileInternal(owner, repo, path, token, uploadContent, commitMsg, username,
                    email);

        } catch (Throwable e) {
            System.err.println("Unexpected error in createFile: " + e.getMessage());
        }

        return "";
    }

    private static String realCreateFileInternal(String owner, String repo, String path,
            String token, String uploadContent, String commitMsg, String username, String email) {
        String content = JNetUtils.encodeBase64(uploadContent);
        String normalizedPath = normalizePath(path);
        String base = "https://api.github.com/repos/%s/%s/contents%s";
        String uploadUrl = String.format(base, owner, repo, normalizedPath);

        String hasUserInfoBase = "{\"content\":\"%s\",\"message\":\"%s\" ,\"committer\":{ \"name\":\"%s\",\"email\":\"%s\" }}";
        String hasNoUserInfoBase = "{\"content\":\"%s\",\"message\":\"%s\" }";
        String data = String.format(hasNoUserInfoBase, content, commitMsg);
        if (!JNetUtils.isEmpty(username) && !JNetUtils.isEmpty(email)) {
            data = String.format(hasUserInfoBase, content, commitMsg, username, email);
        }

        try {
            Response resp = JNetClient.getInstance()
                    .newPut(uploadUrl)
                    .headers(getHttpHeader(token))
                    .body(data)
                    .build()
                    .newCall()
                    .execute();
            String res = resp.getBody();

            if (JNetUtils.isEmpty(res)) {
                return "";
            }
            try {
                JSONObject o1 = new JSONObject(res);
                if (!o1.has("content")) {
                    return "";
                }
                JSONObject o2 = o1.optJSONObject("content");
                if (o2 == null || !o2.has("download_url")) {
                    return "";
                }
                return o2.optString("download_url", "");
            } catch (JSONException e) {
                return "";
            }
        } catch (IOException e) {
            System.err.println("IO error: " + e.getMessage());
        }
        return "";
    }

    public static String getContent(String owner, String repo, String path) {
        return getContent(owner, repo, path, token);
    }

    public static String getContent(String owner, String repo, String path, String token) {
        Map<String, ShaInfo> mps = getSha(owner, repo, path, token);
        if (mps == null || mps.isEmpty()) {
            return "";
        }
        ShaInfo info = mps.get(path);
        if (info == null) {
            return "";
        }
        if ("base64".equalsIgnoreCase(info.encoding)) {
            return JNetUtils.decodeBase64(info.content);
        }
        return "";
    }

    public static Map<String, ShaInfo> getSha(String owner, String repo, String path, String token) {
        Map<String, ShaInfo> shaBody = new HashMap<String, ShaInfo>();
        try {
            String normalizedPath = normalizePath(path);
            String base = "https://api.github.com/repos/%s/%s/contents%s";
            String requestUrl = String.format(base, owner, repo, normalizedPath);
            System.out.println("   [DEBUG] 请求URL: " + requestUrl);

            Response resp = JNetClient.getInstance()
                    .newGet(requestUrl)
                    .headers(getHttpHeader(token))
                    .build()
                    .newCall()
                    .execute();

            int statusCode = resp.getCode();
            String result = resp.getBody();

            System.out.println("   [DEBUG] HTTP状态码: " + statusCode);
            System.out.println("   [DEBUG] 响应内容: "
                    + (result != null && result.length() > 200 ? result.substring(0, 200) + "..." : result));

            if (JNetUtils.isEmpty(result) || JNetUtils.isEmpty(result.trim())) {
                System.out.println("   [DEBUG] 返回空内容");
                return shaBody;
            }

            if (statusCode != 200) {
                System.out.println("   [DEBUG] 非200状态码，返回空");
                return shaBody;
            }

            try {
                JSONObject obj = new JSONObject(result);
                System.out.println("   [DEBUG] JSON对象: " + obj.toString());

                JSONObject links = obj.optJSONObject("_links");
                ShaInfo info = new ShaInfo(
                        obj.optString("name", null),
                        obj.optString("path", null),
                        obj.optString("sha", null),
                        obj.optLong("size", -1L),
                        obj.optString("url", null),
                        obj.optString("html_url", null),
                        obj.optString("git_url", null),
                        obj.optString("download_url", null),
                        obj.optString("type", null),
                        links,
                        (links == null ? "" : links.optString("self", null)),
                        (links == null ? "" : links.optString("git", null)),
                        (links == null ? "" : links.optString("html", null)),
                        obj.optString("content", null),
                        obj.optString("encoding", null));

                shaBody.put(path, info);
                System.out.println("   [DEBUG] 创建ShaInfo成功");

            } catch (JSONException e) {
                System.out.println("   [DEBUG] JSON对象解析失败，尝试数组: " + e.getMessage());
                try {
                    JSONArray arr = new JSONArray(result);
                    System.out.println("   [DEBUG] 是数组，长度: " + arr.length());
                    for (int i = 0; i < arr.length(); i++) {
                        Object o = arr.opt(i);
                        JSONObject obj = o instanceof JSONObject ? (JSONObject) o : null;
                        if (obj != null) {
                            JSONObject links = obj.optJSONObject("_links");
                            String pth = obj.optString("path", null);
                            System.out.println("   [DEBUG] 数组项" + i + ": path=" + pth);

                            shaBody.put(pth, new ShaInfo(obj.optString("name", null), obj.optString("path", null),
                                    obj.optString("sha", null), obj.optLong("size", -1L), obj.optString("url", null),
                                    obj.optString("html_url", null), obj.optString("git_url", null),
                                    obj.optString("download_url", null), obj.optString("type", null), links,
                                    (links == null ? "" : links.optString("self", null)),
                                    (links == null ? "" : links.optString("git", null)),
                                    (links == null ? "" : links.optString("html", null)),
                                    obj.optString("content", null), obj.optString("encoding", null)));
                        }
                    }
                } catch (JSONException ex) {
                    System.err.println("JSON parsing error in getSha: " + ex.getMessage());
                    System.err.println("Raw response: " + result);
                }
            }
        } catch (IOException e) {
            System.err.println("IO error in getSha: " + e.getMessage());
        } catch (Throwable e) {
            System.err.println("Unexpected error in getSha: " + e.getMessage());
        }
        return shaBody;
    }

    /**
     * Normalize path to ensure it starts with a slash for GitHub API
     */
    private static String normalizePath(String path) {
        if (path == null || path.isEmpty()) {
            return "";
        }
        if (!path.startsWith("/")) {
            return "/" + path;
        }
        return path;
    }

    public static Map<String, String> getHttpHeader(String token) {
        Map<String, String> reqHeaderMap = new HashMap<String, String>();
        reqHeaderMap.put("User-Agent", "Github createFile By Java");
        reqHeaderMap.put("Content-Type", "application/json; charset=UTF-8");
        reqHeaderMap.put("Accept", "application/vnd.github.v3+json");
        reqHeaderMap.put("Authorization", "token " + token);
        reqHeaderMap.put("accept-language", "zh-CN,zh;q=0.9,en;q=0.8");
        return reqHeaderMap;
    }

    /**
     * 获取仓库信息
     */
    public static String getRepositoryInfo(String owner, String repo) {
        return getRepositoryInfo(owner, repo, token);
    }

    public static String getRepositoryInfo(String owner, String repo, String token) {
        try {
            String url = String.format("https://api.github.com/repos/%s/%s", owner, repo);
            Response resp = JNetClient.getInstance()
                    .newGet(url)
                    .headers(getHttpHeader(token))
                    .build()
                    .newCall()
                    .execute();
            return resp.getBody();
        } catch (IOException e) {
            System.err.println("IO error in getRepositoryInfo: " + e.getMessage());
            return "";
        }
    }

    /**
     * 获取文件列表（目录内容）
     */
    public static List<ShaInfo> listDirectory(String owner, String repo, String path) {
        return listDirectory(owner, repo, path, token);
    }

    public static List<ShaInfo> listDirectory(String owner, String repo, String path, String token) {
        List<ShaInfo> results = new ArrayList<>();
        Map<String, ShaInfo> shas = getSha(owner, repo, path, token);
        if (shas != null && !shas.isEmpty()) {
            // If it's a single file, return it in a list
            ShaInfo single = shas.get(path);
            if (single != null) {
                results.add(single);
            } else {
                // If it's a directory, return all items
                results.addAll(shas.values());
            }
        }
        return results;
    }

    /**
     * 获取提交历史
     */
    public static String getCommits(String owner, String repo, String path) {
        return getCommits(owner, repo, path, token, 1, 30);
    }

    public static String getCommits(String owner, String repo, String path, String token, int page, int perPage) {
        try {
            String normalizedPath = normalizePath(path);
            String encodedPath = JNetUtils.urlEncode(normalizedPath);
            String url = String.format("https://api.github.com/repos/%s/%s/commits?path=%s&page=%d&per_page=%d",
                    owner, repo, encodedPath, page, perPage);
            Response resp = JNetClient.getInstance()
                    .newGet(url)
                    .headers(getHttpHeader(token))
                    .build()
                    .newCall()
                    .execute();
            return resp.getBody();
        } catch (IOException e) {
            System.err.println("IO error in getCommits: " + e.getMessage());
            return "";
        }
    }

    /**
     * 获取分支列表
     */
    public static String getBranches(String owner, String repo) {
        return getBranches(owner, repo, token);
    }

    public static String getBranches(String owner, String repo, String token) {
        try {
            String url = String.format("https://api.github.com/repos/%s/%s/branches", owner, repo);
            Response resp = JNetClient.getInstance()
                    .newGet(url)
                    .headers(getHttpHeader(token))
                    .build()
                    .newCall()
                    .execute();
            return resp.getBody();
        } catch (IOException e) {
            System.err.println("IO error in getBranches: " + e.getMessage());
            return "";
        }
    }

    /**
     * 批量创建文件（异步）
     */
    public static CompletableFuture<List<String>> batchCreateFiles(String owner, String repo,
            List<Map<String, String>> files, String commitMsg) {
        return batchCreateFiles(owner, repo, files, token, commitMsg);
    }

    public static CompletableFuture<List<String>> batchCreateFiles(String owner, String repo,
            List<Map<String, String>> files, String token, String commitMsg) {
        List<CompletableFuture<String>> futures = new ArrayList<>();

        for (Map<String, String> file : files) {
            String path = file.get("path");
            String content = file.get("content");

            CompletableFuture<String> future = CompletableFuture.supplyAsync(() -> {
                try {
                    return createFile(owner, repo, path, token, content, commitMsg);
                } catch (Exception e) {
                    System.err.println("Error creating file " + path + ": " + e.getMessage());
                    return "";
                }
            });
            futures.add(future);
        }

        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                .thenApply(v -> {
                    List<String> results = new ArrayList<>();
                    for (CompletableFuture<String> future : futures) {
                        results.add(future.join());
                    }
                    return results;
                });
    }

    /**
     * 检查文件是否存在
     */
    public static boolean fileExists(String owner, String repo, String path) {
        return fileExists(owner, repo, path, token);
    }

    public static boolean fileExists(String owner, String repo, String path, String token) {
        try {
            String normalizedPath = normalizePath(path);
            String url = String.format("https://api.github.com/repos/%s/%s/contents%s", owner, repo, normalizedPath);
            Response resp = JNetClient.getInstance()
                    .newGet(url)
                    .headers(getHttpHeader(token))
                    .build()
                    .newCall()
                    .execute();
            return resp.getCode() == 200;
        } catch (IOException e) {
            return false;
        }
    }

    /**
     * 获取速率限制信息
     */
    public static String getRateLimit() {
        return getRateLimit(token);
    }

    public static String getRateLimit(String token) {
        try {
            String url = "https://api.github.com/rate_limit";
            Response resp = JNetClient.getInstance()
                    .newGet(url)
                    .headers(getHttpHeader(token))
                    .build()
                    .newCall()
                    .execute();
            return resp.getBody();
        } catch (IOException e) {
            System.err.println("IO error in getRateLimit: " + e.getMessage());
            return "";
        }
    }

    static class ShaInfo {
        public String name = null;
        public String path = null;
        public String sha = null;
        public long size = -1L;
        public String url = null;
        public String html_url = null;
        public String git_url = null;
        public String download_url = null;
        public String type = null;
        public JSONObject _links = null;
        public String _links_key_self = null;
        public String _links_key_git = null;
        public String _links_key_html = null;
        public String content = null;
        public String encoding = null;

        public ShaInfo(String __name, String __path, String __sha, long __size, String __url, String __html_url,
                String __git_url, String __download_url, String __type, JSONObject ___links, String ___links_key_self,
                String ___links_key_git, String ___links_key_html) {
            this(__name, __path, __sha, __size, __url, __html_url, __git_url, __download_url, __type, ___links,
                    ___links_key_self, ___links_key_git, ___links_key_html, null, null);
        }

        public ShaInfo(String __name, String __path, String __sha, long __size, String __url, String __html_url,
                String __git_url, String __download_url, String __type, JSONObject ___links, String ___links_key_self,
                String ___links_key_git, String ___links_key_html, String __content, String __encoding) {
            this.name = __name;
            this.path = __path;
            this.sha = __sha;
            this.size = __size;
            this.url = __url;
            this.html_url = __html_url;
            this.git_url = __git_url;
            this.download_url = __download_url;
            this.type = __type;
            this._links = ___links;
            this._links_key_self = ___links_key_self;
            this._links_key_git = ___links_key_git;
            this._links_key_html = ___links_key_html;
            this.content = __content;
            this.encoding = __encoding;
        }
    }

    /**
     * 测试方法 - 带详细日志输出
     */
    public static void main(String[] args) {
        System.out.println("=== GithubHelper 测试开始 ===");
        System.out.println("当前Token: "
                + (token != null && !token.isEmpty() ? "已设置(" + token.substring(0, Math.min(8, token.length())) + "...)"
                        : "未设置"));

        String owner = "hhhaiai";
        String repo = "testAPP";
        String path = "test_append.txt";
        String appendcontent = "test";
        String updatecontent = "content content";
        String commitMsg = "update by test jnet";

        System.out.println("\n测试参数:");
        System.out.println("  Owner: " + owner);
        System.out.println("  Repo: " + repo);
        System.out.println("  Path: " + path);
        System.out.println("  Content: " + appendcontent);
        System.out.println("  Commit: " + commitMsg);

        try {
            // 首先检查getSha返回的信息
            System.out.println("\n0. 调试getSha...");
            Map<String, ShaInfo> shas = getSha(owner, repo, path, token);
            System.out.println("   getSha返回: " + (shas != null ? shas.size() + "个条目" : "null"));
            if (shas != null && !shas.isEmpty()) {
                ShaInfo info = shas.get(path);
                if (info != null) {
                    System.out.println("   - name: " + info.name);
                    System.out.println("   - path: " + info.path);
                    System.out.println("   - sha: " + info.sha);
                    System.out.println("   - type: " + info.type);
                    System.out.println("   - encoding: " + info.encoding);
                    System.out.println("   - content长度: " + (info.content != null ? info.content.length() : 0));
                    System.out.println("   - download_url: " + info.download_url);
                } else {
                    System.out.println("   - 未找到路径对应的ShaInfo");
                }
            }

            System.out.println("\n1. 执行 updateContent...");
            String updateResult = updateContent(owner, repo, path, updatecontent, commitMsg);
            System.out.println("   返回: " + (updateResult != null && !updateResult.isEmpty() ? updateResult : "空结果"));

            System.out.println("\n2. 执行 append...");
            String appendResult = append(owner, repo, path, appendcontent, commitMsg);
            System.out.println("   返回: " + (appendResult != null && !appendResult.isEmpty() ? appendResult : "空结果"));

            System.out.println("\n3. 验证文件是否存在...");
            boolean exists = fileExists(owner, repo, path);
            System.out.println("   文件存在: " + exists);

            System.out.println("\n4. 获取文件内容...");
            String fileContent = getContent(owner, repo, path);
            System.out.println("   返回: " + (fileContent != null && !fileContent.isEmpty() ? fileContent : "空结果"));

            System.out.println("\n=== 诊断结论 ===");
            if (shas != null && !shas.isEmpty()) {
                ShaInfo info = shas.get(path);
                if (info != null) {
                    if (info.sha == null || info.sha.isEmpty()) {
                        System.out.println("❌ 问题: getSha返回了条目但sha为空 - 可能是权限不足");
                    } else if (info.content == null && "file".equals(info.type)) {
                        System.out.println("❌ 问题: 文件存在但无内容 - 可能是权限或API限制");
                    } else if (info.encoding != null && !info.encoding.equals("base64")) {
                        System.out.println("⚠️ 警告: 编码不是base64 - " + info.encoding);
                    } else {
                        System.out.println("✅ getSha信息正常，检查updateContent内部逻辑");
                    }
                }
            } else {
                System.out.println("❌ 问题: getSha返回空 - 文件可能不存在或权限不足");
            }

        } catch (Exception e) {
            System.err.println("\n❌ 执行出错:");
            System.err.println("   错误类型: " + e.getClass().getSimpleName());
            System.err.println("   错误信息: " + e.getMessage());
            e.printStackTrace();
        }
    }
    // public static void main(String[] args) {
    // System.out.println(token);
    // updateContent("hhhaiai", "testAPP", "test_append.txt", "test", " update by
    // test jnet");
    // append("hhhaiai", "testAPP", "test_append.txt", "test", " update by test
    // jnet");
    // }

}
