package com.senkoje.presentation.util;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.senkoje.presentation.i18n.LocalizationManager;
import java.time.Duration;
import org.junit.jupiter.api.Test;

/**
 * Kiểm thử đơn vị cho StudyIntervalFormatter với cả tiếng Anh và tiếng Việt.
 */
class StudyIntervalFormatterTest {

    @Test
    void shouldFormatIntervalsInEnglishAndVietnamese() {
        LocalizationManager en = new LocalizationManager("en");
        assertEquals("< 1m", StudyIntervalFormatter.format(Duration.ofSeconds(30), en));
        assertEquals("10m", StudyIntervalFormatter.format(Duration.ofMinutes(10), en));
        assertEquals("3h", StudyIntervalFormatter.format(Duration.ofHours(3), en));
        assertEquals("4d", StudyIntervalFormatter.format(Duration.ofDays(4), en));
        assertEquals("2mo", StudyIntervalFormatter.format(Duration.ofDays(60), en));
        assertEquals("1y", StudyIntervalFormatter.format(Duration.ofDays(365), en));

        LocalizationManager vi = new LocalizationManager("vi");
        assertEquals("< 1 phút", StudyIntervalFormatter.format(Duration.ofSeconds(15), vi));
        assertEquals("10 phút", StudyIntervalFormatter.format(Duration.ofMinutes(10), vi));
        assertEquals("4 ngày", StudyIntervalFormatter.format(Duration.ofDays(4), vi));

        assertEquals("00:00", StudyIntervalFormatter.formatTimer(0));
        assertEquals("02:05", StudyIntervalFormatter.formatTimer(125));
    }
}
