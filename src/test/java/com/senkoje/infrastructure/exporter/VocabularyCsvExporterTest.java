package com.senkoje.infrastructure.exporter;

import com.senkoje.domain.model.Card;
import com.senkoje.domain.model.CardExample;
import com.senkoje.domain.model.CardType;
import com.senkoje.domain.model.DefinitionLanguage;
import com.senkoje.domain.model.ProficiencyLevel;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Kiểm thử đơn vị cho bộ xuất từ vựng CSV VocabularyCsvExporter.
 */
class VocabularyCsvExporterTest {

    private VocabularyCsvExporter exporter;

    @BeforeEach
    void setUp() {
        exporter = new VocabularyCsvExporter();
    }

    @Test
    @DisplayName("Xuất danh sách thẻ ra chuỗi CSV có đúng hàng tiêu đề và trường dữ liệu")
    void shouldExportCardsToCsvStringWithExpectedHeaders() throws IOException {
        Instant now = Instant.parse("2026-11-24T10:00:00Z");
        Card card1 = new Card(
                1L,
                10L,
                CardType.VOCABULARY,
                "猫",
                "ねこ",
                "con mèo",
                "noun",
                ProficiencyLevel.JLPT_N5,
                null,
                "Ghi chú mèo",
                false,
                now,
                now
        );
        CardExample example1 = new CardExample(100L, 1L, "猫が好きです。", "Tôi thích mèo.", DefinitionLanguage.VI, 1);
        ExportCardBundle bundle1 = new ExportCardBundle(card1, List.of(), List.of(example1));

        Card card2 = new Card(
                2L,
                10L,
                CardType.VOCABULARY,
                "犬",
                "いぬ",
                "con chó, \"cún con\"",
                "noun",
                ProficiencyLevel.JLPT_N5,
                null,
                null,
                false,
                now,
                now
        );
        ExportCardBundle bundle2 = ExportCardBundle.ofSingleCard(card2);

        String csvString = exporter.exportCardsToString(List.of(bundle1, bundle2));
        assertNotNull(csvString);

        CSVFormat format = CSVFormat.DEFAULT.builder().setHeader().setSkipHeaderRecord(true).build();
        try (CSVParser parser = format.parse(new java.io.StringReader(csvString))) {
            List<CSVRecord> records = parser.getRecords();
            assertEquals(2, records.size());

            CSVRecord r1 = records.get(0);
            assertEquals("猫", r1.get("term"));
            assertEquals("ねこ", r1.get("reading"));
            assertEquals("con mèo", r1.get("meaning"));
            assertEquals("VOCABULARY", r1.get("card_type"));
            assertEquals("N5", r1.get("proficiency_level"));
            assertEquals("猫が好きです。", r1.get("example_sentence"));
            assertEquals("Tôi thích mèo.", r1.get("example_sentence_meaning"));
            assertEquals("Ghi chú mèo", r1.get("notes"));

            CSVRecord r2 = records.get(1);
            assertEquals("犬", r2.get("term"));
            assertEquals("いぬ", r2.get("reading"));
            assertEquals("con chó, \"cún con\"", r2.get("meaning"));
        }
    }

    @Test
    @DisplayName("Xuất ra tệp CSV cục bộ thành công")
    void shouldExportCardsToFile(@TempDir Path tempDir) throws IOException {
        Instant now = Instant.parse("2026-11-24T10:00:00Z");
        Card card = new Card(
                3L,
                10L,
                CardType.PHRASE,
                "take a break",
                "",
                "nghỉ ngơi",
                "phrase",
                ProficiencyLevel.CEFR_B1,
                null,
                null,
                false,
                now,
                now
        );
        Path targetPath = tempDir.resolve("cards.csv");
        exporter.exportCards(List.of(ExportCardBundle.ofSingleCard(card)), targetPath);

        assertTrue(Files.exists(targetPath));
        assertTrue(Files.size(targetPath) > 0);
    }
}
