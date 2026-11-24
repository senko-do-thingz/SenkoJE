package com.senkoje.infrastructure.exporter;

import com.senkoje.domain.model.Card;
import com.senkoje.domain.model.CardExample;
import com.senkoje.domain.model.CardMeaning;
import java.util.List;
import java.util.Objects;

/**
 * Gói dữ liệu chứa thông tin cốt lõi của một thẻ học kèm các nghĩa phụ và câu ví dụ đi cùng.
 *
 * @param card thực thể thẻ học tập
 * @param meanings danh sách các nghĩa bổ sung của thẻ
 * @param examples danh sách các câu ví dụ minh họa của thẻ
 */
public record ExportCardBundle(
        Card card,
        List<CardMeaning> meanings,
        List<CardExample> examples
) {

    /**
     * Khởi tạo gói dữ liệu thẻ với danh sách bảo vệ chống null.
     */
    public ExportCardBundle {
        Objects.requireNonNull(card, "card must not be null");
        meanings = meanings == null ? List.of() : List.copyOf(meanings);
        examples = examples == null ? List.of() : List.copyOf(examples);
    }

    /**
     * Tạo gói dữ liệu cho thẻ chỉ có nghĩa chính, không có nghĩa phụ hay ví dụ.
     *
     * @param card thực thể thẻ học tập
     * @return gói dữ liệu thẻ
     */
    public static ExportCardBundle ofSingleCard(Card card) {
        return new ExportCardBundle(card, List.of(), List.of());
    }
}
