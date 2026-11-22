package com.senkoje.domain.fsrs;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.senkoje.domain.model.CardState;
import com.senkoje.domain.model.DeckSettings;
import com.senkoje.domain.model.ReviewRating;
import com.senkoje.domain.model.ReviewState;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Kiểm thử nhanh xác minh máy trạng thái, xem trước 4 mức đánh giá và độ nhiễu tất định của FsrsScheduler.
 */
class FsrsSchedulerSmokeTest {

    @Test
    void shouldTransitionAcrossAllStatesAndPreviewWithoutMutatingInput() {
        FsrsScheduler scheduler = new FsrsScheduler();
        Instant t0 = Instant.parse("2026-05-01T08:00:00Z");
        DeckSettings settings = new DeckSettings(1L, 0.90, 36500, 20, 200, "1m,10m", "10m", false);
        ReviewState initial = ReviewState.initialNew(10L, t0);

        Map<ReviewRating, SchedulingResult> previews = scheduler.previewAllRatings(initial, settings, t0);
        assertEquals(4, previews.size());
        assertEquals(CardState.NEW, initial.state());
        assertEquals(0, initial.repetitions());

        // Kiểm tra các bước chuyển trạng thái NEW -> LEARNING -> REVIEW -> RELEARNING -> REVIEW
        SchedulingResult newGood = previews.get(ReviewRating.GOOD);
        assertEquals(CardState.LEARNING, newGood.nextState().state());
        assertEquals(1, newGood.nextState().step());
        assertEquals(Duration.ofMinutes(10), newGood.scheduledInterval());

        Instant t1 = t0.plus(Duration.ofMinutes(10));
        SchedulingResult graduated = scheduler.schedule(newGood.nextState(), ReviewRating.GOOD, settings, t1);
        assertEquals(CardState.REVIEW, graduated.nextState().state());
        assertNull(graduated.nextState().step());
        assertTrue(graduated.scheduledInterval().toDays() >= 1);

        Instant t2 = graduated.nextState().dueAt();
        SchedulingResult lapsed = scheduler.schedule(graduated.nextState(), ReviewRating.AGAIN, settings, t2);
        assertEquals(CardState.RELEARNING, lapsed.nextState().state());
        assertEquals(0, lapsed.nextState().step());
        assertEquals(1, lapsed.nextState().lapses());
        assertEquals(Duration.ofMinutes(10), lapsed.scheduledInterval());

        Instant t3 = t2.plus(Duration.ofMinutes(10));
        SchedulingResult recovered = scheduler.schedule(lapsed.nextState(), ReviewRating.GOOD, settings, t3);
        assertEquals(CardState.REVIEW, recovered.nextState().state());
        assertNull(recovered.nextState().step());
        assertEquals("FSRS-6", recovered.reviewLog().algorithmVersion());
        assertEquals("fsrs-6-default-v1", recovered.reviewLog().parameterVersion());
    }
}
