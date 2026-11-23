package com.senkoje.application.dto;

import com.senkoje.domain.fsrs.SchedulingResult;
import com.senkoje.domain.model.Card;
import com.senkoje.domain.model.CardExample;
import com.senkoje.domain.model.CardMeaning;
import com.senkoje.domain.model.Deck;
import com.senkoje.domain.model.ReviewRating;
import com.senkoje.domain.model.ReviewState;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Dữ liệu đầy đủ của một thẻ đang hiển thị trong phiên ôn tập,
 * kèm thông tin bộ thẻ, dữ liệu mở rộng, nghĩa bổ sung, câu ví dụ và xem trước lịch FSRS-6.
 *
 * @param card thông tin thẻ cốt lõi
 * @param deck thông tin bộ thẻ chứa thẻ này
 * @param reviewState trạng thái lập lịch FSRS-6 hiện tại của thẻ
 * @param additionalMeanings danh sách nghĩa bổ sung của thẻ
 * @param examples danh sách câu ví dụ của thẻ
 * @param extraData dữ liệu mở rộng có cấu trúc (tiếng Nhật, tiếng Anh, ngữ pháp)
 * @param ratingPreviews kết quả xem trước lịch FSRS-6 cho 4 mức đánh giá
 */
public record StudyCardItem(
        Card card,
        Deck deck,
        ReviewState reviewState,
        List<CardMeaning> additionalMeanings,
        List<CardExample> examples,
        CardExtraData extraData,
        Map<ReviewRating, SchedulingResult> ratingPreviews
) {

    /**
     * Đảm bảo các trường cốt lõi không bị null và danh sách là bản sao bất biến.
     */
    public StudyCardItem {
        Objects.requireNonNull(card, "card must not be null");
        Objects.requireNonNull(deck, "deck must not be null");
        Objects.requireNonNull(reviewState, "reviewState must not be null");
        additionalMeanings = additionalMeanings == null ? List.of() : List.copyOf(additionalMeanings);
        examples = examples == null ? List.of() : List.copyOf(examples);
        extraData = extraData == null ? CardExtraData.empty() : extraData;
        ratingPreviews = ratingPreviews == null ? Map.of() : Map.copyOf(ratingPreviews);
    }
}
