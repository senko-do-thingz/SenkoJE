package com.senkoje.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.senkoje.application.dto.TtsPreferences;
import com.senkoje.application.dto.WindowPreferences;
import com.senkoje.domain.model.AppSetting;
import com.senkoje.domain.repository.AppSettingRepository;
import com.senkoje.domain.service.DomainValidationException;
import com.senkoje.persistence.migration.DatabaseMigrator;
import com.senkoje.persistence.repository.SqliteAppSettingRepository;
import com.senkoje.persistence.sqlite.SqliteConnectionManager;
import java.nio.file.Path;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Kiểm thử đơn vị và tích hợp cho dịch vụ cấu hình ứng dụng cục bộ AppSettingsService.
 */
class AppSettingsServiceTest {

    @TempDir
    Path tempDir;

    private AppSettingsService settingsService;

    @BeforeEach
    void setUp() throws SQLException {
        SqliteConnectionManager manager = new SqliteConnectionManager(tempDir.resolve("app-settings.db"));
        new DatabaseMigrator(manager).migrate();
        Clock fixedClock = Clock.fixed(Instant.parse("2026-05-03T10:00:00Z"), ZoneOffset.UTC);
        settingsService = new AppSettingsService(new SqliteAppSettingRepository(manager), fixedClock);
    }

    @Test
    void shouldReturnSafeDefaultsIncludingDarkThemeBeforeAnySettingIsSaved() {
        assertEquals(AppSettingsService.THEME_DARK, settingsService.getTheme());
        assertEquals("vi", settingsService.getInterfaceLanguage());

        TtsPreferences tts = settingsService.getTtsPreferences();
        assertTrue(tts.enabled());
        assertEquals("system", tts.provider());
        assertEquals(1.0, tts.speechRate());

        WindowPreferences window = settingsService.getWindowPreferences();
        assertEquals(1200, window.width());
        assertEquals(800, window.height());
        assertFalse(window.maximized());
    }

    @Test
    void shouldPersistAndReadThemeLanguageTtsAndWindowPreferences() {
        settingsService.setTheme("LIGHT");
        assertEquals("light", settingsService.getTheme());

        settingsService.setInterfaceLanguage("EN");
        assertEquals("en", settingsService.getInterfaceLanguage());

        settingsService.saveTtsPreferences(new TtsPreferences(false, "windows-sapi", 1.25, "Haruka", "Zira"));
        TtsPreferences loadedTts = settingsService.getTtsPreferences();
        assertFalse(loadedTts.enabled());
        assertEquals("windows-sapi", loadedTts.provider());
        assertEquals(1.25, loadedTts.speechRate());
        assertEquals("Haruka", loadedTts.japaneseVoice());
        assertEquals("Zira", loadedTts.englishVoice());

        settingsService.saveWindowPreferences(new WindowPreferences(1440, 900, true));
        WindowPreferences loadedWindow = settingsService.getWindowPreferences();
        assertEquals(1440, loadedWindow.width());
        assertEquals(900, loadedWindow.height());
        assertTrue(loadedWindow.maximized());

        settingsService.setRawSetting("study.show_furigana", "true");
        assertEquals("true", settingsService.getRawSetting("study.show_furigana").orElseThrow());
    }

    @Test
    void shouldRejectInvalidThemeLanguageTtsAndWindowValues() {
        assertThrows(DomainValidationException.class, () -> settingsService.setTheme("solarized"));
        assertThrows(DomainValidationException.class, () -> settingsService.setInterfaceLanguage("fr"));
        assertThrows(DomainValidationException.class, () -> settingsService.saveTtsPreferences(
                new TtsPreferences(true, "system", 3.5, "", "")
        ));
        assertThrows(DomainValidationException.class, () -> settingsService.saveWindowPreferences(
                new WindowPreferences(320, 240, false)
        ));
    }

    @Test
    void shouldPersistTtsAndWindowPreferencesViaSingleSaveAllBatchCall() {
        AtomicInteger saveAllCalls = new AtomicInteger(0);
        AtomicInteger singleSaveCalls = new AtomicInteger(0);
        List<List<AppSetting>> recordedBatches = new ArrayList<>();

        AppSettingRepository recordingRepository = new AppSettingRepository() {
            @Override
            public Optional<AppSetting> findByKey(String settingKey) {
                return Optional.empty();
            }

            @Override
            public List<AppSetting> findAll() {
                return List.of();
            }

            @Override
            public AppSetting save(AppSetting setting) {
                singleSaveCalls.incrementAndGet();
                return setting;
            }

            @Override
            public void saveAll(List<AppSetting> settings) {
                saveAllCalls.incrementAndGet();
                recordedBatches.add(List.copyOf(settings));
            }

            @Override
            public boolean deleteByKey(String settingKey) {
                return false;
            }
        };

        Clock fixedClock = Clock.fixed(Instant.parse("2026-05-03T12:00:00Z"), ZoneOffset.UTC);
        AppSettingsService serviceWithSpy = new AppSettingsService(recordingRepository, fixedClock);

        serviceWithSpy.saveTtsPreferences(new TtsPreferences(true, "system", 1.1, "Haruka", "Zira"));
        serviceWithSpy.saveWindowPreferences(new WindowPreferences(1366, 768, false));

        // Xác minh mỗi thao tác lưu nhóm cấu hình chỉ gọi đúng một lần saveAll nguyên tử
        assertEquals(0, singleSaveCalls.get());
        assertEquals(2, saveAllCalls.get());
        assertEquals(5, recordedBatches.get(0).size());
        assertEquals(3, recordedBatches.get(1).size());
    }
}
