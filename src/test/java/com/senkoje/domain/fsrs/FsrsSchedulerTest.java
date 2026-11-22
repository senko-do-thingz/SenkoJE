package com.senkoje.domain.fsrs;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.senkoje.domain.model.CardState;
import com.senkoje.domain.model.DeckSettings;
import com.senkoje.domain.model.ReviewRating;
import com.senkoje.domain.model.ReviewState;
import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Kiểm thử tự động đối chiếu bộ vector tham chiếu FSRS-6 và toàn bộ kịch bản lập lịch của FsrsScheduler.
 */
class FsrsSchedulerTest {

    private static final double TOLERANCE = 1e-4;
    private static final Instant BASE_TIME = Instant.parse("2026-05-01T08:00:00Z");

    @Test
    void shouldMatchVerifiedFsrs6ReferenceTestVectors() throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        JsonNode root;
        try (InputStream stream = getClass().getResourceAsStream("/fsrs-test-vectors/fsrs6_reference_vectors.json")) {
            assertNotNull(stream, "Reference test vector resource must exist");
            root = mapper.readTree(stream);
        }

        double[] weights = new double[FsrsParameters.PARAMETER_COUNT];
        JsonNode weightsNode = root.get("weights");
        assertEquals(FsrsParameters.PARAMETER_COUNT, weightsNode.size());
        for (int i = 0; i < weights.length; i++) {
            weights[i] = weightsNode.get(i).asDouble();
        }

        FsrsParameters parameters = new FsrsParameters(
                root.get("algorithmVersion").asText(),
                root.get("parameterVersion").asText(),
                weights
        );
        FsrsScheduler scheduler = new FsrsScheduler(parameters);

        for (JsonNode vector : root.get("vectors")) {
            String name = vector.get("name").asText();
            CardState previousState = CardState.fromCode(vector.get("previousState").asText());
            double stability = vector.get("stability").asDouble();
            double difficulty = vector.get("difficulty").asDouble();
            double elapsedDays = vector.get("elapsedDays").asDouble();
            ReviewRating rating = ReviewRating.valueOf(vector.get("rating").asText());
            double desiredRetention = vector.get("desiredRetention").asDouble();
            int maximumInterval = vector.get("maximumInterval").asInt();

            // Cấu hình bước học rỗng để đối chiếu trực tiếp khoảng cách tốt nghiệp theo công thức FSRS-6
            DeckSettings settings = new DeckSettings(
                    1L, desiredRetention, maximumInterval, 20, 200, "", "", false
            );

            Instant lastReviewedAt = previousState == CardState.NEW ? null : BASE_TIME;
            long elapsedMillis = Math.round(elapsedDays * 86_400_000.0);
            Instant reviewedAt = lastReviewedAt == null ? BASE_TIME : lastReviewedAt.plusMillis(elapsedMillis);

            ReviewState currentState = new ReviewState(
                    100L,
                    previousState,
                    stability,
                    difficulty,
                    previousState == CardState.LEARNING ? 0 : null,
                    lastReviewedAt == null ? null : reviewedAt,
                    lastReviewedAt,
                    stability,
                    0.0,
                    previousState == CardState.NEW ? 0 : 2,
                    0,
                    BASE_TIME
            );

            double actualRetrievability = previousState == CardState.NEW
                    ? 0.0
                    : scheduler.math().retrievability(elapsedDays, stability);
            assertEquals(
                    vector.get("expectedRetrievability").asDouble(),
                    actualRetrievability,
                    TOLERANCE,
                    "Retrievability mismatch for vector: " + name
            );

            SchedulingResult result = scheduler.schedule(currentState, rating, settings, reviewedAt);
            assertEquals(
                    vector.get("expectedStability").asDouble(),
                    result.nextState().stability(),
                    TOLERANCE,
                    "Stability mismatch for vector: " + name
            );
            assertEquals(
                    vector.get("expectedDifficulty").asDouble(),
                    result.nextState().difficulty(),
                    TOLERANCE,
                    "Difficulty mismatch for vector: " + name
            );
            assertEquals(
                    vector.get("expectedIntervalDays").asLong(),
                    result.scheduledInterval().toDays(),
                    "Interval days mismatch for vector: " + name
            );
        }
    }

    @Test
    void shouldScheduleNewCardAcrossAllFourRatingsWithDefaultLearningSteps() {
        FsrsScheduler scheduler = new FsrsScheduler();
        DeckSettings settings = new DeckSettings(1L, 0.90, 36500, 20, 200, "1m,10m", "10m", false);
        ReviewState newCard = ReviewState.initialNew(1L, BASE_TIME);

        Map<ReviewRating, SchedulingResult> previews = scheduler.previewAllRatings(newCard, settings, BASE_TIME);

        SchedulingResult again = previews.get(ReviewRating.AGAIN);
        assertEquals(CardState.LEARNING, again.nextState().state());
        assertEquals(0, again.nextState().step());
        assertEquals(Duration.ofMinutes(1), again.scheduledInterval());
        assertEquals(0, again.nextState().lapses());

        SchedulingResult hard = previews.get(ReviewRating.HARD);
        assertEquals(CardState.LEARNING, hard.nextState().state());
        assertEquals(0, hard.nextState().step());
        // Trung bình giữa bước 1m (60s) và bước 10m (600s) là 330s (5m30s)
        assertEquals(Duration.ofSeconds(330), hard.scheduledInterval());

        SchedulingResult good = previews.get(ReviewRating.GOOD);
        assertEquals(CardState.LEARNING, good.nextState().state());
        assertEquals(1, good.nextState().step());
        assertEquals(Duration.ofMinutes(10), good.scheduledInterval());

        SchedulingResult easy = previews.get(ReviewRating.EASY);
        assertEquals(CardState.REVIEW, easy.nextState().state());
        assertNull(easy.nextState().step());
        assertEquals(Duration.ofDays(8), easy.scheduledInterval());

        // Đảm bảo hàm preview không thay đổi trạng thái đầu vào
        assertEquals(CardState.NEW, newCard.state());
        assertEquals(0, newCard.repetitions());
    }

    @Test
    void shouldHandleLearningAndRelearningStepTransitionsCorrectly() {
        FsrsScheduler scheduler = new FsrsScheduler();
        DeckSettings settings = new DeckSettings(1L, 0.90, 36500, 20, 200, "1m,10m", "5m,15m", false);
        ReviewState card = ReviewState.initialNew(2L, BASE_TIME);

        // Bước 1: NEW + AGAIN -> LEARNING bước 0 (1m)
        SchedulingResult step0 = scheduler.schedule(card, ReviewRating.AGAIN, settings, BASE_TIME);
        assertEquals(CardState.LEARNING, step0.nextState().state());
        assertEquals(0, step0.nextState().step());

        // Bước 2: LEARNING bước 0 + GOOD -> LEARNING bước 1 (10m)
        Instant t1 = BASE_TIME.plus(Duration.ofMinutes(1));
        SchedulingResult step1 = scheduler.schedule(step0.nextState(), ReviewRating.GOOD, settings, t1);
        assertEquals(CardState.LEARNING, step1.nextState().state());
        assertEquals(1, step1.nextState().step());
        assertEquals(Duration.ofMinutes(10), step1.scheduledInterval());

        // Bước 3: LEARNING bước 1 + HARD -> giữ nguyên LEARNING bước 1 (10m)
        Instant t2 = t1.plus(Duration.ofMinutes(10));
        SchedulingResult step1Hard = scheduler.schedule(step1.nextState(), ReviewRating.HARD, settings, t2);
        assertEquals(CardState.LEARNING, step1Hard.nextState().state());
        assertEquals(1, step1Hard.nextState().step());
        assertEquals(Duration.ofMinutes(10), step1Hard.scheduledInterval());

        // Bước 4: LEARNING bước 1 + GOOD -> tốt nghiệp sang REVIEW
        Instant t3 = t2.plus(Duration.ofMinutes(10));
        SchedulingResult graduated = scheduler.schedule(step1Hard.nextState(), ReviewRating.GOOD, settings, t3);
        assertEquals(CardState.REVIEW, graduated.nextState().state());
        assertNull(graduated.nextState().step());

        // Bước 5: REVIEW + AGAIN -> chuyển sang RELEARNING bước 0 (5m) và tăng lapses lên 1
        Instant t4 = graduated.nextState().dueAt();
        SchedulingResult lapsed = scheduler.schedule(graduated.nextState(), ReviewRating.AGAIN, settings, t4);
        assertEquals(CardState.RELEARNING, lapsed.nextState().state());
        assertEquals(0, lapsed.nextState().step());
        assertEquals(1, lapsed.nextState().lapses());
        assertEquals(Duration.ofMinutes(5), lapsed.scheduledInterval());

        // Bước 6: RELEARNING bước 0 + GOOD -> RELEARNING bước 1 (15m)
        Instant t5 = t4.plus(Duration.ofMinutes(5));
        SchedulingResult relearnStep1 = scheduler.schedule(lapsed.nextState(), ReviewRating.GOOD, settings, t5);
        assertEquals(CardState.RELEARNING, relearnStep1.nextState().state());
        assertEquals(1, relearnStep1.nextState().step());
        assertEquals(Duration.ofMinutes(15), relearnStep1.scheduledInterval());

        // Bước 7: RELEARNING bước 1 + GOOD -> quay lại REVIEW và giữ nguyên lapses = 1
        Instant t6 = t5.plus(Duration.ofMinutes(15));
        SchedulingResult recovered = scheduler.schedule(relearnStep1.nextState(), ReviewRating.GOOD, settings, t6);
        assertEquals(CardState.REVIEW, recovered.nextState().state());
        assertNull(recovered.nextState().step());
        assertEquals(1, recovered.nextState().lapses());
    }

    @Test
    void shouldRewardOverdueRecallWithLargerStabilityAndInterval() {
        FsrsScheduler scheduler = new FsrsScheduler();
        DeckSettings settings = new DeckSettings(1L, 0.90, 36500, 20, 200, "1m,10m", "10m", false);
        ReviewState reviewCard = new ReviewState(
                3L, CardState.REVIEW, 10.0, 5.0, null,
                BASE_TIME.plus(Duration.ofDays(10)), BASE_TIME, 10.0, 4.0, 3, 0, BASE_TIME
        );

        SchedulingResult onTime = scheduler.schedule(
                reviewCard, ReviewRating.GOOD, settings, BASE_TIME.plus(Duration.ofDays(10))
        );
        SchedulingResult overdue = scheduler.schedule(
                reviewCard, ReviewRating.GOOD, settings, BASE_TIME.plus(Duration.ofDays(25))
        );

        assertTrue(overdue.nextState().stability() > onTime.nextState().stability());
        assertTrue(overdue.scheduledInterval().toDays() > onTime.scheduledInterval().toDays());
        assertEquals(25.0, overdue.nextState().elapsedDays(), TOLERANCE);
    }

    @Test
    void shouldAdjustIntervalsMonotonicallyWithDesiredRetentionAndClampMaximumInterval() {
        FsrsScheduler scheduler = new FsrsScheduler();
        ReviewState reviewCard = new ReviewState(
                4L, CardState.REVIEW, 20.0, 4.5, null,
                BASE_TIME.plus(Duration.ofDays(20)), BASE_TIME, 20.0, 10.0, 5, 0, BASE_TIME
        );
        Instant reviewTime = BASE_TIME.plus(Duration.ofDays(20));

        DeckSettings retention80 = new DeckSettings(1L, 0.80, 36500, 20, 200, "1m,10m", "10m", false);
        DeckSettings retention90 = new DeckSettings(1L, 0.90, 36500, 20, 200, "1m,10m", "10m", false);
        DeckSettings retention95 = new DeckSettings(1L, 0.95, 36500, 20, 200, "1m,10m", "10m", false);
        DeckSettings clampedSettings = new DeckSettings(1L, 0.80, 30, 20, 200, "1m,10m", "10m", false);

        long ivl80 = scheduler.schedule(reviewCard, ReviewRating.GOOD, retention80, reviewTime)
                .scheduledInterval().toDays();
        long ivl90 = scheduler.schedule(reviewCard, ReviewRating.GOOD, retention90, reviewTime)
                .scheduledInterval().toDays();
        long ivl95 = scheduler.schedule(reviewCard, ReviewRating.GOOD, retention95, reviewTime)
                .scheduledInterval().toDays();
        long clamped = scheduler.schedule(reviewCard, ReviewRating.GOOD, clampedSettings, reviewTime)
                .scheduledInterval().toDays();

        // Xác suất ghi nhớ mục tiêu càng cao thì khoảng cách ôn tập càng ngắn
        assertTrue(ivl80 > ivl90);
        assertTrue(ivl90 > ivl95);
        assertEquals(30L, clamped);
    }

    @Test
    void shouldProduceDeterministicBoundedFuzzedIntervals() {
        FsrsScheduler scheduler = new FsrsScheduler();
        FsrsFuzzer fuzzer = new FsrsFuzzer();
        ReviewState reviewCard = new ReviewState(
                55L, CardState.REVIEW, 15.0, 4.5, null,
                BASE_TIME.plus(Duration.ofDays(15)), BASE_TIME, 15.0, 7.0, 4, 0, BASE_TIME
        );
        Instant reviewTime = BASE_TIME.plus(Duration.ofDays(15));

        DeckSettings unfuzzedSettings = new DeckSettings(1L, 0.90, 60, 20, 200, "1m,10m", "10m", false);
        DeckSettings fuzzedSettings = new DeckSettings(1L, 0.90, 60, 20, 200, "1m,10m", "10m", true);

        int exactDays = (int) scheduler.schedule(reviewCard, ReviewRating.GOOD, unfuzzedSettings, reviewTime)
                .scheduledInterval().toDays();
        int[] bounds = fuzzer.computeBounds(exactDays, 60);

        SchedulingResult firstFuzzed = scheduler.schedule(reviewCard, ReviewRating.GOOD, fuzzedSettings, reviewTime);
        SchedulingResult secondFuzzed = scheduler.schedule(reviewCard, ReviewRating.GOOD, fuzzedSettings, reviewTime);

        long fuzzedDays = firstFuzzed.scheduledInterval().toDays();
        assertEquals(fuzzedDays, secondFuzzed.scheduledInterval().toDays());
        assertTrue(fuzzedDays >= bounds[0] && fuzzedDays <= bounds[1]);
        assertTrue(fuzzedDays <= 60);
    }
}
