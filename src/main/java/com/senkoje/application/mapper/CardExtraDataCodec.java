package com.senkoje.application.mapper;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.senkoje.application.dto.CardExtraData;

/**
 * Bộ chuyển đổi JSON cho dữ liệu mở rộng CardExtraData giữa giao diện chỉnh sửa thẻ và cột extra_data trong SQLite.
 */
public final class CardExtraDataCodec {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private CardExtraDataCodec() {
    }

    /**
     * Chuyển đổi CardExtraData sang chuỗi JSON hợp lệ hoặc null nếu mọi trường đều rỗng.
     *
     * @param data dữ liệu mở rộng của thẻ
     * @return chuỗi JSON hoặc null
     */
    public static String toJson(CardExtraData data) {
        if (data == null || data.isEmpty()) {
            return null;
        }
        CardExtraData normalized = new CardExtraData(
                trimToNull(data.furigana()),
                trimToNull(data.kanjiComponents()),
                trimToNull(data.onyomi()),
                trimToNull(data.kunyomi()),
                trimToNull(data.pitchAccent()),
                trimToNull(data.ipa()),
                trimToNull(data.collocations()),
                trimToNull(data.register()),
                trimToNull(data.grammarPattern()),
                trimToNull(data.usageNotes()),
                trimToNull(data.formationRules()),
                trimToNull(data.relatedVocabulary())
        );
        if (normalized.isEmpty()) {
            return null;
        }
        try {
            return OBJECT_MAPPER.writeValueAsString(normalized);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Failed to serialize CardExtraData to JSON", exception);
        }
    }

    /**
     * Phân tích chuỗi JSON extra_data thành đối tượng CardExtraData an toàn.
     *
     * @param json chuỗi JSON lưu trong thẻ (có thể null hoặc rỗng)
     * @return đối tượng CardExtraData
     */
    public static CardExtraData fromJson(String json) {
        if (json == null || json.isBlank()) {
            return CardExtraData.empty();
        }
        try {
            return OBJECT_MAPPER.readValue(json, CardExtraData.class);
        } catch (JsonProcessingException exception) {
            // Trả về đối tượng rỗng nếu chuỗi cũ không khớp cấu trúc thay vì làm lỗi màn hình xem trước
            return CardExtraData.empty();
        }
    }

    private static String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
