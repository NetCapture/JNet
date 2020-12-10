package ff.jnezha.jnt.utils;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;

public class FileUtils {

    public static byte[] readForBytes(String file) {
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

    public static List<String> readForArray(String fn) {
        FileInputStream fis = null;
        try {
            fis = new FileInputStream(fn);
            byte[] buffer = new byte[fis.available()];
            fis.read(buffer);
            return new ArrayList<String>(Arrays.asList(new String(buffer, "UTF-8").split("\n")));
        } catch (Throwable e) {
            System.gc();
        } finally {
            Closer.close(fis);
        }
        return new ArrayList<String>();
    }

    public static void saveTextToFile(final String fileName, final String text, boolean append) {
        FileWriter fileWriter = null;
        try {
            File file = new File(fileName);
            if (!file.exists()) {
                file.createNewFile();
                file.setExecutable(true);
                file.setReadable(true);
                file.setWritable(true);
            }
            fileWriter = new FileWriter(file, append);
            fileWriter.write(text);
            fileWriter.write("\n");
            fileWriter.flush();
        } catch (Throwable e) {
            e.printStackTrace();
        } finally {
            Closer.close(fileWriter);
        }
    }
}
