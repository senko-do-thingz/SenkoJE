package com.senkoje.application.dto;

/**
 * Cấu hình kích thước và trạng thái cửa sổ desktop JavaFX được lưu trong app_settings.
 *
 * @param width chiều rộng cửa sổ tính theo pixel
 * @param height chiều cao cửa sổ tính theo pixel
 * @param maximized trạng thái phóng to toàn màn hình của cửa sổ
 */
public record WindowPreferences(
        int width,
        int height,
        boolean maximized
) {
}
