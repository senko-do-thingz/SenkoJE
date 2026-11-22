package com.senkoje.domain.repository;

import com.senkoje.domain.model.CardState;
import com.senkoje.domain.model.ReviewState;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Giao diện kho lưu trữ cho trạng thái lập lịch FSRS-6 hiện tại của từng thẻ.
 */
public interface ReviewStateRepository {

    /**
     * Tìm trạng thái lập lịch hiện tại của một thẻ theo mã thẻ.
     *
     * @param cardId mã thẻ
     * @return trạng thái ôn tập nếu tồn tại
     */
    Optional<ReviewState> findByCardId(long cardId);

    /**
     * Lấy danh sách trạng thái của các thẻ không bị tạm ngưng và đã đến hạn ôn tập trong một bộ thẻ.
     *
     * @param deckId mã bộ thẻ
     * @param asOf mốc thời gian kiểm tra đến hạn
     * @return danh sách trạng thái thẻ đến hạn ôn tập
     */
    List<ReviewState> findDueByDeckId(long deckId, Instant asOf);

    /**
     * Lấy danh sách trạng thái của các thẻ mới (NEW) không bị tạm ngưng trong một bộ thẻ theo giới hạn số lượng.
     *
     * @param deckId mã bộ thẻ
     * @param limit số lượng thẻ mới tối đa cần lấy
     * @return danh sách trạng thái thẻ mới
     */
    List<ReviewState> findNewByDeckId(long deckId, int limit);

    /**
     * Lấy danh sách trạng thái ôn tập theo nhóm trạng thái FSRS-6.
     *
     * @param state trạng thái thẻ cần lọc
     * @return danh sách trạng thái tương ứng
     */
    List<ReviewState> findByState(CardState state);

    /**
     * Lưu mới hoặc cập nhật trạng thái lập lịch hiện tại của thẻ mà không xóa review_logs.
     *
     * @param reviewState trạng thái ôn tập cần lưu
     * @return trạng thái ôn tập đã lưu
     */
    ReviewState save(ReviewState reviewState);
}
