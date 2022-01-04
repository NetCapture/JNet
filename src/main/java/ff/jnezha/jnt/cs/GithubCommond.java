package ff.jnezha.jnt.cs;


import ff.jnezha.jnt.utils.FileUtils;

import java.io.File;
import java.util.Arrays;
import java.util.List;

/**
 * @Copyright © 2021 sanbo Inc. All rights reserved.
 * @Description: support java -jar upload github. https://github.com/hhhaiai/uploadGithub
 * @Version: 1.0
 * @Create: 2021/11/2 12:58 下午
 * @author: sanbo
 */
public class GithubCommond {

    public static void main(String[] args) {
        work(args);
//        printHelp();
    }

    private static void work(String[] args) {
        if (args == null || args.length == 0) {
            printHelp();
            return;
        }
        List<String> lists = Arrays.asList(args);
        if (lists == null) {
            return;
        }
        String owner = null, repo = null, dirName = null, pathName = null, fileName = null, token = null, contentNoBase64 = null, contentBase64 = null, commitMsg = null, auther = null, mail = null;
        for (int i = 0; i < lists.size(); i++) {
            try {
                String key = lists.get(i);
                if ("-o".equals(key) || "-u".equals(key)) {
                    owner = lists.get(i + 1);
                } else if ("-r".equals(key)) {
                    repo = lists.get(i + 1);
                } else if ("-s".equals(key)) {
                    dirName = lists.get(i + 1);
                } else if ("-p".equals(key)) {
                    pathName = lists.get(i + 1);
                } else if ("-f".equals(key)) {
                    fileName = lists.get(i + 1);
                } else if ("-t".equals(key)) {
                    token = lists.get(i + 1);
                } else if ("-c".equals(key)) {
                    contentNoBase64 = lists.get(i + 1);
                } else if ("-b".equals(key)) {
                    contentBase64 = lists.get(i + 1);
                } else if ("-m".equals(key)) {
                    commitMsg = lists.get(i + 1);
                } else if ("-a".equals(key)) {
                    auther = lists.get(i + 1);
                } else if ("-l".equals(key)) {
                    mail = lists.get(i + 1);
                }
            } catch (Throwable e) {
                e.printStackTrace();
            }
        }
        String path = "";

        if (isEmpty(fileName)) {
            if (!isEmpty(dirName)) {
                path = dirName + (isEmpty(pathName) ? "" : "/" + pathName);
            } else {
                path = pathName;
            }
        } else {
            path = fileName;
        }
        path = "/" + path;

        if (!isEmpty(contentNoBase64) || !isEmpty(contentBase64)) {
            // 上传字符串,非文件
            if (!isEmpty(contentNoBase64)) {
//                createFile(boolean isNeedBase64, String owner, String repo, String path, String token, String uploadContent, String commitMsg, String username, String email) {
                GithubHelper.createFile(true, owner, repo, path, token, contentNoBase64, commitMsg, auther, mail);

            }
            if (!isEmpty(contentBase64)) {
                GithubHelper.createFile(false, owner, repo, path, token, contentBase64, commitMsg, auther, mail);
            }
        } else {
            //上传文件非字符串
            if (!isEmpty(fileName)) {
                GithubHelper.createFile(false, owner, repo, path, token,
                        FileUtils.getBase64FromFile(new File(fileName))
                        , commitMsg, "", "");
            }
        }
    }

    public static boolean isEmpty(CharSequence str) {
        return str == null || str.length() == 0;
    }

    private static void printHelp() {
        StringBuilder sb = new StringBuilder();
        sb
                .append("github 用法:").append("\r\n")
                .append("\t").append("-o:").append("\t").append("github[用户]名字").append("\r\n")
                .append("\t").append("-u:").append("\t").append("github[用户]名字").append("\r\n")
                .append("\t").append("-r:").append("\t").append("github[项目]名称").append("\r\n")
                .append("\t").append("-s:").append("\t").append("github[上传目录]名称").append("\r\n")
                .append("\t").append("-p:").append("\t").append("github[目标文件]名称").append("\r\n")
                .append("\t").append("-f:").append("\t").append("github即将上传的本地文件名").append("\r\n")
                .append("\t").append("-t:").append("\t").append("github 个人 token").append("\r\n")
                .append("\t").append("-c:").append("\t").append("github上传[未base64]内容").append("\r\n")
                .append("\t").append("-b:").append("\t").append("github上传[已base64]内容").append("\r\n")
                .append("\t").append("-m:").append("\t").append("github上传commit内容").append("\r\n")
                .append("\t").append("-a:").append("\t").append("github上传使用的用户名字(auther)").append("\r\n")
                .append("\t").append("-l:").append("\t").append("github上传使用的邮箱名称").append("\r\n")
        ;
        System.out.println(sb.toString());
    }
}
