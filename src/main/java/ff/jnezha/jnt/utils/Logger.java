package ff.jnezha.jnt.utils;

import java.io.File;
import java.lang.reflect.Method;

/**
 * @Copyright © 2022 sanbo Inc. All rights reserved.
 * @Description: TODO
 * @Version: 1.0
 * @Create: 2022/3/9 5:19 PM
 * @author: sanbo
 */
public class Logger {

    private static String TAG = "Jnt";
    private static String TMP_TAG = null;
    private static boolean isDebug = false;

    /**
     * init method
     *
     * @param iDebug
     * @param tag
     */
    public static void init(boolean iDebug, String tag) {
        isDebug = iDebug;
        if (!isEmpty(tag)) {
            TMP_TAG = tag;
        }
    }

    public static void reset() {
        TMP_TAG = null;
    }

    public static void v(Object... objs) {
        print(ELevel.VERBOSE.value, objs);
    }

    public static void d(Object... objs) {
        print(ELevel.DEBUG.value, objs);
    }

    public static void i(Object... objs) {
        print(ELevel.INFO.value, objs);
    }

    public static void w(Object... objs) {
        print(ELevel.WARN.value, objs);
    }

    public static void e(Object... objs) {
        print(ELevel.ERROR.value, objs);
    }


    public static <T> boolean isStartupFromJar(Class<T> clazz) {
        File file = new File(clazz.getProtectionDomain().getCodeSource().getLocation().getPath());
        return file.isFile();
    }

    private static void print(int priority, Object... objs) {
        // 1. check log status
        if (!isDebug) {
            return;
        }
        // 2. get tag
        String tag = getTag();
        //3. get log msg info
        String msg = getPrintMsg(objs);
        if (isEmpty(msg)) {
            return;
        }
        // 2. check platform
        if (isAndroidPlatform()) {
            printFormAndroidPlatform(priority, tag, msg);
        } else {
            printForJava(priority, tag, msg);
        }
    }

    /**
     * java print
     *
     * @param priority
     * @param tag
     * @param msg
     */
    private static void printForJava(int priority, String tag, String msg) {
        // @TODO can update other color
        String header = "", end = "";
        String strPriority = getHeader(priority);
        ppp(String.format("%s%s[%s] %s%s", header, strPriority, tag, msg, end));
    }


    private static void ppp(String formatString) {
        System.out.println(formatString);
    }


    /**
     * android print
     * android.util.Log.println(int priority, String tag, String msg)
     *
     * @param priority
     * @param tag
     * @param msg
     */
    private static void printFormAndroidPlatform(int priority, String tag, String msg) {
        try {
            Class<?> logClass = getClass("android.util.Log");
            if (logClass != null) {
                Method println = getMethod(logClass, "println"
                        , int.class, String.class, String.class);
                if (println != null) {
                    println.invoke(null, priority, tag, msg);
                }
            }
        } catch (Throwable e) {
        }
    }

    private static boolean isAndroidPlatform() {
        try {
            Class<?> log = getClass("android.util.Log");
            if (log != null) {
                return true;
            }
        } catch (ClassNotFoundException e) {
        }
        return false;
    }

    /**
     * get default tag
     *
     * @return
     */
    private static String getTag() {
        if (isEmpty(TMP_TAG)) {
            return TAG;
        } else {
            return TMP_TAG;
        }
    }

    private static String getHeader(int priority) {
        switch (priority) {
            case 2:
                return "V";
            case 3:
                return "D";
            case 4:
                return "I";
            case 5:
                return "W";
            case 6:
                return "E";
            case 7:
                return "A";
            default:
                return "";
        }
    }

    /**
     * get print msg
     *
     * @param objs
     * @return
     */
    private static String getPrintMsg(Object[] objs) {
        if (objs == null || objs.length < 1) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < objs.length; i++) {
            Object obj = objs[i];
            if (obj == null) {
                continue;
            }
            if (obj instanceof String) {
                sb.append((String) obj).append("\r\n");
            } else {
                sb.append(obj.toString()).append("\r\n");
            }
        }
        String result = sb.toString();
        if (isEmpty(result)) {
            return null;
        } else {
            return result.trim();
        }
    }


    private static Class<?> getClass(String className) throws ClassNotFoundException {
        return Class.forName(className);
    }

    private static Method getMethod(Class<?> cls, String methodName, Class<?>... parameterTypes) {
        Method m = null;
        try {
            m = cls.getDeclaredMethod(methodName, parameterTypes);
        } catch (NoSuchMethodException e) {
        }
        if (m != null) {
            return m;
        }
        try {
            m = cls.getMethod(methodName, parameterTypes);
        } catch (NoSuchMethodException e) {
        }
        return m;
    }

    public static boolean isEmpty(CharSequence str) {
        if (str == null || str.length() == 0)
            return true;
        else
            return false;
    }

    public enum ELevel {
        NONE(1),
        VERBOSE(2),
        DEBUG(3),
        INFO(4),
        WARN(5),
        ERROR(6),
        ASSERT(7);
        private final int value;

        private ELevel(int value) {
            this.value = value;
        }

        public String toString() {
            return Integer.toString(value);
        }

        public static void main(String[] args) {

        }
    }

}
