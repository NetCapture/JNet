package ff.jnezha.jnt.utils;

import java.math.BigDecimal;
import java.util.Base64;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Copyright © 2020 analysys Inc. All rights reserved.
 * Description: 文件处理工具类
 * Version: 1.0
 * Create: 2020-12-16 14:18:35
 * Author: sanbo
 *
 * @author sanbo
 * @version $Id: $Id
 */
public class TextUtils {


    /**
     * Returns true if the string is null or 0-length.
     *
     * @param str the string to be examined
     * @return true if str is null or zero length
     */
    public static boolean isEmpty(CharSequence str) {
        if (str == null || str.length() == 0)
            return true;
        else
            return false;
    }

    /**
     * Returns whether the given CharSequence contains only digits.
     *
     * @param str a {@link java.lang.CharSequence} object.
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
     * Returns whether the given CharSequence contains any printable characters.
     *
     * @param str a {@link java.lang.CharSequence} object.
     * @return a boolean.
     */
    public static boolean isGraphic(CharSequence str) {
        final int len = str.length();
        for (int i = 0; i < len; i++) {
            int gc = Character.getType(str.charAt(i));
            if (gc != Character.CONTROL && gc != Character.FORMAT && gc != Character.SURROGATE
                    && gc != Character.UNASSIGNED && gc != Character.LINE_SEPARATOR
                    && gc != Character.PARAGRAPH_SEPARATOR && gc != Character.SPACE_SEPARATOR) {
                return true;
            }
        }
        return false;
    }

    /**
     * Returns whether this character is a printable character.
     *
     * @param c a char.
     * @return a boolean.
     */
    public static boolean isGraphic(char c) {
        int gc = Character.getType(c);
        return gc != Character.CONTROL && gc != Character.FORMAT && gc != Character.SURROGATE
                && gc != Character.UNASSIGNED && gc != Character.LINE_SEPARATOR && gc != Character.PARAGRAPH_SEPARATOR
                && gc != Character.SPACE_SEPARATOR;
    }

    /**
     * <p>isPrintableAscii.</p>
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
     * <p>isPrintableAsciiOnly.</p>
     *
     * @param str a {@link java.lang.CharSequence} object.
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
     * <p>isNumberByJavaAPI.</p>
     *
     * @param str a {@link java.lang.String} object.
     * @return a boolean.
     */
    public static boolean isNumberByJavaAPI(String str) {
        for (int i = 0; i < str.length(); i++) {
            System.out.println(str.charAt(i));
            if (!Character.isDigit(str.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    /**
     * <p>isNumberByPattern.</p>
     *
     * @param str a {@link java.lang.String} object.
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
     * <p>isNumeric.</p>
     *
     * @param str a {@link java.lang.String} object.
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
     * @param s a {@link java.lang.String} object.
     * @return a {@link java.lang.String} object.
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
     * @param s a {@link java.lang.String} object.
     * @return a {@link java.lang.String} object.
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
        return encodeBase64ToString(source,true);
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
                result = Base64.getEncoder().encodeToString(source.getBytes("UTF-8"));

            }

            // 据RFC 822规定，每76个字符，还需要加上一个回车换行
            // 有时就因为这些换行弄得出了问题，解决办法如下，替换所有换行和回车
            // result = result.replaceAll("[\\s*\t\n\r]", "");
            return result.replaceAll("[\\s*]", "");
        } catch (Throwable e) {
        }
        return source;
    }

    /**
     * 解析base64字符串
     * @param source
     * @return
     */
    public static String tryDecodeBase64ToString(String source) {
        if (isEmpty(source)) {
            return source;
        }
        try {
            byte[] bs = Base64.getDecoder().decode(source.getBytes("UTF-8"));
            return new String(bs);
        } catch (Throwable e) {
        }
        return source;
    }
}
