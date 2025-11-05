package com.jnet.core;

import java.io.Closeable;
import java.net.HttpURLConnection;

/**
 * 资源关闭工具类
 * 替代 ff.jnezha.jnt.utils.Closer
 *
 * @author sanbo
 * @version 3.0.0
 */
public final class Closer {

    private Closer() {}

    /**
     * 关闭资源
     */
    public static void close(Object... os) {
        if (os != null && os.length > 0) {
            for (Object o : os) {
                if (o != null) {
                    try {
                        if (o instanceof HttpURLConnection) {
                            ((HttpURLConnection) o).disconnect();
                        } else if (o instanceof Closeable) {
                            ((Closeable) o).close();
                        }
                    } catch (Throwable e) {
                        // 静默关闭
                    }
                }
            }
        }
    }
}
