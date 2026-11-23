package com.senkoje.presentation.controller;

import com.senkoje.application.dto.CardExtraData;
import com.senkoje.application.dto.StudyCardItem;
import com.senkoje.application.dto.StudySessionSnapshot;
import com.senkoje.application.dto.StudySessionStatus;
import com.senkoje.application.dto.TypingPreferences;
import com.senkoje.application.service.AppSettingsService;
import com.senkoje.application.service.DeckService;
import com.senkoje.application.service.StudySessionService;
import com.senkoje.domain.fsrs.SchedulingResult;
import com.senkoje.domain.model.Card;
import com.senkoje.domain.model.CardExample;
import com.senkoje.domain.model.CardMeaning;
import com.senkoje.domain.model.CardType;
import com.senkoje.domain.model.Deck;
import com.senkoje.domain.model.ReviewRating;
import com.senkoje.domain.model.TargetLanguage;
import com.senkoje.domain.service.TypingEvaluationOptions;
import com.senkoje.domain.service.TypingEvaluationResult;
import com.senkoje.domain.service.TypingEvaluator;
import com.senkoje.infrastructure.tts.TtsManager;
import com.senkoje.infrastructure.tts.TtsPlaybackResult;
import com.senkoje.presentation.i18n.LocalizationManager;
import com.senkoje.presentation.navigation.RefreshableView;
import com.senkoje.presentation.theme.ThemeManager;
import com.senkoje.presentation.util.BackgroundTaskExecutor;
import com.senkoje.presentation.util.DialogHelper;
import com.senkoje.presentation.util.StudyIntervalFormatter;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.event.EventHandler;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputControl;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Window;
import javafx.util.StringConverter;

/**
 * Bộ điều khiển Không gian ôn tập thẻ ghi nhớ tối giản:
 * hiển thị từng thẻ một theo mẫu Vocabulary/Phrase/Grammar, xem trước khoảng cách FSRS-6,
 * hỗ trợ phím tắt Space, 1-4, R, Esc, tạm dừng/tiếp tục và bảo toàn tiến độ phiên học.
 */
public class StudyWorkspaceController implements RefreshableView {

    private final StudySessionService studySessionService;
    private final DeckService deckService;
    private final AppSettingsService appSettingsService;
    private final TtsManager ttsManager;
    private final LocalizationManager localizationManager;
    private final ThemeManager themeManager;
    private final BackgroundTaskExecutor backgroundTaskExecutor;
    private final Runnable onNavigateDashboard;
    private final TypingEvaluator typingEvaluator = new TypingEvaluator();

    private List<DeckOption> availableDecks = List.of();
    private StudySessionSnapshot currentSnapshot;
    private boolean answerRevealed = false;
    private boolean submittingReview = false;
    private boolean dictationMode = false;
    private TypingEvaluationResult lastDictationResult;
    private TypingEvaluationOptions cachedTypingOptions = TypingEvaluationOptions.defaults();
    private int displayedSeconds = 0;
    private Timeline sessionTimer;
    private Scene attachedScene;
    private boolean updatingDeckDisplay = false;

    private final EventHandler<KeyEvent> shortcutHandler = this::handleKeyPressed;

    @FXML
    private StackPane rootPane;

    @FXML
    private VBox launcherPane;
    @FXML
    private Label launcherTitleLabel;
    @FXML
    private Label launcherSubtitleLabel;
    @FXML
    private Label deckPickerLabel;
    @FXML
    private ComboBox<DeckOption> deckSelectorBox;
    @FXML
    private Button startSessionButton;
    @FXML
    private Button refreshDecksButton;
    @FXML
    private CheckBox launcherDictationCheckBox;
    @FXML
    private Label queueSummaryLabel;
    @FXML
    private Label shortcutHintLabel;
    @FXML
    private VBox launcherEmptyBox;
    @FXML
    private Label launcherEmptyTitleLabel;
    @FXML
    private Label launcherEmptyDescriptionLabel;

    @FXML
    private VBox activeSessionPane;
    @FXML
    private Label sessionDeckTitleLabel;
    @FXML
    private Label cardTypeBadgeLabel;
    @FXML
    private Label cardLevelBadgeLabel;
    @FXML
    private Label progressSummaryLabel;
    @FXML
    private Label timerLabel;
    @FXML
    private Button toggleDictationButton;
    @FXML
    private Button replayAudioButton;
    @FXML
    private Button pauseResumeButton;
    @FXML
    private Button exitSessionButton;
    @FXML
    private Label audioStatusLabel;
    @FXML
    private HBox pausedBannerBox;
    @FXML
    private Label pausedBannerLabel;
    @FXML
    private Button resumeFromBannerButton;

    @FXML
    private VBox flashcardContainer;
    @FXML
    private VBox frontSectionBox;
    @FXML
    private Label frontTemplateCueLabel;
    @FXML
    private Label frontPrimaryLabel;
    @FXML
    private Label frontSecondaryLabel;
    @FXML
    private VBox dictationSectionBox;
    @FXML
    private TextField dictationInputField;
    @FXML
    private Button checkDictationButton;
    @FXML
    private VBox dictationFeedbackBox;
    @FXML
    private Label dictationFeedbackTitleLabel;
    @FXML
    private Label dictationFeedbackDetailLabel;
    @FXML
    private VBox backSectionBox;
    @FXML
    private VBox answerContentBox;

    @FXML
    private HBox revealButtonBox;
    @FXML
    private Button revealAnswerButton;
    @FXML
    private HBox ratingButtonsBox;
    @FXML
    private Button againButton;
    @FXML
    private Button hardButton;
    @FXML
    private Button goodButton;
    @FXML
    private Button easyButton;
    @FXML
    private Label activeShortcutFooterLabel;

    @FXML
    private VBox completePane;
    @FXML
    private Label completeTitleLabel;
    @FXML
    private Label completeSubtitleLabel;
    @FXML
    private Label summaryReviewedValueLabel;
    @FXML
    private Label summaryReviewedCaptionLabel;
    @FXML
    private Label summaryDurationValueLabel;
    @FXML
    private Label summaryDurationCaptionLabel;
    @FXML
    private HBox summaryBreakdownBox;
    @FXML
    private Button studyAnotherDeckButton;
    @FXML
    private Button backToDashboardButton;

    /**
     * Khởi tạo bộ điều khiển Không gian ôn tập với các dịch vụ ứng dụng qua constructor injection.
     *
     * @param studySessionService dịch vụ quản lý phiên học tập
     * @param deckService dịch vụ bộ thẻ để liệt kê danh sách bộ thẻ có thể học
     * @param localizationManager trình quản lý đa ngôn ngữ
     * @param themeManager trình quản lý chủ đề
     * @param backgroundTaskExecutor bộ thực thi tác vụ nền
     * @param onNavigateDashboard hành động quay về màn hình Tổng quan
     */
    public StudyWorkspaceController(
            StudySessionService studySessionService,
            DeckService deckService,
            LocalizationManager localizationManager,
            ThemeManager themeManager,
            BackgroundTaskExecutor backgroundTaskExecutor,
            Runnable onNavigateDashboard
    ) {
        this(
                studySessionService,
                deckService,
                null,
                localizationManager,
                themeManager,
                backgroundTaskExecutor,
                onNavigateDashboard
        );
    }

    /**
     * Khởi tạo bộ điều khiển Không gian ôn tập kèm dịch vụ cài đặt để đọc quy tắc chấm bài Dictation.
     *
     * @param studySessionService dịch vụ quản lý phiên học tập
     * @param deckService dịch vụ bộ thẻ để liệt kê danh sách bộ thẻ có thể học
     * @param appSettingsService dịch vụ cài đặt ứng dụng (có thể null khi kiểm thử độc lập)
     * @param localizationManager trình quản lý đa ngôn ngữ
     * @param themeManager trình quản lý chủ đề
     * @param backgroundTaskExecutor bộ thực thi tác vụ nền
     * @param onNavigateDashboard hành động quay về màn hình Tổng quan
     */
    public StudyWorkspaceController(
            StudySessionService studySessionService,
            DeckService deckService,
            AppSettingsService appSettingsService,
            LocalizationManager localizationManager,
            ThemeManager themeManager,
            BackgroundTaskExecutor backgroundTaskExecutor,
            Runnable onNavigateDashboard
    ) {
        this(
                studySessionService,
                deckService,
                appSettingsService,
                null,
                localizationManager,
                themeManager,
                backgroundTaskExecutor,
                onNavigateDashboard
        );
    }

    /**
     * Khởi tạo bộ điều khiển Không gian ôn tập đầy đủ kèm dịch vụ cài đặt và trình điều phối TTS ngoại tuyến.
     *
     * @param studySessionService dịch vụ quản lý phiên học tập
     * @param deckService dịch vụ bộ thẻ để liệt kê danh sách bộ thẻ có thể học
     * @param appSettingsService dịch vụ cài đặt ứng dụng (có thể null khi kiểm thử độc lập)
     * @param ttsManager trình điều phối phát âm TTS ngoại tuyến (có thể null khi kiểm thử độc lập)
     * @param localizationManager trình quản lý đa ngôn ngữ
     * @param themeManager trình quản lý chủ đề
     * @param backgroundTaskExecutor bộ thực thi tác vụ nền
     * @param onNavigateDashboard hành động quay về màn hình Tổng quan
     */
    public StudyWorkspaceController(
            StudySessionService studySessionService,
            DeckService deckService,
            AppSettingsService appSettingsService,
            TtsManager ttsManager,
            LocalizationManager localizationManager,
            ThemeManager themeManager,
            BackgroundTaskExecutor backgroundTaskExecutor,
            Runnable onNavigateDashboard
    ) {
        this.studySessionService = Objects.requireNonNull(
                studySessionService, "studySessionService must not be null");
        this.deckService = Objects.requireNonNull(deckService, "deckService must not be null");
        this.appSettingsService = appSettingsService;
        this.ttsManager = ttsManager;
        this.localizationManager = Objects.requireNonNull(
                localizationManager, "localizationManager must not be null");
        this.themeManager = Objects.requireNonNull(themeManager, "themeManager must not be null");
        this.backgroundTaskExecutor = Objects.requireNonNull(
                backgroundTaskExecutor, "backgroundTaskExecutor must not be null");
        this.onNavigateDashboard = onNavigateDashboard;
    }

    @FXML
    private void initialize() {
        bindLocalizedLabels();
        configureDeckSelector();
        configureSessionTimer();

        rootPane.sceneProperty().addListener((obs, oldScene, newScene) -> {
            if (oldScene != null) {
                oldScene.removeEventFilter(KeyEvent.KEY_PRESSED, shortcutHandler);
            }
            attachedScene = newScene;
            if (newScene != null) {
                newScene.addEventFilter(KeyEvent.KEY_PRESSED, shortcutHandler);
            }
        });

        localizationManager.localeProperty().addListener((obs, oldVal, newVal) -> {
            refreshDeckSelectorDisplays();
            if (currentSnapshot != null) {
                if (currentSnapshot.isCompleted()) {
                    renderCompletedSummary(currentSnapshot);
                } else {
                    renderActiveSnapshot(currentSnapshot);
                }
            }
        });

        showLauncherPane();
    }

    @Override
    public void refresh() {
        // Nếu đang có phiên học hoạt động thì giữ nguyên phiên hiện tại thay vì hủy ngang
        if (currentSnapshot != null && !currentSnapshot.isCompleted()) {
            return;
        }
        loadLauncherDecks(null);
    }

    /**
     * Bắt đầu trực tiếp một phiên học mới cho một bộ thẻ chỉ định (ví dụ gọi từ Thư viện bộ thẻ).
     *
     * @param deckId mã bộ thẻ cần học (null để học toàn bộ bộ thẻ)
     */
    public void startStudyForDeck(Long deckId) {
        if (currentSnapshot != null && !currentSnapshot.isCompleted()) {
            long previousId = currentSnapshot.session().id();
            stopSessionTimer();
            backgroundTaskExecutor.runAsync(
                    () -> {
                        studySessionService.finishSession(previousId);
                        return studySessionService.startSession(deckId);
                    },
                    this::applyStartedSession,
                    this::showError
            );
            return;
        }
        startNewSessionAsync(deckId);
    }

    /**
     * Đóng phiên học hiện tại một cách an toàn khi người dùng chuyển sang mục điều hướng khác hoặc thoát ứng dụng.
     */
    public void finishActiveSessionIfNeeded() {
        if (currentSnapshot == null || currentSnapshot.isCompleted()) {
            return;
        }
        long sessionId = currentSnapshot.session().id();
        stopSessionTimer();
        stopTtsPlayback();
        currentSnapshot = null;
        answerRevealed = false;
        submittingReview = false;
        backgroundTaskExecutor.submitFireAndForget(() -> studySessionService.finishSession(sessionId));
    }

    @FXML
    private void handleRefreshLauncher() {
        DeckOption selected = deckSelectorBox.getValue();
        loadLauncherDecks(selected == null ? null : selected.deckId());
    }

    @FXML
    private void handleStartSelectedDeck() {
        dictationMode = launcherDictationCheckBox != null && launcherDictationCheckBox.isSelected();
        DeckOption selected = deckSelectorBox.getValue();
        Long deckId = selected == null ? null : selected.deckId();
        startNewSessionAsync(deckId);
    }

    /**
     * Bật hoặc tắt chế độ luyện nghe chép / gõ đáp án (Dictation) trong Không gian ôn tập.
     *
     * @param enabled true để bật chế độ Dictation, false để quay về thẻ ghi nhớ thường
     */
    public void setDictationMode(boolean enabled) {
        this.dictationMode = enabled;
        if (launcherDictationCheckBox != null) {
            launcherDictationCheckBox.setSelected(enabled);
        }
        resetDictationInputState();
        if (currentSnapshot != null && !currentSnapshot.isCompleted()) {
            renderActiveSnapshot(currentSnapshot);
        }
    }

    /**
     * Kiểm tra chế độ Dictation có đang được kích hoạt hay không.
     *
     * @return true nếu đang ở chế độ Dictation
     */
    public boolean isDictationMode() {
        return dictationMode;
    }

    @FXML
    private void handleToggleDictationMode() {
        setDictationMode(!dictationMode);
    }

    @FXML
    public void handleSubmitDictation() {
        if (!dictationMode
                || currentSnapshot == null
                || currentSnapshot.isCompleted()
                || currentSnapshot.status() == StudySessionStatus.PAUSED
                || currentSnapshot.currentCard() == null) {
            return;
        }
        StudyCardItem item = currentSnapshot.currentCard();
        List<String> acceptedAnswers = buildExplicitAcceptedAnswers(item);
        lastDictationResult = typingEvaluator.evaluate(
                item.deck().targetLanguage(),
                dictationInputField.getText(),
                acceptedAnswers,
                cachedTypingOptions
        );
        renderDictationFeedback(lastDictationResult);
        answerRevealed = true;
        updateRevealStateUi();
        // Chuyển tiêu điểm ra khỏi ô nhập để người dùng có thể bấm 1-4 đánh giá ngay sau khi xem kết quả
        goodButton.requestFocus();
    }

    @FXML
    public void handleRevealAnswer() {
        if (currentSnapshot == null
                || currentSnapshot.isCompleted()
                || currentSnapshot.status() == StudySessionStatus.PAUSED
                || answerRevealed) {
            return;
        }
        answerRevealed = true;
        updateRevealStateUi();
    }

    @FXML
    private void handleRateAgain() {
        submitRating(ReviewRating.AGAIN);
    }

    @FXML
    private void handleRateHard() {
        submitRating(ReviewRating.HARD);
    }

    @FXML
    private void handleRateGood() {
        submitRating(ReviewRating.GOOD);
    }

    @FXML
    private void handleRateEasy() {
        submitRating(ReviewRating.EASY);
    }

    @FXML
    public void handleReplayAudio() {
        if (currentSnapshot == null || currentSnapshot.currentCard() == null) {
            return;
        }
        if (ttsManager == null) {
            showAudioStatus(localizationManager.get("tts.status.disabled"), true);
            return;
        }
        StudyCardItem item = currentSnapshot.currentCard();
        String textToSpeak = item.card().term();
        TargetLanguage language = item.deck().targetLanguage();

        replayAudioButton.setDisable(true);
        showAudioStatus(localizationManager.get("study.audio.playing"), false);
        backgroundTaskExecutor.runAsync(
                () -> ttsManager.speak(textToSpeak, language),
                this::handleTtsPlaybackResult,
                error -> {
                    replayAudioButton.setDisable(false);
                    showAudioStatus(localizationManager.get("tts.status.failed"), true);
                }
        );
    }

    private void handleTtsPlaybackResult(TtsPlaybackResult result) {
        replayAudioButton.setDisable(false);
        if (result == null) {
            showAudioStatus(localizationManager.get("tts.status.failed"), true);
            return;
        }
        if (result.succeeded()) {
            hideAudioStatus();
        } else {
            // Hiển thị rõ trạng thái bản địa hóa khi TTS ngoại tuyến không khả dụng thay vì im lặng
            showAudioStatus(localizationManager.get(result.statusCode()), true);
        }
    }

    @FXML
    private void handleTogglePause() {
        if (currentSnapshot == null || currentSnapshot.isCompleted() || submittingReview) {
            return;
        }
        long sessionId = currentSnapshot.session().id();
        boolean currentlyPaused = currentSnapshot.status() == StudySessionStatus.PAUSED;
        setSessionControlsDisabled(true);
        backgroundTaskExecutor.runAsync(
                () -> currentlyPaused
                        ? studySessionService.resumeSession(sessionId)
                        : studySessionService.pauseSession(sessionId),
                snapshot -> {
                    currentSnapshot = snapshot;
                    displayedSeconds = snapshot.activeDurationSeconds();
                    renderActiveSnapshot(snapshot);
                    if (snapshot.status() == StudySessionStatus.PAUSED) {
                        stopSessionTimer();
                    } else {
                        startSessionTimer();
                    }
                    setSessionControlsDisabled(false);
                },
                error -> {
                    setSessionControlsDisabled(false);
                    showError(error);
                }
        );
    }

    @FXML
    public void handleExitSession() {
        if (currentSnapshot == null || currentSnapshot.isCompleted()) {
            showLauncherPane();
            loadLauncherDecks(null);
            return;
        }
        long sessionId = currentSnapshot.session().id();
        stopSessionTimer();
        stopTtsPlayback();
        setSessionControlsDisabled(true);
        backgroundTaskExecutor.runAsync(
                () -> studySessionService.finishSession(sessionId),
                finished -> {
                    currentSnapshot = finished;
                    setSessionControlsDisabled(false);
                    renderCompletedSummary(finished);
                    showCompletePane();
                },
                error -> {
                    setSessionControlsDisabled(false);
                    showError(error);
                }
        );
    }

    @FXML
    private void handleReturnToLauncher() {
        currentSnapshot = null;
        answerRevealed = false;
        submittingReview = false;
        showLauncherPane();
        loadLauncherDecks(null);
    }

    @FXML
    private void handleBackToDashboard() {
        currentSnapshot = null;
        answerRevealed = false;
        submittingReview = false;
        if (onNavigateDashboard != null) {
            onNavigateDashboard.run();
        }
    }

    /**
     * Xử lý phím tắt toàn cục trong Không gian ôn tập: Space, 1-4, R và Esc.
     *
     * @param event sự kiện nhấn phím JavaFX
     */
    private void handleKeyPressed(KeyEvent event) {
        if (!activeSessionPane.isVisible() || rootPane.getScene() == null || !rootPane.isVisible()) {
            return;
        }
        // Không kích hoạt phím tắt học tập khi con trỏ đang nhập liệu trong ô văn bản (trừ phím Esc)
        Node focusOwner = attachedScene == null ? null : attachedScene.getFocusOwner();
        boolean typingInInput = focusOwner instanceof TextInputControl;

        if (event.getCode() == KeyCode.ESCAPE) {
            event.consume();
            handleExitSession();
            return;
        }
        if (typingInInput || currentSnapshot == null || currentSnapshot.isCompleted()) {
            return;
        }
        if (currentSnapshot.status() == StudySessionStatus.PAUSED || submittingReview) {
            return;
        }

        switch (event.getCode()) {
            case SPACE -> {
                if (!answerRevealed) {
                    event.consume();
                    handleRevealAnswer();
                }
            }
            case DIGIT1, NUMPAD1 -> {
                if (answerRevealed) {
                    event.consume();
                    submitRating(ReviewRating.AGAIN);
                }
            }
            case DIGIT2, NUMPAD2 -> {
                if (answerRevealed) {
                    event.consume();
                    submitRating(ReviewRating.HARD);
                }
            }
            case DIGIT3, NUMPAD3 -> {
                if (answerRevealed) {
                    event.consume();
                    submitRating(ReviewRating.GOOD);
                }
            }
            case DIGIT4, NUMPAD4 -> {
                if (answerRevealed) {
                    event.consume();
                    submitRating(ReviewRating.EASY);
                }
            }
            case R -> {
                event.consume();
                handleReplayAudio();
            }
            default -> {
                // Không xử lý các phím khác
            }
        }
    }

    private void submitRating(ReviewRating rating) {
        if (currentSnapshot == null
                || currentSnapshot.isCompleted()
                || currentSnapshot.status() == StudySessionStatus.PAUSED
                || currentSnapshot.currentCard() == null
                || !answerRevealed
                || submittingReview) {
            return;
        }

        long sessionId = currentSnapshot.session().id();
        long expectedCardId = currentSnapshot.currentCard().card().id();

        // Khóa nút đánh giá và phím tắt ngay khi bắt đầu gửi để chặn gửi trùng lặp
        submittingReview = true;
        stopTtsPlayback();
        setRatingButtonsDisabled(true);

        backgroundTaskExecutor.runAsync(
                () -> studySessionService.submitCurrentCardReview(sessionId, expectedCardId, rating),
                outcome -> {
                    submittingReview = false;
                    StudySessionSnapshot nextSnapshot = outcome.sessionSnapshot();
                    currentSnapshot = nextSnapshot;
                    displayedSeconds = nextSnapshot.activeDurationSeconds();
                    answerRevealed = false;
                    resetDictationInputState();
                    hideAudioStatus();

                    if (nextSnapshot.isCompleted()) {
                        stopSessionTimer();
                        renderCompletedSummary(nextSnapshot);
                        showCompletePane();
                    } else {
                        renderActiveSnapshot(nextSnapshot);
                    }
                },
                error -> {
                    submittingReview = false;
                    setRatingButtonsDisabled(false);
                    showError(error);
                }
        );
    }

    private void startNewSessionAsync(Long deckId) {
        startSessionButton.setDisable(true);
        backgroundTaskExecutor.runAsync(
                () -> {
                    refreshTypingOptionsOffFxThread();
                    return studySessionService.startSession(deckId);
                },
                snapshot -> {
                    startSessionButton.setDisable(false);
                    applyStartedSession(snapshot);
                },
                error -> {
                    startSessionButton.setDisable(false);
                    showError(error);
                }
        );
    }

    private void applyStartedSession(StudySessionSnapshot snapshot) {
        currentSnapshot = snapshot;
        displayedSeconds = snapshot.activeDurationSeconds();
        answerRevealed = false;
        submittingReview = false;
        resetDictationInputState();
        hideAudioStatus();

        if (snapshot.isCompleted()) {
            stopSessionTimer();
            renderCompletedSummary(snapshot);
            showCompletePane();
        } else {
            showActiveSessionPane();
            renderActiveSnapshot(snapshot);
            startSessionTimer();
        }
    }

    private void loadLauncherDecks(Long preferredDeckId) {
        backgroundTaskExecutor.runAsync(() -> {
            List<Deck> decks = deckService.searchDecks(null, null, null);
            List<DeckOption> options = new ArrayList<>();
            int allDueCount = studySessionService.countAvailableStudyCards(null);
            options.add(new DeckOption(null, null, allDueCount));
            for (Deck deck : decks) {
                int available = studySessionService.countAvailableStudyCards(deck.id());
                options.add(new DeckOption(deck.id(), deck, available));
            }
            return List.copyOf(options);
        }, options -> {
            availableDecks = options;
            updatingDeckDisplay = true;
            try {
                deckSelectorBox.getItems().setAll(options);
                DeckOption toSelect = options.getFirst();
                if (preferredDeckId != null) {
                    for (DeckOption option : options) {
                        if (Objects.equals(option.deckId(), preferredDeckId)) {
                            toSelect = option;
                            break;
                        }
                    }
                }
                deckSelectorBox.setValue(toSelect);
            } finally {
                updatingDeckDisplay = false;
            }
            updateLauncherQueueSummary();
        }, this::showError);
    }

    private void updateLauncherQueueSummary() {
        DeckOption selected = deckSelectorBox.getValue();
        boolean hasDecks = availableDecks.size() > 1;
        launcherEmptyBox.setVisible(!hasDecks);
        launcherEmptyBox.setManaged(!hasDecks);

        if (selected == null) {
            queueSummaryLabel.setText("");
            startSessionButton.setDisable(true);
            return;
        }
        if (selected.availableCards() <= 0) {
            queueSummaryLabel.setText(localizationManager.get("study.launcher.noCardsReady"));
            startSessionButton.setDisable(true);
        } else {
            queueSummaryLabel.setText(
                    localizationManager.get("study.launcher.cardsReady", selected.availableCards()));
            startSessionButton.setDisable(false);
        }
    }

    private void renderActiveSnapshot(StudySessionSnapshot snapshot) {
        StudyCardItem item = snapshot.currentCard();
        if (item == null) {
            return;
        }

        Card card = item.card();
        Deck deck = item.deck();
        sessionDeckTitleLabel.setText(deck.title());
        cardTypeBadgeLabel.setText(localizationManager.get("cardType." + card.cardType().name()));
        cardLevelBadgeLabel.setText(card.level() != null ? card.level().code() : deck.level().code());
        progressSummaryLabel.setText(localizationManager.get(
                "study.progress", snapshot.completedReviews(), snapshot.remainingCards()));
        timerLabel.setText(StudyIntervalFormatter.formatTimer(displayedSeconds));

        boolean paused = snapshot.status() == StudySessionStatus.PAUSED;
        pausedBannerBox.setVisible(paused);
        pausedBannerBox.setManaged(paused);
        pauseResumeButton.setText(localizationManager.get(paused ? "study.action.resume" : "study.action.pause"));
        toggleDictationButton.setText(localizationManager.get(
                dictationMode ? "study.mode.switchToFlashcard" : "study.mode.switchToDictation"));

        renderCardFront(item);
        renderCardBack(item);
        renderDictationSection(paused);
        updateRatingButtonLabels(item.ratingPreviews());
        updateRevealStateUi();
    }

    private void renderCardFront(StudyCardItem item) {
        Card card = item.card();
        CardExtraData extra = item.extraData();

        if (dictationMode) {
            frontTemplateCueLabel.setText(localizationManager.get("study.dictation.promptCue"));
            frontPrimaryLabel.setText(card.meaning());
            String langHint = localizationManager.get("targetLang." + item.deck().targetLanguage().name());
            setOptionalLabel(frontSecondaryLabel, langHint);
            return;
        }

        switch (card.cardType()) {
            case VOCABULARY -> {
                frontTemplateCueLabel.setText(localizationManager.get("study.template.vocabulary"));
                frontPrimaryLabel.setText(card.term());
                String secondary = card.partOfSpeech() != null && !card.partOfSpeech().isBlank()
                        ? card.partOfSpeech()
                        : null;
                setOptionalLabel(frontSecondaryLabel, secondary);
            }
            case PHRASE -> {
                frontTemplateCueLabel.setText(localizationManager.get("study.template.phrase"));
                frontPrimaryLabel.setText(card.term());
                String register = extra.register();
                setOptionalLabel(frontSecondaryLabel, register);
            }
            case GRAMMAR -> {
                frontTemplateCueLabel.setText(localizationManager.get("study.template.grammar"));
                String pattern = extra.grammarPattern() != null && !extra.grammarPattern().isBlank()
                        ? extra.grammarPattern()
                        : card.term();
                frontPrimaryLabel.setText(pattern);
                String levelHint = card.level() != null ? card.level().code() : item.deck().level().code();
                setOptionalLabel(frontSecondaryLabel, levelHint);
            }
        }
    }

    private void renderCardBack(StudyCardItem item) {
        answerContentBox.getChildren().clear();
        Card card = item.card();
        Deck deck = item.deck();
        CardExtraData extra = item.extraData();

        if (dictationMode) {
            Label expectedTermLabel = new Label(card.term());
            expectedTermLabel.getStyleClass().add("study-back-meaning");
            expectedTermLabel.setWrapText(true);
            answerContentBox.getChildren().add(expectedTermLabel);
        }

        // Hiển thị cách đọc / Furigana / IPA tùy theo ngôn ngữ và loại thẻ
        if (card.cardType() == CardType.GRAMMAR) {
            renderGrammarBackTemplate(card, extra);
        } else if (card.cardType() == CardType.PHRASE) {
            renderPhraseBackTemplate(card, deck, extra);
        } else {
            renderVocabularyBackTemplate(card, deck, extra);
        }

        // Nghĩa bổ sung
        if (!item.additionalMeanings().isEmpty()) {
            StringBuilder meaningsText = new StringBuilder();
            for (CardMeaning meaning : item.additionalMeanings()) {
                if (!meaningsText.isEmpty()) {
                    meaningsText.append("\n");
                }
                meaningsText.append("- ").append(meaning.meaning());
            }
            addDetailSection(localizationManager.get("cards.preview.meanings"), meaningsText.toString());
        }

        // Câu ví dụ và bản dịch
        if (!item.examples().isEmpty()) {
            VBox examplesBox = new VBox(8);
            Label heading = new Label(localizationManager.get("cards.preview.examples"));
            heading.getStyleClass().add("study-detail-heading");
            examplesBox.getChildren().add(heading);

            for (CardExample example : item.examples()) {
                VBox exRow = new VBox(2);
                exRow.getStyleClass().add("study-example-row");
                Label sentenceLabel = new Label(example.sentence());
                sentenceLabel.getStyleClass().add("study-example-sentence");
                sentenceLabel.setWrapText(true);
                exRow.getChildren().add(sentenceLabel);

                if (example.translation() != null && !example.translation().isBlank()) {
                    Label transLabel = new Label(example.translation());
                    transLabel.getStyleClass().add("muted-label");
                    transLabel.setWrapText(true);
                    exRow.getChildren().add(transLabel);
                }
                examplesBox.getChildren().add(exRow);
            }
            answerContentBox.getChildren().add(examplesBox);
        }

        // Ghi chú cá nhân
        if (card.notes() != null && !card.notes().isBlank()) {
            addDetailSection(localizationManager.get("cards.preview.notes"), card.notes());
        }
    }

    private void renderVocabularyBackTemplate(Card card, Deck deck, CardExtraData extra) {
        addReadingAndPrimaryMeaning(card, deck, extra);

        List<String> languageDetails = new ArrayList<>();
        if (deck.targetLanguage() == TargetLanguage.JA) {
            appendIfPresent(languageDetails, "cardEditor.field.ja.furigana", extra.furigana());
            appendIfPresent(languageDetails, "cardEditor.field.ja.kanjiComponents", extra.kanjiComponents());
            appendIfPresent(languageDetails, "cardEditor.field.ja.onyomi", extra.onyomi());
            appendIfPresent(languageDetails, "cardEditor.field.ja.kunyomi", extra.kunyomi());
            appendIfPresent(languageDetails, "cardEditor.field.ja.pitchAccent", extra.pitchAccent());
        } else {
            appendIfPresent(languageDetails, "cardEditor.field.en.collocations", extra.collocations());
            appendIfPresent(languageDetails, "cardEditor.field.en.register", extra.register());
        }
        if (!languageDetails.isEmpty()) {
            addDetailSection(localizationManager.get("cards.preview.extraFields"), String.join("\n", languageDetails));
        }
    }

    private void renderPhraseBackTemplate(Card card, Deck deck, CardExtraData extra) {
        addReadingAndPrimaryMeaning(card, deck, extra);

        List<String> phraseDetails = new ArrayList<>();
        appendIfPresent(phraseDetails, "cardEditor.field.en.register", extra.register());
        appendIfPresent(phraseDetails, "cardEditor.field.en.collocations", extra.collocations());
        appendIfPresent(phraseDetails, "cardEditor.field.ja.furigana", extra.furigana());
        if (!phraseDetails.isEmpty()) {
            addDetailSection(localizationManager.get("study.template.phraseDetails"), String.join("\n", phraseDetails));
        }
    }

    private void renderGrammarBackTemplate(Card card, CardExtraData extra) {
        Label meaningLabel = new Label(card.meaning());
        meaningLabel.getStyleClass().add("study-back-meaning");
        meaningLabel.setWrapText(true);
        answerContentBox.getChildren().add(meaningLabel);

        if (extra.formationRules() != null && !extra.formationRules().isBlank()) {
            addDetailSection(localizationManager.get("cardEditor.field.grammar.formation"), extra.formationRules());
        }
        if (extra.usageNotes() != null && !extra.usageNotes().isBlank()) {
            addDetailSection(localizationManager.get("cardEditor.field.grammar.usage"), extra.usageNotes());
        }
        if (extra.relatedVocabulary() != null && !extra.relatedVocabulary().isBlank()) {
            addDetailSection(
                    localizationManager.get("cardEditor.field.grammar.relatedVocabulary"), extra.relatedVocabulary());
        }
    }

    private void addReadingAndPrimaryMeaning(Card card, Deck deck, CardExtraData extra) {
        String readingText = card.reading();
        if (deck.targetLanguage() == TargetLanguage.EN && extra.ipa() != null && !extra.ipa().isBlank()) {
            readingText = (readingText == null || readingText.isBlank())
                    ? extra.ipa()
                    : readingText + "  " + extra.ipa();
        }
        if (readingText != null && !readingText.isBlank()) {
            Label readingLabel = new Label(readingText);
            readingLabel.getStyleClass().add("study-back-reading");
            readingLabel.setWrapText(true);
            answerContentBox.getChildren().add(readingLabel);
        }

        Label meaningLabel = new Label(card.meaning());
        meaningLabel.getStyleClass().add("study-back-meaning");
        meaningLabel.setWrapText(true);
        answerContentBox.getChildren().add(meaningLabel);
    }

    private void addDetailSection(String headingText, String bodyText) {
        VBox section = new VBox(4);
        section.getStyleClass().add("study-detail-block");
        Label heading = new Label(headingText);
        heading.getStyleClass().add("study-detail-heading");
        Label body = new Label(bodyText);
        body.getStyleClass().add("study-detail-body");
        body.setWrapText(true);
        section.getChildren().addAll(heading, body);
        answerContentBox.getChildren().add(section);
    }

    private void appendIfPresent(List<String> lines, String labelKey, String value) {
        if (value != null && !value.isBlank()) {
            lines.add(localizationManager.get(labelKey) + ": " + value.trim());
        }
    }

    private void updateRatingButtonLabels(Map<ReviewRating, SchedulingResult> previews) {
        setRatingButtonText(againButton, "1", "study.rating.again", previews.get(ReviewRating.AGAIN));
        setRatingButtonText(hardButton, "2", "study.rating.hard", previews.get(ReviewRating.HARD));
        setRatingButtonText(goodButton, "3", "study.rating.good", previews.get(ReviewRating.GOOD));
        setRatingButtonText(easyButton, "4", "study.rating.easy", previews.get(ReviewRating.EASY));
    }

    private void setRatingButtonText(Button button, String keyHint, String labelKey, SchedulingResult preview) {
        Duration interval = preview == null ? null : preview.scheduledInterval();
        String formattedInterval = StudyIntervalFormatter.format(interval, localizationManager);
        button.setText("[" + keyHint + "] " + localizationManager.get(labelKey) + "\n" + formattedInterval);
    }

    private void updateRevealStateUi() {
        boolean paused = currentSnapshot != null && currentSnapshot.status() == StudySessionStatus.PAUSED;
        backSectionBox.setVisible(answerRevealed);
        backSectionBox.setManaged(answerRevealed);

        revealButtonBox.setVisible(!answerRevealed);
        revealButtonBox.setManaged(!answerRevealed);
        revealAnswerButton.setDisable(paused);

        ratingButtonsBox.setVisible(answerRevealed);
        ratingButtonsBox.setManaged(answerRevealed);
        setRatingButtonsDisabled(paused || submittingReview);
    }

    private void renderCompletedSummary(StudySessionSnapshot snapshot) {
        completeTitleLabel.setText(localizationManager.get("study.complete.title"));
        completeSubtitleLabel.setText(snapshot.completedReviews() == 0
                ? localizationManager.get("study.complete.emptySubtitle")
                : localizationManager.get("study.complete.subtitle"));
        summaryReviewedValueLabel.setText(Integer.toString(snapshot.completedReviews()));
        summaryDurationValueLabel.setText(StudyIntervalFormatter.formatTimer(snapshot.activeDurationSeconds()));

        summaryBreakdownBox.getChildren().clear();
        Map<ReviewRating, Integer> breakdown = snapshot.ratingBreakdown();
        for (ReviewRating rating : ReviewRating.values()) {
            int count = breakdown.getOrDefault(rating, 0);
            String ratingName = switch (rating) {
                case AGAIN -> localizationManager.get("study.rating.again");
                case HARD -> localizationManager.get("study.rating.hard");
                case GOOD -> localizationManager.get("study.rating.good");
                case EASY -> localizationManager.get("study.rating.easy");
            };
            Label badge = new Label(ratingName + ": " + count);
            badge.getStyleClass().add("badge");
            summaryBreakdownBox.getChildren().add(badge);
        }
    }

    private void configureDeckSelector() {
        deckSelectorBox.setConverter(new StringConverter<>() {
            @Override
            public String toString(DeckOption option) {
                return formatDeckOption(option);
            }

            @Override
            public DeckOption fromString(String string) {
                return null;
            }
        });
        deckSelectorBox.setCellFactory(listView -> createDeckOptionCell());
        deckSelectorBox.setButtonCell(createDeckOptionCell());
        deckSelectorBox.valueProperty().addListener((obs, oldVal, newVal) -> {
            if (!updatingDeckDisplay) {
                updateLauncherQueueSummary();
            }
        });
    }

    private ListCell<DeckOption> createDeckOptionCell() {
        return new ListCell<>() {
            @Override
            protected void updateItem(DeckOption item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : formatDeckOption(item));
            }
        };
    }

    private String formatDeckOption(DeckOption option) {
        if (option == null) {
            return "";
        }
        String name = option.deck() == null
                ? localizationManager.get("dashboard.recent.allDecks")
                : option.deck().title() + " (" + option.deck().targetLanguage().name() + ")";
        return name + " - " + localizationManager.get("study.launcher.optionCount", option.availableCards());
    }

    private void refreshDeckSelectorDisplays() {
        updatingDeckDisplay = true;
        try {
            DeckOption current = deckSelectorBox.getValue();
            deckSelectorBox.setValue(null);
            deckSelectorBox.setValue(current);
        } finally {
            updatingDeckDisplay = false;
        }
        updateLauncherQueueSummary();
    }

    private void configureSessionTimer() {
        sessionTimer = new Timeline(new KeyFrame(javafx.util.Duration.seconds(1), event -> {
            if (currentSnapshot != null && currentSnapshot.status() == StudySessionStatus.ACTIVE) {
                displayedSeconds++;
                timerLabel.setText(StudyIntervalFormatter.formatTimer(displayedSeconds));
            }
        }));
        sessionTimer.setCycleCount(Animation.INDEFINITE);
    }

    private void startSessionTimer() {
        if (sessionTimer != null) {
            sessionTimer.play();
        }
    }

    private void stopSessionTimer() {
        if (sessionTimer != null) {
            sessionTimer.stop();
        }
    }

    private void stopTtsPlayback() {
        if (ttsManager != null) {
            backgroundTaskExecutor.submitFireAndForget(ttsManager::stop);
        }
    }

    private void showLauncherPane() {
        launcherPane.setVisible(true);
        launcherPane.setManaged(true);
        activeSessionPane.setVisible(false);
        activeSessionPane.setManaged(false);
        completePane.setVisible(false);
        completePane.setManaged(false);
    }

    private void showActiveSessionPane() {
        launcherPane.setVisible(false);
        launcherPane.setManaged(false);
        activeSessionPane.setVisible(true);
        activeSessionPane.setManaged(true);
        completePane.setVisible(false);
        completePane.setManaged(false);
    }

    private void showCompletePane() {
        launcherPane.setVisible(false);
        launcherPane.setManaged(false);
        activeSessionPane.setVisible(false);
        activeSessionPane.setManaged(false);
        completePane.setVisible(true);
        completePane.setManaged(true);
    }

    private void setRatingButtonsDisabled(boolean disabled) {
        againButton.setDisable(disabled);
        hardButton.setDisable(disabled);
        goodButton.setDisable(disabled);
        easyButton.setDisable(disabled);
    }

    private void setSessionControlsDisabled(boolean disabled) {
        pauseResumeButton.setDisable(disabled);
        exitSessionButton.setDisable(disabled);
        revealAnswerButton.setDisable(disabled);
        setRatingButtonsDisabled(disabled);
    }

    private void showAudioStatus(String message, boolean error) {
        audioStatusLabel.setText(message);
        audioStatusLabel.getStyleClass().removeAll("status-success", "status-error");
        audioStatusLabel.getStyleClass().add(error ? "status-error" : "muted-label");
        audioStatusLabel.setVisible(true);
        audioStatusLabel.setManaged(true);
    }

    private void hideAudioStatus() {
        audioStatusLabel.setVisible(false);
        audioStatusLabel.setManaged(false);
    }

    private void setOptionalLabel(Label label, String text) {
        boolean present = text != null && !text.isBlank();
        label.setText(present ? text : "");
        label.setVisible(present);
        label.setManaged(present);
    }

    private void bindLocalizedLabels() {
        localizationManager.bind(launcherTitleLabel, "study.launcher.title");
        localizationManager.bind(launcherSubtitleLabel, "study.launcher.subtitle");
        localizationManager.bind(deckPickerLabel, "study.launcher.selectDeck");
        localizationManager.bind(startSessionButton, "study.launcher.start");
        localizationManager.bind(refreshDecksButton, "common.refresh");
        localizationManager.bind(launcherDictationCheckBox, "study.mode.dictationToggle");
        localizationManager.bind(shortcutHintLabel, "study.shortcuts.summary");
        localizationManager.bind(launcherEmptyTitleLabel, "study.launcher.emptyTitle");
        localizationManager.bind(launcherEmptyDescriptionLabel, "study.launcher.emptyDescription");

        localizationManager.bind(replayAudioButton, "study.action.replayAudio");
        localizationManager.bind(exitSessionButton, "study.action.exit");
        localizationManager.bind(pausedBannerLabel, "study.paused.banner");
        localizationManager.bind(resumeFromBannerButton, "study.action.resume");
        localizationManager.bindPrompt(dictationInputField, "study.dictation.inputPrompt");
        localizationManager.bind(checkDictationButton, "study.dictation.check");
        localizationManager.bind(revealAnswerButton, "study.action.reveal");
        localizationManager.bind(activeShortcutFooterLabel, "study.shortcuts.footer");

        localizationManager.bind(summaryReviewedCaptionLabel, "study.complete.reviewed");
        localizationManager.bind(summaryDurationCaptionLabel, "study.complete.duration");
        localizationManager.bind(studyAnotherDeckButton, "study.complete.studyAnother");
        localizationManager.bind(backToDashboardButton, "study.complete.backDashboard");
    }

    private void refreshTypingOptionsOffFxThread() {
        if (appSettingsService != null) {
            TypingPreferences prefs = appSettingsService.getTypingPreferences();
            cachedTypingOptions = prefs.toEvaluationOptions();
        }
    }

    private List<String> buildExplicitAcceptedAnswers(StudyCardItem item) {
        List<String> accepted = new ArrayList<>();
        Card card = item.card();
        if (card.cardType() == CardType.GRAMMAR
                && item.extraData().grammarPattern() != null
                && !item.extraData().grammarPattern().isBlank()) {
            accepted.add(item.extraData().grammarPattern().trim());
        }
        if (card.term() != null && !card.term().isBlank() && !accepted.contains(card.term().trim())) {
            accepted.add(card.term().trim());
        }
        return accepted;
    }

    private void renderDictationSection(boolean paused) {
        dictationSectionBox.setVisible(dictationMode);
        dictationSectionBox.setManaged(dictationMode);
        dictationInputField.setDisable(paused || answerRevealed);
        checkDictationButton.setDisable(paused || answerRevealed);
        if (dictationMode && lastDictationResult != null) {
            renderDictationFeedback(lastDictationResult);
        } else {
            dictationFeedbackBox.setVisible(false);
            dictationFeedbackBox.setManaged(false);
        }
    }

    private void renderDictationFeedback(TypingEvaluationResult result) {
        dictationFeedbackBox.getStyleClass().removeAll("dictation-feedback-correct", "dictation-feedback-incorrect");
        dictationFeedbackBox.getStyleClass().add(
                result.correct() ? "dictation-feedback-correct" : "dictation-feedback-incorrect");
        dictationFeedbackTitleLabel.setText(localizationManager.get(
                result.correct() ? "study.dictation.correct" : "study.dictation.incorrect"));
        String expectedJoined = String.join(" / ", result.acceptedAnswers());
        String detail = result.correct()
                ? localizationManager.get("study.dictation.detailCorrect", result.matchedExpected())
                : localizationManager.get("study.dictation.detailIncorrect",
                        result.normalizedInput().isEmpty() ? "-" : result.normalizedInput(),
                        expectedJoined);
        dictationFeedbackDetailLabel.setText(detail);
        dictationFeedbackBox.setVisible(true);
        dictationFeedbackBox.setManaged(true);
    }

    private void resetDictationInputState() {
        lastDictationResult = null;
        if (dictationInputField != null) {
            dictationInputField.clear();
            dictationInputField.setDisable(false);
        }
        if (checkDictationButton != null) {
            checkDictationButton.setDisable(false);
        }
        if (dictationFeedbackBox != null) {
            dictationFeedbackBox.setVisible(false);
            dictationFeedbackBox.setManaged(false);
        }
    }

    /**
     * Truy xuất kết quả chấm bài Dictation gần nhất (phục vụ kiểm thử tích hợp UI).
     *
     * @return kết quả chấm bài gần nhất hoặc null
     */
    public TypingEvaluationResult getLastDictationResult() {
        return lastDictationResult;
    }

    private Window getWindow() {
        return rootPane != null && rootPane.getScene() != null ? rootPane.getScene().getWindow() : null;
    }

    private void showError(Throwable error) {
        String message = error == null || error.getMessage() == null
                ? localizationManager.get("common.error")
                : error.getMessage();
        DialogHelper.showError(getWindow(), message, localizationManager, themeManager);
    }

    /**
     * Truy xuất ảnh chụp phiên học hiện tại (phục vụ kiểm thử tích hợp UI).
     *
     * @return ảnh chụp phiên học hiện tại hoặc null nếu chưa bắt đầu
     */
    public StudySessionSnapshot getCurrentSnapshot() {
        return currentSnapshot;
    }

    /**
     * Kiểm tra mặt sau của thẻ hiện tại đã được lật hay chưa.
     *
     * @return true nếu đáp án đã được hiển thị
     */
    public boolean isAnswerRevealed() {
        return answerRevealed;
    }

    private record DeckOption(Long deckId, Deck deck, int availableCards) {
    }
}
