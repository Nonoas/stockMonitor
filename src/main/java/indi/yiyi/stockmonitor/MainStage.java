package indi.yiyi.stockmonitor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import github.nonoas.jfx.flat.ui.concurrent.TaskHandler;
import github.nonoas.jfx.flat.ui.control.UIFactory;
import github.nonoas.jfx.flat.ui.stage.AppStage;
import github.nonoas.jfx.flat.ui.stage.ToastQueue;
import indi.yiyi.stockmonitor.data.Stock;
import indi.yiyi.stockmonitor.data.StockGroup;
import indi.yiyi.stockmonitor.data.StockRow;
import indi.yiyi.stockmonitor.utils.DateUtils;
import indi.yiyi.stockmonitor.utils.EastMoneyKlineUtil;
import indi.yiyi.stockmonitor.utils.GroupConfig;
import indi.yiyi.stockmonitor.utils.UIUtil;
import indi.yiyi.stockmonitor.view.FXAlert;
import indi.yiyi.stockmonitor.view.PlayfulHelper;
import indi.yiyi.stockmonitor.view.StockSearchDialog;
import indi.yiyi.stockmonitor.view.StockTab;
import indi.yiyi.stockmonitor.view.StockTableView;
import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.geometry.Insets;
import javafx.geometry.Side;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuBar;
import javafx.scene.control.MenuItem;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TextInputDialog;
import javafx.scene.control.Tooltip;
import javafx.scene.image.Image;
import javafx.scene.layout.BorderPane;
import javafx.stage.Stage;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
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
    private static final Logger LOG = LogManager.getLogger(MainStage.class);
    private final TabPane tabPane = new TabPane();
    private final ScheduledExecutorService scheduler;
    private final Map<String, StockGroup> groups = new ConcurrentHashMap<>();

    private final Map<String, String> marketDict = Map.of(
            "0", "SZ",
            "1", "SH"
    );

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(java.time.Duration.ofSeconds(8))
            .build();

    private final ObjectMapper mapper = new ObjectMapper();
    private final Stage stage = getStage();

    public MainStage() {
        setTitle("盯盘小助手");
        addIcons(Collections.singleton(new Image("image/logo.png")));
        stage.getScene().getStylesheets().add("css/style.css");

        stage.setWidth(500);
        stage.setHeight(420);
        setMinWidth(420);
        setMinHeight(200);

        MenuBar menuBar = getMenuBar();

        registryDragger(menuBar);

        tabPane.setSide(Side.BOTTOM);
        tabPane.getSelectionModel().selectedItemProperty().addListener(new ChangeListener<>() {
            @Override
            public void changed(ObservableValue<? extends Tab> observable, Tab oldValue, Tab newValue) {
                if (newValue != null && scheduler != null) {
                    TaskHandler.backRun(() -> fetchAndUpdate());
                }
            }
        });

        // 加载分组中的股票
        initGroups();

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
            scheduler.shutdownNow();
            Platform.exit();
        });
    }

    @NotNull
    private MenuBar getMenuBar() {

        MenuItem index = new MenuItem("查看指数");
        index.setOnAction(e -> {
            List<String> stockCodes = getCurrGroup().getTableView().getItems()
                    .stream()
                    .map(stock -> stock.getMarketCode() + "." + stock.getRawCode())
                    .toList();
            List<EastMoneyKlineUtil.StockKline> stockKlines;
            try {
              stockKlines = EastMoneyKlineUtil.calculateIndexKlines(stockCodes,
                      "20250101",
                      DateUtils.getCurrentDate(), 101, 0);
            } catch (Exception ex) {
                throw new RuntimeException(ex);
            }
            // 创建指数 K 线图窗口
            KLineStage indexStage = null;
            try {
                indexStage = new KLineStage(stockKlines, getCurrGroup().getName());
                indexStage.initOwner(stage);
                // 显示窗口
                indexStage.show();
            } catch (Exception ex) {
                throw new RuntimeException(ex);
            }
        });

        MenuItem addItem = new MenuItem("添加股票");
        addItem.setOnAction(e -> showAddStockDialog(stage));

        MenuItem addGroupItem = new MenuItem("添加分组");
        addGroupItem.setOnAction(e -> {
            TextInputDialog dialog = new TextInputDialog();
            dialog.setTitle("添加分组");
            dialog.setHeaderText("请输入新分组名称");
            dialog.setContentText("分组名称:");
            dialog.initOwner(stage);

            dialog.showAndWait().ifPresent(name -> {
                String groupName = name.trim();
                if (groupName.isEmpty()) {
                    FXAlert.info(stage, "输入无效", "分组名称不能为空！");
                    return;
                }
                // 添加到配置并检查是否已存在
                if (!GroupConfig.addGroup(groupName)) {
                    FXAlert.info(stage, "添加失败", "分组【" + groupName + "】已存在！");
                    return;
                }
                // 界面新增 Tab
                addGroupTab(groupName);
            });
        });

        Menu menu = new Menu("菜单", null, addItem, addGroupItem, index);

        MenuBar menuBar = new MenuBar(menu);
        menuBar.setPadding(new Insets(5, 10, 5, 10));
        return menuBar;
    }

    private void initGroups() {
        for (GroupConfig.Group g : GroupConfig.getGroups()) {
            addGroupTab(g.getName());
        }
    }

    private void addGroupTab(String groupName) {
        StockGroup group = new StockGroup(groupName);
        groups.put(groupName, group);

        Tab tab = new StockTab(group);
        // 右键菜单：删除分组
        ContextMenu contextMenu = new ContextMenu();
        MenuItem deleteItem = new MenuItem("删除分组");
        deleteItem.setOnAction(e -> {
            if (tabPane.getTabs().size() == 1) {
                FXAlert.info(stage, "删除分组", "无法删除唯一的分组");
                return;
            }
            FXAlert.confirm(stage, "删除分组", "确认删除分组【" + groupName + "】吗？\n删除后无法恢复！")
                    .ifPresent(btn -> {
                        if (btn == ButtonType.OK) {
                            // 删除数据
                            GroupConfig.removeGroup(groupName);
                            // 删除 UI
                            tabPane.getTabs().remove(tab);
                            groups.remove(groupName);
                        }
                    });
        });
        contextMenu.getItems().add(deleteItem);
        tab.setContextMenu(contextMenu);
        tab.setClosable(false);
        tabPane.getTabs().add(tab);

        tabPane.getSelectionModel().select(tab);
    }

    private void fetchAndUpdate() {
        try {
            Tab selected = tabPane.getSelectionModel().getSelectedItem();
            if (selected == null) return;

            String groupName = selected.getText();
            StockGroup group = groups.get(groupName);
            if (group == null) return;

            List<Stock> stocks = GroupConfig.getStocksOf(groupName);
            List<CompletableFuture<Optional<StockRow>>> futures = stocks.stream()
                    .map(s -> CompletableFuture.supplyAsync(() -> getSocketData(s.marketCode(), s.stockCode())))
                    .toList();

            List<Optional<StockRow>> results = futures.stream()
                    .map(CompletableFuture::join)
                    .toList();

            Platform.runLater(() -> {
                StockTableView table = group.getTableView();
                int idx = 0;
                for (Optional<StockRow> opt : results) {
                    if (opt.isEmpty()) continue;
                    StockRow row = opt.get();
                    idx++;
                    row.setIndex(idx);
                    String key = row.getMarketCode() + "_" + row.getRawCode();
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
            LOG.error("fetch error: " + e.getMessage());
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
                    .timeout(java.time.Duration.ofSeconds(8))
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
            if (!GroupConfig.addStock(getCurrGroup().getName(), market, code)) {
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
                StockTableView table = getCurrGroup().getTableView();
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
                waiting.close();
                ToastQueue.show(stage, "添加成功：" + (market.equals("0") ? "SZ" : "SH") + code + " · " + row.getName(),
                        2000);
            }));
        });
    }

    private StockGroup getCurrGroup() {
        StockTab selectedItem = (StockTab) tabPane.getSelectionModel().getSelectedItem();
        return selectedItem.getStockGroup();
    }

    /**
     * 校验股票是否存在：能从接口拿到名称/价格即认为存在，返回最新行数据
     */
    private CompletableFuture<Optional<StockRow>> validateStock(String market, String code) {
        // 后台校验，避免卡 UI
        return CompletableFuture.supplyAsync(() -> getSocketData(market, code));
    }


}
