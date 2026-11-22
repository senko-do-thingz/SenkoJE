package com.senkoje.domain.model;

import java.util.Locale;

/**
 * Ngôn ngữ đích mà người dùng đang học trong từng bộ thẻ.
 */
public enum TargetLanguage {
    JA,
    EN;

    /**
     * Chuyển đổi mã chuỗi sang giá trị enum TargetLanguage tương ứng.
     *
     * @param code mã ngôn ngữ đích (JA hoặc EN)
     * @return giá trị TargetLanguage hợp lệ
     */
    public static TargetLanguage fromCode(String code) {
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("Target language code must not be blank");
        }
        return TargetLanguage.valueOf(code.trim().toUpperCase(Locale.ROOT));
    }
}
