package com.senkoje.domain.model;

import java.time.Instant;

/**
 * Cặp khóa và giá trị cấu hình ứng dụng cục bộ được lưu trong bảng app_settings.
 *
 * @param settingKey khóa định danh duy nhất của cấu hình
 * @param settingValue giá trị chuỗi được lưu trữ
 * @param updatedAt thời điểm cập nhật cấu hình gần nhất
 */
public record AppSetting(
        String settingKey,
        String settingValue,
        Instant updatedAt
) {
}
