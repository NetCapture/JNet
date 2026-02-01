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
 * Gitee API操作工具类.
 * <p>
 * Gitee api:
 * https://gitee.com/api/v5/swagger#/getV5ReposOwnerRepoContentsPath
 * </p>
 * <p>
 * Copyright © 2020 sanbo Inc. All rights reserved.
 * </p>
 * <p>
 * Create: 2025-12-30 15:13:01
 * </p>
 *
 * @version 3.0.0
 * @author sanbo
 */
public class GiteeHelper {

    private static final int DEF_TIMEOUT = 50 * 1000;
    private static String token = System.getenv("GITEE_TOKEN");

    public static void setGlobalToken(String _token) {
        token = _token;
    }

    /**
     * 追加内容到文件
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

    /**
     * 更新文件内容
     */
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
            // Gitee API uses different format
            String normalizedPath = normalizePath(path);
            String base = "https://gitee.com/api/v5/repos/%s/%s/contents%s";
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
                // Gitee requires access_token as parameter
                String separator = uploadUrl.contains("?") ? "&" : "?";
                String uploadUrlWithToken = uploadUrl + separator + "access_token=" + token;

                String hasUserInfoBase = "{\"content\":\"%s\",\"message\":\"%s\" ,\"sha\":\"%s\" ,\"committer\":{ \"name\":\"%s\",\"email\":\"%s\" }}";
                String hasNoUserInfoBase = "{\"content\":\"%s\",\"message\":\"%s\", \"sha\":\"%s\" }";
                String data = String.format(hasNoUserInfoBase, content, commitMsg, sha);
                if (!JNetUtils.isEmpty(username) && !JNetUtils.isEmpty(email)) {
                    data = String.format(hasUserInfoBase, content, commitMsg, sha, username, email);
                }
                Response resp = JNetClient.getInstance()
                        .newPut(uploadUrlWithToken)
                        .headers(getHttpHeader())
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

    /**
     * 删除文件
     */
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
        String base = "https://gitee.com/api/v5/repos/%s/%s/contents%s";
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

    /**
     * 删除目录（递归）
     */
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
        String base = "https://gitee.com/api/v5/repos/%s/%s/contents%s";
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
        // Gitee requires access_token as parameter
        String separator = url.contains("?") ? "&" : "?";
        String urlWithToken = url + separator + "access_token=" + token;

        String hasUserInfoBase = "{\"message\":\"%s\" ,\"sha\":\"%s\" ,\"committer\":{ \"name\":\"%s\",\"email\":\"%s\" }}";
        String hasNoUserInfoBase = "{\"message\":\"%s\", \"sha\":\"%s\" }";
        String data = String.format(hasNoUserInfoBase, commitMsg, sha);
        if (!JNetUtils.isEmpty(username) && !JNetUtils.isEmpty(email)) {
            data = String.format(hasUserInfoBase, commitMsg, sha, username, email);
        }
        try {
            Response resp = JNetClient.getInstance()
                    .newDelete(urlWithToken)
                    .headers(getHttpHeader())
                    .body(data)
                    .build()
                    .newCall()
                    .execute();

            return resp.getBody();
        } catch (IOException e) {
            System.err.println("IO error in realDelFileBySha: " + e.getMessage());
        }
        return "";
    }

    /**
     * 创建文件
     */
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
        String base = "https://gitee.com/api/v5/repos/%s/%s/contents%s";
        String uploadUrl = String.format(base, owner, repo, normalizedPath);

        // Gitee requires access_token as parameter
        String separator = uploadUrl.contains("?") ? "&" : "?";
        String uploadUrlWithToken = uploadUrl + separator + "access_token=" + token;

        String hasUserInfoBase = "{\"content\":\"%s\",\"message\":\"%s\" ,\"committer\":{ \"name\":\"%s\",\"email\":\"%s\" }}";
        String hasNoUserInfoBase = "{\"content\":\"%s\",\"message\":\"%s\" }";
        String data = String.format(hasNoUserInfoBase, content, commitMsg);
        if (!JNetUtils.isEmpty(username) && !JNetUtils.isEmpty(email)) {
            data = String.format(hasUserInfoBase, content, commitMsg, username, email);
        }

        try {
            Response resp = JNetClient.getInstance()
                    .newPut(uploadUrlWithToken)
                    .headers(getHttpHeader())
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
            System.err.println("IO error in realCreateFileInternal: " + e.getMessage());
        }
        return "";
    }

    /**
     * 获取文件内容
     */
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

    /**
     * 获取SHA信息
     */
    public static Map<String, ShaInfo> getSha(String owner, String repo, String path, String token) {
        Map<String, ShaInfo> shaBody = new HashMap<String, ShaInfo>();
        try {
            String normalizedPath = normalizePath(path);
            String base = "https://gitee.com/api/v5/repos/%s/%s/contents%s";
            String requestUrl = String.format(base, owner, repo, normalizedPath);
            // Gitee requires access_token as parameter
            String separator = requestUrl.contains("?") ? "&" : "?";
            String requestUrlWithToken = requestUrl + separator + "access_token=" + token;

            Response resp = JNetClient.getInstance()
                    .newGet(requestUrlWithToken)
                    .headers(getHttpHeader())
                    .build()
                    .newCall()
                    .execute();
            String result = resp.getBody();
            if (JNetUtils.isEmpty(result) || JNetUtils.isEmpty(result.trim())) {
                return shaBody;
            }
            try {
                JSONObject obj = new JSONObject(result);
                // Gitee doesn't have _links in the same format, use null
                shaBody.put(path,
                        new ShaInfo(obj.optString("name", null), obj.optString("path", null),
                                obj.optString("sha", null), obj.optLong("size", -1L), obj.optString("url", null),
                                obj.optString("html_url", null), obj.optString("git_url", null),
                                obj.optString("download_url", null), obj.optString("type", null), null,
                                "", "", "", obj.optString("content", null),
                                obj.optString("encoding", null)));
            } catch (JSONException e) {
                try {
                    JSONArray arr = new JSONArray(result);
                    for (int i = 0; i < arr.length(); i++) {
                        Object o = arr.opt(i);
                        JSONObject obj = o instanceof JSONObject ? (JSONObject) o : null;
                        if (obj != null) {
                            String pth = obj.optString("path", null);

                            shaBody.put(pth, new ShaInfo(obj.optString("name", null), obj.optString("path", null),
                                    obj.optString("sha", null), obj.optLong("size", -1L), obj.optString("url", null),
                                    obj.optString("html_url", null), obj.optString("git_url", null),
                                    obj.optString("download_url", null), obj.optString("type", null), null,
                                    "", "", "", obj.optString("content", null),
                                    obj.optString("encoding", null)));
                        }
                    }
                } catch (JSONException ex) {
                    System.err.println("JSON parsing error in getSha: " + ex.getMessage());
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
     * 获取HTTP请求头
     */
    public static Map<String, String> getHttpHeader() {
        Map<String, String> reqHeaderMap = new HashMap<String, String>();
        reqHeaderMap.put("User-Agent", "Gitee createFile By Java");
        reqHeaderMap.put("Content-Type", "application/json; charset=UTF-8");
        reqHeaderMap.put("Accept", "application/json");
        reqHeaderMap.put("accept-language", "zh-CN,zh;q=0.9,en;q=0.8");
        return reqHeaderMap;
    }

    /**
     * Normalize path to ensure it starts with a slash for Gitee API
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

    // 现代API方法

    /**
     * 获取仓库信息
     */
    public static String getRepositoryInfo(String owner, String repo) {
        return getRepositoryInfo(owner, repo, token);
    }

    public static String getRepositoryInfo(String owner, String repo, String token) {
        try {
            String url = String.format("https://gitee.com/api/v5/repos/%s/%s", owner, repo);
            String separator = url.contains("?") ? "&" : "?";
            String urlWithToken = url + separator + "access_token=" + token;

            Response resp = JNetClient.getInstance()
                    .newGet(urlWithToken)
                    .headers(getHttpHeader())
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
            String url = String.format("https://gitee.com/api/v5/repos/%s/%s/commits?path=%s&page=%d&per_page=%d",
                    owner, repo, path, page, perPage);
            String separator = url.contains("?") ? "&" : "?";
            String urlWithToken = url + separator + "access_token=" + token;

            Response resp = JNetClient.getInstance()
                    .newGet(urlWithToken)
                    .headers(getHttpHeader())
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
            String url = String.format("https://gitee.com/api/v5/repos/%s/%s/branches", owner, repo);
            String separator = url.contains("?") ? "&" : "?";
            String urlWithToken = url + separator + "access_token=" + token;

            Response resp = JNetClient.getInstance()
                    .newGet(urlWithToken)
                    .headers(getHttpHeader())
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
            String base = "https://gitee.com/api/v5/repos/%s/%s/contents%s";
            String url = String.format(base, owner, repo, normalizedPath);
            String separator = url.contains("?") ? "&" : "?";
            String urlWithToken = url + separator + "access_token=" + token;

            Response resp = JNetClient.getInstance()
                    .newGet(urlWithToken)
                    .headers(getHttpHeader())
                    .build()
                    .newCall()
                    .execute();
            return resp.getCode() == 200;
        } catch (IOException e) {
            return false;
        }
    }

    /**
     * 获取速率限制信息（Gitee使用配额系统）
     */
    public static String getRateLimit() {
        return getRateLimit(token);
    }

    public static String getRateLimit(String token) {
        try {
            String url = "https://gitee.com/api/v5/rate_limit";
            String separator = url.contains("?") ? "&" : "?";
            String urlWithToken = url + separator + "access_token=" + token;

            Response resp = JNetClient.getInstance()
                    .newGet(urlWithToken)
                    .headers(getHttpHeader())
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
        public Object _links = null; // Gitee doesn't use _links in the same way
        public String _links_key_self = null;
        public String _links_key_git = null;
        public String _links_key_html = null;
        public String content = null;
        public String encoding = null;

        public ShaInfo(String __name, String __path, String __sha, long __size, String __url, String __html_url,
                String __git_url, String __download_url, String __type, Object ___links, String ___links_key_self,
                String ___links_key_git, String ___links_key_html) {
            this(__name, __path, __sha, __size, __url, __html_url, __git_url, __download_url, __type, ___links,
                    ___links_key_self, ___links_key_git, ___links_key_html, null, null);
        }

        public ShaInfo(String __name, String __path, String __sha, long __size, String __url, String __html_url,
                String __git_url, String __download_url, String __type, Object ___links, String ___links_key_self,
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
}
