package com.senkoje.application.service;

import com.senkoje.application.dto.DailyQuotaStatus;
import com.senkoje.domain.fsrs.FsrsScheduler;
import com.senkoje.domain.fsrs.SchedulingResult;
import com.senkoje.domain.model.Card;
import com.senkoje.domain.model.CardState;
import com.senkoje.domain.model.DeckSettings;
import com.senkoje.domain.model.ReviewLog;
import com.senkoje.domain.model.ReviewRating;
import com.senkoje.domain.model.ReviewState;
import com.senkoje.domain.repository.CardRepository;
import com.senkoje.domain.repository.DeckRepository;
import com.senkoje.domain.repository.ReviewLogRepository;
import com.senkoje.domain.repository.ReviewStateRepository;
import com.senkoje.domain.repository.ReviewTransactionRepository;
import com.senkoje.domain.repository.ReviewTransactionRepository.RecordedReview;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Dịch vụ ứng dụng điều phối quy trình ôn tập FSRS-6: tải trạng thái và cấu hình,
 * kiểm tra hạn mức ngày, tính lịch mới, cập nhật ReviewState và ghi ReviewLog trong cùng một giao dịch.
 */
public class ReviewService {

    private final CardRepository cardRepository;
    private final DeckRepository deckRepository;
    private final ReviewStateRepository reviewStateRepository;
    private final ReviewLogRepository reviewLogRepository;
    private final ReviewTransactionRepository reviewTransactionRepository;
    private final FsrsScheduler scheduler;
    private final Clock clock;

    /**
     * Khởi tạo dịch vụ ôn tập với đồng hồ UTC mặc định của hệ thống.
     *
     * @param cardRepository kho lưu trữ thẻ
     * @param deckRepository kho lưu trữ bộ thẻ và cấu hình
     * @param reviewStateRepository kho lưu trữ trạng thái lập lịch
     * @param reviewLogRepository kho lưu trữ nhật ký ôn tập
     * @param reviewTransactionRepository kho lưu trữ giao dịch nguyên tử cho lượt ôn tập
     * @param scheduler bộ lập lịch FSRS-6
     */
    public ReviewService(
            CardRepository cardRepository,
            DeckRepository deckRepository,
            ReviewStateRepository reviewStateRepository,
            ReviewLogRepository reviewLogRepository,
            ReviewTransactionRepository reviewTransactionRepository,
            FsrsScheduler scheduler
    ) {
        this(
                cardRepository,
                deckRepository,
                reviewStateRepository,
                reviewLogRepository,
                reviewTransactionRepository,
                scheduler,
                Clock.systemUTC()
        );
    }

    /**
     * Khởi tạo dịch vụ ôn tập với nguồn đồng hồ được tiêm vào để kiểm thử tất định.
     *
     * @param cardRepository kho lưu trữ thẻ
     * @param deckRepository kho lưu trữ bộ thẻ và cấu hình
     * @param reviewStateRepository kho lưu trữ trạng thái lập lịch
     * @param reviewLogRepository kho lưu trữ nhật ký ôn tập
     * @param reviewTransactionRepository kho lưu trữ giao dịch nguyên tử cho lượt ôn tập
     * @param scheduler bộ lập lịch FSRS-6
     * @param clock nguồn thời gian và múi giờ xác định ranh giới ngày
     */
    public ReviewService(
            CardRepository cardRepository,
            DeckRepository deckRepository,
            ReviewStateRepository reviewStateRepository,
            ReviewLogRepository reviewLogRepository,
            ReviewTransactionRepository reviewTransactionRepository,
            FsrsScheduler scheduler,
            Clock clock
    ) {
        this.cardRepository = Objects.requireNonNull(cardRepository, "cardRepository must not be null");
        this.deckRepository = Objects.requireNonNull(deckRepository, "deckRepository must not be null");
        this.reviewStateRepository = Objects.requireNonNull(
                reviewStateRepository,
                "reviewStateRepository must not be null"
        );
        this.reviewLogRepository = Objects.requireNonNull(
                reviewLogRepository,
                "reviewLogRepository must not be null"
        );
        this.reviewTransactionRepository = Objects.requireNonNull(
                reviewTransactionRepository,
                "reviewTransactionRepository must not be null"
        );
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    /**
     * Xem trước kết quả lập lịch cho cả 4 nút đánh giá tại thời điểm hiện tại mà không ghi vào cơ sở dữ liệu.
     *
     * @param cardId mã thẻ cần xem trước
     * @return bản đồ kết quả xem trước theo từng mức đánh giá
     */
    public Map<ReviewRating, SchedulingResult> previewReview(long cardId) {
        return previewReview(cardId, clock.instant());
    }

    /**
     * Xem trước kết quả lập lịch cho cả 4 nút đánh giá tại mốc thời gian tường minh mà không thay đổi dữ liệu.
     *
     * @param cardId mã thẻ cần xem trước
     * @param reviewedAt mốc thời gian ôn tập tường minh
     * @return bản đồ kết quả xem trước theo từng mức đánh giá
     */
    public Map<ReviewRating, SchedulingResult> previewReview(long cardId, Instant reviewedAt) {
        Objects.requireNonNull(reviewedAt, "reviewedAt must not be null");
        Card card = requireCard(cardId);
        ReviewState currentState = requireReviewState(cardId);
        DeckSettings settings = requireDeckSettings(card.deckId());
        return scheduler.previewAllRatings(currentState, settings, reviewedAt);
    }

    /**
     * Ghi nhận một lượt ôn tập sử dụng thời điểm hiện tại từ đồng hồ đã tiêm vào.
     *
     * @param cardId mã thẻ được ôn tập
     * @param rating mức đánh giá từ 1 đến 4
     * @param sessionId mã phiên học liên quan (có thể null)
     * @param responseTimeMs thời gian phản hồi tính bằng mili giây (có thể null)
     * @return kết quả lập lịch đã được lưu kèm bản ghi nhật ký có mã định danh
     */
    public SchedulingResult submitReview(
            long cardId,
            ReviewRating rating,
            Long sessionId,
            Integer responseTimeMs
    ) {
        return submitReview(cardId, rating, sessionId, responseTimeMs, clock.instant());
    }

    /**
     * Ghi nhận một lượt ôn tập tại mốc thời gian tường minh, kiểm tra hạn mức ngày
     * và lưu đồng thời ReviewState cùng ReviewLog bất biến trong một giao dịch SQLite duy nhất.
     *
     * @param cardId mã thẻ được ôn tập
     * @param rating mức đánh giá từ 1 đến 4
     * @param sessionId mã phiên học liên quan (có thể null)
     * @param responseTimeMs thời gian phản hồi tính bằng mili giây (có thể null)
     * @param reviewedAt thời điểm thực hiện lượt ôn tập
     * @return kết quả lập lịch đã được lưu kèm bản ghi nhật ký có mã định danh
     */
    public SchedulingResult submitReview(
            long cardId,
            ReviewRating rating,
            Long sessionId,
            Integer responseTimeMs,
            Instant reviewedAt
    ) {
        Objects.requireNonNull(rating, "rating must not be null");
        Objects.requireNonNull(reviewedAt, "reviewedAt must not be null");

        Card card = requireCard(cardId);
        if (card.suspended()) {
            throw new IllegalStateException("Cannot review a suspended card: " + cardId);
        }

        ReviewState currentState = requireReviewState(cardId);
        DeckSettings settings = requireDeckSettings(card.deckId());

        enforceDailyLimits(card.deckId(), currentState.state(), settings, reviewedAt);

        SchedulingResult computed = scheduler.schedule(
                currentState,
                rating,
                settings,
                reviewedAt,
                sessionId,
                responseTimeMs
        );
        RecordedReview recorded = reviewTransactionRepository.saveStateAndAppendLog(
                computed.nextState(),
                computed.reviewLog()
        );
        return new SchedulingResult(
                recorded.reviewState(),
                recorded.reviewLog(),
                computed.scheduledInterval()
        );
    }

    /**
     * Kiểm tra trạng thái hạn mức học thẻ mới và ôn tập dài hạn của một bộ thẻ trong ngày hiện tại.
     *
     * @param deckId mã bộ thẻ
     * @return thông tin hạn mức ngày hiện tại của bộ thẻ
     */
    public DailyQuotaStatus getDailyQuotaStatus(long deckId) {
        return getDailyQuotaStatus(deckId, clock.instant());
    }

    /**
     * Kiểm tra trạng thái hạn mức học thẻ mới và ôn tập dài hạn của một bộ thẻ tại mốc thời gian tường minh.
     *
     * @param deckId mã bộ thẻ
     * @param asOf mốc thời gian kiểm tra
     * @return thông tin hạn mức ngày của bộ thẻ
     */
    public DailyQuotaStatus getDailyQuotaStatus(long deckId, Instant asOf) {
        Objects.requireNonNull(asOf, "asOf must not be null");
        DeckSettings settings = requireDeckSettings(deckId);
        DayWindow window = dayWindowFor(asOf);

        int newReviewedToday = reviewLogRepository.countNewCardsReviewedBetween(
                deckId,
                window.startInclusive(),
                window.endExclusive()
        );
        int reviewsToday = reviewLogRepository.countReviewsCompletedBetween(
                deckId,
                window.startInclusive(),
                window.endExclusive()
        );

        return new DailyQuotaStatus(
                deckId,
                newReviewedToday,
                settings.newCardsPerDay(),
                reviewsToday,
                settings.maximumReviewsPerDay()
        );
    }

    /**
     * Lấy hàng đợi học tập đến hạn của một bộ thẻ theo giới hạn thẻ mới và giới hạn ôn tập hằng ngày.
     *
     * @param deckId mã bộ thẻ
     * @return danh sách trạng thái thẻ đủ điều kiện ôn tập trong ngày
     */
    public List<ReviewState> getDueStudyQueue(long deckId) {
        return getDueStudyQueue(deckId, clock.instant());
    }

    /**
     * Lấy hàng đợi học tập đến hạn của một bộ thẻ tại mốc thời gian tường minh,
     * ưu tiên thẻ đang học ngắn hạn/học lại, sau đó đến thẻ ôn tập dài hạn và thẻ mới trong hạn mức.
     *
     * @param deckId mã bộ thẻ
     * @param asOf mốc thời gian kiểm tra đến hạn
     * @return danh sách trạng thái thẻ đủ điều kiện học
     */
    public List<ReviewState> getDueStudyQueue(long deckId, Instant asOf) {
        Objects.requireNonNull(asOf, "asOf must not be null");
        DailyQuotaStatus quota = getDailyQuotaStatus(deckId, asOf);

        List<ReviewState> dueStates = reviewStateRepository.findDueByDeckId(deckId, asOf);
        List<ReviewState> queue = new ArrayList<>();

        int remainingReviews = quota.remainingReviews();
        List<ReviewState> reviewCandidates = new ArrayList<>();
        for (ReviewState state : dueStates) {
            if (state.state() == CardState.LEARNING || state.state() == CardState.RELEARNING) {
                queue.add(state);
            } else if (state.state() == CardState.REVIEW) {
                reviewCandidates.add(state);
            }
        }

        int reviewsToInclude = Math.min(remainingReviews, reviewCandidates.size());
        for (int i = 0; i < reviewsToInclude; i++) {
            queue.add(reviewCandidates.get(i));
        }

        int remainingNew = quota.remainingNewCards();
        if (remainingNew > 0) {
            queue.addAll(reviewStateRepository.findNewByDeckId(deckId, remainingNew));
        }

        return List.copyOf(queue);
    }

    /**
     * Lấy toàn bộ lịch sử nhật ký ôn tập bất biến của một thẻ theo thứ tự thời gian tăng dần.
     *
     * @param cardId mã thẻ
     * @return danh sách nhật ký ôn tập của thẻ
     */
    public List<ReviewLog> getCardReviewHistory(long cardId) {
        return reviewLogRepository.findByCardId(cardId);
    }

    private void enforceDailyLimits(
            long deckId,
            CardState currentState,
            DeckSettings settings,
            Instant reviewedAt
    ) {
        DayWindow window = dayWindowFor(reviewedAt);
        if (currentState == CardState.NEW) {
            int newReviewedToday = reviewLogRepository.countNewCardsReviewedBetween(
                    deckId,
                    window.startInclusive(),
                    window.endExclusive()
            );
            if (newReviewedToday >= settings.newCardsPerDay()) {
                throw new DailyLimitExceededException(
                        "Daily new card limit reached for deck " + deckId + " (" + settings.newCardsPerDay() + ")"
                );
            }
        } else if (currentState == CardState.REVIEW) {
            int reviewsToday = reviewLogRepository.countReviewsCompletedBetween(
                    deckId,
                    window.startInclusive(),
                    window.endExclusive()
            );
            if (reviewsToday >= settings.maximumReviewsPerDay()) {
                throw new DailyLimitExceededException(
                        "Daily review limit reached for deck " + deckId + " (" + settings.maximumReviewsPerDay() + ")"
                );
            }
        }
    }

    private DayWindow dayWindowFor(Instant instant) {
        ZoneId zone = clock.getZone();
        LocalDate date = instant.atZone(zone).toLocalDate();
        Instant start = date.atStartOfDay(zone).toInstant();
        Instant end = date.plusDays(1).atStartOfDay(zone).toInstant();
        return new DayWindow(start, end);
    }

    private Card requireCard(long cardId) {
        return cardRepository.findById(cardId)
                .orElseThrow(() -> new IllegalArgumentException("Card not found: " + cardId));
    }

    private ReviewState requireReviewState(long cardId) {
        return reviewStateRepository.findByCardId(cardId)
                .orElseThrow(() -> new IllegalArgumentException("Review state not found for card: " + cardId));
    }

    private DeckSettings requireDeckSettings(long deckId) {
        return deckRepository.findSettingsByDeckId(deckId)
                .orElseThrow(() -> new IllegalArgumentException("Deck settings not found for deck: " + deckId));
    }

    private record DayWindow(Instant startInclusive, Instant endExclusive) {
    }
}
