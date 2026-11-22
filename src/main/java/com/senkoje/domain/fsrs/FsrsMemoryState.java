package com.senkoje.domain.fsrs;

/**
 * Cặp trạng thái trí nhớ cốt lõi của FSRS-6 gồm độ ổn định (stability) và độ khó (difficulty).
 *
 * @param stability độ ổn định trí nhớ tính theo ngày (số ngày để xác suất nhớ giảm về 90%)
 * @param difficulty độ khó ước lượng của thẻ trong khoảng [1.0, 10.0]
 */
public record FsrsMemoryState(double stability, double difficulty) {

    /**
     * Kiểm tra tính hữu hạn và hợp lệ cơ bản của trạng thái trí nhớ FSRS-6.
     */
    public FsrsMemoryState {
        if (!Double.isFinite(stability) || stability < 0.0) {
            throw new IllegalArgumentException("Stability must be finite and non-negative");
        }
        if (!Double.isFinite(difficulty) || difficulty < 0.0) {
            throw new IllegalArgumentException("Difficulty must be finite and non-negative");
        }
    }
}
