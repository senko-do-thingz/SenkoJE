package com.senkoje.presentation.controller;

import com.senkoje.application.dto.CardExtraData;
import com.senkoje.application.mapper.CardExtraDataCodec;
import com.senkoje.application.service.CardService;
import com.senkoje.application.service.DeckService;
import com.senkoje.domain.model.Card;
import com.senkoje.domain.model.CardExample;
import com.senkoje.domain.model.CardMeaning;
import com.senkoje.domain.model.CardType;
import com.senkoje.domain.model.Deck;
import com.senkoje.presentation.i18n.LocalizationManager;
import com.senkoje.presentation.navigation.RefreshableView;
import com.senkoje.presentation.theme.ThemeManager;
import com.senkoje.presentation.util.BackgroundTaskExecutor;
import com.senkoje.presentation.util.DialogHelper;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.Separator;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Window;
import javafx.util.StringConverter;

/**
 * Bộ điều khiển màn hình Trình duyệt thẻ hỗ trợ tìm kiếm, lọc theo bộ thẻ/loại thẻ/trình độ/trạng thái,
 * chọn hàng loạt để tạm ngưng/kích hoạt/xóa, xem trước chi tiết thẻ và mở hộp thoại chỉnh sửa thẻ.
 */
public class CardBrowserController implements RefreshableView {

    private static final String FILTER_ALL = "ALL";
    private static final String FILTER_ACTIVE = "ACTIVE";
    private static final String FILTER_SUSPENDED = "SUSPENDED";
    private static final String FILTER_CUSTOM = "CUSTOM";

    private final DeckService deckService;
    private final CardService cardService;
    private final LocalizationManager localizationManager;
    private final ThemeManager themeManager;
    private final BackgroundTaskExecutor backgroundTaskExecutor;

    private List<Deck> cachedDecks = List.of();
    private Map<Long, String> deckTitlesById = new HashMap<>();
    private boolean updatingDeckFilter = false;

    @FXML
    private Label titleLabel;
    @FXML
    private Label subtitleLabel;
    @FXML
    private Button refreshButton;
    @FXML
    private Button newCardButton;

    @FXML
    private TextField searchField;
    @FXML
    private ComboBox<DeckFilterOption> deckFilterBox;
    @FXML
    private ComboBox<String> typeFilterBox;
    @FXML
    private ComboBox<String> levelFilterBox;
    @FXML
    private ComboBox<String> statusFilterBox;

    @FXML
    private Label selectionCountLabel;
    @FXML
    private Button editButton;
    @FXML
    private Button suspendButton;
    @FXML
    private Button unsuspendButton;
    @FXML
    private Button deleteButton;

    @FXML
    private TableView<Card> cardsTable;
    @FXML
    private TableColumn<Card, String> termColumn;
    @FXML
    private TableColumn<Card, String> readingColumn;
    @FXML
    private TableColumn<Card, String> meaningColumn;
    @FXML
    private TableColumn<Card, String> typeColumn;
    @FXML
    private TableColumn<Card, String> levelColumn;
    @FXML
    private TableColumn<Card, String> deckColumn;
    @FXML
    private TableColumn<Card, String> statusColumn;

    @FXML
    private VBox previewPane;
    @FXML
    private Label previewTitleLabel;
    @FXML
    private Label previewEmptyLabel;
    @FXML
    private VBox previewContentBox;

    /**
     * Khởi tạo bộ điều khiển Trình duyệt thẻ với các dịch vụ bộ thẻ, thẻ và bộ thực thi nền.
     *
     * @param deckService dịch vụ bộ thẻ
     * @param cardService dịch vụ thẻ
     * @param localizationManager trình quản lý đa ngôn ngữ
     * @param themeManager trình quản lý chủ đề
     * @param backgroundTaskExecutor bộ thực thi tác vụ nền
     */
    public CardBrowserController(
            DeckService deckService,
            CardService cardService,
            LocalizationManager localizationManager,
            ThemeManager themeManager,
            BackgroundTaskExecutor backgroundTaskExecutor
    ) {
        this.deckService = Objects.requireNonNull(deckService, "deckService must not be null");
        this.cardService = Objects.requireNonNull(cardService, "cardService must not be null");
        this.localizationManager = Objects.requireNonNull(
                localizationManager, "localizationManager must not be null");
        this.themeManager = Objects.requireNonNull(themeManager, "themeManager must not be null");
        this.backgroundTaskExecutor = Objects.requireNonNull(
                backgroundTaskExecutor, "backgroundTaskExecutor must not be null");
    }

    @FXML
    private void initialize() {
        bindLocalizedLabels();
        configureFilters();
        configureTable();

        localizationManager.localeProperty().addListener((obs, oldVal, newVal) -> {
            refreshFilterDisplays();
            updateSelectionState();
            cardsTable.refresh();
            Card selected = cardsTable.getSelectionModel().getSelectedItem();
            if (selected != null) {
                loadCardPreview(selected);
            }
        });
    }

    @FXML
    private void handleRefresh() {
        refresh();
    }

    @FXML
    private void handleCreateCard() {
        if (cachedDecks.isEmpty()) {
            DialogHelper.showInfo(
                    getWindow(),
                    localizationManager.get("common.info"),
                    localizationManager.get("cards.noDecksWarning"),
                    themeManager
            );
            return;
        }
        CardEditorDialog.showAndWait(
                        getWindow(), cachedDecks, null, List.of(), List.of(), localizationManager, themeManager)
                .ifPresent(form -> backgroundTaskExecutor.runAsync(
                        () -> cardService.createCard(
                                form.deckId(),
                                form.cardType(),
                                form.term(),
                                form.reading(),
                                form.meaning(),
                                form.partOfSpeech(),
                                form.level(),
                                form.extraDataJson(),
                                form.notes(),
                                form.meanings(),
                                form.examples()
                        ),
                        created -> loadCards(),
                        this::showError
                ));
    }

    @FXML
    private void handleEditSelected() {
        Card selected = cardsTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            return;
        }
        backgroundTaskExecutor.runAsync(() -> new CardDetails(
                selected,
                cardService.getMeanings(selected.id()),
                cardService.getExamples(selected.id())
        ), details -> CardEditorDialog.showAndWait(
                        getWindow(),
                        cachedDecks,
                        details.card(),
                        details.meanings(),
                        details.examples(),
                        localizationManager,
                        themeManager
                ).ifPresent(form -> backgroundTaskExecutor.runAsyncVoid(() -> {
                    cardService.editCard(
                            details.card().id(),
                            form.cardType(),
                            form.term(),
                            form.reading(),
                            form.meaning(),
                            form.partOfSpeech(),
                            form.level(),
                            form.extraDataJson(),
                            form.notes()
                    );
                    cardService.replaceMeanings(details.card().id(), form.meanings());
                    cardService.replaceExamples(details.card().id(), form.examples());
                }, this::loadCards, this::showError)),
                this::showError);
    }

    @FXML
    private void handleSuspendSelected() {
        List<Card> selectedCards = List.copyOf(cardsTable.getSelectionModel().getSelectedItems());
        if (selectedCards.isEmpty()) {
            return;
        }
        backgroundTaskExecutor.runAsyncVoid(() -> {
            for (Card card : selectedCards) {
                cardService.suspendCard(card.id());
            }
        }, this::loadCards, this::showError);
    }

    @FXML
    private void handleUnsuspendSelected() {
        List<Card> selectedCards = List.copyOf(cardsTable.getSelectionModel().getSelectedItems());
        if (selectedCards.isEmpty()) {
            return;
        }
        backgroundTaskExecutor.runAsyncVoid(() -> {
            for (Card card : selectedCards) {
                cardService.unsuspendCard(card.id());
            }
        }, this::loadCards, this::showError);
    }

    @FXML
    private void handleDeleteSelected() {
        List<Card> selectedCards = List.copyOf(cardsTable.getSelectionModel().getSelectedItems());
        if (selectedCards.isEmpty()) {
            return;
        }
        boolean confirmed = DialogHelper.showConfirmation(
                getWindow(),
                localizationManager.get("cards.dialog.deleteTitle"),
                localizationManager.get("cards.dialog.deleteConfirm", selectedCards.size()),
                localizationManager,
                themeManager
        );
        if (!confirmed) {
            return;
        }
        backgroundTaskExecutor.runAsyncVoid(() -> {
            for (Card card : selectedCards) {
                cardService.deleteCard(card.id());
            }
        }, this::loadCards, this::showError);
    }

    @Override
    public void refresh() {
        backgroundTaskExecutor.runAsync(deckService::listAllDecks, decks -> {
            cachedDecks = decks;
            deckTitlesById = new HashMap<>();
            for (Deck deck : decks) {
                deckTitlesById.put(deck.id(), deck.title());
            }
            populateDeckFilter(decks);
            loadCards();
        }, this::showError);
    }

    private void loadCards() {
        DeckFilterOption selectedDeckOption = deckFilterBox.getValue();
        Long deckId = selectedDeckOption == null ? null : selectedDeckOption.deckId();
        String query = searchField.getText();
        String typeValue = typeFilterBox.getValue();
        CardType cardType = (typeValue == null || FILTER_ALL.equals(typeValue))
                ? null
                : CardType.valueOf(typeValue);
        String statusValue = statusFilterBox.getValue();
        Boolean suspendedFilter = FILTER_ACTIVE.equals(statusValue)
                ? Boolean.FALSE
                : (FILTER_SUSPENDED.equals(statusValue) ? Boolean.TRUE : null);
        String levelValue = levelFilterBox.getValue();

        backgroundTaskExecutor.runAsync(() -> {
            List<Card> rawCards = cardService.searchCards(deckId, query, cardType, suspendedFilter);
            if (levelValue == null || FILTER_ALL.equals(levelValue)) {
                return rawCards;
            }
            List<Card> filtered = new ArrayList<>();
            for (Card card : rawCards) {
                if (card.level() == null) {
                    continue;
                }
                if (FILTER_CUSTOM.equals(levelValue)) {
                    if (card.level().isCustom()) {
                        filtered.add(card);
                    }
                } else if (levelValue.equalsIgnoreCase(card.level().code())) {
                    filtered.add(card);
                }
            }
            return List.copyOf(filtered);
        }, cards -> {
            cardsTable.setItems(FXCollections.observableArrayList(cards));
            updateSelectionState();
            if (cards.isEmpty()) {
                clearPreview();
            } else {
                // Luôn đảm bảo chọn thẻ đầu tiên nếu chưa có thẻ nào được chọn và nạp khung xem trước
                if (cardsTable.getSelectionModel().getSelectedItem() == null) {
                    cardsTable.getSelectionModel().selectFirst();
                }
                Card selected = cardsTable.getSelectionModel().getSelectedItem();
                loadCardPreview(selected != null ? selected : cards.getFirst());
            }
        }, this::showError);
    }

    private void bindLocalizedLabels() {
        localizationManager.bind(titleLabel, "cards.title");
        localizationManager.bind(subtitleLabel, "cards.subtitle");
        localizationManager.bind(refreshButton, "common.refresh");
        localizationManager.bind(newCardButton, "cards.action.new");

        localizationManager.bindPrompt(searchField, "cards.search.prompt");

        localizationManager.bind(editButton, "common.edit");
        localizationManager.bind(suspendButton, "cards.action.suspend");
        localizationManager.bind(unsuspendButton, "cards.action.unsuspend");
        localizationManager.bind(deleteButton, "common.delete");

        localizationManager.bindColumn(termColumn, "cards.col.term");
        localizationManager.bindColumn(readingColumn, "cards.col.reading");
        localizationManager.bindColumn(meaningColumn, "cards.col.meaning");
        localizationManager.bindColumn(typeColumn, "cards.col.type");
        localizationManager.bindColumn(levelColumn, "cards.col.level");
        localizationManager.bindColumn(deckColumn, "cards.col.deck");
        localizationManager.bindColumn(statusColumn, "cards.col.status");

        localizationManager.bind(previewTitleLabel, "cards.preview.title");
        localizationManager.bind(previewEmptyLabel, "cards.preview.empty");

        Label emptyTableLabel = new Label();
        emptyTableLabel.getStyleClass().add("muted-label");
        localizationManager.bind(emptyTableLabel, "cards.empty.title");
        cardsTable.setPlaceholder(emptyTableLabel);
    }

    private void configureFilters() {
        // Định dạng bản địa hóa cho toàn bộ hộp chọn bộ lọc bộ thẻ, loại thẻ, trình độ và trạng thái
        deckFilterBox.setConverter(new StringConverter<>() {
            @Override
            public String toString(DeckFilterOption option) {
                return formatDeckFilter(option);
            }

            @Override
            public DeckFilterOption fromString(String string) {
                return null;
            }
        });
        deckFilterBox.setCellFactory(listView -> new ListCell<>() {
            @Override
            protected void updateItem(DeckFilterOption item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : formatDeckFilter(item));
            }
        });
        deckFilterBox.setButtonCell(new ListCell<>() {
            @Override
            protected void updateItem(DeckFilterOption item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : formatDeckFilter(item));
            }
        });

        typeFilterBox.setConverter(new StringConverter<>() {
            @Override
            public String toString(String code) {
                return formatTypeFilter(code);
            }

            @Override
            public String fromString(String string) {
                return null;
            }
        });
        typeFilterBox.setCellFactory(listView -> createLocalizedFilterCell(this::formatTypeFilter));
        typeFilterBox.setButtonCell(createLocalizedFilterCell(this::formatTypeFilter));
        typeFilterBox.getItems().setAll(
                FILTER_ALL, CardType.VOCABULARY.name(), CardType.PHRASE.name(), CardType.GRAMMAR.name()
        );
        typeFilterBox.setValue(FILTER_ALL);

        levelFilterBox.setConverter(new StringConverter<>() {
            @Override
            public String toString(String code) {
                return formatLevelFilter(code);
            }

            @Override
            public String fromString(String string) {
                return null;
            }
        });
        levelFilterBox.setCellFactory(listView -> createLocalizedFilterCell(this::formatLevelFilter));
        levelFilterBox.setButtonCell(createLocalizedFilterCell(this::formatLevelFilter));
        levelFilterBox.getItems().setAll(
                FILTER_ALL, "N5", "N4", "N3", "N2", "N1", "A1", "A2", "B1", "B2", "C1", "C2", FILTER_CUSTOM
        );
        levelFilterBox.setValue(FILTER_ALL);

        statusFilterBox.setConverter(new StringConverter<>() {
            @Override
            public String toString(String code) {
                return formatStatusFilter(code);
            }

            @Override
            public String fromString(String string) {
                return null;
            }
        });
        statusFilterBox.setCellFactory(listView -> createLocalizedFilterCell(this::formatStatusFilter));
        statusFilterBox.setButtonCell(createLocalizedFilterCell(this::formatStatusFilter));
        statusFilterBox.getItems().setAll(FILTER_ALL, FILTER_ACTIVE, FILTER_SUSPENDED);
        statusFilterBox.setValue(FILTER_ALL);

        searchField.textProperty().addListener((obs, oldVal, newVal) -> loadCards());
        deckFilterBox.valueProperty().addListener((obs, oldVal, newVal) -> {
            if (!updatingDeckFilter) {
                loadCards();
            }
        });
        typeFilterBox.valueProperty().addListener((obs, oldVal, newVal) -> {
            if (!updatingDeckFilter) {
                loadCards();
            }
        });
        levelFilterBox.valueProperty().addListener((obs, oldVal, newVal) -> {
            if (!updatingDeckFilter) {
                loadCards();
            }
        });
        statusFilterBox.valueProperty().addListener((obs, oldVal, newVal) -> {
            if (!updatingDeckFilter) {
                loadCards();
            }
        });
    }

    private void refreshFilterDisplays() {
        updatingDeckFilter = true;
        try {
            DeckFilterOption deckOption = deckFilterBox.getValue();
            deckFilterBox.setValue(null);
            deckFilterBox.setValue(deckOption);

            String typeVal = typeFilterBox.getValue();
            typeFilterBox.setValue(null);
            typeFilterBox.setValue(typeVal);

            String levelVal = levelFilterBox.getValue();
            levelFilterBox.setValue(null);
            levelFilterBox.setValue(levelVal);

            String statusVal = statusFilterBox.getValue();
            statusFilterBox.setValue(null);
            statusFilterBox.setValue(statusVal);
        } finally {
            updatingDeckFilter = false;
        }
    }

    private String formatDeckFilter(DeckFilterOption option) {
        if (option == null || option.deckId() == null) {
            return localizationManager.get("cards.filter.deck") + ": " + localizationManager.get("common.all");
        }
        return option.label();
    }

    private String formatTypeFilter(String code) {
        if (code == null || FILTER_ALL.equals(code)) {
            return localizationManager.get("cards.filter.type") + ": " + localizationManager.get("common.all");
        }
        return localizationManager.get("cardType." + code);
    }

    private String formatLevelFilter(String code) {
        if (code == null || FILTER_ALL.equals(code)) {
            return localizationManager.get("cards.filter.level") + ": " + localizationManager.get("common.all");
        }
        if (FILTER_CUSTOM.equals(code)) {
            return localizationManager.get("level.CUSTOM");
        }
        return code;
    }

    private String formatStatusFilter(String code) {
        if (code == null || FILTER_ALL.equals(code)) {
            return localizationManager.get("cards.filter.status") + ": " + localizationManager.get("common.all");
        }
        if (FILTER_ACTIVE.equals(code)) {
            return localizationManager.get("common.active");
        }
        if (FILTER_SUSPENDED.equals(code)) {
            return localizationManager.get("common.suspended");
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

    private void populateDeckFilter(List<Deck> decks) {
        updatingDeckFilter = true;
        try {
            Long previousDeckId = deckFilterBox.getValue() == null ? null : deckFilterBox.getValue().deckId();
            List<DeckFilterOption> options = new ArrayList<>();
            options.add(new DeckFilterOption(null, localizationManager.get("common.all")));
            DeckFilterOption restored = options.getFirst();
            for (Deck deck : decks) {
                DeckFilterOption option = new DeckFilterOption(deck.id(), deck.title());
                options.add(option);
                if (previousDeckId != null && previousDeckId.equals(deck.id())) {
                    restored = option;
                }
            }
            deckFilterBox.setItems(FXCollections.observableArrayList(options));
            deckFilterBox.setValue(restored);
        } finally {
            updatingDeckFilter = false;
        }
    }

    private void configureTable() {
        cardsTable.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        cardsTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);

        termColumn.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().term()));
        readingColumn.setCellValueFactory(cell -> new SimpleStringProperty(
                cell.getValue().reading() == null ? "" : cell.getValue().reading()));
        meaningColumn.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().meaning()));
        typeColumn.setCellValueFactory(cell -> new SimpleStringProperty(
                localizationManager.get("cardType." + cell.getValue().cardType().name())));
        levelColumn.setCellValueFactory(cell -> new SimpleStringProperty(
                cell.getValue().level() == null ? "-" : cell.getValue().level().code()));
        deckColumn.setCellValueFactory(cell -> new SimpleStringProperty(
                deckTitlesById.getOrDefault(cell.getValue().deckId(), "-")));
        statusColumn.setCellValueFactory(cell -> new SimpleStringProperty(
                localizationManager.get(cell.getValue().suspended() ? "common.suspended" : "common.active")));

        cardsTable.getSelectionModel().getSelectedItems().addListener(
                (ListChangeListener<Card>) change -> updateSelectionState());
        cardsTable.getSelectionModel().selectedItemProperty().addListener((obs, oldCard, newCard) -> {
            if (newCard == null) {
                clearPreview();
            } else {
                loadCardPreview(newCard);
            }
        });
    }

    private void updateSelectionState() {
        int count = cardsTable.getSelectionModel().getSelectedItems().size();
        selectionCountLabel.setText(localizationManager.get("cards.selectionCount", count));
        editButton.setDisable(count != 1);
        suspendButton.setDisable(count == 0);
        unsuspendButton.setDisable(count == 0);
        deleteButton.setDisable(count == 0);
    }

    private void loadCardPreview(Card card) {
        backgroundTaskExecutor.runAsync(() -> new CardDetails(
                card,
                cardService.getMeanings(card.id()),
                cardService.getExamples(card.id())
        ), this::renderPreview, this::showError);
    }

    private void renderPreview(CardDetails details) {
        Card card = details.card();
        previewEmptyLabel.setVisible(false);
        previewEmptyLabel.setManaged(false);
        previewContentBox.setVisible(true);
        previewContentBox.setManaged(true);
        previewContentBox.getChildren().clear();

        Label termLabel = new Label(card.term());
        termLabel.getStyleClass().add("screen-title");
        termLabel.setWrapText(true);

        HBox badges = new HBox(6);
        Label typeBadge = new Label(localizationManager.get("cardType." + card.cardType().name()));
        typeBadge.getStyleClass().addAll("badge", "badge-accent");
        badges.getChildren().add(typeBadge);
        if (card.level() != null) {
            Label levelBadge = new Label(card.level().code());
            levelBadge.getStyleClass().add("badge");
            badges.getChildren().add(levelBadge);
        }
        Label statusBadge = new Label(localizationManager.get(
                card.suspended() ? "common.suspended" : "common.active"));
        statusBadge.getStyleClass().add(card.suspended() ? "badge-warning" : "badge");
        badges.getChildren().add(statusBadge);

        previewContentBox.getChildren().addAll(termLabel, badges);

        if (card.reading() != null && !card.reading().isBlank()) {
            Label readingLabel = new Label(card.reading());
            readingLabel.getStyleClass().add("section-title");
            readingLabel.setWrapText(true);
            previewContentBox.getChildren().add(readingLabel);
        }

        Label meaningLabel = new Label(card.meaning());
        meaningLabel.setWrapText(true);
        previewContentBox.getChildren().add(meaningLabel);

        if (card.partOfSpeech() != null && !card.partOfSpeech().isBlank()) {
            previewContentBox.getChildren().add(createFieldRow(
                    localizationManager.get("cards.preview.partOfSpeech"), card.partOfSpeech()));
        }

        CardExtraData extra = CardExtraDataCodec.fromJson(card.extraData());
        if (!extra.isEmpty()) {
            previewContentBox.getChildren().add(new Separator());
            Label extraHeader = new Label(localizationManager.get("cards.preview.extraFields"));
            extraHeader.getStyleClass().add("section-title");
            previewContentBox.getChildren().add(extraHeader);

            addIfPresent("cardEditor.field.ja.furigana", extra.furigana());
            addIfPresent("cardEditor.field.ja.kanjiComponents", extra.kanjiComponents());
            addIfPresent("cardEditor.field.ja.onyomi", extra.onyomi());
            addIfPresent("cardEditor.field.ja.kunyomi", extra.kunyomi());
            addIfPresent("cardEditor.field.ja.pitchAccent", extra.pitchAccent());
            addIfPresent("cardEditor.field.en.ipa", extra.ipa());
            addIfPresent("cardEditor.field.en.collocations", extra.collocations());
            addIfPresent("cardEditor.field.en.register", extra.register());
            addIfPresent("cardEditor.field.grammar.pattern", extra.grammarPattern());
            addIfPresent("cardEditor.field.grammar.usage", extra.usageNotes());
            addIfPresent("cardEditor.field.grammar.formation", extra.formationRules());
            addIfPresent("cardEditor.field.grammar.relatedVocabulary", extra.relatedVocabulary());
        }

        if (!details.meanings().isEmpty()) {
            previewContentBox.getChildren().add(new Separator());
            Label meaningsHeader = new Label(localizationManager.get("cards.preview.meanings"));
            meaningsHeader.getStyleClass().add("section-title");
            previewContentBox.getChildren().add(meaningsHeader);
            for (CardMeaning meaning : details.meanings()) {
                Label item = new Label("[" + meaning.languageCode().getCode() + "] " + meaning.meaning());
                item.setWrapText(true);
                previewContentBox.getChildren().add(item);
            }
        }

        if (!details.examples().isEmpty()) {
            previewContentBox.getChildren().add(new Separator());
            Label examplesHeader = new Label(localizationManager.get("cards.preview.examples"));
            examplesHeader.getStyleClass().add("section-title");
            previewContentBox.getChildren().add(examplesHeader);
            for (CardExample example : details.examples()) {
                Label sentence = new Label(example.sentence());
                sentence.setWrapText(true);
                previewContentBox.getChildren().add(sentence);
                if (example.translation() != null && !example.translation().isBlank()) {
                    Label translation = new Label("  - " + example.translation());
                    translation.getStyleClass().add("muted-label");
                    translation.setWrapText(true);
                    previewContentBox.getChildren().add(translation);
                }
            }
        }

        if (card.notes() != null && !card.notes().isBlank()) {
            previewContentBox.getChildren().add(new Separator());
            previewContentBox.getChildren().add(createFieldRow(
                    localizationManager.get("cards.preview.notes"), card.notes()));
        }
    }

    private void addIfPresent(String labelKey, String value) {
        if (value != null && !value.isBlank()) {
            previewContentBox.getChildren().add(createFieldRow(localizationManager.get(labelKey), value));
        }
    }

    private VBox createFieldRow(String labelText, String valueText) {
        Label keyLabel = new Label(labelText);
        keyLabel.getStyleClass().add("muted-label");
        Label valueLabel = new Label(valueText);
        valueLabel.setWrapText(true);
        return new VBox(2, keyLabel, valueLabel);
    }

    private void clearPreview() {
        previewEmptyLabel.setVisible(true);
        previewEmptyLabel.setManaged(true);
        previewContentBox.setVisible(false);
        previewContentBox.setManaged(false);
        previewContentBox.getChildren().clear();
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

    private record DeckFilterOption(Long deckId, String label) {
    }

    private record CardDetails(Card card, List<CardMeaning> meanings, List<CardExample> examples) {
    }
}
