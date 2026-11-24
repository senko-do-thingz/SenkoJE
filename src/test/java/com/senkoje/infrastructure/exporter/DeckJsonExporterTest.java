package com.senkoje.infrastructure.exporter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.senkoje.application.dto.CardExtraData;
import com.senkoje.application.mapper.CardExtraDataCodec;
import com.senkoje.domain.model.Card;
import com.senkoje.domain.model.CardExample;
import com.senkoje.domain.model.CardMeaning;
import com.senkoje.domain.model.CardType;
import com.senkoje.domain.model.Deck;
import com.senkoje.domain.model.DeckSettings;
import com.senkoje.domain.model.DefinitionLanguage;
import com.senkoje.domain.model.ProficiencyLevel;
import com.senkoje.domain.model.TargetLanguage;
import java.io.IOException;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Kiểm thử đơn vị cho bộ xuất JSON DeckJsonExporter.
 */
class DeckJsonExporterTest {

    private static final Instant FIXED_INSTANT = Instant.parse("2026-11-24T10:00:00Z");
    private DeckJsonExporter exporter;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        Clock fixedClock = Clock.fixed(FIXED_INSTANT, ZoneOffset.UTC);
        exporter = new DeckJsonExporter(fixedClock);
        objectMapper = new ObjectMapper();
    }

    @Test
    @DisplayName("Xuất bộ thẻ tiếng Nhật kèm cấu hình và thẻ học sang JSON hợp lệ")
    void shouldExportJapaneseDeckToJsonSuccessfully() throws IOException {
        Deck deck = new Deck(
                1L,
                "Từ vựng N5",
                TargetLanguage.JA,
                DefinitionLanguage.VI,
                ProficiencyLevel.JLPT_N5,
                "Mô tả bộ thẻ N5",
                FIXED_INSTANT,
                FIXED_INSTANT
        );
        DeckSettings settings = new DeckSettings(
                1L,
                0.90,
                36500,
                20,
                200,
                "1m,10m",
                "10m",
                true
        );

        CardExtraData extraData = new CardExtraData(
                "食[た]べる",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null
        );
        String extraJson = CardExtraDataCodec.toJson(extraData);

        Card card = new Card(
                10L,
                1L,
                CardType.VOCABULARY,
                "食べる",
                "たべる",
                "ăn",
                "verb",
                ProficiencyLevel.JLPT_N5,
                extraJson,
                "Động từ nhóm 2",
                false,
                FIXED_INSTANT,
                FIXED_INSTANT
        );

        CardMeaning meaning = new CardMeaning(101L, 10L, DefinitionLanguage.VI, "dùng bữa", 1);
        CardExample example = new CardExample(201L, 10L, "ご飯を食べる。", "Ăn cơm.", DefinitionLanguage.VI, 1);
        ExportCardBundle bundle = new ExportCardBundle(card, List.of(meaning), List.of(example));

        String json = exporter.exportDeckToString(deck, settings, List.of(bundle));
        assertNotNull(json);

        JsonNode root = objectMapper.readTree(json);
        assertEquals("1.0", root.path("schema_version").asText());
        assertEquals("SenkoJE", root.path("generator").asText());
        assertEquals("2026-11-24T10:00:00Z", root.path("exported_at").asText());

        JsonNode deckNode = root.path("deck");
        assertEquals("Từ vựng N5", deckNode.path("title").asText());
        assertEquals("JA", deckNode.path("target_language").asText());
        assertEquals("vi", deckNode.path("definition_language").asText());
        assertEquals("N5", deckNode.path("level").asText());
        assertEquals(0.90, deckNode.path("settings").path("desired_retention").asDouble());

        JsonNode cardsNode = root.path("cards");
        assertEquals(1, cardsNode.size());
        JsonNode firstCard = cardsNode.get(0);
        assertEquals("食べる", firstCard.path("term").asText());
        assertEquals("たべる", firstCard.path("reading").asText());
        assertEquals("ăn", firstCard.path("meaning").asText());
        assertEquals("食[た]べる", firstCard.path("extra_data").path("furigana").asText());

        assertEquals(1, firstCard.path("meanings").size());
        assertEquals("dùng bữa", firstCard.path("meanings").get(0).path("meaning").asText());

        assertEquals(1, firstCard.path("examples").size());
        assertEquals("ご飯を食べる。", firstCard.path("examples").get(0).path("sentence").asText());
    }

    @Test
    @DisplayName("Xuất ra tệp tin đích Path thành công")
    void shouldExportDeckToFilePath(@TempDir Path tempDir) throws IOException {
        Deck deck = new Deck(
                2L,
                "English B1",
                TargetLanguage.EN,
                DefinitionLanguage.VI,
                ProficiencyLevel.CEFR_B1,
                "English collocations",
                FIXED_INSTANT,
                FIXED_INSTANT
        );
        Card card = new Card(
                20L,
                2L,
                CardType.PHRASE,
                "make a decision",
                "",
                "ra quyết định",
                "phrase",
                ProficiencyLevel.CEFR_B1,
                null,
                null,
                false,
                FIXED_INSTANT,
                FIXED_INSTANT
        );
        ExportCardBundle bundle = ExportCardBundle.ofSingleCard(card);

        Path exportFile = tempDir.resolve("exported_deck.json");
        exporter.exportDeck(deck, null, List.of(bundle), exportFile);

        assertTrue(Files.exists(exportFile));
        assertTrue(Files.size(exportFile) > 0);

        JsonNode root = objectMapper.readTree(Files.readString(exportFile));
        assertEquals("English B1", root.path("deck").path("title").asText());
        assertEquals("EN", root.path("deck").path("target_language").asText());
        assertEquals(1, root.path("cards").size());
    }
}
