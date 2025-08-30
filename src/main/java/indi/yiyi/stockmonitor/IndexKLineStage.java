package indi.yiyi.stockmonitor;

import indi.yiyi.stockmonitor.utils.EastMoneyKlineUtil;
import javafx.scene.Scene;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Line;
import javafx.scene.shape.Rectangle;
import javafx.stage.Stage;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class IndexKLineStage extends Stage {

    private static final double CANDLE_WIDTH = 8;
    private static final double CANDLE_GAP = 2;
    private static final double PADDING_LEFT = 60;
    private static final double PADDING_BOTTOM = 40;
    private static final double CHART_HEIGHT = 600;
    private static final double CHART_WIDTH = 1000;
    private static final double WEIGHT = 100;

    public IndexKLineStage(String title, List<String> stockCodes) throws Exception {
        setTitle(title);

        // 1️⃣ 拉取每只股票 K 线
        Map<String, List<EastMoneyKlineUtil.StockKline>> stockKlines = new HashMap<>();
        for (String code : stockCodes) {
            List<EastMoneyKlineUtil.StockKline> klines =
                    EastMoneyKlineUtil.getHistoryKlines(code, "20240101", "20250830", 101, 1);
            stockKlines.put(code, klines);
        }

        if (stockKlines.isEmpty()) return;

        List<String> dates = stockKlines.values().iterator().next().stream().map(k -> k.date).toList();

        // 2️⃣ 计算指数 K 线（开盘/收盘/最高/最低）
        List<Double> indexOpen = new ArrayList<>();
        List<Double> indexClose = new ArrayList<>();
        List<Double> indexHigh = new ArrayList<>();
        List<Double> indexLow = new ArrayList<>();
        double divisor = 1_000_000_000.0;

        for (int i = 0; i < dates.size(); i++) {
            double open = 0, close = 0, high = 0, low = Double.MAX_VALUE;
            for (String code : stockCodes) {
                EastMoneyKlineUtil.StockKline k = stockKlines.get(code).get(i);
                open += k.open * WEIGHT;
                close += k.close * WEIGHT;
                high = Math.max(high, k.high * WEIGHT);
                low = Math.min(low, k.low * WEIGHT);
            }
            indexOpen.add(open / divisor);
            indexClose.add(close / divisor);
            indexHigh.add(high / divisor);
            indexLow.add(low / divisor);
        }

        double minPrice = indexLow.stream().mapToDouble(d -> d).min().orElse(0);
        double maxPrice = indexHigh.stream().mapToDouble(d -> d).max().orElse(100);
        double priceRange = maxPrice - minPrice;

        // 3️⃣ 固定坐标轴 Pane
        Pane axisPane = new Pane();
        axisPane.setPrefSize(CHART_WIDTH, CHART_HEIGHT + PADDING_BOTTOM);

        // 纵轴刻度
        int step = 10;
        for (int i = 0; i <= step; i++) {
            double y = CHART_HEIGHT - i * CHART_HEIGHT / step;
            double value = minPrice + i * priceRange / step;
            Line line = new Line(PADDING_LEFT, y, CHART_WIDTH, y);
            line.setStroke(Color.LIGHTGRAY);
            axisPane.getChildren().add(line);

            javafx.scene.text.Text text = new javafx.scene.text.Text(String.format("%.2f", value));
            text.setX(5);
            text.setY(y + 5);
            axisPane.getChildren().add(text);
        }

        // 横轴刻度（日期简略显示每隔几天）
        int dateStep = Math.max(1, dates.size() / 10);
        for (int i = 0; i < dates.size(); i += dateStep) {
            double x = PADDING_LEFT + i * (CANDLE_WIDTH + CANDLE_GAP);
            javafx.scene.text.Text text = new javafx.scene.text.Text(dates.get(i));
            text.setX(x);
            text.setY(CHART_HEIGHT + 15);
            axisPane.getChildren().add(text);
        }

        // 4️⃣ 绘制可滚动蜡烛图
        Pane contentPane = new Pane();
        double contentWidth = PADDING_LEFT + dates.size() * (CANDLE_WIDTH + CANDLE_GAP);
        contentPane.setPrefSize(contentWidth, CHART_HEIGHT + PADDING_BOTTOM);

        for (int i = 0; i < dates.size(); i++) {
            double x = PADDING_LEFT + i * (CANDLE_WIDTH + CANDLE_GAP);

            double openY = CHART_HEIGHT - ((indexOpen.get(i) - minPrice) / priceRange * CHART_HEIGHT);
            double closeY = CHART_HEIGHT - ((indexClose.get(i) - minPrice) / priceRange * CHART_HEIGHT);
            double highY = CHART_HEIGHT - ((indexHigh.get(i) - minPrice) / priceRange * CHART_HEIGHT);
            double lowY = CHART_HEIGHT - ((indexLow.get(i) - minPrice) / priceRange * CHART_HEIGHT);

            // 影线
            Line line = new Line(x + CANDLE_WIDTH / 2, highY, x + CANDLE_WIDTH / 2, lowY);
            line.setStroke(Color.BLACK);

            // 蜡身
            double rectY = Math.min(openY, closeY);
            double rectHeight = Math.max(Math.abs(closeY - openY), 1);
            Rectangle rect = new Rectangle(x, rectY, CANDLE_WIDTH, rectHeight);
            rect.setFill(closeY >= openY ? Color.GREEN : Color.RED);

            // Tooltip
            Tooltip tooltip = new Tooltip(
                    "日期: " + dates.get(i) +
                            "\n开盘: " + String.format("%.2f", indexOpen.get(i)) +
                            "\n收盘: " + String.format("%.2f", indexClose.get(i)) +
                            "\n最高: " + String.format("%.2f", indexHigh.get(i)) +
                            "\n最低: " + String.format("%.2f", indexLow.get(i))
            );
            tooltip.setShowDelay(javafx.util.Duration.ZERO);
            tooltip.setHideDelay(javafx.util.Duration.ZERO);
            Tooltip.install(rect, tooltip);
            Tooltip.install(line, tooltip);

            contentPane.getChildren().addAll(line, rect);
        }

        ScrollPane scrollPane = new ScrollPane(contentPane);
        scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.ALWAYS);
        scrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scrollPane.setPrefViewportWidth(CHART_WIDTH);
        scrollPane.setPrefViewportHeight(CHART_HEIGHT + PADDING_BOTTOM);

        // 5️⃣ StackPane：底层坐标轴固定，上层滚动蜡烛图
        StackPane root = new StackPane();
        root.getChildren().addAll(axisPane, scrollPane);

        Scene scene = new Scene(root, CHART_WIDTH, CHART_HEIGHT + PADDING_BOTTOM + 50);
        setScene(scene);
    }
}
