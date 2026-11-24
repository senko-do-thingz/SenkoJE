package com.senkoje.infrastructure.importer;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Kiểm thử tính năng trích xuất an toàn (phòng chống Zip Slip và bom nén) và phân tích gói thẻ Anki .apkg.
 */
class AnkiApkgImporterTest {

    private AnkiApkgImporter importer;

    @BeforeEach
    void setUp() {
        importer = new AnkiApkgImporter();
    }

    @Test
    @DisplayName("Chặn đứng và ném SecurityException khi phát hiện mục ZIP có đường dẫn bất hợp pháp (Zip Slip)")
    void shouldBlockZipSlipAttack(@TempDir Path tempDir) throws IOException {
        Path maliciousZip = tempDir.resolve("malicious_slip.apkg");
        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(maliciousZip))) {
            // Tạo mục nén tìm cách thoát ra thư mục cha bằng ../
            ZipEntry slipEntry = new ZipEntry("../evil_slip.txt");
            zos.putNextEntry(slipEntry);
            zos.write("Dữ liệu độc hại".getBytes());
            zos.closeEntry();
        }

        Path extractTarget = tempDir.resolve("extract_sandbox");
        SecurityException exception = assertThrows(SecurityException.class, () -> {
            importer.extractApkgSafely(maliciousZip, extractTarget);
        });

        assertTrue(exception.getMessage().contains("Zip Slip"));
        assertFalse(Files.exists(tempDir.resolve("evil_slip.txt")), "Tệp không được phép ghi ra ngoài thư mục đích");
    }

    @Test
    @DisplayName("Làm sạch mã HTML và thẻ ngắt dòng an toàn")
    void shouldCleanHtmlTagsCorrectly() {
        String html = "<div><b>食べる</b><br/>ăn uống&nbsp;&amp;&nbsp;dùng bữa</div>";
        String cleaned = AnkiApkgImporter.cleanHtml(html);
        assertEquals("食べる\năn uống & dùng bữa", cleaned);
    }

    @Test
    @DisplayName("Rút trích câu trả lời chính từ mẫu Cloze deletion chuẩn Anki")
    void shouldExtractClozeAnswerProperly() {
        String cloze = "Thủ đô của Nhật Bản là {{c1::Tokyo::thành phố lớn}}.";
        String answer = AnkiApkgImporter.extractClozeAnswer(cloze);
        assertEquals("Tokyo", answer);
    }

    @Test
    @DisplayName("Trích xuất và phân tích thành công gói Anki giả lập chứa collection.anki2")
    void shouldExtractAndParseMockAnkiApkgSuccessfully(@TempDir Path tempDir) throws IOException, SQLException {
        // Tạo cơ sở dữ liệu SQLite Anki giả lập
        Path mockDb = tempDir.resolve("collection.anki2");
        String jdbcUrl = "jdbc:sqlite:" + mockDb.toString().replace("\\", "/");
        try (Connection conn = DriverManager.getConnection(jdbcUrl);
             Statement stmt = conn.createStatement()) {

            stmt.execute("CREATE TABLE col (id INTEGER PRIMARY KEY, decks TEXT)");
            stmt.execute("INSERT INTO col (id, decks) VALUES (1, '{\"159\": {\"id\": 159, \"name\": \"Japanese N5 Vocab\"}}')");

            stmt.execute("CREATE TABLE notes (id INTEGER PRIMARY KEY, flds TEXT, tags TEXT)");
            // Note 1: Thẻ cơ bản (Front \u001f Back \u001f Reading)
            stmt.execute("INSERT INTO notes (id, flds, tags) VALUES (1, '水\u001fnước\u001fみず', 'n5 easy')");
            // Note 2: Thẻ Cloze
            stmt.execute("INSERT INTO notes (id, flds, tags) VALUES (2, '東京は{{c1::日本}}の首都です。\u001fTokyo là thủ đô Nhật Bản', 'geography')");
        }

        // Đóng gói SQLite giả lập vào tệp ZIP .apkg
        Path mockApkg = tempDir.resolve("test_deck.apkg");
        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(mockApkg))) {
            ZipEntry dbEntry = new ZipEntry("collection.anki2");
            zos.putNextEntry(dbEntry);
            Files.copy(mockDb, zos);
            zos.closeEntry();
        }

        ImportPreviewReport report = importer.generatePreview(mockApkg, Set.of("trùng lặp"));

        assertNotNull(report);
        assertEquals(2, report.totalCount());
        assertEquals(2, report.validCount());
        assertEquals(0, report.malformedCount());
        assertNotNull(report.candidateDeck());
        assertEquals("Japanese N5 Vocab", report.candidateDeck().title());

        ImportPreviewItem item1 = report.items().get(0);
        assertEquals("水", item1.card().term());
        assertEquals("nước", item1.card().meaning());
        assertEquals("みず", item1.card().reading());

        ImportPreviewItem item2 = report.items().get(1);
        assertEquals("日本", item2.card().term());
        assertEquals("Tokyo là thủ đô Nhật Bản", item2.card().meaning());
    }

    @Test
    @DisplayName("Xử lý nhẹ nhàng khi ghi chú rỗng mà không làm hỏng toàn bộ lượt nhập")
    void shouldHandleEmptyNotesGracefully(@TempDir Path tempDir) throws IOException, SQLException {
        Path mockDb = tempDir.resolve("collection.anki2");
        String jdbcUrl = "jdbc:sqlite:" + mockDb.toString().replace("\\", "/");
        try (Connection conn = DriverManager.getConnection(jdbcUrl);
             Statement stmt = conn.createStatement()) {
            stmt.execute("CREATE TABLE col (id INTEGER PRIMARY KEY, decks TEXT)");
            stmt.execute("INSERT INTO col (id, decks) VALUES (1, '{}')");

            stmt.execute("CREATE TABLE notes (id INTEGER PRIMARY KEY, flds TEXT, tags TEXT)");
            stmt.execute("INSERT INTO notes (id, flds, tags) VALUES (1, '', '')");
            stmt.execute("INSERT INTO notes (id, flds, tags) VALUES (2, '猫\u001fmèo', '')");
        }

        Path mockApkg = tempDir.resolve("empty_notes.apkg");
        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(mockApkg))) {
            ZipEntry dbEntry = new ZipEntry("collection.anki2");
            zos.putNextEntry(dbEntry);
            Files.copy(mockDb, zos);
            zos.closeEntry();
        }

        ImportPreviewReport report = importer.generatePreview(mockApkg, Set.of());
        assertEquals(2, report.totalCount());
        assertEquals(1, report.validCount());
        assertEquals(1, report.malformedCount());
    }
}
