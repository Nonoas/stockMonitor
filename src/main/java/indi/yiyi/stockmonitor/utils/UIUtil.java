package indi.yiyi.stockmonitor.utils;

import javafx.scene.control.Alert;
import javafx.stage.Stage;

/**
 * @author Nonoas
 * @date 2025/8/21
 * @since
 */
public class UIUtil {
    public static void setDialogIcon(Alert alert, Stage owner) {
        Stage stage = (Stage) alert.getDialogPane().getScene().getWindow();
        if (owner != null && !owner.getIcons().isEmpty()) {
            stage.getIcons().add(owner.getIcons().get(0));
        }
    }
}
