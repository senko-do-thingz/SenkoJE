package com.senkoje.domain.repository;

/**
 * Ngoại lệ không kiểm tra bọc lỗi tầng lưu trữ để không làm rò rỉ chi tiết JDBC vào tầng miền.
 */
public class PersistenceException extends RuntimeException {

    /**
     * Khởi tạo ngoại lệ lưu trữ với thông báo lỗi cụ thể.
     *
     * @param message mô tả lỗi xảy ra ở tầng lưu trữ
     */
    public PersistenceException(String message) {
        super(message);
    }

    /**
     * Khởi tạo ngoại lệ lưu trữ kèm nguyên nhân gốc từ tầng cơ sở dữ liệu.
     *
     * @param message mô tả ngữ cảnh thao tác thất bại
     * @param cause ngoại lệ gốc gây ra lỗi
     */
    public PersistenceException(String message, Throwable cause) {
        super(message, cause);
    }
}
