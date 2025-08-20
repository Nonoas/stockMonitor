package indi.yiyi.stockmonitor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import github.nonoas.jfx.flat.ui.theme.LightTheme;
import github.nonoas.jfx.flat.ui.theme.Styles;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.Scene;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuBar;
import javafx.scene.control.MenuItem;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.layout.BorderPane;
import javafx.stage.Stage;
import javafx.util.Callback;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class StockApp extends Application {

    private final TableView<StockRow> table = new TableView<>();
    private final ObservableList<StockRow> data = FXCollections.observableArrayList();
    private final Map<String, StockRow> rowByKey = new ConcurrentHashMap<>();
    private ScheduledExecutorService scheduler;

    private final Map<String, String> marketDict = Map.of(
            "0", "SZ",
            "1", "SH"
    );

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(8))
            .build();

    private final ObjectMapper mapper = new ObjectMapper();

    @Override
    public void start(Stage stage) {
        setUserAgentStylesheet(new LightTheme().getUserAgentStylesheet());

        stage.setTitle("盯盘小助手");
        stage.setWidth(700);
        stage.setHeight(420);

        // 表格列
        TableColumn<StockRow, Number> colIndex = new TableColumn<>("序号");
        colIndex.setPrefWidth(60);
        colIndex.setCellValueFactory(c -> c.getValue().indexProperty());

        TableColumn<StockRow, String> colCode = new TableColumn<>("股票代码");
        colCode.setPrefWidth(120);
        colCode.setCellValueFactory(c -> c.getValue().codeProperty());

        TableColumn<StockRow, String> colName = new TableColumn<>("股票名称");
        colName.setPrefWidth(140);
        colName.setCellValueFactory(c -> c.getValue().nameProperty());

        TableColumn<StockRow, String> colChangeRate = new TableColumn<>("涨跌幅");
        colChangeRate.setPrefWidth(100);
        colChangeRate.setCellValueFactory(c -> c.getValue().changeRateStrProperty());

        TableColumn<StockRow, Number> colPrice = new TableColumn<>("当前股价");
        colPrice.setPrefWidth(120);
        colPrice.setCellValueFactory(c -> c.getValue().priceProperty());
        colPrice.setCellFactory(formatNumber(3));

        TableColumn<StockRow, Number> colChangeAmt = new TableColumn<>("当日涨跌");
        colChangeAmt.setPrefWidth(120);
        colChangeAmt.setCellValueFactory(c -> c.getValue().changeAmtProperty());
        colChangeAmt.setCellFactory(formatNumber(3));

        table.getColumns().addAll(colIndex, colCode, colName, colChangeRate, colPrice, colChangeAmt);
        table.setItems(data);

        // 行颜色：涨红、跌绿、平默认
        table.setRowFactory(tv -> new TableRow<>() {
            @Override
            protected void updateItem(StockRow item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setStyle("");
                } else {
                    double v = item.getChangeAmt();
                    if (v > 0) getStyleClass().add(Styles.ACCENT);
                    else if (v < 0) getStyleClass().add(Styles.DANGER);
                    else setStyle("");
                }
            }
        });

        MenuItem mi = new MenuItem("点我看看");
        Menu menu = new Menu("菜单", null, mi);

        MenuBar menuBar = new MenuBar(menu);

        BorderPane root = new BorderPane(table);
        root.setTop(menuBar);

        Scene scene = new Scene(root);
        scene.getStylesheets().add("css/style.css");
        stage.setScene(scene);
        stage.show();

        // 启动定时抓取
        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "fetch-thread");
            t.setDaemon(true);
            return t;
        });
        scheduler.scheduleAtFixedRate(this::fetchAndUpdate, 0, 3, TimeUnit.SECONDS);

        // 关闭时停止后台任务
        stage.setOnCloseRequest(ev -> {
            if (scheduler != null) scheduler.shutdownNow();
            Platform.exit();
        });
    }

    private Callback<TableColumn<StockRow, Number>, TableCell<StockRow, Number>> formatNumber(int scale) {
        return col -> new TableCell<>() {
            @Override
            protected void updateItem(Number value, boolean empty) {
                super.updateItem(value, empty);
                if (empty || value == null) {
                    setText(null);
                } else {
                    setText(String.format(Locale.CHINA, "%." + scale + "f", value.doubleValue()));
                }
            }
        };
    }

    private void fetchAndUpdate() {
        try {
            // 读取配置（与 Python 的 cfg.__get_config__() 对应）
            List<CSVConfig.Stock> stocks = CSVConfig.getConfig(); // market_code, stock_code

            // 并发抓取
            List<CompletableFuture<Optional<StockRow>>> futures = stocks.stream()
                    .map(s -> CompletableFuture.supplyAsync(() -> getSocketData(s.marketCode(), s.stockCode())))
                    .toList();

            List<Optional<StockRow>> results = futures.stream()
                    .map(CompletableFuture::join)
                    .toList();

            // 汇总并更新 UI
            Platform.runLater(() -> {
                int idx = 0;
                for (Optional<StockRow> opt : results) {
                    if (opt.isEmpty()) continue;
                    StockRow row = opt.get();
                    idx++;
                    row.setIndex(idx); // 序号
                    String key = row.getMarketCode() + "_" + row.getRawCode(); // 与 Python 的 key 对应

                    if (!rowByKey.containsKey(key)) {
                        rowByKey.put(key, row);
                        data.add(row);
                    } else {
                        StockRow existed = rowByKey.get(key);
                        existed.setIndex(idx);
                        existed.setName(row.getName());
                        existed.setPrice(row.getPrice());
                        existed.setChangeRate(row.getChangeRate());
                        existed.setChangeRateStr(row.getChangeRateStr());
                        existed.setChangeAmt(row.getChangeAmt());
                    }
                }
            });

        } catch (Exception e) {
            // 静默失败或打印到控制台即可
            System.err.println("fetch error: " + e.getMessage());
        }
    }

    /**
     * 拉取单只股票数据并计算涨跌幅/额
     */
    private Optional<StockRow> getSocketData(String marketCode, String stockCode) {
        try {
            String url = "https://push2.eastmoney.com/api/qt/stock/trends2/get?secid="
                    + marketCode + "." + stockCode
                    + "&fields1=f1,f2,f3,f4,f5,f6,f7,f8,f9,f10,f11,f12,f13"
                    + "&fields2=f51,f52,f53,f54,f55,f56,f57,f58";

            HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(8))
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Safari/537.36")
                    .header("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")
                    .GET()
                    .build();

            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() != 200) return Optional.empty();

            JsonNode root = mapper.readTree(resp.body());
            JsonNode dataNode = root.path("data");
            if (dataNode.isMissingNode()) return Optional.empty();

            String name = dataNode.path("name").asText("");
            double preClose = dataNode.path("preClose").asDouble();

            JsonNode trends = dataNode.path("trends");
            if (trends.isMissingNode() || !trends.isArray() || trends.size() == 0) return Optional.empty();

            String last = trends.get(trends.size() - 1).asText(); // "... , price , ..."
            String[] arr = last.split(",");
            if (arr.length < 3) return Optional.empty();

            double currPrice = Double.parseDouble(arr[2]);
            double changeRate = (preClose == 0) ? 0 : (currPrice - preClose) / preClose;
            double changeAmt = currPrice - preClose;

            String codeShown = marketDict.getOrDefault(marketCode, "") + stockCode;
            String changeRateStr = String.format(Locale.CHINA, "%.2f%%", changeRate * 100);

            StockRow row = new StockRow(
                    0,
                    marketCode,
                    stockCode,
                    codeShown,
                    name,
                    currPrice,
                    changeRate,
                    changeRateStr,
                    changeAmt
            );
            return Optional.of(row);
        } catch (IOException | InterruptedException ex) {
            return Optional.empty();
        } catch (Exception ex) {
            System.err.println("parse error: " + ex.getMessage());
            return Optional.empty();
        }
    }

    public static void main(String[] args) {
        launch(args);
    }

    // ========= 数据模型 =========
    public static class StockRow {
        private final String marketCode; // 原始字段，为了 key 组合
        private final String rawCode;    // 原始股票代码（不含市场前缀）
        private final IntegerProperty index = new SimpleIntegerProperty(0);
        private final StringProperty code = new SimpleStringProperty("");
        private final StringProperty name = new SimpleStringProperty("");
        private final DoubleProperty price = new SimpleDoubleProperty(0);
        private final DoubleProperty changeRate = new SimpleDoubleProperty(0);
        private final StringProperty changeRateStr = new SimpleStringProperty("");
        private final DoubleProperty changeAmt = new SimpleDoubleProperty(0);

        public StockRow(int index,
                        String marketCode,
                        String rawCode,
                        String codeShown,
                        String name,
                        double price,
                        double changeRate,
                        String changeRateStr,
                        double changeAmt) {
            this.marketCode = marketCode;
            this.rawCode = rawCode;
            setIndex(index);
            setCode(codeShown);
            setName(name);
            setPrice(price);
            setChangeRate(changeRate);
            setChangeRateStr(changeRateStr);
            setChangeAmt(changeAmt);
        }

        // getters for key
        public String getMarketCode() {
            return marketCode;
        }

        public String getRawCode() {
            return rawCode;
        }

        // properties
        public IntegerProperty indexProperty() {
            return index;
        }

        public StringProperty codeProperty() {
            return code;
        }

        public StringProperty nameProperty() {
            return name;
        }

        public DoubleProperty priceProperty() {
            return price;
        }

        public DoubleProperty changeRateProperty() {
            return changeRate;
        }

        public StringProperty changeRateStrProperty() {
            return changeRateStr;
        }

        public DoubleProperty changeAmtProperty() {
            return changeAmt;
        }

        // getters/setters (for convenience)
        public int getIndex() {
            return index.get();
        }

        public void setIndex(int v) {
            index.set(v);
        }

        public String getCode() {
            return code.get();
        }

        public void setCode(String v) {
            code.set(v);
        }

        public String getName() {
            return name.get();
        }

        public void setName(String v) {
            name.set(v);
        }

        public double getPrice() {
            return price.get();
        }

        public void setPrice(double v) {
            price.set(v);
        }

        public double getChangeRate() {
            return changeRate.get();
        }

        public void setChangeRate(double v) {
            changeRate.set(v);
        }

        public String getChangeRateStr() {
            return changeRateStr.get();
        }

        public void setChangeRateStr(String v) {
            changeRateStr.set(v);
        }

        public double getChangeAmt() {
            return changeAmt.get();
        }

        public void setChangeAmt(double v) {
            changeAmt.set(v);
        }
    }
}
