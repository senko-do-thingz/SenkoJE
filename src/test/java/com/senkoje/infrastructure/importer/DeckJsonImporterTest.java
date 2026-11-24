package com.senkoje.infrastructure.importer;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Kiểm thử đơn vị cho bộ nhập JSON DeckJsonImporter.
 */
class DeckJsonImporterTest {

    private DeckJsonImporter importer;

    @BeforeEach
    void setUp() {
        importer = new DeckJsonImporter();
    }

    @Test
    @DisplayName("Xem trước tệp mẫu ja_n5_essential_100.json thành công với 100 thẻ hợp lệ")
    void shouldPreviewJapaneseN5SampleDeckSuccessfully() throws IOException {
        Path path = Path.of("sample-decks/ja_n5_essential_100.json");
        ImportPreviewReport report = importer.generatePreview(path, Set.of());

        assertEquals(100, report.totalCount());
        assertEquals(100, report.validCount());
        assertEquals(0, report.malformedCount());
        assertEquals(0, report.duplicateCount());
        assertTrue(report.canImport());
        assertFalse(report.hasErrors());

        assertNotNull(report.candidateDeck());
        assertEquals("JLPT N5 Essential Vocabulary 100", report.candidateDeck().title());
    }

    @Test
    @DisplayName("Báo cáo lỗi khi tệp JSON thiếu schema_version")
    void shouldReportErrorWhenSchemaVersionIsMissing() throws IOException {
        String json = """
                {
                  "deck": { "title": "Test Deck" },
                  "cards": [
                    { "term": "test", "meaning": "thử nghiệm" }
                  ]
                }
                """;
        ImportPreviewReport report = importer.generatePreviewFromString(json, Set.of());

        assertTrue(report.hasErrors());
        assertEquals(1, report.malformedCount());
        assertTrue(report.items().get(0).message().contains("schema_version"));
    }

    @Test
    @DisplayName("Báo cáo lỗi khi thẻ thiếu trường bắt buộc term hoặc meaning")
    void shouldReportErrorWhenRequiredFieldsAreMissing() throws IOException {
        String json = """
                {
                  "schema_version": "1.0",
                  "deck": { "title": "Test Deck" },
                  "cards": [
                    { "term": "hợp lệ", "meaning": "nghĩa hợp lệ" },
                    { "term": "", "meaning": "thiếu term" },
                    { "term": "thiếu meaning", "meaning": "" }
                  ]
                }
                """;
        ImportPreviewReport report = importer.generatePreviewFromString(json, Set.of());

        assertEquals(3, report.totalCount());
        assertEquals(1, report.validCount());
        assertEquals(2, report.malformedCount());
        assertEquals(0, report.duplicateCount());
        assertTrue(report.hasErrors());
    }

    @Test
    @DisplayName("Phát hiện từ trùng lặp với bộ thẻ đích và trong cùng một tệp nhập")
    void shouldDetectDuplicateTermsCorrectly() throws IOException {
        String json = """
                {
                  "schema_version": "1.0",
                  "deck": { "title": "Test Deck" },
                  "cards": [
                    { "term": "Trùng Ngoài", "meaning": "nghĩa 1" },
                    { "term": "Mới 1", "meaning": "nghĩa 2" },
                    { "term": "Mới 1", "meaning": "nghĩa 3 trùng trong tệp" }
                  ]
                }
                """;
        ImportPreviewReport report = importer.generatePreviewFromString(json, Set.of("trùng ngoài"));

        assertEquals(3, report.totalCount());
        assertEquals(1, report.validCount());
        assertEquals(0, report.malformedCount());
        assertEquals(2, report.duplicateCount());
        assertTrue(report.hasDuplicates());
    }

    @Test
    @DisplayName("Báo lỗi cú pháp khi chuỗi JSON bị hỏng cấu trúc")
    void shouldHandleMalformedJsonSyntaxGracefully() throws IOException {
        String malformedJson = "{ invalid json content ";
        ImportPreviewReport report = importer.generatePreviewFromString(malformedJson, Set.of());

        assertTrue(report.hasErrors());
        assertEquals(1, report.malformedCount());
        assertTrue(report.items().get(0).message().contains("Malformed JSON"));
    }
}
