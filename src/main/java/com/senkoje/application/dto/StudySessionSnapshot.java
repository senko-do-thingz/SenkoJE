package com.senkoje.application.dto;

import com.senkoje.domain.model.ReviewRating;
import com.senkoje.domain.model.StudySession;
import java.util.Map;
import java.util.Objects;

/**
 * Ảnh chụp trạng thái hiện tại của một phiên học tập để giao diện hiển thị
 * thẻ hiện tại, tiến độ hàng đợi, thời lượng hoạt động, mã tuần tự hiển thị và thống kê đánh giá.
 *
 * @param session bản ghi phiên học mới nhất đã lưu trong SQLite
 * @param status trạng thái vòng đời hiện tại của phiên học
 * @param currentCard thẻ đang cần ôn tập (null nếu phiên học đã hoàn tất)
 * @param presentationSeq số thứ tự tăng đơn điệu của lần hiển thị thẻ ở đầu hàng đợi (0 nếu đã kết thúc)
 * @param remainingCards số thẻ còn lại trong hàng đợi phiên học
 * @param completedReviews tổng số lượt đánh giá đã hoàn thành trong phiên
 * @param initialQueueSize số thẻ ban đầu khi khởi tạo phiên học
 * @param activeDurationSeconds tổng thời gian học thực tế tính bằng giây (không tính thời gian tạm dừng)
 * @param ratingBreakdown số lượt chọn cho từng mức đánh giá Again, Hard, Good, Easy
 */
public record StudySessionSnapshot(
        StudySession session,
        StudySessionStatus status,
        StudyCardItem currentCard,
        long presentationSeq,
        int remainingCards,
        int completedReviews,
        int initialQueueSize,
        int activeDurationSeconds,
        Map<ReviewRating, Integer> ratingBreakdown
) {

    /**
     * Đảm bảo các trường bắt buộc không bị null và bản đồ thống kê là bất biến.
     */
    public StudySessionSnapshot {
        Objects.requireNonNull(session, "session must not be null");
        Objects.requireNonNull(status, "status must not be null");
        ratingBreakdown = ratingBreakdown == null ? Map.of() : Map.copyOf(ratingBreakdown);
    }

    /**
     * Kiểm tra phiên học đã kết thúc hay chưa.
     *
     * @return true nếu phiên học ở trạng thái COMPLETED
     */
    public boolean isCompleted() {
        return status == StudySessionStatus.COMPLETED;
    }
}
