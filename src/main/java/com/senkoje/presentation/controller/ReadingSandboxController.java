package com.senkoje.presentation.controller;

import com.senkoje.application.service.CardService;
import com.senkoje.application.service.DeckService;
import com.senkoje.domain.model.CardExample;
import com.senkoje.domain.model.CardType;
import com.senkoje.domain.model.Deck;
import com.senkoje.domain.model.TargetLanguage;
import com.senkoje.infrastructure.nlp.EnglishReadingDocument;
import com.senkoje.infrastructure.nlp.EnglishSentence;
import com.senkoje.infrastructure.nlp.EnglishTextTokenizer;
import com.senkoje.infrastructure.nlp.EnglishToken;
import com.senkoje.infrastructure.nlp.FuriganaSegment;
import com.senkoje.infrastructure.nlp.FuriganaSegmentBuilder;
import com.senkoje.infrastructure.nlp.JapaneseToken;
import com.senkoje.infrastructure.nlp.JapaneseTokenizerService;
import com.senkoje.infrastructure.tts.TtsManager;
import com.senkoje.infrastructure.tts.TtsPlaybackResult;
import com.senkoje.presentation.component.FuriganaView;
import com.senkoje.presentation.i18n.LocalizationManager;
import com.senkoje.presentation.navigation.RefreshableView;
import com.senkoje.presentation.theme.ThemeManager;
import com.senkoje.presentation.util.BackgroundTaskExecutor;
import com.senkoje.presentation.util.DialogHelper;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Window;
import javafx.util.StringConverter;

/**
 * Bộ điều khiển màn hình Không gian thực hành đọc (Reading Sandbox):
 * hỗ trợ phân tích tiếng Nhật ngoại tuyến bằng Kuromoji (tải lười trên luồng nền), hiển thị và chỉnh sửa Furigana,
 * tách câu và từ tiếng Anh ngoại tuyến, phát âm TTS tùy chọn và quy trình thêm từ vựng vào bộ thẻ.
 */
public class ReadingSandboxController implements RefreshableView {

    private final JapaneseTokenizerService japaneseTokenizerService;
    private final FuriganaSegmentBuilder furiganaSegmentBuilder;
    private final EnglishTextTokenizer englishTextTokenizer;
    private final DeckService deckService;
    private final CardService cardService;
    private final TtsManager ttsManager;
    private final LocalizationManager localizationManager;
    private final ThemeManager themeManager;
    private final BackgroundTaskExecutor backgroundTaskExecutor;

    private final FuriganaView furiganaView = new FuriganaView();
    private final AtomicLong analysisSequence = new AtomicLong(0);

    private String lastAnalyzedText = "";
    private List<FuriganaSegment> currentJapaneseSegments = List.of();
    private int selectedJapaneseIndex = -1;
    private FuriganaSegment selectedJapaneseSegment;

    private EnglishReadingDocument currentEnglishDocument;
    private EnglishToken selectedEnglishToken;
    private Label selectedEnglishTokenNode;

    @FXML
    private VBox rootPane;

    @FXML
    private Label titleLabel;

    @FXML
    private Label subtitleLabel;

    @FXML
    private Label languageLabel;

    @FXML
    private ComboBox<TargetLanguage> languageComboBox;

    @FXML
    private CheckBox showFuriganaCheckBox;

    @FXML
    private Button analyzeButton;

    @FXML
    private Button speakTextButton;

    @FXML
    private Button clearButton;

    @FXML
    private HBox loadingBox;

    @FXML
    private Label loadingLabel;

    @FXML
    private Label statusLabel;

    @FXML
    private Label inputSectionLabel;

    @FXML
    private TextArea inputTextArea;

    @FXML
    private Label outputSectionLabel;

    @FXML
    private Label outputStatsLabel;

    @FXML
    private ScrollPane outputScrollPane;

    @FXML
    private StackPane outputContentStack;

    @FXML
    private VBox outputEmptyState;

    @FXML
    private Label outputEmptyTitleLabel;

    @FXML
    private Label outputEmptyDescriptionLabel;

    @FXML
    private VBox japaneseOutputContainer;

    @FXML
    private VBox englishOutputContainer;

    @FXML
    private Label inspectorTitleLabel;

    @FXML
    private VBox inspectorEmptyState;

    @FXML
    private Label inspectorEmptyTitleLabel;

    @FXML
    private Label inspectorEmptyDescriptionLabel;

    @FXML
    private VBox inspectorDetailBox;

    @FXML
    private Label inspectorSurfaceLabel;

    @FXML
    private Label inspectorPosBadge;

    @FXML
    private Label surfaceKeyLabel;

    @FXML
    private Label surfaceValueLabel;

    @FXML
    private Label baseFormKeyLabel;

    @FXML
    private Label baseFormValueLabel;

    @FXML
    private Label hiraganaKeyLabel;

    @FXML
    private Label hiraganaValueLabel;

    @FXML
    private Label katakanaKeyLabel;

    @FXML
    private Label katakanaValueLabel;

    @FXML
    private Label posKeyLabel;

    @FXML
    private Label posValueLabel;

    @FXML
    private VBox furiganaEditBox;

    @FXML
    private Label editReadingLabel;

    @FXML
    private TextField customReadingField;

    @FXML
    private Button applyReadingButton;

    @FXML
    private Button resetReadingButton;

    @FXML
    private Label furiganaPreviewKeyLabel;

    @FXML
    private Label furiganaPreviewValueLabel;

    @FXML
    private Label contextSentenceHeadingLabel;

    @FXML
    private Label contextSentenceValueLabel;

    @FXML
    private Label nlpBoundaryLabel;

    @FXML
    private Button speakTokenButton;

    @FXML
    private Button addToDeckButton;

    /**
     * Khởi tạo bộ điều khiển Không gian thực hành đọc với đầy đủ dịch vụ NLP, thẻ, bộ thẻ và TTS.
     */
    public ReadingSandboxController(
            JapaneseTokenizerService japaneseTokenizerService,
            FuriganaSegmentBuilder furiganaSegmentBuilder,
            EnglishTextTokenizer englishTextTokenizer,
            DeckService deckService,
            CardService cardService,
            TtsManager ttsManager,
            LocalizationManager localizationManager,
            ThemeManager themeManager,
            BackgroundTaskExecutor backgroundTaskExecutor
    ) {
        this.japaneseTokenizerService = Objects.requireNonNull(
                japaneseTokenizerService, "japaneseTokenizerService must not be null");
        this.furiganaSegmentBuilder = Objects.requireNonNull(
                furiganaSegmentBuilder, "furiganaSegmentBuilder must not be null");
        this.englishTextTokenizer = Objects.requireNonNull(
                englishTextTokenizer, "englishTextTokenizer must not be null");
        this.deckService = Objects.requireNonNull(deckService, "deckService must not be null");
        this.cardService = Objects.requireNonNull(cardService, "cardService must not be null");
        this.ttsManager = Objects.requireNonNull(ttsManager, "ttsManager must not be null");
        this.localizationManager = Objects.requireNonNull(
                localizationManager, "localizationManager must not be null");
        this.themeManager = Objects.requireNonNull(themeManager, "themeManager must not be null");
        this.backgroundTaskExecutor = Objects.requireNonNull(
                backgroundTaskExecutor, "backgroundTaskExecutor must not be null");
    }

    @FXML
    private void initialize() {
        bindStaticLocalization();
        configureLanguageSelector();
        configureFuriganaComponent();

        localizationManager.localeProperty().addListener((obs, oldLocale, newLocale) -> refreshLocalizedState());
        updateLanguageDependentControls();
    }

    @Override
    public void refresh() {
        refreshLocalizedState();
    }

    /**
     * Chạy phân tích văn bản hiện có trong ô nhập trên luồng nền (không bao giờ chạy Kuromoji trên FX Thread).
     */
    @FXML
    public void handleAnalyze() {
        String text = inputTextArea.getText();
        if (text == null || text.isBlank()) {
            showStatus(localizationManager.get("reading.status.emptyInput"), false);
            return;
        }

        TargetLanguage language = getSelectedLanguage();
        long requestId = analysisSequence.incrementAndGet();
        boolean firstKuromojiLoad = language == TargetLanguage.JA && !japaneseTokenizerService.isLoaded();

        setLoadingState(true, localizationManager.get(
                firstKuromojiLoad ? "reading.status.loadingDictionary" : "reading.status.analyzing"));
        hideStatus();

        if (language == TargetLanguage.JA) {
            backgroundTaskExecutor.runAsync(
                    () -> {
                        List<JapaneseToken> tokens = japaneseTokenizerService.tokenize(text);
                        return furiganaSegmentBuilder.buildSegments(tokens);
                    },
                    segments -> {
                        if (requestId != analysisSequence.get()) {
                            return;
                        }
                        setLoadingState(false, "");
                        displayJapaneseResult(text, segments);
                    },
                    error -> {
                        if (requestId != analysisSequence.get()) {
                            return;
                        }
                        setLoadingState(false, "");
                        showStatus(localizationManager.get("reading.status.analysisFailed"), true);
                    }
            );
        } else {
            backgroundTaskExecutor.runAsync(
                    () -> englishTextTokenizer.analyze(text),
                    document -> {
                        if (requestId != analysisSequence.get()) {
                            return;
                        }
                        setLoadingState(false, "");
                        displayEnglishResult(text, document);
                    },
                    error -> {
                        if (requestId != analysisSequence.get()) {
                            return;
                        }
                        setLoadingState(false, "");
                        showStatus(localizationManager.get("reading.status.analysisFailed"), true);
                    }
            );
        }
    }

    /**
     * Xóa toàn bộ văn bản nhập, kết quả phân tích và bảng kiểm tra token.
     */
    @FXML
    public void handleClear() {
        analysisSequence.incrementAndGet();
        inputTextArea.clear();
        clearAnalysisOutput();
        hideStatus();
    }

    /**
     * Phát âm đoạn văn bản đang bôi đen hoặc toàn bộ văn bản trong ô nhập thông qua TtsManager trên luồng nền.
     */
    @FXML
    public void handleSpeakText() {
        String selected = inputTextArea.getSelectedText();
        String textToSpeak = (selected != null && !selected.isBlank()) ? selected : inputTextArea.getText();
        speakOffline(textToSpeak, getSelectedLanguage());
    }

    /**
     * Phát âm từ vựng đang được chọn trong bảng kiểm tra token.
     */
    @FXML
    public void handleSpeakSelectedToken() {
        if (getSelectedLanguage() == TargetLanguage.JA && selectedJapaneseSegment != null) {
            String spoken = selectedJapaneseSegment.surface();
            speakOffline(spoken, TargetLanguage.JA);
        } else if (getSelectedLanguage() == TargetLanguage.EN && selectedEnglishToken != null) {
            speakOffline(selectedEnglishToken.text(), TargetLanguage.EN);
        }
    }

    /**
     * Áp dụng cách đọc Furigana do người dùng chỉnh sửa cho token tiếng Nhật đang chọn.
     */
    @FXML
    public void handleApplyCustomReading() {
        if (selectedJapaneseSegment == null || selectedJapaneseIndex < 0) {
            return;
        }
        String customReading = customReadingField.getText();
        FuriganaSegment updated = furiganaSegmentBuilder.withCustomReading(selectedJapaneseSegment, customReading);
        furiganaView.updateSegment(selectedJapaneseIndex, updated);
        currentJapaneseSegments = furiganaView.getSegments();
        selectedJapaneseSegment = updated;
        populateJapaneseInspector(updated);

        String displayReading = updated.effectiveReading() == null
                ? localizationManager.get("common.none")
                : updated.effectiveReading();
        showStatus(localizationManager.get("reading.status.readingUpdated", updated.surface(), displayReading), false);
    }

    /**
     * Khôi phục cách đọc gốc từ Kuromoji cho token tiếng Nhật đang chọn.
     */
    @FXML
    public void handleResetReading() {
        if (selectedJapaneseSegment == null || selectedJapaneseIndex < 0 || selectedJapaneseSegment.token() == null) {
            return;
        }
        FuriganaSegment restored = furiganaSegmentBuilder.buildSegment(selectedJapaneseSegment.token());
        furiganaView.updateSegment(selectedJapaneseIndex, restored);
        currentJapaneseSegments = furiganaView.getSegments();
        selectedJapaneseSegment = restored;
        populateJapaneseInspector(restored);
        showStatus(localizationManager.get("reading.status.readingReset", restored.surface()), false);
    }

    /**
     * Mở hộp thoại Thêm vào bộ thẻ (tái sử dụng CardEditorDialog và DeckEditorDialog)
     * với dữ liệu điền sẵn gồm thuật ngữ, cách đọc, Furigana, từ loại và câu ví dụ ngữ cảnh.
     */
    @FXML
    public void handleAddToDeck() {
        CardEditorDialog.CardPrefillData prefill = buildAddToDeckPrefill();
        if (prefill == null) {
            return;
        }

        backgroundTaskExecutor.runAsync(
                deckService::listAllDecks,
                decks -> openAddToDeckDialog(decks, prefill),
                error -> showStatus(error.getMessage(), true)
        );
    }

    /**
     * Tạo bản ghi dữ liệu điền sẵn cho hộp thoại Thêm vào bộ thẻ từ token đang chọn (phục vụ cả UI và kiểm thử).
     *
     * @return dữ liệu điền sẵn hoặc null nếu chưa chọn token
     */
    public CardEditorDialog.CardPrefillData buildAddToDeckPrefill() {
        TargetLanguage language = getSelectedLanguage();
        if (language == TargetLanguage.JA && selectedJapaneseSegment != null) {
            JapaneseToken token = selectedJapaneseSegment.token();
            String term = token != null && token.baseForm() != null && !token.baseForm().isBlank()
                    ? token.baseForm()
                    : selectedJapaneseSegment.surface();
            String reading = selectedJapaneseSegment.effectiveReading();
            // Nếu dạng nguyên thể khác dạng bề mặt (động từ chia thể), ưu tiên cách đọc chỉnh sửa nếu có
            String furiganaNotation = selectedJapaneseSegment.hasFurigana()
                    ? selectedJapaneseSegment.toBracketNotation()
                    : null;
            String partOfSpeech = token == null ? null : token.partOfSpeech();
            String contextSentence = token == null
                    ? ""
                    : japaneseTokenizerService.extractContextSentence(
                            lastAnalyzedText, token.startOffset(), token.endOffset());

            List<CardExample> examples = contextSentence.isBlank()
                    ? List.of()
                    : List.of(new CardExample(null, null, contextSentence, null, null, 0));

            return new CardEditorDialog.CardPrefillData(
                    TargetLanguage.JA,
                    CardType.VOCABULARY,
                    term,
                    reading,
                    "",
                    partOfSpeech,
                    furiganaNotation,
                    examples
            );
        }

        if (language == TargetLanguage.EN && selectedEnglishToken != null && currentEnglishDocument != null) {
            String term = selectedEnglishToken.normalizedWord() != null
                    ? selectedEnglishToken.normalizedWord()
                    : selectedEnglishToken.text();
            String contextSentence = currentEnglishDocument.getSentenceText(selectedEnglishToken.sentenceIndex());
            List<CardExample> examples = contextSentence.isBlank()
                    ? List.of()
                    : List.of(new CardExample(null, null, contextSentence, null, null, 0));

            return new CardEditorDialog.CardPrefillData(
                    TargetLanguage.EN,
                    CardType.VOCABULARY,
                    term,
                    null,
                    "",
                    null,
                    null,
                    examples
            );
        }
        return null;
    }

    /**
     * Trả về thành phần FuriganaView hiện tại để hỗ trợ kiểm thử và chụp ảnh giao diện.
     *
     * @return thành phần FuriganaView
     */
    public FuriganaView getFuriganaView() {
        return furiganaView;
    }

    /**
     * Lấy ngôn ngữ đọc đang được chọn trên thanh công cụ.
     *
     * @return ngôn ngữ đích (JA hoặc EN)
     */
    public TargetLanguage getSelectedLanguage() {
        TargetLanguage selected = languageComboBox.getValue();
        return selected == null ? TargetLanguage.JA : selected;
    }

    private void openAddToDeckDialog(List<Deck> availableDecks, CardEditorDialog.CardPrefillData prefill) {
        Window owner = rootPane.getScene() == null ? null : rootPane.getScene().getWindow();
        Optional<CardEditorDialog.CardFormData> formResult = CardEditorDialog.showForNewCard(
                owner,
                availableDecks,
                prefill,
                () -> createNewDeckOnTheFly(owner),
                localizationManager,
                themeManager
        );
        formResult.ifPresent(form -> saveNewCard(form));
    }

    private Optional<Deck> createNewDeckOnTheFly(Window owner) {
        Optional<DeckEditorDialog.DeckFormData> deckForm = DeckEditorDialog.showAndWait(
                owner,
                null,
                localizationManager,
                themeManager
        );
        if (deckForm.isEmpty()) {
            return Optional.empty();
        }
        DeckEditorDialog.DeckFormData form = deckForm.get();
        try {
            Deck created = deckService.createDeck(
                    form.title(),
                    form.targetLanguage(),
                    form.definitionLanguage(),
                    form.level(),
                    form.description()
            );
            return Optional.of(created);
        } catch (RuntimeException exception) {
            DialogHelper.showError(owner, exception.getMessage(), localizationManager, themeManager);
            return Optional.empty();
        }
    }

    private void saveNewCard(CardEditorDialog.CardFormData form) {
        backgroundTaskExecutor.runAsync(
                () -> {
                    cardService.createCard(
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
                    );
                    return deckService.getDeck(form.deckId()).map(Deck::title).orElse("#" + form.deckId());
                },
                deckTitle -> showStatus(
                        localizationManager.get("reading.status.cardSaved", form.term(), deckTitle), false),
                error -> showStatus(error.getMessage(), true)
        );
    }

    private void displayJapaneseResult(String sourceText, List<FuriganaSegment> segments) {
        lastAnalyzedText = sourceText;
        currentJapaneseSegments = segments;
        currentEnglishDocument = null;
        selectedJapaneseIndex = -1;
        selectedJapaneseSegment = null;
        selectedEnglishToken = null;
        selectedEnglishTokenNode = null;

        furiganaView.setSegments(segments);
        showOutputMode(true, false);

        long wordCount = segments.stream().filter(FuriganaSegment::isInspectable).count();
        outputStatsLabel.setText(localizationManager.get("reading.output.statsJa", wordCount));
        outputStatsLabel.setVisible(true);
        outputStatsLabel.setManaged(true);

        resetInspectorToEmpty();
        showStatus(localizationManager.get("reading.status.readyJa", wordCount), false);
    }

    private void displayEnglishResult(String sourceText, EnglishReadingDocument document) {
        lastAnalyzedText = sourceText;
        currentEnglishDocument = document;
        currentJapaneseSegments = List.of();
        selectedJapaneseIndex = -1;
        selectedJapaneseSegment = null;
        selectedEnglishToken = null;
        selectedEnglishTokenNode = null;

        englishOutputContainer.getChildren().clear();
        for (EnglishSentence sentence : document.sentences()) {
            VBox sentenceCard = new VBox(6);
            sentenceCard.getStyleClass().add("reading-en-sentence");

            Label sentenceHeader = new Label(localizationManager.get(
                    "reading.inspector.sentenceLabel", sentence.index() + 1));
            sentenceHeader.getStyleClass().add("reading-en-sentence-header");

            FlowPane tokenFlow = new FlowPane();
            tokenFlow.setHgap(2);
            tokenFlow.setVgap(4);
            tokenFlow.setAlignment(Pos.CENTER_LEFT);

            for (EnglishToken token : sentence.tokens()) {
                if (token.text().isBlank()) {
                    continue;
                }
                Label tokenLabel = new Label(token.text());
                if (token.word()) {
                    tokenLabel.getStyleClass().add("reading-en-token");
                    tokenLabel.setCursor(Cursor.HAND);
                    tokenLabel.setOnMouseClicked(event -> selectEnglishToken(token, tokenLabel));
                } else {
                    tokenLabel.getStyleClass().add("reading-en-punctuation");
                }
                tokenFlow.getChildren().add(tokenLabel);
            }

            sentenceCard.getChildren().addAll(sentenceHeader, tokenFlow);
            englishOutputContainer.getChildren().add(sentenceCard);
        }

        showOutputMode(false, true);

        long wordCount = document.tokens().stream().filter(EnglishToken::word).count();
        outputStatsLabel.setText(localizationManager.get(
                "reading.output.statsEn", document.sentences().size(), wordCount));
        outputStatsLabel.setVisible(true);
        outputStatsLabel.setManaged(true);

        resetInspectorToEmpty();
        showStatus(localizationManager.get(
                "reading.status.readyEn", document.sentences().size(), wordCount), false);
    }

    private void onJapaneseSegmentSelected(int index, FuriganaSegment segment) {
        selectedJapaneseIndex = index;
        selectedJapaneseSegment = segment;
        selectedEnglishToken = null;
        selectedEnglishTokenNode = null;
        populateJapaneseInspector(segment);
    }

    private void selectEnglishToken(EnglishToken token, Label tokenNode) {
        if (selectedEnglishTokenNode != null) {
            selectedEnglishTokenNode.getStyleClass().remove("reading-en-token-selected");
        }
        selectedEnglishToken = token;
        selectedEnglishTokenNode = tokenNode;
        if (!tokenNode.getStyleClass().contains("reading-en-token-selected")) {
            tokenNode.getStyleClass().add("reading-en-token-selected");
        }
        populateEnglishInspector(token);
    }

    private void populateJapaneseInspector(FuriganaSegment segment) {
        JapaneseToken token = segment.token();
        inspectorEmptyState.setVisible(false);
        inspectorEmptyState.setManaged(false);
        inspectorDetailBox.setVisible(true);
        inspectorDetailBox.setManaged(true);

        inspectorSurfaceLabel.setText(segment.surface());
        String pos1 = token == null || token.partOfSpeechLevel1().isBlank()
                ? localizationManager.get("common.unknown")
                : token.partOfSpeechLevel1();
        inspectorPosBadge.setText(pos1);
        inspectorPosBadge.setVisible(true);
        inspectorPosBadge.setManaged(true);

        surfaceValueLabel.setText(segment.surface());
        baseFormKeyLabel.setText(localizationManager.get("reading.inspector.baseForm"));
        baseFormValueLabel.setText(token == null ? segment.surface() : token.baseForm());

        setRowVisible(hiraganaKeyLabel, hiraganaValueLabel, true);
        setRowVisible(katakanaKeyLabel, katakanaValueLabel, true);
        setRowVisible(posKeyLabel, posValueLabel, true);

        String none = localizationManager.get("common.none");
        hiraganaValueLabel.setText(segment.effectiveReading() == null ? none : segment.effectiveReading());
        katakanaValueLabel.setText(token == null || token.readingKatakana() == null ? none : token.readingKatakana());
        posValueLabel.setText(token == null || token.partOfSpeech().isBlank() ? none : token.partOfSpeech());

        // Cho phép người dùng chỉnh sửa cách đọc Furigana đối với từ có chữ Hán
        boolean canEditFurigana = token != null && token.hasKanji();
        furiganaEditBox.setVisible(canEditFurigana);
        furiganaEditBox.setManaged(canEditFurigana);
        if (canEditFurigana) {
            customReadingField.setText(segment.effectiveReading() == null ? "" : segment.effectiveReading());
            furiganaPreviewValueLabel.setText(segment.toBracketNotation());
        }

        String contextSentence = token == null
                ? ""
                : japaneseTokenizerService.extractContextSentence(
                        lastAnalyzedText, token.startOffset(), token.endOffset());
        contextSentenceValueLabel.setText(contextSentence.isBlank() ? none : contextSentence);
        nlpBoundaryLabel.setText(localizationManager.get("reading.notice.jaBoundary"));
    }

    private void populateEnglishInspector(EnglishToken token) {
        inspectorEmptyState.setVisible(false);
        inspectorEmptyState.setManaged(false);
        inspectorDetailBox.setVisible(true);
        inspectorDetailBox.setManaged(true);

        inspectorSurfaceLabel.setText(token.text());
        inspectorPosBadge.setText("EN");
        inspectorPosBadge.setVisible(true);
        inspectorPosBadge.setManaged(true);

        surfaceValueLabel.setText(token.text());
        baseFormKeyLabel.setText(localizationManager.get("reading.inspector.normalizedWord"));
        baseFormValueLabel.setText(token.normalizedWord() == null ? token.text() : token.normalizedWord());

        setRowVisible(hiraganaKeyLabel, hiraganaValueLabel, false);
        setRowVisible(katakanaKeyLabel, katakanaValueLabel, false);
        setRowVisible(posKeyLabel, posValueLabel, false);

        furiganaEditBox.setVisible(false);
        furiganaEditBox.setManaged(false);

        String sentence = currentEnglishDocument == null
                ? ""
                : currentEnglishDocument.getSentenceText(token.sentenceIndex());
        contextSentenceValueLabel.setText(sentence.isBlank() ? localizationManager.get("common.none") : sentence);
        nlpBoundaryLabel.setText(localizationManager.get("reading.notice.enBoundary"));
    }

    private void speakOffline(String text, TargetLanguage language) {
        if (text == null || text.isBlank()) {
            showStatus(localizationManager.get("tts.status.emptyText"), false);
            return;
        }
        showStatus(localizationManager.get("study.audio.playing"), false);
        backgroundTaskExecutor.runAsync(
                () -> ttsManager.speak(text, language),
                this::handleTtsResult,
                error -> showStatus(localizationManager.get("tts.status.failed"), true)
        );
    }

    private void handleTtsResult(TtsPlaybackResult result) {
        if (result == null) {
            return;
        }
        String message = localizationManager.get(result.statusCode());
        showStatus(message, !result.succeeded());
    }

    private void configureLanguageSelector() {
        languageComboBox.getItems().setAll(TargetLanguage.JA, TargetLanguage.EN);
        languageComboBox.setValue(TargetLanguage.JA);
        languageComboBox.setConverter(new StringConverter<>() {
            @Override
            public String toString(TargetLanguage language) {
                if (language == null) {
                    return "";
                }
                return localizationManager.get("targetLang." + language.name());
            }

            @Override
            public TargetLanguage fromString(String string) {
                return null;
            }
        });
        languageComboBox.valueProperty().addListener((obs, oldLang, newLang) -> {
            updateLanguageDependentControls();
            clearAnalysisOutput();
            hideStatus();
        });
    }

    private void configureFuriganaComponent() {
        japaneseOutputContainer.getChildren().setAll(furiganaView);
        furiganaView.showFuriganaProperty().bind(showFuriganaCheckBox.selectedProperty());
        furiganaView.setOnSegmentSelected(this::onJapaneseSegmentSelected);
    }

    private void bindStaticLocalization() {
        localizationManager.bind(titleLabel, "reading.title");
        localizationManager.bind(subtitleLabel, "reading.subtitle");
        localizationManager.bind(languageLabel, "reading.toolbar.language");
        localizationManager.bind(showFuriganaCheckBox, "reading.toolbar.showFurigana");
        localizationManager.bind(analyzeButton, "reading.toolbar.analyze");
        localizationManager.bind(speakTextButton, "reading.toolbar.speakText");
        localizationManager.bind(clearButton, "reading.toolbar.clear");
        localizationManager.bind(inputSectionLabel, "reading.input.title");
        localizationManager.bind(outputSectionLabel, "reading.output.title");
        localizationManager.bind(outputEmptyTitleLabel, "reading.output.emptyTitle");
        localizationManager.bind(outputEmptyDescriptionLabel, "reading.output.emptyDescription");
        localizationManager.bind(inspectorTitleLabel, "reading.inspector.title");
        localizationManager.bind(inspectorEmptyTitleLabel, "reading.inspector.emptyTitle");
        localizationManager.bind(inspectorEmptyDescriptionLabel, "reading.inspector.emptyDescription");
        localizationManager.bind(surfaceKeyLabel, "reading.inspector.surface");
        localizationManager.bind(hiraganaKeyLabel, "reading.inspector.readingHiragana");
        localizationManager.bind(katakanaKeyLabel, "reading.inspector.readingKatakana");
        localizationManager.bind(posKeyLabel, "reading.inspector.partOfSpeech");
        localizationManager.bind(editReadingLabel, "reading.inspector.editReading");
        localizationManager.bind(applyReadingButton, "reading.inspector.applyReading");
        localizationManager.bind(resetReadingButton, "reading.inspector.resetReading");
        localizationManager.bind(furiganaPreviewKeyLabel, "reading.inspector.furiganaPreview");
        localizationManager.bind(contextSentenceHeadingLabel, "reading.inspector.contextSentence");
        localizationManager.bind(speakTokenButton, "reading.action.speakToken");
        localizationManager.bind(addToDeckButton, "reading.action.addToDeck");
    }

    private void refreshLocalizedState() {
        // Cập nhật lại chuỗi hiển thị trong ComboBox ngôn ngữ và các nhãn động theo ngôn ngữ giao diện mới
        TargetLanguage current = getSelectedLanguage();
        languageComboBox.getItems().setAll(TargetLanguage.JA, TargetLanguage.EN);
        languageComboBox.setValue(current);
        updateLanguageDependentControls();
        customReadingField.setPromptText(localizationManager.get("reading.inspector.editReadingPrompt"));

        if (selectedJapaneseSegment != null) {
            populateJapaneseInspector(selectedJapaneseSegment);
        } else if (selectedEnglishToken != null) {
            populateEnglishInspector(selectedEnglishToken);
        }
    }

    private void updateLanguageDependentControls() {
        boolean isJa = getSelectedLanguage() == TargetLanguage.JA;
        showFuriganaCheckBox.setVisible(isJa);
        showFuriganaCheckBox.setManaged(isJa);
        inputTextArea.setPromptText(localizationManager.get(
                isJa ? "reading.input.promptJa" : "reading.input.promptEn"));
        customReadingField.setPromptText(localizationManager.get("reading.inspector.editReadingPrompt"));
    }

    private void clearAnalysisOutput() {
        lastAnalyzedText = "";
        currentJapaneseSegments = List.of();
        currentEnglishDocument = null;
        selectedJapaneseIndex = -1;
        selectedJapaneseSegment = null;
        selectedEnglishToken = null;
        selectedEnglishTokenNode = null;
        furiganaView.clear();
        englishOutputContainer.getChildren().clear();
        outputStatsLabel.setVisible(false);
        outputStatsLabel.setManaged(false);
        showOutputMode(false, false);
        resetInspectorToEmpty();
    }

    private void showOutputMode(boolean showJapanese, boolean showEnglish) {
        boolean empty = !showJapanese && !showEnglish;
        outputEmptyState.setVisible(empty);
        outputEmptyState.setManaged(empty);
        japaneseOutputContainer.setVisible(showJapanese);
        japaneseOutputContainer.setManaged(showJapanese);
        englishOutputContainer.setVisible(showEnglish);
        englishOutputContainer.setManaged(showEnglish);
    }

    private void resetInspectorToEmpty() {
        inspectorEmptyState.setVisible(true);
        inspectorEmptyState.setManaged(true);
        inspectorDetailBox.setVisible(false);
        inspectorDetailBox.setManaged(false);
    }

    private void setLoadingState(boolean loading, String message) {
        loadingBox.setVisible(loading);
        loadingBox.setManaged(loading);
        loadingLabel.setText(message);
        analyzeButton.setDisable(loading);
    }

    private void showStatus(String message, boolean error) {
        statusLabel.setText(message);
        statusLabel.getStyleClass().removeAll("status-error", "status-success", "muted-label");
        statusLabel.getStyleClass().add(error ? "status-error" : "status-success");
        statusLabel.setVisible(true);
        statusLabel.setManaged(true);
    }

    private void hideStatus() {
        statusLabel.setText("");
        statusLabel.setVisible(false);
        statusLabel.setManaged(false);
    }

    private static void setRowVisible(Label keyLabel, Label valueLabel, boolean visible) {
        keyLabel.setVisible(visible);
        keyLabel.setManaged(visible);
        valueLabel.setVisible(visible);
        valueLabel.setManaged(visible);
    }
}
