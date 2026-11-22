package com.senkoje.application.dto;

/**
 * Cấu hình đọc văn bản thành giọng nói ngoại tuyến được lưu trong app_settings.
 *
 * @param enabled bật hoặc tắt tính năng TTS
 * @param provider tên bộ cung cấp TTS cục bộ
 * @param speechRate tốc độ đọc trong khoảng 0.5 đến 2.0
 * @param japaneseVoice tên giọng đọc tiếng Nhật ưu tiên
 * @param englishVoice tên giọng đọc tiếng Anh ưu tiên
 */
public record TtsPreferences(
        boolean enabled,
        String provider,
        double speechRate,
        String japaneseVoice,
        String englishVoice
) {
}
