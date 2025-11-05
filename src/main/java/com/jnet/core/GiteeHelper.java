package com.jnet.core;

import com.jnet.core.org.json.JSONArray;
import com.jnet.core.org.json.JSONException;
import com.jnet.core.org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * @Copyright © 2020 sanbo Inc. All rights reserved.
 * @Description: gitee API操作工具类，gitee
 * api:https://gitee.com/api/v5/swagger#/paths
 * @Version: 3.0.0
 * @Create: 2020-12-09 15:13:01
 * @Author: sanbo
 */
public class GiteeHelper {

    private static final int DEF_TIMEOUT = 50 * 1000;
    private static String token = System.getenv("GITEE_TOKEN");

    public static void setGlobalToken(String _token) {
        token = _token;
    }

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
            String base = "https://gitee.com/api/v5/repos/%s/%s/contents%s";
            String uploadUrl = String.format(base, owner, repo, path);
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
            e.printStackTrace();
        } catch (Throwable e) {
            e.printStackTrace();
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
        for (String path : paths) {
            return deleteFile(owner, repo, path, token, commitMsg);
        }
        return "";
    }

    public static String deleteFile(String owner, String repo, String path, String commitMsg) {
        return deleteFile(owner, repo, path, token, commitMsg);
    }

    public static String deleteFile(String owner, String repo, String path, String token, String commitMsg) {
        return deleteFile(owner, repo, path, token, commitMsg, "", "");
    }

    public static String deleteFile(String owner, String repo, String path, String token, String commitMsg,
                                    String username, String email) {
        String base = "https://gitee.com/api/v5/repos/%s/%s/contents%s";
        String uploadUrl = String.format(base, owner, repo, path);
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
            e.printStackTrace();
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
        return createFile(false, owner, repo, path, token, FileUtils.getBase64FromFile(file.getAbsolutePath()), commitMsg, "", "");
    }

    public static String createFile(String owner, String repo, String path, String token, File file, String commitMsg) {
        return createFile(false, owner, repo, path, token, FileUtils.getBase64FromFile(file.getAbsolutePath()), commitMsg, "", "");
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
            e.printStackTrace();
        }

        return "";
    }

    private static String realCreateFileInternal(String owner, String repo, String path,
                                                 String token, String uploadContent, String commitMsg, String username, String email) {
        String content = JNetUtils.encodeBase64(uploadContent);
        String base = "https://gitee.com/api/v5/repos/%s/%s/contents%s";
        String uploadUrl = String.format(base, owner, repo, path);

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
            e.printStackTrace();
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
            String base = "https://gitee.com/api/v5/repos/%s/%s/contents%s";
            String requestUrl = String.format(base, owner, repo, path);
            Response resp = JNetClient.getInstance()
                .newGet(requestUrl)
                .headers(getHttpHeader(token))
                .build()
                .newCall()
                .execute();
            String result = resp.getBody();
            if (JNetUtils.isEmpty(result) || JNetUtils.isEmpty(result.trim())) {
                return shaBody;
            }
            try {
                JSONObject obj = new JSONObject(result);
                JSONObject links = obj.optJSONObject("_links");
                shaBody.put(path,
                        new ShaInfo(obj.optString("name", null), obj.optString("path", null),
                                obj.optString("sha", null), obj.optLong("size", -1L), obj.optString("url", null),
                                obj.optString("html_url", null), obj.optString("git_url", null),
                                obj.optString("download_url", null), obj.optString("type", null), links,
                                (links == null ? "" : links.optString("self", null)),
                                (links == null ? "" : links.optString("git", null)),
                                (links == null ? "" : links.optString("html", null)), obj.optString("content", null),
                                obj.optString("encoding", null)));
            } catch (JSONException e) {
                try {
                    JSONArray arr = new JSONArray(result);
                    for (int i = 0; i < arr.length(); i++) {
                        Object o = arr.opt(i);
                        JSONObject obj = o instanceof JSONObject ? (JSONObject) o : null;
                        if (obj != null) {
                            JSONObject links = obj.optJSONObject("_links");
                            String pth = obj.optString("path", null);

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
                    ex.printStackTrace();
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        } catch (Throwable e) {
            e.printStackTrace();
        }
        return shaBody;
    }

    public static Map<String, String> getHttpHeader(String token) {
        Map<String, String> reqHeaderMap = new HashMap<String, String>();
        reqHeaderMap.put("User-Agent", "Gitee createFile By Java");
        reqHeaderMap.put("Content-Type", "charset=UTF-8");
        reqHeaderMap.put("Accept", "application/json");
        reqHeaderMap.put("Authorization", "token " + token);
        reqHeaderMap.put("accept-language", "zh-CN,zh;q=0.9,en;q=0.8");
        return reqHeaderMap;
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
                       String ___links_key_git, String ___links_key_html
                , String __content, String __encoding) {
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
