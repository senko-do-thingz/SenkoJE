package com.senkoje.domain.fsrs;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.senkoje.domain.model.ReviewRating;
import java.time.Instant;
import org.junit.jupiter.api.Test;

/**
 * Kiểm thử đơn vị cho bộ tham số FsrsParameters và các công thức cốt lõi trong FsrsMath.
 */
class FsrsMathTest {

    private static final double EPSILON = 1e-6;

    @Test
    void shouldExposeDefaultFsrs6VersionAndTwentyOneWeights() {
        FsrsParameters defaults = FsrsParameters.defaults();

        assertEquals("FSRS-6", defaults.algorithmVersion());
        assertEquals("fsrs-6-default-v1", defaults.parameterVersion());
        assertEquals(21, defaults.weightsCopy().length);
        assertEquals(0.212, defaults.weight(0), EPSILON);
        assertEquals(0.1542, defaults.weight(20), EPSILON);
        assertEquals(-0.1542, defaults.decay(), EPSILON);

        // Kiểm tra tính bất biến khi người gọi sửa mảng bản sao
        double[] copy = defaults.weightsCopy();
        copy[0] = 999.0;
        assertEquals(0.212, defaults.weight(0), EPSILON);
    }

    @Test
    void shouldRejectInvalidParameterConfigurations() {
        double[] valid = FsrsParameters.defaults().weightsCopy();

        assertThrows(IllegalArgumentException.class,
                () -> new FsrsParameters("FSRS-5", "v1", valid));
        assertThrows(IllegalArgumentException.class,
                () -> FsrsParameters.of("v1", new double[19]));

        double[] nonPositiveDecay = FsrsParameters.defaults().weightsCopy();
        nonPositiveDecay[20] = 0.0;
        assertThrows(IllegalArgumentException.class,
                () -> FsrsParameters.of("v1", nonPositiveDecay));

        double[] nonPositiveInitStability = FsrsParameters.defaults().weightsCopy();
        nonPositiveInitStability[2] = -1.0;
        assertThrows(IllegalArgumentException.class,
                () -> FsrsParameters.of("v1", nonPositiveInitStability));
    }

    @Test
    void shouldAnchorRetrievabilityAtNinetyPercentWhenElapsedEqualsStability() {
        FsrsMath math = new FsrsMath(FsrsParameters.defaults());

        assertEquals(1.0, math.retrievability(0.0, 10.0), EPSILON);
        assertEquals(0.90, math.retrievability(10.0, 10.0), EPSILON);
        assertEquals(0.90, math.retrievability(2.3065, 2.3065), EPSILON);
        assertTrue(math.retrievability(20.0, 10.0) < 0.90);
        assertEquals(0.0, math.retrievability(5.0, 0.0), EPSILON);
    }

    @Test
    void shouldCalculateInitialStatesAndElapsedDaysDeterministically() {
        FsrsMath math = new FsrsMath(FsrsParameters.defaults());

        FsrsMemoryState againState = math.initialMemoryState(ReviewRating.AGAIN);
        FsrsMemoryState hardState = math.initialMemoryState(ReviewRating.HARD);
        FsrsMemoryState goodState = math.initialMemoryState(ReviewRating.GOOD);
        FsrsMemoryState easyState = math.initialMemoryState(ReviewRating.EASY);

        assertEquals(0.212, againState.stability(), EPSILON);
        assertEquals(1.2931, hardState.stability(), EPSILON);
        assertEquals(2.3065, goodState.stability(), EPSILON);
        assertEquals(8.2956, easyState.stability(), EPSILON);

        // Độ khó khởi đầu giảm dần từ Again đến Easy và luôn nằm trong [1.0, 10.0]
        assertTrue(againState.difficulty() > hardState.difficulty());
        assertTrue(hardState.difficulty() > goodState.difficulty());
        assertTrue(goodState.difficulty() > easyState.difficulty());
        assertTrue(easyState.difficulty() >= 1.0 && againState.difficulty() <= 10.0);

        Instant t0 = Instant.parse("2026-05-01T08:00:00Z");
        Instant t1 = Instant.parse("2026-05-02T20:00:00Z");
        assertEquals(1.5, math.elapsedDays(t0, t1), EPSILON);
        assertEquals(0.0, math.elapsedDays(null, t1), EPSILON);
        assertEquals(0.0, math.elapsedDays(t1, t0), EPSILON);
    }

    @Test
    void shouldUpdateShortTermRecallForgetAndIntervalCorrectly() {
        FsrsMath math = new FsrsMath(FsrsParameters.defaults());
        FsrsMemoryState current = new FsrsMemoryState(5.0, 5.0);

        // Ôn lại trong ngày dùng công thức ngắn hạn
        FsrsMemoryState sameDayGood = math.nextMemoryState(current, 0.25, ReviewRating.GOOD);
        assertTrue(sameDayGood.stability() >= current.stability());

        // Ôn dài hạn sau 5 ngày: Easy > Good > Hard > Again
        FsrsMemoryState reviewAgain = math.nextMemoryState(current, 5.0, ReviewRating.AGAIN);
        FsrsMemoryState reviewHard = math.nextMemoryState(current, 5.0, ReviewRating.HARD);
        FsrsMemoryState reviewGood = math.nextMemoryState(current, 5.0, ReviewRating.GOOD);
        FsrsMemoryState reviewEasy = math.nextMemoryState(current, 5.0, ReviewRating.EASY);

        assertTrue(reviewAgain.stability() < current.stability());
        assertTrue(reviewHard.stability() > current.stability());
        assertTrue(reviewGood.stability() > reviewHard.stability());
        assertTrue(reviewEasy.stability() > reviewGood.stability());

        // Độ khó tăng khi Again/Hard và giảm khi Easy
        assertTrue(reviewAgain.difficulty() > current.difficulty());
        assertTrue(reviewEasy.difficulty() < current.difficulty());

        // Kiểm tra khoảng cách ôn tập tại desiredRetention = 0.90 xấp xỉ đúng độ ổn định
        assertEquals(10, math.nextIntervalDays(10.0, 0.90, 36500));
        assertEquals(1, math.nextIntervalDays(0.2, 0.90, 36500));
        assertEquals(30, math.nextIntervalDays(120.0, 0.90, 30));
    }
}
