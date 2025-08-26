package indi.yiyi.stockmonitor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import github.nonoas.jfx.flat.ui.control.UIFactory;
import github.nonoas.jfx.flat.ui.stage.AppStage;
import indi.yiyi.stockmonitor.data.StockRow;
import indi.yiyi.stockmonitor.utils.UIUtil;
import indi.yiyi.stockmonitor.view.FXAlert;
import indi.yiyi.stockmonitor.view.PlayfulHelper;
import indi.yiyi.stockmonitor.view.StockSearchDialog;
import indi.yiyi.stockmonitor.view.StockTableView;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Side;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuBar;
import javafx.scene.control.MenuItem;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TableView;
import javafx.scene.control.Tooltip;
import javafx.scene.image.Image;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * @author Nonoas
 * @date 2025/8/20
 * @since
 */
public class MainStage extends AppStage {
    private final StockTableView table = new StockTableView();
    private ScheduledExecutorService scheduler;


    private final Map<String, String> marketDict = Map.of(
            "0", "SZ",
            "1", "SH"
    );

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(8))
            .build();

    private final ObjectMapper mapper = new ObjectMapper();
    private final Stage stage = getStage();

    @SuppressWarnings("unchecked")
    public MainStage() {
        setTitle("盯盘小助手");
        addIcons(Collections.singleton(new Image("image/logo.png")));
        stage.getScene().getStylesheets().add("css/style.css");

        stage.setWidth(500);
        stage.setHeight(420);
        setMinWidth(500);
        setMinHeight(200);

        MenuItem mi = new MenuItem("点我看看");
        mi.setOnAction(e -> PlayfulHelper.start(stage)); // 传你的主 Stage

        MenuItem addItem = new MenuItem("添加股票…");
        addItem.setOnAction(e -> showAddStockDialog(stage));


        Menu menu = new Menu("菜单", null, addItem, mi);

        MenuBar menuBar = new MenuBar(menu);
        menuBar.setPadding(new Insets(5, 10, 5, 10));

        registryDragger(menuBar);

        TabPane tabPane = new TabPane();
        tabPane.setSide(Side.BOTTOM);
        StackPane stackPane = new StackPane(table);
        stackPane.setPadding(new Insets(10));

        Tab tab = new Tab("全部", stackPane);
        tab.setClosable(false);
        tabPane.getTabs().add(tab);

        BorderPane root = new BorderPane(tabPane);
        root.setTop(menuBar);
        setContentView(root);

        // 创建按钮
        Button pinButton = UIFactory.createPinButton(stage);
        // 安装提示
        Tooltip.install(pinButton, new Tooltip("窗口置顶"));

        // 添加到 systemButtons 的开头
        getSystemButtons().addAll(0, List.of(pinButton));


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

                    if (!table.getRowByKey().containsKey(key)) {
                        table.getRowByKey().put(key, row);
                        table.getItems().add(row);
                    } else {
                        StockRow existed = table.getRowByKey().get(key);
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
            if (trends.isMissingNode() || !trends.isArray() || trends.isEmpty()) return Optional.empty();

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


    private void showAddStockDialog(Stage owner) {
        StockSearchDialog dialog = new StockSearchDialog();
        dialog.initOwner(owner);
        dialog.showAndWait().ifPresent(suggestion -> {
            String codeVar = suggestion.getCode();
            String marketVar = "-1";
            if (codeVar.startsWith("SZ")) {
                marketVar = "0";
                codeVar = codeVar.replace("SZ", "");
            } else if (codeVar.startsWith("SH")) {
                marketVar = "1";
                codeVar = codeVar.replace("SH", "");
            } else {
                FXAlert.info(owner, "股票不支持", "暂不支持添加此类型");
                return;
            }
            String market = marketVar;
            String code = codeVar;

            // === 先查重 ===
            if (!CSVConfig.addStock(market, code)) {
                // addStock 内部去重：如果已存在则不写盘并返回 false
                new Alert(Alert.AlertType.INFORMATION, "这只股票已经在列表里啦～").showAndWait();
                return;
            }

            // === 校验存在性 ===
            Alert waiting = new Alert(Alert.AlertType.INFORMATION, "正在校验这只股票是否存在，请稍候…");
            waiting.setHeaderText(null);
            waiting.initOwner(owner);
            UIUtil.setDialogIcon(waiting, owner);
            Node okBtnV = waiting.getDialogPane().lookupButton(ButtonType.OK);
            okBtnV.setDisable(true);
            waiting.show();

            validateStock(market, code).whenComplete((opt, err) -> Platform.runLater(() -> {
                okBtnV.setDisable(false);
                if (err != null || opt.isEmpty() || opt.get().getName() == null || opt.get().getName().isBlank()) {
                    // 回滚 CSV
                    CSVConfig.removeStock(market, code);
                    waiting.setContentText("抱歉，没有找到" + code + "这只股票的有效行情（可能代码错误/无数据/停牌）。");
                    return;
                }

                // 校验通过：更新表格（立即展示这条）
                StockRow row = opt.get();
                String key = row.getMarketCode() + "_" + row.getRawCode();
                StockRow existed = table.getRowByKey().get(key);
                if (existed == null) {
                    row.setIndex(table.getItems().size() + 1);
                    table.getRowByKey().put(key, row);
                    table.getItems().add(row);
                } else {
                    existed.setName(row.getName());
                    existed.setPrice(row.getPrice());
                    existed.setChangeRate(row.getChangeRate());
                    existed.setChangeRateStr(row.getChangeRateStr());
                    existed.setChangeAmt(row.getChangeAmt());
                }
                waiting.setContentText("添加成功："
                        + (market.equals("0") ? "SZ" : "SH") + code + " · " + row.getName());
            }));
        });


    }

    /**
     * 校验股票是否存在：能从接口拿到名称/价格即认为存在，返回最新行数据
     */
    private CompletableFuture<Optional<StockRow>> validateStock(String market, String code) {
        // 后台校验，避免卡 UI
        return CompletableFuture.supplyAsync(() -> getSocketData(market, code));
    }


}
