package com.senkoje.domain.service;

import com.senkoje.domain.model.TargetLanguage;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Dịch vụ miền thuần Java dùng để chấm đáp án gõ tay trong chế độ Dictation / Typing Practice.
 * Đảm bảo quy tắc nghiêm ngặt cho tiếng Nhật (không tự động đánh đồng Kana với Kanji,
 * Hiragana với Katakana hay các cách đọc khác nhau nếu không được liệt kê tường minh)
 * và hỗ trợ tùy chọn hoa thường, dấu câu, khớp nghiêm ngặt cho tiếng Anh.
 */
public class TypingEvaluator {

    public static final String FEEDBACK_EXACT_MATCH = "dictation.feedback.exact";
    public static final String FEEDBACK_NORMALIZED_MATCH = "dictation.feedback.normalized";
    public static final String FEEDBACK_EMPTY_INPUT = "dictation.feedback.empty";
    public static final String FEEDBACK_MISMATCH = "dictation.feedback.mismatch";

    /**
     * Chấm câu trả lời người học gõ so với danh sách đáp án được phép của thẻ.
     *
     * @param targetLanguage ngôn ngữ đích của thẻ (JA hoặc EN)
     * @param userInput chuỗi người học đã nhập
     * @param explicitAcceptedAnswers danh sách đáp án hợp lệ được liệt kê tường minh trên thẻ
     * @param options tùy chọn chấm bài (có thể null để dùng mặc định)
     * @return kết quả chấm bài chi tiết
     */
    public TypingEvaluationResult evaluate(
            TargetLanguage targetLanguage,
            String userInput,
            List<String> explicitAcceptedAnswers,
            TypingEvaluationOptions options
    ) {
        Objects.requireNonNull(targetLanguage, "targetLanguage must not be null");
        TypingEvaluationOptions effectiveOptions = options == null ? TypingEvaluationOptions.defaults() : options;

        List<String> cleanedAccepted = sanitizeAcceptedAnswers(explicitAcceptedAnswers);
        if (cleanedAccepted.isEmpty()) {
            throw new IllegalArgumentException("At least one non-blank accepted answer is required");
        }

        String trimmedInput = trimUnicodeWhitespace(userInput);
        if (trimmedInput.isEmpty()) {
            return new TypingEvaluationResult(false, "", null, cleanedAccepted, FEEDBACK_EMPTY_INPUT);
        }

        String normalizedInput = normalizeForComparison(targetLanguage, trimmedInput, effectiveOptions);
        for (String expected : cleanedAccepted) {
            String trimmedExpected = trimUnicodeWhitespace(expected);
            String normalizedExpected = normalizeForComparison(targetLanguage, trimmedExpected, effectiveOptions);
            if (normalizedInput.equals(normalizedExpected)) {
                boolean exactRaw = toNfc(trimmedInput).equals(toNfc(trimmedExpected));
                String feedback = exactRaw ? FEEDBACK_EXACT_MATCH : FEEDBACK_NORMALIZED_MATCH;
                return new TypingEvaluationResult(true, normalizedInput, trimmedExpected, cleanedAccepted, feedback);
            }
        }

        return new TypingEvaluationResult(false, normalizedInput, null, cleanedAccepted, FEEDBACK_MISMATCH);
    }

    private List<String> sanitizeAcceptedAnswers(List<String> explicitAcceptedAnswers) {
        if (explicitAcceptedAnswers == null) {
            return List.of();
        }
        List<String> result = new ArrayList<>();
        for (String answer : explicitAcceptedAnswers) {
            String trimmed = trimUnicodeWhitespace(answer);
            if (!trimmed.isEmpty() && !result.contains(trimmed)) {
                result.add(trimmed);
            }
        }
        return result;
    }

    private String normalizeForComparison(
            TargetLanguage language,
            String trimmedText,
            TypingEvaluationOptions options
    ) {
        // Chỉ chuẩn hóa NFC để bảo toàn tuyệt đối sự khác biệt giữa Hiragana, Katakana, Kanji và độ rộng ký tự
        String nfc = toNfc(trimmedText);
        if (language == TargetLanguage.JA) {
            return nfc;
        }

        // Quy tắc cho tiếng Anh (EN)
        if (options.strictCharacterMatching()) {
            return nfc;
        }

        String normalized = nfc;
        if (options.ignoreEnglishPunctuation()) {
            normalized = stripEnglishPunctuation(normalized);
        }
        if (options.caseInsensitiveEnglish()) {
            normalized = normalized.toLowerCase(Locale.ROOT);
        }
        return collapseAsciiSpaces(normalized);
    }

    private String stripEnglishPunctuation(String input) {
        StringBuilder builder = new StringBuilder(input.length());
        for (int i = 0; i < input.length(); i++) {
            char ch = input.charAt(i);
            if (isIgnorableEnglishPunctuation(ch)) {
                builder.append(' ');
            } else {
                builder.append(ch);
            }
        }
        return builder.toString();
    }

    private boolean isIgnorableEnglishPunctuation(char ch) {
        return ch == '.' || ch == ',' || ch == '!' || ch == '?' || ch == ';' || ch == ':'
                || ch == '"' || ch == '\'' || ch == '(' || ch == ')' || ch == '-';
    }

    private String collapseAsciiSpaces(String input) {
        StringBuilder builder = new StringBuilder(input.length());
        boolean inWhitespace = false;
        for (int i = 0; i < input.length(); i++) {
            char ch = input.charAt(i);
            if (Character.isWhitespace(ch)) {
                if (!inWhitespace && !builder.isEmpty()) {
                    builder.append(' ');
                    inWhitespace = true;
                }
            } else {
                builder.append(ch);
                inWhitespace = false;
            }
        }
        int len = builder.length();
        if (len > 0 && builder.charAt(len - 1) == ' ') {
            builder.deleteCharAt(len - 1);
        }
        return builder.toString();
    }

    private String trimUnicodeWhitespace(String input) {
        if (input == null || input.isEmpty()) {
            return "";
        }
        int start = 0;
        int end = input.length();
        while (start < end && isUnicodeSpace(input.charAt(start))) {
            start++;
        }
        while (end > start && isUnicodeSpace(input.charAt(end - 1))) {
            end--;
        }
        return input.substring(start, end);
    }

    private boolean isUnicodeSpace(char ch) {
        return Character.isWhitespace(ch) || ch == '\u3000';
    }

    private String toNfc(String text) {
        return Normalizer.normalize(text, Normalizer.Form.NFC);
    }
}
