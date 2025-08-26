package indi.yiyi.stockmonitor.view;

import indi.yiyi.stockmonitor.AppContext;
import indi.yiyi.stockmonitor.CSVConfig;
import indi.yiyi.stockmonitor.data.StockRow;
import javafx.beans.value.ChangeListener;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.css.PseudoClass;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.util.Callback;

import java.util.Comparator;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;


/**
 * @author huangshengsheng
 * @date 2025/8/26 17:47
 */
public class StockTableView extends TableView<StockRow> {
    private final Map<String, StockRow> rowByKey = new ConcurrentHashMap<>();
    private final ObservableList<StockRow> data = FXCollections.observableArrayList();

    private static final PseudoClass UP = PseudoClass.getPseudoClass("up");
    private static final PseudoClass DOWN = PseudoClass.getPseudoClass("down");

    public StockTableView() {
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
        colPrice.setCellFactory(formatNumber());

        TableColumn<StockRow, Number> colChangeAmt = new TableColumn<>("当日涨跌");
        colChangeAmt.setPrefWidth(120);
        colChangeAmt.setCellValueFactory(c -> c.getValue().changeAmtProperty());
        colChangeAmt.setCellFactory(formatNumber());

        getColumns().addAll(colIndex, colCode, colName, colChangeRate, colPrice, colChangeAmt);
        setItems(data);
        setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS);

        // 行颜色：涨红、跌绿、平默认
        setRowFactory(tv -> {
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

                var r = FXAlert.confirm(AppContext.getMainStage(), "确认删除", "确定要删除 " + item.getCode() + "（" + item.getName() + "）吗？");
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
                getItems().remove(item);

                // 3) 重新编号（可选）
                for (int i = 0; i < getItems().size(); i++) {
                    getItems().get(i).setIndex(i + 1);
                }
            });
            return row;
        });
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

    private Callback<TableColumn<StockRow, Number>, TableCell<StockRow, Number>> formatNumber() {
        return col -> new TableCell<>() {
            @Override
            protected void updateItem(Number value, boolean empty) {
                super.updateItem(value, empty);
                if (empty || value == null) {
                    setText(null);
                } else {
                    setText(String.format(Locale.CHINA, "%.3f", value.doubleValue()));
                }
            }
        };
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

    public boolean containsStockKey(String key) {
        return rowByKey.containsKey(key);
    }

    public Map<String, StockRow> getRowByKey() {
        return rowByKey;
    }

}