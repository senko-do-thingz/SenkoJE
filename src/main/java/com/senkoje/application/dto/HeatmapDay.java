package com.senkoje.application.dto;

import java.time.LocalDate;

/**
 * Dữ liệu một ô ngày trên biểu đồ nhiệt hoạt động học tập của Dashboard.
 *
 * @param date ngày lịch theo múi giờ ứng dụng
 * @param reviewCount tổng số lượt ôn tập trong ngày
 * @param intensityLevel mức độ đậm nhạt của ô nhiệt từ 0 đến 4
 */
public record HeatmapDay(
        LocalDate date,
        int reviewCount,
        int intensityLevel
) {
}
