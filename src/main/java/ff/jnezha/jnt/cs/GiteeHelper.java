package ff.jnezha.jnt.cs;

import ff.jnezha.jnt.Jnt;
import ff.jnezha.jnt.utils.HttpType;
import ff.jnezha.jnt.utils.Texts;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @Copyright © 2020 analysys Inc. All rights reserved.
 * @Description: gitee API操作工具类， api 地址:https://gitee.com/api/v5/swagger#/postV5ReposOwnerRepoContentsPath
 * @Version: 1.0
 * @Create: 2020-12-09 14:15:12
 * @author: sanbo
 */
public class GiteeHelper {


    /**
     * gitee创建文件. need POST
     * * 测试了下 貌似下面的提交者没生效
     * * <code>
     * *{
     * *     "必要部分":"下面这部分必须包含"，
     * *     "access_token":"{access_token}",
     * *     "content":"{base(text)}",
     * *     "message":"commit msg",
     * *     "可选部分"："下面部分可选",
     * *     "branch":"分支名称。默认为仓库对默认分支",
     * *     "committer[name]":"Committer的名字，默认为当前用户的名字",
     * *     "committer[email]":"Committer的邮箱，默认为当前用户的邮箱",
     * *     "author[name]":"Author的名字，默认为当前用户的名字",
     * *     "author[email]":"Author的邮箱，默认为当前用户的邮箱"
     * *
     * * }
     *
     * @param owner             用户名
     * @param repo              项目名
     * @param path              创建文件针对对应项目的相对路径
     * @param token             授权token
     * @param contentWillBase64 原始字符串
     * @param commitMsg         提交msg
     */
    public static String createFile(String owner, String repo, String path, String token, String contentWillBase64, String commitMsg) {

        String content = Base64.getEncoder().encodeToString(contentWillBase64.getBytes(StandardCharsets.UTF_8));
        // 据RFC 822规定，每76个字符，还需要加上一个回车换行
        // 有时就因为这些换行弄得出了问题，解决办法如下，替换所有换行和回车
        // result = result.replaceAll("[\\s*\t\n\r]", "");
        content = content.replaceAll("[\\s*]", "");
        Map<String, String> reqHeaderMap = new HashMap<String, String>();
        reqHeaderMap.put("Content-Type", "application/json;charset=UTF-8");
        reqHeaderMap.put("User-Agent", "Gitee");
        String base = "https://gitee.com/api/v5/repos/%s/%s/contents/%s";
        String uploadUrl = String.format(base, owner, repo, path);
//        System.out.println(uploadUrl);
        /**
         *
         * </code>
         */

        String data = String.format("{\"access_token\":\"%s\",\"content\":\"%s\",\"message\":\"%s\"}", token, content, commitMsg);
//        System.out.println(data);
        int timeout = 10 * 1000;
        String result = Jnt.request(HttpType.POST, timeout, uploadUrl, null, reqHeaderMap, data);
        String resultBase = "https://gitee.com/%s/%s/raw/master/%s";
//        System.out.println("result:" +result);
        if (!Texts.isEmpty(result)) {
            return String.format(resultBase, owner, repo, path);
        }
        return "";
    }

    /**
     * 更新文件 . need PUT
     *
     * @param owner
     * @param repo
     * @param path
     * @param token
     * @param commitMsg
     */
    public static void deleteFile(String owner, String repo, String path, String token, String commitMsg) {
        Map<String, String> reqHeaderMap = new HashMap<String, String>();
        reqHeaderMap.put("Content-Type", "application/json;charset=UTF-8");
        reqHeaderMap.put("User-Agent", "Gitee");
        String base = "https://gitee.com/api/v5/repos/%s/%s/contents/%s";
        String uploadUrl = String.format(base, owner, repo, path);
//        System.out.println(uploadUrl);
        String sha = getSha(owner, repo, path, token);

        /**
         * 测试了下 貌似下面的提交者没生效
         * <code>
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
         * </code>
         */

        String data = String.format("{\"access_token\":\"%s\",\"message\":\"%s\",\"sha\":\"%s\"}", token, commitMsg, sha);
        System.out.println(data);
        int timeout = 10 * 1000;
        Jnt.request(HttpType.DELETE, timeout, uploadUrl, null, reqHeaderMap, data);

    }

    /**
     * 更新文件 . need PUT
     *
     * @param owner
     * @param repo
     * @param path
     * @param token
     * @param contentWillBase64
     * @param commitMsg
     */
    public static void updateContent(String owner, String repo, String path, String token, String contentWillBase64, String commitMsg) {

        String content = Base64.getEncoder().encodeToString(contentWillBase64.getBytes(StandardCharsets.UTF_8));
        // 据RFC 822规定，每76个字符，还需要加上一个回车换行
        // 有时就因为这些换行弄得出了问题，解决办法如下，替换所有换行和回车
        // result = result.replaceAll("[\\s*\t\n\r]", "");
        content = content.replaceAll("[\\s*]", "");
        Map<String, String> reqHeaderMap = new HashMap<String, String>();
        reqHeaderMap.put("Content-Type", "application/json;charset=UTF-8");
        reqHeaderMap.put("User-Agent", "Gitee");
        String base = "https://gitee.com/api/v5/repos/%s/%s/contents/%s";
        String uploadUrl = String.format(base, owner, repo, path);
//        System.out.println(uploadUrl);
        String sha = getSha(owner, repo, path, token);

        /**
         * 测试了下 貌似下面的提交者没生效
         * <code>
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
         * </code>
         */

        String data = String.format("{\"access_token\":\"%s\",\"content\":\"%s\",\"message\":\"%s\",\"sha\":\"%s\"}", token, content, commitMsg, sha);
        System.out.println(data);
        int timeout = 10 * 1000;
        Jnt.request(HttpType.PUT, timeout, uploadUrl, null, reqHeaderMap, data);
    }

    /**
     * 获取SHA. need GET
     *
     * @param owner 用户名
     * @param repo  项目名
     * @param path  创建文件针对对应项目的相对路径
     * @param token 授权token
     */
    public static String getSha(String owner, String repo, String path, String token) {
        try {
            String base = "https://gitee.com/api/v5/repos/%s/%s/contents/%s?access_token=%s";
            String requestUrl = String.format(base, owner, repo, path, token);
//            System.out.println(requestUrl);
            Map<String, String> reqHeaderMap = new HashMap<String, String>();
            reqHeaderMap.put("Content-Type", "application/json;charset=UTF-8");
//        reqHeaderMap.put("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/86.0.4240.111 Safari/537.36");
            reqHeaderMap.put("User-Agent", "Gitee");
//        reqHeaderMap.put("Accept-Encoding", "gzip, deflate, br");
//        reqHeaderMap.put("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8");
            int timeout = 10 * 1000;

            String result = Jnt.request(HttpType.GET, timeout, requestUrl, null, reqHeaderMap, null);
//            JSONObject obj = JSON.parseObject(result);
////            System.out.println(obj);
//            if (obj.size() > 0) {
//                String url = obj.getString("url");
////                System.out.println(url);
////                System.out.println(requestUrl);
//                if (requestUrl.contains(url)) {
////                    System.out.println("same requet url");
//                    return obj.getString("sha");
//                } else {
//                    System.err.println("request url diff!");
//                }
//            } else {
//                System.err.println("response json len is 0");
//            }

            Matcher matcher = Pattern.compile("\"sha\": *\"([^\"]+)\"").matcher(result.toString());
            while (matcher.find()) {
                return matcher.group(1);
            }
        } catch (Throwable e) {
            e.printStackTrace();
        }

        return "";
    }


}
