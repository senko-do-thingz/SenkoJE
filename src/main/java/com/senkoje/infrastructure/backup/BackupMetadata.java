package com.senkoje.infrastructure.backup;

import java.nio.file.Path;
import java.time.Instant;

/**
 * Thông tin chi tiết của một bản sao lưu cơ sở dữ liệu SQLite được tạo thành công.
 *
 * @param backupPath đường dẫn tệp tin sao lưu
 * @param createdAt thời điểm hoàn thành sao lưu
 * @param fileSizeBytes dung lượng tệp tin tính theo byte
 * @param deckCount tổng số bộ thẻ trong bản sao lưu
 * @param cardCount tổng số thẻ học tập trong bản sao lưu
 */
public record BackupMetadata(
        Path backupPath,
        Instant createdAt,
        long fileSizeBytes,
        int deckCount,
        int cardCount
) {
}
