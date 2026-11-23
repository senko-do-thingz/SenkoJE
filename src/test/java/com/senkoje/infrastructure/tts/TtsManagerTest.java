package com.senkoje.infrastructure.tts;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.senkoje.application.dto.TtsPreferences;
import com.senkoje.application.service.AppSettingsService;
import com.senkoje.domain.model.TargetLanguage;
import com.senkoje.persistence.migration.DatabaseMigrator;
import com.senkoje.persistence.repository.SqliteAppSettingRepository;
import com.senkoje.persistence.sqlite.SqliteConnectionManager;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Kiểm thử điều phối TtsManager và UnsupportedTtsProvider theo cấu hình AppSettingsService.
 */
class TtsManagerTest {

    @Test
    void shouldSelectConfiguredProviderAndFallbackToUnsupportedWhenDisabled(@TempDir Path tempDir) throws Exception {
        SqliteConnectionManager connectionManager = new SqliteConnectionManager(tempDir.resolve("tts-manager.db"));
        new DatabaseMigrator(connectionManager).migrate();
        AppSettingsService settingsService = new AppSettingsService(new SqliteAppSettingRepository(connectionManager));

        TtsService fakeSystem = new StubTtsService(SystemTtsProvider.PROVIDER_ID, true);
        TtsService fakeBundled = new StubTtsService(BundledOfflineTtsProvider.PROVIDER_ID, false);
        UnsupportedTtsProvider unsupported = new UnsupportedTtsProvider();
        TtsManager manager = new TtsManager(settingsService, fakeSystem, fakeBundled, unsupported);

        // Mặc định enabled=true, provider=system
        assertEquals(SystemTtsProvider.PROVIDER_ID, manager.resolveActiveProvider().getProviderId());
        assertTrue(manager.checkActiveAvailability().available());
        assertTrue(manager.speak("hello", TargetLanguage.EN).succeeded());

        // Chuyển sang bundled
        settingsService.saveTtsPreferences(new TtsPreferences(true, "bundled", 1.1, "", ""));
        assertEquals(BundledOfflineTtsProvider.PROVIDER_ID, manager.resolveActiveProvider().getProviderId());
        assertFalse(manager.checkActiveAvailability().available());

        // Tắt TTS -> luôn rơi về UnsupportedTtsProvider an toàn
        settingsService.saveTtsPreferences(new TtsPreferences(false, "system", 1.0, "", ""));
        assertEquals(UnsupportedTtsProvider.PROVIDER_ID, manager.resolveActiveProvider().getProviderId());
        TtsPlaybackResult disabledResult = manager.speak("hello", TargetLanguage.EN);
        assertFalse(disabledResult.succeeded());
        assertEquals("tts.status.disabled", disabledResult.statusCode());
    }

    private record StubTtsService(String providerId, boolean available) implements TtsService {
        @Override
        public String getProviderId() {
            return providerId;
        }

        @Override
        public TtsAvailability checkAvailability() {
            return new TtsAvailability(
                    available, providerId, available ? "tts.status.ready" : "tts.status.bundledMissing", "");
        }

        @Override
        public List<TtsVoice> listVoices(TargetLanguage language) {
            return List.of();
        }

        @Override
        public TtsPlaybackResult speak(TtsPlaybackRequest request) {
            return new TtsPlaybackResult(
                    available, providerId, available ? "tts.status.played" : "tts.status.bundledMissing", "");
        }

        @Override
        public void stop() {
        }
    }
}
