package com.senkoje.infrastructure.importer;

import com.senkoje.application.dto.CardExtraData;
import com.senkoje.domain.model.CardExample;
import com.senkoje.domain.model.CardMeaning;
import com.senkoje.domain.model.CardType;
import com.senkoje.domain.model.ProficiencyLevel;
import java.util.List;

/**
 * Bản ghi dữ liệu trung gian chứa nội dung thẻ được phân tích từ tệp JSON, CSV hoặc APKG.
 *
 * @param term nội dung chính cần ghi nhớ
 * @param reading cách đọc hoặc phiên âm
 * @param meaning giải nghĩa chính
 * @param cardType phân loại thẻ
 * @param level trình độ ngôn ngữ
 * @param partOfSpeech từ loại
 * @param notes ghi chú cá nhân
 * @param extraData dữ liệu mở rộng JSON
 * @param meanings danh sách nghĩa bổ sung
 * @param examples danh sách câu ví dụ
 */
public record ImportCandidateCard(
        String term,
        String reading,
        String meaning,
        CardType cardType,
        ProficiencyLevel level,
        String partOfSpeech,
        String notes,
        CardExtraData extraData,
        List<CardMeaning> meanings,
        List<CardExample> examples
) {

    public ImportCandidateCard {
        meanings = meanings == null ? List.of() : List.copyOf(meanings);
        examples = examples == null ? List.of() : List.copyOf(examples);
    }
}
