package com.senkoje.presentation.util;

import com.senkoje.presentation.i18n.LocalizationManager;
import java.time.Duration;
import java.util.Objects;

/**
 * Bộ định dạng khoảng thời gian xem trước FSRS-6 sang chuỗi ngắn gọn đa ngôn ngữ
 * hiển thị trực tiếp trên các nút đánh giá (Again, Hard, Good, Easy).
 */
public final class StudyIntervalFormatter {

    private StudyIntervalFormatter() {
    }

    /**
     * Chuyển đổi đối tượng Duration từ bộ lập lịch FSRS-6 sang nhãn thân thiện với người học.
     *
     * @param interval khoảng thời gian lập lịch từ SchedulingResult
     * @param localizationManager trình quản lý đa ngôn ngữ
     * @return chuỗi hiển thị ngắn gọn (ví dụ: 1m, 10m, 1d, 2mo, 1y hoặc bản dịch tiếng Việt)
     */
    public static String format(Duration interval, LocalizationManager localizationManager) {
        Objects.requireNonNull(localizationManager, "localizationManager must not be null");
        if (interval == null || interval.isNegative()) {
            return localizationManager.get("study.interval.lessThanMinute");
        }

        long totalSeconds = interval.getSeconds();
        if (totalSeconds < 60) {
            return localizationManager.get("study.interval.lessThanMinute");
        }

        long totalMinutes = totalSeconds / 60;
        if (totalMinutes < 60) {
            return localizationManager.get("study.interval.minutes", totalMinutes);
        }

        long totalHours = totalMinutes / 60;
        if (totalHours < 24) {
            return localizationManager.get("study.interval.hours", totalHours);
        }

        long totalDays = Math.max(1L, Math.round(totalSeconds / 86_400.0));
        if (totalDays < 30) {
            return localizationManager.get("study.interval.days", totalDays);
        }

        if (totalDays < 365) {
            long months = Math.max(1L, Math.round(totalDays / 30.0));
            return localizationManager.get("study.interval.months", months);
        }

        long years = Math.max(1L, Math.round(totalDays / 365.0));
        return localizationManager.get("study.interval.years", years);
    }

    /**
     * Định dạng thời lượng phiên học tính bằng giây sang dạng mm:ss.
     *
     * @param totalSeconds tổng số giây hoạt động
     * @return chuỗi đồng hồ định dạng mm:ss
     */
    public static String formatTimer(int totalSeconds) {
        int safeSeconds = Math.max(0, totalSeconds);
        int minutes = safeSeconds / 60;
        int seconds = safeSeconds % 60;
        return String.format("%02d:%02d", minutes, seconds);
    }
}
