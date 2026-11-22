package com.senkoje.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.senkoje.application.dto.DailyQuotaStatus;
import com.senkoje.domain.fsrs.FsrsScheduler;
import com.senkoje.domain.fsrs.SchedulingResult;
import com.senkoje.domain.model.Card;
import com.senkoje.domain.model.CardState;
import com.senkoje.domain.model.CardType;
import com.senkoje.domain.model.Deck;
import com.senkoje.domain.model.DeckSettings;
import com.senkoje.domain.model.DefinitionLanguage;
import com.senkoje.domain.model.ProficiencyLevel;
import com.senkoje.domain.model.ReviewLog;
import com.senkoje.domain.model.ReviewRating;
import com.senkoje.domain.model.ReviewState;
import com.senkoje.domain.model.TargetLanguage;
import com.senkoje.domain.repository.PersistenceException;
import com.senkoje.domain.service.CardValidator;
import com.senkoje.domain.service.DeckSettingsValidator;
import com.senkoje.domain.service.DeckValidator;
import com.senkoje.persistence.migration.DatabaseMigrator;
import com.senkoje.persistence.repository.SqliteCardRepository;
import com.senkoje.persistence.repository.SqliteDeckRepository;
import com.senkoje.persistence.repository.SqliteReviewLogRepository;
import com.senkoje.persistence.repository.SqliteReviewStateRepository;
import com.senkoje.persistence.repository.SqliteReviewTransactionRepository;
import com.senkoje.persistence.sqlite.SqliteConnectionManager;
import java.nio.file.Path;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Kiểm thử tích hợp cho ReviewService xác minh lưu giao dịch nguyên tử,
 * lịch sử nhật ký bất biến sau khi khởi động lại và giới hạn học hằng ngày.
 */
class ReviewServiceTest {

    @TempDir
    Path tempDir;

    private Path dbPath;
    private Instant baseTime;
    private Clock fixedClock;
    private DeckService deckService;
    private CardService cardService;
    private ReviewService reviewService;
    private SqliteReviewStateRepository reviewStateRepository;
    private SqliteReviewLogRepository reviewLogRepository;

    @BeforeEach
    void setUp() throws SQLException {
        dbPath = tempDir.resolve("review-service.db");
        baseTime = Instant.parse("2026-05-01T08:00:00Z");
        fixedClock = Clock.fixed(baseTime, ZoneOffset.UTC);

        SqliteConnectionManager manager = new SqliteConnectionManager(dbPath);
        new DatabaseMigrator(manager).migrate();
        wireServices(manager);
    }

    private void wireServices(SqliteConnectionManager manager) {
        SqliteDeckRepository deckRepository = new SqliteDeckRepository(manager);
        SqliteCardRepository cardRepository = new SqliteCardRepository(manager);
        reviewStateRepository = new SqliteReviewStateRepository(manager);
        reviewLogRepository = new SqliteReviewLogRepository(manager);
        SqliteReviewTransactionRepository transactionRepository = new SqliteReviewTransactionRepository(manager);

        deckService = new DeckService(
                deckRepository,
                new DeckValidator(),
                new DeckSettingsValidator(),
                fixedClock
        );
        cardService = new CardService(cardRepository, new CardValidator(), fixedClock);
        reviewService = new ReviewService(
                cardRepository,
                deckRepository,
                reviewStateRepository,
                reviewLogRepository,
                transactionRepository,
                new FsrsScheduler(),
                fixedClock
        );
    }

    @Test
    void shouldPersistReviewStateAndImmutableLogsAcrossDatabaseRestart() {
        Deck deck = deckService.createDeck(
                "JLPT N4 Vocabulary",
                TargetLanguage.JA,
                DefinitionLanguage.VI,
                ProficiencyLevel.JLPT_N4,
                "Core N4 vocabulary"
        );
        deckService.updateDeckSettings(new DeckSettings(
                deck.id(), 0.90, 36500, 20, 200, "1m,10m", "10m", false
        ));
        Card card = cardService.createCard(
                deck.id(), CardType.VOCABULARY, "食べる", "たべる", "ăn",
                "verb", ProficiencyLevel.JLPT_N4, null, null, List.of(), List.of()
        );

        // Xem trước không làm thay đổi trạng thái trong SQLite
        Map<ReviewRating, SchedulingResult> preview = reviewService.previewReview(card.id(), baseTime);
        assertEquals(4, preview.size());
        assertEquals(CardState.NEW, reviewStateRepository.findByCardId(card.id()).orElseThrow().state());
        assertTrue(reviewLogRepository.findByCardId(card.id()).isEmpty());

        // Lượt 1: NEW + GOOD -> LEARNING bước 1
        SchedulingResult first = reviewService.submitReview(card.id(), ReviewRating.GOOD, null, 1800, baseTime);
        assertNotNull(first.reviewLog().id());
        assertEquals(CardState.LEARNING, first.nextState().state());
        assertEquals(1, first.nextState().step());

        // Lượt 2: LEARNING bước 1 + GOOD -> tốt nghiệp sang REVIEW
        Instant secondTime = baseTime.plus(Duration.ofMinutes(10));
        SchedulingResult second = reviewService.submitReview(card.id(), ReviewRating.GOOD, null, 1200, secondTime);
        assertEquals(CardState.REVIEW, second.nextState().state());

        // Giả lập khởi động lại ứng dụng bằng cách khởi tạo lại toàn bộ connection manager và service
        SqliteConnectionManager restartedManager = new SqliteConnectionManager(dbPath);
        wireServices(restartedManager);

        ReviewState reloadedState = reviewStateRepository.findByCardId(card.id()).orElseThrow();
        assertEquals(CardState.REVIEW, reloadedState.state());
        assertEquals(2, reloadedState.repetitions());
        assertEquals(second.nextState().dueAt(), reloadedState.dueAt());

        List<ReviewLog> history = reviewService.getCardReviewHistory(card.id());
        assertEquals(2, history.size());
        assertEquals(CardState.NEW, history.get(0).previousState());
        assertEquals(CardState.LEARNING, history.get(0).nextState());
        assertEquals(1800, history.get(0).responseTimeMs());
        assertEquals(CardState.LEARNING, history.get(1).previousState());
        assertEquals(CardState.REVIEW, history.get(1).nextState());
        assertEquals("FSRS-6", history.get(1).algorithmVersion());
        assertEquals("fsrs-6-default-v1", history.get(1).parameterVersion());
    }

    @Test
    void shouldRollbackReviewStateWhenReviewLogAppendFailsInTransaction() {
        Deck deck = deckService.createDeck(
                "Atomicity Check",
                TargetLanguage.EN,
                DefinitionLanguage.VI,
                ProficiencyLevel.CEFR_B1,
                null
        );
        Card card = cardService.createCard(
                deck.id(), CardType.VOCABULARY, "resilient", "/rɪˈzɪljənt/", "kiên cường",
                "adjective", ProficiencyLevel.CEFR_B1, null, null, List.of(), List.of()
        );

        // Truyền sessionId không tồn tại để vi phạm khóa ngoại khi chèn review_logs
        assertThrows(
                PersistenceException.class,
                () -> reviewService.submitReview(card.id(), ReviewRating.GOOD, 999999L, 900, baseTime)
        );

        // Xác nhận trạng thái review_states vẫn giữ nguyên NEW do toàn bộ giao dịch đã hoàn tác
        ReviewState unchanged = reviewStateRepository.findByCardId(card.id()).orElseThrow();
        assertEquals(CardState.NEW, unchanged.state());
        assertEquals(0, unchanged.repetitions());
        assertTrue(reviewLogRepository.findByCardId(card.id()).isEmpty());
    }

    @Test
    void shouldEnforceDailyNewCardAndReviewLimitsAndBuildBoundedQueue() {
        Deck deck = deckService.createDeck(
                "Daily Limit Deck",
                TargetLanguage.JA,
                DefinitionLanguage.VI,
                ProficiencyLevel.JLPT_N5,
                null
        );
        deckService.updateDeckSettings(new DeckSettings(
                deck.id(), 0.90, 36500, 1, 1, "1m,10m", "10m", false
        ));

        Card newCard1 = cardService.createCard(
                deck.id(), CardType.VOCABULARY, "水", "みず", "nước",
                "noun", ProficiencyLevel.JLPT_N5, null, null, List.of(), List.of()
        );
        Card newCard2 = cardService.createCard(
                deck.id(), CardType.VOCABULARY, "火", "ひ", "lửa",
                "noun", ProficiencyLevel.JLPT_N5, null, null, List.of(), List.of()
        );
        Card reviewCard1 = cardService.createCard(
                deck.id(), CardType.VOCABULARY, "山", "やま", "núi",
                "noun", ProficiencyLevel.JLPT_N5, null, null, List.of(), List.of()
        );
        Card reviewCard2 = cardService.createCard(
                deck.id(), CardType.VOCABULARY, "川", "かわ", "sông",
                "noun", ProficiencyLevel.JLPT_N5, null, null, List.of(), List.of()
        );

        // Thiết lập sẵn hai thẻ ở trạng thái REVIEW đã đến hạn ôn tập
        Instant past = baseTime.minus(Duration.ofDays(5));
        reviewStateRepository.save(new ReviewState(
                reviewCard1.id(), CardState.REVIEW, 5.0, 4.5, null,
                baseTime.minus(Duration.ofHours(1)), past, 5.0, 2.0, 2, 0, past
        ));
        reviewStateRepository.save(new ReviewState(
                reviewCard2.id(), CardState.REVIEW, 5.0, 4.5, null,
                baseTime.minus(Duration.ofMinutes(30)), past, 5.0, 2.0, 2, 0, past
        ));

        // Hàng đợi ngày bị giới hạn đúng 1 thẻ REVIEW và 1 thẻ NEW
        List<ReviewState> queue = reviewService.getDueStudyQueue(deck.id(), baseTime);
        assertEquals(2, queue.size());
        assertEquals(reviewCard1.id(), queue.get(0).cardId());
        assertEquals(newCard1.id(), queue.get(1).cardId());

        // Học xong 1 thẻ mới -> chạm giới hạn thẻ mới trong ngày
        reviewService.submitReview(newCard1.id(), ReviewRating.GOOD, null, 1000, baseTime);
        assertThrows(
                DailyLimitExceededException.class,
                () -> reviewService.submitReview(newCard2.id(), ReviewRating.GOOD, null, 1000, baseTime)
        );

        // Thẻ newCard1 đang ở trạng thái LEARNING vẫn có thể hoàn tất bước học ngắn hạn trong cùng ngày
        SchedulingResult graduatedNewCard = reviewService.submitReview(
                newCard1.id(), ReviewRating.GOOD, null, 1100, baseTime.plus(Duration.ofMinutes(10))
        );
        assertEquals(CardState.REVIEW, graduatedNewCard.nextState().state());

        // Ôn xong 1 thẻ REVIEW -> chạm giới hạn ôn tập trong ngày
        reviewService.submitReview(reviewCard1.id(), ReviewRating.GOOD, null, 1500, baseTime);
        assertThrows(
                DailyLimitExceededException.class,
                () -> reviewService.submitReview(reviewCard2.id(), ReviewRating.GOOD, null, 1500, baseTime)
        );

        DailyQuotaStatus quotaToday = reviewService.getDailyQuotaStatus(deck.id(), baseTime);
        assertEquals(1, quotaToday.newCardsReviewedToday());
        assertEquals(0, quotaToday.remainingNewCards());
        assertEquals(1, quotaToday.reviewsCompletedToday());
        assertEquals(0, quotaToday.remainingReviews());

        // Sang ngày hôm sau hạn mức được đặt lại tự động
        Instant nextDay = baseTime.plus(Duration.ofDays(1));
        DailyQuotaStatus quotaNextDay = reviewService.getDailyQuotaStatus(deck.id(), nextDay);
        assertEquals(1, quotaNextDay.remainingNewCards());
        assertEquals(1, quotaNextDay.remainingReviews());
    }
}
