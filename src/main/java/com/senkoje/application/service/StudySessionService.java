package com.senkoje.application.service;

import com.senkoje.application.dto.CardExtraData;
import com.senkoje.application.dto.StudyCardItem;
import com.senkoje.application.dto.StudySessionSnapshot;
import com.senkoje.application.dto.StudySessionStatus;
import com.senkoje.application.dto.SubmittedReviewOutcome;
import com.senkoje.application.mapper.CardExtraDataCodec;
import com.senkoje.domain.fsrs.SchedulingResult;
import com.senkoje.domain.model.Card;
import com.senkoje.domain.model.CardExample;
import com.senkoje.domain.model.CardMeaning;
import com.senkoje.domain.model.CardState;
import com.senkoje.domain.model.Deck;
import com.senkoje.domain.model.ReviewRating;
import com.senkoje.domain.model.ReviewState;
import com.senkoje.domain.model.StudySession;
import com.senkoje.domain.repository.StudySessionRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Dịch vụ quản lý phiên học tập thẻ ghi nhớ: xây dựng hàng đợi thẻ đến hạn và thẻ mới,
 * theo dõi bắt đầu/tạm dừng/tiếp tục/hoàn thành, đo thời gian phản hồi thực tế,
 * ngăn gửi trùng lặp và lưu tiến độ vào study_sessions ngay sau mỗi lượt đánh giá.
 */
public class StudySessionService {

    // Ngưỡng thời gian đưa lại thẻ đang học ngắn hạn (LEARNING/RELEARNING) vào cuối hàng đợi trong cùng phiên
    static final Duration INTRA_SESSION_REQUEUE_WINDOW = Duration.ofMinutes(20);

    private final ReviewService reviewService;
    private final CardService cardService;
    private final DeckService deckService;
    private final StudySessionRepository studySessionRepository;
    private final Clock clock;

    private final Map<Long, ActiveSessionState> activeSessions = new ConcurrentHashMap<>();

    /**
     * Khởi tạo dịch vụ quản lý phiên học tập với đầy đủ các phụ thuộc ứng dụng và đồng hồ tiêm vào.
     *
     * @param reviewService dịch vụ ôn tập FSRS-6 dùng để lấy hàng đợi, xem trước và ghi nhận đánh giá
     * @param cardService dịch vụ thẻ dùng để nạp nội dung thẻ, nghĩa bổ sung và câu ví dụ
     * @param deckService dịch vụ bộ thẻ dùng để nạp thông tin bộ thẻ
     * @param studySessionRepository kho lưu trữ bảng study_sessions trong SQLite
     * @param clock nguồn thời gian chuẩn hóa (UTC+7 trong ứng dụng chính hoặc đồng hồ cố định khi kiểm thử)
     */
    public StudySessionService(
            ReviewService reviewService,
            CardService cardService,
            DeckService deckService,
            StudySessionRepository studySessionRepository,
            Clock clock
    ) {
        this.reviewService = Objects.requireNonNull(reviewService, "reviewService must not be null");
        this.cardService = Objects.requireNonNull(cardService, "cardService must not be null");
        this.deckService = Objects.requireNonNull(deckService, "deckService must not be null");
        this.studySessionRepository = Objects.requireNonNull(
                studySessionRepository, "studySessionRepository must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    /**
     * Bắt đầu một phiên học mới cho một bộ thẻ cụ thể hoặc toàn bộ các bộ thẻ (khi deckId là null).
     *
     * @param deckId mã bộ thẻ cần học (null để gom thẻ đến hạn và thẻ mới từ mọi bộ thẻ)
     * @return ảnh chụp trạng thái ban đầu của phiên học vừa tạo
     */
    public StudySessionSnapshot startSession(Long deckId) {
        Instant now = clock.instant();
        List<ReviewState> dueQueue = buildInitialQueue(deckId, now);

        boolean emptyQueue = dueQueue.isEmpty();
        StudySession created = studySessionRepository.create(new StudySession(
                null,
                deckId,
                now,
                emptyQueue ? now : null,
                0,
                0
        ));

        Deque<ReviewState> queue = new ArrayDeque<>(dueQueue);
        ActiveSessionState state = new ActiveSessionState(created, queue, now, emptyQueue);
        activeSessions.put(created.id(), state);
        return buildSnapshot(state, now);
    }

    /**
     * Lấy ảnh chụp trạng thái hiện tại của một phiên học đang được theo dõi.
     *
     * @param sessionId mã phiên học
     * @return ảnh chụp trạng thái phiên học kèm thời lượng hoạt động cập nhật mới nhất
     */
    public StudySessionSnapshot getSessionSnapshot(long sessionId) {
        ActiveSessionState state = requireSession(sessionId);
        synchronized (state) {
            return buildSnapshot(state, clock.instant());
        }
    }

    /**
     * Tạm dừng phiên học đang chạy, dừng tính giờ học và lưu ngay thời lượng đã tích lũy vào SQLite.
     *
     * @param sessionId mã phiên học cần tạm dừng
     * @return ảnh chụp trạng thái phiên học sau khi tạm dừng
     */
    public StudySessionSnapshot pauseSession(long sessionId) {
        ActiveSessionState state = requireSession(sessionId);
        synchronized (state) {
            if (state.status == StudySessionStatus.COMPLETED) {
                throw new IllegalStateException("Cannot pause a completed study session: " + sessionId);
            }
            Instant now = clock.instant();
            if (state.status == StudySessionStatus.ACTIVE) {
                state.accumulateActiveTime(now);
                state.status = StudySessionStatus.PAUSED;
                persistSessionProgress(state, null);
            }
            return buildSnapshot(state, now);
        }
    }

    /**
     * Tiếp tục một phiên học đang tạm dừng và khởi động lại bộ đếm thời gian phản hồi cho thẻ hiện tại.
     *
     * @param sessionId mã phiên học cần tiếp tục
     * @return ảnh chụp trạng thái phiên học sau khi tiếp tục
     */
    public StudySessionSnapshot resumeSession(long sessionId) {
        ActiveSessionState state = requireSession(sessionId);
        synchronized (state) {
            if (state.status == StudySessionStatus.COMPLETED) {
                throw new IllegalStateException("Cannot resume a completed study session: " + sessionId);
            }
            Instant now = clock.instant();
            if (state.status == StudySessionStatus.PAUSED) {
                state.status = StudySessionStatus.ACTIVE;
                state.activeSegmentStartedAt = now;
                state.currentCardSegmentStartedAt = now;
            }
            return buildSnapshot(state, now);
        }
    }

    /**
     * Ghi nhận đánh giá FSRS-6 cho thẻ hiện tại ở đầu hàng đợi, đồng thời lưu ngay
     * review_states, review_logs và cập nhật study_sessions trước khi chuyển sang thẻ tiếp theo.
     *
     * @param sessionId mã phiên học đang hoạt động
     * @param expectedCardId mã thẻ mà giao diện đang hiển thị để chặn gửi trùng lặp hoặc dữ liệu cũ
     * @param rating mức đánh giá FSRS-6 từ 1 đến 4
     * @return kết quả lập lịch và ảnh chụp trạng thái tiếp theo của phiên học
     */
    public SubmittedReviewOutcome submitCurrentCardReview(
            long sessionId,
            long expectedCardId,
            ReviewRating rating
    ) {
        Objects.requireNonNull(rating, "rating must not be null");
        ActiveSessionState state = requireSession(sessionId);
        synchronized (state) {
            if (state.status == StudySessionStatus.COMPLETED) {
                throw new IllegalStateException("Study session is already completed: " + sessionId);
            }
            if (state.status == StudySessionStatus.PAUSED) {
                throw new IllegalStateException("Cannot submit review while study session is paused: " + sessionId);
            }
            if (state.submissionInFlight) {
                throw new IllegalStateException("A review submission is already in progress for session: " + sessionId);
            }

            ReviewState currentHead = state.queue.peekFirst();
            if (currentHead == null) {
                throw new IllegalStateException("No cards remaining in study session: " + sessionId);
            }
            if (!Objects.equals(currentHead.cardId(), expectedCardId)) {
                throw new IllegalStateException(
                        "Duplicate or stale review submission: expected card "
                                + currentHead.cardId() + " but received " + expectedCardId);
            }

            Instant now = clock.instant();
            state.submissionInFlight = true;
            try {
                int responseTimeMs = state.computeCurrentCardResponseTimeMs(now);
                SchedulingResult result = reviewService.submitReview(
                        expectedCardId,
                        rating,
                        sessionId,
                        responseTimeMs,
                        now
                );

                // Cập nhật hàng đợi và thống kê sau khi ReviewService đã lưu giao dịch SQLite thành công
                state.accumulateActiveTime(now);
                state.queue.pollFirst();
                if (shouldRequeueInSession(result.nextState(), now)) {
                    state.queue.addLast(result.nextState());
                }

                state.completedReviews++;
                state.ratingBreakdown.merge(rating, 1, Integer::sum);
                state.resetCurrentCardTimer(now);

                Instant completedAt = null;
                if (state.queue.isEmpty()) {
                    state.status = StudySessionStatus.COMPLETED;
                    completedAt = now;
                }
                persistSessionProgress(state, completedAt);

                StudySessionSnapshot snapshot = buildSnapshot(state, now);
                return new SubmittedReviewOutcome(result, snapshot);
            } finally {
                state.submissionInFlight = false;
            }
        }
    }

    /**
     * Kết thúc sớm hoặc hoàn tất một phiên học (ví dụ khi người dùng nhấn Esc hoặc đóng ứng dụng).
     * Mọi lượt ôn tập đã hoàn thành đều được giữ nguyên và thời lượng phiên học được lưu ngay vào SQLite.
     *
     * @param sessionId mã phiên học cần kết thúc
     * @return ảnh chụp tổng kết cuối cùng của phiên học
     */
    public StudySessionSnapshot finishSession(long sessionId) {
        ActiveSessionState state = requireSession(sessionId);
        synchronized (state) {
            Instant now = clock.instant();
            if (state.status != StudySessionStatus.COMPLETED) {
                if (state.status == StudySessionStatus.ACTIVE) {
                    state.accumulateActiveTime(now);
                }
                state.status = StudySessionStatus.COMPLETED;
                persistSessionProgress(state, now);
            }
            return buildSnapshot(state, now);
        }
    }

    /**
     * Đếm số thẻ đến hạn và thẻ mới sẵn sàng học trong ngày hiện tại cho một bộ thẻ hoặc toàn bộ hệ thống.
     *
     * @param deckId mã bộ thẻ (null để tính tổng trên tất cả bộ thẻ)
     * @return số lượng thẻ sẵn sàng đưa vào phiên học
     */
    public int countAvailableStudyCards(Long deckId) {
        return buildInitialQueue(deckId, clock.instant()).size();
    }

    private List<ReviewState> buildInitialQueue(Long deckId, Instant now) {
        if (deckId != null) {
            deckService.getDeck(deckId)
                    .orElseThrow(() -> new IllegalArgumentException("Deck not found: " + deckId));
            return reviewService.getDueStudyQueue(deckId, now);
        }

        // Khi học toàn bộ bộ thẻ, gom hàng đợi theo từng bộ thẻ và giữ nguyên thứ tự ưu tiên của ReviewService
        List<Deck> decks = deckService.searchDecks(null, null, null);
        Map<Long, ReviewState> uniqueQueue = new LinkedHashMap<>();
        for (Deck deck : decks) {
            for (ReviewState item : reviewService.getDueStudyQueue(deck.id(), now)) {
                uniqueQueue.putIfAbsent(item.cardId(), item);
            }
        }
        return new ArrayList<>(uniqueQueue.values());
    }

    private boolean shouldRequeueInSession(ReviewState nextState, Instant now) {
        if (nextState.state() != CardState.LEARNING && nextState.state() != CardState.RELEARNING) {
            return false;
        }
        if (nextState.dueAt() == null) {
            return false;
        }
        Duration waitDuration = Duration.between(now, nextState.dueAt());
        return !waitDuration.isNegative() && waitDuration.compareTo(INTRA_SESSION_REQUEUE_WINDOW) <= 0;
    }

    private void persistSessionProgress(ActiveSessionState state, Instant completedAt) {
        Instant finalCompletedAt = completedAt != null ? completedAt : state.persistedSession.completedAt();
        StudySession updated = new StudySession(
                state.persistedSession.id(),
                state.persistedSession.deckId(),
                state.persistedSession.startedAt(),
                finalCompletedAt,
                state.completedReviews,
                state.getAccumulatedDurationSeconds()
        );
        state.persistedSession = studySessionRepository.update(updated);
    }

    private StudySessionSnapshot buildSnapshot(ActiveSessionState state, Instant now) {
        StudyCardItem currentCard = null;
        if (state.status != StudySessionStatus.COMPLETED && !state.queue.isEmpty()) {
            ReviewState head = state.queue.peekFirst();
            currentCard = loadStudyCardItem(head, now);
        }
        return new StudySessionSnapshot(
                state.persistedSession,
                state.status,
                currentCard,
                state.queue.size(),
                state.completedReviews,
                state.initialQueueSize,
                state.computeLiveDurationSeconds(now),
                new EnumMap<>(state.ratingBreakdown)
        );
    }

    private StudyCardItem loadStudyCardItem(ReviewState reviewState, Instant now) {
        long cardId = reviewState.cardId();
        Card card = cardService.getCard(cardId)
                .orElseThrow(() -> new IllegalStateException("Card not found in active session: " + cardId));
        Deck deck = deckService.getDeck(card.deckId())
                .orElseThrow(() -> new IllegalStateException("Deck not found for card: " + cardId));
        List<CardMeaning> meanings = cardService.getMeanings(cardId);
        List<CardExample> examples = cardService.getExamples(cardId);
        CardExtraData extraData = CardExtraDataCodec.fromJson(card.extraData());
        Map<ReviewRating, SchedulingResult> previews = reviewService.previewReview(cardId, now);
        return new StudyCardItem(card, deck, reviewState, meanings, examples, extraData, previews);
    }

    private ActiveSessionState requireSession(long sessionId) {
        ActiveSessionState state = activeSessions.get(sessionId);
        if (state == null) {
            throw new IllegalArgumentException("Active study session not found: " + sessionId);
        }
        return state;
    }

    /**
     * Trạng thái nội bộ trong bộ nhớ của một phiên học đang được điều phối.
     */
    private static final class ActiveSessionState {
        private StudySession persistedSession;
        private StudySessionStatus status;
        private final Deque<ReviewState> queue;
        private final int initialQueueSize;
        private int completedReviews;
        private final Map<ReviewRating, Integer> ratingBreakdown = new EnumMap<>(ReviewRating.class);

        private long accumulatedSessionMs;
        private Instant activeSegmentStartedAt;

        private long accumulatedCardMs;
        private Instant currentCardSegmentStartedAt;
        private boolean submissionInFlight;

        private ActiveSessionState(
                StudySession persistedSession,
                Deque<ReviewState> queue,
                Instant startedAt,
                boolean emptyQueue
        ) {
            this.persistedSession = persistedSession;
            this.queue = queue;
            this.initialQueueSize = queue.size();
            this.status = emptyQueue ? StudySessionStatus.COMPLETED : StudySessionStatus.ACTIVE;
            this.activeSegmentStartedAt = startedAt;
            this.currentCardSegmentStartedAt = startedAt;
            for (ReviewRating rating : ReviewRating.values()) {
                this.ratingBreakdown.put(rating, 0);
            }
        }

        private void accumulateActiveTime(Instant now) {
            if (activeSegmentStartedAt != null) {
                long delta = Math.max(0L, Duration.between(activeSegmentStartedAt, now).toMillis());
                accumulatedSessionMs += delta;
                activeSegmentStartedAt = now;
            }
            if (currentCardSegmentStartedAt != null) {
                long cardDelta = Math.max(0L, Duration.between(currentCardSegmentStartedAt, now).toMillis());
                accumulatedCardMs += cardDelta;
                currentCardSegmentStartedAt = now;
            }
        }

        private int computeCurrentCardResponseTimeMs(Instant now) {
            long totalMs = accumulatedCardMs;
            if (status == StudySessionStatus.ACTIVE && currentCardSegmentStartedAt != null) {
                totalMs += Math.max(0L, Duration.between(currentCardSegmentStartedAt, now).toMillis());
            }
            return (int) Math.min(Integer.MAX_VALUE, Math.max(0L, totalMs));
        }

        private void resetCurrentCardTimer(Instant now) {
            accumulatedCardMs = 0L;
            currentCardSegmentStartedAt = now;
        }

        private int getAccumulatedDurationSeconds() {
            return (int) Math.min(Integer.MAX_VALUE, accumulatedSessionMs / 1000L);
        }

        private int computeLiveDurationSeconds(Instant now) {
            long totalMs = accumulatedSessionMs;
            if (status == StudySessionStatus.ACTIVE && activeSegmentStartedAt != null) {
                totalMs += Math.max(0L, Duration.between(activeSegmentStartedAt, now).toMillis());
            }
            return (int) Math.min(Integer.MAX_VALUE, totalMs / 1000L);
        }
    }
}
