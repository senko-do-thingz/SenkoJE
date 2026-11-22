package com.senkoje.application.dto;

import java.util.List;

/**
 * Tập chỉ số tổng hợp hiển thị trên màn hình Dashboard, được tính toàn bộ từ dữ liệu SQLite cục bộ.
 *
 * @param totalDecks tổng số bộ thẻ
 * @param totalCards tổng số thẻ học tập
 * @param cardsDueToday tổng số thẻ đến hạn ôn tập hôm nay
 * @param newCardsAvailable tổng số thẻ mới sẵn sàng học trong hạn mức hôm nay
 * @param reviewsCompletedToday số lượt ôn tập đã hoàn thành trong ngày hôm nay
 * @param dailyStudyTimeSeconds tổng thời gian học trong ngày hôm nay tính bằng giây
 * @param currentStreakDays số ngày học liên tiếp hiện tại
 * @param heatmapDays danh sách ngày cho biểu đồ nhiệt hoạt động học tập
 * @param recentSessions danh sách phiên học gần đây
 */
public record DashboardSummary(
        int totalDecks,
        int totalCards,
        int cardsDueToday,
        int newCardsAvailable,
        int reviewsCompletedToday,
        int dailyStudyTimeSeconds,
        int currentStreakDays,
        List<HeatmapDay> heatmapDays,
        List<RecentSessionSummary> recentSessions
) {

    /**
     * Kiểm tra hệ thống chưa có bộ thẻ, thẻ học hoặc lịch sử ôn tập nào.
     *
     * @return true nếu người dùng chưa có dữ liệu học tập
     */
    public boolean isEmpty() {
        return totalDecks == 0 && totalCards == 0 && recentSessions.isEmpty();
    }
}
