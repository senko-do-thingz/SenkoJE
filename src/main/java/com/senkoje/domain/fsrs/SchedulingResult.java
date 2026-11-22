package com.senkoje.domain.fsrs;

import com.senkoje.domain.model.ReviewLog;
import com.senkoje.domain.model.ReviewState;
import java.time.Duration;
import java.util.Objects;

/**
 * Kết quả bất biến của một phép tính lập lịch FSRS-6 gồm trạng thái tiếp theo,
 * bản ghi nhật ký ôn tập tương ứng và khoảng thời gian đến lần ôn kế tiếp.
 *
 * @param nextState trạng thái lập lịch FSRS-6 mới của thẻ
 * @param reviewLog bản ghi nhật ký ôn tập bất biến cần lưu
 * @param scheduledInterval khoảng thời gian chờ đến thời điểm đến hạn tiếp theo
 */
public record SchedulingResult(
        ReviewState nextState,
        ReviewLog reviewLog,
        Duration scheduledInterval
) {

    /**
     * Đảm bảo các thành phần kết quả lập lịch không bị null hoặc âm.
     */
    public SchedulingResult {
        Objects.requireNonNull(nextState, "nextState must not be null");
        Objects.requireNonNull(reviewLog, "reviewLog must not be null");
        Objects.requireNonNull(scheduledInterval, "scheduledInterval must not be null");
        if (scheduledInterval.isNegative()) {
            throw new IllegalArgumentException("scheduledInterval must not be negative");
        }
    }
}
