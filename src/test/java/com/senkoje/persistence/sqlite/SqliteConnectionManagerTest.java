package com.senkoje.persistence.sqlite;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.senkoje.persistence.migration.DatabaseMigrator;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import org.flywaydb.core.api.output.MigrateResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Kiểm thử đơn vị cho trình quản lý kết nối SQLite và cơ chế di trú Flyway cục bộ.
 */
class SqliteConnectionManagerTest {

    @TempDir
    Path tempDir;

    @Test
    void shouldEnableAndVerifyForeignKeysOnEveryConnection() throws SQLException {
        Path dbPath = tempDir.resolve("nested").resolve("senkoje.db");
        SqliteConnectionManager manager = new SqliteConnectionManager(dbPath);

        // Kiểm tra kết nối đầu tiên bật PRAGMA foreign_keys = 1 và tạo tệp cơ sở dữ liệu
        try (Connection connection = manager.openConnection();
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery("PRAGMA foreign_keys")) {
            assertTrue(resultSet.next());
            assertEquals(1, resultSet.getInt(1));
        }
        assertTrue(Files.exists(dbPath));

        // Kiểm tra ràng buộc khóa ngoại thực sự ngăn bản ghi con không hợp lệ
        try (Connection connection = manager.openConnection();
             Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE parent_items (id INTEGER PRIMARY KEY)");
            statement.execute("CREATE TABLE child_items (id INTEGER PRIMARY KEY, parent_id INTEGER NOT NULL REFERENCES parent_items(id))");
            assertThrows(SQLException.class, () ->
                    statement.execute("INSERT INTO child_items (id, parent_id) VALUES (1, 999)"));
        }
    }

    @Test
    void shouldExecuteFlywayMigrationsAgainstLocalSqliteDatabase() throws SQLException {
        Path dbPath = tempDir.resolve("flyway").resolve("senkoje.db");
        SqliteConnectionManager manager = new SqliteConnectionManager(dbPath);
        DatabaseMigrator migrator = new DatabaseMigrator(manager, "classpath:db/test-migration");

        MigrateResult result = migrator.migrate();
        assertTrue(result.success);
        assertEquals(1, result.migrationsExecuted);

        // Xác minh bảng kiểm thử do kịch bản Flyway tạo ra đã tồn tại trong SQLite
        try (Connection connection = manager.openConnection();
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(
                     "SELECT name FROM sqlite_master WHERE type = 'table' AND name = 'bootstrap_check'")) {
            assertTrue(resultSet.next());
            assertEquals("bootstrap_check", resultSet.getString("name"));
        }
    }
}
