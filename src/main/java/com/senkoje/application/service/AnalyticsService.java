package com.senkoje.application.service;

import com.senkoje.application.dto.DashboardSummary;
import com.senkoje.application.dto.HeatmapDay;
import com.senkoje.application.dto.RecentSessionSummary;
import com.senkoje.domain.model.Deck;
import com.senkoje.domain.model.DeckSettings;
import com.senkoje.domain.model.ReviewLog;
import com.senkoje.domain.model.StudySession;
import com.senkoje.domain.repository.CardRepository;
import com.senkoje.domain.repository.DeckRepository;
import com.senkoje.domain.repository.ReviewLogRepository;
import com.senkoje.domain.repository.ReviewStateRepository;
import com.senkoje.domain.repository.StudySessionRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Dịch vụ phân tích học tập tính toán toàn bộ chỉ số Dashboard, biểu đồ nhiệt
 * và chuỗi ngày học từ dữ liệu SQLite cục bộ theo múi giờ được cấu hình.
 */
public class AnalyticsService {

    public static final int DEFAULT_HEATMAP_DAYS = 84;
    public static final int DEFAULT_RECENT_SESSIONS_LIMIT = 10;
    private static final int STREAK_LOOKBACK_DAYS = 365;

    private final DeckRepository deckRepository;
    private final CardRepository cardRepository;
    private final ReviewStateRepository reviewStateRepository;
    private final ReviewLogRepository reviewLogRepository;
    private final StudySessionRepository studySessionRepository;
    private final Clock clock;

    /**
     * Khởi tạo dịch vụ phân tích với các kho lưu trữ miền và nguồn đồng hồ.
     *
     * @param deckRepository kho lưu trữ bộ thẻ
     * @param cardRepository kho lưu trữ thẻ
     * @param reviewStateRepository kho lưu trữ trạng thái ôn tập
     * @param reviewLogRepository kho lưu trữ nhật ký ôn tập
     * @param studySessionRepository kho lưu trữ phiên học
     * @param clock nguồn thời gian và múi giờ chuẩn của ứng dụng
     */
    public AnalyticsService(
            DeckRepository deckRepository,
            CardRepository cardRepository,
            ReviewStateRepository reviewStateRepository,
            ReviewLogRepository reviewLogRepository,
            StudySessionRepository studySessionRepository,
            Clock clock
    ) {
        this.deckRepository = Objects.requireNonNull(deckRepository, "deckRepository must not be null");
        this.cardRepository = Objects.requireNonNull(cardRepository, "cardRepository must not be null");
        this.reviewStateRepository = Objects.requireNonNull(
                reviewStateRepository, "reviewStateRepository must not be null");
        this.reviewLogRepository = Objects.requireNonNull(
                reviewLogRepository, "reviewLogRepository must not be null");
        this.studySessionRepository = Objects.requireNonNull(
                studySessionRepository, "studySessionRepository must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    /**
     * Tổng hợp toàn bộ chỉ số Dashboard tại thời điểm hiện tại.
     *
     * @return đối tượng DashboardSummary
     */
    public DashboardSummary getDashboardSummary() {
        return getDashboardSummary(clock.instant());
    }

    /**
     * Tổng hợp toàn bộ chỉ số Dashboard tại một mốc thời gian tường minh để hỗ trợ kiểm thử tất định.
     *
     * @param asOf mốc thời gian tính toán
     * @return đối tượng DashboardSummary
     */
    public DashboardSummary getDashboardSummary(Instant asOf) {
        Objects.requireNonNull(asOf, "asOf must not be null");
        ZoneId zone = clock.getZone();
        LocalDate today = asOf.atZone(zone).toLocalDate();
        Instant todayStart = today.atStartOfDay(zone).toInstant();
        Instant todayEndExclusive = today.plusDays(1).atStartOfDay(zone).toInstant();
        Instant todayEndInclusive = todayEndExclusive.minusNanos(1);

        List<Deck> allDecks = deckRepository.findAll();
        Map<Long, String> deckTitlesById = new HashMap<>();
        int totalDecks = allDecks.size();
        int cardsDueToday = 0;
        int newCardsAvailable = 0;

        for (Deck deck : allDecks) {
            if (deck.id() == null) {
                continue;
            }
            deckTitlesById.put(deck.id(), deck.title());
            cardsDueToday += reviewStateRepository.findDueByDeckId(deck.id(), asOf).size();

            int newLimit = deckRepository.findSettingsByDeckId(deck.id())
                    .map(DeckSettings::newCardsPerDay)
                    .orElse(DeckSettings.DEFAULT_NEW_CARDS_PER_DAY);
            int newReviewedToday = reviewLogRepository.countNewCardsReviewedBetween(
                    deck.id(), todayStart, todayEndExclusive);
            int remainingNewQuota = Math.max(0, newLimit - newReviewedToday);
            if (remainingNewQuota > 0) {
                newCardsAvailable += reviewStateRepository.findNewByDeckId(deck.id(), remainingNewQuota).size();
            }
        }

        int totalCards = cardRepository.search(null, null, null, null).size();

        // Đọc toàn bộ nhật ký ôn tập trong 365 ngày gần nhất một lần duy nhất để tính lượt hôm nay, chuỗi ngày và biểu đồ nhiệt
        LocalDate lookbackStartDate = today.minusDays(STREAK_LOOKBACK_DAYS - 1L);
        Instant lookbackStart = lookbackStartDate.atStartOfDay(zone).toInstant();
        List<ReviewLog> recentLogs = reviewLogRepository.findByReviewedAtBetween(lookbackStart, todayEndInclusive);

        Map<LocalDate, Integer> dailyReviewCounts = new HashMap<>();
        long todayResponseTimeMs = 0L;
        for (ReviewLog log : recentLogs) {
            LocalDate reviewDate = log.reviewedAt().atZone(zone).toLocalDate();
            dailyReviewCounts.merge(reviewDate, 1, Integer::sum);
            if (reviewDate.equals(today) && log.responseTimeMs() != null && log.responseTimeMs() > 0) {
                todayResponseTimeMs += log.responseTimeMs();
            }
        }

        int reviewsCompletedToday = dailyReviewCounts.getOrDefault(today, 0);
        int currentStreakDays = calculateStreakDays(today, dailyReviewCounts);
        List<HeatmapDay> heatmapDays = buildHeatmap(today, dailyReviewCounts);

        List<StudySession> rawRecentSessions = studySessionRepository.findRecent(DEFAULT_RECENT_SESSIONS_LIMIT);
        List<RecentSessionSummary> recentSessions = new ArrayList<>(rawRecentSessions.size());
        int dailyStudyTimeSeconds = 0;

        for (StudySession session : rawRecentSessions) {
            String deckTitle = session.deckId() == null ? null : deckTitlesById.get(session.deckId());
            recentSessions.add(new RecentSessionSummary(
                    session.id(),
                    session.deckId(),
                    deckTitle,
                    session.startedAt(),
                    session.completedAt(),
                    session.cardsReviewed(),
                    session.durationSeconds()
            ));
            LocalDate sessionDate = session.startedAt().atZone(zone).toLocalDate();
            if (sessionDate.equals(today) && session.durationSeconds() > 0) {
                dailyStudyTimeSeconds += session.durationSeconds();
            }
        }

        // Nếu chưa có bản ghi study_sessions trong ngày nhưng đã có lượt ôn tập, ước lượng thời lượng từ response_time_ms
        if (dailyStudyTimeSeconds == 0 && todayResponseTimeMs > 0) {
            dailyStudyTimeSeconds = (int) Math.max(1L, Math.round(todayResponseTimeMs / 1000.0));
        }

        return new DashboardSummary(
                totalDecks,
                totalCards,
                cardsDueToday,
                newCardsAvailable,
                reviewsCompletedToday,
                dailyStudyTimeSeconds,
                currentStreakDays,
                List.copyOf(heatmapDays),
                List.copyOf(recentSessions)
        );
    }

    private int calculateStreakDays(LocalDate today, Map<LocalDate, Integer> dailyReviewCounts) {
        // Nếu hôm nay đã có lượt ôn tập thì đếm lùi từ hôm nay; nếu chưa thì cho phép giữ chuỗi tính đến hôm qua
        LocalDate cursor = dailyReviewCounts.getOrDefault(today, 0) > 0 ? today : today.minusDays(1);
        int streak = 0;
        while (dailyReviewCounts.getOrDefault(cursor, 0) > 0) {
            streak++;
            cursor = cursor.minusDays(1);
        }
        return streak;
    }

    private List<HeatmapDay> buildHeatmap(LocalDate today, Map<LocalDate, Integer> dailyReviewCounts) {
        List<HeatmapDay> days = new ArrayList<>(DEFAULT_HEATMAP_DAYS);
        LocalDate startDate = today.minusDays(DEFAULT_HEATMAP_DAYS - 1L);
        for (int i = 0; i < DEFAULT_HEATMAP_DAYS; i++) {
            LocalDate date = startDate.plusDays(i);
            int count = dailyReviewCounts.getOrDefault(date, 0);
            days.add(new HeatmapDay(date, count, resolveIntensityLevel(count)));
        }
        return days;
    }

    private int resolveIntensityLevel(int reviewCount) {
        if (reviewCount <= 0) {
            return 0;
        }
        if (reviewCount < 5) {
            return 1;
        }
        if (reviewCount < 15) {
            return 2;
        }
        if (reviewCount < 30) {
            return 3;
        }
        return 4;
    }
}
