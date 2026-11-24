package com.senkoje.infrastructure.backup;

import java.nio.file.Path;
import java.time.Instant;

/**
 * Kết quả thực thi quá trình phục hồi cơ sở dữ liệu SQLite từ bản sao lưu an toàn.
 *
 * @param restoredFromPath đường dẫn tệp sao lưu nguồn được sử dụng để phục hồi
 * @param safetyBackupPath đường dẫn bản sao lưu an toàn tự động tạo trước khi ghi đè
 * @param restoredAt thời điểm hoàn tất phục hồi
 * @param deckCount số lượng bộ thẻ sau khi phục hồi
 * @param cardCount số lượng thẻ sau khi phục hồi
 */
public record RestoreResult(
        Path restoredFromPath,
        Path safetyBackupPath,
        Instant restoredAt,
        int deckCount,
        int cardCount
) {
}
