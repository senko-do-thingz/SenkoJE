package com.senkoje.domain.model;

import java.time.Instant;

/**
 * Thực thể miền đại diện cho một bộ thẻ học tập trong SenkoJE.
 *
 * @param id mã định danh duy nhất của bộ thẻ (có thể null khi chưa lưu)
 * @param title tên hiển thị của bộ thẻ
 * @param targetLanguage ngôn ngữ đích của bộ thẻ
 * @param definitionLanguage ngôn ngữ giải nghĩa chính của bộ thẻ
 * @param level trình độ ngôn ngữ mục tiêu của bộ thẻ
 * @param description mô tả ngắn về nội dung bộ thẻ
 * @param createdAt thời điểm tạo bộ thẻ
 * @param updatedAt thời điểm cập nhật gần nhất
 */
public record Deck(
        Long id,
        String title,
        TargetLanguage targetLanguage,
        DefinitionLanguage definitionLanguage,
        ProficiencyLevel level,
        String description,
        Instant createdAt,
        Instant updatedAt
) {
}
