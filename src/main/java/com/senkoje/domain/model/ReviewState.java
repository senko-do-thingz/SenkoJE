package com.senkoje.domain.model;

import java.time.Instant;

/**
 * Trạng thái lập lịch FSRS-6 hiện tại của một thẻ học tập.
 *
 * @param cardId mã thẻ tương ứng
 * @param state trạng thái hiện tại trong vòng đời FSRS-6
 * @param stability độ ổn định trí nhớ
 * @param difficulty độ khó ước lượng của thẻ
 * @param step chỉ số bước học ngắn hạn hiện tại (có thể null)
 * @param dueAt thời điểm đến hạn ôn tập tiếp theo (có thể null)
 * @param lastReviewedAt thời điểm ôn tập gần nhất (có thể null)
 * @param scheduledDays khoảng cách ôn tập đã lên lịch tính theo ngày
 * @param elapsedDays khoảng thời gian thực tế đã trôi qua tính theo ngày
 * @param repetitions tổng số lần đã ôn tập
 * @param lapses tổng số lần quên thẻ
 * @param updatedAt thời điểm cập nhật trạng thái gần nhất
 */
public record ReviewState(
        Long cardId,
        CardState state,
        double stability,
        double difficulty,
        Integer step,
        Instant dueAt,
        Instant lastReviewedAt,
        double scheduledDays,
        double elapsedDays,
        int repetitions,
        int lapses,
        Instant updatedAt
) {

    /**
     * Tạo trạng thái FSRS-6 khởi đầu NEW cho một thẻ vừa được tạo hoặc nhân bản.
     *
     * @param cardId mã thẻ tương ứng
     * @param now thời điểm khởi tạo trạng thái
     * @return trạng thái ReviewState khởi đầu ở mức NEW
     */
    public static ReviewState initialNew(Long cardId, Instant now) {
        return new ReviewState(
                cardId,
                CardState.NEW,
                0.0,
                0.0,
                null,
                null,
                null,
                0.0,
                0.0,
                0,
                0,
                now
        );
    }
}
