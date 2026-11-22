package com.senkoje.domain.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.senkoje.domain.model.Deck;
import com.senkoje.domain.model.DefinitionLanguage;
import com.senkoje.domain.model.ProficiencyLevel;
import com.senkoje.domain.model.TargetLanguage;
import java.time.Instant;
import org.junit.jupiter.api.Test;

/**
 * Kiểm thử đơn vị cho bộ kiểm tra hợp lệ thực thể miền Deck.
 */
class DeckValidatorTest {

    private final DeckValidator validator = new DeckValidator();

    @Test
    void shouldAcceptValidDeckWithStandardAndCustomLevels() {
        Instant now = Instant.parse("2026-01-01T00:00:00Z");
        Deck jlptDeck = new Deck(
                null,
                "JLPT N5 Core Vocabulary",
                TargetLanguage.JA,
                DefinitionLanguage.VI,
                ProficiencyLevel.JLPT_N5,
                "Essential N5 words",
                now,
                now
        );
        Deck customDeck = new Deck(
                2L,
                "Business Email Phrases",
                TargetLanguage.EN,
                DefinitionLanguage.EN,
                ProficiencyLevel.of("Business-Intermediate"),
                null,
                now,
                now
        );

        assertDoesNotThrow(() -> validator.validate(jlptDeck));
        assertDoesNotThrow(() -> validator.validate(customDeck));
        assertTrue(ProficiencyLevel.JLPT_N5.isJlpt());
        assertTrue(ProficiencyLevel.CEFR_B2.isCefr());
        assertTrue(customDeck.level().isCustom());
    }

    @Test
    void shouldRejectNullOrInvalidDeckFields() {
        assertThrows(DomainValidationException.class, () -> validator.validate(null));

        Instant now = Instant.parse("2026-01-01T00:00:00Z");
        Deck invalidDeck = new Deck(
                null,
                "   ",
                null,
                null,
                null,
                "x".repeat(DeckValidator.MAX_DESCRIPTION_LENGTH + 1),
                now,
                now
        );

        DomainValidationException exception = assertThrows(
                DomainValidationException.class,
                () -> validator.validate(invalidDeck)
        );
        assertFalse(exception.getErrors().isEmpty());
        assertTrue(exception.getErrors().size() >= 4);
    }

    @Test
    void shouldRejectOverlyLongDeckTitleAndLevel() {
        Instant now = Instant.parse("2026-01-01T00:00:00Z");
        Deck invalidDeck = new Deck(
                null,
                "a".repeat(DeckValidator.MAX_TITLE_LENGTH + 1),
                TargetLanguage.JA,
                DefinitionLanguage.VI,
                ProficiencyLevel.of("L".repeat(DeckValidator.MAX_LEVEL_LENGTH + 1)),
                null,
                now,
                now
        );

        DomainValidationException exception = assertThrows(
                DomainValidationException.class,
                () -> validator.validate(invalidDeck)
        );
        assertTrue(exception.getErrors().size() >= 2);
    }
}
