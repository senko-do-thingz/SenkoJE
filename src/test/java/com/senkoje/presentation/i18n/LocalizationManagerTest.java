package com.senkoje.presentation.i18n;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.senkoje.application.service.AppSettingsService;
import com.senkoje.persistence.migration.DatabaseMigrator;
import com.senkoje.persistence.repository.SqliteAppSettingRepository;
import com.senkoje.persistence.sqlite.SqliteConnectionManager;
import com.senkoje.presentation.theme.TypographyConfig;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.ResourceBundle;
import java.util.Set;
import javafx.beans.binding.StringBinding;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Kiểm thử đơn vị cho LocalizationManager và sự đồng bộ khóa giữa gói ngôn ngữ tiếng Việt và tiếng Anh.
 */
class LocalizationManagerTest {

    @Test
    void shouldSwitchLanguageDynamicallyAndUpdateBindings() {
        // Kiểm tra StringBinding tự cập nhật trực tiếp khi chuyển đổi giữa tiếng Việt và tiếng Anh
        LocalizationManager manager = new LocalizationManager("vi");
        StringBinding dashboardBinding = manager.createStringBinding("nav.dashboard");

        assertEquals("Tổng quan", dashboardBinding.get());
        assertEquals("vi", manager.getCurrentLanguage());

        manager.setLanguage("en");
        assertEquals("Dashboard", dashboardBinding.get());
        assertEquals("en", manager.getCurrentLanguage());

        manager.setLanguage("vi");
        assertEquals("Tổng quan", dashboardBinding.get());
    }

    @Test
    void shouldPersistLanguageSelectionViaAppSettingsService(@TempDir Path tempDir) throws SQLException {
        // Xác minh lựa chọn ngôn ngữ được lưu bền vững vào SQLite app_settings
        SqliteConnectionManager connectionManager = new SqliteConnectionManager(tempDir.resolve("i18n.db"));
        new DatabaseMigrator(connectionManager).migrate();
        AppSettingsService settingsService = new AppSettingsService(new SqliteAppSettingRepository(connectionManager));

        LocalizationManager firstManager = new LocalizationManager(settingsService);
        assertEquals("vi", firstManager.getCurrentLanguage());

        firstManager.setLanguage("en");

        LocalizationManager reloadedManager = new LocalizationManager(settingsService);
        assertEquals("en", reloadedManager.getCurrentLanguage());
        assertEquals("Settings", reloadedManager.get("nav.settings"));
    }

    @Test
    void shouldMaintainExactKeyParityBetweenVietnameseAndEnglishBundles() {
        // Đảm bảo hai tệp messages_vi.properties và messages_en.properties có cùng tập khóa và không chứa gạch ngang dài
        ResourceBundle viBundle = ResourceBundle.getBundle(
                LocalizationManager.BUNDLE_BASE_NAME, LocalizationManager.LOCALE_VI);
        ResourceBundle enBundle = ResourceBundle.getBundle(
                LocalizationManager.BUNDLE_BASE_NAME, LocalizationManager.LOCALE_EN);

        Set<String> viKeys = viBundle.keySet();
        Set<String> enKeys = enBundle.keySet();
        assertEquals(enKeys, viKeys, "Vietnamese and English bundles must contain identical keys");

        for (String key : enKeys) {
            String viValue = viBundle.getString(key);
            String enValue = enBundle.getString(key);
            assertFalse(viValue.isBlank(), "Vietnamese translation must not be blank for key: " + key);
            assertFalse(enValue.isBlank(), "English translation must not be blank for key: " + key);
            assertFalse(viValue.contains("\u2013") || viValue.contains("\u2014"));
            assertFalse(enValue.contains("\u2013") || enValue.contains("\u2014"));
        }
    }

    @Test
    void shouldSupportVietnameseDiacriticsKanjiKanaRomajiAndIpaInTypographySample() {
        // Xác minh chuỗi mẫu Unicode bao phủ đủ tiếng Việt có dấu, Kanji, Hiragana, Katakana, Romaji và IPA
        String sample = TypographyConfig.UNICODE_VERIFICATION_SAMPLE;
        assertTrue(sample.contains("Tiếng Việt"));
        assertTrue(sample.contains("漢字"));
        assertTrue(sample.contains("ひらがな"));
        assertTrue(sample.contains("カタカナ"));
        assertTrue(sample.contains("Rōmaji"));
        assertTrue(sample.contains("/ˈsɛn.koʊ/"));
    }
}
