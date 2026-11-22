package com.senkoje.domain.fsrs;

import com.senkoje.domain.model.ReviewRating;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/**
 * Bộ công thức toán học cốt lõi của thuật toán FSRS-6 để tính xác suất nhớ lại,
 * độ ổn định, độ khó và khoảng cách ôn tập tiếp theo một cách thuần túy và tất định.
 */
public final class FsrsMath {

    private static final double MILLIS_PER_DAY = 86_400_000.0;

    private final FsrsParameters parameters;

    /**
     * Khởi tạo bộ công thức FSRS-6 với bộ tham số chỉ định.
     *
     * @param parameters bộ tham số FSRS-6
     */
    public FsrsMath(FsrsParameters parameters) {
        this.parameters = Objects.requireNonNull(parameters, "parameters must not be null");
    }

    /**
     * Trả về bộ tham số FSRS-6 đang được sử dụng.
     *
     * @return bộ tham số FSRS-6
     */
    public FsrsParameters parameters() {
        return parameters;
    }

    /**
     * Tính số ngày thực tế đã trôi qua giữa hai mốc thời gian tường minh.
     *
     * @param lastReviewedAt thời điểm ôn tập trước đó (có thể null với thẻ mới)
     * @param reviewedAt thời điểm ôn tập hiện tại
     * @return số ngày đã trôi qua không âm (dạng số thực)
     */
    public double elapsedDays(Instant lastReviewedAt, Instant reviewedAt) {
        Objects.requireNonNull(reviewedAt, "reviewedAt must not be null");
        if (lastReviewedAt == null || !reviewedAt.isAfter(lastReviewedAt)) {
            return 0.0;
        }
        long elapsedMillis = Duration.between(lastReviewedAt, reviewedAt).toMillis();
        return elapsedMillis / MILLIS_PER_DAY;
    }

    /**
     * Tính xác suất nhớ lại R(t, S) theo đường cong quên hàm lũy thừa của FSRS-6.
     *
     * @param elapsedDays số ngày đã trôi qua kể từ lần ôn tập gần nhất
     * @param stability độ ổn định trí nhớ hiện tại tính theo ngày
     * @return xác suất nhớ lại trong đoạn [0.0, 1.0]
     */
    public double retrievability(double elapsedDays, double stability) {
        if (!Double.isFinite(elapsedDays) || !Double.isFinite(stability) || stability <= 0.0) {
            return 0.0;
        }
        double safeElapsed = Math.max(0.0, elapsedDays);
        return Math.pow(1.0 + parameters.factor() * safeElapsed / stability, parameters.decay());
    }

    /**
     * Tính độ ổn định khởi đầu S0(G) cho lần ôn tập đầu tiên của một thẻ mới.
     *
     * @param rating mức đánh giá từ 1 đến 4
     * @return độ ổn định khởi đầu không nhỏ hơn ngưỡng tối thiểu
     */
    public double initialStability(ReviewRating rating) {
        Objects.requireNonNull(rating, "rating must not be null");
        return clampStability(parameters.weight(rating.getValue() - 1));
    }

    /**
     * Tính độ khó khởi đầu D0(G) cho lần ôn tập đầu tiên và giới hạn trong [1.0, 10.0].
     *
     * @param rating mức đánh giá từ 1 đến 4
     * @return độ khó khởi đầu hợp lệ
     */
    public double initialDifficulty(ReviewRating rating) {
        Objects.requireNonNull(rating, "rating must not be null");
        double raw = parameters.weight(4)
                - Math.exp(parameters.weight(5) * (rating.getValue() - 1))
                + 1.0;
        return clampDifficulty(raw);
    }

    /**
     * Tính cặp trạng thái trí nhớ khởi đầu (S0, D0) cho một thẻ mới.
     *
     * @param rating mức đánh giá lần đầu
     * @return trạng thái trí nhớ khởi đầu
     */
    public FsrsMemoryState initialMemoryState(ReviewRating rating) {
        return new FsrsMemoryState(initialStability(rating), initialDifficulty(rating));
    }

    /**
     * Cập nhật độ khó D'(D, G) với giảm chấn tuyến tính và hồi quy trung bình về D0(Easy).
     *
     * @param currentDifficulty độ khó hiện tại
     * @param rating mức đánh giá của lượt ôn tập
     * @return độ khó mới nằm trong đoạn [1.0, 10.0]
     */
    public double nextDifficulty(double currentDifficulty, ReviewRating rating) {
        Objects.requireNonNull(rating, "rating must not be null");
        double deltaDifficulty = -parameters.weight(6) * (rating.getValue() - 3.0);
        // Áp dụng giảm chấn tuyến tính để độ khó không thay đổi quá đột ngột khi gần biên trên
        double dampedDifficulty = currentDifficulty + deltaDifficulty * (10.0 - currentDifficulty) / 9.0;
        // Áp dụng hồi quy trung bình về độ khó khởi đầu của mức Easy theo chuẩn FSRS-6
        double meanReverted = parameters.weight(7) * initialDifficulty(ReviewRating.EASY)
                + (1.0 - parameters.weight(7)) * dampedDifficulty;
        return clampDifficulty(meanReverted);
    }

    /**
     * Cập nhật độ ổn định ngắn hạn khi hai lượt ôn tập diễn ra trong cùng một ngày (t < 1 ngày).
     *
     * @param currentStability độ ổn định hiện tại
     * @param rating mức đánh giá của lượt ôn tập
     * @return độ ổn định mới sau cập nhật ngắn hạn
     */
    public double shortTermStability(double currentStability, ReviewRating rating) {
        Objects.requireNonNull(rating, "rating must not be null");
        double safeStability = clampStability(currentStability);
        double increase = Math.exp(
                parameters.weight(17) * (rating.getValue() - 3.0 + parameters.weight(18))
        ) * Math.pow(safeStability, -parameters.weight(19));
        // Với đánh giá Good hoặc Easy trong ngày, hệ số tăng trưởng ngắn hạn không giảm dưới 1.0
        if (rating == ReviewRating.GOOD || rating == ReviewRating.EASY) {
            increase = Math.max(1.0, increase);
        }
        return clampStability(safeStability * increase);
    }

    /**
     * Cập nhật độ ổn định sau khi nhớ lại thành công (Hard, Good hoặc Easy) với t >= 1 ngày.
     *
     * @param difficulty độ khó hiện tại trước khi cập nhật
     * @param stability độ ổn định hiện tại
     * @param retrievability xác suất nhớ lại tại thời điểm ôn tập
     * @param rating mức đánh giá (HARD, GOOD hoặc EASY)
     * @return độ ổn định mới sau khi nhớ thành công
     */
    public double nextRecallStability(
            double difficulty,
            double stability,
            double retrievability,
            ReviewRating rating
    ) {
        Objects.requireNonNull(rating, "rating must not be null");
        if (rating == ReviewRating.AGAIN) {
            throw new IllegalArgumentException("Recall stability requires rating HARD, GOOD, or EASY");
        }
        double safeStability = clampStability(stability);
        double hardPenalty = rating == ReviewRating.HARD ? parameters.weight(15) : 1.0;
        double easyBonus = rating == ReviewRating.EASY ? parameters.weight(16) : 1.0;

        double growthFactor = Math.exp(parameters.weight(8))
                * (11.0 - difficulty)
                * Math.pow(safeStability, -parameters.weight(9))
                * (Math.exp((1.0 - retrievability) * parameters.weight(10)) - 1.0)
                * hardPenalty
                * easyBonus;
        return clampStability(safeStability * (1.0 + growthFactor));
    }

    /**
     * Cập nhật độ ổn định sau khi quên thẻ (Again) với giới hạn trên từ mô hình ngắn hạn.
     *
     * @param difficulty độ khó hiện tại trước khi cập nhật
     * @param stability độ ổn định hiện tại
     * @param retrievability xác suất nhớ lại tại thời điểm ôn tập
     * @return độ ổn định mới sau khi quên thẻ
     */
    public double nextForgetStability(double difficulty, double stability, double retrievability) {
        double safeStability = clampStability(stability);
        double longTermForget = parameters.weight(11)
                * Math.pow(difficulty, -parameters.weight(12))
                * (Math.pow(safeStability + 1.0, parameters.weight(13)) - 1.0)
                * Math.exp((1.0 - retrievability) * parameters.weight(14));
        double shortTermCap = safeStability / Math.exp(parameters.weight(17) * parameters.weight(18));
        return clampStability(Math.min(longTermForget, shortTermCap));
    }

    /**
     * Tính trạng thái trí nhớ tiếp theo (S', D') dựa trên trạng thái hiện tại, số ngày trôi qua và đánh giá.
     *
     * @param current trạng thái trí nhớ hiện tại (nếu stability <= 0 sẽ coi như thẻ mới)
     * @param elapsedDays số ngày đã trôi qua kể từ lần ôn tập trước
     * @param rating mức đánh giá của người học
     * @return trạng thái trí nhớ mới sau lượt ôn tập
     */
    public FsrsMemoryState nextMemoryState(FsrsMemoryState current, double elapsedDays, ReviewRating rating) {
        Objects.requireNonNull(rating, "rating must not be null");
        if (current == null || current.stability() <= 0.0) {
            return initialMemoryState(rating);
        }

        double safeElapsed = Math.max(0.0, elapsedDays);
        double nextStability;
        if (safeElapsed < 1.0) {
            nextStability = shortTermStability(current.stability(), rating);
        } else {
            double r = retrievability(safeElapsed, current.stability());
            if (rating == ReviewRating.AGAIN) {
                nextStability = nextForgetStability(current.difficulty(), current.stability(), r);
            } else {
                nextStability = nextRecallStability(current.difficulty(), current.stability(), r, rating);
            }
        }
        double nextDifficulty = nextDifficulty(current.difficulty(), rating);
        return new FsrsMemoryState(nextStability, nextDifficulty);
    }

    /**
     * Tính khoảng cách ôn tập tiếp theo (số ngày nguyên) từ độ ổn định và xác suất ghi nhớ mục tiêu.
     *
     * @param stability độ ổn định trí nhớ tính theo ngày
     * @param desiredRetention xác suất ghi nhớ mục tiêu trong khoảng (0, 1)
     * @param maximumInterval giới hạn khoảng cách tối đa tính theo ngày
     * @return khoảng cách ôn tập nguyên nằm trong đoạn [1, maximumInterval]
     */
    public int nextIntervalDays(double stability, double desiredRetention, int maximumInterval) {
        if (!Double.isFinite(stability) || stability <= 0.0) {
            throw new IllegalArgumentException("Stability must be finite and positive");
        }
        if (!Double.isFinite(desiredRetention) || desiredRetention <= 0.0 || desiredRetention >= 1.0) {
            throw new IllegalArgumentException("Desired retention must be strictly between 0 and 1");
        }
        if (maximumInterval < 1) {
            throw new IllegalArgumentException("Maximum interval must be at least 1 day");
        }

        double rawInterval = (stability / parameters.factor())
                * (Math.pow(desiredRetention, 1.0 / parameters.decay()) - 1.0);
        long rounded = Math.round(rawInterval);
        return (int) Math.min(maximumInterval, Math.max(1L, rounded));
    }

    private double clampStability(double value) {
        return Math.max(FsrsParameters.STABILITY_MIN, value);
    }

    private double clampDifficulty(double value) {
        return Math.min(FsrsParameters.MAX_DIFFICULTY, Math.max(FsrsParameters.MIN_DIFFICULTY, value));
    }
}
