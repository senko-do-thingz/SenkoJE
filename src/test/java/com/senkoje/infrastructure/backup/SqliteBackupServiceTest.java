package com.senkoje.infrastructure.backup;

import com.senkoje.domain.model.Card;
import com.senkoje.domain.model.CardType;
import com.senkoje.domain.model.Deck;
import com.senkoje.domain.model.DeckSettings;
import com.senkoje.domain.model.DefinitionLanguage;
import com.senkoje.domain.model.ProficiencyLevel;
import com.senkoje.domain.model.ReviewState;
import com.senkoje.domain.model.TargetLanguage;
import com.senkoje.domain.repository.CardRepository;
import com.senkoje.domain.repository.DeckRepository;
import com.senkoje.persistence.migration.DatabaseMigrator;
import com.senkoje.persistence.repository.SqliteCardRepository;
import com.senkoje.persistence.repository.SqliteDeckRepository;
import com.senkoje.persistence.sqlite.SqliteConnectionManager;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.List;
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
 * Kiểm thử tính an toàn của sao lưu trực tuyến và phục hồi cơ sở dữ liệu SQLite SqliteBackupService.
 */
class SqliteBackupServiceTest {

    private Path dbPath;
    private SqliteConnectionManager connectionManager;
    private SqliteBackupService backupService;
    private DeckRepository deckRepository;
    private CardRepository cardRepository;

    @BeforeEach
    void setUp(@TempDir Path tempDir) throws SQLException {
        dbPath = tempDir.resolve("senkoje_test.db");
        connectionManager = new SqliteConnectionManager(dbPath);
        DatabaseMigrator migrator = new DatabaseMigrator(connectionManager);
        migrator.migrate();

        backupService = new SqliteBackupService(connectionManager);
        deckRepository = new SqliteDeckRepository(connectionManager);
        cardRepository = new SqliteCardRepository(connectionManager);
    }

    @Test
    @DisplayName("Sao lưu trực tuyến cơ sở dữ liệu đang hoạt động thành công tạo tệp hợp lệ")
    void shouldCreateValidOnlineBackupFromActiveDatabase(@TempDir Path backupDir) {
        Instant now = Instant.parse("2026-11-24T10:00:00Z");
        Deck deck = deckRepository.createWithSettings(
                new Deck(null, "Bộ thẻ mẫu", TargetLanguage.JA, DefinitionLanguage.VI, ProficiencyLevel.JLPT_N5, "Mô tả", now, now),
                DeckSettings.defaults(null)
        );

        Card card = new Card(null, deck.id(), CardType.VOCABULARY, "本", "ほん", "sách", "noun", ProficiencyLevel.JLPT_N5, null, null, false, now, now);
        cardRepository.createWithDetails(card, List.of(), List.of(), ReviewState.initialNew(null, now));

        Path backupFile = backupDir.resolve("backup.db");
        BackupMetadata metadata = backupService.createBackup(backupFile);

        assertNotNull(metadata);
        assertTrue(Files.exists(backupFile));
        assertTrue(metadata.fileSizeBytes() > 0);
        assertEquals(1, metadata.deckCount());
        assertEquals(1, metadata.cardCount());
        assertTrue(backupService.validateBackupFile(backupFile));
    }

    @Test
    @DisplayName("Phục hồi cơ sở dữ liệu từ tệp sao lưu hợp lệ và tạo bản sao lưu an toàn tự động")
    void shouldRestoreDatabaseFromValidBackup(@TempDir Path backupDir) {
        Instant now = Instant.parse("2026-11-24T10:00:00Z");
        Deck originalDeck = deckRepository.createWithSettings(
                new Deck(null, "Gốc 1", TargetLanguage.JA, DefinitionLanguage.VI, ProficiencyLevel.JLPT_N5, "Mô tả 1", now, now),
                DeckSettings.defaults(null)
        );

        Path backupFile = backupDir.resolve("senkoje_original_backup.db");
        backupService.createBackup(backupFile);

        // Thêm một bộ thẻ mới vào DB hiện tại
        deckRepository.createWithSettings(
                new Deck(null, "Thêm Mới 2", TargetLanguage.EN, DefinitionLanguage.VI, ProficiencyLevel.CEFR_B1, "Mô tả 2", now, now),
                DeckSettings.defaults(null)
        );
        assertEquals(2, deckRepository.findAll().size());

        // Phục hồi lại từ bản sao lưu ban đầu (chỉ có 1 bộ thẻ)
        RestoreResult restoreResult = backupService.restoreBackup(backupFile);
        assertNotNull(restoreResult);
        assertEquals(1, restoreResult.deckCount());
        assertTrue(Files.exists(restoreResult.safetyBackupPath()), "Phải tự động tạo bản sao lưu an toàn trước khi phục hồi");

        // Kiểm tra cơ sở dữ liệu sau phục hồi
        List<Deck> restoredDecks = deckRepository.findAll();
        assertEquals(1, restoredDecks.size());
        assertEquals("Gốc 1", restoredDecks.get(0).title());
    }

    @Test
    @DisplayName("Từ chối phục hồi khi tệp bản sao lưu không tồn tại hoặc rỗng")
    void shouldRejectNonExistentOrEmptyBackupFile(@TempDir Path tempDir) throws IOException {
        Path nonExistent = tempDir.resolve("does_not_exist.db");
        assertThrows(BackupRestoreException.class, () -> backupService.validateBackupFile(nonExistent));
        assertThrows(BackupRestoreException.class, () -> backupService.restoreBackup(nonExistent));

        Path emptyFile = tempDir.resolve("empty.db");
        Files.createFile(emptyFile);
        assertThrows(BackupRestoreException.class, () -> backupService.validateBackupFile(emptyFile));
        assertThrows(BackupRestoreException.class, () -> backupService.restoreBackup(emptyFile));
    }

    @Test
    @DisplayName("Từ chối phục hồi tệp văn bản giả mạo không phải cơ sở dữ liệu SQLite")
    void shouldRejectCorruptedOrNonSqliteBackupFile(@TempDir Path tempDir) throws IOException {
        Path fakeDb = tempDir.resolve("corrupted.db");
        Files.writeString(fakeDb, "Đây không phải là tệp SQLite hợp lệ");

        assertThrows(BackupRestoreException.class, () -> backupService.validateBackupFile(fakeDb));
        assertThrows(BackupRestoreException.class, () -> backupService.restoreBackup(fakeDb));
    }

    @Test
    @DisplayName("Từ chối tệp SQLite thiếu các bảng dữ liệu cốt lõi của SenkoJE")
    void shouldRejectBackupFileWithMissingCoreTables(@TempDir Path tempDir) throws SQLException {
        Path incompleteDb = tempDir.resolve("incomplete.db");
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + incompleteDb.toString().replace("\\", "/"));
             Statement stmt = conn.createStatement()) {
            stmt.execute("CREATE TABLE dummy_table (id INTEGER PRIMARY KEY, name TEXT)");
        }

        assertThrows(BackupRestoreException.class, () -> backupService.validateBackupFile(incompleteDb));
        assertThrows(BackupRestoreException.class, () -> backupService.restoreBackup(incompleteDb));
    }
}
