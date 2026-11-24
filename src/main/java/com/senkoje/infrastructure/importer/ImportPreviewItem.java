package com.senkoje.infrastructure.importer;

import java.util.Objects;

/**
 * Bản ghi xem trước của từng dòng dữ liệu trong báo cáo kiểm tra trước khi nhập.
 *
 * @param index chỉ số dòng hoặc thứ tự bản ghi (bắt đầu từ 1)
 * @param card dữ liệu thẻ đã phân tích (có thể null nếu dòng bị lỗi nghiêm trọng)
 * @param status trạng thái đánh giá (hợp lệ, trùng lặp hoặc lỗi)
 * @param message thông điệp mô tả chi tiết lỗi hoặc lý do bỏ qua
 */
public record ImportPreviewItem(
        int index,
        ImportCandidateCard card,
        ImportItemStatus status,
        String message
) {

    public ImportPreviewItem {
        Objects.requireNonNull(status, "status must not be null");
    }

    /**
     * Kiểm tra bản ghi này có đủ điều kiện hợp lệ để nhập vào hay không.
     *
     * @return true nếu trạng thái là VALID
     */
    public boolean isValid() {
        return status == ImportItemStatus.VALID;
    }

    /**
     * Kiểm tra bản ghi này có bị trùng lặp với thẻ đã có hay không.
     *
     * @return true nếu trạng thái là DUPLICATE
     */
    public boolean isDuplicate() {
        return status == ImportItemStatus.DUPLICATE;
    }

    /**
     * Kiểm tra bản ghi này có bị lỗi định dạng hoặc thiếu trường bắt buộc hay không.
     *
     * @return true nếu trạng thái là INVALID
     */
    public boolean isInvalid() {
        return status == ImportItemStatus.INVALID;
    }
}
