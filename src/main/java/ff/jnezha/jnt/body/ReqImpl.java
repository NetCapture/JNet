package ff.jnezha.jnt.body;

import ff.jnezha.jnt.JntConfig;
import ff.jnezha.jnt.utils.*;

import java.io.InputStream;
import java.io.PrintWriter;
import java.net.HttpURLConnection;
import java.net.Proxy;
import java.net.URL;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import javax.net.ssl.HttpsURLConnection;

/**
 * @Copyright © 2022 sanbo Inc. All rights reserved.
 * @Description: 网络请求协调类
 * @Version: 1.0
 * @Create: 2022/1/4 1:03 PM
 * @author: sanbo
 */
public class ReqImpl {
    // 默认全部调试状态
    public static boolean globalDebugConfig = false;

    public static JntResponse request(JntConfig config) {
        try {
            Logger.init(getDebug(config), config.user_tag,config.description);
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


    /**
     * 获取debug
     * @param config
     * @return 是否为调试状态。默认为全局标记,默认值为fasle. 优先级: 单次>全局
     */
    private static boolean getDebug(JntConfig config) {
        if (config.debugConfig) {
            return true;
        } else {
            return globalDebugConfig;
        }
    }

    private static JntResponse requestImpl(String url, String method
            , int timeout, Proxy proxy, Map<String, String> reqHeaderMap
            , String data
            , int tryTime) {
        JntResponse response = new JntResponse();

        if (TextUitls.isEmpty(url) || TextUitls.isEmpty(method)) {
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
                    conn = getConnection(method, timeout, url, proxy, reqHeaderMap, TextUitls.isEmpty(data) ? false : true);

                    if (conn != null) {
                        conn.connect();
                        if (!TextUitls.isEmpty(data)) {
                            // 2. post data
                            postData(conn, data);
                        }
                        listenStatusCodeAndProcess(response, conn, url);
                    }
                } catch (Throwable e) {
                    response.setRunException(e);
//                    Logger.e(e);
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
        try {
            int code = conn.getResponseCode();
            Logger.i("send message:" + url + "  status:" + code
//                    +"\r\n调用堆栈:"+Logger.getStackTraceString(new Exception("========call stack======="))
            );
            response.setResponseCode(code);
            response.setResponseMessage(conn.getResponseMessage());
        } catch (Throwable e) {
            response.setRunException(e);
        } finally {
            try {
                setResponseHeaders(response, conn.getHeaderFields());
                setInstanceFollowRedirects(response, conn.getInstanceFollowRedirects());
                setErrorStream(response, conn.getErrorStream());
                setInputStream(response, conn.getInputStream());
            } catch (Throwable ew) {
                response.setRunException(ew);
            }
        }
    }

    private static void setInputStream(JntResponse response, InputStream inputStream) {
        String input = DataConver.parserInputStreamToString(inputStream);
        response.setInputStream(input);
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
            Logger.e(e);
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
            Logger.e(e);
        } finally {
            Closer.close(pw);
        }
    }


    private static void setInstanceFollowRedirects(JntResponse response, boolean instanceFollowRedirects) {
        response.setInstanceFollowRedirects(instanceFollowRedirects);
    }


    private static void setErrorStream(JntResponse response, InputStream errorStream) {
        if (errorStream != null) {
            try {
                String errInfo = DataConver.parserInputStreamToString(errorStream);
                if (!TextUitls.isEmpty(errInfo)) {
                    response.setErrorStream(errInfo);
                }
            } finally {
                Closer.close(errorStream);
            }
        }
    }

    private static void setResponseHeaders(JntResponse response, Map<String, List<String>> headerFields) {
        if (headerFields != null && headerFields.size() > 0) {
            response.setResponseHeaders(headerFields);
        }
    }
//    private static void setOutputStream(JntResponse response, HttpURLConnection conn) throws IOException {
//        if (conn != null) {
//            OutputStream outputStream = null;
//            try {
//                outputStream = conn.getOutputStream();
//                if (outputStream == null) {
//                    return;
//                }
//                String outInfo = DataConver.parserOutputStreamToString(outputStream);
//                if (TextUitls.isEmpty(outInfo)) {
//                    return;
//                }
//                response.setOutputStream(outInfo);
//            } finally {
//                Closer.close(outputStream);
//            }
//        }
//    }
}
