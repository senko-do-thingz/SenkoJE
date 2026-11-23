package com.senkoje.infrastructure.tts;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Triển khai mặc định của ProcessRunner sử dụng ProcessBuilder với danh sách tham số an toàn,
 * hỗ trợ giới hạn thời gian chờ và hủy tiến trình đang chạy.
 */
public class DefaultProcessRunner implements ProcessRunner {

    private final AtomicReference<Process> activeProcess = new AtomicReference<>();

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

        Process process = null;
        try {
            process = builder.start();
            activeProcess.set(process);

            boolean finished = process.waitFor(effectiveTimeout.toMillis(), TimeUnit.MILLISECONDS);
            if (!finished) {
                process.destroyForcibly();
                process.waitFor(2, TimeUnit.SECONDS);
                return new ProcessExecutionResult(-1, "", "Process timed out", true, false);
            }

            String stdout = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
            String stderr = new String(process.getErrorStream().readAllBytes(), StandardCharsets.UTF_8).trim();
            return new ProcessExecutionResult(process.exitValue(), stdout, stderr, false, false);
        } catch (IOException exception) {
            return new ProcessExecutionResult(-1, "", exception.getMessage(), false, true);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            if (process != null) {
                process.destroyForcibly();
            }
            return new ProcessExecutionResult(-1, "", "Interrupted", false, false);
        } finally {
            activeProcess.compareAndSet(process, null);
        }
    }

    @Override
    public void cancelActive() {
        Process running = activeProcess.getAndSet(null);
        if (running != null && running.isAlive()) {
            running.destroyForcibly();
        }
    }
}
