package com.senkoje.infrastructure.importer;

/**
 * Kết quả thực thi thao tác nhập dữ liệu vào cơ sở dữ liệu SQLite trong một giao dịch nguyên tử.
 *
 * @param importedCount số lượng thẻ đã thêm mới hoặc cập nhật thành công
 * @param skippedCount số lượng thẻ đã được bỏ qua theo chiến lược xử lý trùng lặp
 * @param rejectedCount số lượng thẻ bị từ chối do không hợp lệ
 * @param targetDeckId mã bộ thẻ đã nhận dữ liệu
 */
public record ImportResult(
        int importedCount,
        int skippedCount,
        int rejectedCount,
        long targetDeckId
) {
}
