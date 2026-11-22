package com.senkoje.presentation.controller;

import com.senkoje.domain.model.Deck;
import com.senkoje.domain.model.DeckSettings;
import com.senkoje.domain.service.DeckSettingsValidator;
import com.senkoje.domain.service.DomainValidationException;
import com.senkoje.presentation.i18n.LocalizationManager;
import com.senkoje.presentation.theme.ThemeManager;
import java.util.Optional;
import javafx.event.ActionEvent;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Window;

/**
 * Hộp thoại cấu hình FSRS-6 cho từng bộ thẻ: xác suất ghi nhớ mục tiêu, khoảng cách tối đa,
 * hạn mức hằng ngày, bước học ngắn hạn, bước học lại và độ nhiễu phân tán (fuzzing).
 */
public final class DeckSettingsDialog {

    private static final DeckSettingsValidator VALIDATOR = new DeckSettingsValidator();

    private DeckSettingsDialog() {
    }

    /**
     * Mở hộp thoại cấu hình FSRS-6 cho một bộ thẻ và trả về cấu hình đã kiểm tra hợp lệ.
     *
     * @param owner cửa sổ cha
     * @param deck bộ thẻ đang cấu hình
     * @param currentSettings cấu hình hiện tại của bộ thẻ
     * @param localizationManager trình quản lý đa ngôn ngữ
     * @param themeManager trình quản lý chủ đề
     * @return cấu hình DeckSettings mới nếu người dùng xác nhận lưu
     */
    public static Optional<DeckSettings> showAndWait(
            Window owner,
            Deck deck,
            DeckSettings currentSettings,
            LocalizationManager localizationManager,
            ThemeManager themeManager
    ) {
        DeckSettings baseline = currentSettings == null ? DeckSettings.defaults(deck.id()) : currentSettings;

        Dialog<DeckSettings> dialog = new Dialog<>();
        if (owner != null) {
            dialog.initOwner(owner);
        }
        dialog.setTitle(localizationManager.get("deckSettings.title", deck.title()));
        themeManager.applyTheme(dialog.getDialogPane());

        ButtonType saveButtonType = new ButtonType(
                localizationManager.get("common.save"), ButtonBar.ButtonData.OK_DONE);
        ButtonType cancelButtonType = new ButtonType(
                localizationManager.get("common.cancel"), ButtonBar.ButtonData.CANCEL_CLOSE);
        dialog.getDialogPane().getButtonTypes().setAll(saveButtonType, cancelButtonType);

        TextField retentionField = new TextField(Double.toString(baseline.desiredRetention()));
        TextField maxIntervalField = new TextField(Integer.toString(baseline.maximumInterval()));
        TextField newPerDayField = new TextField(Integer.toString(baseline.newCardsPerDay()));
        TextField maxReviewsField = new TextField(Integer.toString(baseline.maximumReviewsPerDay()));
        TextField learningStepsField = new TextField(baseline.learningSteps());
        TextField relearningStepsField = new TextField(baseline.relearningSteps());
        CheckBox fuzzingCheckBox = new CheckBox(localizationManager.get("deckSettings.enableFuzzing"));
        fuzzingCheckBox.setSelected(baseline.enableFuzzing());

        Label errorLabel = new Label();
        errorLabel.getStyleClass().add("status-error");
        errorLabel.setVisible(false);
        errorLabel.setManaged(false);
        errorLabel.setWrapText(true);

        GridPane grid = new GridPane();
        grid.setHgap(12);
        grid.setVgap(10);
        grid.setPadding(new Insets(12));
        grid.setPrefWidth(480);

        grid.add(new Label(localizationManager.get("deckSettings.desiredRetention")), 0, 0);
        grid.add(retentionField, 1, 0);
        grid.add(new Label(localizationManager.get("deckSettings.maximumInterval")), 0, 1);
        grid.add(maxIntervalField, 1, 1);
        grid.add(new Label(localizationManager.get("deckSettings.newCardsPerDay")), 0, 2);
        grid.add(newPerDayField, 1, 2);
        grid.add(new Label(localizationManager.get("deckSettings.maximumReviewsPerDay")), 0, 3);
        grid.add(maxReviewsField, 1, 3);
        grid.add(new Label(localizationManager.get("deckSettings.learningSteps")), 0, 4);
        grid.add(learningStepsField, 1, 4);
        grid.add(new Label(localizationManager.get("deckSettings.relearningSteps")), 0, 5);
        grid.add(relearningStepsField, 1, 5);
        grid.add(fuzzingCheckBox, 1, 6);

        GridPane.setHgrow(retentionField, Priority.ALWAYS);
        GridPane.setHgrow(maxIntervalField, Priority.ALWAYS);
        GridPane.setHgrow(newPerDayField, Priority.ALWAYS);
        GridPane.setHgrow(maxReviewsField, Priority.ALWAYS);
        GridPane.setHgrow(learningStepsField, Priority.ALWAYS);
        GridPane.setHgrow(relearningStepsField, Priority.ALWAYS);

        VBox content = new VBox(8, grid, errorLabel);
        dialog.getDialogPane().setContent(content);

        // Kiểm tra tính hợp lệ của toàn bộ thông số FSRS-6 trước khi đóng hộp thoại
        Button saveButton = (Button) dialog.getDialogPane().lookupButton(saveButtonType);
        saveButton.addEventFilter(ActionEvent.ACTION, event -> {
            try {
                DeckSettings candidate = parseSettings(
                        deck.id(),
                        retentionField.getText(),
                        maxIntervalField.getText(),
                        newPerDayField.getText(),
                        maxReviewsField.getText(),
                        learningStepsField.getText(),
                        relearningStepsField.getText(),
                        fuzzingCheckBox.isSelected()
                );
                VALIDATOR.validate(candidate);
            } catch (NumberFormatException exception) {
                errorLabel.setText(localizationManager.get("deckSettings.validation.invalidNumber"));
                errorLabel.setVisible(true);
                errorLabel.setManaged(true);
                event.consume();
            } catch (DomainValidationException exception) {
                errorLabel.setText(String.join("; ", exception.getErrors()));
                errorLabel.setVisible(true);
                errorLabel.setManaged(true);
                event.consume();
            }
        });

        dialog.setResultConverter(buttonType -> {
            if (buttonType != saveButtonType) {
                return null;
            }
            return parseSettings(
                    deck.id(),
                    retentionField.getText(),
                    maxIntervalField.getText(),
                    newPerDayField.getText(),
                    maxReviewsField.getText(),
                    learningStepsField.getText(),
                    relearningStepsField.getText(),
                    fuzzingCheckBox.isSelected()
            );
        });

        return dialog.showAndWait();
    }

    private static DeckSettings parseSettings(
            Long deckId,
            String retentionText,
            String maxIntervalText,
            String newPerDayText,
            String maxReviewsText,
            String learningStepsText,
            String relearningStepsText,
            boolean enableFuzzing
    ) {
        return new DeckSettings(
                deckId,
                Double.parseDouble(retentionText.trim()),
                Integer.parseInt(maxIntervalText.trim()),
                Integer.parseInt(newPerDayText.trim()),
                Integer.parseInt(maxReviewsText.trim()),
                learningStepsText == null ? "" : learningStepsText.trim(),
                relearningStepsText == null ? "" : relearningStepsText.trim(),
                enableFuzzing
        );
    }
}
