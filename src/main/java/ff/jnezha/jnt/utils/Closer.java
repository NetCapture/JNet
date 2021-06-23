package ff.jnezha.jnt.utils;

import java.io.Closeable;
import java.net.HttpURLConnection;

/**
 * Copyright © 2020 analysys Inc. All rights reserved.
 * Description: Java关闭器
 * Version: 1.0
 * Create: 2020-12-16 14:01:30
 * Author: sanbo
 *
 * @author sanbo
 * @version $Id: $Id
 */
public class Closer {
    /**
     * java 对象关闭器.
     *
     * @param os 可关闭的对象，如I/O类，HttpURLConnection 等
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
                        e.printStackTrace();
                    }
                }
            }
        }
    }

}
