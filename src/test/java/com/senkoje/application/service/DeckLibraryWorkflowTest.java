package com.senkoje.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.senkoje.app.ApplicationContainer;
import com.senkoje.domain.model.Deck;
import com.senkoje.domain.model.DeckSettings;
import com.senkoje.domain.model.DefinitionLanguage;
import com.senkoje.domain.model.ProficiencyLevel;
import com.senkoje.domain.model.TargetLanguage;
import com.senkoje.infrastructure.config.AppDataPathResolver;
import java.nio.file.Path;
import java.sql.SQLException;
import java.time.Clock;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Kiểm thử bổ sung cho thao tác cập nhật toàn bộ bộ thẻ và cài đặt FSRS-6 phục vụ màn hình Thư viện bộ thẻ.
 */
class DeckLibraryWorkflowTest {

    @Test
    void shouldUpdateDeckFieldsAndFsrsSettings(@TempDir Path tempDir) throws SQLException {
        // Kiểm tra tạo bộ thẻ, cập nhật toàn bộ trường và cập nhật cấu hình FSRS-6
        try (ApplicationContainer container = ApplicationContainer.createForDatabase(
                tempDir.resolve("deck-library.db"),
                new AppDataPathResolver(),
                Clock.system(ApplicationContainer.DEFAULT_TIMEZONE)
        )) {
            DeckService deckService = container.getDeckService();
            Deck created = deckService.createDeck(
                    "JLPT N5", TargetLanguage.JA, DefinitionLanguage.VI, ProficiencyLevel.JLPT_N5, "Initial");

            Deck updated = deckService.updateDeck(
                    created.id(),
                    "Business English",
                    TargetLanguage.EN,
                    DefinitionLanguage.EN,
                    ProficiencyLevel.of("Business"),
                    "Updated description"
            );

            assertEquals("Business English", updated.title());
            assertEquals(TargetLanguage.EN, updated.targetLanguage());
            assertEquals(DefinitionLanguage.EN, updated.definitionLanguage());
            assertEquals("Business", updated.level().code());
            assertEquals("Updated description", updated.description());

            DeckSettings customSettings = new DeckSettings(
                    created.id(), 0.92, 1800, 15, 150, "1m,10m,1h", "15m", false);
            DeckSettings saved = deckService.updateDeckSettings(customSettings);
            assertEquals(0.92, saved.desiredRetention());
            assertEquals(1800, saved.maximumInterval());
            assertEquals("1m,10m,1h", saved.learningSteps());
            assertEquals(false, saved.enableFuzzing());
        }
    }
}
