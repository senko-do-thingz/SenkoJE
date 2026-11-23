package com.senkoje.domain.service;

/**
 * Tùy chọn kiểm tra chính tả và gõ đáp án cho chế độ luyện nghe chép (Dictation).
 *
 * @param caseInsensitiveEnglish bỏ qua chữ hoa/chữ thường khi chấm đáp án tiếng Anh
 * @param ignoreEnglishPunctuation bỏ qua dấu câu cơ bản khi chấm đáp án tiếng Anh
 * @param strictCharacterMatching bật chế độ khớp từng ký tự nghiêm ngặt (ưu tiên cao nhất)
 */
public record TypingEvaluationOptions(
        boolean caseInsensitiveEnglish,
        boolean ignoreEnglishPunctuation,
        boolean strictCharacterMatching
) {

    /**
     * Trả về cấu hình chấm bài mặc định: tiếng Anh không phân biệt hoa thường,
     * bỏ qua dấu câu cơ bản, tắt chế độ khớp ký tự nghiêm ngặt.
     *
     * @return cấu hình mặc định
     */
    public static TypingEvaluationOptions defaults() {
        return new TypingEvaluationOptions(true, true, false);
    }
}
