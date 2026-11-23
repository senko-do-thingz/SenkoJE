package com.senkoje.infrastructure.tts;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.senkoje.domain.model.TargetLanguage;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Kiểm thử độc lập cho SystemTtsProvider sử dụng ProcessRunner giả lập
 * trên Windows, macOS và Linux mà không cần thiết bị âm thanh thật trong CI.
 */
class SystemTtsProviderTest {

    @Test
    void shouldPassCardTextSafelyViaEnvironmentVariablesOnWindows() {
        RecordingProcessRunner runner = new RecordingProcessRunner(
                (cmd, env) -> new ProcessExecutionResult(0, "2", "", false, false));
        SystemTtsProvider provider = new SystemTtsProvider("Windows 11", runner);

        assertTrue(provider.checkAvailability().available());

        String dangerousText = "食べる\"; Remove-Item -Recurse C:\\; $(whoami)";
        TtsPlaybackResult result = provider.speak(
                new TtsPlaybackRequest(dangerousText, TargetLanguage.JA, 1.5, "Microsoft Haruka Desktop"));

        assertTrue(result.succeeded());
        assertEquals("tts.status.played", result.statusCode());
        assertTrue(runner.cancelCount >= 1);

        RecordedCall speakCall = runner.calls.getLast();
        assertEquals("powershell", speakCall.command().getFirst());
        // Xác minh nội dung thẻ không bao giờ bị nối trực tiếp vào chuỗi lệnh PowerShell
        assertFalse(speakCall.command().stream().anyMatch(arg -> arg.contains("Remove-Item")));
        assertEquals(dangerousText, speakCall.environment().get("SENKOJE_TTS_TEXT"));
        assertEquals("Microsoft Haruka Desktop", speakCall.environment().get("SENKOJE_TTS_VOICE"));
        assertEquals("ja", speakCall.environment().get("SENKOJE_TTS_LANG"));
        assertEquals("3", speakCall.environment().get("SENKOJE_TTS_RATE"));
    }

    @Test
    void shouldInvokeMacSayWithArgumentListAndParseVoices() {
        RecordingProcessRunner runner = new RecordingProcessRunner((cmd, env) -> {
            if (cmd.size() >= 3 && "say".equals(cmd.getFirst()) && "?".equals(cmd.get(2))) {
                return new ProcessExecutionResult(
                        0,
                        "Kyoko               ja_JP    # こんにちは\nSamantha            en_US    # Hello",
                        "",
                        false,
                        false
                );
            }
            return new ProcessExecutionResult(0, "", "", false, false);
        });
        SystemTtsProvider provider = new SystemTtsProvider("Mac OS X", runner);

        assertTrue(provider.checkAvailability().available());
        List<TtsVoice> jaVoices = provider.listVoices(TargetLanguage.JA);
        assertEquals(1, jaVoices.size());
        assertEquals("Kyoko", jaVoices.getFirst().id());

        TtsPlaybackResult result = provider.speak(new TtsPlaybackRequest("ありがとう", TargetLanguage.JA, 1.0, "Kyoko"));
        assertTrue(result.succeeded());
        RecordedCall speakCall = runner.calls.getLast();
        assertEquals(List.of("say", "-r", "180", "-v", "Kyoko", "--", "ありがとう"), speakCall.command());
    }

    @Test
    void shouldFallbackFromEspeakNgToSpdSayOnLinuxAndHandleUnavailableEngine() {
        RecordingProcessRunner fallbackRunner = new RecordingProcessRunner((cmd, env) -> {
            if ("espeak-ng".equals(cmd.getFirst())) {
                return new ProcessExecutionResult(-1, "", "not found", false, true);
            }
            return new ProcessExecutionResult(0, "spd-say 0.11", "", false, false);
        });
        SystemTtsProvider linuxProvider = new SystemTtsProvider("Linux", fallbackRunner);

        TtsAvailability availability = linuxProvider.checkAvailability();
        assertTrue(availability.available());
        assertEquals("Linux spd-say", availability.detailMessage());

        TtsPlaybackResult played = linuxProvider.speak(new TtsPlaybackRequest("hello", TargetLanguage.EN, 1.2, ""));
        assertTrue(played.succeeded());
        assertEquals("spd-say", fallbackRunner.calls.getLast().command().getFirst());

        // Khi cả espeak-ng và spd-say đều không tồn tại phải báo không khả dụng rõ ràng
        RecordingProcessRunner missingRunner = new RecordingProcessRunner(
                (cmd, env) -> new ProcessExecutionResult(-1, "", "not found", false, true));
        SystemTtsProvider unavailableProvider = new SystemTtsProvider("Linux", missingRunner);
        assertFalse(unavailableProvider.checkAvailability().available());
        TtsPlaybackResult unavailableResult = unavailableProvider.speak(
                new TtsPlaybackRequest("hello", TargetLanguage.EN, 1.0, ""));
        assertFalse(unavailableResult.succeeded());
        assertEquals("tts.status.systemUnavailable", unavailableResult.statusCode());
    }

    @Test
    void shouldReportTimeoutAndCancelOnStop() {
        RecordingProcessRunner timeoutRunner = new RecordingProcessRunner(
                (cmd, env) -> new ProcessExecutionResult(-1, "", "Process timed out", true, false));
        SystemTtsProvider provider = new SystemTtsProvider("Windows 11", timeoutRunner);

        TtsPlaybackResult result = provider.speak(new TtsPlaybackRequest("test", TargetLanguage.EN, 1.0, ""));
        assertFalse(result.succeeded());
        assertEquals("tts.status.timeout", result.statusCode());

        int beforeStop = timeoutRunner.cancelCount;
        provider.stop();
        assertEquals(beforeStop + 1, timeoutRunner.cancelCount);

        RecordingProcessRunner cancelledRunner = new RecordingProcessRunner(
                (cmd, env) -> new ProcessExecutionResult(-1, "", "Process cancelled", false, false, true));
        SystemTtsProvider cancelledProvider = new SystemTtsProvider("Windows 11", cancelledRunner);
        TtsPlaybackResult cancelledResult = cancelledProvider.speak(
                new TtsPlaybackRequest("test", TargetLanguage.EN, 1.0, ""));
        assertFalse(cancelledResult.succeeded());
        assertTrue(cancelledResult.cancelled());
        assertEquals("tts.status.cancelled", cancelledResult.statusCode());
    }

    private record RecordedCall(List<String> command, Map<String, String> environment, Duration timeout) {
    }

    private interface FakeHandler {
        ProcessExecutionResult handle(List<String> command, Map<String, String> environment);
    }

    private static final class RecordingProcessRunner implements ProcessRunner {
        private final FakeHandler handler;
        private final List<RecordedCall> calls = new ArrayList<>();
        private int cancelCount = 0;

        private RecordingProcessRunner(FakeHandler handler) {
            this.handler = handler;
        }

        @Override
        public ProcessExecutionResult run(List<String> command, Map<String, String> environment, Duration timeout) {
            calls.add(new RecordedCall(List.copyOf(command), Map.copyOf(environment), timeout));
            return handler.handle(command, environment);
        }

        @Override
        public void cancelActive() {
            cancelCount++;
        }
    }
}
