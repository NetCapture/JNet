package ff.jnezha.jnt.utils;

import ff.jnezha.jnt.org.json.JSONObject;

public class JsonHelper {

    public static boolean has(JSONObject obj, String key) {
        if (obj == null || obj.length() <= 0) {
            return false;
        }
        if (obj.has(key)) {
            return true;
        }
        return false;
    }
}
