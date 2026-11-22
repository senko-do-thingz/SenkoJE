package com.senkoje.persistence.migration;

import com.senkoje.persistence.sqlite.SqliteConnectionManager;
import java.sql.SQLException;
import java.util.Objects;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.output.MigrateResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Thực thi quản lý phiên bản lược đồ cơ sở dữ liệu SQLite bằng Flyway.
 */
public class DatabaseMigrator {

    public static final String DEFAULT_MIGRATION_LOCATION = "classpath:db/migration";

    private static final Logger LOGGER = LoggerFactory.getLogger(DatabaseMigrator.class);

    private final SqliteConnectionManager connectionManager;
    private final String migrationLocation;

    /**
     * Khởi tạo trình di trú với thư mục tệp SQL mặc định của ứng dụng.
     *
     * @param connectionManager trình quản lý kết nối SQLite cục bộ
     */
    public DatabaseMigrator(SqliteConnectionManager connectionManager) {
        this(connectionManager, DEFAULT_MIGRATION_LOCATION);
    }

    /**
     * Khởi tạo trình di trú với đường dẫn kịch bản Flyway tùy chỉnh.
     *
     * @param connectionManager trình quản lý kết nối SQLite cục bộ
     * @param migrationLocation vị trí nạp kịch bản di trú Flyway
     */
    public DatabaseMigrator(SqliteConnectionManager connectionManager, String migrationLocation) {
        this.connectionManager = Objects.requireNonNull(connectionManager, "connectionManager must not be null");
        this.migrationLocation = Objects.requireNonNull(migrationLocation, "migrationLocation must not be null");
    }

    /**
     * Chạy toàn bộ kịch bản di trú Flyway chưa được áp dụng lên cơ sở dữ liệu SQLite.
     *
     * @return kết quả thực thi di trú của Flyway
     * @throws SQLException nếu không thể chuẩn bị thư mục cơ sở dữ liệu trước khi di trú
     */
    public MigrateResult migrate() throws SQLException {
        connectionManager.ensureParentDirectoryExists();
        LOGGER.info("Running Flyway database migrations from {} against {}",
                migrationLocation, connectionManager.getDatabasePath());

        Flyway flyway = Flyway.configure()
                .dataSource(connectionManager.getJdbcUrl(), null, null)
                .locations(migrationLocation)
                .load();

        MigrateResult result = flyway.migrate();
        LOGGER.info("Completed Flyway migrations: {} executed, target schema version {}",
                result.migrationsExecuted, result.targetSchemaVersion);
        return result;
    }
}
