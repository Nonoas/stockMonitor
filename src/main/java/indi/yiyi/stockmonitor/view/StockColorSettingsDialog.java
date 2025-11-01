package indi.yiyi.stockmonitor.view;


import indi.yiyi.stockmonitor.data.StockColors;
import javafx.geometry.Insets;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ColorPicker;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.layout.GridPane;

/**
 * 独立的颜色设置对话框，用于修改股票颜色设置。
 * 返回一个 StockColors 对象。
 *
 * @author huangshengsheng
 * @date 2025/11/1 9:53
 */
public class StockColorSettingsDialog extends Dialog<StockColors> {

    private final ColorPicker upColorPicker;
    private final ColorPicker downColorPicker;
    private final ColorPicker flatColorPicker;

    public StockColorSettingsDialog() {
        // 设置对话框的基本属性
        setTitle("股票颜色设置");

        // --- 1. 初始化 ColorPicker 并设置初始值 ---
        upColorPicker = new ColorPicker();
        downColorPicker = new ColorPicker();
        flatColorPicker = new ColorPicker();

        // --- 2. 创建布局并添加控件 ---
        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        // 增加内边距，使对话框看起来更美观
        grid.setPadding(new Insets(20, 150, 10, 10));

        grid.add(new Label("上涨颜色:"), 0, 0);
        grid.add(upColorPicker, 1, 0);

        grid.add(new Label("下跌颜色:"), 0, 1);
        grid.add(downColorPicker, 1, 1);

        grid.add(new Label("平盘颜色:"), 0, 2);
        grid.add(flatColorPicker, 1, 2);

        getDialogPane().setContent(grid);

        // --- 3. 添加按钮 (确定和取消) ---
        ButtonType okButtonType = new ButtonType("确定", ButtonBar.ButtonData.OK_DONE);
        getDialogPane().getButtonTypes().addAll(okButtonType, ButtonType.CANCEL);

        // --- 4. 设置结果转换器 ---
        // 只有当点击“确定”时，才会执行这个转换器并返回结果
        setResultConverter(dialogButton -> {
            if (dialogButton == okButtonType) {
                // 返回一个新的 StockColors 对象，包含用户选择的值
                return new StockColors(
                        upColorPicker.getValue(),
                        downColorPicker.getValue(),
                        flatColorPicker.getValue()
                );
            }
            // 如果点击取消，返回 null
            return null;
        });
    }
}