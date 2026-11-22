-- Lưu cấu hình ứng dụng cục bộ dưới dạng cặp khóa và giá trị
CREATE TABLE app_settings (
    setting_key TEXT PRIMARY KEY,
    setting_value TEXT NOT NULL,
    updated_at TEXT NOT NULL
);
