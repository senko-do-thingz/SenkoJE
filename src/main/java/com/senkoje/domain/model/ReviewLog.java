package com.senkoje.domain.model;

import java.time.Instant;

/**
 * Bản ghi nhật ký bất biến lưu lại kết quả của một lượt ôn tập đã hoàn thành.
 *
 * @param id mã định danh duy nhất của bản ghi nhật ký (có thể null khi chưa lưu)
 * @param cardId mã thẻ được ôn tập
 * @param sessionId mã phiên học liên quan (có thể null)
 * @param rating mức đánh giá từ 1 đến 4 của người dùng
 * @param reviewedAt thời điểm thực hiện lượt ôn tập
 * @param responseTimeMs thời gian phản hồi tính bằng mili giây (có thể null)
 * @param previousState trạng thái thẻ trước khi ôn tập
 * @param nextState trạng thái thẻ sau khi ôn tập
 * @param previousStability độ ổn định trí nhớ trước lượt ôn tập
 * @param nextStability độ ổn định trí nhớ sau lượt ôn tập
 * @param previousDifficulty độ khó trước lượt ôn tập
 * @param nextDifficulty độ khó sau lượt ôn tập
 * @param elapsedDays số ngày thực tế đã trôi qua kể từ lần ôn tập trước
 * @param scheduledDays số ngày lập lịch cho lần ôn tập tiếp theo
 * @param algorithmVersion phiên bản thuật toán FSRS đã sử dụng
 * @param parameterVersion phiên bản bộ tham số FSRS đã sử dụng
 */
public record ReviewLog(
        Long id,
        Long cardId,
        Long sessionId,
        ReviewRating rating,
        Instant reviewedAt,
        Integer responseTimeMs,
        CardState previousState,
        CardState nextState,
        double previousStability,
        double nextStability,
        double previousDifficulty,
        double nextDifficulty,
        double elapsedDays,
        double scheduledDays,
        String algorithmVersion,
        String parameterVersion
) {
}
