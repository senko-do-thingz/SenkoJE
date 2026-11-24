package com.senkoje.infrastructure.importer;

import java.io.IOException;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Kiểm thử đơn vị cho bộ nhập CSV VocabularyCsvImporter.
 */
class VocabularyCsvImporterTest {

    private VocabularyCsvImporter importer;

    @BeforeEach
    void setUp() {
        importer = new VocabularyCsvImporter();
    }

    @Test
    @DisplayName("Phân tích chuỗi CSV hợp lệ với tiêu đề chuẩn SenkoJE")
    void shouldPreviewValidCsvSuccessfully() throws IOException {
        String csv = """
                term,reading,meaning,card_type,proficiency_level,example_sentence,example_sentence_reading,example_sentence_meaning,notes,tags
                猫,ねこ,con mèo,VOCABULARY,N5,猫が寝ている。,,Con mèo đang ngủ.,ghi chú,
                take a break,,nghỉ ngơi,PHRASE,B1,Let us take a break.,,Hãy nghỉ ngơi một chút.,,
                """;
        ImportPreviewReport report = importer.generatePreviewFromString(csv, Set.of());

        assertEquals(2, report.totalCount());
        assertEquals(2, report.validCount());
        assertEquals(0, report.malformedCount());
        assertEquals(0, report.duplicateCount());
        assertFalse(report.hasErrors());

        ImportPreviewItem item1 = report.items().get(0);
        assertEquals("猫", item1.card().term());
        assertEquals("ねこ", item1.card().reading());
        assertEquals("con mèo", item1.card().meaning());
        assertEquals("N5", item1.card().level().code());
    }

    @Test
    @DisplayName("Báo lỗi khi dòng CSV thiếu cột term hoặc meaning bắt buộc")
    void shouldReportMalformedCsvRows() throws IOException {
        String csv = """
                term,reading,meaning,card_type,proficiency_level,example_sentence,example_sentence_reading,example_sentence_meaning,notes,tags
                ,reading,meaning,VOCABULARY,N5,,,,
                term,reading,,VOCABULARY,N5,,,,
                """;
        ImportPreviewReport report = importer.generatePreviewFromString(csv, Set.of());

        assertEquals(2, report.totalCount());
        assertEquals(0, report.validCount());
        assertEquals(2, report.malformedCount());
        assertTrue(report.hasErrors());
    }

    @Test
    @DisplayName("Phát hiện thẻ trùng lặp từ tập từ vựng hiện có trong bộ thẻ")
    void shouldDetectDuplicateCsvRecords() throws IOException {
        String csv = """
                term,reading,meaning,card_type,proficiency_level,example_sentence,example_sentence_reading,example_sentence_meaning,notes,tags
                chó,cho,con chó,VOCABULARY,N5,,,,
                mèo,meo,con mèo,VOCABULARY,N5,,,,
                """;
        ImportPreviewReport report = importer.generatePreviewFromString(csv, Set.of("chó"));

        assertEquals(2, report.totalCount());
        assertEquals(1, report.validCount());
        assertEquals(1, report.duplicateCount());
        assertTrue(report.hasDuplicates());
    }
}
