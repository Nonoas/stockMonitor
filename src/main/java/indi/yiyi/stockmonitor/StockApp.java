package indi.yiyi.stockmonitor;

import github.nonoas.jfx.flat.ui.theme.LightTheme;
import indi.yiyi.stockmonitor.utils.AppConfig;
import javafx.application.Application;
import javafx.stage.Stage;

public class StockApp extends Application {

    @Override
    public void init() throws Exception {
        AppConfig.load();
    }

    @Override
    public void start(Stage stage) {
        setUserAgentStylesheet(new LightTheme().getUserAgentStylesheet());
        MainStage mainStage = new MainStage();
        AppContext.setMainStage(mainStage.getStage());
        mainStage.show();
    }

    @Override
    public void stop() throws Exception {
        AppConfig.getConfigManager().saveConfig();
    }
}
