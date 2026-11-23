package com.senkoje.infrastructure.nlp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/**
 * Kiểm thử đơn vị cho EnglishTextTokenizer: xác minh tách câu có chữ viết tắt, số thập phân,
 * từ rút gọn, từ ghép gạch nối, dấu câu và bảo toàn nguyên vẹn văn bản gốc.
 */
class EnglishTextTokenizerTest {

    private final EnglishTextTokenizer tokenizer = new EnglishTextTokenizer();

    @Test
    void shouldSegmentSentencesWithoutSplittingAbbreviationsOrDecimals() {
        String text = "Dr. Smith visited the U.S. in 2026. Pi is roughly 3.14! Isn't that well-known?";

        EnglishReadingDocument doc = tokenizer.analyze(text);
        assertEquals(3, doc.sentences().size());
        assertEquals("Dr. Smith visited the U.S. in 2026.", doc.sentences().get(0).text());
        assertEquals("Pi is roughly 3.14!", doc.sentences().get(1).text());
        assertEquals("Isn't that well-known?", doc.sentences().get(2).text());

        // Đảm bảo nối toàn bộ token khôi phục chính xác văn bản gốc
        String reconstructed = doc.tokens().stream()
                .map(EnglishToken::text)
                .collect(Collectors.joining());
        assertEquals(text, reconstructed);
    }

    @Test
    void shouldPreserveContractionsHyphenatedWordsAndOffsets() {
        String text = "We don't rely on cloud APIs; it's an offline-first, state-of-the-art study app.";
        EnglishReadingDocument doc = tokenizer.analyze(text);

        List<String> words = doc.tokens().stream()
                .filter(EnglishToken::word)
                .map(EnglishToken::text)
                .toList();

        assertTrue(words.contains("don't"));
        assertTrue(words.contains("it's"));
        assertTrue(words.contains("offline-first"));
        assertTrue(words.contains("state-of-the-art"));
        assertFalse(words.contains(";"));
        assertFalse(words.contains(","));
        assertFalse(words.contains("."));

        // Xác minh offset từng token khớp chính xác với chuỗi gốc
        for (EnglishToken token : doc.tokens()) {
            assertEquals(token.text(), text.substring(token.startOffset(), token.endOffset()));
        }
    }

    @Test
    void shouldHandleParagraphNewlinesAndTypographicApostrophes() {
        String text = "First line.\nShe\u2019s reading a B2-level essay.";
        EnglishReadingDocument doc = tokenizer.analyze(text);

        assertEquals(2, doc.sentences().size());
        assertEquals("First line.", doc.getSentenceText(0));
        assertEquals("She\u2019s reading a B2-level essay.", doc.getSentenceText(1));

        EnglishToken shes = doc.tokens().stream()
                .filter(token -> "She\u2019s".equals(token.text()))
                .findFirst()
                .orElseThrow();
        assertTrue(shes.word());
        assertEquals("she's", shes.normalizedWord());
        assertEquals(1, shes.sentenceIndex());
    }

    @Test
    void shouldHandleEmptyAndNullInputSafely() {
        assertTrue(tokenizer.analyze(null).sentences().isEmpty());
        assertTrue(tokenizer.analyze("").tokens().isEmpty());
    }
}
