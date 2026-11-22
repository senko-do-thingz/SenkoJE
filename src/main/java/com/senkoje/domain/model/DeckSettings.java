package com.senkoje.domain.model;

/**
 * Cấu hình thuật toán lập lịch FSRS-6 và giới hạn học hằng ngày cho từng bộ thẻ.
 *
 * @param deckId mã định danh bộ thẻ sở hữu cấu hình
 * @param desiredRetention xác suất ghi nhớ mục tiêu
 * @param maximumInterval khoảng cách ôn tập tối đa tính theo ngày
 * @param newCardsPerDay số thẻ mới tối đa mỗi ngày
 * @param maximumReviewsPerDay số lượt ôn tập tối đa mỗi ngày
 * @param learningSteps chuỗi cấu hình các bước học ngắn hạn (ví dụ "1m,10m")
 * @param relearningSteps chuỗi cấu hình các bước học lại sau khi quên (ví dụ "10m")
 * @param enableFuzzing bật hoặc tắt độ nhiễu phân tán ngày ôn tập
 */
public record DeckSettings(
        Long deckId,
        double desiredRetention,
        int maximumInterval,
        int newCardsPerDay,
        int maximumReviewsPerDay,
        String learningSteps,
        String relearningSteps,
        boolean enableFuzzing
) {

    public static final double DEFAULT_DESIRED_RETENTION = 0.90;
    public static final int DEFAULT_MAXIMUM_INTERVAL = 36500;
    public static final int DEFAULT_NEW_CARDS_PER_DAY = 20;
    public static final int DEFAULT_MAXIMUM_REVIEWS_PER_DAY = 200;
    public static final String DEFAULT_LEARNING_STEPS = "1m,10m";
    public static final String DEFAULT_RELEARNING_STEPS = "10m";
    public static final boolean DEFAULT_ENABLE_FUZZING = true;

    /**
     * Khởi tạo bộ cấu hình mặc định an toàn cho một bộ thẻ mới.
     *
     * @param deckId mã bộ thẻ tương ứng (có thể null trước khi lưu)
     * @return cấu hình DeckSettings mặc định
     */
    public static DeckSettings defaults(Long deckId) {
        return new DeckSettings(
                deckId,
                DEFAULT_DESIRED_RETENTION,
                DEFAULT_MAXIMUM_INTERVAL,
                DEFAULT_NEW_CARDS_PER_DAY,
                DEFAULT_MAXIMUM_REVIEWS_PER_DAY,
                DEFAULT_LEARNING_STEPS,
                DEFAULT_RELEARNING_STEPS,
                DEFAULT_ENABLE_FUZZING
        );
    }
}
