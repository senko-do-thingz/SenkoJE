package com.senkoje.domain.service;

import java.util.List;
import java.util.Objects;

/**
 * Kết quả bất biến sau khi chấm câu trả lời gõ tay của người học trong chế độ Dictation.
 *
 * @param correct true nếu câu trả lời khớp với một trong các đáp án được chấp nhận
 * @param normalizedInput chuỗi người học nhập sau khi chuẩn hóa theo quy tắc ngôn ngữ
 * @param matchedExpected đáp án gốc khớp được (null nếu trả lời sai)
 * @param acceptedAnswers danh sách toàn bộ đáp án hợp lệ của thẻ
 * @param feedbackCode mã phản hồi ngắn gọn phục vụ hiển thị thông điệp đa ngôn ngữ
 */
public record TypingEvaluationResult(
        boolean correct,
        String normalizedInput,
        String matchedExpected,
        List<String> acceptedAnswers,
        String feedbackCode
) {

    /**
     * Đảm bảo danh sách đáp án hợp lệ không bị null và bất biến.
     */
    public TypingEvaluationResult {
        normalizedInput = normalizedInput == null ? "" : normalizedInput;
        acceptedAnswers = acceptedAnswers == null ? List.of() : List.copyOf(acceptedAnswers);
        Objects.requireNonNull(feedbackCode, "feedbackCode must not be null");
    }
}
