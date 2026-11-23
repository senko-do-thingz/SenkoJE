package com.senkoje.infrastructure.tts;

import java.util.Objects;

/**
 * Kết quả trả về sau một yêu cầu phát âm TTS ngoại tuyến.
 *
 * @param succeeded true nếu phát âm hoàn tất thành công
 * @param providerId mã nhà cung cấp đã xử lý yêu cầu
 * @param statusCode khóa trạng thái bản địa hóa để hiển thị rõ trên giao diện
 * @param detailMessage chi tiết bổ sung nếu xảy ra lỗi hoặc không khả dụng
 * @param cancelled true nếu lượt phát âm bị hủy chủ động (ví dụ chuyển thẻ hoặc dừng phát)
 */
public record TtsPlaybackResult(
        boolean succeeded,
        String providerId,
        String statusCode,
        String detailMessage,
        boolean cancelled
) {

    /**
     * Hàm khởi tạo rút gọn cho các kết quả không phải do hủy chủ động.
     *
     * @param succeeded cờ thành công
     * @param providerId mã nhà cung cấp
     * @param statusCode khóa trạng thái bản địa hóa
     * @param detailMessage thông điệp chi tiết bổ sung
     */
    public TtsPlaybackResult(
            boolean succeeded,
            String providerId,
            String statusCode,
            String detailMessage
    ) {
        this(succeeded, providerId, statusCode, detailMessage, false);
    }

    /**
     * Đảm bảo mã nhà cung cấp và khóa trạng thái không bị null.
     */
    public TtsPlaybackResult {
        Objects.requireNonNull(providerId, "providerId must not be null");
        Objects.requireNonNull(statusCode, "statusCode must not be null");
        detailMessage = detailMessage == null ? "" : detailMessage;
    }
}
