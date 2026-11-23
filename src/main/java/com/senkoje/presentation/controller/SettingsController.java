package com.senkoje.presentation.controller;

import com.senkoje.application.dto.TtsPreferences;
import com.senkoje.application.dto.TypingPreferences;
import com.senkoje.application.service.AppSettingsService;
import com.senkoje.domain.model.DeckSettings;
import com.senkoje.domain.model.TargetLanguage;
import com.senkoje.domain.service.DeckSettingsValidator;
import com.senkoje.domain.service.DomainValidationException;
import com.senkoje.infrastructure.tts.TtsAvailability;
import com.senkoje.infrastructure.tts.TtsManager;
import com.senkoje.infrastructure.tts.TtsVoice;
import com.senkoje.presentation.i18n.LocalizationManager;
import com.senkoje.presentation.navigation.RefreshableView;
import com.senkoje.presentation.theme.ThemeManager;
import com.senkoje.presentation.util.BackgroundTaskExecutor;
import com.senkoje.presentation.util.DialogHelper;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.Slider;
import javafx.scene.control.TextField;
import javafx.stage.Window;
import javafx.util.StringConverter;

/**
 * Bộ điều khiển màn hình Cài đặt quản lý ngôn ngữ giao diện, chế độ tối/sáng,
 * cấu hình TTS ngoại tuyến, thông số FSRS-6 mặc định, điểm vào sao lưu/khôi phục và thông tin ứng dụng.
 */
public class SettingsController implements RefreshableView {

    public static final String KEY_SRS_RETENTION = "srs.default.desired_retention";
    public static final String KEY_SRS_MAX_INTERVAL = "srs.default.maximum_interval";
    public static final String KEY_SRS_NEW_CARDS = "srs.default.new_cards_per_day";
    public static final String KEY_SRS_MAX_REVIEWS = "srs.default.maximum_reviews_per_day";
    public static final String KEY_SRS_LEARNING_STEPS = "srs.default.learning_steps";
    public static final String KEY_SRS_RELEARNING_STEPS = "srs.default.relearning_steps";
    public static final String KEY_SRS_ENABLE_FUZZING = "srs.default.enable_fuzzing";

    private static final DeckSettingsValidator SRS_VALIDATOR = new DeckSettingsValidator();

    private final AppSettingsService appSettingsService;
    private final TtsManager ttsManager;
    private final LocalizationManager localizationManager;
    private final ThemeManager themeManager;
    private final BackgroundTaskExecutor backgroundTaskExecutor;
    private final Path databasePath;

    private boolean loadingControls = false;

    @FXML
    private Label titleLabel;
    @FXML
    private Label subtitleLabel;
    @FXML
    private Label statusMessageLabel;

    @FXML
    private Label appearanceSectionLabel;
    @FXML
    private Label languageFieldLabel;
    @FXML
    private ComboBox<String> languageComboBox;
    @FXML
    private Label themeFieldLabel;
    @FXML
    private ComboBox<String> themeComboBox;

    @FXML
    private Label ttsSectionLabel;
    @FXML
    private CheckBox ttsEnabledCheckBox;
    @FXML
    private Label ttsProviderFieldLabel;
    @FXML
    private ComboBox<String> ttsProviderComboBox;
    @FXML
    private Label speechRateFieldLabel;
    @FXML
    private Slider speechRateSlider;
    @FXML
    private Label jaVoiceFieldLabel;
    @FXML
    private ComboBox<VoiceOption> jaVoiceComboBox;
    @FXML
    private Label enVoiceFieldLabel;
    @FXML
    private ComboBox<VoiceOption> enVoiceComboBox;
    @FXML
    private Label ttsAvailabilityStatusLabel;
    @FXML
    private Button testTtsButton;
    @FXML
    private Button saveTtsButton;

    @FXML
    private Label typingSectionLabel;
    @FXML
    private Label typingNoteLabel;
    @FXML
    private CheckBox typingEnCaseInsensitiveCheckBox;
    @FXML
    private CheckBox typingEnIgnorePunctuationCheckBox;
    @FXML
    private CheckBox typingStrictMatchingCheckBox;
    @FXML
    private Button saveTypingButton;

    @FXML
    private Label srsSectionLabel;
    @FXML
    private Label srsNoteLabel;
    @FXML
    private Label retentionFieldLabel;
    @FXML
    private TextField retentionField;
    @FXML
    private Label maxIntervalFieldLabel;
    @FXML
    private TextField maxIntervalField;
    @FXML
    private Label newCardsFieldLabel;
    @FXML
    private TextField newCardsField;
    @FXML
    private Label maxReviewsFieldLabel;
    @FXML
    private TextField maxReviewsField;
    @FXML
    private Label learningStepsFieldLabel;
    @FXML
    private TextField learningStepsField;
    @FXML
    private Label relearningStepsFieldLabel;
    @FXML
    private TextField relearningStepsField;
    @FXML
    private CheckBox fuzzingCheckBox;
    @FXML
    private Button saveSrsButton;

    @FXML
    private Label backupSectionLabel;
    @FXML
    private Label databasePathFieldLabel;
    @FXML
    private Label databasePathValueLabel;
    @FXML
    private Button backupButton;
    @FXML
    private Button restoreButton;
    @FXML
    private Label backupNoticeLabel;

    @FXML
    private Label aboutSectionLabel;
    @FXML
    private Label aboutNameLabel;
    @FXML
    private Label aboutVersionLabel;
    @FXML
    private Label aboutAuthorLabel;
    @FXML
    private Label aboutLicenseLabel;
    @FXML
    private Label aboutLicenseValueLabel;
    @FXML
    private Label aboutRuntimeLabel;
    @FXML
    private Label aboutRuntimeValueLabel;

    /**
     * Khởi tạo bộ điều khiển màn hình Cài đặt.
     *
     * @param appSettingsService dịch vụ cài đặt ứng dụng
     * @param localizationManager trình quản lý đa ngôn ngữ
     * @param themeManager trình quản lý chủ đề
     * @param backgroundTaskExecutor bộ thực thi tác vụ nền
     * @param databasePath đường dẫn tệp cơ sở dữ liệu SQLite đang dùng
     */
    public SettingsController(
            AppSettingsService appSettingsService,
            LocalizationManager localizationManager,
            ThemeManager themeManager,
            BackgroundTaskExecutor backgroundTaskExecutor,
            Path databasePath
    ) {
        this(appSettingsService, null, localizationManager, themeManager, backgroundTaskExecutor, databasePath);
    }

    /**
     * Khởi tạo bộ điều khiển màn hình Cài đặt kèm trình điều phối TTS ngoại tuyến.
     *
     * @param appSettingsService dịch vụ cài đặt ứng dụng
     * @param ttsManager trình điều phối TTS ngoại tuyến (có thể null khi kiểm thử độc lập)
     * @param localizationManager trình quản lý đa ngôn ngữ
     * @param themeManager trình quản lý chủ đề
     * @param backgroundTaskExecutor bộ thực thi tác vụ nền
     * @param databasePath đường dẫn tệp cơ sở dữ liệu SQLite đang dùng
     */
    public SettingsController(
            AppSettingsService appSettingsService,
            TtsManager ttsManager,
            LocalizationManager localizationManager,
            ThemeManager themeManager,
            BackgroundTaskExecutor backgroundTaskExecutor,
            Path databasePath
    ) {
        this.appSettingsService = Objects.requireNonNull(
                appSettingsService, "appSettingsService must not be null");
        this.ttsManager = ttsManager;
        this.localizationManager = Objects.requireNonNull(
                localizationManager, "localizationManager must not be null");
        this.themeManager = Objects.requireNonNull(themeManager, "themeManager must not be null");
        this.backgroundTaskExecutor = Objects.requireNonNull(
                backgroundTaskExecutor, "backgroundTaskExecutor must not be null");
        this.databasePath = Objects.requireNonNull(databasePath, "databasePath must not be null");
    }

    @FXML
    private void initialize() {
        bindLocalizedLabels();
        configureSelectors();
        databasePathValueLabel.setText(databasePath.toString());
        String javaVersion = System.getProperty("java.version", "25");
        String javafxVersion = System.getProperty("javafx.runtime.version", "25");
        aboutRuntimeValueLabel.setText("Java " + javaVersion + " / JavaFX " + javafxVersion);

        speechRateSlider.valueProperty().addListener((obs, oldVal, newVal) -> updateSpeechRateLabel());
        localizationManager.localeProperty().addListener((obs, oldVal, newVal) -> {
            updateSpeechRateLabel();
            refreshComboBoxDisplays();
        });
        updateSpeechRateLabel();
    }

    @FXML
    private void handleSaveTts() {
        double roundedRate = Math.round(speechRateSlider.getValue() * 10.0) / 10.0;
        String selectedJaVoice = jaVoiceComboBox.getValue() == null ? "" : jaVoiceComboBox.getValue().id();
        String selectedEnVoice = enVoiceComboBox.getValue() == null ? "" : enVoiceComboBox.getValue().id();
        TtsPreferences prefs = new TtsPreferences(
                ttsEnabledCheckBox.isSelected(),
                ttsProviderComboBox.getValue(),
                roundedRate,
                selectedJaVoice,
                selectedEnVoice
        );
        backgroundTaskExecutor.runAsyncVoid(
                () -> appSettingsService.saveTtsPreferences(prefs),
                () -> {
                    showStatus(localizationManager.get("settings.saved"), false);
                    refreshTtsAvailabilityStatus();
                },
                error -> showStatus(extractErrorMessage(error), true)
        );
    }

    @FXML
    private void handleTestTts() {
        if (ttsManager == null) {
            showStatus(localizationManager.get("tts.status.disabled"), true);
            return;
        }
        testTtsButton.setDisable(true);
        backgroundTaskExecutor.runAsync(
                () -> ttsManager.speak("SenkoJE offline speech test.", TargetLanguage.EN),
                result -> {
                    testTtsButton.setDisable(false);
                    showStatus(localizationManager.get(result.statusCode()), !result.succeeded());
                },
                error -> {
                    testTtsButton.setDisable(false);
                    showStatus(extractErrorMessage(error), true);
                }
        );
    }

    @FXML
    private void handleSaveTyping() {
        TypingPreferences prefs = new TypingPreferences(
                typingEnCaseInsensitiveCheckBox.isSelected(),
                typingEnIgnorePunctuationCheckBox.isSelected(),
                typingStrictMatchingCheckBox.isSelected()
        );
        backgroundTaskExecutor.runAsyncVoid(
                () -> appSettingsService.saveTypingPreferences(prefs),
                () -> showStatus(localizationManager.get("settings.saved"), false),
                error -> showStatus(extractErrorMessage(error), true)
        );
    }

    @FXML
    private void handleSaveSrsDefaults() {
        try {
            DeckSettings candidate = new DeckSettings(
                    null,
                    Double.parseDouble(retentionField.getText().trim()),
                    Integer.parseInt(maxIntervalField.getText().trim()),
                    Integer.parseInt(newCardsField.getText().trim()),
                    Integer.parseInt(maxReviewsField.getText().trim()),
                    learningStepsField.getText().trim(),
                    relearningStepsField.getText().trim(),
                    fuzzingCheckBox.isSelected()
            );
            SRS_VALIDATOR.validate(candidate);

            backgroundTaskExecutor.runAsyncVoid(() -> {
                appSettingsService.setRawSetting(KEY_SRS_RETENTION, Double.toString(candidate.desiredRetention()));
                appSettingsService.setRawSetting(KEY_SRS_MAX_INTERVAL, Integer.toString(candidate.maximumInterval()));
                appSettingsService.setRawSetting(KEY_SRS_NEW_CARDS, Integer.toString(candidate.newCardsPerDay()));
                appSettingsService.setRawSetting(KEY_SRS_MAX_REVIEWS,
                        Integer.toString(candidate.maximumReviewsPerDay()));
                appSettingsService.setRawSetting(KEY_SRS_LEARNING_STEPS, candidate.learningSteps());
                appSettingsService.setRawSetting(KEY_SRS_RELEARNING_STEPS, candidate.relearningSteps());
                appSettingsService.setRawSetting(KEY_SRS_ENABLE_FUZZING, Boolean.toString(candidate.enableFuzzing()));
            }, () -> showStatus(localizationManager.get("settings.saved"), false),
                    error -> showStatus(extractErrorMessage(error), true));
        } catch (NumberFormatException exception) {
            showStatus(localizationManager.get("deckSettings.validation.invalidNumber"), true);
        } catch (DomainValidationException exception) {
            showStatus(String.join("; ", exception.getErrors()), true);
        }
    }

    @FXML
    private void handleBackupEntryPoint() {
        DialogHelper.showInfo(
                getWindow(),
                localizationManager.get("settings.backup.action.backup"),
                localizationManager.get("settings.backup.phaseNotice"),
                themeManager
        );
    }

    @FXML
    private void handleRestoreEntryPoint() {
        DialogHelper.showInfo(
                getWindow(),
                localizationManager.get("settings.backup.action.restore"),
                localizationManager.get("settings.backup.phaseNotice"),
                themeManager
        );
    }

    @Override
    public void refresh() {
        backgroundTaskExecutor.runAsync(() -> new LoadedSettings(
                appSettingsService.getInterfaceLanguage(),
                appSettingsService.getTheme(),
                appSettingsService.getTtsPreferences(),
                appSettingsService.getTypingPreferences(),
                appSettingsService.getRawSetting(KEY_SRS_RETENTION)
                        .orElse(Double.toString(DeckSettings.DEFAULT_DESIRED_RETENTION)),
                appSettingsService.getRawSetting(KEY_SRS_MAX_INTERVAL)
                        .orElse(Integer.toString(DeckSettings.DEFAULT_MAXIMUM_INTERVAL)),
                appSettingsService.getRawSetting(KEY_SRS_NEW_CARDS)
                        .orElse(Integer.toString(DeckSettings.DEFAULT_NEW_CARDS_PER_DAY)),
                appSettingsService.getRawSetting(KEY_SRS_MAX_REVIEWS)
                        .orElse(Integer.toString(DeckSettings.DEFAULT_MAXIMUM_REVIEWS_PER_DAY)),
                appSettingsService.getRawSetting(KEY_SRS_LEARNING_STEPS)
                        .orElse(DeckSettings.DEFAULT_LEARNING_STEPS),
                appSettingsService.getRawSetting(KEY_SRS_RELEARNING_STEPS)
                        .orElse(DeckSettings.DEFAULT_RELEARNING_STEPS),
                Boolean.parseBoolean(appSettingsService.getRawSetting(KEY_SRS_ENABLE_FUZZING)
                        .orElse(Boolean.toString(DeckSettings.DEFAULT_ENABLE_FUZZING)))
        ), this::applyLoadedSettings, error -> showStatus(extractErrorMessage(error), true));
    }

    private void applyLoadedSettings(LoadedSettings loaded) {
        loadingControls = true;
        try {
            languageComboBox.setValue(loaded.language());
            themeComboBox.setValue(loaded.theme());

            ttsEnabledCheckBox.setSelected(loaded.tts().enabled());
            ttsProviderComboBox.setValue(loaded.tts().provider());
            speechRateSlider.setValue(loaded.tts().speechRate());
            applyVoiceChoices(jaVoiceComboBox, List.of(), loaded.tts().japaneseVoice());
            applyVoiceChoices(enVoiceComboBox, List.of(), loaded.tts().englishVoice());

            typingEnCaseInsensitiveCheckBox.setSelected(loaded.typing().caseInsensitiveEnglish());
            typingEnIgnorePunctuationCheckBox.setSelected(loaded.typing().ignoreEnglishPunctuation());
            typingStrictMatchingCheckBox.setSelected(loaded.typing().strictCharacterMatching());

            retentionField.setText(loaded.srsRetention());
            maxIntervalField.setText(loaded.srsMaxInterval());
            newCardsField.setText(loaded.srsNewCards());
            maxReviewsField.setText(loaded.srsMaxReviews());
            learningStepsField.setText(loaded.srsLearningSteps());
            relearningStepsField.setText(loaded.srsRelearningSteps());
            fuzzingCheckBox.setSelected(loaded.srsEnableFuzzing());
            updateSpeechRateLabel();
            refreshTtsAvailabilityStatus();
            loadVoicesForProviderAsync(
                    loaded.tts().provider(),
                    loaded.tts().japaneseVoice(),
                    loaded.tts().englishVoice()
            );
        } finally {
            loadingControls = false;
        }
    }

    private void bindLocalizedLabels() {
        localizationManager.bind(titleLabel, "settings.title");
        localizationManager.bind(subtitleLabel, "settings.subtitle");

        localizationManager.bind(appearanceSectionLabel, "settings.section.appearance");
        localizationManager.bind(languageFieldLabel, "settings.field.language");
        localizationManager.bind(themeFieldLabel, "settings.field.theme");

        localizationManager.bind(ttsSectionLabel, "settings.section.tts");
        localizationManager.bind(ttsEnabledCheckBox, "settings.field.ttsEnabled");
        localizationManager.bind(ttsProviderFieldLabel, "settings.field.ttsProvider");
        localizationManager.bind(jaVoiceFieldLabel, "settings.field.jaVoice");
        localizationManager.bind(enVoiceFieldLabel, "settings.field.enVoice");
        localizationManager.bind(testTtsButton, "settings.tts.test");
        localizationManager.bind(saveTtsButton, "common.save");

        localizationManager.bind(typingSectionLabel, "settings.section.typing");
        localizationManager.bind(typingNoteLabel, "settings.typing.note");
        localizationManager.bind(typingEnCaseInsensitiveCheckBox, "settings.typing.enCaseInsensitive");
        localizationManager.bind(typingEnIgnorePunctuationCheckBox, "settings.typing.enIgnorePunctuation");
        localizationManager.bind(typingStrictMatchingCheckBox, "settings.typing.strictMatching");
        localizationManager.bind(saveTypingButton, "common.save");

        localizationManager.bind(srsSectionLabel, "settings.section.srs");
        localizationManager.bind(srsNoteLabel, "settings.srs.note");
        localizationManager.bind(retentionFieldLabel, "deckSettings.desiredRetention");
        localizationManager.bind(maxIntervalFieldLabel, "deckSettings.maximumInterval");
        localizationManager.bind(newCardsFieldLabel, "deckSettings.newCardsPerDay");
        localizationManager.bind(maxReviewsFieldLabel, "deckSettings.maximumReviewsPerDay");
        localizationManager.bind(learningStepsFieldLabel, "deckSettings.learningSteps");
        localizationManager.bind(relearningStepsFieldLabel, "deckSettings.relearningSteps");
        localizationManager.bind(fuzzingCheckBox, "deckSettings.enableFuzzing");
        localizationManager.bind(saveSrsButton, "common.save");

        localizationManager.bind(backupSectionLabel, "settings.section.backup");
        localizationManager.bind(databasePathFieldLabel, "settings.backup.databasePath");
        localizationManager.bind(backupButton, "settings.backup.action.backup");
        localizationManager.bind(restoreButton, "settings.backup.action.restore");
        localizationManager.bind(backupNoticeLabel, "settings.backup.phaseNotice");

        localizationManager.bind(aboutSectionLabel, "settings.section.about");
        localizationManager.bind(aboutNameLabel, "settings.about.name");
        localizationManager.bind(aboutVersionLabel, "settings.about.version");
        localizationManager.bind(aboutAuthorLabel, "settings.about.author");
        localizationManager.bind(aboutLicenseLabel, "settings.about.license");
        localizationManager.bind(aboutLicenseValueLabel, "settings.about.licenseValue");
        localizationManager.bind(aboutRuntimeLabel, "settings.about.runtime");
    }

    private void configureSelectors() {
        languageComboBox.getItems().setAll("vi", "en");
        languageComboBox.setConverter(new StringConverter<>() {
            @Override
            public String toString(String code) {
                return code == null ? "" : localizationManager.get("lang." + code);
            }

            @Override
            public String fromString(String string) {
                return null;
            }
        });
        languageComboBox.valueProperty().addListener((obs, oldVal, newVal) -> {
            if (!loadingControls && newVal != null) {
                localizationManager.setLanguage(newVal);
                showStatus(localizationManager.get("settings.saved"), false);
            }
        });

        themeComboBox.getItems().setAll(AppSettingsService.THEME_DARK, AppSettingsService.THEME_LIGHT);
        themeComboBox.setConverter(new StringConverter<>() {
            @Override
            public String toString(String code) {
                return code == null ? "" : localizationManager.get("theme." + code);
            }

            @Override
            public String fromString(String string) {
                return null;
            }
        });
        themeComboBox.valueProperty().addListener((obs, oldVal, newVal) -> {
            if (!loadingControls && newVal != null) {
                themeManager.setTheme(newVal);
                showStatus(localizationManager.get("settings.saved"), false);
            }
        });

        ttsProviderComboBox.getItems().setAll("system", "bundled", "unsupported");
        ttsProviderComboBox.setConverter(new StringConverter<>() {
            @Override
            public String toString(String provider) {
                return provider == null ? "" : localizationManager.get("settings.ttsProvider." + provider);
            }

            @Override
            public String fromString(String string) {
                return null;
            }
        });
        ttsProviderComboBox.valueProperty().addListener((obs, oldVal, newVal) -> {
            if (!loadingControls && newVal != null) {
                String currentJa = jaVoiceComboBox.getValue() == null ? "" : jaVoiceComboBox.getValue().id();
                String currentEn = enVoiceComboBox.getValue() == null ? "" : enVoiceComboBox.getValue().id();
                loadVoicesForProviderAsync(newVal, currentJa, currentEn);
            }
        });

        configureVoiceComboBox(jaVoiceComboBox);
        configureVoiceComboBox(enVoiceComboBox);
    }

    private void configureVoiceComboBox(ComboBox<VoiceOption> comboBox) {
        comboBox.setConverter(new StringConverter<>() {
            @Override
            public String toString(VoiceOption option) {
                return formatVoiceOption(option);
            }

            @Override
            public VoiceOption fromString(String string) {
                return null;
            }
        });
        comboBox.setCellFactory(listView -> createVoiceOptionCell());
        comboBox.setButtonCell(createVoiceOptionCell());
    }

    private ListCell<VoiceOption> createVoiceOptionCell() {
        return new ListCell<>() {
            @Override
            protected void updateItem(VoiceOption item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : formatVoiceOption(item));
            }
        };
    }

    private String formatVoiceOption(VoiceOption option) {
        if (option == null || option.isSystemDefault()) {
            return localizationManager.get("settings.tts.systemDefault");
        }
        return option.displayName();
    }

    private void loadVoicesForProviderAsync(String providerId, String savedJaVoice, String savedEnVoice) {
        if (ttsManager == null) {
            applyVoiceChoices(jaVoiceComboBox, List.of(), savedJaVoice);
            applyVoiceChoices(enVoiceComboBox, List.of(), savedEnVoice);
            return;
        }
        backgroundTaskExecutor.runAsync(
                () -> new DetectedVoices(
                        ttsManager.listVoicesForProvider(providerId, TargetLanguage.JA),
                        ttsManager.listVoicesForProvider(providerId, TargetLanguage.EN)
                ),
                detected -> {
                    applyVoiceChoices(jaVoiceComboBox, detected.jaVoices(), savedJaVoice);
                    applyVoiceChoices(enVoiceComboBox, detected.enVoices(), savedEnVoice);
                },
                error -> {
                    applyVoiceChoices(jaVoiceComboBox, List.of(), savedJaVoice);
                    applyVoiceChoices(enVoiceComboBox, List.of(), savedEnVoice);
                }
        );
    }

    private void applyVoiceChoices(
            ComboBox<VoiceOption> comboBox,
            List<TtsVoice> detectedVoices,
            String savedVoiceId
    ) {
        String normalizedSaved = savedVoiceId == null ? "" : savedVoiceId.trim();
        List<VoiceOption> options = new ArrayList<>();
        // Mục đầu tiên luôn là mặc định hệ thống (lưu thành chuỗi rỗng trong app_settings)
        VoiceOption defaultOption = new VoiceOption("", "");
        options.add(defaultOption);

        VoiceOption selected = defaultOption;
        for (TtsVoice voice : detectedVoices) {
            VoiceOption option = new VoiceOption(voice.id(), voice.displayName());
            options.add(option);
            if (!normalizedSaved.isEmpty() && normalizedSaved.equals(voice.id())) {
                selected = option;
            }
        }
        // Giữ nguyên giọng đọc đã lưu trước đó ngay cả khi hiện chưa phát hiện trên máy
        if (!normalizedSaved.isEmpty() && selected == defaultOption) {
            VoiceOption preserved = new VoiceOption(normalizedSaved, normalizedSaved);
            options.add(preserved);
            selected = preserved;
        }

        loadingControls = true;
        try {
            comboBox.getItems().setAll(options);
            comboBox.setValue(selected);
        } finally {
            loadingControls = false;
        }
    }

    private void refreshComboBoxDisplays() {
        loadingControls = true;
        try {
            String lang = languageComboBox.getValue();
            languageComboBox.setValue(null);
            languageComboBox.setValue(lang);

            String theme = themeComboBox.getValue();
            themeComboBox.setValue(null);
            themeComboBox.setValue(theme);

            String provider = ttsProviderComboBox.getValue();
            ttsProviderComboBox.setValue(null);
            ttsProviderComboBox.setValue(provider);

            VoiceOption jaVoice = jaVoiceComboBox.getValue();
            jaVoiceComboBox.setValue(null);
            jaVoiceComboBox.setValue(jaVoice);

            VoiceOption enVoice = enVoiceComboBox.getValue();
            enVoiceComboBox.setValue(null);
            enVoiceComboBox.setValue(enVoice);
        } finally {
            loadingControls = false;
        }
    }

    private void updateSpeechRateLabel() {
        String formattedRate = String.format(Locale.ROOT, "%.1f", speechRateSlider.getValue());
        speechRateFieldLabel.setText(localizationManager.get("settings.field.speechRate", formattedRate));
    }

    private void refreshTtsAvailabilityStatus() {
        if (ttsAvailabilityStatusLabel == null) {
            return;
        }
        if (ttsManager == null) {
            ttsAvailabilityStatusLabel.setText(localizationManager.get("tts.status.disabled"));
            return;
        }
        backgroundTaskExecutor.runAsync(
                ttsManager::checkActiveAvailability,
                this::applyTtsAvailability,
                error -> ttsAvailabilityStatusLabel.setText(localizationManager.get("tts.status.failed"))
        );
    }

    private void applyTtsAvailability(TtsAvailability availability) {
        if (ttsAvailabilityStatusLabel == null || availability == null) {
            return;
        }
        ttsAvailabilityStatusLabel.setText(localizationManager.get(availability.reasonCode()));
        ttsAvailabilityStatusLabel.getStyleClass().removeAll("status-success", "status-error", "muted-label");
        ttsAvailabilityStatusLabel.getStyleClass().add(availability.available() ? "status-success" : "status-error");
    }

    private void showStatus(String message, boolean error) {
        statusMessageLabel.setText(message);
        statusMessageLabel.getStyleClass().removeAll("status-success", "status-error");
        statusMessageLabel.getStyleClass().add(error ? "status-error" : "status-success");
        statusMessageLabel.setVisible(true);
        statusMessageLabel.setManaged(true);
    }

    private String extractErrorMessage(Throwable error) {
        return error == null || error.getMessage() == null
                ? localizationManager.get("common.error")
                : error.getMessage();
    }

    private Window getWindow() {
        return titleLabel != null && titleLabel.getScene() != null ? titleLabel.getScene().getWindow() : null;
    }

    private record DetectedVoices(List<TtsVoice> jaVoices, List<TtsVoice> enVoices) {
    }

    /**
     * Tùy chọn giọng đọc hiển thị trong ComboBox cài đặt TTS.
     *
     * @param id mã giọng đọc lưu trong app_settings (rỗng nghĩa là mặc định hệ thống)
     * @param displayName tên hiển thị trên giao diện
     */
    public record VoiceOption(String id, String displayName) {
        public boolean isSystemDefault() {
            return id == null || id.isEmpty();
        }
    }

    private record LoadedSettings(
            String language,
            String theme,
            TtsPreferences tts,
            TypingPreferences typing,
            String srsRetention,
            String srsMaxInterval,
            String srsNewCards,
            String srsMaxReviews,
            String srsLearningSteps,
            String srsRelearningSteps,
            boolean srsEnableFuzzing
    ) {
    }
}
