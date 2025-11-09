package indi.yiyi.stockmonitor;

import github.nonoas.jfx.flat.ui.stage.AppStage;
import javafx.scene.image.Image;

import java.util.Collections;

public class BaseStage extends AppStage {
    public BaseStage() {
        setTitle("盯盘小助手");
        setMinWidth(420);
        setMinHeight(200);
        addIcons(Collections.singleton(new Image("image/logo.png")));
        getStage().setWidth(420);
        getStage().setHeight(300);
        getStage().getScene().getStylesheets().add("css/style.css");
    }
}
