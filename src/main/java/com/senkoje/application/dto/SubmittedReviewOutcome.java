package com.senkoje.application.dto;

import com.senkoje.domain.fsrs.SchedulingResult;
import java.util.Objects;

/**
 * Kết quả trả về sau khi ghi nhận một lượt đánh giá trong phiên học tập,
 * gồm kết quả lập lịch FSRS-6 và ảnh chụp trạng thái tiếp theo của phiên học.
 *
 * @param schedulingResult kết quả lập lịch FSRS-6 đã lưu kèm nhật ký ôn tập bất biến
 * @param sessionSnapshot ảnh chụp trạng thái phiên học ngay sau lượt đánh giá
 */
public record SubmittedReviewOutcome(
        SchedulingResult schedulingResult,
        StudySessionSnapshot sessionSnapshot
) {

    /**
     * Đảm bảo cả hai thành phần kết quả đều không bị null.
     */
    public SubmittedReviewOutcome {
        Objects.requireNonNull(schedulingResult, "schedulingResult must not be null");
        Objects.requireNonNull(sessionSnapshot, "sessionSnapshot must not be null");
    }
}
