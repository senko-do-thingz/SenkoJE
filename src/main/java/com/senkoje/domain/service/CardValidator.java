package com.senkoje.domain.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.senkoje.domain.model.Card;
import com.senkoje.domain.model.CardExample;
import com.senkoje.domain.model.CardMeaning;
import java.util.ArrayList;
import java.util.List;

/**
 * Bộ kiểm tra tính hợp lệ cho thẻ học tập, nghĩa bổ sung, câu ví dụ và dữ liệu JSON mở rộng.
 */
public class CardValidator {

    public static final int MAX_TERM_LENGTH = 500;
    public static final int MAX_READING_LENGTH = 500;
    public static final int MAX_MEANING_LENGTH = 2000;
    public static final int MAX_PART_OF_SPEECH_LENGTH = 100;
    public static final int MAX_LEVEL_LENGTH = 50;
    public static final int MAX_NOTES_LENGTH = 5000;
    public static final int MAX_SENTENCE_LENGTH = 2000;

    // Bật kiểm tra token thừa ở cuối chuỗi để từ chối các chuỗi JSON bị hỏng một phần
    private static final ObjectMapper STRICT_JSON_MAPPER = new ObjectMapper()
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);

    /**
     * Kiểm tra toàn bộ trường bắt buộc, giới hạn độ dài và cú pháp JSON của trường extraData.
     *
     * @param card thẻ học tập cần kiểm tra
     * @throws DomainValidationException nếu thẻ chứa dữ liệu không hợp lệ
     */
    public void validate(Card card) {
        List<String> errors = new ArrayList<>();
        if (card == null) {
            throw new DomainValidationException(List.of("Card must not be null"));
        }

        if (card.deckId() == null || card.deckId() <= 0) {
            errors.add("Card deckId must be a positive identifier");
        }

        if (card.cardType() == null) {
            errors.add("Card type must not be null");
        }

        if (card.term() == null || card.term().isBlank()) {
            errors.add("Card term must not be blank");
        } else if (card.term().trim().length() > MAX_TERM_LENGTH) {
            errors.add("Card term must not exceed " + MAX_TERM_LENGTH + " characters");
        }

        if (card.reading() != null && card.reading().length() > MAX_READING_LENGTH) {
            errors.add("Card reading must not exceed " + MAX_READING_LENGTH + " characters");
        }

        if (card.meaning() == null || card.meaning().isBlank()) {
            errors.add("Card meaning must not be blank");
        } else if (card.meaning().trim().length() > MAX_MEANING_LENGTH) {
            errors.add("Card meaning must not exceed " + MAX_MEANING_LENGTH + " characters");
        }

        if (card.partOfSpeech() != null && card.partOfSpeech().length() > MAX_PART_OF_SPEECH_LENGTH) {
            errors.add("Card partOfSpeech must not exceed " + MAX_PART_OF_SPEECH_LENGTH + " characters");
        }

        if (card.level() != null && card.level().code().length() > MAX_LEVEL_LENGTH) {
            errors.add("Card level must not exceed " + MAX_LEVEL_LENGTH + " characters");
        }

        if (card.notes() != null && card.notes().length() > MAX_NOTES_LENGTH) {
            errors.add("Card notes must not exceed " + MAX_NOTES_LENGTH + " characters");
        }

        if (card.extraData() != null && !card.extraData().isBlank()) {
            if (!isValidJson(card.extraData())) {
                errors.add("Card extraData must contain valid JSON");
            }
        }

        if (!errors.isEmpty()) {
            throw new DomainValidationException(errors);
        }
    }

    /**
     * Kiểm tra tính hợp lệ của một bản ghi nghĩa bổ sung cho thẻ học tập.
     *
     * @param meaning bản ghi nghĩa bổ sung cần kiểm tra
     * @throws DomainValidationException nếu nghĩa bổ sung không hợp lệ
     */
    public void validateMeaning(CardMeaning meaning) {
        List<String> errors = new ArrayList<>();
        if (meaning == null) {
            throw new DomainValidationException(List.of("CardMeaning must not be null"));
        }
        if (meaning.languageCode() == null) {
            errors.add("CardMeaning languageCode must not be null");
        }
        if (meaning.meaning() == null || meaning.meaning().isBlank()) {
            errors.add("CardMeaning text must not be blank");
        } else if (meaning.meaning().trim().length() > MAX_MEANING_LENGTH) {
            errors.add("CardMeaning text must not exceed " + MAX_MEANING_LENGTH + " characters");
        }
        if (meaning.displayOrder() < 0) {
            errors.add("CardMeaning displayOrder must be non-negative");
        }
        if (!errors.isEmpty()) {
            throw new DomainValidationException(errors);
        }
    }

    /**
     * Kiểm tra tính hợp lệ của một câu ví dụ đi kèm thẻ học tập.
     *
     * @param example bản ghi câu ví dụ cần kiểm tra
     * @throws DomainValidationException nếu câu ví dụ không hợp lệ
     */
    public void validateExample(CardExample example) {
        List<String> errors = new ArrayList<>();
        if (example == null) {
            throw new DomainValidationException(List.of("CardExample must not be null"));
        }
        if (example.sentence() == null || example.sentence().isBlank()) {
            errors.add("CardExample sentence must not be blank");
        } else if (example.sentence().trim().length() > MAX_SENTENCE_LENGTH) {
            errors.add("CardExample sentence must not exceed " + MAX_SENTENCE_LENGTH + " characters");
        }
        if (example.translation() != null && example.translation().length() > MAX_SENTENCE_LENGTH) {
            errors.add("CardExample translation must not exceed " + MAX_SENTENCE_LENGTH + " characters");
        }
        if (example.displayOrder() < 0) {
            errors.add("CardExample displayOrder must be non-negative");
        }
        if (!errors.isEmpty()) {
            throw new DomainValidationException(errors);
        }
    }

    /**
     * Kiểm tra xem chuỗi JSON đầu vào có đúng cú pháp chuẩn hay không.
     *
     * @param json chuỗi JSON cần kiểm tra
     * @return true nếu chuỗi phân tích được thành cấu trúc JSON hợp lệ
     */
    private boolean isValidJson(String json) {
        try {
            STRICT_JSON_MAPPER.readTree(json);
            return true;
        } catch (JsonProcessingException exception) {
            return false;
        }
    }
}
