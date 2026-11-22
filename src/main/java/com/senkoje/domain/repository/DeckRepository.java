package com.senkoje.domain.repository;

import com.senkoje.domain.model.Deck;
import com.senkoje.domain.model.DeckSettings;
import com.senkoje.domain.model.ProficiencyLevel;
import com.senkoje.domain.model.TargetLanguage;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Giao diện kho lưu trữ cho bộ thẻ và cấu hình lập lịch FSRS-6 của từng bộ thẻ.
 */
public interface DeckRepository {

    /**
     * Tạo mới một bộ thẻ cùng cấu hình deck_settings trong cùng một giao dịch nguyên tử.
     *
     * @param deck thông tin bộ thẻ mới
     * @param settings cấu hình FSRS-6 đi kèm bộ thẻ
     * @return bộ thẻ đã được lưu kèm mã định danh sinh tự động
     */
    Deck createWithSettings(Deck deck, DeckSettings settings);

    /**
     * Tìm bộ thẻ theo mã định danh duy nhất.
     *
     * @param deckId mã bộ thẻ
     * @return bộ thẻ tìm thấy nếu tồn tại
     */
    Optional<Deck> findById(long deckId);

    /**
     * Lấy danh sách toàn bộ bộ thẻ hiện có trong cơ sở dữ liệu.
     *
     * @return danh sách bộ thẻ
     */
    List<Deck> findAll();

    /**
     * Tìm kiếm và lọc bộ thẻ theo từ khóa, ngôn ngữ đích và trình độ.
     *
     * @param query từ khóa tìm kiếm trên tiêu đề và mô tả (có thể null hoặc rỗng)
     * @param targetLanguage ngôn ngữ đích cần lọc (có thể null)
     * @param level trình độ cần lọc (có thể null)
     * @return danh sách bộ thẻ khớp điều kiện
     */
    List<Deck> search(String query, TargetLanguage targetLanguage, ProficiencyLevel level);

    /**
     * Cập nhật thông tin của một bộ thẻ đã tồn tại.
     *
     * @param deck thông tin bộ thẻ cần cập nhật
     * @return bộ thẻ sau khi cập nhật
     */
    Deck update(Deck deck);

    /**
     * Xóa một bộ thẻ theo mã định danh.
     *
     * @param deckId mã bộ thẻ cần xóa
     * @return true nếu có bản ghi bị xóa
     */
    boolean deleteById(long deckId);

    /**
     * Lấy cấu hình FSRS-6 của một bộ thẻ theo mã bộ thẻ.
     *
     * @param deckId mã bộ thẻ
     * @return cấu hình bộ thẻ nếu tồn tại
     */
    Optional<DeckSettings> findSettingsByDeckId(long deckId);

    /**
     * Lưu mới hoặc cập nhật cấu hình FSRS-6 cho một bộ thẻ.
     *
     * @param settings cấu hình bộ thẻ cần lưu
     * @return cấu hình đã lưu thành công
     */
    DeckSettings saveSettings(DeckSettings settings);

    /**
     * Nhân bản toàn bộ bộ thẻ, cấu hình, thẻ, nghĩa bổ sung, ví dụ và khởi tạo trạng thái NEW cho các thẻ mới.
     *
     * @param sourceDeckId mã bộ thẻ gốc
     * @param newTitle tiêu đề của bộ thẻ nhân bản
     * @param now thời điểm tạo bộ thẻ nhân bản
     * @return bộ thẻ mới được nhân bản thành công
     */
    Deck duplicateWithCards(long sourceDeckId, String newTitle, Instant now);
}
