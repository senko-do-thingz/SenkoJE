package com.senkoje.infrastructure.exporter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Kiểm thử tính hợp lệ và số lượng của các bộ thẻ mẫu được tạo trong thư mục sample-decks.
 */
class SampleDecksValidationTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    @DisplayName("Xác thực tệp ja_n5_essential_100.json có đủ 100 từ vựng và trường hợp lệ")
    void shouldValidateJapaneseN5SampleDeck() throws IOException {
        Path filePath = Path.of("sample-decks/ja_n5_essential_100.json");
        assertTrue(Files.exists(filePath), "Tệp ja_n5_essential_100.json phải tồn tại");

        JsonNode root = objectMapper.readTree(filePath.toFile());
        assertEquals("1.0", root.path("schema_version").asText());
        assertEquals("SenkoJE", root.path("generator").asText());

        JsonNode deck = root.path("deck");
        assertEquals("JLPT N5 Essential Vocabulary 100", deck.path("title").asText());
        assertEquals("JA", deck.path("target_language").asText());
        assertEquals("vi", deck.path("definition_language").asText());
        assertEquals("N5", deck.path("level").asText());

        JsonNode cards = root.path("cards");
        assertEquals(100, cards.size(), "Bộ thẻ mẫu tiếng Nhật N5 phải có chính xác 100 thẻ");

        for (int i = 0; i < cards.size(); i++) {
            JsonNode card = cards.get(i);
            assertFalse(card.path("term").asText().isBlank(), "Thẻ thứ " + i + " phải có từ vựng");
            assertFalse(card.path("reading").asText().isBlank(), "Thẻ thứ " + i + " phải có cách đọc");
            assertFalse(card.path("meaning").asText().isBlank(), "Thẻ thứ " + i + " phải có giải nghĩa");
            assertEquals("VOCABULARY", card.path("card_type").asText());
            assertEquals("N5", card.path("level").asText());
            assertNotNull(card.path("extra_data").path("furigana").asText());
            assertTrue(card.path("examples").size() >= 1, "Thẻ thứ " + i + " phải có ít nhất 1 câu ví dụ");
        }
    }

    @Test
    @DisplayName("Xác thực tệp en_b1_collocations.json có đủ 100 cụm từ và trường hợp lệ")
    void shouldValidateEnglishB1SampleDeck() throws IOException {
        Path filePath = Path.of("sample-decks/en_b1_collocations.json");
        assertTrue(Files.exists(filePath), "Tệp en_b1_collocations.json phải tồn tại");

        JsonNode root = objectMapper.readTree(filePath.toFile());
        assertEquals("1.0", root.path("schema_version").asText());
        assertEquals("SenkoJE", root.path("generator").asText());

        JsonNode deck = root.path("deck");
        assertEquals("English B1 Essential Collocations 100", deck.path("title").asText());
        assertEquals("EN", deck.path("target_language").asText());
        assertEquals("vi", deck.path("definition_language").asText());
        assertEquals("B1", deck.path("level").asText());

        JsonNode cards = root.path("cards");
        assertEquals(100, cards.size(), "Bộ thẻ mẫu tiếng Anh B1 phải có chính xác 100 cụm từ");

        for (int i = 0; i < cards.size(); i++) {
            JsonNode card = cards.get(i);
            assertFalse(card.path("term").asText().isBlank(), "Thẻ thứ " + i + " phải có cụm từ");
            assertFalse(card.path("meaning").asText().isBlank(), "Thẻ thứ " + i + " phải có giải nghĩa");
            assertEquals("PHRASE", card.path("card_type").asText());
            assertEquals("B1", card.path("level").asText());
            assertTrue(card.path("examples").size() >= 1, "Thẻ thứ " + i + " phải có ít nhất 1 câu ví dụ");
        }
    }
}
