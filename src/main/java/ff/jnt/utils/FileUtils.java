package ff.jnt.utils;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.Base64;

public class FileUtils {

    public static byte[] read(String file) {
        try {
            // 传输文件内容
            byte[] buffer = new byte[1024 * 1002]; // 3的倍数
            RandomAccessFile raf = new RandomAccessFile(file, "r");
            long size = raf.read(buffer);
            while (size > -1) {
                if (size == buffer.length) {
                    return Base64.getEncoder().encode(buffer);
                } else {
                    byte tmp[] = new byte[(int) size];
                    System.arraycopy(buffer, 0, tmp, 0, (int) size);
                    return Base64.getEncoder().encode(tmp);
                }
            }
            raf.close();
        } catch (Throwable e) {
            e.printStackTrace();
        }

        return new byte[]{};
    }

    public static String readContent(String fileName) {
        FileInputStream fis = null;
        try {
            fis = new FileInputStream(fileName);
            byte[] buffer = new byte[fis.available()];
            fis.read(buffer);

            return new String(buffer, "UTF-8");
        } catch (Throwable e) {
            e.printStackTrace();
        } finally {
            if (fis != null) {
                try {
                    fis.close();
                } catch (Throwable e) {
                    System.gc();
                }
            }
        }
        return "";
    }
}
