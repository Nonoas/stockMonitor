package indi.yiyi.stockmonitor.view;

import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.service.AiServices;
import indi.yiyi.stockmonitor.BaseStage;
import indi.yiyi.stockmonitor.data.Stock;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;

import java.util.List;

/**
 * AIStage: 使用 LangChain4j AiServices，支持动态注入股票上下文。
 */
public class AIStage extends BaseStage {

    // UI 组件
    private final TextArea chatHistoryArea;
    private final TextField inputField;
    private final Button sendButton;

    private final Assistant assistant;

    /**
     * 记忆 ID ，暂时写死
     */
    public static final int CHAT_MEMORY_ID = 0;

    /**
     * 构造函数，初始化模型、内存和 UI。
     */
    public AIStage(String deepseekApiKey, List<Stock> stocksOfCurrentGroup) {
        if (deepseekApiKey == null || deepseekApiKey.isEmpty()) {
            throw new IllegalArgumentException("DEEPSEEK_API_KEY 不能为空");
        }

        // 1. 初始化模型
        OpenAiChatModel model = OpenAiChatModel.builder()
                .baseUrl("https://api.deepseek.com/v1")
                .apiKey(deepseekApiKey)
                .modelName("deepseek-chat")
                .build();

        this.assistant = AiServices.builder(Assistant.class)
                .chatLanguageModel(model)
                .chatMemoryProvider(memoryId -> MessageWindowChatMemory.withMaxMessages(10))
                .systemMessageProvider(memoryId -> injectDynamicContext(stocksOfCurrentGroup))
                .build();

        // 5. 初始化 UI 组件
        this.chatHistoryArea = new TextArea();
        this.chatHistoryArea.setEditable(false);
        this.chatHistoryArea.setWrapText(true);
        this.chatHistoryArea.setPromptText("对话历史将显示在此处...");

        this.inputField = new TextField();
        this.inputField.setPromptText("输入你的消息...");

        this.sendButton = new Button("发送");
        this.sendButton.setOnAction(e -> sendMessage());

        this.inputField.setOnAction(e -> sendMessage());

        // 6. 布局
        HBox inputContainer = new HBox(5, inputField, sendButton);
        HBox.setHgrow(inputField, Priority.ALWAYS);
        inputContainer.setPadding(new Insets(10, 0, 0, 0));

        Region header = new Region();
        header.setPrefHeight(30);

        registryDragger(header);

        BorderPane root = new BorderPane();
        root.setCenter(chatHistoryArea);
        root.setTop(header);
        root.setBottom(inputContainer);
        root.setPadding(new Insets(10));

        // 7. Stage 配置
        setTitle("DeepSeek AI 股票助理 (动态上下文)");
        setContentView(root);

        // 8. 启动时提示上下文已加载
        chatHistoryArea.appendText("欢迎使用 DeepSeek 股票助理。\n");
        chatHistoryArea.appendText("您的股票持仓信息已作为系统上下文自动加载。\n\n");
    }

    /**
     * **核心方法：** 动态加载股票数据并将其作为 SystemMessage 注入到 ChatMemory 中。
     */
    private String injectDynamicContext(List<Stock> stocksOfCurrentGroup) {
        String systemInstruction = "你现在是一名专业的股票助理。在回答用户问题时，请记住以下是用户的持仓信息，并始终参考它："
                + stocksOfCurrentGroup
                + "请严格基于这些信息来回答关于用户持仓的问题，不要提供你不知道的信息。";
        return systemInstruction;
    }

    /**
     * 发送消息并与 DeepSeek 模型交互。
     */
    private void sendMessage() {
        String userMessageText = inputField.getText().trim();
        if (userMessageText.isEmpty()) {
            return;
        }

        // 1. 清空输入框并禁用 UI
        inputField.clear();
        inputField.setDisable(true);
        sendButton.setDisable(true);

        // 2. 更新对话历史 UI (用户消息)
        chatHistoryArea.appendText("你: " + userMessageText + "\n");

        // 3. 在后台线程中调用 DeepSeek API
        new Thread(() -> {
            try {
                // AiServices 自动将用户消息添加到内存，然后调用模型，并保存模型回复。
                String aiResponse = assistant.chat(userMessageText);

                // 4. 回到 JavaFX UI 线程更新对话历史 (AI 响应)
                Platform.runLater(() -> {
                    chatHistoryArea.appendText("AI: " + aiResponse + "\n\n");
                });

            } catch (Exception e) {
                // 错误处理
                Platform.runLater(() -> {
                    chatHistoryArea.appendText("\n--- 错误: " + e.getMessage() + " ---\n\n");
                });
                e.printStackTrace();
            } finally {
                // 5. 重新启用 UI
                Platform.runLater(() -> {
                    inputField.setDisable(false);
                    sendButton.setDisable(false);
                    inputField.requestFocus();
                });
            }
        }).start();
    }

    /**
     * LangChain4j 声明式 AI 服务的接口。
     * 无需 @SystemMessage，因为我们已手动注入。
     */
    interface Assistant {
        String chat(String userMessage);
    }
}