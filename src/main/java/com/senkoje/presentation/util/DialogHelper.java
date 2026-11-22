package com.senkoje.presentation.util;

import com.senkoje.presentation.i18n.LocalizationManager;
import com.senkoje.presentation.theme.ThemeManager;
import java.util.Optional;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.TextInputDialog;
import javafx.stage.Window;

/**
 * Tiện ích tạo các hộp thoại xác nhận, thông báo lỗi, thông tin và nhập văn bản
 * đã được bản địa hóa và đồng bộ chủ đề sáng/tối.
 */
public final class DialogHelper {

    private DialogHelper() {
    }

    /**
     * Hiển thị hộp thoại xác nhận hành động với hai nút bản địa hóa.
     *
     * @param owner cửa sổ cha (có thể null)
     * @param title tiêu đề hộp thoại
     * @param message nội dung xác nhận
     * @param localizationManager trình quản lý đa ngôn ngữ
     * @param themeManager trình quản lý chủ đề
     * @return true nếu người dùng xác nhận đồng ý
     */
    public static boolean showConfirmation(
            Window owner,
            String title,
            String message,
            LocalizationManager localizationManager,
            ThemeManager themeManager
    ) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        if (owner != null) {
            alert.initOwner(owner);
        }
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);

        ButtonType confirmButton = new ButtonType(
                localizationManager.get("common.confirm"), ButtonBar.ButtonData.OK_DONE);
        ButtonType cancelButton = new ButtonType(
                localizationManager.get("common.cancel"), ButtonBar.ButtonData.CANCEL_CLOSE);
        alert.getButtonTypes().setAll(confirmButton, cancelButton);
        themeManager.applyTheme(alert.getDialogPane());

        Optional<ButtonType> result = alert.showAndWait();
        return result.isPresent() && result.get() == confirmButton;
    }

    /**
     * Hiển thị hộp thoại thông báo lỗi theo chủ đề hiện tại.
     *
     * @param owner cửa sổ cha (có thể null)
     * @param message thông điệp lỗi
     * @param localizationManager trình quản lý đa ngôn ngữ
     * @param themeManager trình quản lý chủ đề
     */
    public static void showError(
            Window owner,
            String message,
            LocalizationManager localizationManager,
            ThemeManager themeManager
    ) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        if (owner != null) {
            alert.initOwner(owner);
        }
        alert.setTitle(localizationManager.get("common.error"));
        alert.setHeaderText(null);
        alert.setContentText(message);
        themeManager.applyTheme(alert.getDialogPane());
        alert.showAndWait();
    }

    /**
     * Hiển thị hộp thoại thông tin theo chủ đề hiện tại.
     *
     * @param owner cửa sổ cha (có thể null)
     * @param title tiêu đề thông tin
     * @param message nội dung thông tin
     * @param themeManager trình quản lý chủ đề
     */
    public static void showInfo(
            Window owner,
            String title,
            String message,
            ThemeManager themeManager
    ) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        if (owner != null) {
            alert.initOwner(owner);
        }
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        themeManager.applyTheme(alert.getDialogPane());
        alert.showAndWait();
    }

    /**
     * Hiển thị hộp thoại nhập một dòng văn bản (ví dụ đổi tên hoặc đặt tên bản sao bộ thẻ).
     *
     * @param owner cửa sổ cha (có thể null)
     * @param title tiêu đề hộp thoại
     * @param prompt nhãn trường nhập liệu
     * @param initialValue giá trị ban đầu
     * @param localizationManager trình quản lý đa ngôn ngữ
     * @param themeManager trình quản lý chủ đề
     * @return chuỗi người dùng đã nhập nếu xác nhận
     */
    public static Optional<String> showTextInput(
            Window owner,
            String title,
            String prompt,
            String initialValue,
            LocalizationManager localizationManager,
            ThemeManager themeManager
    ) {
        TextInputDialog dialog = new TextInputDialog(initialValue == null ? "" : initialValue);
        if (owner != null) {
            dialog.initOwner(owner);
        }
        dialog.setTitle(title);
        dialog.setHeaderText(null);
        dialog.setContentText(prompt);
        themeManager.applyTheme(dialog.getDialogPane());
        return dialog.showAndWait().map(String::trim).filter(value -> !value.isBlank());
    }
}
