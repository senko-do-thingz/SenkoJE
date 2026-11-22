package com.senkoje.presentation.theme;

import com.senkoje.application.service.AppSettingsService;
import java.net.URL;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import javafx.beans.property.ReadOnlyStringProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.scene.Scene;
import javafx.scene.control.DialogPane;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Quản lý giao diện Chế độ tối (mặc định) và Chế độ sáng cho toàn bộ khung cảnh JavaFX.
 * Tự động đồng bộ stylesheet trực tiếp và lưu lựa chọn người dùng vào app_settings.
 */
public class ThemeManager {

    private static final Logger LOGGER = LoggerFactory.getLogger(ThemeManager.class);

    public static final String DARK_THEME_STYLESHEET = "/css/dark-theme.css";
    public static final String LIGHT_THEME_STYLESHEET = "/css/light-theme.css";

    private final AppSettingsService appSettingsService;
    private final StringProperty themeProperty;
    private final List<Scene> registeredScenes = new CopyOnWriteArrayList<>();

    /**
     * Khởi tạo trình quản lý giao diện với trạng thái đọc từ dịch vụ cài đặt cục bộ.
     *
     * @param appSettingsService dịch vụ cài đặt ứng dụng (có thể null khi kiểm thử độc lập)
     */
    public ThemeManager(AppSettingsService appSettingsService) {
        this.appSettingsService = appSettingsService;
        String initialTheme = appSettingsService == null
                ? AppSettingsService.DEFAULT_THEME
                : appSettingsService.getTheme();
        this.themeProperty = new SimpleStringProperty(normalizeTheme(initialTheme));
    }

    /**
     * Khởi tạo trình quản lý giao diện độc lập với chủ đề chỉ định.
     *
     * @param initialTheme tên chủ đề ("dark" hoặc "light")
     */
    public ThemeManager(String initialTheme) {
        this.appSettingsService = null;
        this.themeProperty = new SimpleStringProperty(normalizeTheme(initialTheme));
    }

    /**
     * Trả về thuộc tính chỉ đọc đại diện cho chủ đề đang kích hoạt.
     *
     * @return thuộc tính tên chủ đề ("dark" hoặc "light")
     */
    public ReadOnlyStringProperty themeProperty() {
        return themeProperty;
    }

    /**
     * Lấy tên chủ đề hiện tại ("dark" hoặc "light").
     *
     * @return tên chủ đề hiện tại
     */
    public String getCurrentTheme() {
        return themeProperty.get();
    }

    /**
     * Kiểm tra ứng dụng có đang sử dụng chế độ tối hay không.
     *
     * @return true nếu đang ở chế độ tối
     */
    public boolean isDarkTheme() {
        return AppSettingsService.THEME_DARK.equals(getCurrentTheme());
    }

    /**
     * Đăng ký một Scene để nhận stylesheet hiện tại và tự động cập nhật khi đổi chủ đề.
     *
     * @param scene khung cảnh JavaFX cần áp dụng giao diện
     */
    public void registerScene(Scene scene) {
        Objects.requireNonNull(scene, "scene must not be null");
        if (!registeredScenes.contains(scene)) {
            registeredScenes.add(scene);
        }
        applyThemeToStylesheets(scene.getStylesheets(), getCurrentTheme());
    }

    /**
     * Hủy đăng ký Scene khi cửa sổ phụ đóng lại để tránh rò rỉ tham chiếu.
     *
     * @param scene khung cảnh cần hủy đăng ký
     */
    public void unregisterScene(Scene scene) {
        registeredScenes.remove(scene);
    }

    /**
     * Áp dụng chủ đề hiện tại cho một DialogPane của hộp thoại JavaFX.
     *
     * @param dialogPane vùng nội dung hộp thoại
     */
    public void applyTheme(DialogPane dialogPane) {
        Objects.requireNonNull(dialogPane, "dialogPane must not be null");
        applyThemeToStylesheets(dialogPane.getStylesheets(), getCurrentTheme());
    }

    /**
     * Chuyển đổi chủ đề giao diện trực tiếp cho toàn bộ Scene đã đăng ký và lưu cài đặt.
     *
     * @param theme tên chủ đề mới ("dark" hoặc "light")
     */
    public void setTheme(String theme) {
        String normalized = normalizeTheme(theme);
        if (appSettingsService != null) {
            appSettingsService.setTheme(normalized);
        }
        themeProperty.set(normalized);
        for (Scene scene : registeredScenes) {
            applyThemeToStylesheets(scene.getStylesheets(), normalized);
        }
        LOGGER.info("Switched active theme to {}", normalized);
    }

    /**
     * Trả về đường dẫn tài nguyên CSS tương ứng với tên chủ đề.
     *
     * @param theme tên chủ đề ("dark" hoặc "light")
     * @return đường dẫn tệp CSS trong classpath
     */
    public static String resolveStylesheetPath(String theme) {
        return AppSettingsService.THEME_LIGHT.equals(normalizeTheme(theme))
                ? LIGHT_THEME_STYLESHEET
                : DARK_THEME_STYLESHEET;
    }

    /**
     * Chuẩn hóa tên chủ đề, luôn mặc định về chế độ tối nếu giá trị không hợp lệ.
     *
     * @param theme tên chủ đề đầu vào
     * @return "dark" hoặc "light"
     */
    public static String normalizeTheme(String theme) {
        if (theme != null && AppSettingsService.THEME_LIGHT.equalsIgnoreCase(theme.trim())) {
            return AppSettingsService.THEME_LIGHT;
        }
        return AppSettingsService.THEME_DARK;
    }

    private void applyThemeToStylesheets(List<String> stylesheets, String theme) {
        String darkUrl = resolveExternalForm(DARK_THEME_STYLESHEET);
        String lightUrl = resolveExternalForm(LIGHT_THEME_STYLESHEET);
        if (darkUrl != null) {
            stylesheets.remove(darkUrl);
        }
        if (lightUrl != null) {
            stylesheets.remove(lightUrl);
        }

        String targetPath = resolveStylesheetPath(theme);
        String targetUrl = resolveExternalForm(targetPath);
        if (targetUrl == null) {
            LOGGER.warn("Theme stylesheet not found at {}", targetPath);
            return;
        }
        stylesheets.add(targetUrl);
    }

    private String resolveExternalForm(String resourcePath) {
        URL url = getClass().getResource(resourcePath);
        return url == null ? null : url.toExternalForm();
    }
}
