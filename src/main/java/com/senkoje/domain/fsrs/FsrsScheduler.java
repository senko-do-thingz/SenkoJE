package com.senkoje.domain.fsrs;

import com.senkoje.domain.model.CardState;
import com.senkoje.domain.model.DeckSettings;
import com.senkoje.domain.model.ReviewLog;
import com.senkoje.domain.model.ReviewRating;
import com.senkoje.domain.model.ReviewState;
import com.senkoje.domain.service.DeckSettingsValidator;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Bộ lập lịch máy trạng thái FSRS-6 xử lý toàn bộ chuyển đổi trạng thái
 * NEW, LEARNING, REVIEW, RELEARNING cho cả 4 mức đánh giá Again, Hard, Good và Easy.
 */
public class FsrsScheduler {

    private static final double SECONDS_PER_DAY = 86_400.0;

    private final FsrsMath math;
    private final FsrsFuzzer fuzzer;
    private final DeckSettingsValidator settingsValidator;
    private final Clock clock;

    /**
     * Khởi tạo bộ lập lịch với tham số FSRS-6 mặc định và đồng hồ UTC hệ thống.
     */
    public FsrsScheduler() {
        this(FsrsParameters.defaults(), Clock.systemUTC());
    }

    /**
     * Khởi tạo bộ lập lịch với bộ tham số FSRS-6 chỉ định và đồng hồ UTC hệ thống.
     *
     * @param parameters bộ tham số FSRS-6
     */
    public FsrsScheduler(FsrsParameters parameters) {
        this(parameters, Clock.systemUTC());
    }

    /**
     * Khởi tạo bộ lập lịch với bộ tham số FSRS-6 và nguồn đồng hồ được tiêm vào để kiểm thử tất định.
     *
     * @param parameters bộ tham số FSRS-6
     * @param clock nguồn đồng hồ
     */
    public FsrsScheduler(FsrsParameters parameters, Clock clock) {
        this.math = new FsrsMath(Objects.requireNonNull(parameters, "parameters must not be null"));
        this.fuzzer = new FsrsFuzzer();
        this.settingsValidator = new DeckSettingsValidator();
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    /**
     * Trả về bộ tham số FSRS-6 hiện tại của bộ lập lịch.
     *
     * @return bộ tham số FSRS-6
     */
    public FsrsParameters parameters() {
        return math.parameters();
    }

    /**
     * Trả về bộ công thức toán học FSRS-6 cốt lõi.
     *
     * @return đối tượng FsrsMath
     */
    public FsrsMath math() {
        return math;
    }

    /**
     * Tính kết quả lập lịch sử dụng thời điểm hiện tại từ đồng hồ đã tiêm vào.
     *
     * @param currentState trạng thái hiện tại của thẻ
     * @param rating mức đánh giá của người học
     * @param settings cấu hình FSRS-6 của bộ thẻ
     * @return kết quả lập lịch gồm trạng thái mới và bản ghi nhật ký
     */
    public SchedulingResult schedule(ReviewState currentState, ReviewRating rating, DeckSettings settings) {
        return schedule(currentState, rating, settings, clock.instant(), null, null);
    }

    /**
     * Tính kết quả lập lịch với mốc thời gian ôn tập tường minh.
     *
     * @param currentState trạng thái hiện tại của thẻ
     * @param rating mức đánh giá của người học
     * @param settings cấu hình FSRS-6 của bộ thẻ
     * @param reviewedAt thời điểm ôn tập tường minh
     * @return kết quả lập lịch gồm trạng thái mới và bản ghi nhật ký
     */
    public SchedulingResult schedule(
            ReviewState currentState,
            ReviewRating rating,
            DeckSettings settings,
            Instant reviewedAt
    ) {
        return schedule(currentState, rating, settings, reviewedAt, null, null);
    }

    /**
     * Tính kết quả lập lịch đầy đủ với mốc thời gian tường minh, mã phiên học và thời gian phản hồi.
     *
     * @param currentState trạng thái hiện tại của thẻ (không bị thay đổi)
     * @param rating mức đánh giá từ 1 đến 4
     * @param settings cấu hình FSRS-6 của bộ thẻ
     * @param reviewedAt thời điểm thực hiện lượt ôn tập
     * @param sessionId mã phiên học liên quan (có thể null)
     * @param responseTimeMs thời gian phản hồi tính bằng mili giây (có thể null)
     * @return kết quả lập lịch gồm trạng thái mới, bản ghi nhật ký và khoảng thời gian chờ
     */
    public SchedulingResult schedule(
            ReviewState currentState,
            ReviewRating rating,
            DeckSettings settings,
            Instant reviewedAt,
            Long sessionId,
            Integer responseTimeMs
    ) {
        Objects.requireNonNull(currentState, "currentState must not be null");
        Objects.requireNonNull(rating, "rating must not be null");
        Objects.requireNonNull(settings, "settings must not be null");
        Objects.requireNonNull(reviewedAt, "reviewedAt must not be null");
        if (responseTimeMs != null && responseTimeMs < 0) {
            throw new IllegalArgumentException("responseTimeMs must not be negative");
        }

        settingsValidator.validate(settings);

        List<Duration> learningSteps = LearningStepsParser.parse(settings.learningSteps());
        List<Duration> relearningSteps = LearningStepsParser.parse(settings.relearningSteps());

        double elapsedDays = currentState.state() == CardState.NEW
                ? 0.0
                : math.elapsedDays(currentState.lastReviewedAt(), reviewedAt);

        FsrsMemoryState nextMemory = computeNextMemoryState(currentState, elapsedDays, rating);
        TransitionOutcome outcome = computeTransition(
                currentState,
                rating,
                nextMemory.stability(),
                settings,
                learningSteps,
                relearningSteps,
                reviewedAt
        );

        Instant dueAt = reviewedAt.plus(outcome.interval());
        double scheduledDays = outcome.interval().toSeconds() / SECONDS_PER_DAY;
        int nextRepetitions = currentState.repetitions() + 1;
        int nextLapses = currentState.lapses()
                + (currentState.state() == CardState.REVIEW && rating == ReviewRating.AGAIN ? 1 : 0);

        ReviewState nextState = new ReviewState(
                currentState.cardId(),
                outcome.nextCardState(),
                nextMemory.stability(),
                nextMemory.difficulty(),
                outcome.nextStep(),
                dueAt,
                reviewedAt,
                scheduledDays,
                elapsedDays,
                nextRepetitions,
                nextLapses,
                reviewedAt
        );

        ReviewLog reviewLog = new ReviewLog(
                null,
                currentState.cardId(),
                sessionId,
                rating,
                reviewedAt,
                responseTimeMs,
                currentState.state(),
                outcome.nextCardState(),
                currentState.stability(),
                nextMemory.stability(),
                currentState.difficulty(),
                nextMemory.difficulty(),
                elapsedDays,
                scheduledDays,
                math.parameters().algorithmVersion(),
                math.parameters().parameterVersion()
        );

        return new SchedulingResult(nextState, reviewLog, outcome.interval());
    }

    /**
     * Tính xem trước lịch ôn tập cho cả 4 nút đánh giá sử dụng đồng hồ đã tiêm vào mà không thay đổi trạng thái gốc.
     *
     * @param currentState trạng thái hiện tại của thẻ
     * @param settings cấu hình FSRS-6 của bộ thẻ
     * @return bản đồ bất biến ánh xạ từng mức đánh giá sang kết quả lập lịch xem trước
     */
    public Map<ReviewRating, SchedulingResult> previewAllRatings(
            ReviewState currentState,
            DeckSettings settings
    ) {
        return previewAllRatings(currentState, settings, clock.instant());
    }

    /**
     * Tính xem trước lịch ôn tập cho cả 4 nút đánh giá tại mốc thời gian tường minh mà không thay đổi trạng thái gốc.
     *
     * @param currentState trạng thái hiện tại của thẻ
     * @param settings cấu hình FSRS-6 của bộ thẻ
     * @param reviewedAt thời điểm ôn tập tường minh
     * @return bản đồ bất biến ánh xạ cả 4 mức đánh giá sang kết quả lập lịch xem trước
     */
    public Map<ReviewRating, SchedulingResult> previewAllRatings(
            ReviewState currentState,
            DeckSettings settings,
            Instant reviewedAt
    ) {
        Map<ReviewRating, SchedulingResult> previews = new EnumMap<>(ReviewRating.class);
        for (ReviewRating rating : ReviewRating.values()) {
            previews.put(rating, schedule(currentState, rating, settings, reviewedAt, null, null));
        }
        return Collections.unmodifiableMap(previews);
    }

    private FsrsMemoryState computeNextMemoryState(
            ReviewState currentState,
            double elapsedDays,
            ReviewRating rating
    ) {
        if (currentState.state() == CardState.NEW || currentState.stability() <= 0.0) {
            return math.initialMemoryState(rating);
        }
        FsrsMemoryState currentMemory = new FsrsMemoryState(currentState.stability(), currentState.difficulty());
        return math.nextMemoryState(currentMemory, elapsedDays, rating);
    }

    private TransitionOutcome computeTransition(
            ReviewState currentState,
            ReviewRating rating,
            double nextStability,
            DeckSettings settings,
            List<Duration> learningSteps,
            List<Duration> relearningSteps,
            Instant reviewedAt
    ) {
        return switch (currentState.state()) {
            case NEW -> transitionFromNew(currentState, rating, nextStability, settings, learningSteps, reviewedAt);
            case LEARNING -> transitionFromStepState(
                    CardState.LEARNING,
                    currentState,
                    rating,
                    nextStability,
                    settings,
                    learningSteps,
                    reviewedAt
            );
            case REVIEW -> transitionFromReview(currentState, rating, nextStability, settings, relearningSteps, reviewedAt);
            case RELEARNING -> transitionFromStepState(
                    CardState.RELEARNING,
                    currentState,
                    rating,
                    nextStability,
                    settings,
                    relearningSteps,
                    reviewedAt
            );
        };
    }

    private TransitionOutcome transitionFromNew(
            ReviewState currentState,
            ReviewRating rating,
            double nextStability,
            DeckSettings settings,
            List<Duration> learningSteps,
            Instant reviewedAt
    ) {
        if (learningSteps.isEmpty()) {
            return graduateToReview(currentState, rating, nextStability, settings, reviewedAt);
        }

        return switch (rating) {
            case AGAIN -> new TransitionOutcome(CardState.LEARNING, 0, learningSteps.getFirst());
            case HARD -> new TransitionOutcome(
                    CardState.LEARNING,
                    0,
                    computeHardStepDuration(learningSteps, 0)
            );
            case GOOD -> {
                if (learningSteps.size() == 1) {
                    yield graduateToReview(currentState, rating, nextStability, settings, reviewedAt);
                }
                yield new TransitionOutcome(CardState.LEARNING, 1, learningSteps.get(1));
            }
            case EASY -> graduateToReview(currentState, rating, nextStability, settings, reviewedAt);
        };
    }

    private TransitionOutcome transitionFromStepState(
            CardState stepState,
            ReviewState currentState,
            ReviewRating rating,
            double nextStability,
            DeckSettings settings,
            List<Duration> steps,
            Instant reviewedAt
    ) {
        if (steps.isEmpty()) {
            return graduateToReview(currentState, rating, nextStability, settings, reviewedAt);
        }

        int rawStep = currentState.step() == null ? 0 : currentState.step();
        int currentStep = Math.max(0, Math.min(rawStep, steps.size() - 1));

        return switch (rating) {
            case AGAIN -> new TransitionOutcome(stepState, 0, steps.getFirst());
            case HARD -> new TransitionOutcome(
                    stepState,
                    currentStep,
                    computeHardStepDuration(steps, currentStep)
            );
            case GOOD -> {
                int nextStep = currentStep + 1;
                if (nextStep >= steps.size()) {
                    yield graduateToReview(currentState, rating, nextStability, settings, reviewedAt);
                }
                yield new TransitionOutcome(stepState, nextStep, steps.get(nextStep));
            }
            case EASY -> graduateToReview(currentState, rating, nextStability, settings, reviewedAt);
        };
    }

    private TransitionOutcome transitionFromReview(
            ReviewState currentState,
            ReviewRating rating,
            double nextStability,
            DeckSettings settings,
            List<Duration> relearningSteps,
            Instant reviewedAt
    ) {
        if (rating == ReviewRating.AGAIN) {
            if (relearningSteps.isEmpty()) {
                return graduateToReview(currentState, rating, nextStability, settings, reviewedAt);
            }
            return new TransitionOutcome(CardState.RELEARNING, 0, relearningSteps.getFirst());
        }
        return graduateToReview(currentState, rating, nextStability, settings, reviewedAt);
    }

    private TransitionOutcome graduateToReview(
            ReviewState currentState,
            ReviewRating rating,
            double nextStability,
            DeckSettings settings,
            Instant reviewedAt
    ) {
        int intervalDays = math.nextIntervalDays(
                nextStability,
                settings.desiredRetention(),
                settings.maximumInterval()
        );
        if (settings.enableFuzzing()) {
            intervalDays = fuzzer.fuzzInterval(
                    intervalDays,
                    settings.maximumInterval(),
                    currentState.cardId(),
                    reviewedAt,
                    currentState.repetitions(),
                    rating
            );
        }
        return new TransitionOutcome(CardState.REVIEW, null, Duration.ofDays(intervalDays));
    }

    /**
     * Tính thời lượng bước Hard cho thẻ đang trong giai đoạn học ngắn hạn hoặc học lại.
     */
    private Duration computeHardStepDuration(List<Duration> steps, int currentStep) {
        if (currentStep == 0 && steps.size() == 1) {
            long seconds = Math.max(1L, Math.round(steps.getFirst().toSeconds() * 1.5));
            return Duration.ofSeconds(seconds);
        }
        if (currentStep == 0 && steps.size() >= 2) {
            long averageSeconds = Math.max(
                    1L,
                    Math.round((steps.get(0).toSeconds() + steps.get(1).toSeconds()) / 2.0)
            );
            return Duration.ofSeconds(averageSeconds);
        }
        return steps.get(currentStep);
    }

    private record TransitionOutcome(
            CardState nextCardState,
            Integer nextStep,
            Duration interval
    ) {
    }
}
