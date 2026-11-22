package com.senkoje.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.senkoje.app.ApplicationContainer;
import com.senkoje.application.dto.DashboardSummary;
import com.senkoje.domain.model.Card;
import com.senkoje.domain.model.CardType;
import com.senkoje.domain.model.Deck;
import com.senkoje.domain.model.DefinitionLanguage;
import com.senkoje.domain.model.ProficiencyLevel;
import com.senkoje.domain.model.ReviewRating;
import com.senkoje.domain.model.StudySession;
import com.senkoje.domain.model.TargetLanguage;
import com.senkoje.infrastructure.config.AppDataPathResolver;
import java.nio.file.Path;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Kiểm thử tích hợp cho AnalyticsService xác nhận toàn bộ chỉ số Dashboard được tính từ SQLite theo UTC+7.
 */
class AnalyticsServiceTest {

    private static final ZoneId VIETNAM_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    @Test
    void shouldReturnCleanEmptySummaryWhenDatabaseHasNoData(@TempDir Path tempDir) throws SQLException {
        // Khi cơ sở dữ liệu trống, mọi chỉ số phải bằng 0 và cờ isEmpty() là true
        Clock fixedClock = Clock.fixed(Instant.parse("2026-11-22T05:00:00Z"), VIETNAM_ZONE);
        try (ApplicationContainer container = ApplicationContainer.createForDatabase(
                tempDir.resolve("empty-analytics.db"), new AppDataPathResolver(), fixedClock)) {
            DashboardSummary summary = container.getAnalyticsService().getDashboardSummary();
            assertTrue(summary.isEmpty());
            assertEquals(0, summary.totalDecks());
            assertEquals(0, summary.totalCards());
            assertEquals(0, summary.cardsDueToday());
            assertEquals(0, summary.newCardsAvailable());
            assertEquals(0, summary.reviewsCompletedToday());
            assertEquals(0, summary.dailyStudyTimeSeconds());
            assertEquals(0, summary.currentStreakDays());
            assertEquals(AnalyticsService.DEFAULT_HEATMAP_DAYS, summary.heatmapDays().size());
            assertTrue(summary.recentSessions().isEmpty());
        }
    }

    @Test
    void shouldComputeMetricsStreakHeatmapAndRecentSessionsFromSqlite(@TempDir Path tempDir) throws SQLException {
        // Kiểm tra tính toán bộ thẻ, thẻ mới, lượt ôn hôm nay, chuỗi 2 ngày liên tiếp và phiên học gần đây
        Instant todayInstant = LocalDate.of(2026, 11, 22).atTime(10, 0).atZone(VIETNAM_ZONE).toInstant();
        Instant yesterdayInstant = LocalDate.of(2026, 11, 21).atTime(20, 0).atZone(VIETNAM_ZONE).toInstant();
        Clock fixedClock = Clock.fixed(todayInstant, VIETNAM_ZONE);

        try (ApplicationContainer container = ApplicationContainer.createForDatabase(
                tempDir.resolve("seeded-analytics.db"), new AppDataPathResolver(), fixedClock)) {
            Deck deck = container.getDeckService().createDeck(
                    "JLPT N5 Core", TargetLanguage.JA, DefinitionLanguage.VI, ProficiencyLevel.JLPT_N5, "Core deck");
            Card card1 = container.getCardService().createCard(
                    deck.id(), CardType.VOCABULARY, "食べる", "たべる", "ăn", "verb",
                    ProficiencyLevel.JLPT_N5, null, null, List.of(), List.of());
            Card card2 = container.getCardService().createCard(
                    deck.id(), CardType.VOCABULARY, "飲む", "のむ", "uống", "verb",
                    ProficiencyLevel.JLPT_N5, null, null, List.of(), List.of());

            // Ghi nhận một lượt ôn hôm qua và một lượt ôn hôm nay để tạo chuỗi 2 ngày liên tiếp
            StudySession session = container.getStudySessionRepository().create(
                    new StudySession(null, deck.id(), todayInstant.minusSeconds(300), todayInstant, 1, 300));
            container.getReviewService().submitReview(card1.id(), ReviewRating.GOOD, null, 2500, yesterdayInstant);
            container.getReviewService().submitReview(card1.id(), ReviewRating.GOOD, session.id(), 3200, todayInstant);

            DashboardSummary summary = container.getAnalyticsService().getDashboardSummary(todayInstant);
            assertFalse(summary.isEmpty());
            assertEquals(1, summary.totalDecks());
            assertEquals(2, summary.totalCards());
            assertEquals(1, summary.newCardsAvailable());
            assertEquals(1, summary.reviewsCompletedToday());
            assertEquals(300, summary.dailyStudyTimeSeconds());
            assertEquals(2, summary.currentStreakDays());
            assertEquals(1, summary.recentSessions().size());
            assertEquals("JLPT N5 Core", summary.recentSessions().getFirst().deckTitle());
            assertEquals(1, summary.heatmapDays().getLast().reviewCount());
            assertEquals(1, summary.heatmapDays().getLast().intensityLevel());
        }
    }
}
