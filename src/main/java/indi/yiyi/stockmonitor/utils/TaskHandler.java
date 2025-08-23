package indi.yiyi.stockmonitor.utils;

import javafx.concurrent.Task;

import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * 用于新建一个线程执行一个操作，并对返回值做出响应
 *
 * @author Nonoas
 * @datetime 2022/1/8 15:02
 */
public class TaskHandler<T> {

    private static final ExecutorService THREAD_POOL = Executors.newCachedThreadPool();

    private Supplier<T> whenCall;
    private Consumer<T> andThen;
    private final Task<T> task;

    public TaskHandler() {
        this.task = new Task<>() {
            @Override
            protected T call() throws Exception {
                return Optional.ofNullable(whenCall)
                        .orElseThrow(() -> new IllegalStateException("whenCall 未设置"))
                        .get();
            }
        };

        task.valueProperty().addListener((observable, oldValue, newValue) -> {
            if (andThen != null) {
                andThen.accept(newValue);
            }
        });
    }

    /**
     * 设置任务，子线程执行
     */
    public TaskHandler<T> whenCall(Supplier<T> supplier) {
        this.whenCall = supplier;
        return this;
    }

    /**
     * 设置回调，UI线程执行
     */
    public TaskHandler<T> andThen(Consumer<T> consumer) {
        this.andThen = consumer;
        return this;
    }

    public void handle() {
        if (whenCall == null) {
            throw new IllegalStateException("未调用 whenCall 指定执行任务");
        }
        THREAD_POOL.execute(task);
    }

    /**
     * 简单后台任务
     */
    public static void backRun(Runnable run) {
        THREAD_POOL.execute(run);
    }
}