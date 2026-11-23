package com.senkoje.application.dto;

/**
 * Trạng thái vòng đời của một phiên ôn tập thẻ ghi nhớ.
 */
public enum StudySessionStatus {
    // Phiên học đang hoạt động và sẵn sàng nhận đánh giá
    ACTIVE,
    // Phiên học đang tạm dừng, thời gian trôi qua không được cộng vào thời lượng học
    PAUSED,
    // Phiên học đã hoàn tất hoặc người học đã thoát giữa chừng
    COMPLETED
}
