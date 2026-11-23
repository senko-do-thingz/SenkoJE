package com.senkoje.presentation.controller;

import com.senkoje.application.service.CardService;
import com.senkoje.application.service.DeckService;
import com.senkoje.domain.model.Deck;
import com.senkoje.domain.model.DeckSettings;
import com.senkoje.domain.model.ProficiencyLevel;
import com.senkoje.domain.model.TargetLanguage;
import com.senkoje.domain.repository.ReviewStateRepository;
import com.senkoje.presentation.i18n.LocalizationManager;
import com.senkoje.presentation.navigation.RefreshableView;
import com.senkoje.presentation.theme.ThemeManager;
import com.senkoje.presentation.util.BackgroundTaskExecutor;
import com.senkoje.presentation.util.DialogHelper;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.LongConsumer;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Window;
import javafx.util.StringConverter;

/**
 * Bộ điều khiển màn hình Thư viện bộ thẻ hỗ trợ tìm kiếm, lọc theo ngôn ngữ đích và trình độ,
 * cùng các thao tác tạo mới, đổi tên, chỉnh sửa, nhân bản, cài đặt FSRS-6 và xóa bộ thẻ.
 */
public class DeckLibraryController implements RefreshableView {

    private static final String FILTER_ALL = "ALL";
    private static final String FILTER_CUSTOM = "CUSTOM";

    private final DeckService deckService;
    private final CardService cardService;
    private final ReviewStateRepository reviewStateRepository;
    private final LocalizationManager localizationManager;
    private final ThemeManager themeManager;
    private final BackgroundTaskExecutor backgroundTaskExecutor;
    private final Clock clock;
    private final LongConsumer onStudyDeck;

    private List<DeckRowItem> latestRows = List.of();
    private boolean updatingFilterDisplays = false;

    @FXML
    private Label titleLabel;
    @FXML
    private Label subtitleLabel;
    @FXML
    private Button refreshButton;
    @FXML
    private Button newDeckButton;

    @FXML
    private TextField searchField;
    @FXML
    private Label languageFilterLabel;
    @FXML
    private ComboBox<String> languageFilterBox;
    @FXML
    private Label levelFilterLabel;
    @FXML
    private ComboBox<String> levelFilterBox;

    @FXML
    private VBox emptyStateBox;
    @FXML
    private Label emptyTitleLabel;
    @FXML
    private Label emptyDescriptionLabel;
    @FXML
    private Button emptyCreateButton;

    @FXML
    private ScrollPane deckScrollPane;
    @FXML
    private VBox deckListContainer;

    /**
     * Khởi tạo bộ điều khiển Thư viện bộ thẻ với các dịch vụ ứng dụng và bộ thực thi nền.
     *
     * @param deckService dịch vụ quản lý bộ thẻ
     * @param cardService dịch vụ thẻ dùng để đếm thẻ trong từng bộ
     * @param reviewStateRepository kho lưu trữ trạng thái ôn tập để đếm thẻ đến hạn và thẻ mới
     * @param localizationManager trình quản lý đa ngôn ngữ
     * @param themeManager trình quản lý chủ đề
     * @param backgroundTaskExecutor bộ thực thi tác vụ nền
     * @param clock nguồn thời gian ứng dụng
     */
    public DeckLibraryController(
            DeckService deckService,
            CardService cardService,
            ReviewStateRepository reviewStateRepository,
            LocalizationManager localizationManager,
            ThemeManager themeManager,
            BackgroundTaskExecutor backgroundTaskExecutor,
            Clock clock
    ) {
        this(
                deckService,
                cardService,
                reviewStateRepository,
                localizationManager,
                themeManager,
                backgroundTaskExecutor,
                clock,
                null
        );
    }

    /**
     * Khởi tạo bộ điều khiển Thư viện bộ thẻ kèm hành động mở nhanh Chế độ ôn tập cho từng bộ thẻ.
     *
     * @param deckService dịch vụ quản lý bộ thẻ
     * @param cardService dịch vụ thẻ dùng để đếm thẻ trong từng bộ
     * @param reviewStateRepository kho lưu trữ trạng thái ôn tập để đếm thẻ đến hạn và thẻ mới
     * @param localizationManager trình quản lý đa ngôn ngữ
     * @param themeManager trình quản lý chủ đề
     * @param backgroundTaskExecutor bộ thực thi tác vụ nền
     * @param clock nguồn thời gian ứng dụng
     * @param onStudyDeck hàm gọi lại khi người dùng nhấn nút Ôn tập trên một bộ thẻ (có thể null)
     */
    public DeckLibraryController(
            DeckService deckService,
            CardService cardService,
            ReviewStateRepository reviewStateRepository,
            LocalizationManager localizationManager,
            ThemeManager themeManager,
            BackgroundTaskExecutor backgroundTaskExecutor,
            Clock clock,
            LongConsumer onStudyDeck
    ) {
        this.deckService = Objects.requireNonNull(deckService, "deckService must not be null");
        this.cardService = Objects.requireNonNull(cardService, "cardService must not be null");
        this.reviewStateRepository = Objects.requireNonNull(
                reviewStateRepository, "reviewStateRepository must not be null");
        this.localizationManager = Objects.requireNonNull(
                localizationManager, "localizationManager must not be null");
        this.themeManager = Objects.requireNonNull(themeManager, "themeManager must not be null");
        this.backgroundTaskExecutor = Objects.requireNonNull(
                backgroundTaskExecutor, "backgroundTaskExecutor must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
        this.onStudyDeck = onStudyDeck;
    }

    @FXML
    private void initialize() {
        bindLocalizedLabels();
        configureFilters();
        localizationManager.localeProperty().addListener((obs, oldVal, newVal) -> {
            refreshFilterComboBoxDisplays();
            renderDecks(latestRows);
        });
    }

    @FXML
    private void handleRefresh() {
        refresh();
    }

    @FXML
    private void handleCreateDeck() {
        DeckEditorDialog.showAndWait(getWindow(), null, localizationManager, themeManager)
                .ifPresent(form -> backgroundTaskExecutor.runAsync(
                        () -> deckService.createDeck(
                                form.title(),
                                form.targetLanguage(),
                                form.definitionLanguage(),
                                form.level(),
                                form.description()
                        ),
                        created -> refresh(),
                        error -> showError(error)
                ));
    }

    @Override
    public void refresh() {
        String query = searchField.getText();
        String langFilter = languageFilterBox.getValue();
        String levelFilter = levelFilterBox.getValue();

        TargetLanguage targetLanguage = FILTER_ALL.equals(langFilter) || langFilter == null
                ? null
                : TargetLanguage.valueOf(langFilter);
        boolean filterCustomOnly = FILTER_CUSTOM.equals(levelFilter);
        ProficiencyLevel exactLevel = (levelFilter == null || FILTER_ALL.equals(levelFilter) || filterCustomOnly)
                ? null
                : ProficiencyLevel.of(levelFilter);

        backgroundTaskExecutor.runAsync(() -> {
            List<Deck> decks = deckService.searchDecks(query, targetLanguage, exactLevel);
            Instant now = clock.instant();
            List<DeckRowItem> rows = new ArrayList<>();
            for (Deck deck : decks) {
                if (filterCustomOnly && !deck.level().isCustom()) {
                    continue;
                }
                int totalCards = cardService.listCardsByDeck(deck.id()).size();
                int dueCards = reviewStateRepository.findDueByDeckId(deck.id(), now).size();
                int newCards = reviewStateRepository.findNewByDeckId(deck.id(), Integer.MAX_VALUE).size();
                rows.add(new DeckRowItem(deck, totalCards, dueCards, newCards));
            }
            return List.copyOf(rows);
        }, rows -> {
            latestRows = rows;
            renderDecks(rows);
        }, this::showError);
    }

    private void bindLocalizedLabels() {
        localizationManager.bind(titleLabel, "decks.title");
        localizationManager.bind(subtitleLabel, "decks.subtitle");
        localizationManager.bind(refreshButton, "common.refresh");
        localizationManager.bind(newDeckButton, "decks.action.new");

        localizationManager.bindPrompt(searchField, "decks.search.prompt");
        localizationManager.bind(languageFilterLabel, "decks.filter.language");
        localizationManager.bind(levelFilterLabel, "decks.filter.level");

        localizationManager.bind(emptyTitleLabel, "decks.empty.title");
        localizationManager.bind(emptyDescriptionLabel, "decks.empty.description");
        localizationManager.bind(emptyCreateButton, "decks.action.new");
    }

    private void configureFilters() {
        // Định dạng bản địa hóa cho bộ lọc ngôn ngữ đích và trình độ
        StringConverter<String> languageConverter = new StringConverter<>() {
            @Override
            public String toString(String code) {
                return formatLanguageFilter(code);
            }

            @Override
            public String fromString(String string) {
                return null;
            }
        };
        languageFilterBox.setConverter(languageConverter);
        languageFilterBox.setCellFactory(listView -> createLocalizedFilterCell(this::formatLanguageFilter));
        languageFilterBox.setButtonCell(createLocalizedFilterCell(this::formatLanguageFilter));
        languageFilterBox.getItems().setAll(FILTER_ALL, TargetLanguage.JA.name(), TargetLanguage.EN.name());
        languageFilterBox.setValue(FILTER_ALL);

        StringConverter<String> levelConverter = new StringConverter<>() {
            @Override
            public String toString(String code) {
                return formatLevelFilter(code);
            }

            @Override
            public String fromString(String string) {
                return null;
            }
        };
        levelFilterBox.setConverter(levelConverter);
        levelFilterBox.setCellFactory(listView -> createLocalizedFilterCell(this::formatLevelFilter));
        levelFilterBox.setButtonCell(createLocalizedFilterCell(this::formatLevelFilter));
        levelFilterBox.getItems().setAll(
                FILTER_ALL, "N5", "N4", "N3", "N2", "N1", "A1", "A2", "B1", "B2", "C1", "C2", FILTER_CUSTOM
        );
        levelFilterBox.setValue(FILTER_ALL);

        searchField.textProperty().addListener((obs, oldVal, newVal) -> refresh());
        languageFilterBox.valueProperty().addListener((obs, oldVal, newVal) -> {
            if (!updatingFilterDisplays) {
                refresh();
            }
        });
        levelFilterBox.valueProperty().addListener((obs, oldVal, newVal) -> {
            if (!updatingFilterDisplays) {
                refresh();
            }
        });
    }

    private void refreshFilterComboBoxDisplays() {
        updatingFilterDisplays = true;
        try {
            String lang = languageFilterBox.getValue();
            languageFilterBox.setValue(null);
            languageFilterBox.setValue(lang);

            String level = levelFilterBox.getValue();
            levelFilterBox.setValue(null);
            levelFilterBox.setValue(level);
        } finally {
            updatingFilterDisplays = false;
        }
    }

    private String formatLanguageFilter(String code) {
        if (code == null || FILTER_ALL.equals(code)) {
            return localizationManager.get("common.all");
        }
        return localizationManager.get("targetLang." + code);
    }

    private String formatLevelFilter(String code) {
        if (code == null || FILTER_ALL.equals(code)) {
            return localizationManager.get("common.all");
        }
        if (FILTER_CUSTOM.equals(code)) {
            return localizationManager.get("level.CUSTOM");
        }
        return code;
    }

    private ListCell<String> createLocalizedFilterCell(java.util.function.Function<String, String> formatter) {
        return new ListCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : formatter.apply(item));
            }
        };
    }

    private void renderDecks(List<DeckRowItem> rows) {
        boolean empty = rows.isEmpty();
        emptyStateBox.setVisible(empty);
        emptyStateBox.setManaged(empty);
        deckScrollPane.setVisible(!empty);
        deckScrollPane.setManaged(!empty);

        deckListContainer.getChildren().clear();
        for (DeckRowItem item : rows) {
            deckListContainer.getChildren().add(createDeckCardNode(item));
        }
    }

    private VBox createDeckCardNode(DeckRowItem item) {
        Deck deck = item.deck();

        Label nameLabel = new Label(deck.title());
        nameLabel.getStyleClass().add("section-title");

        Label langBadge = new Label(deck.targetLanguage().name());
        langBadge.getStyleClass().addAll("badge", "badge-accent");

        Label levelBadge = new Label(deck.level().code());
        levelBadge.getStyleClass().add("badge");

        Label defLangLabel = new Label(
                localizationManager.get("decks.field.definitionLanguage") + ": "
                        + deck.definitionLanguage().getCode());
        defLangLabel.getStyleClass().add("muted-label");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Label cardCountBadge = new Label(localizationManager.get("decks.cardCount", item.totalCards()));
        cardCountBadge.getStyleClass().add("badge");
        Label dueCountBadge = new Label(localizationManager.get("decks.dueCount", item.dueCards()));
        dueCountBadge.getStyleClass().addAll("badge", "badge-warning");
        Label newCountBadge = new Label(localizationManager.get("decks.newCount", item.newCards()));
        newCountBadge.getStyleClass().addAll("badge", "badge-accent");

        HBox headerRow = new HBox(8, nameLabel, langBadge, levelBadge, spacer,
                cardCountBadge, dueCountBadge, newCountBadge);
        headerRow.setAlignment(Pos.CENTER_LEFT);

        String descriptionText = deck.description() == null || deck.description().isBlank()
                ? "-"
                : deck.description();
        Label descriptionLabel = new Label(descriptionText);
        descriptionLabel.getStyleClass().add("muted-label");
        descriptionLabel.setWrapText(true);

        Button studyButton = new Button(localizationManager.get("decks.action.study"));
        studyButton.getStyleClass().add("primary-button");
        studyButton.setDisable(onStudyDeck == null);
        studyButton.setOnAction(event -> {
            if (onStudyDeck != null) {
                onStudyDeck.accept(deck.id());
            }
        });

        Button renameButton = new Button(localizationManager.get("common.rename"));
        renameButton.getStyleClass().add("secondary-button");
        renameButton.setOnAction(event -> handleRenameDeck(deck));

        Button editButton = new Button(localizationManager.get("common.edit"));
        editButton.getStyleClass().add("secondary-button");
        editButton.setOnAction(event -> handleEditDeck(deck));

        Button duplicateButton = new Button(localizationManager.get("common.duplicate"));
        duplicateButton.getStyleClass().add("secondary-button");
        duplicateButton.setOnAction(event -> handleDuplicateDeck(deck));

        Button settingsButton = new Button(localizationManager.get("decks.action.settings"));
        settingsButton.getStyleClass().add("secondary-button");
        settingsButton.setOnAction(event -> handleDeckSettings(deck));

        Button deleteButton = new Button(localizationManager.get("common.delete"));
        deleteButton.getStyleClass().add("danger-button");
        deleteButton.setOnAction(event -> handleDeleteDeck(deck));

        HBox footerRow = new HBox(8, defLangLabel, new Region(),
                studyButton, renameButton, editButton, duplicateButton, settingsButton, deleteButton);
        HBox.setHgrow(footerRow.getChildren().get(1), Priority.ALWAYS);
        footerRow.setAlignment(Pos.CENTER_LEFT);

        VBox cardBox = new VBox(8, headerRow, descriptionLabel, footerRow);
        cardBox.getStyleClass().add("card-panel");
        return cardBox;
    }

    private void handleRenameDeck(Deck deck) {
        DialogHelper.showTextInput(
                getWindow(),
                localizationManager.get("decks.dialog.renameTitle"),
                localizationManager.get("decks.field.title"),
                deck.title(),
                localizationManager,
                themeManager
        ).ifPresent(newTitle -> backgroundTaskExecutor.runAsync(
                () -> deckService.renameDeck(deck.id(), newTitle),
                updated -> refresh(),
                this::showError
        ));
    }

    private void handleEditDeck(Deck deck) {
        DeckEditorDialog.showAndWait(getWindow(), deck, localizationManager, themeManager)
                .ifPresent(form -> backgroundTaskExecutor.runAsync(
                        () -> deckService.updateDeck(
                                deck.id(),
                                form.title(),
                                form.targetLanguage(),
                                form.definitionLanguage(),
                                form.level(),
                                form.description()
                        ),
                        updated -> refresh(),
                        this::showError
                ));
    }

    private void handleDuplicateDeck(Deck deck) {
        String suggestedTitle = localizationManager.get("decks.duplicate.suffix", deck.title());
        DialogHelper.showTextInput(
                getWindow(),
                localizationManager.get("decks.dialog.duplicateTitle"),
                localizationManager.get("decks.field.title"),
                suggestedTitle,
                localizationManager,
                themeManager
        ).ifPresent(copyTitle -> backgroundTaskExecutor.runAsync(
                () -> deckService.duplicateDeck(deck.id(), copyTitle),
                duplicated -> refresh(),
                this::showError
        ));
    }

    private void handleDeckSettings(Deck deck) {
        backgroundTaskExecutor.runAsync(
                () -> deckService.getDeckSettings(deck.id()).orElse(DeckSettings.defaults(deck.id())),
                currentSettings -> DeckSettingsDialog.showAndWait(
                                getWindow(), deck, currentSettings, localizationManager, themeManager)
                        .ifPresent(updatedSettings -> backgroundTaskExecutor.runAsync(
                                () -> deckService.updateDeckSettings(updatedSettings),
                                saved -> refresh(),
                                this::showError
                        )),
                this::showError
        );
    }

    private void handleDeleteDeck(Deck deck) {
        boolean confirmed = DialogHelper.showConfirmation(
                getWindow(),
                localizationManager.get("decks.dialog.deleteTitle"),
                localizationManager.get("decks.dialog.deleteConfirm", deck.title()),
                localizationManager,
                themeManager
        );
        if (!confirmed) {
            return;
        }
        backgroundTaskExecutor.runAsync(
                () -> deckService.deleteDeck(deck.id()),
                deleted -> refresh(),
                this::showError
        );
    }

    private Window getWindow() {
        return titleLabel != null && titleLabel.getScene() != null ? titleLabel.getScene().getWindow() : null;
    }

    private void showError(Throwable error) {
        String message = error == null || error.getMessage() == null
                ? localizationManager.get("common.error")
                : error.getMessage();
        DialogHelper.showError(getWindow(), message, localizationManager, themeManager);
    }

    private record DeckRowItem(Deck deck, int totalCards, int dueCards, int newCards) {
    }
}
