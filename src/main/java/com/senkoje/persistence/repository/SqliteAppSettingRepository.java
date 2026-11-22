package com.senkoje.persistence.repository;

import com.senkoje.domain.model.AppSetting;
import com.senkoje.domain.repository.AppSettingRepository;
import com.senkoje.persistence.sqlite.SqliteConnectionManager;
import com.senkoje.persistence.sqlite.SqliteTransactionExecutor;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Triển khai SQLite cho kho lưu trữ cấu hình ứng dụng cục bộ trong bảng app_settings.
 */
public class SqliteAppSettingRepository implements AppSettingRepository {

    private final SqliteTransactionExecutor transactionExecutor;

    /**
     * Khởi tạo kho lưu trữ cấu hình ứng dụng sử dụng trình quản lý kết nối SQLite.
     *
     * @param connectionManager trình quản lý kết nối SQLite
     */
    public SqliteAppSettingRepository(SqliteConnectionManager connectionManager) {
        this.transactionExecutor = new SqliteTransactionExecutor(connectionManager);
    }

    @Override
    public Optional<AppSetting> findByKey(String settingKey) {
        Objects.requireNonNull(settingKey, "settingKey must not be null");
        String sql = """
                SELECT setting_key, setting_value, updated_at
                FROM app_settings
                WHERE setting_key = ?
                """;
        return transactionExecutor.executeRead("find app setting by key", connection -> {
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                ps.setString(1, settingKey);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return Optional.of(mapAppSetting(rs));
                    }
                    return Optional.empty();
                }
            }
        });
    }

    @Override
    public List<AppSetting> findAll() {
        String sql = """
                SELECT setting_key, setting_value, updated_at
                FROM app_settings
                ORDER BY setting_key ASC
                """;
        return transactionExecutor.executeRead("list all app settings", connection -> {
            List<AppSetting> settings = new ArrayList<>();
            try (PreparedStatement ps = connection.prepareStatement(sql);
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    settings.add(mapAppSetting(rs));
                }
            }
            return settings;
        });
    }

    @Override
    public AppSetting save(AppSetting setting) {
        Objects.requireNonNull(setting, "setting must not be null");
        String sql = """
                INSERT INTO app_settings (setting_key, setting_value, updated_at)
                VALUES (?, ?, ?)
                ON CONFLICT(setting_key) DO UPDATE SET
                    setting_value = excluded.setting_value,
                    updated_at = excluded.updated_at
                """;
        return transactionExecutor.executeInTransaction("save app setting", connection -> {
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                ps.setString(1, setting.settingKey());
                ps.setString(2, setting.settingValue());
                ps.setString(3, setting.updatedAt().toString());
                ps.executeUpdate();
            }
            return setting;
        });
    }

    @Override
    public boolean deleteByKey(String settingKey) {
        Objects.requireNonNull(settingKey, "settingKey must not be null");
        String sql = "DELETE FROM app_settings WHERE setting_key = ?";
        return transactionExecutor.executeInTransaction("delete app setting", connection -> {
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                ps.setString(1, settingKey);
                return ps.executeUpdate() > 0;
            }
        });
    }

    private AppSetting mapAppSetting(ResultSet rs) throws SQLException {
        return new AppSetting(
                rs.getString("setting_key"),
                rs.getString("setting_value"),
                Instant.parse(rs.getString("updated_at"))
        );
    }
}
