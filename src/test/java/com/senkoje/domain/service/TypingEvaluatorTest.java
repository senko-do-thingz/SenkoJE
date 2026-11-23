package com.senkoje.domain.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.senkoje.domain.model.TargetLanguage;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Kiểm thử đơn vị thuần miền cho TypingEvaluator xác minh quy tắc nghiêm ngặt cho tiếng Nhật
 * (không đánh đồng Kana/Kanji hay Hiragana/Katakana) và tùy chọn linh hoạt/nghiêm ngặt cho tiếng Anh.
 */
class TypingEvaluatorTest {

    private final TypingEvaluator evaluator = new TypingEvaluator();

    @Test
    void shouldRejectJapaneseKanaWhenOnlyKanjiIsAccepted() {
        TypingEvaluationResult result = evaluator.evaluate(
                TargetLanguage.JA,
                "たべる",
                List.of("食べる"),
                TypingEvaluationOptions.defaults()
        );
        assertFalse(result.correct());
        assertNull(result.matchedExpected());
        assertEquals(TypingEvaluator.FEEDBACK_MISMATCH, result.feedbackCode());
    }

    @Test
    void shouldRejectJapaneseKatakanaWhenOnlyHiraganaIsAccepted() {
        TypingEvaluationResult result = evaluator.evaluate(
                TargetLanguage.JA,
                "ネコ",
                List.of("ねこ"),
                TypingEvaluationOptions.defaults()
        );
        assertFalse(result.correct());
        assertEquals(TypingEvaluator.FEEDBACK_MISMATCH, result.feedbackCode());
    }

    @Test
    void shouldRejectDistinctJapaneseReadingsWithoutExplicitAcceptance() {
        TypingEvaluationResult result = evaluator.evaluate(
                TargetLanguage.JA,
                "はし",
                List.of("橋"),
                TypingEvaluationOptions.defaults()
        );
        assertFalse(result.correct());
    }

    @Test
    void shouldAcceptExplicitJapaneseAnswersAndTrimIdeographicSpaces() {
        TypingEvaluationResult kanjiMatch = evaluator.evaluate(
                TargetLanguage.JA,
                "\u3000食べる\u3000",
                List.of("食べる", "たべる"),
                TypingEvaluationOptions.defaults()
        );
        assertTrue(kanjiMatch.correct());
        assertEquals("食べる", kanjiMatch.matchedExpected());
        assertEquals(TypingEvaluator.FEEDBACK_EXACT_MATCH, kanjiMatch.feedbackCode());

        TypingEvaluationResult explicitKanaMatch = evaluator.evaluate(
                TargetLanguage.JA,
                "たべる",
                List.of("食べる", "たべる"),
                TypingEvaluationOptions.defaults()
        );
        assertTrue(explicitKanaMatch.correct());
        assertEquals("たべる", explicitKanaMatch.matchedExpected());
    }

    @Test
    void shouldSupportConfigurableEnglishCaseInsensitivityAndPunctuationHandling() {
        TypingEvaluationOptions relaxed = new TypingEvaluationOptions(true, true, false);
        TypingEvaluationResult matched = evaluator.evaluate(
                TargetLanguage.EN,
                "  Break the ice! ",
                List.of("break the ice"),
                relaxed
        );
        assertTrue(matched.correct());
        assertEquals("break the ice", matched.matchedExpected());
        assertEquals(TypingEvaluator.FEEDBACK_NORMALIZED_MATCH, matched.feedbackCode());

        TypingEvaluationOptions caseSensitive = new TypingEvaluationOptions(false, true, false);
        assertFalse(evaluator.evaluate(
                TargetLanguage.EN, "Break the ice", List.of("break the ice"), caseSensitive).correct());

        TypingEvaluationOptions punctuationSensitive = new TypingEvaluationOptions(true, false, false);
        assertFalse(evaluator.evaluate(
                TargetLanguage.EN, "break the ice!", List.of("break the ice"), punctuationSensitive).correct());
    }

    @Test
    void shouldEnforceStrictCharacterMatchingWhenEnabled() {
        TypingEvaluationOptions strict = new TypingEvaluationOptions(true, true, true);
        assertFalse(evaluator.evaluate(
                TargetLanguage.EN, "Resilient", List.of("resilient"), strict).correct());
        assertFalse(evaluator.evaluate(
                TargetLanguage.EN, "resilient.", List.of("resilient"), strict).correct());
        assertTrue(evaluator.evaluate(
                TargetLanguage.EN, "resilient", List.of("resilient"), strict).correct());
    }

    @Test
    void shouldHandleBlankInputAndRejectEmptyAcceptedAnswerList() {
        TypingEvaluationResult emptyInput = evaluator.evaluate(
                TargetLanguage.EN, "   ", List.of("hello"), TypingEvaluationOptions.defaults());
        assertFalse(emptyInput.correct());
        assertEquals(TypingEvaluator.FEEDBACK_EMPTY_INPUT, emptyInput.feedbackCode());

        assertThrows(
                IllegalArgumentException.class,
                () -> evaluator.evaluate(TargetLanguage.JA, "水", List.of("  "), TypingEvaluationOptions.defaults())
        );
    }
}
