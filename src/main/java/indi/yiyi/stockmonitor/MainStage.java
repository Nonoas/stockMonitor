package indi.yiyi.stockmonitor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import github.nonoas.jfx.flat.ui.control.UIFactory;
import github.nonoas.jfx.flat.ui.stage.AppStage;
import indi.yiyi.stockmonitor.data.StockRow;
import indi.yiyi.stockmonitor.utils.UIUtil;
import indi.yiyi.stockmonitor.view.FXAlert;
import indi.yiyi.stockmonitor.view.PlayfulHelper;
import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.css.PseudoClass;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuBar;
import javafx.scene.control.MenuItem;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.image.Image;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import javafx.util.Callback;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Collections;
import java.util.Comparator;
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
    private final TableView<StockRow> table = new TableView<>();
    private final ObservableList<StockRow> data = FXCollections.observableArrayList();
    private final Map<String, StockRow> rowByKey = new ConcurrentHashMap<>();
    private ScheduledExecutorService scheduler;

    private static final PseudoClass UP = PseudoClass.getPseudoClass("up");
    private static final PseudoClass DOWN = PseudoClass.getPseudoClass("down");


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

        TableColumn<StockRow, Number> colChangeRate = new TableColumn<>("涨跌幅");
        colChangeRate.setPrefWidth(100);
        colChangeRate.setCellValueFactory(c -> c.getValue().changeRateProperty());
        colChangeRate.setComparator(Comparator.comparingDouble(n -> n == null ? 0.0 : n.doubleValue()));
        // 显示成百分比文本
        colChangeRate.setCellFactory(percentCell(2));

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
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS);

        // 行颜色：涨红、跌绿、平默认
        table.setRowFactory(tv -> {
            TableRow<StockRow> row = new TableRow<>();

            ChangeListener<Number> amtListener = (obs, ov, nv) -> applyPseudo(row);

            row.itemProperty().addListener((obs, oldItem, newItem) -> {
                if (oldItem != null) {
                    oldItem.changeAmtProperty().removeListener(amtListener);
                }
                if (newItem != null) {
                    newItem.changeAmtProperty().addListener(amtListener);
                }
                applyPseudo(row);
            });

            row.emptyProperty().addListener((obs, wasEmpty, isEmpty) -> applyPseudo(row));

            MenuItem del = new MenuItem("删除");
            ContextMenu cm = new ContextMenu(del);

            // 仅在行非空时显示
            row.contextMenuProperty().bind(
                    javafx.beans.binding.Bindings.when(row.emptyProperty())
                            .then((ContextMenu) null)
                            .otherwise(cm)
            );

            del.setOnAction(evt -> {
                StockRow item = row.getItem();
                if (item == null) return;

                var r = FXAlert.confirm(stage, "确认删除", "确定要删除 " + item.getCode() + "（" + item.getName() + "）并从 CSV 移除吗？");
                if (r.isEmpty() || r.get() != ButtonType.OK) return;

                // 1) 从 CSV 移除
                boolean ok = CSVConfig.removeStock(item.getMarketCode(), item.getRawCode());
                if (!ok) {
                    new Alert(Alert.AlertType.ERROR, "从 CSV 移除失败，可能该条目不存在。").showAndWait();
                    return;
                }

                // 2) 从表格移除
                String key = item.getMarketCode() + "_" + item.getRawCode();
                rowByKey.remove(key);
                data.remove(item);

                // 3) 重新编号（可选）
                for (int i = 0; i < data.size(); i++) {
                    data.get(i).setIndex(i + 1);
                }
            });
            return row;
        });

        MenuItem mi = new MenuItem("点我看看");
        mi.setOnAction(e -> PlayfulHelper.start(stage)); // 传你的主 Stage

        MenuItem addItem = new MenuItem("添加股票…");
        addItem.setOnAction(e -> showAddStockDialog(stage));


        Menu menu = new Menu("菜单", null, addItem, mi);

        MenuBar menuBar = new MenuBar(menu);
        menuBar.setPadding(new Insets(5, 10, 5, 10));

        registryDragger(menuBar);

        StackPane stackPane = new StackPane(table);
        stackPane.setPadding(new Insets(10));

        BorderPane root = new BorderPane(stackPane);
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

    private void applyPseudo(TableRow<StockRow> row) {
        StockRow item = row.getItem();
        boolean up = false, down = false;
        if (item != null && !row.isEmpty()) {
            double v = item.getChangeAmt();
            up = v > 0;
            down = v < 0;
        }
        row.pseudoClassStateChanged(UP, up);
        row.pseudoClassStateChanged(DOWN, down);
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

    // 定义一个百分比单元格工厂
    private static Callback<TableColumn<StockRow, Number>, TableCell<StockRow, Number>> percentCell(int scale) {
        return col -> new TableCell<>() {
            @Override
            protected void updateItem(Number v, boolean empty) {
                super.updateItem(v, empty);
                if (empty || v == null) {
                    setText(null);
                } else {
                    setText(String.format(Locale.CHINA, "%." + scale + "f%%", v.doubleValue() * 100));
                }
            }
        };
    }

    private void showAddStockDialog(Stage owner) {
        Dialog<ButtonType> dlg = new Dialog<>();
        dlg.initOwner(owner);
        dlg.setTitle("添加股票");
        dlg.setHeaderText("请输入市场与股票代码（例如：0 / 000001）");

        ComboBox<String> marketBox = new ComboBox<>();
        marketBox.getItems().addAll("0（深市SZ）", "1（沪市SH）");
        marketBox.getSelectionModel().selectFirst();

        TextField codeField = new TextField();
        codeField.setPromptText("6位数字，如 000001 / 600519 / 513100");
        codeField.textProperty().addListener((obs, ov, nv) -> {
            if (nv != null) {
                String digits = nv.replaceAll("\\D", "");
                if (!digits.equals(nv)) codeField.setText(digits);
                if (digits.length() > 6) codeField.setText(digits.substring(0, 7));
            }
        });

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.addRow(0, new Label("市场："), marketBox);
        grid.addRow(1, new Label("代码："), codeField);
        grid.setStyle("-fx-padding: 10;");
        dlg.getDialogPane().setContent(grid);

        ButtonType ok = new ButtonType("添加", ButtonBar.ButtonData.OK_DONE);
        ButtonType cancel = ButtonType.CANCEL;
        dlg.getDialogPane().getButtonTypes().setAll(ok, cancel);

        Node okBtn = dlg.getDialogPane().lookupButton(ok);
        okBtn.setDisable(true);
        codeField.textProperty().addListener((obs, ov, nv) ->
                okBtn.setDisable(nv == null || !nv.matches("\\d{1,8}"))
        );

        var res = dlg.showAndWait();
        if (res.isEmpty() || res.get() != ok) return;

        String market = marketBox.getSelectionModel().getSelectedItem().startsWith("0") ? "0" : "1";
        String code = codeField.getText().trim();
        // === 新增：校验股票代码必须是6位 ===
        if (code.length() != 6) {
            FXAlert.error(owner, "输入错误", "股票代码必须是 6 位数字！");
            return;
        }

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
            StockRow existed = rowByKey.get(key);
            if (existed == null) {
                row.setIndex(data.size() + 1);
                rowByKey.put(key, row);
                data.add(row);
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
    }

    /**
     * 校验股票是否存在：能从接口拿到名称/价格即认为存在，返回最新行数据
     */
    private CompletableFuture<Optional<StockRow>> validateStock(String market, String code) {
        // 后台校验，避免卡 UI
        return CompletableFuture.supplyAsync(() -> getSocketData(market, code));
    }


}
