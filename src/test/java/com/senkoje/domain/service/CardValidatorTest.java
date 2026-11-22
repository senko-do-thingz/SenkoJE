package com.senkoje.domain.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.senkoje.domain.model.Card;
import com.senkoje.domain.model.CardExample;
import com.senkoje.domain.model.CardMeaning;
import com.senkoje.domain.model.CardType;
import com.senkoje.domain.model.DefinitionLanguage;
import com.senkoje.domain.model.ProficiencyLevel;
import java.time.Instant;
import org.junit.jupiter.api.Test;

/**
 * Kiểm thử đơn vị cho bộ kiểm tra hợp lệ thẻ học tập, nghĩa bổ sung, ví dụ và chuỗi JSON extraData.
 */
class CardValidatorTest {

    private final CardValidator validator = new CardValidator();

    @Test
    void shouldAcceptValidCardWithValidJsonExtraData() {
        Instant now = Instant.parse("2026-01-01T00:00:00Z");
        Card card = new Card(
                null,
                1L,
                CardType.VOCABULARY,
                "勉強",
                "べんきょう",
                "học tập",
                "noun",
                ProficiencyLevel.JLPT_N5,
                "{\"pitchAccent\":0,\"tags\":[\"common\",\"school\"]}",
                "Suru verb",
                false,
                now,
                now
        );

        assertDoesNotThrow(() -> validator.validate(card));
        assertDoesNotThrow(() -> validator.validateMeaning(
                new CardMeaning(null, 1L, DefinitionLanguage.EN, "study", 0)));
        assertDoesNotThrow(() -> validator.validateExample(
                new CardExample(null, 1L, "毎日日本語を勉強します。", "Tôi học tiếng Nhật mỗi ngày.",
                        DefinitionLanguage.VI, 0)));
    }

    @Test
    void shouldRejectInvalidJsonExtraDataIncludingTrailingTokens() {
        Instant now = Instant.parse("2026-01-01T00:00:00Z");
        Card brokenJsonCard = new Card(
                null,
                1L,
                CardType.GRAMMAR,
                "〜ている",
                null,
                "đang làm gì đó",
                null,
                ProficiencyLevel.JLPT_N5,
                "{\"form\": \"te-form\"",
                null,
                false,
                now,
                now
        );
        assertThrows(DomainValidationException.class, () -> validator.validate(brokenJsonCard));

        Card trailingTokenJsonCard = new Card(
                null,
                1L,
                CardType.PHRASE,
                "break the ice",
                "/breɪk ðə aɪs/",
                "phá vỡ bầu không khí ngượng ngùng",
                "idiom",
                ProficiencyLevel.CEFR_B2,
                "{\"register\":\"informal\"} trailing",
                null,
                false,
                now,
                now
        );
        assertThrows(DomainValidationException.class, () -> validator.validate(trailingTokenJsonCard));
    }

    @Test
    void shouldRejectMissingRequiredCardFieldsAndInvalidSubEntities() {
        assertThrows(DomainValidationException.class, () -> validator.validate(null));

        Instant now = Instant.parse("2026-01-01T00:00:00Z");
        Card invalidCard = new Card(
                null,
                0L,
                null,
                "   ",
                null,
                "",
                null,
                null,
                null,
                null,
                false,
                now,
                now
        );

        DomainValidationException exception = assertThrows(
                DomainValidationException.class,
                () -> validator.validate(invalidCard)
        );
        assertTrue(exception.getErrors().size() >= 4);

        assertThrows(DomainValidationException.class, () -> validator.validateMeaning(
                new CardMeaning(null, 1L, null, "", -1)));
        assertThrows(DomainValidationException.class, () -> validator.validateExample(
                new CardExample(null, 1L, "   ", null, null, -1)));
    }
}
