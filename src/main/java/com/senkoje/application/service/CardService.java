package com.senkoje.application.service;

import com.senkoje.domain.model.Card;
import com.senkoje.domain.model.CardExample;
import com.senkoje.domain.model.CardMeaning;
import com.senkoje.domain.model.CardType;
import com.senkoje.domain.model.ProficiencyLevel;
import com.senkoje.domain.model.ReviewState;
import com.senkoje.domain.repository.CardRepository;
import com.senkoje.domain.service.CardValidator;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Dịch vụ ứng dụng quản lý vòng đời thẻ học tập, trạng thái tạm ngưng, nghĩa bổ sung và câu ví dụ.
 */
public class CardService {

    private final CardRepository cardRepository;
    private final CardValidator cardValidator;
    private final Clock clock;

    /**
     * Khởi tạo dịch vụ thẻ sử dụng đồng hồ UTC mặc định của hệ thống.
     *
     * @param cardRepository kho lưu trữ thẻ
     * @param cardValidator bộ kiểm tra hợp lệ thẻ
     */
    public CardService(CardRepository cardRepository, CardValidator cardValidator) {
        this(cardRepository, cardValidator, Clock.systemUTC());
    }

    /**
     * Khởi tạo dịch vụ thẻ với đồng hồ được tiêm vào để phục vụ kiểm thử tất định.
     *
     * @param cardRepository kho lưu trữ thẻ
     * @param cardValidator bộ kiểm tra hợp lệ thẻ
     * @param clock nguồn thời gian
     */
    public CardService(CardRepository cardRepository, CardValidator cardValidator, Clock clock) {
        this.cardRepository = Objects.requireNonNull(cardRepository, "cardRepository must not be null");
        this.cardValidator = Objects.requireNonNull(cardValidator, "cardValidator must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    /**
     * Tạo mới một thẻ học tập cùng nghĩa bổ sung, câu ví dụ và trạng thái FSRS-6 NEW khởi đầu.
     *
     * @param deckId mã bộ thẻ chứa thẻ mới
     * @param cardType loại thẻ
     * @param term nội dung chính
     * @param reading cách đọc hoặc phiên âm (có thể null)
     * @param meaning nghĩa chính
     * @param partOfSpeech từ loại (có thể null)
     * @param level trình độ riêng của thẻ (có thể null)
     * @param extraData chuỗi JSON mở rộng hợp lệ (có thể null)
     * @param notes ghi chú cá nhân (có thể null)
     * @param meanings danh sách nghĩa bổ sung (có thể null hoặc rỗng)
     * @param examples danh sách câu ví dụ (có thể null hoặc rỗng)
     * @return thẻ đã được lưu thành công
     */
    public Card createCard(
            long deckId,
            CardType cardType,
            String term,
            String reading,
            String meaning,
            String partOfSpeech,
            ProficiencyLevel level,
            String extraData,
            String notes,
            List<CardMeaning> meanings,
            List<CardExample> examples
    ) {
        Instant now = clock.instant();
        Card candidate = new Card(
                null,
                deckId,
                cardType,
                term == null ? null : term.trim(),
                reading,
                meaning == null ? null : meaning.trim(),
                partOfSpeech,
                level,
                extraData,
                notes,
                false,
                now,
                now
        );
        List<CardMeaning> safeMeanings = meanings == null ? List.of() : meanings;
        List<CardExample> safeExamples = examples == null ? List.of() : examples;

        cardValidator.validate(candidate);
        for (CardMeaning item : safeMeanings) {
            cardValidator.validateMeaning(item);
        }
        for (CardExample item : safeExamples) {
            cardValidator.validateExample(item);
        }

        ReviewState initialReviewState = ReviewState.initialNew(null, now);
        return cardRepository.createWithDetails(candidate, safeMeanings, safeExamples, initialReviewState);
    }

    /**
     * Chỉnh sửa nội dung cốt lõi của một thẻ đã tồn tại sau khi kiểm tra tính hợp lệ.
     *
     * @param cardId mã thẻ cần cập nhật
     * @param cardType loại thẻ mới
     * @param term nội dung chính mới
     * @param reading cách đọc hoặc phiên âm mới
     * @param meaning nghĩa chính mới
     * @param partOfSpeech từ loại mới
     * @param level trình độ mới
     * @param extraData chuỗi JSON mở rộng hợp lệ mới
     * @param notes ghi chú mới
     * @return thẻ sau khi cập nhật
     */
    public Card editCard(
            long cardId,
            CardType cardType,
            String term,
            String reading,
            String meaning,
            String partOfSpeech,
            ProficiencyLevel level,
            String extraData,
            String notes
    ) {
        Card existing = requireCard(cardId);
        Card updated = new Card(
                existing.id(),
                existing.deckId(),
                cardType,
                term == null ? null : term.trim(),
                reading,
                meaning == null ? null : meaning.trim(),
                partOfSpeech,
                level,
                extraData,
                notes,
                existing.suspended(),
                existing.createdAt(),
                clock.instant()
        );
        cardValidator.validate(updated);
        return cardRepository.update(updated);
    }

    /**
     * Xóa thẻ theo mã định danh.
     *
     * @param cardId mã thẻ cần xóa
     * @return true nếu thẻ tồn tại và đã bị xóa
     */
    public boolean deleteCard(long cardId) {
        return cardRepository.deleteById(cardId);
    }

    /**
     * Lấy thông tin thẻ theo mã định danh.
     *
     * @param cardId mã thẻ
     * @return thẻ nếu tồn tại
     */
    public Optional<Card> getCard(long cardId) {
        return cardRepository.findById(cardId);
    }

    /**
     * Lấy toàn bộ thẻ thuộc một bộ thẻ xác định.
     *
     * @param deckId mã bộ thẻ
     * @return danh sách thẻ trong bộ thẻ
     */
    public List<Card> listCardsByDeck(long deckId) {
        return cardRepository.findByDeckId(deckId);
    }

    /**
     * Tìm kiếm và lọc thẻ theo bộ thẻ, từ khóa, loại thẻ và trạng thái tạm ngưng.
     *
     * @param deckId mã bộ thẻ cần lọc (có thể null)
     * @param query từ khóa tìm kiếm (có thể null)
     * @param cardType loại thẻ cần lọc (có thể null)
     * @param suspended cờ tạm ngưng cần lọc (có thể null)
     * @return danh sách thẻ phù hợp
     */
    public List<Card> searchCards(Long deckId, String query, CardType cardType, Boolean suspended) {
        return cardRepository.search(deckId, query, cardType, suspended);
    }

    /**
     * Tạm ngưng thẻ để không xuất hiện trong các phiên ôn tập tiếp theo.
     *
     * @param cardId mã thẻ
     * @return true nếu thẻ tồn tại và đã được tạm ngưng
     */
    public boolean suspendCard(long cardId) {
        return cardRepository.setSuspended(cardId, true, clock.instant());
    }

    /**
     * Kích hoạt lại thẻ đã bị tạm ngưng để tiếp tục ôn tập.
     *
     * @param cardId mã thẻ
     * @return true nếu thẻ tồn tại và đã được kích hoạt lại
     */
    public boolean unsuspendCard(long cardId) {
        return cardRepository.setSuspended(cardId, false, clock.instant());
    }

    /**
     * Lấy danh sách nghĩa bổ sung của một thẻ.
     *
     * @param cardId mã thẻ
     * @return danh sách nghĩa bổ sung
     */
    public List<CardMeaning> getMeanings(long cardId) {
        return cardRepository.findMeaningsByCardId(cardId);
    }

    /**
     * Kiểm tra hợp lệ và thay thế toàn bộ danh sách nghĩa bổ sung của một thẻ.
     *
     * @param cardId mã thẻ
     * @param meanings danh sách nghĩa bổ sung mới
     * @return danh sách nghĩa bổ sung đã lưu
     */
    public List<CardMeaning> replaceMeanings(long cardId, List<CardMeaning> meanings) {
        requireCard(cardId);
        List<CardMeaning> safeMeanings = meanings == null ? List.of() : meanings;
        for (CardMeaning item : safeMeanings) {
            cardValidator.validateMeaning(item);
        }
        return cardRepository.replaceMeanings(cardId, safeMeanings);
    }

    /**
     * Lấy danh sách câu ví dụ của một thẻ.
     *
     * @param cardId mã thẻ
     * @return danh sách câu ví dụ
     */
    public List<CardExample> getExamples(long cardId) {
        return cardRepository.findExamplesByCardId(cardId);
    }

    /**
     * Kiểm tra hợp lệ và thay thế toàn bộ danh sách câu ví dụ của một thẻ.
     *
     * @param cardId mã thẻ
     * @param examples danh sách câu ví dụ mới
     * @return danh sách câu ví dụ đã lưu
     */
    public List<CardExample> replaceExamples(long cardId, List<CardExample> examples) {
        requireCard(cardId);
        List<CardExample> safeExamples = examples == null ? List.of() : examples;
        for (CardExample item : safeExamples) {
            cardValidator.validateExample(item);
        }
        return cardRepository.replaceExamples(cardId, safeExamples);
    }

    private Card requireCard(long cardId) {
        return cardRepository.findById(cardId)
                .orElseThrow(() -> new IllegalArgumentException("Card not found: " + cardId));
    }
}
