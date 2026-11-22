package com.senkoje.domain.model;

/**
 * Câu ví dụ minh họa và bản dịch đi kèm của một thẻ học tập.
 *
 * @param id mã định danh duy nhất (có thể null khi chưa lưu)
 * @param cardId mã thẻ sở hữu câu ví dụ
 * @param sentence câu ví dụ bằng ngôn ngữ đích
 * @param translation bản dịch của câu ví dụ (có thể null)
 * @param translationLanguage ngôn ngữ của bản dịch (có thể null)
 * @param displayOrder thứ tự hiển thị của câu ví dụ
 */
public record CardExample(
        Long id,
        Long cardId,
        String sentence,
        String translation,
        DefinitionLanguage translationLanguage,
        int displayOrder
) {
}
