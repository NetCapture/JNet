package ff.jnezha.jnt.cs;

import ff.jnezha.jnt.Jnt;
import ff.jnezha.jnt.org.json.JSONArray;
import ff.jnezha.jnt.org.json.JSONException;
import ff.jnezha.jnt.org.json.JSONObject;
import ff.jnezha.jnt.utils.FileUtils;
import ff.jnezha.jnt.utils.HttpType;
import ff.jnezha.jnt.utils.TextUtils;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

/**
 * Copyright © 2020 analysys Inc. All rights reserved.
 * Description: github API操作工具类， github api:https://developer.github.com/v3/repos/contents/
 * Version: 1.0
 * Create: 2020-12-09 15:13:01
 * Author: sanbo
 *
 * @author sanbo
 * @version $Id: $Id
 */
public class GithubHelper {

    private static final int DEF_TIMEOUT = 50 * 1000;
    private static String token = System.getenv("GITHUB_TOKEN");

    public static String updateContent(String owner, String repo, String path, String contentWillBase64, String commitMsg) {
        return updateContent(owner, repo, path, token, contentWillBase64, commitMsg);
    }

    /**
     * <p>updateContent.</p>
     *
     * @param owner             a {@link java.lang.String} object.
     * @param repo              a {@link java.lang.String} object.
     * @param path              a {@link java.lang.String} object.
     * @param token             a {@link java.lang.String} object.
     * @param contentWillBase64 a {@link java.lang.String} object.
     * @param commitMsg         a {@link java.lang.String} object.
     */
    public static String updateContent(String owner, String repo, String path, String token, String contentWillBase64, String commitMsg) {
        return updateContent(owner, repo, path, token, contentWillBase64, commitMsg, "", "");
    }

    /**
     * <p>updateContent.</p>
     *
     * @param owner             a {@link java.lang.String} object.
     * @param repo              a {@link java.lang.String} object.
     * @param path              a {@link java.lang.String} object.
     * @param token             a {@link java.lang.String} object.
     * @param contentWillBase64 a {@link java.lang.String} object.
     * @param commitMsg         a {@link java.lang.String} object.
     * @param username          a {@link java.lang.String} object.
     * @param email             a {@link java.lang.String} object.
     * @return a {@link java.lang.String} object.
     */
    public static String updateContent(String owner, String repo, String path, String token, String contentWillBase64, String commitMsg, String username, String email) {

        String content = TextUtils.encodeBase64ToString(contentWillBase64);
        String base = "https://api.github.com/repos/%s/%s/contents%s";
        String uploadUrl = String.format(base, owner, repo, path);
        Map<String, ShaInfo> shas = getSha(owner, repo, path, token);

        if (shas == null || shas.size() < 1) {
            // 不存在. 则新建
            return createFile(true, owner, repo, path, token, contentWillBase64, commitMsg, username, email);
        }
        ShaInfo s = shas.get(path);
        if (s == null) {
            return "";
        }
        String sha = s.sha;
        if (!TextUtils.isEmpty(sha)) {
            String hasUserInfoBase = "{\"content\":\"%s\",\"message\":\"%s\" ,\"sha\":\"%s\" ,\"committer\":{ \"name\":\"%s\",\"email\":\"%s\" }}";
            String hasNoUserInfoBase = "{\"content\":\"%s\",\"message\":\"%s\", \"sha\":\"%s\" }";
            String data = String.format(hasNoUserInfoBase, content, commitMsg, sha);
            if (!TextUtils.isEmpty(username) && !TextUtils.isEmpty(email)) {
                data = String.format(hasUserInfoBase, content, commitMsg, sha, username, email);
            }
            return Jnt.request(HttpType.PUT, DEF_TIMEOUT, uploadUrl, null, getHttpHeader(token), data);
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
        for (int i = 0; i < paths.length; i++) {
            return deleteFile(owner, repo, paths[i], token, commitMsg);
        }
        return "";
    }


    /**
     * <p>deleteFile.</p>
     *
     * @param owner     a {@link java.lang.String} object.
     * @param repo      a {@link java.lang.String} object.
     * @param path      a {@link java.lang.String} object.
     * @param token     a {@link java.lang.String} object.
     * @param commitMsg a {@link java.lang.String} object.
     * @return a {@link java.lang.String} object.
     */
    public static String deleteFile(String owner, String repo, String path, String token, String commitMsg) {
        return deleteFile(owner, repo, path, token, commitMsg, "", "");
    }

    /**
     * <p>deleteFile.</p>
     *
     * @param owner     a {@link java.lang.String} object.
     * @param repo      a {@link java.lang.String} object.
     * @param path      a {@link java.lang.String} object.
     * @param token     a {@link java.lang.String} object.
     * @param commitMsg a {@link java.lang.String} object.
     * @param username  a {@link java.lang.String} object.
     * @param email     a {@link java.lang.String} object.
     * @return a {@link java.lang.String} object.
     */
    public static String deleteFile(String owner, String repo, String path, String token, String commitMsg, String username, String email) {

        String base = "https://api.github.com/repos/%s/%s/contents%s";
        String uploadUrl = String.format(base, owner, repo, path);

        Map<String, ShaInfo> shas = getSha(owner, repo, path, token);

        if (shas == null || shas.size() < 1) {
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
        for (int i = 0; i < paths.length; i++) {
            deleteDir(owner, repo, paths[i], token, commitMsg, "", "");
        }

    }

    public static void deleteDir(String owner, String repo, String path, String token, String commitMsg) {
        deleteDir(owner, repo, path, token, commitMsg, "", "");
    }

    public static void deleteDir(String owner, String repo, String path, String token, String commitMsg, String username, String email) {

        String base = "https://api.github.com/repos/%s/%s/contents%s";
//        String uploadUrl = String.format(base, owner, repo, path);
        Map<String, ShaInfo> shas = getSha(owner, repo, path, token);
        if (shas == null || shas.size() < 1) {
            return;
        }

        if (shas.size() > 0) {
            //  编译删除文件夹中文件
            for (Map.Entry<String, ShaInfo> entry : shas.entrySet()) {
                ShaInfo s = entry.getValue();
                if (s != null) {
                    String ps = s.path.startsWith("/") ? (s.path) : ("/" + s.path);
                    //// type: file、dir
                    if ("file".endsWith(s.type)) {
                        realDelFileBySha(String.format(base, owner, repo, ps), s.sha, token, commitMsg, username, email);
                    } else {
                        deleteDir(owner, repo, ps, token, commitMsg, username, email);
                    }
                }
            }
        }

    }

    private static String realDelFileBySha(String url, String sha, String token, String commitMsg, String username, String email) {

        if (TextUtils.isEmpty(url) || TextUtils.isEmpty(sha) || TextUtils.isEmpty(token)) {
            return "";
        }
        String hasUserInfoBase = "{\"message\":\"%s\" ,\"sha\":\"%s\" ,\"committer\":{ \"name\":\"%s\",\"email\":\"%s\" }}";
        String hasNoUserInfoBase = "{\"message\":\"%s\", \"sha\":\"%s\" }";
        String data = String.format(hasNoUserInfoBase, commitMsg, sha);
        if (!TextUtils.isEmpty(username) && !TextUtils.isEmpty(email)) {
            data = String.format(hasUserInfoBase, commitMsg, sha, username, email);
        }
        String result = Jnt.request(HttpType.DELETE, DEF_TIMEOUT, url, null, getHttpHeader(token), data);
//        System.out.println(url + "---->" + result);

        return result;
    }


    /**
     * github创建文件. need POST
     *
     * @param owner             用户名
     * @param repo              项目名
     * @param path              创建文件针对对应项目的相对路径
     * @param token             授权token
     * @param contentWillBase64 原始字符串
     * @param commitMsg         提交msg
     * @return a {@link java.lang.String} object.
     */
    public static String createFile(String owner, String repo, String path, String token, String contentWillBase64, String commitMsg) {
        return createFile(true, owner, repo, path, token, contentWillBase64, commitMsg, "", "");
    }


    public static String createFile(String owner, String repo, String path,  File file, String commitMsg) {
        return createFile(false, owner, repo, path, token, FileUtils.getBase64FromFile(file), commitMsg, "", "");
    }
    /**
     * <p>createFile.</p>
     *
     * @param owner     a {@link java.lang.String} object.
     * @param repo      a {@link java.lang.String} object.
     * @param path      a {@link java.lang.String} object.
     * @param token     a {@link java.lang.String} object.
     * @param file      a {@link java.io.File} object.
     * @param commitMsg a {@link java.lang.String} object.
     * @return a {@link java.lang.String} object.
     */
    public static String createFile(String owner, String repo, String path, String token, File file, String commitMsg) {
        return createFile(false, owner, repo, path, token, FileUtils.getBase64FromFile(file), commitMsg, "", "");
    }


    /**
     * <p>createFile.</p>
     *
     * @param isNeedBase64  a boolean.
     * @param owner         a {@link java.lang.String} object.
     * @param repo          a {@link java.lang.String} object.
     * @param path          a {@link java.lang.String} object.
     * @param token         a {@link java.lang.String} object.
     * @param uploadContent a {@link java.lang.String} object.
     * @param commitMsg     a {@link java.lang.String} object.
     * @param username      a {@link java.lang.String} object.
     * @param email         a {@link java.lang.String} object.
     * @return a {@link java.lang.String} object.
     */
    public static String createFile(boolean isNeedBase64, String owner, String repo, String path, String token, String uploadContent, String commitMsg, String username, String email) {
        try {

            Map<String, ShaInfo> shas = getSha(owner, repo, path, token);
            if (shas != null && shas.size() > 0) {
                ShaInfo s = shas.get(path);
                if (s != null && !TextUtils.isEmpty(s.download_url)) {
                    System.out.println("已经有了文件,路径: " + s.download_url);
                    return s.download_url;
                }
            }

            String content = TextUtils.encodeBase64ToString(uploadContent, isNeedBase64);
            String base = "https://api.github.com/repos/%s/%s/contents%s";
            String uploadUrl = String.format(base, owner, repo, path);

            String hasUserInfoBase = "{\"content\":\"%s\",\"message\":\"%s\" ,\"committer\":{ \"name\":\"%s\",\"email\":\"%s\" }}";
            String hasNoUserInfoBase = "{\"content\":\"%s\",\"message\":\"%s\" }";
            String data = String.format(hasNoUserInfoBase, content, commitMsg);
            if (!TextUtils.isEmpty(username) && !TextUtils.isEmpty(email)) {
                data = String.format(hasUserInfoBase, content, commitMsg, username, email);
            }
            String res = Jnt.request(HttpType.PUT, DEF_TIMEOUT, uploadUrl, null, getHttpHeader(token), data);

            if (TextUtils.isEmpty(res)) {
                return "";
            }
            return new JSONObject(res).optJSONObject("content").optString("download_url", "");
        } catch (Throwable e) {
            e.printStackTrace();
        }

        return "";
    }


    public static String getContent(String owner, String repo, String path) {
        return getContent(owner, repo, path, token);
    }

    public static String getContent(String owner, String repo, String path, String token) {
        Map<String, ShaInfo> mps = getSha(owner, repo, path, token);
        if (mps == null || mps.size() < 1) {
            return "";
        }
        ShaInfo info = mps.get(path);
        if ("base64".equalsIgnoreCase(info.encoding)) {
            return TextUtils.tryDecodeBase64ToString(info.content);
        }
        return "";
    }

    /**
     * 这个API只支持1MB以内的获取
     * //https://api.github.com/repos/hhhaiai/ManagerApk/contents/VMOS-Pro_1.3.apk
     * {
     * "message": "This API returns blobs up to 1 MB in size. The requested blob is too large to fetch via the API, but you can use the Git Data API to request blobs up to 100 MB in size.",
     * "errors": [
     * {
     * "resource": "Blob",
     * "field": "data",
     * "code": "too_large"
     * }
     * ],
     * "documentation_url": "https://docs.github.com/rest/reference/repos#get-repository-content"
     * }
     *
     * @param owner a {@link String} object.
     * @param repo  a {@link String} object.
     * @param path  a {@link String} object.
     * @param token
     * @return
     */
    public static Map<String, ShaInfo> getSha(String owner, String repo, String path, String token) {
        Map<String, ShaInfo> shaBody = new HashMap<String, ShaInfo>();
        try {
            String base = "https://api.github.com/repos/%s/%s/contents%s";
            String requestUrl = String.format(base, owner, repo, path);
//            System.out.println("getSha url:" + requestUrl);

            // add header, support private token
            String result = Jnt.get(requestUrl, getHttpHeader(token));
            // update map
            if (TextUtils.isEmpty(result)) {
                return shaBody;
            }
            try {

                JSONObject obj = new JSONObject(result);
                JSONObject links = obj.optJSONObject("_links");
                shaBody.put(path, new ShaInfo(obj.optString("name", null), obj.optString("path", null), obj.optString("sha", null), obj.optLong("size", -1L), obj.optString("url", null), obj.optString("html_url", null), obj.optString("git_url", null), obj.optString("download_url", null), obj.optString("type", null), links, (links == null ? "" : links.optString("self", null)), (links == null ? "" : links.optString("git", null)), (links == null ? "" : links.optString("html", null)), obj.optString("content", null), obj.optString("encoding", null)));
            } catch (JSONException e) {
                try {
                    JSONArray arr = new JSONArray(result);
                    for (int i = 0; i < arr.length(); i++) {
                        JSONObject obj = arr.optJSONObject(i);
                        if (obj != null) {
                            JSONObject links = obj.optJSONObject("_links");
                            String pth = obj.optString("path", null);

                            shaBody.put(pth, new ShaInfo(obj.optString("name", null), obj.optString("path", null), obj.optString("sha", null), obj.optLong("size", -1L), obj.optString("url", null), obj.optString("html_url", null), obj.optString("git_url", null), obj.optString("download_url", null), obj.optString("type", null), links, (links == null ? "" : links.optString("self", null)), (links == null ? "" : links.optString("git", null)), (links == null ? "" : links.optString("html", null)), obj.optString("content", null), obj.optString("encoding", null)));
                        }
                    }

                } catch (JSONException ex) {
                    ex.printStackTrace();
                }
            }

        } catch (Throwable e) {
            e.printStackTrace();
        }

        return shaBody;
    }


    public static Map<String, String> getHttpHeader(String token) {
        Map<String, String> reqHeaderMap = new HashMap<String, String>();
//        reqHeaderMap.put("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/86.0.4240.111 Safari/537.36");
        reqHeaderMap.put("User-Agent", "Github createFile By Java");
        reqHeaderMap.put("Content-Type", "charset=UTF-8");
        reqHeaderMap.put("Accept", "application/vnd.github.v3+json");
        //accept: text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3;q=0.9

        reqHeaderMap.put("Authorization", "token " + token);
//        reqHeaderMap.put("accept-encoding", "gzip, deflate, br");
        reqHeaderMap.put("accept-language", "zh-CN,zh;q=0.9,en;q=0.8");
        return reqHeaderMap;
    }


    /**
     * @Copyright © 2022 sanbo Inc. All rights reserved.
     * @Description: sha获取模型，需要考虑兼容几种方式: 文件夹path获取的文件、文件夹path获取的文件夹、文件path获取的文件。样例数据如下:
     * <p>
     * 1. 文件夹path请求的文件、文件夹
     * <code>
     * [
     * {
     * "name": "124113-CheckDemo-dev-sdk",
     * "path": "ci/20211229/124113-CheckDemo-dev-sdk",
     * "sha": "1a29509a11cc85b3414c7420fa7a4419cde251bd",
     * "size": 0,
     * "url": "https://api.github.com/repos/hhhaiai/Git_result/contents/ci/20211229/124113-CheckDemo-dev-sdk?ref=main",
     * "html_url": "https://github.com/hhhaiai/Git_result/tree/main/ci/20211229/124113-CheckDemo-dev-sdk",
     * "git_url": "https://api.github.com/repos/hhhaiai/Git_result/git/trees/1a29509a11cc85b3414c7420fa7a4419cde251bd",
     * "download_url": null,
     * "type": "dir",
     * "_links": {
     * "self": "https://api.github.com/repos/hhhaiai/Git_result/contents/ci/20211229/124113-CheckDemo-dev-sdk?ref=main",
     * "git": "https://api.github.com/repos/hhhaiai/Git_result/git/trees/1a29509a11cc85b3414c7420fa7a4419cde251bd",
     * "html": "https://github.com/hhhaiai/Git_result/tree/main/ci/20211229/124113-CheckDemo-dev-sdk"
     * }
     * },
     * {
     * "name": "CheckDemo_122109_spoon-24-google_apis-x86_64.tgz",
     * "path": "ci/20211229/CheckDemo_122109_spoon-24-google_apis-x86_64.tgz",
     * "sha": "5e180d6749cec35f153ed1b8341226fa2133aea4",
     * "size": 984005,
     * "url": "https://api.github.com/repos/hhhaiai/Git_result/contents/ci/20211229/CheckDemo_122109_spoon-24-google_apis-x86_64.tgz?ref=main",
     * "html_url": "https://github.com/hhhaiai/Git_result/blob/main/ci/20211229/CheckDemo_122109_spoon-24-google_apis-x86_64.tgz",
     * "git_url": "https://api.github.com/repos/hhhaiai/Git_result/git/blobs/5e180d6749cec35f153ed1b8341226fa2133aea4",
     * "download_url": "https://raw.githubusercontent.com/hhhaiai/Git_result/main/ci/20211229/CheckDemo_122109_spoon-24-google_apis-x86_64.tgz",
     * "type": "file",
     * "_links": {
     * "self": "https://api.github.com/repos/hhhaiai/Git_result/contents/ci/20211229/CheckDemo_122109_spoon-24-google_apis-x86_64.tgz?ref=main",
     * "git": "https://api.github.com/repos/hhhaiai/Git_result/git/blobs/5e180d6749cec35f153ed1b8341226fa2133aea4",
     * "html": "https://github.com/hhhaiai/Git_result/blob/main/ci/20211229/CheckDemo_122109_spoon-24-google_apis-x86_64.tgz"
     * }
     * }
     * ]
     * </code>
     * <p>
     * 2. 文件path请求的文件
     * <code>
     * {
     * "name":"CheckDemo_122109_spoon-24-google_apis-x86_64.tgz",
     * "path":"ci/20211229/CheckDemo_122109_spoon-24-google_apis-x86_64.tgz",
     * "sha":"5e180d6749cec35f153ed1b8341226fa2133aea4",
     * "size":984005,
     * "url":"https://api.github.com/repos/hhhaiai/Git_result/contents/ci/20211229/CheckDemo_122109_spoon-24-google_apis-x86_64.tgz?ref=main",
     * "html_url":"https://github.com/hhhaiai/Git_result/blob/main/ci/20211229/CheckDemo_122109_spoon-24-google_apis-x86_64.tgz",
     * "git_url":"https://api.github.com/repos/hhhaiai/Git_result/git/blobs/5e180d6749cec35f153ed1b8341226fa2133aea4",
     * "download_url":"https://raw.githubusercontent.com/hhhaiai/Git_result/main/ci/20211229/CheckDemo_122109_spoon-24-google_apis-x86_64.tgz",
     * "type":"file",
     * "content":"HT09PT09PQo=\n",
     * "encoding":"base64",
     * "_links":{
     * "self":"https://api.github.com/repos/hhhaiai/Git_result/contents/ci/20211229/CheckDemo_122109_spoon-24-google_apis-x86_64.tgz?ref=main",
     * "git":"https://api.github.com/repos/hhhaiai/Git_result/git/blobs/5e180d6749cec35f153ed1b8341226fa2133aea4",
     * "html":"https://github.com/hhhaiai/Git_result/blob/main/ci/20211229/CheckDemo_122109_spoon-24-google_apis-x86_64.tgz"
     * }
     * }
     * </code>
     * @Version: 1.0
     * @Create: 2022/01/06 10:38:44
     * @author: sanbo
     */
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
        // only in 文件path请求的文件
        public String content = null;
        public String encoding = null;

        // dir request
        public ShaInfo(String __name, String __path, String __sha, long __size, String __url, String __html_url, String __git_url, String __download_url, String __type, JSONObject ___links, String ___links_key_self, String ___links_key_git, String ___links_key_html) {
            this(__name, __path, __sha, __size, __url, __html_url, __git_url, __download_url, __type, ___links, ___links_key_self, ___links_key_git, ___links_key_html, null, null);
        }

        public ShaInfo(String __name, String __path, String __sha, long __size, String __url, String __html_url, String __git_url, String __download_url, String __type, JSONObject ___links, String ___links_key_self, String ___links_key_git, String ___links_key_html
                       // only in 文件path请求的文件
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
