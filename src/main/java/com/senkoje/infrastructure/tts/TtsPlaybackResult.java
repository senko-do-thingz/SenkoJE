package com.senkoje.infrastructure.tts;

import java.util.Objects;

/**
 * Kết quả trả về sau một yêu cầu phát âm TTS ngoại tuyến.
 *
 * @param succeeded true nếu phát âm hoàn tất thành công
 * @param providerId mã nhà cung cấp đã xử lý yêu cầu
 * @param statusCode khóa trạng thái bản địa hóa để hiển thị rõ trên giao diện
 * @param detailMessage chi tiết bổ sung nếu xảy ra lỗi hoặc không khả dụng
 */
public record TtsPlaybackResult(
        boolean succeeded,
        String providerId,
        String statusCode,
        String detailMessage
) {

    /**
     * Đảm bảo mã nhà cung cấp và khóa trạng thái không bị null.
     */
    public TtsPlaybackResult {
        Objects.requireNonNull(providerId, "providerId must not be null");
        Objects.requireNonNull(statusCode, "statusCode must not be null");
        detailMessage = detailMessage == null ? "" : detailMessage;
    }
}
