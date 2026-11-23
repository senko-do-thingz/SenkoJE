package com.senkoje.infrastructure.tts;

import java.util.Objects;

/**
 * Trạng thái sẵn sàng của một nhà cung cấp TTS ngoại tuyến trên máy cục bộ.
 *
 * @param available true nếu nhà cung cấp có thể phát âm ngoại tuyến ngay lập tức
 * @param providerId mã nhà cung cấp ("system", "bundled", "unsupported")
 * @param reasonCode khóa lý do bản địa hóa (ví dụ "tts.status.ready" hoặc "tts.status.unavailable")
 * @param detailMessage thông điệp chi tiết kỹ thuật bổ sung (có thể rỗng)
 */
public record TtsAvailability(
        boolean available,
        String providerId,
        String reasonCode,
        String detailMessage
) {

    /**
     * Đảm bảo các trường mô tả trạng thái không bị null.
     */
    public TtsAvailability {
        Objects.requireNonNull(providerId, "providerId must not be null");
        Objects.requireNonNull(reasonCode, "reasonCode must not be null");
        detailMessage = detailMessage == null ? "" : detailMessage;
    }
}
