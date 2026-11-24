package com.senkoje.infrastructure.importer;

import com.senkoje.domain.model.Card;
import com.senkoje.domain.model.Deck;
import com.senkoje.domain.repository.CardRepository;
import com.senkoje.domain.repository.DeckRepository;
import com.senkoje.domain.repository.PersistenceException;
import com.senkoje.persistence.migration.DatabaseMigrator;
import com.senkoje.persistence.repository.SqliteCardRepository;
import com.senkoje.persistence.repository.SqliteDeckRepository;
import com.senkoje.persistence.sqlite.SqliteConnectionManager;
import java.io.IOException;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Kiểm thử tính nguyên tử, hoàn tác (rollback) và xử lý trùng lặp của AtomicDeckImporter.
 */
class AtomicDeckImporterTest {

    private SqliteConnectionManager connectionManager;
    private DeckRepository deckRepository;
    private CardRepository cardRepository;
    private AtomicDeckImporter atomicDeckImporter;
    private DeckJsonImporter jsonImporter;

    @BeforeEach
    void setUp(@TempDir Path tempDir) throws SQLException {
        Path dbPath = tempDir.resolve("atomic_import_test.db");
        connectionManager = new SqliteConnectionManager(dbPath);
        DatabaseMigrator migrator = new DatabaseMigrator(connectionManager);
        migrator.migrate();

        deckRepository = new SqliteDeckRepository(connectionManager);
        cardRepository = new SqliteCardRepository(connectionManager);
        atomicDeckImporter = new AtomicDeckImporter(connectionManager);
        jsonImporter = new DeckJsonImporter();
    }

    @Test
    @DisplayName("Nhập thành công bộ thẻ mẫu từ tệp JSON tạo mới cả bộ thẻ và các thẻ")
    void shouldImportFullDeckFromJsonSuccessfully() throws IOException {
        Path jsonPath = Path.of("sample-decks/en_b1_collocations.json");
        ImportPreviewReport preview = jsonImporter.generatePreview(jsonPath, Set.of());

        assertEquals(100, preview.validCount());
        ImportResult result = atomicDeckImporter.executeImport(preview, null, DuplicateStrategy.SKIP);

        assertEquals(100, result.importedCount());
        assertEquals(0, result.skippedCount());
        assertEquals(0, result.rejectedCount());

        List<Deck> decks = deckRepository.findAll();
        assertEquals(1, decks.size());
        assertEquals("English B1 Essential Collocations 100", decks.get(0).title());

        List<Card> cards = cardRepository.findByDeckId(result.targetDeckId());
        assertEquals(100, cards.size());
    }

    @Test
    @DisplayName("Bỏ qua các thẻ trùng lặp khi chiến lược là SKIP")
    void shouldSkipDuplicateCardsWhenStrategyIsSkip() throws IOException {
        String json = """
                {
                  "schema_version": "1.0",
                  "deck": { "title": "Duplicate Deck" },
                  "cards": [
                    { "term": "Từ 1", "meaning": "Nghĩa 1" },
                    { "term": "Từ 2", "meaning": "Nghĩa 2" }
                  ]
                }
                """;
        ImportPreviewReport preview = jsonImporter.generatePreviewFromString(json, Set.of());
        ImportResult firstRun = atomicDeckImporter.executeImport(preview, null, DuplicateStrategy.SKIP);
        assertEquals(2, firstRun.importedCount());

        // Nhập lại cùng nội dung với targetDeckId vừa tạo
        ImportPreviewReport secondPreview = jsonImporter.generatePreviewFromString(json, Set.of("từ 1", "từ 2"));
        ImportResult secondRun = atomicDeckImporter.executeImport(secondPreview, firstRun.targetDeckId(), DuplicateStrategy.SKIP);

        assertEquals(0, secondRun.importedCount());
        assertEquals(2, secondRun.skippedCount());

        List<Card> cards = cardRepository.findByDeckId(firstRun.targetDeckId());
        assertEquals(2, cards.size(), "Số lượng thẻ trong bộ thẻ không được tăng thêm");
    }

    @Test
    @DisplayName("Hoàn tác toàn bộ giao dịch (rollback) khi phát sinh lỗi midway, không để lại thẻ rác")
    void shouldRollbackCompletelyOnFailureMidway() throws SQLException {
        // Tạo một preview hợp lệ với 3 thẻ
        String json = """
                {
                  "schema_version": "1.0",
                  "deck": { "title": "Rollback Deck" },
                  "cards": [
                    { "term": "Thẻ A", "meaning": "Nghĩa A" },
                    { "term": "Thẻ B", "meaning": "Nghĩa B" }
                  ]
                }
                """;
        ImportPreviewReport preview;
        try {
            preview = jsonImporter.generatePreviewFromString(json, Set.of());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        // Tạo sẵn một bộ thẻ rỗng
        long targetDeckId = 9999L; // mã bộ thẻ không tồn tại để kiểm tra khóa ngoại thất bại hoặc tạo kết nối giả lập lỗi
        // Khi chèn vào targetDeckId không tồn tại, ràng buộc khóa ngoại (foreign keys = ON) sẽ ném lỗi!
        assertThrows(PersistenceException.class, () -> {
            atomicDeckImporter.executeImport(preview, targetDeckId, DuplicateStrategy.SKIP);
        });

        // Xác nhận không có bản ghi nào bị ghi dở dang vào bảng cards
        try (Connection conn = connectionManager.openConnection()) {
            try (var ps = conn.prepareStatement("SELECT COUNT(*) FROM cards WHERE deck_id = ?")) {
                ps.setLong(1, targetDeckId);
                try (var rs = ps.executeQuery()) {
                    assertTrue(rs.next());
                    assertEquals(0, rs.getInt(1), "Cơ sở dữ liệu phải không có thẻ nào sau khi rollback");
                }
            }
        }
    }
}
