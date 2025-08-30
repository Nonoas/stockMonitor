package indi.yiyi.stockmonitor.data;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.Charset;

public class NetEaseHistoryFetcher {
    public static void main(String[] args) throws Exception {
        // 网易财经历史行情API
        String urlStr = "http://quotes.money.163.com/service/chddata.html?code=1000063&start=20240101&end=20250830&fields=TCLOSE";
        URL url = new URL(urlStr);

        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");

        // 网易财经返回是 GB2312 编码
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(conn.getInputStream(), Charset.forName("GB2312")))) {
            String line;
            while ((line = reader.readLine()) != null) {
                System.out.println(line); // CSV 格式，可以自己再解析
            }
        }
    }
}
