package indi.yiyi.stockmonitor;

import indi.yiyi.stockmonitor.utils.EastMoneyKlineUtil;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Line;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Font;
import javafx.stage.Stage;

import java.util.List;

public class KLineStage extends Stage {

    public KLineStage(List<EastMoneyKlineUtil.StockKline> klineData, String name) {
        Label label = new Label(name);
        label.setFont(new Font(40));
        label.setAlignment(Pos.CENTER);
        label.setMaxWidth(Double.MAX_VALUE);

        Pane pane = new Pane();
        double width = 1200;
        double height = 600;
        double padding = 50;

        double maxPrice = klineData.stream().mapToDouble(k -> k.high).max().orElse(1);
        double minPrice = klineData.stream().mapToDouble(k -> k.low).min().orElse(0);

        int n = klineData.size();
        double candleWidth = (width - 2 * padding) / n * 0.6;
        double spacing = (width - 2 * padding) / n;

        for (int i = 0; i < klineData.size(); i++) {
            EastMoneyKlineUtil.StockKline k = klineData.get(i);

            double x = padding + i * spacing + spacing / 2;
            double yHigh = padding + (maxPrice - k.high) / (maxPrice - minPrice) * (height - 2 * padding);
            double yLow = padding + (maxPrice - k.low) / (maxPrice - minPrice) * (height - 2 * padding);
            double yOpen = padding + (maxPrice - k.open) / (maxPrice - minPrice) * (height - 2 * padding);
            double yClose = padding + (maxPrice - k.close) / (maxPrice - minPrice) * (height - 2 * padding);

            Line line = new Line(x, yHigh, x, yLow);
            line.setStroke(k.close >= k.open ? Color.RED : Color.GREEN);

            double rectY = Math.min(yOpen, yClose);
            double rectHeight = Math.abs(yClose - yOpen);
            Rectangle rect = new Rectangle(x - candleWidth / 2, rectY, candleWidth, rectHeight);
            rect.setFill(k.close >= k.open ? Color.RED : Color.GREEN);

            pane.getChildren().addAll(line, rect);
        }
        VBox root = new VBox();
        VBox.setVgrow(pane, Priority.ALWAYS);
        root.setFillWidth(true);
        root.getChildren().addAll(label, pane);
        Scene scene = new Scene(root, width, height);
        this.setTitle("K线图");
        this.setScene(scene);
    }
}
