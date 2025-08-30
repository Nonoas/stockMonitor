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
        setMaximized(false);
        Label titleLabel = new Label(name);
        titleLabel.setFont(new Font(40));
        titleLabel.setAlignment(Pos.CENTER);
        titleLabel.setMaxWidth(Double.MAX_VALUE);

        Pane pane = new Pane();
        double width = 1200;
        double height = 600;
        double padding = 50;

        double maxPrice = klineData.stream().mapToDouble(k -> k.high).max().orElse(1);
        double minPrice = klineData.stream().mapToDouble(k -> k.low).min().orElse(0);

        int n = klineData.size();
        double candleWidth = (width - 2 * padding) / n * 0.6;
        double spacing = (width - 2 * padding) / n;

        // 网格和坐标
        for (int i = 0; i <= 10; i++) {
            double y = padding + i * (height - 2 * padding) / 10;
            Line line = new Line(padding, y, width - padding, y);
            line.setStroke(Color.LIGHTGRAY);
            line.getStrokeDashArray().addAll(5.0, 5.0);
            pane.getChildren().add(line);
        }

        Label infoLabel = new Label();
        infoLabel.setStyle("-fx-background-color: white; -fx-border-color: black;");
        infoLabel.setVisible(false);
        pane.getChildren().add(infoLabel);

        Line verticalLine = new Line();
        verticalLine.setStroke(Color.GRAY);
        verticalLine.setVisible(false);
        Line horizontalLine = new Line();
        horizontalLine.setStroke(Color.GRAY);
        horizontalLine.setVisible(false);
        pane.getChildren().addAll(verticalLine, horizontalLine);

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
            double rectHeight = Math.max(Math.abs(yClose - yOpen), 1);
            Rectangle rect = new Rectangle(x - candleWidth / 2, rectY, candleWidth, rectHeight);
            rect.setFill(k.close >= k.open ? Color.RED : Color.GREEN);

            int index = i;
            rect.setOnMouseEntered(e -> {
                infoLabel.toFront();
                EastMoneyKlineUtil.StockKline kline = klineData.get(index);
                infoLabel.setText(
                        "开盘: " + kline.open +
                                "\n收盘: " + kline.close +
                                "\n最高: " + kline.high +
                                "\n最低: " + kline.low
                );
                infoLabel.setVisible(true);
                verticalLine.setVisible(true);
                horizontalLine.setVisible(true);
            });

            rect.setOnMouseMoved(e -> {
                infoLabel.setLayoutX(e.getX() + 20);
                infoLabel.setLayoutY(e.getY() - 10);

                verticalLine.setStartX(e.getX());
                verticalLine.setEndX(e.getX());
                verticalLine.setStartY(padding);
                verticalLine.setEndY(height - padding);

                horizontalLine.setStartY(e.getY());
                horizontalLine.setEndY(e.getY());
                horizontalLine.setStartX(padding);
                horizontalLine.setEndX(width - padding);
            });

            rect.setOnMouseExited(e -> {
                infoLabel.setVisible(false);
                verticalLine.setVisible(false);
                horizontalLine.setVisible(false);
            });

            pane.getChildren().addAll(line, rect);
        }

        VBox root = new VBox();
        VBox.setVgrow(pane, Priority.ALWAYS);
        root.setFillWidth(true);
        root.getChildren().addAll(titleLabel, pane);

        Scene scene = new Scene(root, width, height);
        this.setTitle("K线图");
        this.setScene(scene);
    }
}
