package com.senkoje.domain.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.senkoje.domain.model.DeckSettings;
import org.junit.jupiter.api.Test;

/**
 * Kiểm thử đơn vị cho bộ kiểm tra hợp lệ cấu hình FSRS-6 của bộ thẻ.
 */
class DeckSettingsValidatorTest {

    private final DeckSettingsValidator validator = new DeckSettingsValidator();

    @Test
    void shouldAcceptDefaultAndCustomValidDeckSettings() {
        assertDoesNotThrow(() -> validator.validate(DeckSettings.defaults(1L)));

        DeckSettings custom = new DeckSettings(
                1L,
                0.85,
                365,
                0,
                500,
                "30s,5m,1h",
                "",
                false
        );
        assertDoesNotThrow(() -> validator.validate(custom));
    }

    @Test
    void shouldRejectOutOfBoundsRetentionAndLimits() {
        assertThrows(DomainValidationException.class, () -> validator.validate(null));

        DeckSettings outOfBounds = new DeckSettings(
                1L,
                0.50,
                0,
                -1,
                -10,
                "1m,10m",
                "10m",
                true
        );

        DomainValidationException exception = assertThrows(
                DomainValidationException.class,
                () -> validator.validate(outOfBounds)
        );
        assertTrue(exception.getErrors().size() >= 4);

        DeckSettings nanRetention = new DeckSettings(
                1L,
                Double.NaN,
                36500,
                20,
                200,
                "1m",
                "10m",
                true
        );
        assertThrows(DomainValidationException.class, () -> validator.validate(nanRetention));
    }

    @Test
    void shouldRejectMalformedOrZeroDurationSteps() {
        DeckSettings invalidToken = new DeckSettings(
                1L,
                0.90,
                36500,
                20,
                200,
                "1m,abc",
                "10m",
                true
        );
        assertThrows(DomainValidationException.class, () -> validator.validate(invalidToken));

        DeckSettings zeroDuration = new DeckSettings(
                1L,
                0.90,
                36500,
                20,
                200,
                "0m,10m",
                "10m",
                true
        );
        assertThrows(DomainValidationException.class, () -> validator.validate(zeroDuration));

        DeckSettings trailingComma = new DeckSettings(
                1L,
                0.90,
                36500,
                20,
                200,
                "1m,",
                "10m",
                true
        );
        assertThrows(DomainValidationException.class, () -> validator.validate(trailingComma));
    }
}
