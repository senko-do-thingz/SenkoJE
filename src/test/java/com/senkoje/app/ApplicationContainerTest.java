package com.senkoje.app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.senkoje.infrastructure.config.AppDataPathResolver;
import com.senkoje.presentation.navigation.NavigationSection;
import java.nio.file.Path;
import java.sql.SQLException;
import java.time.Clock;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Kiểm thử khởi tạo ApplicationContainer với múi giờ Việt Nam (UTC+7) và các mục điều hướng chính.
 */
class ApplicationContainerTest {

    @Test
    void shouldWireServicesWithVietnamTimezoneAndDefaultDarkTheme(@TempDir Path tempDir) throws SQLException {
        // Kiểm tra bộ chứa ứng dụng khởi tạo SQLite, Flyway, các dịch vụ và múi giờ Asia/Ho_Chi_Minh
        Path dbPath = tempDir.resolve("container-test.db");
        try (ApplicationContainer container = ApplicationContainer.createForDatabase(
                dbPath,
                new AppDataPathResolver(),
                Clock.system(ApplicationContainer.DEFAULT_TIMEZONE)
        )) {
            assertEquals(ZoneId.of("Asia/Ho_Chi_Minh"), container.getClock().getZone());
            assertNotNull(container.getDeckService());
            assertNotNull(container.getCardService());
            assertNotNull(container.getAppSettingsService());
            assertNotNull(container.getReviewService());
            assertNotNull(container.getLocalizationManager());
            assertNotNull(container.getThemeManager());
            assertNotNull(container.getBackgroundTaskExecutor());
            assertNotNull(container.getJapaneseTokenizerService());
            assertNotNull(container.getSqliteBackupService());
            assertNotNull(container.getDeckJsonExporter());
            assertNotNull(container.getVocabularyCsvExporter());
            assertNotNull(container.getDeckJsonImporter());
            assertNotNull(container.getVocabularyCsvImporter());
            assertNotNull(container.getAtomicDeckImporter());
            assertFalse(container.getJapaneseTokenizerService().isLoaded(),
                    "Kuromoji dictionary must remain lazy-loaded at startup");
            assertTrue(container.getThemeManager().isDarkTheme());
            assertEquals("vi", container.getLocalizationManager().getCurrentLanguage());
        }
    }

    @Test
    void shouldDefineAllSevenNavigationSectionsInSpecificationOrder() {
        // Xác minh thứ tự 7 mục điều hướng trên thanh bên đúng theo đặc tả FEATURES_AND_UI.md
        NavigationSection[] sections = NavigationSection.values();
        assertEquals(7, sections.length);
        assertEquals(NavigationSection.DASHBOARD, sections[0]);
        assertEquals(NavigationSection.DECKS, sections[1]);
        assertEquals(NavigationSection.STUDY, sections[2]);
        assertEquals(NavigationSection.READING_SANDBOX, sections[3]);
        assertEquals(NavigationSection.CARD_BROWSER, sections[4]);
        assertEquals(NavigationSection.IMPORT_EXPORT, sections[5]);
        assertEquals(NavigationSection.SETTINGS, sections[6]);
    }
}
