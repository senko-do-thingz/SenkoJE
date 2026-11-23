package com.senkoje.infrastructure.tts;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.senkoje.domain.model.TargetLanguage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Kiểm thử độc lập cho BundledOfflineTtsProvider xác minh phát hiện gói giọng đọc cục bộ
 * và báo cáo trạng thái không khả dụng sạch sẽ khi chưa cài đặt tài nguyên giọng đọc.
 */
class BundledOfflineTtsProviderTest {

    @Test
    void shouldReportUnavailableCleanlyWhenVoicesDirectoryIsEmpty(@TempDir Path tempDir) {
        BundledOfflineTtsProvider provider = new BundledOfflineTtsProvider(tempDir.resolve("missing-voices"));

        TtsAvailability availability = provider.checkAvailability();
        assertFalse(availability.available());
        assertEquals("tts.status.bundledMissing", availability.reasonCode());
        assertTrue(provider.listVoices(TargetLanguage.JA).isEmpty());

        TtsPlaybackResult result = provider.speak(new TtsPlaybackRequest("猫", TargetLanguage.JA, 1.0, ""));
        assertFalse(result.succeeded());
        assertEquals("tts.status.bundledMissing", result.statusCode());
    }

    @Test
    void shouldDetectBundledVoiceManifestsWhenPresent(@TempDir Path tempDir) throws Exception {
        Path voicesDir = tempDir.resolve("voices");
        Files.createDirectories(voicesDir);
        Files.writeString(
                voicesDir.resolve("ja-kokoro.voice.properties"),
                "id=ja-kokoro-v1\nname=Kokoro Japanese Offline\nlanguage=JA\n"
        );
        Files.writeString(
                voicesDir.resolve("en-amy.voice.properties"),
                "id=en-amy-v1\nname=Amy English Offline\nlanguage=EN\n"
        );

        BundledOfflineTtsProvider provider = new BundledOfflineTtsProvider(voicesDir);

        TtsAvailability availability = provider.checkAvailability();
        assertTrue(availability.available());
        assertEquals("tts.status.bundledReady", availability.reasonCode());

        List<TtsVoice> jaVoices = provider.listVoices(TargetLanguage.JA);
        assertEquals(1, jaVoices.size());
        assertEquals("ja-kokoro-v1", jaVoices.getFirst().id());

        List<TtsVoice> enVoices = provider.listVoices(TargetLanguage.EN);
        assertEquals(1, enVoices.size());
        assertEquals("en-amy-v1", enVoices.getFirst().id());
    }
}
