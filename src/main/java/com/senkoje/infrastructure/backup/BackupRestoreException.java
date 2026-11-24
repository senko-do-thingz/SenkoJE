package com.senkoje.infrastructure.backup;

/**
 * Ngoại lệ chuyên biệt cho các lỗi phát sinh trong quá trình sao lưu hoặc phục hồi cơ sở dữ liệu SQLite.
 */
public class BackupRestoreException extends RuntimeException {

    public BackupRestoreException(String message) {
        super(message);
    }

    public BackupRestoreException(String message, Throwable cause) {
        super(message, cause);
    }
}
