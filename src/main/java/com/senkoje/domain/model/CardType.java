package com.senkoje.domain.model;

import java.util.Locale;

/**
 * Phân loại nội dung của thẻ học tập trong hệ thống SenkoJE.
 */
public enum CardType {
    VOCABULARY,
    PHRASE,
    GRAMMAR;

    /**
     * Chuyển đổi mã chuỗi sang giá trị enum CardType tương ứng.
     *
     * @param code mã loại thẻ
     * @return giá trị CardType hợp lệ
     */
    public static CardType fromCode(String code) {
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("Card type code must not be blank");
        }
        return CardType.valueOf(code.trim().toUpperCase(Locale.ROOT));
    }
}
