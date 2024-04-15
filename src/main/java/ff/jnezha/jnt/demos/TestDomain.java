package ff.jnezha.jnt.demos;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import ff.jnezha.jnt.NJnt;
import ff.jnezha.jnt.body.JntResponse;
import ff.jnezha.jnt.utils.DataConver;

public class TestDomain {
    public static void main(String[] args) throws Throwable {
        req("taobao.com");
    }

    private static void req(String domain) throws IOException {
        String baseDomain = "micp.chinaz.com";
        String baseUrl = "https://" + baseDomain;
        String url = baseUrl + "/" + domain;
        String body = "keyword=" + domain + "&accessmode=2&isupdate=0";
        Map<String, String> header = new HashMap<>();
        header.put("Host", baseDomain);
        header.put("cache-control", "max-age=0");
        header.put("origin", baseUrl);
        header.put("upgrade-insecure-requests", "1");
        header.put("content-type", "application/x-www-form-urlencoded");
        header.put("accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,image/apng,*/*;q=0.8");
        header.put("referer", url);
        header.put("accept-encoding", "gzip, deflate, br");
        header.put("accept-language", "zh-CN,zh;q=0.9");
        header.put("User-Agent",
                "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/116.0.0.0 Safari/537.36");
        JntResponse resp = NJnt.url(url)
                .body(body)
                .header(header)
                .post();
        System.out.println(resp.getInputStream());

        // Document doc = Jsoup.connect(url).headers(header).requestBody(body)
        // .ignoreContentType(true).ignoreHttpErrors(true)
        // .post();

        // System.out.println(doc.toString());
    }
}
