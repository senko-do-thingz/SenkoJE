package com.senkoje.infrastructure.exporter;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.senkoje.application.dto.CardExtraData;
import java.time.Instant;
import java.util.List;

/**
 * Lớp cấu trúc truyền tải dữ liệu JSON phục vụ xuất và nhập trọn vẹn một bộ thẻ học tập.
 */
public final class DeckExportPackage {

    public static final String CURRENT_SCHEMA_VERSION = "1.0";
    public static final String GENERATOR_NAME = "SenkoJE";

    private DeckExportPackage() {
    }

    /**
     * Gói toàn bộ dữ liệu của một tệp xuất JSON.
     *
     * @param schemaVersion phiên bản lược đồ định dạng tệp xuất
     * @param exportedAt thời điểm xuất tệp theo chuẩn ISO-8601 UTC
     * @param generator tên phần mềm tạo tệp
     * @param deck thông tin chi tiết và cấu hình của bộ thẻ
     * @param cards danh sách toàn bộ các thẻ học trong bộ thẻ
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Root(
            @JsonProperty("schema_version") String schemaVersion,
            @JsonProperty("exported_at") Instant exportedAt,
            @JsonProperty("generator") String generator,
            @JsonProperty("deck") DeckMetadata deck,
            @JsonProperty("cards") List<CardItem> cards
    ) {
    }

    /**
     * Siêu dữ liệu mô tả bộ thẻ cùng các tham số lập lịch FSRS-6.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record DeckMetadata(
            @JsonProperty("title") String title,
            @JsonProperty("target_language") String targetLanguage,
            @JsonProperty("definition_language") String definitionLanguage,
            @JsonProperty("level") String level,
            @JsonProperty("description") String description,
            @JsonProperty("settings") SettingsItem settings
    ) {
    }

    /**
     * Tham số cấu hình FSRS-6 gắn liền với bộ thẻ.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record SettingsItem(
            @JsonProperty("desired_retention") double desiredRetention,
            @JsonProperty("maximum_interval") int maximumInterval,
            @JsonProperty("new_cards_per_day") int newCardsPerDay,
            @JsonProperty("maximum_reviews_per_day") int maximumReviewsPerDay,
            @JsonProperty("learning_steps") String learningSteps,
            @JsonProperty("relearning_steps") String relearningSteps,
            @JsonProperty("enable_fuzzing") boolean enableFuzzing
    ) {
    }

    /**
     * Bản ghi chứa nội dung một thẻ học tập độc lập.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record CardItem(
            @JsonProperty("term") String term,
            @JsonProperty("reading") String reading,
            @JsonProperty("meaning") String meaning,
            @JsonProperty("card_type") String cardType,
            @JsonProperty("level") String level,
            @JsonProperty("part_of_speech") String partOfSpeech,
            @JsonProperty("notes") String notes,
            @JsonProperty("extra_data") CardExtraData extraData,
            @JsonProperty("meanings") List<MeaningItem> meanings,
            @JsonProperty("examples") List<ExampleItem> examples
    ) {
    }

    /**
     * Bản ghi nghĩa bổ sung theo ngôn ngữ giải nghĩa.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record MeaningItem(
            @JsonProperty("language_code") String languageCode,
            @JsonProperty("meaning") String meaning,
            @JsonProperty("display_order") int displayOrder
    ) {
    }

    /**
     * Bản ghi câu ví dụ kèm bản dịch ngữ cảnh.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ExampleItem(
            @JsonProperty("sentence") String sentence,
            @JsonProperty("translation") String translation,
            @JsonProperty("translation_language") String translationLanguage,
            @JsonProperty("display_order") int displayOrder
    ) {
    }
}
