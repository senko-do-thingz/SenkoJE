package com.senkoje.domain.model;

import java.util.Locale;

/**
 * Ngôn ngữ dùng để giải nghĩa và dịch câu ví dụ của thẻ học tập.
 */
public enum DefinitionLanguage {
    VI("vi"),
    EN("en");

    private final String code;

    DefinitionLanguage(String code) {
        this.code = code;
    }

    /**
     * Trả về mã ngôn ngữ viết thường dùng để lưu trữ trong cơ sở dữ liệu.
     *
     * @return mã ngôn ngữ ("vi" hoặc "en")
     */
    public String getCode() {
        return code;
    }

    /**
     * Chuyển đổi mã chuỗi sang giá trị enum DefinitionLanguage tương ứng.
     *
     * @param code mã ngôn ngữ giải nghĩa ("vi" hoặc "en")
     * @return giá trị DefinitionLanguage hợp lệ
     */
    public static DefinitionLanguage fromCode(String code) {
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("Definition language code must not be blank");
        }
        String normalized = code.trim().toLowerCase(Locale.ROOT);
        for (DefinitionLanguage language : values()) {
            if (language.code.equals(normalized)) {
                return language;
            }
        }
        throw new IllegalArgumentException("Unsupported definition language: " + code);
    }
}
