package com.senkoje.presentation.util;

import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import javafx.concurrent.Task;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Bộ thực thi tác vụ nền dành cho giao diện JavaFX nhằm đẩy toàn bộ truy vấn SQLite và I/O
 * ra khỏi luồng giao diện chính (JavaFX Application Thread).
 */
public class BackgroundTaskExecutor implements AutoCloseable {

    private static final Logger LOGGER = LoggerFactory.getLogger(BackgroundTaskExecutor.class);
    private static final int DEFAULT_POOL_SIZE = 3;

    private final ExecutorService executorService;

    /**
     * Khởi tạo bộ thực thi nền với nhóm luồng daemon mặc định.
     */
    public BackgroundTaskExecutor() {
        AtomicInteger threadCounter = new AtomicInteger(1);
        this.executorService = Executors.newFixedThreadPool(DEFAULT_POOL_SIZE, runnable -> {
            Thread thread = new Thread(runnable, "senkoje-worker-" + threadCounter.getAndIncrement());
            thread.setDaemon(true);
            return thread;
        });
    }

    /**
     * Thực thi tác vụ nặng trên luồng nền bằng JavaFX Task và trả kết quả an toàn về luồng JavaFX.
     *
     * @param work tác vụ cần chạy ở nền
     * @param onSuccess hàm xử lý kết quả trên luồng JavaFX (có thể null)
     * @param onError hàm xử lý ngoại lệ trên luồng JavaFX (có thể null)
     * @param <T> kiểu dữ liệu kết quả
     * @return đối tượng JavaFX Task đang được thực thi
     */
    public <T> Task<T> runAsync(Callable<T> work, Consumer<T> onSuccess, Consumer<Throwable> onError) {
        Objects.requireNonNull(work, "work must not be null");
        Task<T> task = new Task<>() {
            @Override
            protected T call() throws Exception {
                return work.call();
            }
        };
        task.setOnSucceeded(event -> {
            if (onSuccess != null) {
                onSuccess.accept(task.getValue());
            }
        });
        task.setOnFailed(event -> {
            Throwable exception = task.getException();
            LOGGER.error("Background task failed", exception);
            if (onError != null) {
                onError.accept(exception);
            }
        });
        executorService.submit(task);
        return task;
    }

    /**
     * Thực thi một tác vụ không trả về giá trị trên luồng nền.
     *
     * @param work tác vụ nền
     * @param onSuccess hàm gọi lại khi hoàn tất trên luồng JavaFX (có thể null)
     * @param onError hàm xử lý lỗi trên luồng JavaFX (có thể null)
     * @return đối tượng JavaFX Task đang được thực thi
     */
    public Task<Void> runAsyncVoid(Runnable work, Runnable onSuccess, Consumer<Throwable> onError) {
        Objects.requireNonNull(work, "work must not be null");
        return runAsync(() -> {
            work.run();
            return null;
        }, ignored -> {
            if (onSuccess != null) {
                onSuccess.run();
            }
        }, onError);
    }

    /**
     * Thực thi trực tiếp một tác vụ không cần phản hồi về luồng JavaFX (ví dụ lưu cấu hình khi đóng cửa sổ).
     *
     * @param work tác vụ cần chạy ở nền
     */
    public void submitFireAndForget(Runnable work) {
        Objects.requireNonNull(work, "work must not be null");
        executorService.submit(() -> {
            try {
                work.run();
            } catch (RuntimeException exception) {
                LOGGER.error("Fire-and-forget background task failed", exception);
            }
        });
    }

    @Override
    public void close() {
        executorService.shutdownNow();
    }
}
