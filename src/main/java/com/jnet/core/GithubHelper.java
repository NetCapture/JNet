package com.jnet.core;

import com.jnet.core.org.json.JSONObject;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/** GitHub repository-content API compatibility facade. */
public class GithubHelper {
    private static final GitContentsClient CLIENT = GitContentsClient.github();
    private static volatile String token = System.getenv("GITHUB_TOKEN");

    public static void setGlobalToken(String _token) {
        token = _token;
    }

    public static String append(String owner, String repo, String path, String content, String commitMsg) {
        return append(owner, repo, path, token, content, commitMsg);
    }

    public static String append(String owner, String repo, String path, String accessToken, String content,
            String commitMsg) {
        return append(owner, repo, path, accessToken, content, commitMsg, "", "");
    }

    public static String append(String owner, String repo, String path, String accessToken, String content,
            String commitMsg, String username, String email) {
        return CLIENT.append(owner, repo, path, accessToken, content, commitMsg, username, email);
    }

    public static String updateContent(String owner, String repo, String path, String content, String commitMsg) {
        return updateContent(owner, repo, path, token, content, commitMsg);
    }

    public static String updateContent(String owner, String repo, String path, String accessToken, String content,
            String commitMsg) {
        return updateContent(owner, repo, path, accessToken, content, commitMsg, "", "");
    }

    public static String updateContent(String owner, String repo, String path, String accessToken, String content,
            String commitMsg, String username, String email) {
        return CLIENT.updateContent(owner, repo, path, accessToken, content, commitMsg, username, email);
    }

    public static String deleteFile(String owner, String repo, String[] paths, String commitMsg) {
        return deleteFile(owner, repo, paths, token, commitMsg);
    }

    public static String deleteFile(String owner, String repo, String[] paths, String accessToken, String commitMsg) {
        return CLIENT.deleteFiles(owner, repo, paths, accessToken, commitMsg);
    }

    public static String deleteFile(String owner, String repo, String path, String commitMsg) {
        return deleteFile(owner, repo, path, token, commitMsg);
    }

    public static String deleteFile(String owner, String repo, String path, String accessToken, String commitMsg) {
        return deleteFile(owner, repo, path, accessToken, commitMsg, "", "");
    }

    public static String deleteFile(String owner, String repo, String path, String accessToken, String commitMsg,
            String username, String email) {
        return CLIENT.deleteFile(owner, repo, path, accessToken, commitMsg, username, email);
    }

    public static void deleteDir(String owner, String repo, String[] paths, String accessToken, String commitMsg) {
        CLIENT.deleteDirectories(owner, repo, paths, accessToken, commitMsg);
    }

    public static void deleteDir(String owner, String repo, String path, String commitMsg) {
        deleteDir(owner, repo, path, token, commitMsg);
    }

    public static void deleteDir(String owner, String repo, String path, String accessToken, String commitMsg) {
        deleteDir(owner, repo, path, accessToken, commitMsg, "", "");
    }

    public static void deleteDir(String owner, String repo, String path, String accessToken, String commitMsg,
            String username, String email) {
        CLIENT.deleteDirectory(owner, repo, path, accessToken, commitMsg, username, email);
    }

    public static String createFile(String owner, String repo, String path, String accessToken, String content,
            String commitMsg) {
        return createFile(true, owner, repo, path, accessToken, content, commitMsg, "", "");
    }

    public static String createFile(String owner, String repo, String path, String content, String commitMsg) {
        return createFile(true, owner, repo, path, token, content, commitMsg, "", "");
    }

    public static String createFile(String owner, String repo, String path, File file, String commitMsg) {
        return createFileFromDisk(owner, repo, path, token, file, commitMsg);
    }

    public static String createFile(String owner, String repo, String path, String accessToken, File file,
            String commitMsg) {
        return createFileFromDisk(owner, repo, path, accessToken, file, commitMsg);
    }

    private static String createFileFromDisk(String owner, String repo, String path,
            String accessToken, File file, String commitMsg) {
        String content = FileUtils.getBase64FromFile(file);
        return content == null ? "" : createFile(
                false, owner, repo, path, accessToken, content, commitMsg, "", "");
    }

    public static String createFile(boolean isNeedBase64, String owner, String repo, String path,
            String uploadContent, String commitMsg, String username, String email) {
        return createFile(isNeedBase64, owner, repo, path, token, uploadContent, commitMsg, username, email);
    }

    public static String createFile(boolean isNeedBase64, String owner, String repo, String path, String accessToken,
            String uploadContent, String commitMsg, String username, String email) {
        return CLIENT.createFile(isNeedBase64, owner, repo, path, accessToken,
                uploadContent, commitMsg, username, email);
    }

    public static String getContent(String owner, String repo, String path) {
        return getContent(owner, repo, path, token);
    }

    public static String getContent(String owner, String repo, String path, String accessToken) {
        return CLIENT.getContent(owner, repo, path, accessToken);
    }

    public static Map<String, ShaInfo> getSha(String owner, String repo, String path, String accessToken) {
        return adapt(CLIENT.getItems(owner, repo, path, accessToken));
    }

    public static Map<String, String> getHttpHeader(String accessToken) {
        return CLIENT.headers(accessToken);
    }

    public static String getRepositoryInfo(String owner, String repo) {
        return getRepositoryInfo(owner, repo, token);
    }

    public static String getRepositoryInfo(String owner, String repo, String accessToken) {
        return CLIENT.getRepositoryInfo(owner, repo, accessToken);
    }

    public static List<ShaInfo> listDirectory(String owner, String repo, String path) {
        return listDirectory(owner, repo, path, token);
    }

    public static List<ShaInfo> listDirectory(String owner, String repo, String path, String accessToken) {
        List<ShaInfo> result = new ArrayList<>();
        for (GitContentsClient.Item item : CLIENT.listDirectory(owner, repo, path, accessToken)) {
            result.add(adapt(item));
        }
        return result;
    }

    public static String getCommits(String owner, String repo, String path) {
        return getCommits(owner, repo, path, token, 1, 30);
    }

    public static String getCommits(String owner, String repo, String path, String accessToken,
            int page, int perPage) {
        return CLIENT.getCommits(owner, repo, path, accessToken, page, perPage);
    }

    public static String getBranches(String owner, String repo) {
        return getBranches(owner, repo, token);
    }

    public static String getBranches(String owner, String repo, String accessToken) {
        return CLIENT.getBranches(owner, repo, accessToken);
    }

    public static CompletableFuture<List<String>> batchCreateFiles(String owner, String repo,
            List<Map<String, String>> files, String commitMsg) {
        return batchCreateFiles(owner, repo, files, token, commitMsg);
    }

    public static CompletableFuture<List<String>> batchCreateFiles(String owner, String repo,
            List<Map<String, String>> files, String accessToken, String commitMsg) {
        return CLIENT.batchCreateFiles(owner, repo, files, accessToken, commitMsg);
    }

    public static boolean fileExists(String owner, String repo, String path) {
        return fileExists(owner, repo, path, token);
    }

    public static boolean fileExists(String owner, String repo, String path, String accessToken) {
        return CLIENT.fileExists(owner, repo, path, accessToken);
    }

    public static String getRateLimit() {
        return getRateLimit(token);
    }

    public static String getRateLimit(String accessToken) {
        return CLIENT.getRateLimit(accessToken);
    }

    private static Map<String, ShaInfo> adapt(Map<String, GitContentsClient.Item> items) {
        Map<String, ShaInfo> result = new HashMap<>();
        for (Map.Entry<String, GitContentsClient.Item> entry : items.entrySet()) {
            result.put(entry.getKey(), adapt(entry.getValue()));
        }
        return result;
    }

    private static ShaInfo adapt(GitContentsClient.Item item) {
        return new ShaInfo(item.name, item.path, item.sha, item.size, item.url, item.htmlUrl,
                item.gitUrl, item.downloadUrl, item.type, item.links,
                item.linkSelf, item.linkGit, item.linkHtml, item.content, item.encoding);
    }

    public static class ShaInfo {
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

        public ShaInfo(String name, String path, String sha, long size, String url, String htmlUrl,
                String gitUrl, String downloadUrl, String type, JSONObject links,
                String linkSelf, String linkGit, String linkHtml) {
            this(name, path, sha, size, url, htmlUrl, gitUrl, downloadUrl, type,
                    links, linkSelf, linkGit, linkHtml, null, null);
        }

        public ShaInfo(String name, String path, String sha, long size, String url, String htmlUrl,
                String gitUrl, String downloadUrl, String type, JSONObject links,
                String linkSelf, String linkGit, String linkHtml, String content, String encoding) {
            this.name = name;
            this.path = path;
            this.sha = sha;
            this.size = size;
            this.url = url;
            this.html_url = htmlUrl;
            this.git_url = gitUrl;
            this.download_url = downloadUrl;
            this.type = type;
            this._links = links;
            this._links_key_self = linkSelf;
            this._links_key_git = linkGit;
            this._links_key_html = linkHtml;
            this.content = content;
            this.encoding = encoding;
        }
    }

    /** @deprecated Runtime examples were removed from the library artifact. */
    @Deprecated
    public static void main(String[] args) {
        // Retained only for binary compatibility with 3.x artifacts.
    }
}
