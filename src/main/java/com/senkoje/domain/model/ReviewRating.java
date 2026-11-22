package com.senkoje.domain.model;

/**
 * Mức đánh giá mức độ ghi nhớ của người dùng sau mỗi lượt ôn tập FSRS-6.
 */
public enum ReviewRating {
    AGAIN(1),
    HARD(2),
    GOOD(3),
    EASY(4);

    private final int value;

    ReviewRating(int value) {
        this.value = value;
    }

    /**
     * Trả về giá trị số nguyên từ 1 đến 4 tương ứng với đặc tả FSRS-6.
     *
     * @return giá trị đánh giá số nguyên
     */
    public int getValue() {
        return value;
    }

    /**
     * Chuyển đổi giá trị số nguyên sang enum ReviewRating tương ứng.
     *
     * @param value giá trị đánh giá trong khoảng 1-4
     * @return giá trị ReviewRating hợp lệ
     */
    public static ReviewRating fromValue(int value) {
        for (ReviewRating rating : values()) {
            if (rating.value == value) {
                return rating;
            }
        }
        throw new IllegalArgumentException("Unsupported review rating value: " + value);
    }
}
