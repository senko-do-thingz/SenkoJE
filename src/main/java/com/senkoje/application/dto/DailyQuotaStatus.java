package com.senkoje.application.dto;

/**
 * Trạng thái hạn mức học thẻ mới và ôn tập hằng ngày của một bộ thẻ.
 *
 * @param deckId mã bộ thẻ
 * @param newCardsReviewedToday số thẻ mới đã học trong ngày hiện tại
 * @param newCardsLimit giới hạn số thẻ mới mỗi ngày
 * @param reviewsCompletedToday số lượt ôn tập thẻ dài hạn đã hoàn thành trong ngày hiện tại
 * @param reviewsLimit giới hạn số lượt ôn tập mỗi ngày
 */
public record DailyQuotaStatus(
        long deckId,
        int newCardsReviewedToday,
        int newCardsLimit,
        int reviewsCompletedToday,
        int reviewsLimit
) {

    /**
     * Trả về số lượng thẻ mới còn lại có thể học trong ngày.
     *
     * @return số thẻ mới còn lại không âm
     */
    public int remainingNewCards() {
        return Math.max(0, newCardsLimit - newCardsReviewedToday);
    }

    /**
     * Trả về số lượt ôn tập thẻ dài hạn còn lại trong ngày.
     *
     * @return số lượt ôn tập còn lại không âm
     */
    public int remainingReviews() {
        return Math.max(0, reviewsLimit - reviewsCompletedToday);
    }
}
