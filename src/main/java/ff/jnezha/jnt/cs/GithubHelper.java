package ff.jnezha.jnt.cs;

import ff.jnezha.jnt.Jnt;
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
    public static void updateContent(String owner, String repo, String path, String token, String contentWillBase64, String commitMsg) {
        updateContent(owner, repo, path, token, contentWillBase64, commitMsg, "", "");
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
        JSONObject shaJSON = getSha(owner, repo, path, token);

        // 如没有该文件，则新建
        if (shaJSON.length() <= 0 || !shaJSON.has("sha")) {
            createFile(owner, repo, path, token, contentWillBase64, commitMsg);
        }
        if (shaJSON.has("sha")) {
            String sha = shaJSON.optString("sha", "");
            if (!TextUtils.isEmpty(sha)) {
                String hasUserInfoBase = "{\"content\":\"%s\",\"message\":\"%s\" ,\"sha\":\"%s\" ,\"committer\":{ \"name\":\"%s\",\"email\":\"%s\" }}";
                String hasNoUserInfoBase = "{\"content\":\"%s\",\"message\":\"%s\", \"sha\":\"%s\" }";
                String data = String.format(hasNoUserInfoBase, content, commitMsg, sha);
                if (!TextUtils.isEmpty(username) && !TextUtils.isEmpty(email)) {
                    data = String.format(hasUserInfoBase, content, commitMsg, sha, username, email);
                }
                return Jnt.request(HttpType.PUT, DEF_TIMEOUT, uploadUrl, null, getHttpHeader(token), data);
            }
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

        JSONObject shaJson = getSha(owner, repo, path, token);

        if (shaJson.length() == 0 || !shaJson.has("sha")) {
            return "";
        }
        String sha = shaJson.optString("sha", "");
        if (!TextUtils.isEmpty(sha)) {
            String hasUserInfoBase = "{\"message\":\"%s\" ,\"sha\":\"%s\" ,\"committer\":{ \"name\":\"%s\",\"email\":\"%s\" }}";
            String hasNoUserInfoBase = "{\"message\":\"%s\", \"sha\":\"%s\" }";
            String data = String.format(hasNoUserInfoBase, commitMsg, sha);
            if (!TextUtils.isEmpty(username) && !TextUtils.isEmpty(email)) {
                data = String.format(hasUserInfoBase, commitMsg, sha, username, email);
            }
            return Jnt.request(HttpType.DELETE, DEF_TIMEOUT, uploadUrl, null, getHttpHeader(token), data);
        }
        return "";
    }

    /**
     * gitee创建文件. need POST
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
            JSONObject shaJson = getSha(owner, repo, path, token);
            if (shaJson.length() > 0 && shaJson.has("sha")) {
                String downUrl = shaJson.optString("download_url", "");
                System.out.println("已经有了文件,路径: " + downUrl);
                return downUrl;
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
     * @param owner a {@link java.lang.String} object.
     * @param repo  a {@link java.lang.String} object.
     * @param path  a {@link java.lang.String} object.
     * @param token a {@link java.lang.String} object.
     * @return a {@link java.lang.String} object.
     */
    public static JSONObject getSha(String owner, String repo, String path, String token) {
        try {
            String base = "https://api.github.com/repos/%s/%s/contents%s";
            String requestUrl = String.format(base, owner, repo, path, token);
//            System.out.println("getSha url:" + requestUrl);

            String result = Jnt.request(HttpType.GET, DEF_TIMEOUT, requestUrl, null, null, null);
            // update map
            if (TextUtils.isEmpty(result)) {
                return new JSONObject();
            }
            return new JSONObject(result);

        } catch (Throwable e) {
            e.printStackTrace();
        }

        return new JSONObject();
    }

    public static Map<String, String> getHttpHeader(String token) {
        Map<String, String> reqHeaderMap = new HashMap<String, String>();
//        reqHeaderMap.put("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/86.0.4240.111 Safari/537.36");
        reqHeaderMap.put("User-Agent", "Github createFile By Java");
        reqHeaderMap.put("Content-Type", "charset=UTF-8");
        reqHeaderMap.put("Accept", "application/vnd.github.v3+json");
        //accept: text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3;q=0.9

        reqHeaderMap.put("Authorization", "token " + token);
        reqHeaderMap.put("accept-encoding", "gzip, deflate, br");
        reqHeaderMap.put("accept-language", "zh-CN,zh;q=0.9,en;q=0.8");
        return reqHeaderMap;
    }

}
