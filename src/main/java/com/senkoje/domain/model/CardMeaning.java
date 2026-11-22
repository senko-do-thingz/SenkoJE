package com.senkoje.domain.model;

/**
 * Nghĩa bổ sung theo ngôn ngữ giải nghĩa cho một thẻ học tập.
 *
 * @param id mã định danh duy nhất (có thể null khi chưa lưu)
 * @param cardId mã thẻ sở hữu nghĩa bổ sung này
 * @param languageCode mã ngôn ngữ của nghĩa bổ sung
 * @param meaning nội dung giải nghĩa
 * @param displayOrder thứ tự hiển thị của nghĩa bổ sung
 */
public record CardMeaning(
        Long id,
        Long cardId,
        DefinitionLanguage languageCode,
        String meaning,
        int displayOrder
) {
}
