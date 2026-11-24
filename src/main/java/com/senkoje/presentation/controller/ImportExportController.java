package com.senkoje.presentation.controller;

import com.senkoje.application.service.CardService;
import com.senkoje.application.service.DeckService;
import com.senkoje.domain.model.Card;
import com.senkoje.domain.model.CardExample;
import com.senkoje.domain.model.CardMeaning;
import com.senkoje.domain.model.Deck;
import com.senkoje.domain.model.DeckSettings;
import com.senkoje.domain.repository.CardRepository;
import com.senkoje.domain.repository.DeckRepository;
import com.senkoje.infrastructure.backup.BackupMetadata;
import com.senkoje.infrastructure.backup.RestoreResult;
import com.senkoje.infrastructure.backup.SqliteBackupService;
import com.senkoje.infrastructure.exporter.DeckJsonExporter;
import com.senkoje.infrastructure.exporter.ExportCardBundle;
import com.senkoje.infrastructure.exporter.VocabularyCsvExporter;
import com.senkoje.infrastructure.importer.AnkiApkgImporter;
import com.senkoje.infrastructure.importer.AtomicDeckImporter;
import com.senkoje.infrastructure.importer.DeckJsonImporter;
import com.senkoje.infrastructure.importer.DuplicateStrategy;
import com.senkoje.infrastructure.importer.ImportItemStatus;
import com.senkoje.infrastructure.importer.ImportPreviewItem;
import com.senkoje.infrastructure.importer.ImportPreviewReport;
import com.senkoje.infrastructure.importer.ImportResult;
import com.senkoje.infrastructure.importer.VocabularyCsvImporter;
import com.senkoje.presentation.i18n.LocalizationManager;
import com.senkoje.presentation.navigation.RefreshableView;
import com.senkoje.presentation.theme.ThemeManager;
import com.senkoje.presentation.util.BackgroundTaskExecutor;
import com.senkoje.presentation.util.DialogHelper;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.text.MessageFormat;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.stage.FileChooser;
import javafx.stage.Window;
import javafx.util.StringConverter;

/**
 * Bộ điều khiển cho màn hình Nhập và Xuất dữ liệu của SenkoJE.
 * Hỗ trợ nhập tệp JSON/CSV/.apkg, xem trước dữ liệu, xuất bộ thẻ và sao lưu/phục hồi SQLite an toàn.
 */
public class ImportExportController implements RefreshableView {

    private final DeckService deckService;
    private final CardService cardService;
    private final DeckRepository deckRepository;
    private final CardRepository cardRepository;
    private final DeckJsonExporter deckJsonExporter;
    private final VocabularyCsvExporter vocabularyCsvExporter;
    private final DeckJsonImporter deckJsonImporter;
    private final VocabularyCsvImporter vocabularyCsvImporter;
    private final AtomicDeckImporter atomicDeckImporter;
    private final AnkiApkgImporter ankiApkgImporter;
    private final SqliteBackupService sqliteBackupService;
    private final LocalizationManager localizationManager;
    private final ThemeManager themeManager;
    private final BackgroundTaskExecutor backgroundTaskExecutor;

    private Path selectedSourcePath;
    private ImportPreviewReport activePreviewReport;

    @FXML
    private Label titleLabel;
    @FXML
    private Label subtitleLabel;

    // Các điều khiển khu vực nhập dữ liệu
    @FXML
    private Label importSectionTitleLabel;
    @FXML
    private Label importSectionDescLabel;
    @FXML
    private Label fileFieldLabel;
    @FXML
    private TextField filePathField;
    @FXML
    private Button chooseFileButton;
    @FXML
    private Label targetDeckFieldLabel;
    @FXML
    private ComboBox<DeckChoice> targetDeckComboBox;
    @FXML
    private Label duplicateStrategyFieldLabel;
    @FXML
    private ComboBox<DuplicateStrategyChoice> duplicateStrategyComboBox;
    @FXML
    private Button previewButton;
    @FXML
    private Button confirmImportButton;
    @FXML
    private Label importProgressLabel;
    @FXML
    private Label previewSummaryLabel;

    @FXML
    private TableView<ImportPreviewItem> previewTable;
    @FXML
    private TableColumn<ImportPreviewItem, String> colIndex;
    @FXML
    private TableColumn<ImportPreviewItem, ImportItemStatus> colStatus;
    @FXML
    private TableColumn<ImportPreviewItem, String> colTerm;
    @FXML
    private TableColumn<ImportPreviewItem, String> colReading;
    @FXML
    private TableColumn<ImportPreviewItem, String> colMeaning;
    @FXML
    private TableColumn<ImportPreviewItem, String> colType;
    @FXML
    private TableColumn<ImportPreviewItem, String> colLevel;
    @FXML
    private TableColumn<ImportPreviewItem, String> colMessage;

    // Các điều khiển khu vực xuất dữ liệu
    @FXML
    private Label exportSectionTitleLabel;
    @FXML
    private Label exportSectionDescLabel;
    @FXML
    private Label exportDeckFieldLabel;
    @FXML
    private ComboBox<DeckChoice> exportDeckComboBox;
    @FXML
    private Label exportFormatFieldLabel;
    @FXML
    private ComboBox<ExportFormatChoice> exportFormatComboBox;
    @FXML
    private Button exportButton;
    @FXML
    private Label exportProgressLabel;

    // Các điều khiển khu vực sao lưu và phục hồi
    @FXML
    private Label backupSectionTitleLabel;
    @FXML
    private Label backupSectionDescLabel;
    @FXML
    private Button createBackupButton;
    @FXML
    private Button restoreBackupButton;
    @FXML
    private Label backupProgressLabel;

    /**
     * Khởi tạo bộ điều khiển với đầy đủ các dịch vụ và thành phần hạ tầng cần thiết.
     */
    public ImportExportController(
            DeckService deckService,
            CardService cardService,
            DeckRepository deckRepository,
            CardRepository cardRepository,
            DeckJsonExporter deckJsonExporter,
            VocabularyCsvExporter vocabularyCsvExporter,
            DeckJsonImporter deckJsonImporter,
            VocabularyCsvImporter vocabularyCsvImporter,
            AtomicDeckImporter atomicDeckImporter,
            AnkiApkgImporter ankiApkgImporter,
            SqliteBackupService sqliteBackupService,
            LocalizationManager localizationManager,
            ThemeManager themeManager,
            BackgroundTaskExecutor backgroundTaskExecutor
    ) {
        this.deckService = Objects.requireNonNull(deckService, "deckService must not be null");
        this.cardService = Objects.requireNonNull(cardService, "cardService must not be null");
        this.deckRepository = Objects.requireNonNull(deckRepository, "deckRepository must not be null");
        this.cardRepository = Objects.requireNonNull(cardRepository, "cardRepository must not be null");
        this.deckJsonExporter = Objects.requireNonNull(deckJsonExporter, "deckJsonExporter must not be null");
        this.vocabularyCsvExporter = Objects.requireNonNull(vocabularyCsvExporter, "vocabularyCsvExporter must not be null");
        this.deckJsonImporter = Objects.requireNonNull(deckJsonImporter, "deckJsonImporter must not be null");
        this.vocabularyCsvImporter = Objects.requireNonNull(vocabularyCsvImporter, "vocabularyCsvImporter must not be null");
        this.atomicDeckImporter = Objects.requireNonNull(atomicDeckImporter, "atomicDeckImporter must not be null");
        this.ankiApkgImporter = Objects.requireNonNull(ankiApkgImporter, "ankiApkgImporter must not be null");
        this.sqliteBackupService = Objects.requireNonNull(sqliteBackupService, "sqliteBackupService must not be null");
        this.localizationManager = Objects.requireNonNull(localizationManager, "localizationManager must not be null");
        this.themeManager = Objects.requireNonNull(themeManager, "themeManager must not be null");
        this.backgroundTaskExecutor = Objects.requireNonNull(backgroundTaskExecutor, "backgroundTaskExecutor must not be null");
    }

    @FXML
    private void initialize() {
        bindLocalization();
        configureTableColumns();
        configureDuplicateStrategies();
        configureExportFormats();
        refresh();
    }

    @Override
    public void refresh() {
        populateDeckChoices();
    }

    private void bindLocalization() {
        localizationManager.bind(titleLabel, "importExport.title");
        localizationManager.bind(subtitleLabel, "importExport.subtitle");

        localizationManager.bind(importSectionTitleLabel, "importExport.importSection");
        localizationManager.bind(importSectionDescLabel, "importExport.importSectionDesc");
        localizationManager.bind(fileFieldLabel, "importExport.fileLabel");
        localizationManager.bind(chooseFileButton, "importExport.chooseFile");
        localizationManager.bind(targetDeckFieldLabel, "importExport.targetDeckLabel");
        localizationManager.bind(duplicateStrategyFieldLabel, "importExport.duplicateStrategyLabel");
        localizationManager.bind(previewButton, "importExport.previewButton");
        localizationManager.bind(confirmImportButton, "importExport.confirmImportButton");

        localizationManager.bindColumn(colIndex, "importExport.col.index");
        localizationManager.bindColumn(colStatus, "importExport.col.status");
        localizationManager.bindColumn(colTerm, "importExport.col.term");
        localizationManager.bindColumn(colReading, "importExport.col.reading");
        localizationManager.bindColumn(colMeaning, "importExport.col.meaning");
        localizationManager.bindColumn(colType, "importExport.col.cardType");
        localizationManager.bindColumn(colLevel, "importExport.col.level");
        localizationManager.bindColumn(colMessage, "importExport.col.message");

        localizationManager.bind(exportSectionTitleLabel, "importExport.exportSection");
        localizationManager.bind(exportSectionDescLabel, "importExport.exportSectionDesc");
        localizationManager.bind(exportDeckFieldLabel, "importExport.exportDeckLabel");
        localizationManager.bind(exportFormatFieldLabel, "importExport.exportFormatLabel");
        localizationManager.bind(exportButton, "importExport.exportButton");

        localizationManager.bind(backupSectionTitleLabel, "importExport.backupSection");
        localizationManager.bind(backupSectionDescLabel, "importExport.backupSectionDesc");
        localizationManager.bind(createBackupButton, "importExport.createBackupButton");
        localizationManager.bind(restoreBackupButton, "importExport.restoreBackupButton");
    }

    private void configureTableColumns() {
        colIndex.setCellValueFactory(cell -> new SimpleStringProperty(String.valueOf(cell.getValue().index())));
        colStatus.setCellValueFactory(cell -> cell.getValue().status() != null
                ? new javafx.beans.property.SimpleObjectProperty<>(cell.getValue().status())
                : null);
        colStatus.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(ImportItemStatus item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                    setStyle(null);
                } else {
                    String labelText = localizationManager.get("importExport.status." + item.name());
                    Label badge = new Label(labelText);
                    badge.getStyleClass().add(switch (item) {
                        case VALID -> "status-badge-valid";
                        case DUPLICATE -> "status-badge-duplicate";
                        case INVALID -> "status-badge-invalid";
                    });
                    setGraphic(badge);
                    setText(null);
                }
            }
        });

        colTerm.setCellValueFactory(cell -> new SimpleStringProperty(
                cell.getValue().card() != null ? cell.getValue().card().term() : ""));
        colReading.setCellValueFactory(cell -> new SimpleStringProperty(
                cell.getValue().card() != null && cell.getValue().card().reading() != null
                        ? cell.getValue().card().reading() : ""));
        colMeaning.setCellValueFactory(cell -> new SimpleStringProperty(
                cell.getValue().card() != null ? cell.getValue().card().meaning() : ""));
        colType.setCellValueFactory(cell -> new SimpleStringProperty(
                cell.getValue().card() != null && cell.getValue().card().cardType() != null
                        ? cell.getValue().card().cardType().name() : ""));
        colLevel.setCellValueFactory(cell -> new SimpleStringProperty(
                cell.getValue().card() != null && cell.getValue().card().level() != null
                        ? cell.getValue().card().level().code() : ""));
        colMessage.setCellValueFactory(cell -> new SimpleStringProperty(
                cell.getValue().message() != null ? cell.getValue().message() : ""));
    }

    private void configureDuplicateStrategies() {
        duplicateStrategyComboBox.setItems(FXCollections.observableArrayList(
                new DuplicateStrategyChoice(DuplicateStrategy.SKIP, "importExport.duplicate.skip"),
                new DuplicateStrategyChoice(DuplicateStrategy.OVERWRITE, "importExport.duplicate.overwrite"),
                new DuplicateStrategyChoice(DuplicateStrategy.IMPORT_AS_DUPLICATE, "importExport.duplicate.importAsDuplicate")
        ));
        duplicateStrategyComboBox.setConverter(new StringConverter<>() {
            @Override
            public String toString(DuplicateStrategyChoice object) {
                return object != null ? localizationManager.get(object.labelKey()) : "";
            }

            @Override
            public DuplicateStrategyChoice fromString(String string) {
                return null;
            }
        });
        duplicateStrategyComboBox.getSelectionModel().selectFirst();
    }

    private void configureExportFormats() {
        exportFormatComboBox.setItems(FXCollections.observableArrayList(
                new ExportFormatChoice(ExportFormat.JSON, "importExport.format.json"),
                new ExportFormatChoice(ExportFormat.CSV, "importExport.format.csv")
        ));
        exportFormatComboBox.setConverter(new StringConverter<>() {
            @Override
            public String toString(ExportFormatChoice object) {
                return object != null ? localizationManager.get(object.labelKey()) : "";
            }

            @Override
            public ExportFormatChoice fromString(String string) {
                return null;
            }
        });
        exportFormatComboBox.getSelectionModel().selectFirst();
    }

    private void populateDeckChoices() {
        List<Deck> decks = deckRepository.findAll();
        List<DeckChoice> choices = new ArrayList<>();
        choices.add(DeckChoice.newDeckOption(localizationManager.get("importExport.newDeckOption")));
        for (Deck deck : decks) {
            choices.add(DeckChoice.existingDeck(deck));
        }

        targetDeckComboBox.setItems(FXCollections.observableArrayList(choices));
        targetDeckComboBox.setConverter(new StringConverter<>() {
            @Override
            public String toString(DeckChoice object) {
                return object != null ? object.displayText() : "";
            }

            @Override
            public DeckChoice fromString(String string) {
                return null;
            }
        });
        targetDeckComboBox.getSelectionModel().selectFirst();

        List<DeckChoice> exportChoices = new ArrayList<>();
        for (Deck deck : decks) {
            exportChoices.add(DeckChoice.existingDeck(deck));
        }
        exportDeckComboBox.setItems(FXCollections.observableArrayList(exportChoices));
        exportDeckComboBox.setConverter(new StringConverter<>() {
            @Override
            public String toString(DeckChoice object) {
                return object != null ? object.displayText() : "";
            }

            @Override
            public DeckChoice fromString(String string) {
                return null;
            }
        });
        if (!exportChoices.isEmpty()) {
            exportDeckComboBox.getSelectionModel().selectFirst();
        }
    }

    @FXML
    public void handleChooseFile() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle(localizationManager.get("importExport.chooseFile"));
        chooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("Supported Files (*.json, *.csv, *.apkg)", "*.json", "*.csv", "*.apkg"),
                new FileChooser.ExtensionFilter("SenkoJE JSON (*.json)", "*.json"),
                new FileChooser.ExtensionFilter("Vocabulary CSV (*.csv)", "*.csv"),
                new FileChooser.ExtensionFilter("Anki Deck Package (*.apkg)", "*.apkg")
        );

        Window window = chooseFileButton.getScene() != null ? chooseFileButton.getScene().getWindow() : null;
        File selected = chooser.showOpenDialog(window);
        if (selected != null) {
            selectedSourcePath = selected.toPath();
            filePathField.setText(selectedSourcePath.toAbsolutePath().toString());
            previewTable.getItems().clear();
            confirmImportButton.setDisable(true);
            previewSummaryLabel.setVisible(false);
            previewSummaryLabel.setManaged(false);
        }
    }

    @FXML
    public void handlePreview() {
        if (selectedSourcePath == null) {
            DialogHelper.showError(getWindow(), localizationManager.get("importExport.noFileSelected"),
                    localizationManager, themeManager);
            return;
        }

        String fileName = selectedSourcePath.getFileName().toString().toLowerCase(Locale.ROOT);
        Set<String> existingTerms = extractExistingTermsForTarget();

        importProgressLabel.setText(localizationManager.get("importExport.status.processing"));
        previewButton.setDisable(true);

        backgroundTaskExecutor.runAsync(
                () -> {
                    if (fileName.endsWith(".json")) {
                        return deckJsonImporter.generatePreview(selectedSourcePath, existingTerms);
                    } else if (fileName.endsWith(".csv")) {
                        return vocabularyCsvImporter.generatePreview(selectedSourcePath, existingTerms);
                    } else if (fileName.endsWith(".apkg")) {
                        return ankiApkgImporter.generatePreview(selectedSourcePath, existingTerms);
                    } else {
                        throw new IllegalArgumentException(localizationManager.get("importExport.unsupportedFormat"));
                    }
                },
                report -> {
                    previewButton.setDisable(false);
                    importProgressLabel.setText("");
                    activePreviewReport = report;
                    previewTable.setItems(FXCollections.observableArrayList(report.items()));

                    String summary = MessageFormat.format(
                            localizationManager.get("importExport.previewSummary"),
                            report.totalCount(),
                            report.validCount(),
                            report.duplicateCount(),
                            report.malformedCount()
                    );
                    previewSummaryLabel.setText(summary);
                    previewSummaryLabel.setVisible(true);
                    previewSummaryLabel.setManaged(true);

                    confirmImportButton.setDisable(!report.canImport());
                },
                throwable -> {
                    previewButton.setDisable(false);
                    importProgressLabel.setText("");
                    DialogHelper.showError(getWindow(), throwable.getMessage(), localizationManager, themeManager);
                }
        );
    }

    @FXML
    public void handleConfirmImport() {
        if (activePreviewReport == null || !activePreviewReport.canImport()) {
            DialogHelper.showError(getWindow(), localizationManager.get("importExport.noValidCardsToImport"),
                    localizationManager, themeManager);
            return;
        }

        DeckChoice choice = targetDeckComboBox.getSelectionModel().getSelectedItem();
        Long targetDeckId = (choice != null && !choice.isNewDeck()) ? choice.deck().id() : null;
        DuplicateStrategy strategy = duplicateStrategyComboBox.getSelectionModel().getSelectedItem() != null
                ? duplicateStrategyComboBox.getSelectionModel().getSelectedItem().strategy()
                : DuplicateStrategy.SKIP;

        confirmImportButton.setDisable(true);
        importProgressLabel.setText(localizationManager.get("importExport.status.processing"));

        backgroundTaskExecutor.runAsync(
                () -> atomicDeckImporter.executeImport(activePreviewReport, targetDeckId, strategy),
                result -> {
                    confirmImportButton.setDisable(false);
                    importProgressLabel.setText("");
                    refresh();

                    String msg = MessageFormat.format(
                            localizationManager.get("importExport.importSuccessMessage"),
                            result.importedCount(),
                            result.skippedCount(),
                            result.rejectedCount()
                    );
                    DialogHelper.showInfo(getWindow(), localizationManager.get("importExport.importSuccessTitle"),
                            msg, themeManager);
                },
                throwable -> {
                    confirmImportButton.setDisable(false);
                    importProgressLabel.setText("");
                    DialogHelper.showError(getWindow(), throwable.getMessage(), localizationManager, themeManager);
                }
        );
    }

    @FXML
    public void handleExport() {
        DeckChoice selectedChoice = exportDeckComboBox.getSelectionModel().getSelectedItem();
        if (selectedChoice == null || selectedChoice.deck() == null) {
            DialogHelper.showError(getWindow(), localizationManager.get("importExport.noDeckSelected"),
                    localizationManager, themeManager);
            return;
        }

        Deck deck = selectedChoice.deck();
        ExportFormatChoice formatChoice = exportFormatComboBox.getSelectionModel().getSelectedItem();
        ExportFormat format = formatChoice != null ? formatChoice.format() : ExportFormat.JSON;

        FileChooser chooser = new FileChooser();
        chooser.setTitle(localizationManager.get("importExport.exportButton"));
        if (format == ExportFormat.JSON) {
            chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("SenkoJE JSON (*.json)", "*.json"));
            chooser.setInitialFileName(sanitizeFileName(deck.title()) + ".json");
        } else {
            chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Vocabulary CSV (*.csv)", "*.csv"));
            chooser.setInitialFileName(sanitizeFileName(deck.title()) + ".csv");
        }

        File targetFile = chooser.showSaveDialog(getWindow());
        if (targetFile == null) {
            return;
        }

        Path targetPath = targetFile.toPath();
        exportProgressLabel.setText(localizationManager.get("importExport.status.processing"));
        exportButton.setDisable(true);

        backgroundTaskExecutor.runAsync(
                () -> {
                    List<Card> cards = cardRepository.findByDeckId(deck.id());
                    List<ExportCardBundle> bundles = new ArrayList<>(cards.size());
                    for (Card card : cards) {
                        List<CardMeaning> meanings = cardRepository.findMeaningsByCardId(card.id());
                        List<CardExample> examples = cardRepository.findExamplesByCardId(card.id());
                        bundles.add(new ExportCardBundle(card, meanings, examples));
                    }

                    if (format == ExportFormat.JSON) {
                        DeckSettings settings = deckRepository.findSettingsByDeckId(deck.id()).orElse(null);
                        deckJsonExporter.exportDeck(deck, settings, bundles, targetPath);
                    } else {
                        vocabularyCsvExporter.exportCards(bundles, targetPath);
                    }
                    return targetPath;
                },
                path -> {
                    exportButton.setDisable(false);
                    exportProgressLabel.setText("");
                    String msg = MessageFormat.format(
                            localizationManager.get("importExport.exportSuccessMessage"),
                            path.toAbsolutePath().toString()
                    );
                    DialogHelper.showInfo(getWindow(), localizationManager.get("importExport.exportSuccessTitle"),
                            msg, themeManager);
                },
                throwable -> {
                    exportButton.setDisable(false);
                    exportProgressLabel.setText("");
                    DialogHelper.showError(getWindow(), throwable.getMessage(), localizationManager, themeManager);
                }
        );
    }

    @FXML
    public void handleCreateBackup() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle(localizationManager.get("importExport.createBackupButton"));
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("SQLite Database (*.db)", "*.db"));
        chooser.setInitialFileName("senkoje_backup_" + Instant.now().toEpochMilli() + ".db");

        File targetFile = chooser.showSaveDialog(getWindow());
        if (targetFile == null) {
            return;
        }

        Path targetPath = targetFile.toPath();
        backupProgressLabel.setText(localizationManager.get("importExport.status.processing"));
        createBackupButton.setDisable(true);

        backgroundTaskExecutor.runAsync(
                () -> sqliteBackupService.createBackup(targetPath),
                metadata -> {
                    createBackupButton.setDisable(false);
                    backupProgressLabel.setText("");
                    String msg = MessageFormat.format(
                            localizationManager.get("importExport.backupSuccessMessage"),
                            metadata.backupPath().toAbsolutePath().toString(),
                            metadata.fileSizeBytes(),
                            metadata.deckCount(),
                            metadata.cardCount()
                    );
                    DialogHelper.showInfo(getWindow(), localizationManager.get("importExport.backupSuccessTitle"),
                            msg, themeManager);
                },
                throwable -> {
                    createBackupButton.setDisable(false);
                    backupProgressLabel.setText("");
                    DialogHelper.showError(getWindow(), throwable.getMessage(), localizationManager, themeManager);
                }
        );
    }

    @FXML
    public void handleRestoreBackup() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle(localizationManager.get("importExport.restoreBackupButton"));
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("SQLite Database (*.db, *.sqlite)", "*.db", "*.sqlite"));

        File sourceFile = chooser.showOpenDialog(getWindow());
        if (sourceFile == null) {
            return;
        }

        boolean confirmed = DialogHelper.showConfirmation(
                getWindow(),
                localizationManager.get("importExport.restoreConfirmTitle"),
                localizationManager.get("importExport.restoreConfirmMessage"),
                localizationManager,
                themeManager
        );
        if (!confirmed) {
            return;
        }

        Path sourcePath = sourceFile.toPath();
        backupProgressLabel.setText(localizationManager.get("importExport.status.processing"));
        restoreBackupButton.setDisable(true);

        backgroundTaskExecutor.runAsync(
                () -> sqliteBackupService.restoreBackup(sourcePath),
                result -> {
                    restoreBackupButton.setDisable(false);
                    backupProgressLabel.setText("");
                    refresh();

                    String msg = MessageFormat.format(
                            localizationManager.get("importExport.restoreSuccessMessage"),
                            result.restoredFromPath().toAbsolutePath().toString(),
                            result.safetyBackupPath().toAbsolutePath().toString(),
                            result.deckCount(),
                            result.cardCount()
                    );
                    DialogHelper.showInfo(getWindow(), localizationManager.get("importExport.restoreSuccessTitle"),
                            msg, themeManager);
                },
                throwable -> {
                    restoreBackupButton.setDisable(false);
                    backupProgressLabel.setText("");
                    DialogHelper.showError(getWindow(), throwable.getMessage(), localizationManager, themeManager);
                }
        );
    }

    private Set<String> extractExistingTermsForTarget() {
        DeckChoice choice = targetDeckComboBox.getSelectionModel().getSelectedItem();
        if (choice == null || choice.isNewDeck() || choice.deck() == null) {
            return Set.of();
        }
        List<Card> cards = cardRepository.findByDeckId(choice.deck().id());
        Set<String> terms = new HashSet<>();
        for (Card card : cards) {
            if (card.term() != null) {
                terms.add(card.term().trim().toLowerCase(Locale.ROOT));
            }
        }
        return terms;
    }

    private ImportPreviewReport createApkgPreviewNotice(Path path) {
        ImportPreviewItem notice = new ImportPreviewItem(
                1,
                null,
                ImportItemStatus.INVALID,
                "Anki .apkg file detected: " + path.getFileName()
        );
        return ImportPreviewReport.of(List.of(notice), null, null);
    }

    private Window getWindow() {
        return chooseFileButton.getScene() != null ? chooseFileButton.getScene().getWindow() : null;
    }

    private static String sanitizeFileName(String name) {
        if (name == null || name.isBlank()) {
            return "deck";
        }
        return name.replaceAll("[^a-zA-Z0-9._-]", "_");
    }

    public record DeckChoice(Long id, String displayText, Deck deck, boolean isNewDeck) {
        public static DeckChoice newDeckOption(String text) {
            return new DeckChoice(null, text, null, true);
        }

        public static DeckChoice existingDeck(Deck deck) {
            return new DeckChoice(deck.id(), deck.title() + " (" + deck.targetLanguage() + " / " + deck.level().code() + ")", deck, false);
        }
    }

    public record DuplicateStrategyChoice(DuplicateStrategy strategy, String labelKey) {
    }

    public enum ExportFormat {
        JSON,
        CSV
    }

    public record ExportFormatChoice(ExportFormat format, String labelKey) {
    }
}
