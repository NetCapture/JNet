package ff.jnezha.jnt;

import ff.jnezha.jnt.utils.Closer;
import ff.jnezha.jnt.utils.SSLConfig;
import ff.jnezha.jnt.utils.TextUtils;

import javax.net.ssl.HttpsURLConnection;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.Proxy;
import java.net.URL;
import java.util.Iterator;
import java.util.Map;

/**
 * Copyright © 2020 analysys Inc. All rights reserved.
 * Description: 网络请求工具类
 * Version: 1.0
 * Create: 2020-12-16 14:19:02
 * Author: sanbo
 *
 * @author sanbo
 * @version $Id: $Id
 */
public class Jnt {

    /** Constant <code>VERSION="v1.0.3"</code> */
    public static final String VERSION = "v1.0.3";
    // debug, control log
    private static volatile boolean bDebug = false;

    /**
     * <p>getVersion.</p>
     *
     * @return a {@link java.lang.String} object.
     */
    public static String getVersion() {
        return VERSION;
    }

    /**
     * <p>setDebug.</p>
     *
     * @param debug a boolean.
     */
    public static void setDebug(boolean debug) {
        bDebug = debug;
    }

    /**
     * <p>isDebug.</p>
     *
     * @return a boolean.
     */
    public static boolean isDebug() {
        return bDebug;
    }


    /**
     * request:
     * * 1. a).getConnection b).parser args and add RequestProperty 3).connect
     * * 2. a).post data b).listen the code,
     * * 3. process failed case or success case(parser the response)
     *
     * @param method       网络请求方式
     * @param timeout      网络请求超时时间
     * @param requestUrl   请求链接
     * @param proxy        代理
     * @param reqHeaderMap HTTP请求头键值对
     * @param data         请求数据
     * @return a {@link java.lang.String} object.
     */
    public static String request(String method, int timeout, String requestUrl, Proxy proxy, Map<String, String> reqHeaderMap, String data) {
        try {
            // 1. getConnection
            HttpURLConnection conn = getConnection(method, timeout, requestUrl, proxy, reqHeaderMap, TextUtils.isEmpty(data) ? false : true);
            conn.connect();
            if (!TextUtils.isEmpty(data)) {
                // 2. post data
                postData(conn, data);
            }
            return listenStatusCodeAndProcess(conn, requestUrl);
        } catch (Throwable e) {
            e.printStackTrace();
        }
        return "";
    }

    /**
     * 获取HttpURLConnection对象
     *
     * @param method
     * @param timeout
     * @param urlString
     * @param proxy
     * @param reqHeaderMap
     * @param isHasData
     * @return
     */
    private static HttpURLConnection getConnection(String method, int timeout, String urlString, Proxy proxy, Map<String, String> reqHeaderMap, boolean isHasData) {
        HttpURLConnection conn = null;
        try {
            URL url = new URL(urlString);

            if (urlString.startsWith("https")) {
                if (proxy != null) {
                    conn = (HttpsURLConnection) url.openConnection(proxy);
                } else {
                    conn = (HttpsURLConnection) url.openConnection();
                }
                ((HttpsURLConnection) conn).setHostnameVerifier(SSLConfig.NOT_VERYFY);
                ((HttpsURLConnection) conn).setSSLSocketFactory(SSLConfig.getSSLFactory());
            } else {
                if (proxy != null) {
                    conn = (HttpURLConnection) url.openConnection(proxy);
                } else {
                    conn = (HttpURLConnection) url.openConnection();
                }
            }

            conn.setRequestMethod(method);
            conn.setConnectTimeout(timeout);
            conn.setReadTimeout(timeout);
            if (isHasData) {
                conn.setDoOutput(true);
                conn.setDoInput(true);
                conn.setUseCaches(false);
            }

            if (reqHeaderMap != null) {
                Iterator<Map.Entry<String, String>> iterator = reqHeaderMap.entrySet().iterator();
                while (iterator.hasNext()) {
                    Map.Entry<String, String> entry = iterator.next();
                    conn.addRequestProperty(entry.getKey(), entry.getValue());
                }

            }

        } catch (Throwable e) {
            e.printStackTrace();
        }
        return conn;
    }

    private static void postData(HttpURLConnection conn, String data) {
        PrintWriter pw = null;
        try {
            pw = new PrintWriter(conn.getOutputStream());
            pw.print(data);
            pw.print("\r\n");
            pw.flush();
            pw.close();
        } catch (Throwable e) {
            e.printStackTrace();
        } finally {
            Closer.close(pw);
        }
    }


//    private static void postDataB(HttpURLConnection conn, String data) {
//        try {
//            DataOutputStream dos = new DataOutputStream(conn.getOutputStream());
//            dos.writeBytes(data);
////        dos.write(data.getBytes(StandardCharsets.UTF_8));
//            dos.flush();
//            dos.close();
//        } catch (Throwable e) {
//            e.printStackTrace();
//        }
//    }


    /**
     * 处理网络请求返回值和状态
     *
     * @param conn
     * @param url
     * @return
     */
    private static String listenStatusCodeAndProcess(HttpURLConnection conn, String url) {
        try {

            int code = conn.getResponseCode();
            System.out.println("Jnt(" + VERSION + ") url:" + url + ",  response code:" + code + ", msg:" + conn.getResponseMessage());
            if (code == 200 || code == 201) {
                String result = parserResponseResult(conn);
                if (isDebug()) {
                    System.out.println("Jnt(" + VERSION + ") request sucess!  response info:" + result);
                }
                return result;
            } else {
                if (isDebug()) {
                    System.err.println("Jnt(" + VERSION + ") request failed! response code: " + conn.getResponseCode() + " ,response msg: " + conn.getResponseMessage());
                }
            }
        } catch (Throwable e) {
            e.printStackTrace();
        }
        return "";
    }

    /**
     * 解析网络请求的返回值
     *
     * @param conn
     * @return
     */
    private static String parserResponseResult(HttpURLConnection conn) {
        InputStream is = null;
        InputStreamReader isr = null;
        BufferedReader reader = null;
        try {
            StringBuilder sbf = new StringBuilder();
            is = conn.getInputStream();
            isr = new InputStreamReader(is, "UTF-8");
            reader = new BufferedReader(isr);
            String strRead = null;
            while ((strRead = reader.readLine()) != null) {
                sbf.append(strRead);
                sbf.append("\n");
            }

            return sbf.toString();
        } catch (Throwable e) {
            e.printStackTrace();
        } finally {
            Closer.close(is, isr, reader);
        }
        return "";
    }
}
