package com.senkoje.application.service;

/**
 * Ngoại lệ miền ứng dụng được ném ra khi bộ thẻ đã đạt giới hạn thẻ mới hoặc giới hạn ôn tập trong ngày.
 */
public class DailyLimitExceededException extends RuntimeException {

    /**
     * Khởi tạo ngoại lệ vượt quá hạn mức hằng ngày với thông điệp mô tả chi tiết.
     *
     * @param message mô tả loại giới hạn đã chạm ngưỡng
     */
    public DailyLimitExceededException(String message) {
        super(message);
    }
}
