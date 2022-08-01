package ff.jnezha.jnt;

import java.net.Proxy;
import java.util.Map;

/**
 * @Copyright © 2022 sanbo Inc. All rights reserved.
 * @Description: one request , one config
 * @Version: 1.0
 * @Create: 2022/2/25 2:53 PM
 * @author: sanbo
 */
public class JntConfig {


    public JntConfig() {
    }
    public String TAG = "Jnt";
    public String url = null;
    public String method = null;
    public int timeout_Config = 10 * 1000;
    public Proxy proxy_Config = null;
    public Map<String, String> headers_Config = null;
    public String data_Config = null;
    public int retryTime_Config = 3;
    // debug, control log
    public boolean debugConfig = true;
}
