package com.senkoje.infrastructure.tts;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Kiểm thử cho DefaultProcessRunner xác minh:
 * 1) Tiến trình xuất lượng lớn stdout/stderr không bị treo deadlock đường ống.
 * 2) Hủy tiến trình qua cancelActive() trả về kết quả cancelled riêng biệt với lỗi thất bại.
 */
class DefaultProcessRunnerTest {

    @Test
    void shouldDrainLargeOutputWithoutDeadlock(@TempDir Path tempDir) throws Exception {
        Path script = tempDir.resolve("LargeOutput.java");
        Files.writeString(script, """
                public class LargeOutput {
                    public static void main(String[] args) {
                        String line = "0123456789abcdef".repeat(64);
                        for (int i = 0; i < 300; i++) {
                            System.out.println(line);
                            System.err.println(line);
                        }
                    }
                }
                """);

        DefaultProcessRunner runner = new DefaultProcessRunner();
        ProcessExecutionResult result = runner.run(
                List.of(javaExecutable(), script.toString()),
                Map.of(),
                Duration.ofSeconds(15)
        );

        assertTrue(result.isSuccess(), "Expected large output process to succeed without pipe deadlock");
        assertFalse(result.timedOut());
        assertFalse(result.cancelled());
        assertTrue(result.stdout().length() > 100_000);
        assertTrue(result.stderr().length() > 100_000);
    }

    @Test
    void shouldReturnDistinctCancelledResultWhenStoppedViaCancelActive(@TempDir Path tempDir) throws Exception {
        Path script = tempDir.resolve("SleepShort.java");
        Files.writeString(script, """
                public class SleepShort {
                    public static void main(String[] args) throws Exception {
                        Thread.sleep(10_000);
                    }
                }
                """);

        DefaultProcessRunner runner = new DefaultProcessRunner();
        CompletableFuture<ProcessExecutionResult> future = CompletableFuture.supplyAsync(() ->
                runner.run(List.of(javaExecutable(), script.toString()), Map.of(), Duration.ofSeconds(15))
        );

        Thread.sleep(500);
        runner.cancelActive();

        ProcessExecutionResult result = future.get(10, TimeUnit.SECONDS);
        assertFalse(result.isSuccess());
        assertTrue(result.cancelled(), "Expected cancelled flag to be true when stopped via cancelActive()");
        assertFalse(result.timedOut());
    }

    private static String javaExecutable() {
        String javaHome = System.getProperty("java.home");
        String os = System.getProperty("os.name", "").toLowerCase();
        String binary = os.contains("win") ? "java.exe" : "java";
        return Path.of(javaHome, "bin", binary).toString();
    }
}
