package com.senkoje.domain.service;

import com.senkoje.domain.model.Deck;
import java.util.ArrayList;
import java.util.List;

/**
 * Bộ kiểm tra tính hợp lệ cho thực thể miền Deck trước khi lưu xuống cơ sở dữ liệu.
 */
public class DeckValidator {

    public static final int MAX_TITLE_LENGTH = 200;
    public static final int MAX_LEVEL_LENGTH = 50;
    public static final int MAX_DESCRIPTION_LENGTH = 2000;

    /**
     * Kiểm tra toàn bộ trường bắt buộc và giới hạn độ dài của một bộ thẻ.
     *
     * @param deck bộ thẻ cần kiểm tra
     * @throws DomainValidationException nếu có bất kỳ quy tắc miền nào bị vi phạm
     */
    public void validate(Deck deck) {
        List<String> errors = new ArrayList<>();
        if (deck == null) {
            throw new DomainValidationException(List.of("Deck must not be null"));
        }

        if (deck.title() == null || deck.title().isBlank()) {
            errors.add("Deck title must not be blank");
        } else if (deck.title().trim().length() > MAX_TITLE_LENGTH) {
            errors.add("Deck title must not exceed " + MAX_TITLE_LENGTH + " characters");
        }

        if (deck.targetLanguage() == null) {
            errors.add("Deck target language must not be null");
        }

        if (deck.definitionLanguage() == null) {
            errors.add("Deck definition language must not be null");
        }

        if (deck.level() == null || deck.level().code().isBlank()) {
            errors.add("Deck proficiency level must not be blank");
        } else if (deck.level().code().length() > MAX_LEVEL_LENGTH) {
            errors.add("Deck proficiency level must not exceed " + MAX_LEVEL_LENGTH + " characters");
        }

        if (deck.description() != null && deck.description().length() > MAX_DESCRIPTION_LENGTH) {
            errors.add("Deck description must not exceed " + MAX_DESCRIPTION_LENGTH + " characters");
        }

        if (!errors.isEmpty()) {
            throw new DomainValidationException(errors);
        }
    }
}
