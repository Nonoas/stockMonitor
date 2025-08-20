package indi.yiyi.stockmonitor;

import github.nonoas.jfx.flat.ui.theme.LightTheme;
import javafx.application.Application;
import javafx.stage.Stage;

public class StockApp extends Application {

    @Override
    public void start(Stage stage) {
        setUserAgentStylesheet(new LightTheme().getUserAgentStylesheet());
        MainStage mainStage = new MainStage();
        mainStage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
