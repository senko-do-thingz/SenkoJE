package com.senkoje.application.service;

import com.senkoje.domain.model.Deck;
import com.senkoje.domain.model.DeckSettings;
import com.senkoje.domain.model.DefinitionLanguage;
import com.senkoje.domain.model.ProficiencyLevel;
import com.senkoje.domain.model.TargetLanguage;
import com.senkoje.domain.repository.DeckRepository;
import com.senkoje.domain.service.DeckSettingsValidator;
import com.senkoje.domain.service.DeckValidator;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Dịch vụ ứng dụng điều phối toàn bộ thao tác quản lý bộ thẻ và cấu hình học tập FSRS-6.
 */
public class DeckService {

    private final DeckRepository deckRepository;
    private final DeckValidator deckValidator;
    private final DeckSettingsValidator deckSettingsValidator;
    private final Clock clock;

    /**
     * Khởi tạo dịch vụ bộ thẻ sử dụng đồng hồ UTC mặc định của hệ thống.
     *
     * @param deckRepository kho lưu trữ bộ thẻ
     * @param deckValidator bộ kiểm tra hợp lệ bộ thẻ
     * @param deckSettingsValidator bộ kiểm tra hợp lệ cấu hình bộ thẻ
     */
    public DeckService(
            DeckRepository deckRepository,
            DeckValidator deckValidator,
            DeckSettingsValidator deckSettingsValidator
    ) {
        this(deckRepository, deckValidator, deckSettingsValidator, Clock.systemUTC());
    }

    /**
     * Khởi tạo dịch vụ bộ thẻ với đồng hồ được tiêm vào để đảm bảo kiểm thử tất định.
     *
     * @param deckRepository kho lưu trữ bộ thẻ
     * @param deckValidator bộ kiểm tra hợp lệ bộ thẻ
     * @param deckSettingsValidator bộ kiểm tra hợp lệ cấu hình bộ thẻ
     * @param clock nguồn thời gian
     */
    public DeckService(
            DeckRepository deckRepository,
            DeckValidator deckValidator,
            DeckSettingsValidator deckSettingsValidator,
            Clock clock
    ) {
        this.deckRepository = Objects.requireNonNull(deckRepository, "deckRepository must not be null");
        this.deckValidator = Objects.requireNonNull(deckValidator, "deckValidator must not be null");
        this.deckSettingsValidator = Objects.requireNonNull(
                deckSettingsValidator, "deckSettingsValidator must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    /**
     * Tạo mới một bộ thẻ cùng bộ cấu hình FSRS-6 mặc định sau khi kiểm tra hợp lệ.
     *
     * @param title tên bộ thẻ
     * @param targetLanguage ngôn ngữ đích
     * @param definitionLanguage ngôn ngữ giải nghĩa
     * @param level trình độ ngôn ngữ
     * @param description mô tả ngắn (có thể null)
     * @return bộ thẻ đã lưu thành công
     */
    public Deck createDeck(
            String title,
            TargetLanguage targetLanguage,
            DefinitionLanguage definitionLanguage,
            ProficiencyLevel level,
            String description
    ) {
        Instant now = clock.instant();
        Deck candidate = new Deck(
                null,
                title == null ? null : title.trim(),
                targetLanguage,
                definitionLanguage,
                level,
                description,
                now,
                now
        );
        DeckSettings defaultSettings = DeckSettings.defaults(null);
        deckValidator.validate(candidate);
        deckSettingsValidator.validate(defaultSettings);
        return deckRepository.createWithSettings(candidate, defaultSettings);
    }

    /**
     * Đổi tên một bộ thẻ hiện có sau khi kiểm tra tính hợp lệ của tên mới.
     *
     * @param deckId mã bộ thẻ
     * @param newTitle tên bộ thẻ mới
     * @return bộ thẻ sau khi đổi tên
     */
    public Deck renameDeck(long deckId, String newTitle) {
        Deck existing = requireDeck(deckId);
        Deck updated = new Deck(
                existing.id(),
                newTitle == null ? null : newTitle.trim(),
                existing.targetLanguage(),
                existing.definitionLanguage(),
                existing.level(),
                existing.description(),
                existing.createdAt(),
                clock.instant()
        );
        deckValidator.validate(updated);
        return deckRepository.update(updated);
    }

    /**
     * Chỉnh sửa mô tả của một bộ thẻ hiện có sau khi kiểm tra giới hạn độ dài.
     *
     * @param deckId mã bộ thẻ
     * @param newDescription nội dung mô tả mới (có thể null)
     * @return bộ thẻ sau khi cập nhật mô tả
     */
    public Deck editDescription(long deckId, String newDescription) {
        Deck existing = requireDeck(deckId);
        Deck updated = new Deck(
                existing.id(),
                existing.title(),
                existing.targetLanguage(),
                existing.definitionLanguage(),
                existing.level(),
                newDescription,
                existing.createdAt(),
                clock.instant()
        );
        deckValidator.validate(updated);
        return deckRepository.update(updated);
    }

    /**
     * Xóa bộ thẻ theo mã định danh.
     *
     * @param deckId mã bộ thẻ cần xóa
     * @return true nếu bộ thẻ tồn tại và đã bị xóa
     */
    public boolean deleteDeck(long deckId) {
        return deckRepository.deleteById(deckId);
    }

    /**
     * Nhân bản bộ thẻ bao gồm cấu hình, toàn bộ thẻ, nghĩa bổ sung, ví dụ và khởi tạo trạng thái NEW mới.
     *
     * @param sourceDeckId mã bộ thẻ gốc
     * @param newTitle tiêu đề của bộ thẻ nhân bản
     * @return bộ thẻ mới được tạo
     */
    public Deck duplicateDeck(long sourceDeckId, String newTitle) {
        Deck source = requireDeck(sourceDeckId);
        Instant now = clock.instant();
        String trimmedTitle = newTitle == null ? null : newTitle.trim();
        Deck candidate = new Deck(
                null,
                trimmedTitle,
                source.targetLanguage(),
                source.definitionLanguage(),
                source.level(),
                source.description(),
                now,
                now
        );
        deckValidator.validate(candidate);
        return deckRepository.duplicateWithCards(sourceDeckId, trimmedTitle, now);
    }

    /**
     * Tìm bộ thẻ theo mã định danh.
     *
     * @param deckId mã bộ thẻ
     * @return bộ thẻ nếu tồn tại
     */
    public Optional<Deck> getDeck(long deckId) {
        return deckRepository.findById(deckId);
    }

    /**
     * Lấy danh sách toàn bộ bộ thẻ hiện có.
     *
     * @return danh sách bộ thẻ
     */
    public List<Deck> listAllDecks() {
        return deckRepository.findAll();
    }

    /**
     * Tìm kiếm và lọc bộ thẻ theo từ khóa, ngôn ngữ đích và trình độ.
     *
     * @param query từ khóa tìm kiếm (có thể null)
     * @param targetLanguage ngôn ngữ đích cần lọc (có thể null)
     * @param level trình độ cần lọc (có thể null)
     * @return danh sách bộ thẻ phù hợp
     */
    public List<Deck> searchDecks(String query, TargetLanguage targetLanguage, ProficiencyLevel level) {
        return deckRepository.search(query, targetLanguage, level);
    }

    /**
     * Đọc cấu hình FSRS-6 hiện tại của một bộ thẻ.
     *
     * @param deckId mã bộ thẻ
     * @return cấu hình bộ thẻ nếu tồn tại
     */
    public Optional<DeckSettings> getDeckSettings(long deckId) {
        return deckRepository.findSettingsByDeckId(deckId);
    }

    /**
     * Kiểm tra hợp lệ và cập nhật cấu hình FSRS-6 cho một bộ thẻ tồn tại.
     *
     * @param settings cấu hình mới của bộ thẻ
     * @return cấu hình sau khi lưu thành công
     */
    public DeckSettings updateDeckSettings(DeckSettings settings) {
        deckSettingsValidator.validate(settings);
        if (settings.deckId() == null) {
            throw new IllegalArgumentException("DeckSettings deckId must not be null");
        }
        requireDeck(settings.deckId());
        return deckRepository.saveSettings(settings);
    }

    private Deck requireDeck(long deckId) {
        return deckRepository.findById(deckId)
                .orElseThrow(() -> new IllegalArgumentException("Deck not found: " + deckId));
    }
}
