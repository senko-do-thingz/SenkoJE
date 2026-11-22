package com.senkoje.persistence.sqlite;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Quản lý kết nối JDBC đến cơ sở dữ liệu SQLite cục bộ của SenkoJE.
 * Luôn bật và kiểm tra ràng buộc khóa ngoại trên mọi kết nối được mở.
 */
public class SqliteConnectionManager {

    private static final Logger LOGGER = LoggerFactory.getLogger(SqliteConnectionManager.class);
    private static final String JDBC_SQLITE_PREFIX = "jdbc:sqlite:";

    private final Path databasePath;
    private final String jdbcUrl;

    /**
     * Khởi tạo trình quản lý kết nối cho tệp cơ sở dữ liệu SQLite chỉ định.
     *
     * @param databasePath đường dẫn tới tệp cơ sở dữ liệu SQLite
     */
    public SqliteConnectionManager(Path databasePath) {
        this.databasePath = Objects.requireNonNull(databasePath, "databasePath must not be null")
                .toAbsolutePath()
                .normalize();
        this.jdbcUrl = JDBC_SQLITE_PREFIX + this.databasePath;
    }

    /**
     * Mở một kết nối SQLite mới, đồng thời bật và xác minh PRAGMA foreign_keys = ON.
     *
     * @return kết nối SQLite đã sẵn sàng thực thi truy vấn
     * @throws SQLException nếu không thể mở kết nối hoặc không xác minh được ràng buộc khóa ngoại
     */
    public Connection openConnection() throws SQLException {
        ensureParentDirectoryExists();
        Connection connection = DriverManager.getConnection(jdbcUrl);
        try {
            enableAndVerifyForeignKeys(connection);
            return connection;
        } catch (SQLException exception) {
            // Đóng kết nối ngay lập tức nếu bước xác minh khóa ngoại thất bại để tránh rò rỉ tài nguyên
            connection.close();
            throw exception;
        }
    }

    /**
     * Đảm bảo thư mục cha của tệp cơ sở dữ liệu tồn tại trước khi kết nối hoặc di trú.
     *
     * @throws SQLException nếu không thể tạo thư mục cha trên hệ thống tệp
     */
    public void ensureParentDirectoryExists() throws SQLException {
        Path parentDirectory = databasePath.getParent();
        if (parentDirectory == null) {
            return;
        }
        try {
            Files.createDirectories(parentDirectory);
        } catch (IOException exception) {
            throw new SQLException("Failed to create database directory: " + parentDirectory, exception);
        }
    }

    /**
     * Trả về đường dẫn tuyệt đối của tệp cơ sở dữ liệu SQLite.
     *
     * @return đường dẫn tệp cơ sở dữ liệu
     */
    public Path getDatabasePath() {
        return databasePath;
    }

    /**
     * Trả về chuỗi kết nối JDBC SQLite tương ứng.
     *
     * @return chuỗi JDBC URL
     */
    public String getJdbcUrl() {
        return jdbcUrl;
    }

    /**
     * Bật chế độ kiểm tra khóa ngoại của SQLite và đọc lại giá trị để xác nhận thành công.
     *
     * @param connection kết nối SQLite vừa được mở
     * @throws SQLException nếu câu lệnh PRAGMA thất bại hoặc khóa ngoại chưa được kích hoạt
     */
    private void enableAndVerifyForeignKeys(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA foreign_keys = ON");
            try (ResultSet resultSet = statement.executeQuery("PRAGMA foreign_keys")) {
                if (!resultSet.next() || resultSet.getInt(1) != 1) {
                    throw new SQLException("Failed to verify SQLite PRAGMA foreign_keys = ON");
                }
            }
        }
        LOGGER.debug("Opened SQLite connection with foreign_keys enabled at {}", databasePath);
    }
}
