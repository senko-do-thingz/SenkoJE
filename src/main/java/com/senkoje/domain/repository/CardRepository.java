package com.senkoje.domain.repository;

import com.senkoje.domain.model.Card;
import com.senkoje.domain.model.CardExample;
import com.senkoje.domain.model.CardMeaning;
import com.senkoje.domain.model.CardType;
import com.senkoje.domain.model.ReviewState;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Giao diện kho lưu trữ cho thẻ học tập, nghĩa bổ sung và câu ví dụ đi kèm.
 */
public interface CardRepository {

    /**
     * Tạo mới thẻ cùng nghĩa bổ sung, câu ví dụ và trạng thái ôn tập khởi đầu trong một giao dịch nguyên tử.
     *
     * @param card thông tin thẻ mới
     * @param meanings danh sách nghĩa bổ sung
     * @param examples danh sách câu ví dụ
     * @param initialReviewState trạng thái ôn tập khởi đầu (thường là NEW)
     * @return thẻ đã lưu kèm mã định danh sinh tự động
     */
    Card createWithDetails(
            Card card,
            List<CardMeaning> meanings,
            List<CardExample> examples,
            ReviewState initialReviewState
    );

    /**
     * Tìm thẻ theo mã định danh duy nhất.
     *
     * @param cardId mã thẻ
     * @return thẻ tìm thấy nếu tồn tại
     */
    Optional<Card> findById(long cardId);

    /**
     * Lấy toàn bộ thẻ thuộc một bộ thẻ xác định.
     *
     * @param deckId mã bộ thẻ
     * @return danh sách thẻ trong bộ thẻ
     */
    List<Card> findByDeckId(long deckId);

    /**
     * Tìm kiếm và lọc thẻ theo bộ thẻ, từ khóa, loại thẻ và trạng thái tạm ngưng.
     *
     * @param deckId mã bộ thẻ cần lọc (có thể null)
     * @param query từ khóa tìm kiếm trên nội dung chính, cách đọc, nghĩa và ghi chú (có thể null)
     * @param cardType loại thẻ cần lọc (có thể null)
     * @param suspended trạng thái tạm ngưng cần lọc (có thể null)
     * @return danh sách thẻ phù hợp
     */
    List<Card> search(Long deckId, String query, CardType cardType, Boolean suspended);

    /**
     * Cập nhật thông tin cốt lõi của một thẻ đã tồn tại.
     *
     * @param card thẻ cần cập nhật
     * @return thẻ sau khi cập nhật
     */
    Card update(Card card);

    /**
     * Thay đổi trạng thái tạm ngưng ôn tập của một thẻ.
     *
     * @param cardId mã thẻ
     * @param suspended true để tạm ngưng, false để kích hoạt lại
     * @param updatedAt thời điểm cập nhật
     * @return true nếu thẻ tồn tại và đã được cập nhật
     */
    boolean setSuspended(long cardId, boolean suspended, Instant updatedAt);

    /**
     * Xóa thẻ theo mã định danh.
     *
     * @param cardId mã thẻ cần xóa
     * @return true nếu có thẻ bị xóa
     */
    boolean deleteById(long cardId);

    /**
     * Lấy danh sách nghĩa bổ sung của một thẻ theo thứ tự hiển thị tăng dần.
     *
     * @param cardId mã thẻ
     * @return danh sách nghĩa bổ sung
     */
    List<CardMeaning> findMeaningsByCardId(long cardId);

    /**
     * Thay thế toàn bộ danh sách nghĩa bổ sung của một thẻ trong một giao dịch nguyên tử.
     *
     * @param cardId mã thẻ
     * @param meanings danh sách nghĩa bổ sung mới
     * @return danh sách nghĩa bổ sung đã lưu kèm mã định danh
     */
    List<CardMeaning> replaceMeanings(long cardId, List<CardMeaning> meanings);

    /**
     * Lấy danh sách câu ví dụ của một thẻ theo thứ tự hiển thị tăng dần.
     *
     * @param cardId mã thẻ
     * @return danh sách câu ví dụ
     */
    List<CardExample> findExamplesByCardId(long cardId);

    /**
     * Thay thế toàn bộ danh sách câu ví dụ của một thẻ trong một giao dịch nguyên tử.
     *
     * @param cardId mã thẻ
     * @param examples danh sách câu ví dụ mới
     * @return danh sách câu ví dụ đã lưu kèm mã định danh
     */
    List<CardExample> replaceExamples(long cardId, List<CardExample> examples);
}
