package com.senkoje.domain.fsrs;

import com.senkoje.domain.model.ReviewRating;
import java.time.Instant;
import java.util.Objects;
import java.util.SplittableRandom;

/**
 * Bộ tạo độ nhiễu khoảng cách ôn tập tất định theo phạm vi phân tán chuẩn của FSRS-6
 * nhằm giảm tình trạng dồn thẻ vào cùng một ngày nhưng vẫn đảm bảo tái lập hoàn toàn khi kiểm thử.
 */
public final class FsrsFuzzer {

    private static final double MIN_FUZZABLE_INTERVAL = 2.5;

    /**
     * Tính cặp biên dưới và biên trên [minInterval, maxInterval] cho khoảng cách ôn tập sau khi áp dụng nhiễu.
     *
     * @param intervalDays khoảng cách gốc tính theo ngày
     * @param maximumInterval giới hạn khoảng cách tối đa theo cấu hình bộ thẻ
     * @return mảng 2 phần tử gồm biên dưới và biên trên hợp lệ
     */
    public int[] computeBounds(int intervalDays, int maximumInterval) {
        if (maximumInterval < 1) {
            throw new IllegalArgumentException("Maximum interval must be at least 1 day");
        }
        int clampedInterval = Math.min(maximumInterval, Math.max(1, intervalDays));
        if (clampedInterval < MIN_FUZZABLE_INTERVAL) {
            return new int[]{clampedInterval, clampedInterval};
        }

        // Tính biên độ nhiễu theo ba đoạn chuẩn của FSRS: [2.5, 7.0), [7.0, 20.0) và [20.0, +inf)
        double delta = 1.0;
        delta += 0.15 * Math.max(Math.min(clampedInterval, 7.0) - 2.5, 0.0);
        delta += 0.10 * Math.max(Math.min(clampedInterval, 20.0) - 7.0, 0.0);
        delta += 0.05 * Math.max(clampedInterval - 20.0, 0.0);

        int minInterval = (int) Math.round(clampedInterval - delta);
        int maxInterval = (int) Math.round(clampedInterval + delta);
        minInterval = Math.max(2, minInterval);
        maxInterval = Math.min(maxInterval, maximumInterval);
        minInterval = Math.min(minInterval, maxInterval);

        return new int[]{minInterval, maxInterval};
    }

    /**
     * Áp dụng độ nhiễu tất định cho khoảng cách ôn tập dựa trên thông tin ngữ cảnh của lượt ôn tập.
     *
     * @param intervalDays khoảng cách gốc tính theo ngày
     * @param maximumInterval khoảng cách tối đa cho phép
     * @param cardId mã thẻ (có thể null khi xem trước thẻ chưa lưu)
     * @param reviewedAt thời điểm ôn tập
     * @param repetitions số lần ôn tập trước đó
     * @param rating mức đánh giá của lượt ôn tập
     * @return khoảng cách ôn tập sau khi áp dụng độ nhiễu tất định
     */
    public int fuzzInterval(
            int intervalDays,
            int maximumInterval,
            Long cardId,
            Instant reviewedAt,
            int repetitions,
            ReviewRating rating
    ) {
        Objects.requireNonNull(reviewedAt, "reviewedAt must not be null");
        Objects.requireNonNull(rating, "rating must not be null");

        int[] bounds = computeBounds(intervalDays, maximumInterval);
        int minInterval = bounds[0];
        int maxInterval = bounds[1];
        if (minInterval >= maxInterval) {
            return minInterval;
        }

        long seed = deriveDeterministicSeed(cardId, reviewedAt, repetitions, intervalDays, rating);
        SplittableRandom random = new SplittableRandom(seed);
        return random.nextInt(minInterval, maxInterval + 1);
    }

    private long deriveDeterministicSeed(
            Long cardId,
            Instant reviewedAt,
            int repetitions,
            int intervalDays,
            ReviewRating rating
    ) {
        long seed = 0x9E3779B97F4A7C15L;
        seed = mix64(seed ^ (cardId == null ? 0L : cardId));
        seed = mix64(seed ^ reviewedAt.toEpochMilli());
        seed = mix64(seed ^ (((long) repetitions << 32) | (intervalDays & 0xFFFFFFFFL)));
        seed = mix64(seed ^ rating.getValue());
        return seed;
    }

    private long mix64(long value) {
        long z = value + 0x9E3779B97F4A7C15L;
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }
}
