package com.senkoje.domain.service;

import com.senkoje.domain.model.DeckSettings;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Bộ kiểm tra tính hợp lệ cho cấu hình FSRS-6 và giới hạn học hằng ngày của từng bộ thẻ.
 */
public class DeckSettingsValidator {

    public static final double MIN_DESIRED_RETENTION = 0.70;
    public static final double MAX_DESIRED_RETENTION = 0.99;
    public static final int MIN_MAXIMUM_INTERVAL = 1;
    public static final int MAX_MAXIMUM_INTERVAL = 36500;
    public static final int MAX_NEW_CARDS_PER_DAY = 9999;
    public static final int MAX_REVIEWS_PER_DAY = 99999;

    // Định dạng bước học hợp lệ gồm số nguyên dương đi kèm đơn vị thời gian s, m, h hoặc d
    private static final Pattern STEP_TOKEN_PATTERN = Pattern.compile("^(\\d+)([smhd])$");

    /**
     * Kiểm tra toàn bộ thông số cấu hình bộ thẻ theo giới hạn an toàn của thuật toán FSRS-6.
     *
     * @param settings cấu hình bộ thẻ cần kiểm tra
     * @throws DomainValidationException nếu cấu hình chứa giá trị không hợp lệ
     */
    public void validate(DeckSettings settings) {
        List<String> errors = new ArrayList<>();
        if (settings == null) {
            throw new DomainValidationException(List.of("DeckSettings must not be null"));
        }

        double retention = settings.desiredRetention();
        if (!Double.isFinite(retention)
                || retention < MIN_DESIRED_RETENTION
                || retention > MAX_DESIRED_RETENTION) {
            errors.add("Desired retention must be between "
                    + MIN_DESIRED_RETENTION + " and " + MAX_DESIRED_RETENTION);
        }

        if (settings.maximumInterval() < MIN_MAXIMUM_INTERVAL
                || settings.maximumInterval() > MAX_MAXIMUM_INTERVAL) {
            errors.add("Maximum interval must be between "
                    + MIN_MAXIMUM_INTERVAL + " and " + MAX_MAXIMUM_INTERVAL + " days");
        }

        if (settings.newCardsPerDay() < 0 || settings.newCardsPerDay() > MAX_NEW_CARDS_PER_DAY) {
            errors.add("New cards per day must be between 0 and " + MAX_NEW_CARDS_PER_DAY);
        }

        if (settings.maximumReviewsPerDay() < 0 || settings.maximumReviewsPerDay() > MAX_REVIEWS_PER_DAY) {
            errors.add("Maximum reviews per day must be between 0 and " + MAX_REVIEWS_PER_DAY);
        }

        validateSteps("Learning steps", settings.learningSteps(), errors);
        validateSteps("Relearning steps", settings.relearningSteps(), errors);

        if (!errors.isEmpty()) {
            throw new DomainValidationException(errors);
        }
    }

    /**
     * Kiểm tra chuỗi bước học ngắn hạn hoặc học lại theo dạng danh sách ngăn cách bằng dấu phẩy.
     *
     * @param fieldName tên trường dùng trong thông báo lỗi
     * @param rawSteps chuỗi cấu hình bước học
     * @param errors danh sách lỗi tích lũy
     */
    private void validateSteps(String fieldName, String rawSteps, List<String> errors) {
        if (rawSteps == null) {
            errors.add(fieldName + " must not be null");
            return;
        }
        String trimmed = rawSteps.trim();
        if (trimmed.isEmpty()) {
            // Cho phép để trống nếu người học không dùng bước học ngắn hạn
            return;
        }
        String[] tokens = trimmed.split(",", -1);
        for (String token : tokens) {
            String candidate = token.trim();
            Matcher matcher = STEP_TOKEN_PATTERN.matcher(candidate);
            if (!matcher.matches()) {
                errors.add(fieldName + " contains invalid step token: '" + candidate + "'");
                return;
            }
            long value = Long.parseLong(matcher.group(1));
            if (value <= 0) {
                errors.add(fieldName + " step duration must be greater than zero: '" + candidate + "'");
                return;
            }
        }
    }
}
