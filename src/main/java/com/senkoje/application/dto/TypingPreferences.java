package com.senkoje.application.dto;

import com.senkoje.domain.service.TypingEvaluationOptions;

/**
 * Cấu hình luyện gõ chính tả / nghe chép (Dictation) được lưu cục bộ trong app_settings.
 *
 * @param caseInsensitiveEnglish không phân biệt hoa thường với tiếng Anh
 * @param ignoreEnglishPunctuation bỏ qua dấu câu cơ bản với tiếng Anh
 * @param strictCharacterMatching bật chế độ khớp ký tự nghiêm ngặt
 */
public record TypingPreferences(
        boolean caseInsensitiveEnglish,
        boolean ignoreEnglishPunctuation,
        boolean strictCharacterMatching
) {

    /**
     * Chuyển đổi cấu hình ứng dụng sang tùy chọn chấm bài cho tầng miền TypingEvaluator.
     *
     * @return đối tượng TypingEvaluationOptions tương ứng
     */
    public TypingEvaluationOptions toEvaluationOptions() {
        return new TypingEvaluationOptions(
                caseInsensitiveEnglish,
                ignoreEnglishPunctuation,
                strictCharacterMatching
        );
    }
}
