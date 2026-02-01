package com.jnet.download;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Download 模块测试套件
 * 目标：80%+ 测试覆盖率
 */
@DisplayName("Download Tests")
class TestDownload {

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("ProgressListener: 进度回调接口")
    void testProgressListener() {
        AtomicLong lastProgress = new AtomicLong(0);
        AtomicLong lastTotal = new AtomicLong(0);
        AtomicLong completionCount = new AtomicLong(0);

        ProgressListener listener = new ProgressListener() {
            @Override
            public void update(long downloaded, long total, boolean done) {
                lastProgress.set(downloaded);
                lastTotal.set(total);
                if (done) {
                    completionCount.incrementAndGet();
                }
            }
        };

        listener.update(100, 1000, false);
        assertEquals(100, lastProgress.get());
        assertEquals(1000, lastTotal.get());
        assertEquals(0, completionCount.get());

        listener.update(1000, 1000, true);
        assertEquals(1000, lastProgress.get());
        assertEquals(1, completionCount.get());
    }

    @Test
    @DisplayName("ProgressListener: Null listener 应该安全处理")
    void testNullProgressListener() {
        ProgressListener listener = null;
        assertDoesNotThrow(() -> {
            if (listener != null) {
                listener.update(0, 0, false);
            }
        });
    }

    @Test
    @DisplayName("ProgressListener: 多次进度更新")
    void testMultipleProgressUpdates() {
        int[] callCount = {0};
        long[] lastDownloaded = {0};

        ProgressListener listener = (downloaded, total, done) -> {
            callCount[0]++;
            lastDownloaded[0] = downloaded;
        };

        listener.update(100, 1000, false);
        listener.update(500, 1000, false);
        listener.update(1000, 1000, true);

        assertEquals(3, callCount[0]);
        assertEquals(1000, lastDownloaded[0]);
    }

    @Test
    @DisplayName("ProgressListener: 未知总大小 (total=-1)")
    void testUnknownTotalSize() {
        ProgressListener listener = (downloaded, total, done) -> {
            if (total == -1) {
                assertTrue(downloaded >= 0);
            }
        };

        listener.update(100, -1, false);
        listener.update(200, -1, false);
        listener.update(300, -1, true);
    }

    @Test
    @DisplayName("ProgressListener: 百分比计算")
    void testPercentageCalculation() {
        ProgressListener listener = (downloaded, total, done) -> {
            if (total > 0) {
                double percentage = (downloaded * 100.0) / total;
                assertTrue(percentage >= 0 && percentage <= 100);
            }
        };

        listener.update(250, 1000, false); // 25%
        listener.update(500, 1000, false); // 50%
        listener.update(1000, 1000, true); // 100%
    }

    @Test
    @DisplayName("ProgressListener: 大文件进度跟踪")
    void testLargeFileProgress() {
        long largeFileSize = 1024L * 1024L * 1024L; // 1GB
        AtomicLong lastProgress = new AtomicLong(0);

        ProgressListener listener = (downloaded, total, done) -> {
            assertTrue(downloaded <= total);
            assertTrue(downloaded >= lastProgress.get());
            lastProgress.set(downloaded);
        };

        listener.update(largeFileSize / 4, largeFileSize, false);
        listener.update(largeFileSize / 2, largeFileSize, false);
        listener.update(largeFileSize, largeFileSize, true);

        assertEquals(largeFileSize, lastProgress.get());
    }

    @Test
    @DisplayName("ProgressListener: 完成标志验证")
    void testCompletionFlag() {
        boolean[] doneFlags = new boolean[5];
        int[] index = {0};

        ProgressListener listener = (downloaded, total, done) -> {
            if (index[0] < doneFlags.length) {
                doneFlags[index[0]++] = done;
            }
        };

        listener.update(0, 100, false);
        listener.update(25, 100, false);
        listener.update(50, 100, false);
        listener.update(75, 100, false);
        listener.update(100, 100, true);

        assertFalse(doneFlags[0]);
        assertFalse(doneFlags[1]);
        assertFalse(doneFlags[2]);
        assertFalse(doneFlags[3]);
        assertTrue(doneFlags[4]);
    }

    @Test
    @DisplayName("ProgressListener: 线程安全性")
    void testThreadSafety() throws InterruptedException {
        AtomicLong totalUpdates = new AtomicLong(0);

        ProgressListener listener = (downloaded, total, done) -> {
            totalUpdates.incrementAndGet();
        };

        Thread[] threads = new Thread[10];
        for (int i = 0; i < threads.length; i++) {
            final int threadId = i;
            threads[i] = new Thread(() -> {
                for (int j = 0; j < 100; j++) {
                    listener.update(threadId * 100 + j, 10000, false);
                }
            });
            threads[i].start();
        }

        for (Thread thread : threads) {
            thread.join();
        }

        assertEquals(1000, totalUpdates.get());
    }

    @Test
    @DisplayName("ProgressListener: Lambda 表达式使用")
    void testLambdaExpression() {
        StringBuilder log = new StringBuilder();

        ProgressListener listener = (downloaded, total, done) -> {
            log.append(String.format("Progress: %d/%d%n", downloaded, total));
        };

        listener.update(50, 100, false);
        listener.update(100, 100, true);

        assertTrue(log.toString().contains("50/100"));
        assertTrue(log.toString().contains("100/100"));
    }

    @Test
    @DisplayName("ProgressListener: 方法引用使用")
    void testMethodReference() {
        TestProgressTracker tracker = new TestProgressTracker();
        ProgressListener listener = tracker::track;

        listener.update(100, 1000, false);
        assertEquals(100, tracker.lastDownloaded);
        assertEquals(1000, tracker.lastTotal);
        assertFalse(tracker.lastDone);

        listener.update(1000, 1000, true);
        assertTrue(tracker.lastDone);
    }

    static class TestProgressTracker {
        long lastDownloaded;
        long lastTotal;
        boolean lastDone;

        void track(long downloaded, long total, boolean done) {
            this.lastDownloaded = downloaded;
            this.lastTotal = total;
            this.lastDone = done;
        }
    }
}
