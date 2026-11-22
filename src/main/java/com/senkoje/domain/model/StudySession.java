package com.senkoje.domain.model;

import java.time.Instant;

/**
 * Thông tin một phiên học tập của người dùng trên một bộ thẻ hoặc toàn bộ hệ thống.
 *
 * @param id mã định danh duy nhất của phiên học (có thể null khi chưa lưu)
 * @param deckId mã bộ thẻ liên quan (có thể null)
 * @param startedAt thời điểm bắt đầu phiên học
 * @param completedAt thời điểm kết thúc phiên học (có thể null nếu đang học)
 * @param cardsReviewed số thẻ đã ôn tập trong phiên
 * @param durationSeconds tổng thời lượng phiên học tính bằng giây
 */
public record StudySession(
        Long id,
        Long deckId,
        Instant startedAt,
        Instant completedAt,
        int cardsReviewed,
        int durationSeconds
) {
}
