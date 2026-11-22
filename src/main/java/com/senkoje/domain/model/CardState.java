package com.senkoje.domain.model;

import java.util.Locale;

/**
 * Trạng thái học tập hiện tại của một thẻ trong vòng đời lập lịch FSRS-6.
 */
public enum CardState {
    NEW,
    LEARNING,
    REVIEW,
    RELEARNING;

    /**
     * Chuyển đổi mã chuỗi sang giá trị enum CardState tương ứng.
     *
     * @param code mã trạng thái thẻ
     * @return giá trị CardState hợp lệ
     */
    public static CardState fromCode(String code) {
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("Card state code must not be blank");
        }
        return CardState.valueOf(code.trim().toUpperCase(Locale.ROOT));
    }
}
