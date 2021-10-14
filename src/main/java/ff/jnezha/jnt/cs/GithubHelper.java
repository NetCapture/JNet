package ff.jnezha.jnt.cs;

import ff.jnezha.jnt.Jnt;
import ff.jnezha.jnt.utils.FileUtils;
import ff.jnezha.jnt.utils.HttpType;
import ff.jnezha.jnt.utils.TextUtils;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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


    /**
     * <p>main.</p>
     *
     * @param args an array of {@link java.lang.String} objects.
     */
    public static void main(String[] args) {

        String sw="-Z2hwX2FUSFdIb1ZVdEg1QnZrWnJhRmZET3RpSmxKcnpVWTFrc3lOZg==-";
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
        String content = Base64.getEncoder().encodeToString(contentWillBase64.getBytes(StandardCharsets.UTF_8));
        // 据RFC 822规定，每76个字符，还需要加上一个回车换行
        // 有时就因为这些换行弄得出了问题，解决办法如下，替换所有换行和回车
        // result = result.replaceAll("[\\s*\t\n\r]", "");
        content = content.replaceAll("[\\s*]", "");
        Map<String, String> reqHeaderMap = new HashMap<String, String>(6);
        reqHeaderMap.put("Content-Type", "application/json;charset=UTF-8");
        reqHeaderMap.put("Authorization", "token " + token);
        reqHeaderMap.put("User-Agent", "Github updateFileContent By Java");
        String base = "https://api.github.com/repos/%s/%s/contents%s";
        String uploadUrl = String.format(base, owner, repo, path);
//        System.out.println(uploadUrl);
        String sha = getSha(owner, repo, path, token);

        String hasUserInfoBase = "{\"content\":\"%s\",\"message\":\"%s\" ,\"sha\":\"%s\" ,\"committer\":{ \"name\":\"%s\",\"email\":\"%s\" }}";
        String hasNoUserInfoBase = "{\"content\":\"%s\",\"message\":\"%s\", \"sha\":\"%s\" }";
        String data = String.format(hasNoUserInfoBase, content, commitMsg, sha);
        if (!TextUtils.isEmpty(username) && !TextUtils.isEmpty(email)) {
            data = String.format(hasUserInfoBase, content, commitMsg, sha, username, email);
        }
//        System.out.println(data);
        int timeout = 10 * 1000;
        return Jnt.request(HttpType.PUT, timeout, uploadUrl, null, reqHeaderMap, data);
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

        Map<String, String> reqHeaderMap = new HashMap<String, String>(10);
        reqHeaderMap.put("Content-Type", "application/json;charset=UTF-8");
        reqHeaderMap.put("Authorization", "token " + token);
        reqHeaderMap.put("User-Agent", "Github deleteFile By Java");
        String base = "https://api.github.com/repos/%s/%s/contents%s";
        String uploadUrl = String.format(base, owner, repo, path);
//        System.out.println(uploadUrl);
        String sha = getSha(owner, repo, path, token);

        String hasUserInfoBase = "{\"message\":\"%s\" ,\"sha\":\"%s\" ,\"committer\":{ \"name\":\"%s\",\"email\":\"%s\" }}";
        String hasNoUserInfoBase = "{\"message\":\"%s\", \"sha\":\"%s\" }";
        String data = String.format(hasNoUserInfoBase, commitMsg, sha);
        if (!TextUtils.isEmpty(username) && !TextUtils.isEmpty(email)) {
            data = String.format(hasUserInfoBase, commitMsg, sha, username, email);
        }
//        System.out.println(data);
        int timeout = 10 * 1000;
        return Jnt.request(HttpType.DELETE, timeout, uploadUrl, null, reqHeaderMap, data);
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
        String content = FileUtils.getBase64FromFile(file);
        return createFile(false, owner, repo, path, token, content, commitMsg, "", "");
    }

    private static Pattern downloadUrlPattern = Pattern.compile("\"download_url\": *\"([^\"]+)\"");
    private static Pattern getSha = Pattern.compile("\"sha\": *\"([^\"]+)\"");


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
            String content = null;
            if (isNeedBase64) {
                content = Base64.getEncoder().encodeToString(uploadContent.getBytes(StandardCharsets.UTF_8));
            } else {
                content = uploadContent;
            }
            // 据RFC 822规定，每76个字符，还需要加上一个回车换行
            // 有时就因为这些换行弄得出了问题，解决办法如下，替换所有换行和回车
            // result = result.replaceAll("[\\s*\t\n\r]", "");
            content = content.replaceAll("[\\s*]", "");
            Map<String, String> reqHeaderMap = new HashMap<String, String>(10);
            reqHeaderMap.put("Content-Type", "application/json;charset=UTF-8");
            reqHeaderMap.put("Authorization", "token " + token);
            reqHeaderMap.put("User-Agent", "Github createFile By Java");
            String base = "https://api.github.com/repos/%s/%s/contents%s";
            String uploadUrl = String.format(base, owner, repo, path);
            System.out.println(uploadUrl);

            String hasUserInfoBase = "{\"content\":\"%s\",\"message\":\"%s\" ,\"committer\":{ \"name\":\"%s\",\"email\":\"%s\" }}";
            String hasNoUserInfoBase = "{\"content\":\"%s\",\"message\":\"%s\" }";
            String data = String.format(hasNoUserInfoBase, content, commitMsg);
            if (!TextUtils.isEmpty(username) && !TextUtils.isEmpty(email)) {
                data = String.format(hasUserInfoBase, content, commitMsg, username, email);
            }
//        System.out.println(data);
            int timeout = 10 * 1000;
            String res = Jnt.request(HttpType.PUT, timeout, uploadUrl, null, reqHeaderMap, data);

            Matcher matcher = downloadUrlPattern.matcher(res.toString());
            while (matcher.find()) {
                return matcher.group(1);
            }
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
    public static String getSha(String owner, String repo, String path, String token) {
        try {
            String base = "https://api.github.com/repos/%s/%s/contents%s";
            String requestUrl = String.format(base, owner, repo, path, token);
//            System.out.println(requestUrl);
            Map<String, String> reqHeaderMap = new HashMap<String, String>();
//        reqHeaderMap.put("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/86.0.4240.111 Safari/537.36");
            reqHeaderMap.put("User-Agent", "Github createFile By Java");
            reqHeaderMap.put("Content-Type", "application/json;charset=UTF-8");
            reqHeaderMap.put("Authorization", "token " + token);
            reqHeaderMap.put("Accept-Encoding", "gzip, deflate, br");
            reqHeaderMap.put("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8");
            int timeout = 10 * 1000;
            String result = Jnt.request(HttpType.GET, timeout, requestUrl, null, reqHeaderMap, null);
            System.out.println("getSha result:" + result);
            Matcher matcher = getSha.matcher(result.toString());
            while (matcher.find()) {
                return matcher.group(1);
            }
        } catch (Throwable e) {
            e.printStackTrace();
        }

        return "";
    }
}
