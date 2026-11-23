package com.senkoje.infrastructure.tts;

import com.senkoje.domain.model.TargetLanguage;
import java.util.Objects;

/**
 * Yêu cầu phát âm một đoạn văn bản học tập thông qua nhà cung cấp TTS ngoại tuyến.
 *
 * @param text nội dung văn bản cần đọc
 * @param language ngôn ngữ đích của văn bản (JA hoặc EN)
 * @param speechRate tốc độ đọc trong khoảng 0.5 đến 2.0
 * @param preferredVoice tên giọng đọc ưu tiên do người dùng cấu hình (có thể rỗng hoặc null)
 */
public record TtsPlaybackRequest(
        String text,
        TargetLanguage language,
        double speechRate,
        String preferredVoice
) {

    /**
     * Chuẩn hóa các tham số yêu cầu phát âm và giới hạn tốc độ đọc an toàn.
     */
    public TtsPlaybackRequest {
        text = text == null ? "" : text.trim();
        Objects.requireNonNull(language, "language must not be null");
        if (!Double.isFinite(speechRate)) {
            speechRate = 1.0;
        }
        speechRate = Math.max(0.5, Math.min(2.0, speechRate));
        preferredVoice = preferredVoice == null ? "" : preferredVoice.trim();
    }
}
