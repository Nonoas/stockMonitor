package indi.yiyi.stockmonitor.view;

/**
 * @author Nonoas
 * @date 2025/8/21
 * @since
 */

import indi.yiyi.stockmonitor.utils.UIUtil;
import javafx.application.Platform;
import javafx.geometry.Rectangle2D;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.TextInputDialog;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class PlayfulHelper {

    private static final int MAX_NAME_LEN = 16;
    private static final String TARGET_NAME = "黄铱铱";
    private static final Random RNG = new Random();

    // 入口
    public static void start(Stage owner) {
        String name = askNameLoop(owner);
        if (name == null) return; // 用户取消

        if (!TARGET_NAME.equals(name)) {
            Alert a = new Alert(Alert.AlertType.INFORMATION);
            a.initOwner(owner);
            a.setHeaderText(null);
            a.setTitle("啊？");
            a.setContentText(name + "？ 我好像不认识你哦～(>_<)！");
            a.showAndWait();
            return;
        }

        showRunawayWindow(owner);
    }

    // 循环询问姓名，长度>16 先惊叹再继续询问
    private static String askNameLoop(Stage owner) {
        while (true) {
            TextInputDialog dialog = new TextInputDialog();
            dialog.initOwner(owner);
            dialog.setTitle("问候");
            dialog.setHeaderText("你是哪位？");
            dialog.setContentText("姓名：");
            // 简单长度拦截（手滑输太长也会被截断）
            dialog.getEditor().textProperty().addListener((obs, ov, nv) -> {
                if (nv != null && nv.length() > MAX_NAME_LEN) {
                    dialog.getEditor().setText(nv.substring(0, MAX_NAME_LEN));
                    dialog.getEditor().positionCaret(MAX_NAME_LEN);
                }
            });

            var opt = dialog.showAndWait();
            if (opt.isEmpty()) return null; // 取消
            String name = opt.get().trim();
            if (name.isEmpty()) continue;

            if (name.length() > MAX_NAME_LEN) {
                // 理论上进不到这里（已截断），保底
                exclaimTooLong(owner, name.length());
                continue;
            }
            if (name.length() >= 9) {
                exclaimTooLong(owner, name.length());
            }
            return name;
        }
    }

    private static void exclaimTooLong(Stage owner, int len) {
        String msg;
        if (len >= 16) msg = "哇哦，这个名字……跟长城一样长呀！";
        else if (len >= 8) msg = "哇～名字好长！我小脑袋记不住啦～";
        else if (len >= 5) msg = "呀！名字这么威风～给我点时间背一背！";
        else msg = "嘿，这名字有点长哦~";
        Alert a = new Alert(Alert.AlertType.INFORMATION);
        a.initOwner(owner);
        a.setTitle("惊叹！");
        a.setHeaderText(null);
        a.setContentText(msg);
        a.showAndWait();
    }

    // “抓我呀”模式窗口
    private static void showRunawayWindow(Stage owner) {
        Stage s = new Stage(StageStyle.UTILITY);
        s.initOwner(owner);
        s.initModality(Modality.WINDOW_MODAL);
        s.setAlwaysOnTop(true);
        s.setTitle("抓我呀～");

        Label tips = new Label("是你呀，铱铱！你抓到我我就跟你玩个游戏哦(>▽<)");
        Button btn = new Button("点我呀");
        btn.setDefaultButton(true);

        // 俏皮话列表
        List<String> lines = new ArrayList<>(List.of(
                "咻——我溜走啦！",
                "要抓我？再快一点点～",
                "哎呀差一点点～",
                "喂喂别点我肚皮，痒痒的！",
                "你再点，我就躲右边啦！",
                "哇！高手～不过我更快！",
                "看我瞬移术！嗖～",
                "嘿嘿，抓不到～"
        ));

        VBox root = new VBox(10, tips, btn);
        root.setStyle("-fx-padding: 12; -fx-font-size: 14px;");
        Scene scene = new Scene(root);
        s.setScene(scene);

        // 不可关闭（前 5 次）
        final boolean[] closable = {false};
        s.setOnCloseRequest(ev -> {
            if (!closable[0]) ev.consume();
        });

        // 初始位置随机
        placeRandomly(s, owner);

        final int[] clicks = {0};
        btn.addEventFilter(MouseEvent.MOUSE_PRESSED, ev -> {
            clicks[0]++;

            // 每次换一句话
            String line = lines.get(RNG.nextInt(lines.size()));
            tips.setText(line);

            if (clicks[0] > 5) {
                s.close();
                // 第 5 次：给选择
                closable[0] = true; // 允许关闭
                Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
                confirm.initOwner(s);
                confirm.setTitle("开始游戏吗？");
                confirm.setHeaderText("哼~算你厉害，终于抓到我了！");
                confirm.setContentText("要不要开始猜数字的小游戏？");
                UIUtil.setDialogIcon(confirm, owner);
                ButtonType start = new ButtonType("好呀");
                ButtonType later = new ButtonType("先不玩");
                confirm.getButtonTypes().setAll(start, later);
                var res = confirm.showAndWait();
                if (res.isPresent() && res.get() == start) {
                    s.hide();
                    startGuessGame(owner);
                } else {
                    // 留在原地，可关可不关
                    tips.setText("那我先在这儿等你~ 随时叫我开局！");
                }
            } else {
                // 继续乱跑
                placeRandomly(s, owner);
            }
        });

        s.show();
    }

    // 把窗口随机放到屏幕（可视范围）内
    private static void placeRandomly(Stage s, Stage anchor) {
        Screen screen = Screen.getScreensForRectangle(
                anchor.getX(), anchor.getY(), anchor.getWidth(), anchor.getHeight()
        ).stream().findFirst().orElse(Screen.getPrimary());

        Rectangle2D bounds = screen.getVisualBounds();

        double w = 320, h = 140; // 预估窗口大小
        double x = bounds.getMinX() + RNG.nextDouble() * Math.max(1, bounds.getWidth() - w);
        double y = bounds.getMinY() + RNG.nextDouble() * Math.max(1, bounds.getHeight() - h);
        s.setX(x);
        s.setY(y);
    }


    // ======== 1~1024 猜数游戏（10 次是/否）========
    private static void startGuessGame(Stage owner) {
        // 说明弹窗
        info(owner,
                "游戏规则",
                "你在心里想一个 1~1024 的整数，\n我只问 10 个问题，\n你只需要回到 “是” 或 “否” ，我就能猜到！\n准备好就开始咯～"
        );

        int low = 1, high = 1024;
        int steps = 0;
        while (low < high && steps < 10) {
            int mid = (low + high) >>> 1;
            boolean yes = askYesNo(owner,
                    "第 " + (steps + 1) + " 问",
                    "悄悄告诉我：你的数字是否大于 " + mid + " 呢？\n（点“是”代表更大，点“否”代表不大于）",
                    "是", "否"
            );
            if (yes) low = mid + 1;
            else high = mid;
            steps++;
            playfulToast(owner, sweetLine(steps));
        }

        int answer = low;
        // 收尾：若还剩歧义（极罕见）做一次确认

        // 结果弹窗
        Alert done = new Alert(Alert.AlertType.INFORMATION);
        done.initOwner(owner);
        done.setTitle("我猜到了！");
        done.setHeaderText("开心！我有 99% 把握～");
        done.setContentText("你想的数字是：\n\n【 " + answer + " 】\n\n如果我猜错了，那一定是你笑场了～再来一把？");
        ButtonType again = new ButtonType("再玩一次");
        ButtonType close = new ButtonType("下次再玩");
        done.getButtonTypes().setAll(again, close);
        var res = done.showAndWait();
        if (res.isPresent() && res.get() == again) {
            Platform.runLater(() -> startGuessGame(owner));
        }
    }

    private static boolean askYesNo(Stage owner, String title, String msg, String yes, String no) {
        Alert q = new Alert(Alert.AlertType.CONFIRMATION);
        q.initOwner(owner);
        q.setTitle(title);
        q.setHeaderText(null);
        q.setContentText(msg);
        ButtonType YES = new ButtonType(yes, ButtonBar.ButtonData.YES);
        ButtonType NO = new ButtonType(no, ButtonBar.ButtonData.NO);
        q.getButtonTypes().setAll(YES, NO);
        return q.showAndWait().filter(bt -> bt == YES).isPresent();
    }

    private static void info(Stage owner, String title, String msg) {
        Alert a = new Alert(Alert.AlertType.INFORMATION);
        a.initOwner(owner);
        a.setTitle(title);
        a.setHeaderText(null);
        a.setContentText(msg);
        a.getDialogPane().setMinHeight(Region.USE_PREF_SIZE);
        a.showAndWait();
    }

    // 每步逗趣提示
    private static String sweetLine(int step) {
        return switch (step) {
            case 1 -> "嘿嘿～我感觉离真相近了一丢丢！";
            case 2 -> "嗯哼～我在认真推理呢，别笑我～";
            case 3 -> "前方高能预警，真相渐渐浮出水面～";
            case 4 -> "小姐姐，我的第六感上线啦！";
            case 5 -> "嘘——别出声，我要锁定目标了！";
            case 6 -> "马上就要揭晓谜底咯～";
            case 7 -> "嘿，这一步非常关键！";
            case 8 -> "就要成功啦！紧张不紧张？";
            case 9 -> "最后两问，握住你的幸运！";
            default -> "冲鸭～冲向答案！";
        };
    }

    // 轻量小提示（非阻塞）
    private static void playfulToast(Stage owner, String text) {
        Stage toast = new Stage(StageStyle.TRANSPARENT);
        toast.initOwner(owner);
        toast.setAlwaysOnTop(true);

        Label l = new Label(text);
        l.setStyle(
                "-fx-background-color: rgba(0,0,0,0.75);" +
                        "-fx-text-fill: white;" +
                        "-fx-padding: 8 12;" +
                        "-fx-background-radius: 9999;" +
                        "-fx-font-size: 13px;"
        );

        HBox root = new HBox(l);
        root.setStyle("-fx-background-color: transparent;"); // 关键：容器透明

        Scene sc = new Scene(root);
        sc.setFill(javafx.scene.paint.Color.TRANSPARENT);   // 关键：Scene透明
        toast.setScene(sc);

        Rectangle2D vb = Screen.getPrimary().getVisualBounds();
        double x = vb.getMinX() + vb.getWidth() - 320;
        double y = vb.getMinY() + vb.getHeight() - 120;
        toast.setX(x);
        toast.setY(y);
        toast.show();

        // 1.2 秒后自动消失
        new Thread(() -> {
            try {
                Thread.sleep(1200);
            } catch (InterruptedException ignored) {}
            Platform.runLater(toast::close);
        }, "toast-hide").start();
    }

}
