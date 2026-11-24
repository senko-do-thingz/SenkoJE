package com.senkoje.infrastructure.importer;

import com.senkoje.domain.model.Deck;
import com.senkoje.domain.model.DeckSettings;
import java.util.List;

/**
 * Báo cáo tổng hợp kết quả xem trước trước khi thực hiện ghi dữ liệu nhập vào cơ sở dữ liệu.
 *
 * @param totalCount tổng số dòng hoặc bản ghi được phát hiện
 * @param validCount số lượng bản ghi hoàn toàn hợp lệ
 * @param malformedCount số lượng bản ghi bị lỗi cú pháp hoặc thiếu trường bắt buộc
 * @param duplicateCount số lượng bản ghi bị trùng lặp với thẻ đã có
 * @param items danh sách chi tiết từng bản ghi xem trước
 * @param candidateDeck thông tin bộ thẻ nếu tệp nguồn là JSON có định nghĩa bộ thẻ (có thể null)
 * @param candidateSettings cấu hình bộ thẻ đi kèm nếu có (có thể null)
 */
public record ImportPreviewReport(
        int totalCount,
        int validCount,
        int malformedCount,
        int duplicateCount,
        List<ImportPreviewItem> items,
        Deck candidateDeck,
        DeckSettings candidateSettings
) {

    public ImportPreviewReport {
        items = items == null ? List.of() : List.copyOf(items);
    }

    /**
     * Tạo báo cáo xem trước cho danh sách các bản ghi xem trước.
     *
     * @param items danh sách bản ghi xem trước
     * @param candidateDeck thông tin bộ thẻ đi kèm nếu có
     * @param candidateSettings cấu hình bộ thẻ đi kèm nếu có
     * @return báo cáo xem trước đã tính toán số lượng chính xác
     */
    public static ImportPreviewReport of(
            List<ImportPreviewItem> items,
            Deck candidateDeck,
            DeckSettings candidateSettings
    ) {
        int total = items.size();
        int valid = 0;
        int malformed = 0;
        int duplicate = 0;

        for (ImportPreviewItem item : items) {
            switch (item.status()) {
                case VALID -> valid++;
                case DUPLICATE -> duplicate++;
                case INVALID -> malformed++;
            }
        }
        return new ImportPreviewReport(total, valid, malformed, duplicate, items, candidateDeck, candidateSettings);
    }

    /**
     * Kiểm tra tệp nguồn có thể nhập được dữ liệu hay không (ít nhất 1 bản ghi hợp lệ).
     *
     * @return true nếu có ít nhất 1 bản ghi hợp lệ
     */
    public boolean canImport() {
        return validCount > 0 || duplicateCount > 0;
    }

    /**
     * Kiểm tra tệp nguồn có chứa bản ghi bị lỗi hay không.
     *
     * @return true nếu có bản ghi bị lỗi
     */
    public boolean hasErrors() {
        return malformedCount > 0;
    }

    /**
     * Kiểm tra tệp nguồn có chứa bản ghi bị trùng lặp hay không.
     *
     * @return true nếu có bản ghi trùng lặp
     */
    public boolean hasDuplicates() {
        return duplicateCount > 0;
    }
}
