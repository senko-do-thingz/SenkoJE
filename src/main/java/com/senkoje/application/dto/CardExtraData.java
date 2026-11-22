package com.senkoje.application.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Dữ liệu mở rộng có cấu trúc cho thẻ tiếng Nhật, tiếng Anh và ngữ pháp,
 * được tuần tự hóa thành JSON hợp lệ trong cột extra_data của bảng cards.
 *
 * @param furigana cách đọc Furigana (tiếng Nhật)
 * @param kanjiComponents các thành phần chữ Hán (tiếng Nhật)
 * @param onyomi âm On (tiếng Nhật)
 * @param kunyomi âm Kun (tiếng Nhật)
 * @param pitchAccent trọng âm cao độ nếu có (tiếng Nhật)
 * @param ipa phiên âm quốc tế IPA (tiếng Anh)
 * @param collocations các cụm từ kết hợp (tiếng Anh)
 * @param register sắc thái sử dụng ngôn ngữ (tiếng Anh)
 * @param grammarPattern mẫu ngữ pháp (thẻ ngữ pháp)
 * @param usageNotes ghi chú cách sử dụng ngữ pháp
 * @param formationRules quy tắc cấu tạo ngữ pháp
 * @param relatedVocabulary từ vựng liên quan đến mẫu ngữ pháp
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record CardExtraData(
        String furigana,
        String kanjiComponents,
        String onyomi,
        String kunyomi,
        String pitchAccent,
        String ipa,
        String collocations,
        String register,
        String grammarPattern,
        String usageNotes,
        String formationRules,
        String relatedVocabulary
) {

    /**
     * Khởi tạo bản ghi dữ liệu mở rộng rỗng.
     *
     * @return bản ghi với toàn bộ trường là null
     */
    public static CardExtraData empty() {
        return new CardExtraData(null, null, null, null, null, null, null, null, null, null, null, null);
    }

    /**
     * Kiểm tra toàn bộ trường dữ liệu mở rộng có đang trống hay không.
     *
     * @return true nếu không có trường nào có nội dung
     */
    public boolean isEmpty() {
        return isBlank(furigana)
                && isBlank(kanjiComponents)
                && isBlank(onyomi)
                && isBlank(kunyomi)
                && isBlank(pitchAccent)
                && isBlank(ipa)
                && isBlank(collocations)
                && isBlank(register)
                && isBlank(grammarPattern)
                && isBlank(usageNotes)
                && isBlank(formationRules)
                && isBlank(relatedVocabulary);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
