package com.senkoje.persistence.migration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.senkoje.persistence.sqlite.SqliteConnectionManager;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashSet;
import java.util.Set;
import org.flywaydb.core.api.output.MigrateResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Kiểm thử việc áp dụng đầy đủ 5 kịch bản di trú Flyway, chỉ mục và ràng buộc khóa ngoại trên SQLite.
 */
class DatabaseMigrationTest {

    @TempDir
    Path tempDir;

    @Test
    void shouldApplyAllFiveMigrationsAndCreateExpectedTablesAndIndexes() throws SQLException {
        SqliteConnectionManager manager = new SqliteConnectionManager(tempDir.resolve("schema.db"));
        DatabaseMigrator migrator = new DatabaseMigrator(manager);

        MigrateResult result = migrator.migrate();
        assertTrue(result.success);
        assertEquals(5, result.migrationsExecuted);
        assertEquals("5", result.targetSchemaVersion);

        Set<String> tables = new HashSet<>();
        Set<String> indexes = new HashSet<>();
        try (Connection connection = manager.openConnection();
             Statement statement = connection.createStatement()) {
            try (ResultSet rs = statement.executeQuery(
                    "SELECT name FROM sqlite_master WHERE type = 'table'")) {
                while (rs.next()) {
                    tables.add(rs.getString("name"));
                }
            }
            try (ResultSet rs = statement.executeQuery(
                    "SELECT name FROM sqlite_master WHERE type = 'index'")) {
                while (rs.next()) {
                    indexes.add(rs.getString("name"));
                }
            }
        }

        // Xác minh đầy đủ 9 bảng dữ liệu theo đặc tả lược đồ
        assertTrue(tables.containsAll(Set.of(
                "decks",
                "deck_settings",
                "cards",
                "card_meanings",
                "card_examples",
                "review_states",
                "review_logs",
                "study_sessions",
                "app_settings"
        )));

        // Xác minh các chỉ mục cốt lõi cho thẻ và lịch ôn tập FSRS-6
        assertTrue(indexes.containsAll(Set.of(
                "idx_cards_deck_id",
                "idx_cards_type",
                "idx_review_due",
                "idx_review_state",
                "idx_review_logs_card_date",
                "idx_review_logs_date",
                "idx_study_sessions_deck_id",
                "idx_review_logs_session_id"
        )));
    }

    @Test
    void shouldEnforceForeignKeysCheckConstraintsAndCascadeBehaviors() throws SQLException {
        SqliteConnectionManager manager = new SqliteConnectionManager(tempDir.resolve("constraints.db"));
        new DatabaseMigrator(manager).migrate();

        try (Connection connection = manager.openConnection();
             Statement statement = connection.createStatement()) {
            // Tạo bộ thẻ, cấu hình, thẻ, nghĩa bổ sung, ví dụ, phiên học, trạng thái và nhật ký ôn tập
            statement.execute("""
                    INSERT INTO decks (id, title, target_language, definition_language, level, description, created_at, updated_at)
                    VALUES (1, 'JLPT N5 Core', 'JA', 'vi', 'N5', 'Basic deck', '2026-01-01T00:00:00Z', '2026-01-01T00:00:00Z')
                    """);
            statement.execute("""
                    INSERT INTO deck_settings (deck_id, desired_retention, maximum_interval, new_cards_per_day,
                                               maximum_reviews_per_day, learning_steps, relearning_steps, enable_fuzzing)
                    VALUES (1, 0.90, 36500, 20, 200, '1m,10m', '10m', 1)
                    """);
            statement.execute("""
                    INSERT INTO cards (id, deck_id, card_type, term, reading, meaning, part_of_speech, level,
                                       extra_data, notes, suspended, created_at, updated_at)
                    VALUES (10, 1, 'VOCABULARY', '食べる', 'たべる', 'ăn', 'verb', 'N5',
                            '{"jlpt":"N5"}', 'Ichidan verb', 0, '2026-01-01T00:00:00Z', '2026-01-01T00:00:00Z')
                    """);
            statement.execute("""
                    INSERT INTO card_meanings (card_id, language_code, meaning, display_order)
                    VALUES (10, 'en', 'to eat', 1)
                    """);
            statement.execute("""
                    INSERT INTO card_examples (card_id, sentence, translation, translation_language, display_order)
                    VALUES (10, 'りんごを食べる。', 'Tôi ăn táo.', 'vi', 0)
                    """);
            statement.execute("""
                    INSERT INTO study_sessions (id, deck_id, started_at, completed_at, cards_reviewed, duration_seconds)
                    VALUES (100, 1, '2026-01-01T01:00:00Z', '2026-01-01T01:10:00Z', 1, 600)
                    """);
            statement.execute("""
                    INSERT INTO review_states (card_id, state, stability, difficulty, step, due_at, last_reviewed_at,
                                               scheduled_days, elapsed_days, repetitions, lapses, updated_at)
                    VALUES (10, 'LEARNING', 1.5, 4.2, 0, '2026-01-01T01:10:00Z', '2026-01-01T01:00:00Z',
                            0.0, 0.0, 1, 0, '2026-01-01T01:00:00Z')
                    """);
            statement.execute("""
                    INSERT INTO review_logs (id, card_id, session_id, rating, reviewed_at, response_time_ms,
                                             previous_state, next_state, previous_stability, next_stability,
                                             previous_difficulty, next_difficulty, elapsed_days, scheduled_days,
                                             algorithm_version, parameter_version)
                    VALUES (1000, 10, 100, 3, '2026-01-01T01:00:00Z', 1200,
                            'NEW', 'LEARNING', 0.0, 1.5, 0.0, 4.2, 0.0, 0.0, 'FSRS-6', 'default-v1')
                    """);

            // Xác minh ràng buộc CHECK từ chối mức đánh giá ngoài khoảng 1-4
            assertThrows(SQLException.class, () -> statement.execute("""
                    INSERT INTO review_logs (card_id, session_id, rating, reviewed_at, response_time_ms,
                                             previous_state, next_state, previous_stability, next_stability,
                                             previous_difficulty, next_difficulty, elapsed_days, scheduled_days,
                                             algorithm_version, parameter_version)
                    VALUES (10, 100, 5, '2026-01-01T01:05:00Z', 900,
                            'LEARNING', 'REVIEW', 1.5, 3.0, 4.2, 4.0, 0.0, 1.0, 'FSRS-6', 'default-v1')
                    """));

            // Xác minh ràng buộc khóa ngoại từ chối thẻ trỏ tới bộ thẻ không tồn tại
            assertThrows(SQLException.class, () -> statement.execute("""
                    INSERT INTO cards (deck_id, card_type, term, meaning, suspended, created_at, updated_at)
                    VALUES (999, 'VOCABULARY', '水', 'nước', 0, '2026-01-01T00:00:00Z', '2026-01-01T00:00:00Z')
                    """));

            // Xác minh cập nhật review_states không làm mất nhật ký ôn tập bất biến trong review_logs
            statement.execute("""
                    UPDATE review_states
                    SET state = 'REVIEW', stability = 4.5, updated_at = '2026-01-02T00:00:00Z'
                    WHERE card_id = 10
                    """);
            assertEquals(1, countRows(connection, "review_logs"));

            // Xác minh xóa study_sessions đặt session_id về NULL nhưng vẫn giữ nguyên review_logs
            statement.execute("DELETE FROM study_sessions WHERE id = 100");
            try (PreparedStatement ps = connection.prepareStatement("SELECT session_id FROM review_logs WHERE id = 1000");
                 ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next());
                assertNull(rs.getObject("session_id"));
            }

            // Tạo thêm phiên học mới để kiểm tra ON DELETE SET NULL khi xóa bộ thẻ
            statement.execute("""
                    INSERT INTO study_sessions (id, deck_id, started_at, cards_reviewed, duration_seconds)
                    VALUES (101, 1, '2026-01-02T01:00:00Z', 5, 300)
                    """);

            // Xác minh xóa deck kéo theo xóa deck_settings, cards và toàn bộ bản ghi con của card
            statement.execute("DELETE FROM decks WHERE id = 1");
            assertEquals(0, countRows(connection, "deck_settings"));
            assertEquals(0, countRows(connection, "cards"));
            assertEquals(0, countRows(connection, "card_meanings"));
            assertEquals(0, countRows(connection, "card_examples"));
            assertEquals(0, countRows(connection, "review_states"));
            assertEquals(0, countRows(connection, "review_logs"));

            // Xác minh lịch sử phiên học vẫn được giữ lại với deck_id = NULL
            try (PreparedStatement ps = connection.prepareStatement("SELECT deck_id FROM study_sessions WHERE id = 101");
                 ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next());
                assertNull(rs.getObject("deck_id"));
            }
        }
    }

    private int countRows(Connection connection, String tableName) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery("SELECT COUNT(*) FROM " + tableName)) {
            assertTrue(rs.next());
            return rs.getInt(1);
        }
    }
}
