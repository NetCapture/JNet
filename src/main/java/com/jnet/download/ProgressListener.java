package com.jnet.download;

/**
 * 下载进度监听器
 */
public interface ProgressListener {
    /**
     * 进度更新
     * @param bytesRead 已下载字节数
     * @param contentLength 总长度（如果未知则为 -1）
     * @param done 是否完成
     */
    void update(long bytesRead, long contentLength, boolean done);
}
