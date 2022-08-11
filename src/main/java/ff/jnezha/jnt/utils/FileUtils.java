package ff.jnezha.jnt.utils;

import java.io.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javax.swing.filechooser.FileSystemView;

/**
 * Copyright © 2020 sanbo Inc. All rights reserved.
 * Description: 文件读写类
 * Version: 1.0
 * Create: 2020-12-16 14:05:44
 * Author: sanbo
 */
public class FileUtils {
    public static String getDestopFilePath(String fileName) {
        FileSystemView fsv = FileSystemView.getFileSystemView();
        File home = fsv.getHomeDirectory();
        if (home.getAbsolutePath().endsWith("Desktop")) {
            return new File(home, fileName).getAbsolutePath();
        } else {
            File desktop = new File(home, "Desktop");
            return new File(desktop, fileName).getAbsolutePath();
        }
    }

    /**
     * 读取文件内容,将文件内容读取成直接字节数组
     *
     * @param fileFullPathWithName 文件的全路径名称
     * @return an array of byte objects.
     */
    public static byte[] readForBytes(String fileFullPathWithName) {
        RandomAccessFile raf = null;
        try {
            // 传输文件内容
            byte[] buffer = new byte[1024 * 1002]; // 3的倍数
            raf = new RandomAccessFile(fileFullPathWithName, "r");
            long size = raf.read(buffer);
            while (size > -1) {
                if (size == buffer.length) {
                    return JdkBase64.getEncoder().encode(buffer);
                } else {
                    byte tmp[] = new byte[(int) size];
                    System.arraycopy(buffer, 0, tmp, 0, (int) size);
                    return JdkBase64.getEncoder().encode(tmp);
                }
            }
        } catch (Throwable e) {
            Logger.e(e);
        } finally {
            Closer.close(raf);
        }

        return new byte[]{};
    }

    /**
     * 获得指定文件的byte数组
     *
     * @param file a {@link java.io.File} object.
     * @return a {@link java.lang.String} object.
     */
    public static String getBase64FromFile(File file) {
        byte[] buffer = null;
        FileInputStream fis = null;
        ByteArrayOutputStream bos = null;
        try {
            fis = new FileInputStream(file);
            bos = new ByteArrayOutputStream(1000);
            byte[] b = new byte[1000];
            int n;
            while ((n = fis.read(b)) != -1) {
                bos.write(b, 0, n);
            }
            buffer = bos.toByteArray();
        } catch (Exception e) {
            Logger.e(e);
        } finally {
            Closer.close(fis, bos);
        }
        if (buffer != null) {
            return JdkBase64.getEncoder().encodeToString(buffer);
        }
        return "";
    }

    /**
     * 读取文件内容,将文件内容读取成字符串
     *
     * @param fileFullPathWithName 文件的全路径名称
     * @return 文件内容
     */
    public static String readContent(String fileFullPathWithName) {
        FileInputStream fis = null;
        try {
            fis = new FileInputStream(fileFullPathWithName);
            byte[] buffer = new byte[fis.available()];
            fis.read(buffer);

            return new String(buffer, "UTF-8");
        } catch (Throwable e) {
            Logger.e(e);
        } finally {
            Closer.close(fis);
        }
        return "";
    }

    /**
     * 读取文件内容,将文件内容按行分割以列表形式返回
     *
     * @param fileFullPathWithName 文件的全路径名称
     * @return a {@link java.util.List} object.
     */
    public static List<String> readForArray(String fileFullPathWithName) {
        FileInputStream fis = null;
        try {
            fis = new FileInputStream(fileFullPathWithName);
            byte[] buffer = new byte[fis.available()];
            fis.read(buffer);
            return new ArrayList<String>(Arrays.asList(new String(buffer, "UTF-8").split("\n")));
        } catch (Throwable e) {
            Logger.e(e);
        } finally {
            Closer.close(fis);
        }
        return new ArrayList<String>();
    }

    /**
     * 保存文件到指定文件
     *
     * @param fileFullPathWithName 待保存的文件。如不存在，则会新建
     * @param saveContent          待保存的内容
     */
    public static void saveTextToFile(final String fileFullPathWithName, final String saveContent) {
        saveTextToFile(new File(fileFullPathWithName), saveContent, false);
    }

    /**
     * 保存文件到指定文件
     *
     * @param fileFullPathWithName 待保存的文件。如不存在，则会新建
     * @param saveContent          待保存的内容
     * @param append               是否追加保存
     */
    public static void saveTextToFile(final String fileFullPathWithName, final String saveContent, boolean append) {
        saveTextToFile(new File(fileFullPathWithName), saveContent, append);
    }

    public static void saveTextToFile(final File file, final String saveContent, boolean append) {
        FileWriter fileWriter = null;
        try {
            if (!file.exists()) {
                file.createNewFile();
                file.setExecutable(true);
                file.setReadable(true);
                file.setWritable(true);
            }
            fileWriter = new FileWriter(file, append);
            fileWriter.write(saveContent);
            fileWriter.write("\n");
            fileWriter.flush();
        } catch (Throwable e) {
            Logger.e(e);
        } finally {
            Closer.close(fileWriter);
        }
    }


}
