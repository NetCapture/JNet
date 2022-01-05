package ff.jnezha.jnt.utils;

import java.io.*;

/**
 * @Copyright © 2022 sanbo Inc. All rights reserved.
 * @Description: TODO
 * @Version: 1.0
 * @Create: 2022/1/4 1:43 PM
 * @author: sanbo
 */
public class DataConver {

    /**
     * 解析网络请求的返回值
     *
     * @param is
     * @return
     */
    public static String parserInputStreamToString(InputStream is) {

//        方法一:
//        try {
//            byte[] bytes = new byte[0];
//            bytes = new byte[is.available()];
//            is.read(bytes);
//            return new String(bytes);
//        } catch (Throwable e) {
//            e.printStackTrace();
//        }
        // 方法二：
        //String result = new BufferedReader(new InputStreamReader(inputStream))
        //        .lines().collect(Collectors.joining(System.lineSeparator()));

        // 方法三：
        //String result = new BufferedReader(new InputStreamReader(inputStream))
        //       .lines().parallel().collect(Collectors.joining(System.lineSeparator()));

        //方法四：
        //Scanner s = new Scanner(inputStream).useDelimiter("\\A");
        //String str = s.hasNext() ? s.next() : "";

        //方法五：
        //String resource = new Scanner(inputStream).useDelimiter("\\Z").next();
        //return resource;

        // 方法六：
        //StringBuilder sb = new StringBuilder();
        //String line;
        //
        //BufferedReader br = new BufferedReader(new InputStreamReader(inputStream));
        //while ((line = br.readLine()) != null) {
        //    sb.append(line);
        //}
        //String str = sb.toString();
        //return str;

        // 方法七：
//        ByteArrayOutputStream result = new ByteArrayOutputStream();
//        try {
//            byte[] buffer = new byte[1024];
//            int length;
//            while ((length = is.read(buffer)) != -1) {
//                result.write(buffer, 0, length);
//            }
//            return result.toString("UTF-8");
//        } catch (Throwable e) {
//           e.printStackTrace();
//        }finally {
//            Closer.close(result);
//        }


        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try {
            byte[] buffer = new byte[1024];
            int n = 0;
            while (-1 != (n = is.read(buffer))) {
                bos.write(buffer, 0, n);
            }

            return bos.toString("UTF-8");
        } catch (IOException e) {
            e.printStackTrace();
            }finally {
            Closer.close(bos);
        }
        return "";
    }

    /**
     * Convert inputStream to byte array,stream will not be closed
     *
     * @param input
     * @return byte[]
     * @throws IOException
     */
    public static byte[] readStreamToByteArray(InputStream input) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();

        byte[] buffer = new byte[1024];
        int n = 0;

        while (-1 != (n = input.read(buffer))) {
            bos.write(buffer, 0, n);
        }

        return bos.toByteArray();
    }

    /**
     * 输出流转化为字符串
     *
     * @param output
     * @return
     */
    public static String parserOutputStreamToString(OutputStream output) {
        ByteArrayOutputStream baos = null;
        ByteArrayInputStream swapStream = null;
        String result = null;
        try {
            baos = new ByteArrayOutputStream();
            baos = (ByteArrayOutputStream) output;
            swapStream = new ByteArrayInputStream(baos.toByteArray());
            result = swapStream.toString();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return result;
    }

    /**
     * 字符串转化为输入流
     *
     * @param str
     * @return
     */
    public static InputStream readStringToInputStream(String str) {
        try {
            if (str == null) {
                return null;
            }
            return new ByteArrayInputStream(str.getBytes());
        } catch (Throwable e) {
            e.printStackTrace();
        }
        return null;
    }

    /**
     * 字符串转化成输出流
     *
     * @param str
     * @return
     */
    public static OutputStream readStringToOutputStream(String str) {
        try {
            if (str == null) {
                return null;
            }
            OutputStream os = System.out;
            os.write(str.getBytes());
            return os;
        } catch (Throwable e) {
            e.printStackTrace();
        }
        return null;
    }

    /**
     * 输入流转化为字符串
     *
     * @param input
     * @return
     */
    public static String readInputStreamToString(InputStream input) {
        StringWriter writer = null;
        InputStreamReader reader = null;
        String result = null;
        try {
            reader = new InputStreamReader(input, "UTF-8");
            char[] buffer = new char[1024];
            int n = 0;
            writer = new StringWriter();
            while (-1 != (n = reader.read(buffer))) {
                writer.write(buffer, 0, n);
            }
            result = writer.toString();
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            Closer.close(reader, writer);
        }
        return result;
    }
}
