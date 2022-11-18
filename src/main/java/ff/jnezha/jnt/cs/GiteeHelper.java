package ff.jnezha.jnt.cs;

import ff.jnezha.jnt.Jnt;
import ff.jnezha.jnt.utils.HttpType;
import ff.jnezha.jnt.utils.Logger;
import ff.jnezha.jnt.utils.TextUitls;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @Copyright © 2020 sanbo Inc. All rights reserved.
 * @Description: gitee API操作工具类，gitee api: https://gitee.com/api/v5/swagger#/postV5ReposOwnerRepoContentsPath
 * @version: 1.0
 * @Create: 2020-12-09 14:15:12
 * @Author: sanbo
 */
public class GiteeHelper {

    private static final int DEF_TIMEOUT = 50 * 1000;

    /**
     * gitee创建文件. need POST
     *
     * @param owner             用户名
     * @param repo              项目名
     * @param path              创建文件针对对应项目的相对路径
     * @param token             授权token
     * @param contentWillBase64 原始字符串
     * @param commitMsg         提交msg
     * @return a {@link String} object.
     */
    public static String createFile(String owner, String repo, String path, String token, String contentWillBase64, String commitMsg) {
        return createFile(owner, repo, path, token, contentWillBase64, commitMsg, "", "");
    }

    /**
     * gitee创建文件. need POST
     * 测试了下 貌似下面的提交者没生效,已经反馈官方
     * {
     * "必要部分":"下面这部分必须包含"，
     * "access_token":"{access_token}",
     * "content":"{base(text)}",
     * "message":"commit msg",
     * "可选部分"："下面部分可选",
     * "branch":"分支名称。默认为仓库对默认分支",
     * "committer[name]":"Committer的名字，默认为当前用户的名字",
     * "committer[email]":"Committer的邮箱，默认为当前用户的邮箱",
     * "author[name]":"Author的名字，默认为当前用户的名字",
     * "author[email]":"Author的邮箱，默认为当前用户的邮箱"
     * <p>
     * }
     *
     * @param owner             用户名
     * @param repo              项目名
     * @param path              创建文件针对对应项目的相对路径
     * @param token             授权token
     * @param contentWillBase64 原始字符串
     * @param commitMsg         提交msg
     * @param author            提交者名称
     * @param email             提交者email
     * @return a {@link String} object.
     */
    public static String createFile(String owner, String repo, String path, String token, String contentWillBase64, String commitMsg, String author, String email) {

        String content = TextUitls.encodeBase64ToString(contentWillBase64);
        String base = "https://gitee.com/api/v5/repos/%s/%s/contents/%s";
        String uploadUrl = String.format(base, owner, repo, path);
//        System.out.println(uploadUrl);
        String baseA = "{\"access_token\":\"%s\",\"content\":\"%s\",\"message\":\"%s\"}";
        String baseB = "{\"access_token\":\"%s\",\"content\":\"%s\",\"message\":\"%s\",\"committer[name]\":\"%s\",\"committer[email]\":\"%s\",\"author[name]\":\"%s\",\"author[email]\":\"%s\"}";
        String data = String.format(baseA, token, content, commitMsg);
        if (!TextUitls.isEmpty(author) && !TextUitls.isEmpty(email)) {
            data = String.format(baseB, token, content, commitMsg, author, email, author, email);
        }
        String result = Jnt.request(HttpType.POST, DEF_TIMEOUT, uploadUrl, null, getHeader(), data);
        String resultBase = "https://gitee.com/%s/%s/raw/master/%s";
        if (!TextUitls.isEmpty(result)) {
            return String.format(resultBase, owner, repo, path);
        }
        return "";
    }

    /**
     * 更新文件 . need PUT
     *
     * @param owner     a {@link String} object.
     * @param repo      a {@link String} object.
     * @param path      a {@link String} object.
     * @param token     a {@link String} object.
     * @param commitMsg a {@link String} object.
     * @return a {@link String} object.
     */
    public static String deleteFile(String owner, String repo, String path, String token, String commitMsg) {
        return deleteFile(owner, repo, path, token, commitMsg, "", "");
    }

    /**
     * 更新文件 . need PUT
     *
     * @param owner     a {@link String} object.
     * @param repo      a {@link String} object.
     * @param path      a {@link String} object.
     * @param token     a {@link String} object.
     * @param commitMsg a {@link String} object.
     * @param author    a {@link String} object.
     * @param email     a {@link String} object.
     * @return a {@link String} object.
     */
    public static String deleteFile(String owner, String repo, String path, String token, String commitMsg, String author, String email) {

        String base = "https://gitee.com/api/v5/repos/%s/%s/contents/%s";
        String uploadUrl = String.format(base, owner, repo, path);
        String sha = getSha(owner, repo, path, token);

        if (!TextUitls.isEmpty(sha)) {
            /**
             *{
             *     "必要部分":"下面这部分必须包含"，
             *     "access_token":"{access_token}",
             *     "content":"{base(text)}",
             *     "message":"commit msg",
             *     "sha":"文件的 Blob SHA",
             *     "可选部分"："下面部分可选",
             *     "branch":"分支名称。默认为仓库对默认分支",
             *     "committer[name]":"Committer的名字，默认为当前用户的名字",
             *     "committer[email]":"Committer的邮箱，默认为当前用户的邮箱",
             *     "author[name]":"Author的名字，默认为当前用户的名字",
             *     "author[email]":"Author的邮箱，默认为当前用户的邮箱"
             * }
             *
             */
            String baseA = "{\"access_token\":\"%s\",\"message\":\"%s\",\"sha\":\"%s\"}";
            String baseB = "{\"access_token\":\"%s\",\"message\":\"%s\",\"sha\":\"%s\",\"committer[name]\":\"%s\",\"committer[email]\":\"%s\",\"author[name]\":\"%s\",\"author[email]\":\"%s\"}";
            String data = String.format(baseA, token, commitMsg, sha);
            if (!TextUitls.isEmpty(author) && !TextUitls.isEmpty(email)) {
                data = String.format(baseB, token, commitMsg, sha, author, email, author, email);
            }
            return Jnt.request(HttpType.DELETE, DEF_TIMEOUT, uploadUrl, null, getHeader(), data);
        } else {
            return "";
        }


    }

    /**
     * 更新文件 . need PUT
     *
     * @param owner             a {@link String} object.
     * @param repo              a {@link String} object.
     * @param path              a {@link String} object.
     * @param token             a {@link String} object.
     * @param contentWillBase64 a {@link String} object.
     * @param commitMsg         a {@link String} object.
     * @return a {@link String} object.
     */
    public static String updateContent(String owner, String repo, String path, String token, String contentWillBase64, String commitMsg) {
        return updateContent(owner, repo, path, token, contentWillBase64, commitMsg, "", "");
    }

    /**
     * 更新文件 . need PUT
     *
     * @param owner             a {@link String} object.
     * @param repo              a {@link String} object.
     * @param path              a {@link String} object.
     * @param token             a {@link String} object.
     * @param contentWillBase64 a {@link String} object.
     * @param commitMsg         a {@link String} object.
     * @param author            a {@link String} object.
     * @param email             a {@link String} object.
     * @return a {@link String} object.
     */
    public static String updateContent(String owner, String repo, String path, String token, String contentWillBase64, String commitMsg, String author, String email) {

        String content = TextUitls.encodeBase64ToString(contentWillBase64);
        String base = "https://gitee.com/api/v5/repos/%s/%s/contents/%s";
        String uploadUrl = String.format(base, owner, repo, path);
//        System.out.println(uploadUrl);
        String sha = getSha(owner, repo, path, token);

        /**
         *{
         *     "必要部分":"下面这部分必须包含"，
         *     "access_token":"{access_token}",
         *     "content":"{base(text)}",
         *     "message":"commit msg",
         *     "sha":"文件的 Blob SHA",
         *     "可选部分"："下面部分可选",
         *     "branch":"分支名称。默认为仓库对默认分支",
         *     "committer[name]":"Committer的名字，默认为当前用户的名字",
         *     "committer[email]":"Committer的邮箱，默认为当前用户的邮箱",
         *     "author[name]":"Author的名字，默认为当前用户的名字",
         *     "author[email]":"Author的邮箱，默认为当前用户的邮箱"
         *
         * }
         *
         */
        String baseA = "{\"access_token\":\"%s\",\"content\":\"%s\",\"message\":\"%s\",\"sha\":\"%s\"}";
        String baseB = "{\"access_token\":\"%s\",\"content\":\"%s\",\"message\":\"%s\",\"sha\":\"%s\",\"committer[name]\":\"%s\",\"committer[email]\":\"%s\",\"author[name]\":\"%s\",\"author[email]\":\"%s\"}";
        String data = String.format(baseA, token, content, commitMsg, sha);
        if (!TextUitls.isEmpty(author) && !TextUitls.isEmpty(email)) {
            data = String.format(baseB, token, content, commitMsg, sha, author, email, author, email);
        }

        return Jnt.request(HttpType.PUT, DEF_TIMEOUT, uploadUrl, null, getHeader(), data);
    }


    /**
     * 获取SHA. need GET
     *
     * @param owner 用户名
     * @param repo  项目名
     * @param path  创建文件针对对应项目的相对路径
     * @param token 授权token
     * @return a {@link String} object.
     */
    public static String getSha(String owner, String repo, String path, String token) {
        try {
            String base = "https://gitee.com/api/v5/repos/%s/%s/contents/%s?access_token=%s";
            String requestUrl = String.format(base, owner, repo, path, token);
            String result = Jnt.request(HttpType.GET, DEF_TIMEOUT, requestUrl, null, getHeader(), null);
            Matcher matcher = Pattern.compile("\"sha\": *\"([^\"]+)\"").matcher(result);
            while (matcher.find()) {
                return matcher.group(1);
            }
        } catch (Throwable e) {
            Logger.e(e);
        }

        return "";
    }

    private static Map<String, String> getHeader() {
        Map<String, String> reqHeaderMap = new HashMap<String, String>();
        reqHeaderMap.put("Content-Type", "application/json;charset=UTF-8");
//        reqHeaderMap.put("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/86.0.4240.111 Safari/537.36");
        reqHeaderMap.put("User-Agent", "Gitee");
//        reqHeaderMap.put("Accept-Encoding", "gzip, deflate, br");
//        reqHeaderMap.put("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8");
        return reqHeaderMap;
    }
}
