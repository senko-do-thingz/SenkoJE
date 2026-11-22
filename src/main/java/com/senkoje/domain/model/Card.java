package com.senkoje.domain.model;

import java.time.Instant;

/**
 * Thực thể miền đại diện cho một thẻ học tập thuộc bộ thẻ.
 *
 * @param id mã định danh duy nhất của thẻ (có thể null khi chưa lưu)
 * @param deckId mã bộ thẻ chứa thẻ này
 * @param cardType phân loại thẻ (từ vựng, cụm từ hoặc ngữ pháp)
 * @param term nội dung chính cần ghi nhớ
 * @param reading cách đọc hoặc phiên âm (có thể null)
 * @param meaning nghĩa chính của thẻ
 * @param partOfSpeech từ loại của mục học tập (có thể null)
 * @param level trình độ ngôn ngữ riêng của thẻ (có thể null)
 * @param extraData dữ liệu mở rộng định dạng JSON hợp lệ (có thể null)
 * @param notes ghi chú cá nhân của người học (có thể null)
 * @param suspended cờ tạm ngưng đưa thẻ vào phiên ôn tập
 * @param createdAt thời điểm tạo thẻ
 * @param updatedAt thời điểm cập nhật thẻ gần nhất
 */
public record Card(
        Long id,
        Long deckId,
        CardType cardType,
        String term,
        String reading,
        String meaning,
        String partOfSpeech,
        ProficiencyLevel level,
        String extraData,
        String notes,
        boolean suspended,
        Instant createdAt,
        Instant updatedAt
) {
}
