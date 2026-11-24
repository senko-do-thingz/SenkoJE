package com.senkoje.infrastructure.backup;

import com.senkoje.persistence.migration.DatabaseMigrator;
import com.senkoje.persistence.sqlite.SqliteConnectionManager;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Dịch vụ sao lưu và phục hồi an toàn cho cơ sở dữ liệu SQLite cục bộ của SenkoJE.
 * Sử dụng cơ chế SQLite VACUUM INTO để xử lý trạng thái WAL/journal trực tuyến mà không gây khóa tệp.
 */
public class SqliteBackupService {

    private static final Logger LOGGER = LoggerFactory.getLogger(SqliteBackupService.class);
    private static final String JDBC_PREFIX = "jdbc:sqlite:";

    private final SqliteConnectionManager connectionManager;
    private final DatabaseMigrator databaseMigrator;
    private final Clock clock;

    /**
     * Khởi tạo dịch vụ sao lưu với trình quản lý kết nối và đồng hồ UTC mặc định.
     *
     * @param connectionManager trình quản lý kết nối SQLite của ứng dụng
     */
    public SqliteBackupService(SqliteConnectionManager connectionManager) {
        this(connectionManager, Clock.systemUTC());
    }

    /**
     * Khởi tạo dịch vụ sao lưu với đồng hồ chỉ định phục vụ kiểm thử tất định.
     *
     * @param connectionManager trình quản lý kết nối SQLite
     * @param clock nguồn cung cấp thời gian
     */
    public SqliteBackupService(SqliteConnectionManager connectionManager, Clock clock) {
        this.connectionManager = Objects.requireNonNull(connectionManager, "connectionManager must not be null");
        this.databaseMigrator = new DatabaseMigrator(this.connectionManager);
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    /**
     * Thực hiện sao lưu trực tuyến cơ sở dữ liệu SQLite hiện tại sang tệp đích chỉ định.
     *
     * @param destinationPath đường dẫn tệp tin sao lưu đích
     * @return siêu dữ liệu của bản sao lưu vừa tạo thành công
     * @throws BackupRestoreException nếu thao tác sao lưu thất bại
     */
    public BackupMetadata createBackup(Path destinationPath) {
        Objects.requireNonNull(destinationPath, "destinationPath must not be null");
        Path absoluteDest = destinationPath.toAbsolutePath().normalize();

        try {
            Path parentDir = absoluteDest.getParent();
            if (parentDir != null) {
                Files.createDirectories(parentDir);
            }
            // SQLite VACUUM INTO yêu cầu tệp đích chưa tồn tại trước khi chạy lệnh
            if (Files.exists(absoluteDest)) {
                Files.delete(absoluteDest);
            }
        } catch (IOException exception) {
            throw new BackupRestoreException("Failed to prepare destination path: " + absoluteDest, exception);
        }

        String escapedPath = absoluteDest.toString().replace("\\", "/").replace("'", "''");
        String vacuumSql = "VACUUM INTO '" + escapedPath + "'";

        try (Connection connection = connectionManager.openConnection();
             Statement statement = connection.createStatement()) {
            LOGGER.info("Starting SQLite online backup via VACUUM INTO to {}", absoluteDest);
            statement.execute(vacuumSql);
        } catch (SQLException exception) {
            // Dọn dẹp tệp dở dang nếu lệnh VACUUM INTO bị gián đoạn hoặc thất bại
            try {
                Files.deleteIfExists(absoluteDest);
            } catch (IOException ignored) {
            }
            throw new BackupRestoreException("SQLite online backup failed for target: " + absoluteDest, exception);
        }

        // Xác thực tính toàn vẹn của tệp bản sao lưu vừa được tạo
        validateBackupFile(absoluteDest);

        int deckCount = countRowsInBackup(absoluteDest, "decks");
        int cardCount = countRowsInBackup(absoluteDest, "cards");
        long fileSize;
        try {
            fileSize = Files.size(absoluteDest);
        } catch (IOException exception) {
            fileSize = 0L;
        }

        Instant now = clock.instant();
        LOGGER.info("SQLite backup completed successfully at {}: size={} bytes, decks={}, cards={}",
                absoluteDest, fileSize, deckCount, cardCount);
        return new BackupMetadata(absoluteDest, now, fileSize, deckCount, cardCount);
    }

    /**
     * Kiểm tra tính toàn vẹn và cấu trúc lược đồ bảng cốt lõi của một tệp sao lưu SQLite.
     *
     * @param backupPath đường dẫn tệp bản sao lưu cần kiểm tra
     * @return true nếu tệp tin hợp lệ
     * @throws BackupRestoreException nếu tệp không tồn tại, bị hỏng hoặc thiếu bảng dữ liệu cốt lõi
     */
    public boolean validateBackupFile(Path backupPath) {
        Objects.requireNonNull(backupPath, "backupPath must not be null");
        Path absolutePath = backupPath.toAbsolutePath().normalize();

        if (!Files.exists(absolutePath)) {
            throw new BackupRestoreException("Backup file does not exist: " + absolutePath);
        }

        try {
            if (Files.size(absolutePath) == 0) {
                throw new BackupRestoreException("Backup file is empty: " + absolutePath);
            }
        } catch (IOException exception) {
            throw new BackupRestoreException("Unable to read backup file size: " + absolutePath, exception);
        }

        String jdbcUrl = JDBC_PREFIX + absolutePath.toString().replace("\\", "/");
        try (Connection connection = DriverManager.getConnection(jdbcUrl);
             Statement statement = connection.createStatement()) {

            // Kiểm tra PRAGMA integrity_check
            try (ResultSet rs = statement.executeQuery("PRAGMA integrity_check")) {
                if (!rs.next() || !"ok".equalsIgnoreCase(rs.getString(1))) {
                    throw new BackupRestoreException("Integrity check failed for backup file: " + absolutePath);
                }
            }

            // Kiểm tra sự hiện diện của các bảng cốt lõi của SenkoJE
            String checkTablesSql = """
                    SELECT count(*) FROM sqlite_master
                    WHERE type = 'table' AND name IN ('decks', 'cards', 'review_states')
                    """;
            try (ResultSet rs = statement.executeQuery(checkTablesSql)) {
                if (!rs.next() || rs.getInt(1) < 3) {
                    throw new BackupRestoreException(
                            "Backup file is missing core tables (decks, cards, review_states): " + absolutePath);
                }
            }

            return true;
        } catch (SQLException exception) {
            throw new BackupRestoreException("Invalid or corrupted SQLite backup file: " + absolutePath, exception);
        }
    }

    /**
     * Phục hồi cơ sở dữ liệu từ tệp sao lưu an toàn với cơ chế tự động sao lưu dự phòng trước khi thay thế.
     *
     * @param sourceBackupPath đường dẫn tệp bản sao lưu nguồn
     * @return kết quả phục hồi chi tiết
     * @throws BackupRestoreException nếu việc phục hồi thất bại và đã hoàn tác về trạng thái cũ
     */
    public RestoreResult restoreBackup(Path sourceBackupPath) {
        Objects.requireNonNull(sourceBackupPath, "sourceBackupPath must not be null");
        Path absoluteSource = sourceBackupPath.toAbsolutePath().normalize();

        // Bước 1: Xác thực nghiêm ngặt tệp sao lưu nguồn trước khi can thiệp cơ sở dữ liệu hiện tại
        validateBackupFile(absoluteSource);

        Path currentDbPath = connectionManager.getDatabasePath();
        Instant now = clock.instant();
        Path safetyBackupPath = generateSafetyBackupPath(currentDbPath, now);

        // Bước 2: Tạo bản sao lưu an toàn tự động cho cơ sở dữ liệu hiện tại trước khi ghi đè
        if (Files.exists(currentDbPath)) {
            LOGGER.info("Creating automatic safety backup before restore at {}", safetyBackupPath);
            createBackup(safetyBackupPath);
        }

        // Bước 3: Thay thế tệp cơ sở dữ liệu và dọn dẹp các tệp nhật ký phụ (WAL / SHM / journal)
        try {
            cleanupCompanionFiles(currentDbPath);
            Files.copy(absoluteSource, currentDbPath, StandardCopyOption.REPLACE_EXISTING);
            LOGGER.info("Copied backup database from {} to {}", absoluteSource, currentDbPath);

            // Bước 4: Xác thực cơ sở dữ liệu vừa được phục hồi và chạy di trú Flyway nếu cần
            try (Connection connection = connectionManager.openConnection();
                 Statement statement = connection.createStatement()) {
                try (ResultSet rs = statement.executeQuery("PRAGMA integrity_check")) {
                    if (!rs.next() || !"ok".equalsIgnoreCase(rs.getString(1))) {
                        throw new SQLException("Restored database failed integrity check");
                    }
                }
            }

            databaseMigrator.migrate();

            int deckCount = countRowsInBackup(currentDbPath, "decks");
            int cardCount = countRowsInBackup(currentDbPath, "cards");

            LOGGER.info("Restore completed successfully: decks={}, cards={}", deckCount, cardCount);
            return new RestoreResult(absoluteSource, safetyBackupPath, now, deckCount, cardCount);
        } catch (Exception exception) {
            LOGGER.error("Restore failed! Attempting rollback from safety backup at {}", safetyBackupPath, exception);
            // Bước 5: Hoàn tác phục hồi an toàn về bản sao lưu dự phòng
            try {
                if (Files.exists(safetyBackupPath)) {
                    cleanupCompanionFiles(currentDbPath);
                    Files.copy(safetyBackupPath, currentDbPath, StandardCopyOption.REPLACE_EXISTING);
                    LOGGER.info("Rollback to safety backup succeeded.");
                }
            } catch (IOException rollbackIoException) {
                exception.addSuppressed(rollbackIoException);
                LOGGER.error("CRITICAL: Failed to rollback to safety backup!", rollbackIoException);
            }
            throw new BackupRestoreException("Restore failed and database was restored from safety backup: "
                    + safetyBackupPath, exception);
        }
    }

    private void cleanupCompanionFiles(Path dbPath) {
        Path walFile = Path.of(dbPath.toString() + "-wal");
        Path shmFile = Path.of(dbPath.toString() + "-shm");
        Path journalFile = Path.of(dbPath.toString() + "-journal");

        deleteIfExistsSilent(walFile);
        deleteIfExistsSilent(shmFile);
        deleteIfExistsSilent(journalFile);
    }

    private void deleteIfExistsSilent(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException exception) {
            LOGGER.warn("Failed to delete SQLite companion file during restore: {}", path, exception);
        }
    }

    private Path generateSafetyBackupPath(Path dbPath, Instant timestamp) {
        long epochMillis = timestamp.toEpochMilli();
        String fileName = dbPath.getFileName().toString() + ".safety_backup_" + epochMillis + ".db";
        return dbPath.resolveSibling(fileName);
    }

    private int countRowsInBackup(Path path, String tableName) {
        String jdbcUrl = JDBC_PREFIX + path.toAbsolutePath().normalize().toString().replace("\\", "/");
        try (Connection connection = DriverManager.getConnection(jdbcUrl);
             Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery("SELECT count(*) FROM " + tableName)) {
            if (rs.next()) {
                return rs.getInt(1);
            }
            return 0;
        } catch (SQLException exception) {
            return 0;
        }
    }
}
