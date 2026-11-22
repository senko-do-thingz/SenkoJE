package com.senkoje.presentation.theme;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.senkoje.application.service.AppSettingsService;
import com.senkoje.persistence.migration.DatabaseMigrator;
import com.senkoje.persistence.repository.SqliteAppSettingRepository;
import com.senkoje.persistence.sqlite.SqliteConnectionManager;
import java.nio.file.Path;
import java.sql.SQLException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Kiểm thử đơn vị cho ThemeManager và tài nguyên CSS chế độ tối/sáng.
 */
class ThemeManagerTest {

    @Test
    void shouldDefaultToDarkThemeAndPersistThemeChanges(@TempDir Path tempDir) throws SQLException {
        // Kiểm tra mặc định là Chế độ tối và lưu trữ bền vững khi chuyển sang Chế độ sáng
        SqliteConnectionManager connectionManager = new SqliteConnectionManager(tempDir.resolve("theme.db"));
        new DatabaseMigrator(connectionManager).migrate();
        AppSettingsService settingsService = new AppSettingsService(new SqliteAppSettingRepository(connectionManager));

        ThemeManager themeManager = new ThemeManager(settingsService);
        assertEquals(AppSettingsService.THEME_DARK, themeManager.getCurrentTheme());
        assertTrue(themeManager.isDarkTheme());

        themeManager.setTheme(AppSettingsService.THEME_LIGHT);
        assertEquals(AppSettingsService.THEME_LIGHT, themeManager.getCurrentTheme());
        assertFalse(themeManager.isDarkTheme());

        ThemeManager reloadedManager = new ThemeManager(settingsService);
        assertEquals(AppSettingsService.THEME_LIGHT, reloadedManager.getCurrentTheme());
    }

    @Test
    void shouldResolveBothDarkAndLightThemeStylesheetsOnClasspath() {
        // Đảm bảo cả hai tệp định kiểu dark-theme.css và light-theme.css đều tồn tại trong classpath
        assertNotNull(ThemeManager.class.getResource(ThemeManager.DARK_THEME_STYLESHEET));
        assertNotNull(ThemeManager.class.getResource(ThemeManager.LIGHT_THEME_STYLESHEET));
        assertEquals(ThemeManager.DARK_THEME_STYLESHEET, ThemeManager.resolveStylesheetPath("dark"));
        assertEquals(ThemeManager.LIGHT_THEME_STYLESHEET, ThemeManager.resolveStylesheetPath("light"));
        assertEquals(ThemeManager.DARK_THEME_STYLESHEET, ThemeManager.resolveStylesheetPath("unknown"));
    }
}
