package com.senkoje.domain.repository;

import com.senkoje.domain.model.ReviewLog;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Giao diện kho lưu trữ chỉ ghi thêm cho nhật ký ôn tập bất biến.
 */
public interface ReviewLogRepository {

    /**
     * Ghi thêm một bản ghi nhật ký ôn tập mới vào lịch sử bất biến.
     *
     * @param reviewLog bản ghi nhật ký cần lưu
     * @return bản ghi nhật ký đã lưu kèm mã định danh sinh tự động
     */
    ReviewLog append(ReviewLog reviewLog);

    /**
     * Tìm bản ghi nhật ký ôn tập theo mã định danh.
     *
     * @param id mã bản ghi nhật ký
     * @return bản ghi nhật ký nếu tồn tại
     */
    Optional<ReviewLog> findById(long id);

    /**
     * Lấy toàn bộ lịch sử ôn tập của một thẻ theo thứ tự thời gian tăng dần.
     *
     * @param cardId mã thẻ
     * @return danh sách nhật ký ôn tập của thẻ
     */
    List<ReviewLog> findByCardId(long cardId);

    /**
     * Lấy toàn bộ nhật ký ôn tập thuộc một phiên học cụ thể.
     *
     * @param sessionId mã phiên học
     * @return danh sách nhật ký ôn tập trong phiên học
     */
    List<ReviewLog> findBySessionId(long sessionId);

    /**
     * Lấy toàn bộ nhật ký ôn tập trong một khoảng thời gian xác định.
     *
     * @param startInclusive thời điểm bắt đầu (bao gồm)
     * @param endInclusive thời điểm kết thúc (bao gồm)
     * @return danh sách nhật ký ôn tập trong khoảng thời gian
     */
    List<ReviewLog> findByReviewedAtBetween(Instant startInclusive, Instant endInclusive);

    /**
     * Đếm số lượt giới thiệu thẻ mới (trạng thái trước ôn tập là NEW) của một bộ thẻ trong khoảng thời gian.
     *
     * @param deckId mã bộ thẻ
     * @param startInclusive thời điểm bắt đầu (bao gồm)
     * @param endExclusive thời điểm kết thúc (không bao gồm)
     * @return số thẻ mới đã học trong khoảng thời gian
     */
    int countNewCardsReviewedBetween(long deckId, Instant startInclusive, Instant endExclusive);

    /**
     * Đếm số lượt ôn tập thẻ dài hạn (trạng thái trước ôn tập là REVIEW) của một bộ thẻ trong khoảng thời gian.
     *
     * @param deckId mã bộ thẻ
     * @param startInclusive thời điểm bắt đầu (bao gồm)
     * @param endExclusive thời điểm kết thúc (không bao gồm)
     * @return số lượt ôn tập dài hạn đã thực hiện trong khoảng thời gian
     */
    int countReviewsCompletedBetween(long deckId, Instant startInclusive, Instant endExclusive);
}
