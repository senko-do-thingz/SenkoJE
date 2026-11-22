package com.senkoje.application.dto;

import java.time.Instant;

/**
 * Bản tóm tắt một phiên học gần đây dùng để hiển thị trên màn hình Dashboard.
 *
 * @param sessionId mã định danh phiên học
 * @param deckId mã bộ thẻ liên quan (có thể null)
 * @param deckTitle tên bộ thẻ đã phân giải (có thể null nếu phiên học chung hoặc bộ thẻ đã xóa)
 * @param startedAt thời điểm bắt đầu phiên học
 * @param completedAt thời điểm kết thúc phiên học (có thể null)
 * @param cardsReviewed số thẻ đã ôn tập trong phiên
 * @param durationSeconds tổng thời lượng học tính bằng giây
 */
public record RecentSessionSummary(
        long sessionId,
        Long deckId,
        String deckTitle,
        Instant startedAt,
        Instant completedAt,
        int cardsReviewed,
        int durationSeconds
) {
}
