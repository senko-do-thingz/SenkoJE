package com.senkoje.presentation.controller;

import com.senkoje.domain.model.Deck;
import com.senkoje.domain.model.DefinitionLanguage;
import com.senkoje.domain.model.ProficiencyLevel;
import com.senkoje.domain.model.TargetLanguage;
import com.senkoje.presentation.i18n.LocalizationManager;
import com.senkoje.presentation.theme.ThemeManager;
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
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Window;

/**
 * Hộp thoại tạo mới hoặc chỉnh sửa thông tin bộ thẻ (tên, ngôn ngữ đích, ngôn ngữ giải nghĩa, trình độ, mô tả).
 */
public final class DeckEditorDialog {

    private static final List<String> STANDARD_LEVEL_CODES = List.of(
            "N5", "N4", "N3", "N2", "N1", "A1", "A2", "B1", "B2", "C1", "C2"
    );

    private DeckEditorDialog() {
    }

    /**
     * Dữ liệu biểu mẫu bộ thẻ trả về khi người dùng xác nhận lưu.
     */
    public record DeckFormData(
            String title,
            TargetLanguage targetLanguage,
            DefinitionLanguage definitionLanguage,
            ProficiencyLevel level,
            String description
    ) {
    }

    /**
     * Mở hộp thoại tạo mới hoặc chỉnh sửa bộ thẻ và trả về dữ liệu hợp lệ nếu người dùng bấm Lưu.
     *
     * @param owner cửa sổ cha
     * @param existingDeck bộ thẻ hiện có nếu đang chỉnh sửa (null nếu tạo mới)
     * @param localizationManager trình quản lý đa ngôn ngữ
     * @param themeManager trình quản lý chủ đề
     * @return dữ liệu biểu mẫu bộ thẻ nếu xác nhận lưu
     */
    public static Optional<DeckFormData> showAndWait(
            Window owner,
            Deck existingDeck,
            LocalizationManager localizationManager,
            ThemeManager themeManager
    ) {
        return createDialog(owner, existingDeck, localizationManager, themeManager).showAndWait();
    }

    /**
     * Tạo đối tượng Dialog chỉnh sửa bộ thẻ với bố cục cột rõ ràng và nút theo chủ đề hiện tại.
     *
     * @param owner cửa sổ cha
     * @param existingDeck bộ thẻ hiện có nếu đang chỉnh sửa (null nếu tạo mới)
     * @param localizationManager trình quản lý đa ngôn ngữ
     * @param themeManager trình quản lý chủ đề
     * @return đối tượng Dialog sẵn sàng hiển thị hoặc kiểm thử bố cục
     */
    public static Dialog<DeckFormData> createDialog(
            Window owner,
            Deck existingDeck,
            LocalizationManager localizationManager,
            ThemeManager themeManager
    ) {
        Dialog<DeckFormData> dialog = new Dialog<>();
        if (owner != null) {
            dialog.initOwner(owner);
        }
        dialog.setTitle(localizationManager.get(
                existingDeck == null ? "decks.dialog.createTitle" : "decks.dialog.editTitle"));
        themeManager.applyTheme(dialog.getDialogPane());
        dialog.getDialogPane().setMinWidth(520);
        dialog.getDialogPane().setPrefWidth(540);

        ButtonType saveButtonType = new ButtonType(
                localizationManager.get("common.save"), ButtonBar.ButtonData.OK_DONE);
        ButtonType cancelButtonType = new ButtonType(
                localizationManager.get("common.cancel"), ButtonBar.ButtonData.CANCEL_CLOSE);
        dialog.getDialogPane().getButtonTypes().setAll(saveButtonType, cancelButtonType);

        TextField titleField = new TextField(existingDeck == null ? "" : existingDeck.title());
        ComboBox<TargetLanguage> targetLangBox = new ComboBox<>();
        targetLangBox.getItems().setAll(TargetLanguage.values());
        targetLangBox.setValue(existingDeck == null ? TargetLanguage.JA : existingDeck.targetLanguage());
        targetLangBox.setMaxWidth(Double.MAX_VALUE);

        ComboBox<DefinitionLanguage> defLangBox = new ComboBox<>();
        defLangBox.getItems().setAll(DefinitionLanguage.values());
        defLangBox.setValue(existingDeck == null ? DefinitionLanguage.VI : existingDeck.definitionLanguage());
        defLangBox.setMaxWidth(Double.MAX_VALUE);

        ComboBox<String> levelBox = new ComboBox<>();
        levelBox.setEditable(true);
        levelBox.getItems().setAll(STANDARD_LEVEL_CODES);
        levelBox.setValue(existingDeck == null ? "N5" : existingDeck.level().code());
        levelBox.setMaxWidth(Double.MAX_VALUE);

        TextArea descriptionArea = new TextArea(
                existingDeck == null || existingDeck.description() == null ? "" : existingDeck.description());
        descriptionArea.setPrefRowCount(3);
        descriptionArea.setWrapText(true);

        Label errorLabel = new Label();
        errorLabel.getStyleClass().add("status-error");
        errorLabel.setVisible(false);
        errorLabel.setManaged(false);
        errorLabel.setWrapText(true);

        GridPane grid = new GridPane();
        grid.setHgap(12);
        grid.setVgap(10);
        grid.setPadding(new Insets(12));
        // Đảm bảo cột nhãn luôn giữ đủ chiều rộng ưu tiên, không bị nén thành dấu ba chấm
        ColumnConstraints labelColumn = new ColumnConstraints();
        labelColumn.setMinWidth(Region.USE_PREF_SIZE);
        labelColumn.setHgrow(Priority.NEVER);
        ColumnConstraints inputColumn = new ColumnConstraints();
        inputColumn.setHgrow(Priority.ALWAYS);
        inputColumn.setFillWidth(true);
        grid.getColumnConstraints().addAll(labelColumn, inputColumn);

        grid.add(new Label(localizationManager.get("decks.field.title")), 0, 0);
        grid.add(titleField, 1, 0);
        grid.add(new Label(localizationManager.get("decks.field.targetLanguage")), 0, 1);
        grid.add(targetLangBox, 1, 1);
        grid.add(new Label(localizationManager.get("decks.field.definitionLanguage")), 0, 2);
        grid.add(defLangBox, 1, 2);
        grid.add(new Label(localizationManager.get("decks.field.level")), 0, 3);
        grid.add(levelBox, 1, 3);
        grid.add(new Label(localizationManager.get("decks.field.description")), 0, 4);
        grid.add(descriptionArea, 1, 4);

        GridPane.setHgrow(titleField, Priority.ALWAYS);
        GridPane.setHgrow(targetLangBox, Priority.ALWAYS);
        GridPane.setHgrow(defLangBox, Priority.ALWAYS);
        GridPane.setHgrow(levelBox, Priority.ALWAYS);
        GridPane.setHgrow(descriptionArea, Priority.ALWAYS);

        VBox content = new VBox(8, grid, errorLabel);
        dialog.getDialogPane().setContent(content);

        // Chặn đóng hộp thoại nếu dữ liệu nhập chưa hợp lệ và áp dụng kiểu nút đồng nhất theo chủ đề
        Button saveButton = (Button) dialog.getDialogPane().lookupButton(saveButtonType);
        saveButton.getStyleClass().add("primary-button");
        Button cancelButton = (Button) dialog.getDialogPane().lookupButton(cancelButtonType);
        cancelButton.getStyleClass().add("secondary-button");
        saveButton.addEventFilter(ActionEvent.ACTION, event -> {
            String title = titleField.getText() == null ? "" : titleField.getText().trim();
            String rawLevel = levelBox.getEditor().getText() == null
                    ? ""
                    : levelBox.getEditor().getText().trim();
            if (title.isBlank() || rawLevel.isBlank()
                    || targetLangBox.getValue() == null || defLangBox.getValue() == null) {
                errorLabel.setText(localizationManager.get("decks.field.title")
                        + " / " + localizationManager.get("decks.field.level")
                        + " - " + localizationManager.get("common.warning"));
                errorLabel.setVisible(true);
                errorLabel.setManaged(true);
                event.consume();
            }
        });

        dialog.setResultConverter(buttonType -> {
            if (buttonType != saveButtonType) {
                return null;
            }
            String title = titleField.getText().trim();
            String levelCode = levelBox.getEditor().getText().trim();
            String desc = descriptionArea.getText() == null || descriptionArea.getText().isBlank()
                    ? null
                    : descriptionArea.getText().trim();
            return new DeckFormData(
                    title,
                    targetLangBox.getValue(),
                    defLangBox.getValue(),
                    ProficiencyLevel.of(levelCode),
                    desc
            );
        });

        return dialog;
    }
}
