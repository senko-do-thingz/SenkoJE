package com.senkoje.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.senkoje.application.dto.StudySessionSnapshot;
import com.senkoje.application.dto.StudySessionStatus;
import com.senkoje.application.dto.SubmittedReviewOutcome;
import com.senkoje.domain.fsrs.FsrsScheduler;
import com.senkoje.domain.model.Card;
import com.senkoje.domain.model.CardState;
import com.senkoje.domain.model.CardType;
import com.senkoje.domain.model.Deck;
import com.senkoje.domain.model.DeckSettings;
import com.senkoje.domain.model.DefinitionLanguage;
import com.senkoje.domain.model.ProficiencyLevel;
import com.senkoje.domain.model.ReviewLog;
import com.senkoje.domain.model.ReviewRating;
import com.senkoje.domain.model.StudySession;
import com.senkoje.domain.model.TargetLanguage;
import com.senkoje.domain.service.CardValidator;
import com.senkoje.domain.service.DeckSettingsValidator;
import com.senkoje.domain.service.DeckValidator;
import com.senkoje.persistence.migration.DatabaseMigrator;
import com.senkoje.persistence.repository.SqliteCardRepository;
import com.senkoje.persistence.repository.SqliteDeckRepository;
import com.senkoje.persistence.repository.SqliteReviewLogRepository;
import com.senkoje.persistence.repository.SqliteReviewStateRepository;
import com.senkoje.persistence.repository.SqliteReviewTransactionRepository;
import com.senkoje.persistence.repository.SqliteStudySessionRepository;
import com.senkoje.persistence.sqlite.SqliteConnectionManager;
import java.nio.file.Path;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Kiểm thử tích hợp cho StudySessionService xác minh hàng đợi học tập,
 * tạm dừng/tiếp tục không tính thời gian nghỉ, lưu ngay study_sessions sau mỗi lượt đánh giá,
 * chặn gửi trùng lặp và bảo toàn dữ liệu khi thoát giữa phiên.
 */
class StudySessionServiceTest {

    private static final ZoneId VIETNAM_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    @TempDir
    Path tempDir;

    private MutableClock clock;
    private DeckService deckService;
    private CardService cardService;
    private ReviewService reviewService;
    private StudySessionService studySessionService;
    private SqliteStudySessionRepository studySessionRepository;
    private SqliteReviewLogRepository reviewLogRepository;

    @BeforeEach
    void setUp() throws SQLException {
        Path dbPath = tempDir.resolve("study-session-service.db");
        clock = new MutableClock(Instant.parse("2026-06-10T02:00:00Z"), VIETNAM_ZONE);

        SqliteConnectionManager manager = new SqliteConnectionManager(dbPath);
        new DatabaseMigrator(manager).migrate();

        SqliteDeckRepository deckRepository = new SqliteDeckRepository(manager);
        SqliteCardRepository cardRepository = new SqliteCardRepository(manager);
        SqliteReviewStateRepository reviewStateRepository = new SqliteReviewStateRepository(manager);
        reviewLogRepository = new SqliteReviewLogRepository(manager);
        SqliteReviewTransactionRepository reviewTransactionRepository = new SqliteReviewTransactionRepository(manager);
        studySessionRepository = new SqliteStudySessionRepository(manager);

        deckService = new DeckService(deckRepository, new DeckValidator(), new DeckSettingsValidator(), clock);
        cardService = new CardService(cardRepository, new CardValidator(), clock);
        reviewService = new ReviewService(
                cardRepository,
                deckRepository,
                reviewStateRepository,
                reviewLogRepository,
                reviewTransactionRepository,
                new FsrsScheduler(),
                clock
        );
        studySessionService = new StudySessionService(
                reviewService,
                cardService,
                deckService,
                studySessionRepository,
                clock
        );
    }

    @Test
    void shouldPersistProgressImmediatelyExcludePausedTimeAndCompleteSession() {
        Deck deck = deckService.createDeck(
                "JLPT N5 Core", TargetLanguage.JA, DefinitionLanguage.VI, ProficiencyLevel.JLPT_N5, null);
        deckService.updateDeckSettings(new DeckSettings(
                deck.id(), 0.90, 36500, 20, 200, "1m,10m", "10m", false));

        Card card1 = cardService.createCard(
                deck.id(), CardType.VOCABULARY, "猫", "ねこ", "con mèo",
                "noun", ProficiencyLevel.JLPT_N5, null, null, List.of(), List.of());
        Card card2 = cardService.createCard(
                deck.id(), CardType.VOCABULARY, "犬", "いぬ", "con chó",
                "noun", ProficiencyLevel.JLPT_N5, null, null, List.of(), List.of());

        StudySessionSnapshot started = studySessionService.startSession(deck.id());
        long sessionId = started.session().id();
        assertEquals(StudySessionStatus.ACTIVE, started.status());
        assertEquals(2, started.initialQueueSize());
        assertEquals(2, started.remainingCards());
        assertEquals(card1.id(), started.currentCard().card().id());
        assertEquals(4, started.currentCard().ratingPreviews().size());

        // Người học suy nghĩ 4 giây rồi tạm dừng 60 giây, sau đó tiếp tục thêm 2 giây -> thời gian phản hồi phải là 6000 ms
        clock.advance(Duration.ofSeconds(4));
        StudySessionSnapshot paused = studySessionService.pauseSession(sessionId);
        assertEquals(StudySessionStatus.PAUSED, paused.status());
        assertEquals(4, paused.activeDurationSeconds());

        clock.advance(Duration.ofSeconds(60));
        StudySessionSnapshot resumed = studySessionService.resumeSession(sessionId);
        assertEquals(StudySessionStatus.ACTIVE, resumed.status());
        assertEquals(4, resumed.activeDurationSeconds());

        clock.advance(Duration.ofSeconds(2));
        // Đánh giá EASY cho thẻ 1 để tốt nghiệp ngay sang REVIEW (không đưa lại vào hàng đợi phiên)
        SubmittedReviewOutcome firstOutcome = studySessionService.submitCurrentCardReview(
                sessionId, card1.id(), ReviewRating.EASY);
        assertEquals(CardState.REVIEW, firstOutcome.schedulingResult().nextState().state());
        assertEquals(6000, firstOutcome.schedulingResult().reviewLog().responseTimeMs());
        assertEquals(1, firstOutcome.sessionSnapshot().completedReviews());
        assertEquals(1, firstOutcome.sessionSnapshot().remainingCards());
        assertEquals(card2.id(), firstOutcome.sessionSnapshot().currentCard().card().id());

        // Kiểm tra bảng study_sessions trong SQLite đã được cập nhật ngay lập tức sau thẻ 1
        StudySession persistedAfterFirst = studySessionRepository.findById(sessionId).orElseThrow();
        assertEquals(1, persistedAfterFirst.cardsReviewed());
        assertEquals(6, persistedAfterFirst.durationSeconds());
        assertNull(persistedAfterFirst.completedAt());

        // Thẻ 2 được đánh giá EASY sau 3 giây -> phiên học tự động hoàn tất
        clock.advance(Duration.ofSeconds(3));
        SubmittedReviewOutcome secondOutcome = studySessionService.submitCurrentCardReview(
                sessionId, card2.id(), ReviewRating.EASY);
        assertTrue(secondOutcome.sessionSnapshot().isCompleted());
        assertNull(secondOutcome.sessionSnapshot().currentCard());
        assertEquals(2, secondOutcome.sessionSnapshot().completedReviews());
        assertEquals(9, secondOutcome.sessionSnapshot().activeDurationSeconds());

        StudySession persistedCompleted = studySessionRepository.findById(sessionId).orElseThrow();
        assertEquals(2, persistedCompleted.cardsReviewed());
        assertEquals(9, persistedCompleted.durationSeconds());
        assertNotNull(persistedCompleted.completedAt());
    }

    @Test
    void shouldRejectDuplicateSubmissionAndPreserveCompletedReviewsOnMidSessionExit() {
        Deck deck = deckService.createDeck(
                "English Phrases", TargetLanguage.EN, DefinitionLanguage.VI, ProficiencyLevel.CEFR_B1, null);
        Card card1 = cardService.createCard(
                deck.id(), CardType.PHRASE, "break the ice", null, "phá vỡ sự ngại ngùng",
                "idiom", ProficiencyLevel.CEFR_B1, null, null, List.of(), List.of());
        cardService.createCard(
                deck.id(), CardType.PHRASE, "piece of cake", null, "dễ như ăn bánh",
                "idiom", ProficiencyLevel.CEFR_B1, null, null, List.of(), List.of());

        StudySessionSnapshot started = studySessionService.startSession(deck.id());
        long sessionId = started.session().id();

        clock.advance(Duration.ofSeconds(5));
        studySessionService.submitCurrentCardReview(sessionId, card1.id(), ReviewRating.EASY);

        // Gửi lại đúng card1.id thêm một lần nữa phải bị từ chối và không tạo nhật ký trùng lặp
        assertThrows(
                IllegalStateException.class,
                () -> studySessionService.submitCurrentCardReview(sessionId, card1.id(), ReviewRating.EASY)
        );
        List<ReviewLog> logsForCard1 = reviewLogRepository.findByCardId(card1.id());
        assertEquals(1, logsForCard1.size());
        assertEquals(sessionId, logsForCard1.getFirst().sessionId());

        // Thoát giữa phiên (Esc) phải bảo toàn lượt ôn đã hoàn thành và chốt thời điểm kết thúc
        clock.advance(Duration.ofSeconds(2));
        StudySessionSnapshot finished = studySessionService.finishSession(sessionId);
        assertEquals(StudySessionStatus.COMPLETED, finished.status());
        assertEquals(1, finished.completedReviews());
        assertEquals(7, finished.activeDurationSeconds());

        StudySession persisted = studySessionRepository.findById(sessionId).orElseThrow();
        assertEquals(1, persisted.cardsReviewed());
        assertEquals(7, persisted.durationSeconds());
        assertNotNull(persisted.completedAt());
    }

    @Test
    void shouldRequeueShortTermLearningCardWithinSameSession() {
        Deck deck = deckService.createDeck(
                "Grammar N4", TargetLanguage.JA, DefinitionLanguage.VI, ProficiencyLevel.JLPT_N4, null);
        deckService.updateDeckSettings(new DeckSettings(
                deck.id(), 0.90, 36500, 20, 200, "1m,10m", "10m", false));

        Card card = cardService.createCard(
                deck.id(), CardType.GRAMMAR, "～てみる", null, "thử làm gì đó",
                "grammar", ProficiencyLevel.JLPT_N4, null, null, List.of(), List.of());

        StudySessionSnapshot started = studySessionService.startSession(deck.id());
        long sessionId = started.session().id();

        // Đánh giá AGAIN đẩy thẻ sang bước học 1 phút -> được đưa lại vào hàng đợi của phiên hiện tại
        clock.advance(Duration.ofSeconds(3));
        SubmittedReviewOutcome againOutcome = studySessionService.submitCurrentCardReview(
                sessionId, card.id(), ReviewRating.AGAIN);
        assertEquals(CardState.LEARNING, againOutcome.schedulingResult().nextState().state());
        assertEquals(StudySessionStatus.ACTIVE, againOutcome.sessionSnapshot().status());
        assertEquals(1, againOutcome.sessionSnapshot().remainingCards());
        assertEquals(card.id(), againOutcome.sessionSnapshot().currentCard().card().id());
    }

    @Test
    void shouldCompleteImmediatelyWhenQueueIsEmpty() {
        Deck deck = deckService.createDeck(
                "Empty Deck", TargetLanguage.EN, DefinitionLanguage.VI, ProficiencyLevel.CEFR_A1, null);

        StudySessionSnapshot started = studySessionService.startSession(deck.id());
        assertTrue(started.isCompleted());
        assertNull(started.currentCard());
        assertEquals(0, started.remainingCards());
        assertNotNull(started.session().completedAt());
    }

    /**
     * Đồng hồ khả biến phục vụ kiểm thử tất định thời gian phản hồi và thời lượng phiên học.
     */
    private static final class MutableClock extends Clock {
        private Instant current;
        private final ZoneId zone;

        private MutableClock(Instant initial, ZoneId zone) {
            this.current = initial;
            this.zone = zone;
        }

        private void advance(Duration duration) {
            this.current = this.current.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return zone;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return new MutableClock(current, zone);
        }

        @Override
        public Instant instant() {
            return current;
        }
    }
}
