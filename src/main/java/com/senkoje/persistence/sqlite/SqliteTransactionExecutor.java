package com.senkoje.persistence.sqlite;

import com.senkoje.domain.repository.PersistenceException;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Objects;

/**
 * Tiện ích thực thi truy vấn đọc và giao dịch ghi tường minh trên SQLite với cơ chế hoàn tác an toàn.
 */
public class SqliteTransactionExecutor {

    private final SqliteConnectionManager connectionManager;

    /**
     * Hàm chức năng nhận kết nối JDBC và có thể ném SQLException trong quá trình truy vấn.
     *
     * @param <T> kiểu dữ liệu trả về của thao tác cơ sở dữ liệu
     */
    @FunctionalInterface
    public interface SqlCallback<T> {
        T execute(Connection connection) throws SQLException;
    }

    /**
     * Khởi tạo bộ thực thi giao dịch dựa trên trình quản lý kết nối SQLite hiện có.
     *
     * @param connectionManager trình quản lý kết nối SQLite
     */
    public SqliteTransactionExecutor(SqliteConnectionManager connectionManager) {
        this.connectionManager = Objects.requireNonNull(connectionManager, "connectionManager must not be null");
    }

    /**
     * Thực thi một thao tác chỉ đọc trên kết nối SQLite đã bật khóa ngoại và tự động đóng kết nối.
     *
     * @param operationDescription mô tả thao tác phục vụ thông báo lỗi
     * @param callback hàm truy vấn đọc
     * @param <T> kiểu dữ liệu trả về
     * @return kết quả truy vấn
     */
    public <T> T executeRead(String operationDescription, SqlCallback<T> callback) {
        try (Connection connection = connectionManager.openConnection()) {
            return callback.execute(connection);
        } catch (SQLException exception) {
            throw new PersistenceException("Failed to " + operationDescription, exception);
        }
    }

    /**
     * Thực thi một hoặc nhiều câu lệnh ghi trong một giao dịch SQLite tường minh (commit hoặc rollback).
     *
     * @param operationDescription mô tả thao tác phục vụ thông báo lỗi
     * @param callback hàm thực thi ghi trên cùng một kết nối giao dịch
     * @param <T> kiểu dữ liệu trả về
     * @return kết quả sau khi commit thành công
     */
    public <T> T executeInTransaction(String operationDescription, SqlCallback<T> callback) {
        try (Connection connection = connectionManager.openConnection()) {
            boolean previousAutoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try {
                T result = callback.execute(connection);
                connection.commit();
                return result;
            } catch (SQLException | RuntimeException exception) {
                // Hoàn tác toàn bộ thay đổi trong giao dịch nếu có bất kỳ lỗi nào phát sinh
                try {
                    connection.rollback();
                } catch (SQLException rollbackException) {
                    exception.addSuppressed(rollbackException);
                }
                if (exception instanceof PersistenceException persistenceException) {
                    throw persistenceException;
                }
                throw new PersistenceException("Failed to " + operationDescription, exception);
            } finally {
                try {
                    connection.setAutoCommit(previousAutoCommit);
                } catch (SQLException ignored) {
                    // Kết nối sắp được đóng bởi khối try-with-resources
                }
            }
        } catch (SQLException exception) {
            throw new PersistenceException("Failed to open SQLite connection to " + operationDescription, exception);
        }
    }
}
