package com.senkoje.domain.fsrs;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Trình phân tích chuỗi cấu hình bước học ngắn hạn và học lại (ví dụ "1m,10m") sang danh sách Duration.
 */
public final class LearningStepsParser {

    // Định dạng bước học khớp với DeckSettingsValidator gồm số nguyên dương và đơn vị s, m, h hoặc d
    private static final Pattern STEP_TOKEN_PATTERN = Pattern.compile("^(\\d+)([smhd])$");

    private LearningStepsParser() {
    }

    /**
     * Chuyển đổi chuỗi cấu hình bước học thành danh sách thời lượng bất biến.
     *
     * @param rawSteps chuỗi cấu hình bước học ngăn cách bằng dấu phẩy (có thể rỗng)
     * @return danh sách thời lượng bước học tương ứng
     */
    public static List<Duration> parse(String rawSteps) {
        if (rawSteps == null) {
            throw new IllegalArgumentException("Learning steps string must not be null");
        }
        String trimmed = rawSteps.trim();
        if (trimmed.isEmpty()) {
            return List.of();
        }
        String[] tokens = trimmed.split(",", -1);
        List<Duration> steps = new ArrayList<>(tokens.length);
        for (String token : tokens) {
            String candidate = token.trim();
            Matcher matcher = STEP_TOKEN_PATTERN.matcher(candidate);
            if (!matcher.matches()) {
                throw new IllegalArgumentException("Invalid learning step token: '" + candidate + "'");
            }
            long amount = Long.parseLong(matcher.group(1));
            if (amount <= 0) {
                throw new IllegalArgumentException("Learning step duration must be positive: '" + candidate + "'");
            }
            String unit = matcher.group(2);
            steps.add(toDuration(amount, unit));
        }
        return List.copyOf(steps);
    }

    private static Duration toDuration(long amount, String unit) {
        return switch (unit) {
            case "s" -> Duration.ofSeconds(amount);
            case "m" -> Duration.ofMinutes(amount);
            case "h" -> Duration.ofHours(amount);
            case "d" -> Duration.ofDays(amount);
            default -> throw new IllegalArgumentException("Unsupported duration unit: " + unit);
        };
    }
}
