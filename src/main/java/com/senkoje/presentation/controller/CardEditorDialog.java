package com.senkoje.presentation.controller;

import com.senkoje.application.dto.CardExtraData;
import com.senkoje.application.mapper.CardExtraDataCodec;
import com.senkoje.domain.model.Card;
import com.senkoje.domain.model.CardExample;
import com.senkoje.domain.model.CardMeaning;
import com.senkoje.domain.model.CardType;
import com.senkoje.domain.model.Deck;
import com.senkoje.domain.model.DefinitionLanguage;
import com.senkoje.domain.model.ProficiencyLevel;
import com.senkoje.domain.model.TargetLanguage;
import com.senkoje.presentation.i18n.LocalizationManager;
import com.senkoje.presentation.theme.ThemeManager;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import javafx.event.ActionEvent;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Separator;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Window;
import javafx.util.StringConverter;

/**
 * Hộp thoại tạo mới và chỉnh sửa thẻ học tập hỗ trợ đầy đủ trường tiếng Nhật, tiếng Anh,
 * ngữ pháp, nhiều nghĩa bổ sung và nhiều câu ví dụ kèm bản dịch.
 */
public final class CardEditorDialog {

    private static final List<String> STANDARD_LEVELS = List.of(
            "", "N5", "N4", "N3", "N2", "N1", "A1", "A2", "B1", "B2", "C1", "C2"
    );

    private CardEditorDialog() {
    }

    /**
     * Dữ liệu biểu mẫu thẻ hoàn chỉnh trả về khi người dùng xác nhận lưu.
     */
    public record CardFormData(
            long deckId,
            CardType cardType,
            String term,
            String reading,
            String meaning,
            String partOfSpeech,
            ProficiencyLevel level,
            String extraDataJson,
            String notes,
            List<CardMeaning> meanings,
            List<CardExample> examples
    ) {
    }

    /**
     * Mở hộp thoại tạo mới hoặc chỉnh sửa thẻ học tập.
     *
     * @param owner cửa sổ cha
     * @param availableDecks danh sách bộ thẻ hiện có
     * @param existingCard thẻ đang chỉnh sửa (null nếu tạo mới)
     * @param existingMeanings danh sách nghĩa bổ sung hiện có
     * @param existingExamples danh sách câu ví dụ hiện có
     * @param localizationManager trình quản lý đa ngôn ngữ
     * @param themeManager trình quản lý chủ đề
     * @return dữ liệu biểu mẫu thẻ nếu người dùng bấm Lưu
     */
    public static Optional<CardFormData> showAndWait(
            Window owner,
            List<Deck> availableDecks,
            Card existingCard,
            List<CardMeaning> existingMeanings,
            List<CardExample> existingExamples,
            LocalizationManager localizationManager,
            ThemeManager themeManager
    ) {
        return createDialog(
                owner, availableDecks, existingCard, existingMeanings, existingExamples,
                localizationManager, themeManager
        ).showAndWait();
    }

    /**
     * Tạo đối tượng Dialog tạo mới hoặc chỉnh sửa thẻ học tập với bố cục cột rõ ràng và kiểu nút đồng nhất.
     *
     * @param owner cửa sổ cha
     * @param availableDecks danh sách bộ thẻ hiện có
     * @param existingCard thẻ đang chỉnh sửa (null nếu tạo mới)
     * @param existingMeanings danh sách nghĩa bổ sung hiện có
     * @param existingExamples danh sách câu ví dụ hiện có
     * @param localizationManager trình quản lý đa ngôn ngữ
     * @param themeManager trình quản lý chủ đề
     * @return đối tượng Dialog sẵn sàng hiển thị hoặc kiểm thử bố cục
     */
    public static Dialog<CardFormData> createDialog(
            Window owner,
            List<Deck> availableDecks,
            Card existingCard,
            List<CardMeaning> existingMeanings,
            List<CardExample> existingExamples,
            LocalizationManager localizationManager,
            ThemeManager themeManager
    ) {
        Dialog<CardFormData> dialog = new Dialog<>();
        if (owner != null) {
            dialog.initOwner(owner);
        }
        dialog.setTitle(localizationManager.get(
                existingCard == null ? "cardEditor.createTitle" : "cardEditor.editTitle"));
        dialog.setResizable(true);
        themeManager.applyTheme(dialog.getDialogPane());
        dialog.getDialogPane().setMinWidth(660);
        dialog.getDialogPane().setPrefWidth(680);

        ButtonType saveButtonType = new ButtonType(
                localizationManager.get("common.save"), ButtonBar.ButtonData.OK_DONE);
        ButtonType cancelButtonType = new ButtonType(
                localizationManager.get("common.cancel"), ButtonBar.ButtonData.CANCEL_CLOSE);
        dialog.getDialogPane().getButtonTypes().setAll(saveButtonType, cancelButtonType);

        ComboBox<Deck> deckBox = new ComboBox<>();
        deckBox.getItems().setAll(availableDecks);
        deckBox.setMaxWidth(Double.MAX_VALUE);
        deckBox.setConverter(new StringConverter<>() {
            @Override
            public String toString(Deck deck) {
                return deck == null ? "" : deck.title() + " (" + deck.targetLanguage().name() + ")";
            }

            @Override
            public Deck fromString(String string) {
                return null;
            }
        });
        if (existingCard != null) {
            availableDecks.stream()
                    .filter(deck -> existingCard.deckId().equals(deck.id()))
                    .findFirst()
                    .ifPresent(deckBox::setValue);
            deckBox.setDisable(true);
        } else if (!availableDecks.isEmpty()) {
            deckBox.setValue(availableDecks.getFirst());
        }

        ComboBox<CardType> typeBox = new ComboBox<>();
        typeBox.getItems().setAll(CardType.values());
        typeBox.setValue(existingCard == null ? CardType.VOCABULARY : existingCard.cardType());
        typeBox.setMaxWidth(Double.MAX_VALUE);

        TextField termField = new TextField(existingCard == null ? "" : existingCard.term());
        TextField readingField = new TextField(existingCard == null || existingCard.reading() == null
                ? "" : existingCard.reading());
        TextField meaningField = new TextField(existingCard == null ? "" : existingCard.meaning());
        TextField posField = new TextField(existingCard == null || existingCard.partOfSpeech() == null
                ? "" : existingCard.partOfSpeech());

        ComboBox<String> levelBox = new ComboBox<>();
        levelBox.setEditable(true);
        levelBox.getItems().setAll(STANDARD_LEVELS);
        levelBox.setValue(existingCard == null || existingCard.level() == null ? "" : existingCard.level().code());
        levelBox.setMaxWidth(Double.MAX_VALUE);

        TextArea notesArea = new TextArea(existingCard == null || existingCard.notes() == null
                ? "" : existingCard.notes());
        notesArea.setPrefRowCount(2);
        notesArea.setWrapText(true);

        GridPane coreGrid = createFormGrid();
        coreGrid.add(new Label(localizationManager.get("cardEditor.field.deck")), 0, 0);
        coreGrid.add(deckBox, 1, 0);
        coreGrid.add(new Label(localizationManager.get("cardEditor.field.type")), 0, 1);
        coreGrid.add(typeBox, 1, 1);
        coreGrid.add(new Label(localizationManager.get("cardEditor.field.term")), 0, 2);
        coreGrid.add(termField, 1, 2);
        coreGrid.add(new Label(localizationManager.get("cardEditor.field.reading")), 0, 3);
        coreGrid.add(readingField, 1, 3);
        coreGrid.add(new Label(localizationManager.get("cardEditor.field.meaning")), 0, 4);
        coreGrid.add(meaningField, 1, 4);
        coreGrid.add(new Label(localizationManager.get("cardEditor.field.partOfSpeech")), 0, 5);
        coreGrid.add(posField, 1, 5);
        coreGrid.add(new Label(localizationManager.get("cardEditor.field.level")), 0, 6);
        coreGrid.add(levelBox, 1, 6);
        coreGrid.add(new Label(localizationManager.get("cardEditor.field.notes")), 0, 7);
        coreGrid.add(notesArea, 1, 7);
        setGrow(deckBox, typeBox, termField, readingField, meaningField, posField, levelBox, notesArea);

        CardExtraData extra = CardExtraDataCodec.fromJson(existingCard == null ? null : existingCard.extraData());

        // Các trường mở rộng dành riêng cho tiếng Nhật
        TextField furiganaField = new TextField(nullToEmpty(extra.furigana()));
        TextField kanjiField = new TextField(nullToEmpty(extra.kanjiComponents()));
        TextField onyomiField = new TextField(nullToEmpty(extra.onyomi()));
        TextField kunyomiField = new TextField(nullToEmpty(extra.kunyomi()));
        TextField pitchField = new TextField(nullToEmpty(extra.pitchAccent()));
        GridPane jaGrid = createFormGrid();
        jaGrid.add(new Label(localizationManager.get("cardEditor.field.ja.furigana")), 0, 0);
        jaGrid.add(furiganaField, 1, 0);
        jaGrid.add(new Label(localizationManager.get("cardEditor.field.ja.kanjiComponents")), 0, 1);
        jaGrid.add(kanjiField, 1, 1);
        jaGrid.add(new Label(localizationManager.get("cardEditor.field.ja.onyomi")), 0, 2);
        jaGrid.add(onyomiField, 1, 2);
        jaGrid.add(new Label(localizationManager.get("cardEditor.field.ja.kunyomi")), 0, 3);
        jaGrid.add(kunyomiField, 1, 3);
        jaGrid.add(new Label(localizationManager.get("cardEditor.field.ja.pitchAccent")), 0, 4);
        jaGrid.add(pitchField, 1, 4);
        setGrow(furiganaField, kanjiField, onyomiField, kunyomiField, pitchField);
        Label jaHeader = new Label(localizationManager.get("cardEditor.section.japanese"));
        jaHeader.getStyleClass().add("section-title");
        VBox jaSection = new VBox(6, jaHeader, jaGrid);

        // Các trường mở rộng dành riêng cho tiếng Anh
        TextField ipaField = new TextField(nullToEmpty(extra.ipa()));
        TextField collocationsField = new TextField(nullToEmpty(extra.collocations()));
        TextField registerField = new TextField(nullToEmpty(extra.register()));
        GridPane enGrid = createFormGrid();
        enGrid.add(new Label(localizationManager.get("cardEditor.field.en.ipa")), 0, 0);
        enGrid.add(ipaField, 1, 0);
        enGrid.add(new Label(localizationManager.get("cardEditor.field.en.collocations")), 0, 1);
        enGrid.add(collocationsField, 1, 1);
        enGrid.add(new Label(localizationManager.get("cardEditor.field.en.register")), 0, 2);
        enGrid.add(registerField, 1, 2);
        setGrow(ipaField, collocationsField, registerField);
        Label enHeader = new Label(localizationManager.get("cardEditor.section.english"));
        enHeader.getStyleClass().add("section-title");
        VBox enSection = new VBox(6, enHeader, enGrid);

        // Các trường mở rộng dành riêng cho thẻ ngữ pháp
        TextField patternField = new TextField(nullToEmpty(extra.grammarPattern()));
        TextField usageField = new TextField(nullToEmpty(extra.usageNotes()));
        TextField formationField = new TextField(nullToEmpty(extra.formationRules()));
        TextField relatedVocabField = new TextField(nullToEmpty(extra.relatedVocabulary()));
        GridPane grammarGrid = createFormGrid();
        grammarGrid.add(new Label(localizationManager.get("cardEditor.field.grammar.pattern")), 0, 0);
        grammarGrid.add(patternField, 1, 0);
        grammarGrid.add(new Label(localizationManager.get("cardEditor.field.grammar.usage")), 0, 1);
        grammarGrid.add(usageField, 1, 1);
        grammarGrid.add(new Label(localizationManager.get("cardEditor.field.grammar.formation")), 0, 2);
        grammarGrid.add(formationField, 1, 2);
        grammarGrid.add(new Label(localizationManager.get("cardEditor.field.grammar.relatedVocabulary")), 0, 3);
        grammarGrid.add(relatedVocabField, 1, 3);
        setGrow(patternField, usageField, formationField, relatedVocabField);
        Label grammarHeader = new Label(localizationManager.get("cardEditor.section.grammar"));
        grammarHeader.getStyleClass().add("section-title");
        VBox grammarSection = new VBox(6, grammarHeader, grammarGrid);

        Runnable updateSectionVisibility = () -> {
            Deck selectedDeck = deckBox.getValue();
            boolean isJa = selectedDeck == null || selectedDeck.targetLanguage() == TargetLanguage.JA;
            boolean isEn = selectedDeck != null && selectedDeck.targetLanguage() == TargetLanguage.EN;
            boolean isGrammar = typeBox.getValue() == CardType.GRAMMAR;

            jaSection.setVisible(isJa);
            jaSection.setManaged(isJa);
            enSection.setVisible(isEn);
            enSection.setManaged(isEn);
            grammarSection.setVisible(isGrammar);
            grammarSection.setManaged(isGrammar);
        };
        deckBox.valueProperty().addListener((obs, oldVal, newVal) -> updateSectionVisibility.run());
        typeBox.valueProperty().addListener((obs, oldVal, newVal) -> updateSectionVisibility.run());
        updateSectionVisibility.run();

        // Khu vực danh sách nhiều nghĩa bổ sung
        VBox meaningsRowsBox = new VBox(6);
        List<MeaningRow> meaningRows = new ArrayList<>();
        if (existingMeanings != null) {
            for (CardMeaning item : existingMeanings) {
                addMeaningRow(meaningsRowsBox, meaningRows, item.languageCode(), item.meaning(), localizationManager);
            }
        }
        Button addMeaningButton = new Button(localizationManager.get("common.add"));
        addMeaningButton.getStyleClass().add("secondary-button");
        addMeaningButton.setOnAction(event ->
                addMeaningRow(meaningsRowsBox, meaningRows, DefinitionLanguage.VI, "", localizationManager));
        Label meaningsHeader = new Label(localizationManager.get("cardEditor.section.meanings"));
        meaningsHeader.getStyleClass().add("section-title");
        HBox meaningsTitleRow = new HBox(10, meaningsHeader, addMeaningButton);

        // Khu vực danh sách nhiều câu ví dụ và bản dịch
        VBox examplesRowsBox = new VBox(8);
        List<ExampleRow> exampleRows = new ArrayList<>();
        if (existingExamples != null) {
            for (CardExample item : existingExamples) {
                addExampleRow(
                        examplesRowsBox,
                        exampleRows,
                        item.sentence(),
                        nullToEmpty(item.translation()),
                        item.translationLanguage() == null ? DefinitionLanguage.VI : item.translationLanguage(),
                        localizationManager
                );
            }
        }
        Button addExampleButton = new Button(localizationManager.get("common.add"));
        addExampleButton.getStyleClass().add("secondary-button");
        addExampleButton.setOnAction(event ->
                addExampleRow(examplesRowsBox, exampleRows, "", "", DefinitionLanguage.VI, localizationManager));
        Label examplesHeader = new Label(localizationManager.get("cardEditor.section.examples"));
        examplesHeader.getStyleClass().add("section-title");
        HBox examplesTitleRow = new HBox(10, examplesHeader, addExampleButton);

        Label errorLabel = new Label();
        errorLabel.getStyleClass().add("status-error");
        errorLabel.setVisible(false);
        errorLabel.setManaged(false);
        errorLabel.setWrapText(true);

        VBox formBody = new VBox(
                12,
                coreGrid,
                new Separator(),
                jaSection,
                enSection,
                grammarSection,
                new Separator(),
                meaningsTitleRow,
                meaningsRowsBox,
                new Separator(),
                examplesTitleRow,
                examplesRowsBox,
                errorLabel
        );
        formBody.setPadding(new Insets(12));

        ScrollPane scrollPane = new ScrollPane(formBody);
        scrollPane.setFitToWidth(true);
        scrollPane.setPrefViewportWidth(640);
        scrollPane.setPrefViewportHeight(540);
        dialog.getDialogPane().setContent(scrollPane);

        Button saveButton = (Button) dialog.getDialogPane().lookupButton(saveButtonType);
        saveButton.getStyleClass().add("primary-button");
        Button cancelButton = (Button) dialog.getDialogPane().lookupButton(cancelButtonType);
        cancelButton.getStyleClass().add("secondary-button");
        saveButton.addEventFilter(ActionEvent.ACTION, event -> {
            if (deckBox.getValue() == null
                    || typeBox.getValue() == null
                    || termField.getText() == null || termField.getText().isBlank()
                    || meaningField.getText() == null || meaningField.getText().isBlank()) {
                errorLabel.setText(localizationManager.get("cardEditor.validation.required"));
                errorLabel.setVisible(true);
                errorLabel.setManaged(true);
                event.consume();
            }
        });

        dialog.setResultConverter(buttonType -> {
            if (buttonType != saveButtonType) {
                return null;
            }
            String rawLevel = levelBox.getEditor().getText();
            ProficiencyLevel level = rawLevel == null || rawLevel.isBlank()
                    ? null
                    : ProficiencyLevel.of(rawLevel.trim());

            CardExtraData extraData = new CardExtraData(
                    furiganaField.getText(),
                    kanjiField.getText(),
                    onyomiField.getText(),
                    kunyomiField.getText(),
                    pitchField.getText(),
                    ipaField.getText(),
                    collocationsField.getText(),
                    registerField.getText(),
                    patternField.getText(),
                    usageField.getText(),
                    formationField.getText(),
                    relatedVocabField.getText()
            );

            List<CardMeaning> meanings = new ArrayList<>();
            int meaningOrder = 0;
            for (MeaningRow row : meaningRows) {
                String text = row.meaningField().getText();
                if (text != null && !text.isBlank()) {
                    meanings.add(new CardMeaning(
                            null,
                            existingCard == null ? null : existingCard.id(),
                            row.languageBox().getValue(),
                            text.trim(),
                            meaningOrder++
                    ));
                }
            }

            List<CardExample> examples = new ArrayList<>();
            int exampleOrder = 0;
            for (ExampleRow row : exampleRows) {
                String sentence = row.sentenceField().getText();
                if (sentence != null && !sentence.isBlank()) {
                    String translation = trimToNull(row.translationField().getText());
                    examples.add(new CardExample(
                            null,
                            existingCard == null ? null : existingCard.id(),
                            sentence.trim(),
                            translation,
                            translation == null ? null : row.languageBox().getValue(),
                            exampleOrder++
                    ));
                }
            }

            return new CardFormData(
                    deckBox.getValue().id(),
                    typeBox.getValue(),
                    termField.getText().trim(),
                    trimToNull(readingField.getText()),
                    meaningField.getText().trim(),
                    trimToNull(posField.getText()),
                    level,
                    CardExtraDataCodec.toJson(extraData),
                    trimToNull(notesArea.getText()),
                    List.copyOf(meanings),
                    List.copyOf(examples)
            );
        });

        return dialog;
    }

    private static void addMeaningRow(
            VBox container,
            List<MeaningRow> rows,
            DefinitionLanguage language,
            String meaning,
            LocalizationManager localizationManager
    ) {
        ComboBox<DefinitionLanguage> langBox = new ComboBox<>();
        langBox.getItems().setAll(DefinitionLanguage.values());
        langBox.setValue(language == null ? DefinitionLanguage.VI : language);
        langBox.setPrefWidth(90);

        TextField textField = new TextField(nullToEmpty(meaning));
        textField.setPromptText(localizationManager.get("cardEditor.meanings.placeholder"));
        HBox.setHgrow(textField, Priority.ALWAYS);

        Button removeButton = new Button(localizationManager.get("common.remove"));
        removeButton.getStyleClass().add("danger-button");

        HBox rowNode = new HBox(8, langBox, textField, removeButton);
        MeaningRow row = new MeaningRow(langBox, textField, rowNode);
        rows.add(row);
        container.getChildren().add(rowNode);

        removeButton.setOnAction(event -> {
            rows.remove(row);
            container.getChildren().remove(rowNode);
        });
    }

    private static void addExampleRow(
            VBox container,
            List<ExampleRow> rows,
            String sentence,
            String translation,
            DefinitionLanguage translationLanguage,
            LocalizationManager localizationManager
    ) {
        TextField sentenceField = new TextField(nullToEmpty(sentence));
        sentenceField.setPromptText(localizationManager.get("cardEditor.examples.sentencePrompt"));

        ComboBox<DefinitionLanguage> langBox = new ComboBox<>();
        langBox.getItems().setAll(DefinitionLanguage.values());
        langBox.setValue(translationLanguage == null ? DefinitionLanguage.VI : translationLanguage);
        langBox.setPrefWidth(90);

        TextField translationField = new TextField(nullToEmpty(translation));
        translationField.setPromptText(localizationManager.get("cardEditor.examples.translationPrompt"));
        HBox.setHgrow(translationField, Priority.ALWAYS);

        Button removeButton = new Button(localizationManager.get("common.remove"));
        removeButton.getStyleClass().add("danger-button");

        HBox translationRow = new HBox(8, langBox, translationField, removeButton);
        VBox rowNode = new VBox(6, sentenceField, translationRow);
        rowNode.getStyleClass().add("card-panel");

        ExampleRow row = new ExampleRow(sentenceField, translationField, langBox, rowNode);
        rows.add(row);
        container.getChildren().add(rowNode);

        removeButton.setOnAction(event -> {
            rows.remove(row);
            container.getChildren().remove(rowNode);
        });
    }

    private static GridPane createFormGrid() {
        GridPane grid = new GridPane();
        grid.setHgap(12);
        grid.setVgap(8);
        // Cột nhãn luôn giữ đủ chiều rộng ưu tiên, căn thẳng hàng giữa các nhóm trường và không bị cắt chữ
        ColumnConstraints labelColumn = new ColumnConstraints();
        labelColumn.setMinWidth(Region.USE_PREF_SIZE);
        labelColumn.setPrefWidth(210);
        labelColumn.setHgrow(Priority.NEVER);
        ColumnConstraints inputColumn = new ColumnConstraints();
        inputColumn.setHgrow(Priority.ALWAYS);
        inputColumn.setFillWidth(true);
        grid.getColumnConstraints().addAll(labelColumn, inputColumn);
        return grid;
    }

    private static void setGrow(javafx.scene.Node... nodes) {
        for (javafx.scene.Node node : nodes) {
            GridPane.setHgrow(node, Priority.ALWAYS);
        }
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private static String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private record MeaningRow(
            ComboBox<DefinitionLanguage> languageBox,
            TextField meaningField,
            HBox node
    ) {
    }

    private record ExampleRow(
            TextField sentenceField,
            TextField translationField,
            ComboBox<DefinitionLanguage> languageBox,
            VBox node
    ) {
    }
}
