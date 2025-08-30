package indi.yiyi.stockmonitor.utils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.http.HttpEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class EastMoneyKlineUtil {

    private static final String BASE_URL =
            "https://push2his.eastmoney.com/api/qt/stock/kline/get";

    /**
     * 获取历史 K 线数据（开盘、收盘、最高、最低、成交量等）
     *
     * @param secid 市场代码+股票代码，例如 "0.000568" (沪市000568)，"1.000063" (深市000063)
     * @param beg   开始日期 (yyyyMMdd)，0 表示最早
     * @param end   结束日期 (yyyyMMdd)，如 20250830
     * @param klt   K线类型: 101=日K, 102=周K, 103=月K
     * @param fqt   复权方式: 0=不复权, 1=前复权, 2=后复权
     * @return 历史 K 线数据列表
     */
    public static List<StockKline> getHistoryKlines(
            String secid, String beg, String end, int klt, int fqt) throws Exception {

        String url = BASE_URL +
                "?fields1=f1,f2,f3,f4,f5,f6,f7,f8,f9,f10,f11,f12,f13" +
                "&fields2=f51,f52,f53,f54,f55,f56,f57,f58,f59,f60,f61" +
                "&beg=" + beg +
                "&end=" + end +
                "&ut=fa5fd1943c7b386f172d6893dbfba10b" +
                "&rtntype=6" +
                "&secid=" + secid +
                "&klt=" + klt +
                "&fqt=" + fqt;

        List<StockKline> result = new ArrayList<>();

        try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
            HttpGet httpGet = new HttpGet(url);
            httpGet.addHeader("User-Agent", "Mozilla/5.0");

            try (CloseableHttpResponse response = httpClient.execute(httpGet)) {
                HttpEntity entity = response.getEntity();
                if (entity != null) {
                    String json = EntityUtils.toString(entity, StandardCharsets.UTF_8);

                    // 处理 JSONP 包裹
                    if (json.startsWith("jsonp")) {
                        int start = json.indexOf("(") + 1;
                        int endIdx = json.lastIndexOf(")");
                        json = json.substring(start, endIdx);
                    }

                    ObjectMapper mapper = new ObjectMapper();
                    JsonNode root = mapper.readTree(json);
                    JsonNode klines = root.path("data").path("klines");

                    for (JsonNode kline : klines) {
                        String[] parts = kline.asText().split(",");
                        StockKline k = new StockKline();
                        k.date = parts[0];                    // f51 日期
                        k.open = Double.parseDouble(parts[1]); // f52 开盘
                        k.close = Double.parseDouble(parts[2]); // f53 收盘
                        k.high = Double.parseDouble(parts[3]);  // f54 最高
                        k.low = Double.parseDouble(parts[4]);   // f55 最低
                        k.volume = Long.parseLong(parts[5]);    // f56 成交量
                        k.turnover = Double.parseDouble(parts[6]); // f57 成交额
                        result.add(k);
                    }
                }
            }
        }

        return result;
    }

    /**
     * 数据结构：保存每根 K 线的完整信息
     */
    public static class StockKline {
        public String date;
        public double open;
        public double close;
        public double high;
        public double low;
        public long volume;
        public double turnover;

        @Override
        public String toString() {
            return "StockKline{" +
                    "date='" + date + '\'' +
                    ", open=" + open +
                    ", close=" + close +
                    ", high=" + high +
                    ", low=" + low +
                    ", volume=" + volume +
                    ", turnover=" + turnover +
                    '}';
        }
    }

    // 简单测试
    public static void main(String[] args) throws Exception {
        List<StockKline> list = getHistoryKlines("0.000568", "20240101", "20250830", 101, 1);
        list.forEach(System.out::println);
    }
}
