package com.senkoje.infrastructure.tts;

import com.senkoje.domain.model.TargetLanguage;
import java.util.Objects;

/**
 * Thông tin một giọng đọc TTS ngoại tuyến được nhà cung cấp phát hiện cho tiếng Nhật hoặc tiếng Anh.
 *
 * @param id mã định danh giọng đọc dùng để truyền cho bộ tổng hợp tiếng nói
 * @param displayName tên hiển thị thân thiện với người dùng
 * @param language ngôn ngữ mục tiêu của giọng đọc (JA hoặc EN)
 */
public record TtsVoice(
        String id,
        String displayName,
        TargetLanguage language
) {

    /**
     * Đảm bảo mã giọng đọc, tên hiển thị và ngôn ngữ không bị null.
     */
    public TtsVoice {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(displayName, "displayName must not be null");
        Objects.requireNonNull(language, "language must not be null");
    }
}
