package indi.yiyi.stockmonitor;


import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class CSVConfig {

    public record Stock(String marketCode, String stockCode) {
        public String key() {
            return marketCode + "_" + stockCode;
        }
    }

    private static final Path CSV_PATH = Path.of("stocks.csv"); // 与软件同级目录
    private static final ReentrantReadWriteLock LOCK = new ReentrantReadWriteLock();
    private static final LinkedHashMap<String, Stock> CACHE = new LinkedHashMap<>();

    static {
        ensureLoaded();
    }

    /**
     * 读取或创建文件，并加载到内存缓存
     */
    private static void ensureLoaded() {
        LOCK.writeLock().lock();
        try {
            if (Files.notExists(CSV_PATH)) {
                // 首次创建一个空文件（可写入示例行）
                Files.writeString(CSV_PATH, "# market_code,stock_code\n", StandardCharsets.UTF_8,
                        StandardOpenOption.CREATE, StandardOpenOption.WRITE);
            }
            CACHE.clear();
            try (BufferedReader br = Files.newBufferedReader(CSV_PATH, StandardCharsets.UTF_8)) {
                String line;
                while ((line = br.readLine()) != null) {
                    line = line.trim();
                    if (line.isEmpty() || line.startsWith("#")) continue;
                    String[] arr = line.split(",");
                    if (arr.length >= 2) {
                        String m = stripQuotes(arr[0]);
                        String c = stripQuotes(arr[1]);
                        if (isValidMarket(m) && isValidCode(c)) {
                            Stock s = new Stock(m, c);
                            CACHE.put(s.key(), s);
                        }
                    }
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } finally {
            LOCK.writeLock().unlock();
        }
    }

    /**
     * 供外部读取的快照（避免外部修改内部缓存）
     */
    public static List<Stock> getConfig() {
        LOCK.readLock().lock();
        try {
            return new ArrayList<>(CACHE.values());
        } finally {
            LOCK.readLock().unlock();
        }
    }

    /**
     * 添加一条（内存 + 写盘），返回是否新增成功（已存在则 false）
     */
    public static boolean addStock(String marketCode, String stockCode) {
        marketCode = stripQuotes(marketCode);
        stockCode = stripQuotes(stockCode);
        if (!isValidMarket(marketCode) || !isValidCode(stockCode)) return false;

        LOCK.writeLock().lock();
        try {
            Stock s = new Stock(marketCode, stockCode);
            if (CACHE.containsKey(s.key())) return false;
            CACHE.put(s.key(), s);
            saveAllUnlocked();
            return true;
        } finally {
            LOCK.writeLock().unlock();
        }
    }

    /**
     * 删除一条（内存 + 写盘），返回是否删除成功
     */
    public static boolean removeStock(String marketCode, String stockCode) {
        LOCK.writeLock().lock();
        try {
            String key = marketCode + "_" + stockCode;
            if (CACHE.remove(key) != null) {
                saveAllUnlocked();
                return true;
            }
            return false;
        } finally {
            LOCK.writeLock().unlock();
        }
    }

    /**
     * 持久化内存缓存到 CSV（需在已拿写锁时调用）
     */
    private static void saveAllUnlocked() {
        try (BufferedWriter bw = Files.newBufferedWriter(CSV_PATH, StandardCharsets.UTF_8,
                StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.CREATE, StandardOpenOption.WRITE)) {
            bw.write("# market_code,stock_code\n");
            for (Stock s : CACHE.values()) {
                bw.write(s.marketCode + "," + s.stockCode);
                bw.write("\n");
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static String stripQuotes(String s) {
        if (s == null) return "";
        return s.trim().replace("\"", "");
    }

    private static boolean isValidMarket(String m) {
        return "0".equals(m) || "1".equals(m); // 0=SZ, 1=SH
    }

    private static boolean isValidCode(String c) {
        // A股/ETF 常见 6 位数字，这里放宽成 1-8 位数字，按需调整
        return c != null && c.matches("\\d{1,8}");
    }
}

