package ff.jnezha.jnt.utils;

import java.math.BigDecimal;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @Copyright © 2020 sanbo Inc. All rights reserved.
 * @Description: 文件处理工具类
 * @Version: 1.0
 * @Create: 2020-12-16 14:18:35
 * @Author: sanbo
 */
public class TextUitls {


    /**
     * 如果字符串为空或长度为 0，则返回 true。
     *
     * @param str the string to be examined
     * @return true if str is null or zero length
     */
    public static boolean isEmpty(CharSequence str) {
        if (str == null || str.length() == 0 || str.toString() == null || str.toString().trim() == null || str.toString().trim().length() == 0) {
            return true;
        } else {
            return false;
        }
    }


    /**
     * 都是空
     * @param strs
     * @return true: 全部都是空的值、未传入有效值
     *          false: 有非空的选项
     */
    public static boolean isAllEmpty(CharSequence... strs) {
        if (strs == null || strs.length < 1) {
            return true;
        }
        for (CharSequence str : strs) {
            if (!isEmpty(str)) {
                return false;
            }
        }
        return true;
    }

    /**
     * 有为空选项。
     * @param strs
     * @return true: 有为空的值、未传入有效值
     *          false: 没有为空的选项
     */
    public static boolean isHasEmpty(CharSequence... strs) {
        if (strs == null || strs.length < 1) {
            return true;
        }
        for (CharSequence str : strs) {
            if (isEmpty(str)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 返回给定的 CharSequence 是否仅包含数字。
     *
     * @param str a {@link CharSequence} object.
     * @return a boolean.
     */
    public static boolean isDigitsOnly(CharSequence str) {
        final int len = str.length();
        for (int i = 0; i < len; i++) {
            if (!Character.isDigit(str.charAt(i))) {
                return false;
            }
        }
        return true;
    }


    /**
     * 返回给定的 CharSequence 是否包含任何可打印的字符。
     *
     * @param str a {@link CharSequence} object.
     * @return a boolean.
     */
    public static boolean isGraphic(CharSequence str) {
        final int len = str.length();
        for (int i = 0; i < len; i++) {
            int gc = Character.getType(str.charAt(i));
            if (gc != Character.CONTROL && gc != Character.FORMAT && gc != Character.SURROGATE && gc != Character.UNASSIGNED && gc != Character.LINE_SEPARATOR && gc != Character.PARAGRAPH_SEPARATOR && gc != Character.SPACE_SEPARATOR) {
                return true;
            }
        }
        return false;
    }

    /**
     * 返回此字符是否为可打印字符。
     *
     * @param c a char.
     * @return a boolean.
     */
    public static boolean isGraphic(char c) {
        int gc = Character.getType(c);
        return gc != Character.CONTROL && gc != Character.FORMAT && gc != Character.SURROGATE && gc != Character.UNASSIGNED && gc != Character.LINE_SEPARATOR && gc != Character.PARAGRAPH_SEPARATOR && gc != Character.SPACE_SEPARATOR;
    }

    /**
     * 判断给的char是否是可打印的Ascii
     *
     * @param c a char.
     * @return a boolean.
     */
    public static boolean isPrintableAscii(final char c) {
        final int asciiFirst = 0x20;
        final int asciiLast = 0x7E; // included
        return (asciiFirst <= c && c <= asciiLast) || c == '\r' || c == '\n';
    }

    /**
     * 判断CharSequence是否进包含可打印的Ascii
     *
     * @param str a {@link CharSequence} object.
     * @return a boolean.
     */
    public static boolean isPrintableAsciiOnly(final CharSequence str) {
        final int len = str.length();
        for (int i = 0; i < len; i++) {
            if (!isPrintableAscii(str.charAt(i))) {
                return false;
            }
        }
        return true;
    }


    /**
     * Java API判断是否为数字
     *
     * @param str a {@link String} object.
     * @return a boolean.
     */
    public static boolean isNumberByJavaAPI(String str) {
        for (int i = 0; i < str.length(); i++) {
            Logger.i(str.charAt(i));
            if (!Character.isDigit(str.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    /**
     * 通过正则方式判断提供的字符串是否是数字
     *
     * @param str a {@link String} object.
     * @return a boolean.
     */
    public static boolean isNumberByPattern(String str) {
        // 该正则表达式可以匹配所有的数字 包括负数
//        -?[0-9]+\\.?[0-9]*这个表达式在匹配字符串1.、222.时，确实会出现问题。
//        网友的建议改为：-?[0-9]+(\\.[0-9]+)?。
        Pattern pattern = Pattern.compile("-?[0-9]+(\\.[0-9]+)?");
        String bigStr;
        try {
            bigStr = new BigDecimal(str).toString();
        } catch (Exception e) {
            return false;//异常 说明包含非数字。
        }

        Matcher isNum = pattern.matcher(bigStr); // matcher是全匹配
        if (!isNum.matches()) {
            return false;
        }
        return true;
    }

    /**
     * 数字判断
     *
     * @param str a {@link String} object.
     * @return a boolean.
     */
    public static boolean isNumeric(String str) {
        String bigStr;
        try {
            bigStr = new BigDecimal(str).toString();
        } catch (Exception e) {
            return false;//异常 说明包含非数字。
        }
        return true;
    }


    /**
     * 中文转Unicode
     *
     * @param s a {@link String} object.
     * @return a {@link String} object.
     */
    public static String strToUnicode(String s) {
        char[] utfBytes = s.toCharArray();
        String unicodeBytes = "";
        for (int i = 0; i < utfBytes.length; i++) {
            String hexB = Integer.toHexString(utfBytes[i]);
            if (hexB.length() <= 2) {
                hexB = "00" + hexB;
            }
            unicodeBytes = unicodeBytes + "\\u" + hexB;
        }
        return unicodeBytes;
    }

    /**
     * Unicode转中文
     *
     * @param s a {@link String} object.
     * @return a {@link String} object.
     */
    public static String unicodetoString(String s) {
        Pattern pattern = Pattern.compile("(\\\\u(\\p{XDigit}{4}))");
        Matcher matcher = pattern.matcher(s);
        char ch;
        while (matcher.find()) {
            ch = (char) Integer.parseInt(matcher.group(2), 16);
            s = s.replace(matcher.group(1), ch + "");
        }
        return s;
    }

    public static String encodeBase64ToString(String source) {
        return encodeBase64ToString(source, true);
    }

    /**
     * 获取base64字符串
     *
     * @param source          原始字符串
     * @param isBase64Process 是否真正需要base64处理
     * @return
     */
    public static String encodeBase64ToString(String source, boolean isBase64Process) {
        try {
            String result = source;
            if (isBase64Process) {
                result = JdkBase64.getEncoder().encodeToString(source.getBytes("UTF-8"));

            }

            // 据RFC 822规定，每76个字符，还需要加上一个回车换行
            // 有时就因为这些换行弄得出了问题，解决办法如下，替换所有换行和回车
            // result = result.replaceAll("[\\s*\t\n\r]", "");
            return result.replaceAll("[\\s*]", "");
        } catch (Throwable e) {
            Logger.e(e);
        }
        return source;
    }

    /**
     * 解析base64字符串
     *
     * @param source
     * @return
     */
    public static String tryDecodeBase64ToString(String source) {
        if (isEmpty(source)) {
            return source;
        }
        try {
            source = source.replaceAll("[\\s*]", "");
            byte[] bs = JdkBase64.getDecoder().decode(source.getBytes("UTF-8"));
            return new String(bs);
        } catch (Throwable e) {
            Logger.e(e);
        }
        return source;
    }
}
