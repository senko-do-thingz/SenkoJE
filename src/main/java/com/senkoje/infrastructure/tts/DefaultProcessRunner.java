package com.senkoje.infrastructure.tts;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Triển khai mặc định của ProcessRunner sử dụng ProcessBuilder với danh sách tham số an toàn,
 * đọc đồng thời stdout/stderr trên luồng ảo để tránh deadlock đường ống,
 * hỗ trợ giới hạn thời gian chờ và phân biệt rõ tiến trình bị hủy chủ động với lỗi thực thi.
 */
public class DefaultProcessRunner implements ProcessRunner {

    private final AtomicReference<ActiveExecution> activeExecution = new AtomicReference<>();

    @Override
    public ProcessExecutionResult run(List<String> command, Map<String, String> environment, Duration timeout) {
        Objects.requireNonNull(command, "command must not be null");
        if (command.isEmpty()) {
            throw new IllegalArgumentException("command must not be empty");
        }
        Duration effectiveTimeout = timeout == null || timeout.isNegative() || timeout.isZero()
                ? Duration.ofSeconds(15)
                : timeout;

        ProcessBuilder builder = new ProcessBuilder(command);
        if (environment != null && !environment.isEmpty()) {
            builder.environment().putAll(environment);
        }

        ActiveExecution current = null;
        try {
            Process process = builder.start();
            AtomicBoolean cancelledFlag = new AtomicBoolean(false);
            current = new ActiveExecution(process, cancelledFlag);
            activeExecution.set(current);

            // Rút đồng thời cả stdout và stderr ngay sau khi khởi chạy để tránh đầy bộ đệm đường ống gây deadlock
            CompletableFuture<String> stdoutFuture = readStreamAsync(process.getInputStream());
            CompletableFuture<String> stderrFuture = readStreamAsync(process.getErrorStream());

            boolean finished = process.waitFor(effectiveTimeout.toMillis(), TimeUnit.MILLISECONDS);
            if (!finished) {
                process.destroyForcibly();
                process.waitFor(2, TimeUnit.SECONDS);
                if (cancelledFlag.get()) {
                    return new ProcessExecutionResult(-1, "", "Process cancelled", false, false, true);
                }
                return new ProcessExecutionResult(-1, "", "Process timed out", true, false, false);
            }

            if (cancelledFlag.get()) {
                return new ProcessExecutionResult(-1, "", "Process cancelled", false, false, true);
            }

            String stdout = joinStream(stdoutFuture);
            String stderr = joinStream(stderrFuture);
            return new ProcessExecutionResult(process.exitValue(), stdout, stderr, false, false, false);
        } catch (IOException exception) {
            return new ProcessExecutionResult(-1, "", exception.getMessage(), false, true, false);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            if (current != null) {
                current.process().destroyForcibly();
            }
            boolean wasCancelled = current != null && current.cancelled().get();
            return new ProcessExecutionResult(-1, "", "Interrupted", false, false, wasCancelled);
        } finally {
            if (current != null) {
                activeExecution.compareAndSet(current, null);
            }
        }
    }

    @Override
    public void cancelActive() {
        ActiveExecution running = activeExecution.getAndSet(null);
        if (running != null) {
            // Đánh dấu cờ hủy trước khi ép dừng tiến trình để hàm run() trả về kết quả cancelled chính xác
            running.cancelled().set(true);
            if (running.process().isAlive()) {
                running.process().destroyForcibly();
            }
        }
    }

    private static CompletableFuture<String> readStreamAsync(InputStream stream) {
        CompletableFuture<String> future = new CompletableFuture<>();
        Thread.startVirtualThread(() -> {
            try (InputStream in = stream) {
                future.complete(new String(in.readAllBytes(), StandardCharsets.UTF_8).trim());
            } catch (IOException exception) {
                future.complete("");
            }
        });
        return future;
    }

    private static String joinStream(CompletableFuture<String> future) {
        try {
            return future.get(2, TimeUnit.SECONDS);
        } catch (Exception exception) {
            return "";
        }
    }

    private record ActiveExecution(Process process, AtomicBoolean cancelled) {
    }
}
