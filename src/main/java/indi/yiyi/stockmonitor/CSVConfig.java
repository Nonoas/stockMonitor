package indi.yiyi.stockmonitor;



import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Nonoas
 * @date 2025/8/20
 * @since
 */
public class CSVConfig {

    public record Stock(String marketCode, String stockCode) {}

    public static List<Stock> getConfig() {
        List<Stock> list = new ArrayList<>();
        try (InputStream is = CSVConfig.class.getResourceAsStream("/stocks.csv")) {
            if (is == null) return list;
            try (BufferedReader br = new BufferedReader(new InputStreamReader(is))) {
                String line;
                while ((line = br.readLine()) != null) {
                    line = line.trim();
                    if (line.isEmpty() || line.startsWith("#")) continue;
                    String[] arr = line.split(",");
                    if (arr.length >= 2) {
                        list.add(new Stock(arr[0].trim(), arr[1].trim()));
                    }
                }
            }
        } catch (IOException ignored) {}
        return list;
    }
}
