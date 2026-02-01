package com.jnet.multipart;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * Multipart 部分接口
 */
public interface Part {
    /**
     * 获取部分的内容流
     */
    InputStream getInputStream() throws IOException;

    /**
     * 获取部分的长度
     * @return 长度，如果未知则返回 -1
     */
    long getLength();

    /**
     * 获取部分的头部信息
     */
    String getHeaders();
}
