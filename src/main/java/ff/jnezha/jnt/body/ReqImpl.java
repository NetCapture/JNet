package ff.jnezha.jnt.body;

import ff.jnezha.jnt.JntConfig;
import ff.jnezha.jnt.NJnt;
import ff.jnezha.jnt.utils.*;

import javax.net.ssl.HttpsURLConnection;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.net.HttpURLConnection;
import java.net.Proxy;
import java.net.URL;
import java.util.Iterator;
import java.util.Map;

/**
 * @Copyright © 2022 sanbo Inc. All rights reserved.
 * @Description: TODO
 * @Version: 1.0
 * @Create: 2022/1/4 1:03 PM
 * @author: sanbo
 */
public class ReqImpl {
    public static JntResponse request(JntConfig config) {
        try {
            Logger.init(config.debugConfig, config.TAG);
            return requestImpl(config.url, config.method
                    , config.timeout_Config, config.proxy_Config
                    , config.headers_Config, config.data_Config
                    , config.retryTime_Config);
        } catch (Throwable e) {
            Logger.e(e);
        } finally {
            Logger.reset();
        }
        return new JntResponse();

    }

    private static JntResponse requestImpl(String url, String method
            , int timeout, Proxy proxy, Map<String, String> reqHeaderMap
            , String data
            , int tryTime) {
        JntResponse response = new JntResponse();

        if (TextUtils.isEmpty(url) || TextUtils.isEmpty(method)) {
            return response;
        }
        HttpURLConnection conn = null;
        long begin = System.currentTimeMillis();
        if (tryTime > 0) {
            for (int i = 0; i < tryTime; i++) {
                try {
                    response.setRequestUrl(url);
                    response.setRequestMethod(method);
                    // 1. getConnection
                    conn = getConnection(method, timeout, url, proxy, reqHeaderMap, TextUtils.isEmpty(data) ? false : true);

                    if (conn != null) {
                        conn.connect();
                        if (!TextUtils.isEmpty(data)) {
                            // 2. post data
                            postData(conn, data);
                        }
                        listenStatusCodeAndProcess(response, conn, url);
                    }
                } catch (Throwable e) {
                    response.setRunException(e);
                } finally {
                    Closer.close(conn);
                    if (response.getResponseCode() == HttpURLConnection.HTTP_OK) {
                        long end = System.currentTimeMillis();
                        response.setTimingPhases((end - begin) / (i + 1));
                        return response;
                    }
                }
            }
        }

        return response;
    }

    /**
     * 处理网络请求返回值和状态
     *
     * @param response
     * @param conn
     * @param url
     * @return
     */
    private static void listenStatusCodeAndProcess(JntResponse response, HttpURLConnection conn, String url) {
        InputStream is = null, es = null;
        OutputStream os = null;
        try {
            int code = conn.getResponseCode();
            Logger.i("Jnt(" + NJnt.version() + ") send message:" + url + "  status:" + code);

            response.setResponseCode(code);
            response.setResponseMessage(conn.getResponseMessage());
        } catch (Throwable e) {
            response.setRunException(e);
        } finally {
            try {
                response.setResponseHeaders(conn.getHeaderFields());
                is = conn.getInputStream();
                response.setInputStream(DataConver.parserInputStreamToString(is));
                es = conn.getErrorStream();
                response.setErrorStream(DataConver.parserInputStreamToString(es));
                os = conn.getOutputStream();
                response.setOutputStream(DataConver.parserOutputStreamToString(os));
                response.setInstanceFollowRedirects(conn.getInstanceFollowRedirects());
            } catch (Throwable e) {
                response.setRunException(e);
            } finally {
                Closer.close(is, es, os);
            }

        }
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

}
