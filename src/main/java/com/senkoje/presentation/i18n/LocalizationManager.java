package com.senkoje.presentation.i18n;

import com.senkoje.application.service.AppSettingsService;
import java.text.MessageFormat;
import java.util.Locale;
import java.util.MissingResourceException;
import java.util.Objects;
import java.util.ResourceBundle;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.StringBinding;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.scene.control.Labeled;
import javafx.scene.control.TableColumnBase;
import javafx.scene.control.TextInputControl;
import javafx.scene.control.Tooltip;

/**
 * Quản lý đa ngôn ngữ động cho giao diện JavaFX của SenkoJE (tiếng Việt và tiếng Anh).
 * Hỗ trợ chuyển đổi ngôn ngữ trực tiếp mà không cần khởi động lại ứng dụng.
 */
public class LocalizationManager {

    public static final String BUNDLE_BASE_NAME = "i18n.messages";
    public static final Locale LOCALE_VI = Locale.of("vi");
    public static final Locale LOCALE_EN = Locale.of("en");

    private final AppSettingsService appSettingsService;
    private final ObjectProperty<Locale> localeProperty;

    /**
     * Khởi tạo trình quản lý đa ngôn ngữ với ngôn ngữ mặc định từ cài đặt ứng dụng.
     *
     * @param appSettingsService dịch vụ cài đặt cục bộ (có thể null khi kiểm thử độc lập)
     */
    public LocalizationManager(AppSettingsService appSettingsService) {
        this.appSettingsService = appSettingsService;
        String initialLanguage = appSettingsService == null
                ? AppSettingsService.DEFAULT_LANGUAGE
                : appSettingsService.getInterfaceLanguage();
        this.localeProperty = new SimpleObjectProperty<>(resolveLocale(initialLanguage));
    }

    /**
     * Khởi tạo trình quản lý đa ngôn ngữ độc lập không gắn với cơ sở dữ liệu.
     *
     * @param initialLanguage mã ngôn ngữ khởi tạo ("vi" hoặc "en")
     */
    public LocalizationManager(String initialLanguage) {
        this.appSettingsService = null;
        this.localeProperty = new SimpleObjectProperty<>(resolveLocale(initialLanguage));
    }

    /**
     * Trả về thuộc tính chỉ đọc của Locale hiện tại để các thành phần UI lắng nghe thay đổi trực tiếp.
     *
     * @return thuộc tính Locale chỉ đọc
     */
    public ReadOnlyObjectProperty<Locale> localeProperty() {
        return localeProperty;
    }

    /**
     * Lấy Locale đang được kích hoạt.
     *
     * @return Locale hiện tại
     */
    public Locale getLocale() {
        return localeProperty.get();
    }

    /**
     * Lấy mã ngôn ngữ giao diện đang được kích hoạt ("vi" hoặc "en").
     *
     * @return mã ngôn ngữ hiện tại
     */
    public String getCurrentLanguage() {
        return getLocale().getLanguage();
    }

    /**
     * Chuyển đổi ngôn ngữ giao diện trực tiếp và lưu vào app_settings nếu có dịch vụ cài đặt.
     *
     * @param languageCode mã ngôn ngữ mới ("vi" hoặc "en")
     */
    public void setLanguage(String languageCode) {
        Locale targetLocale = resolveLocale(languageCode);
        if (appSettingsService != null) {
            appSettingsService.setInterfaceLanguage(targetLocale.getLanguage());
        }
        localeProperty.set(targetLocale);
    }

    /**
     * Lấy gói ResourceBundle tương ứng với ngôn ngữ hiện tại.
     *
     * @return ResourceBundle đang có hiệu lực
     */
    public ResourceBundle getBundle() {
        return ResourceBundle.getBundle(BUNDLE_BASE_NAME, getLocale());
    }

    /**
     * Tra cứu chuỗi bản địa hóa theo khóa và định dạng tham số nếu có.
     *
     * @param key khóa bản địa hóa
     * @param args tham số định dạng tùy chọn
     * @return chuỗi đã bản địa hóa
     */
    public String get(String key, Object... args) {
        Objects.requireNonNull(key, "key must not be null");
        String pattern;
        try {
            pattern = getBundle().getString(key);
        } catch (MissingResourceException exception) {
            // Trả về chính khóa khi thiếu bản dịch để tránh làm sập giao diện
            return key;
        }
        if (args == null || args.length == 0) {
            return pattern;
        }
        return new MessageFormat(pattern, getLocale()).format(args);
    }

    /**
     * Tạo liên kết chuỗi động tự động cập nhật khi người dùng chuyển ngôn ngữ giao diện.
     *
     * @param key khóa bản địa hóa
     * @param args tham số định dạng tùy chọn
     * @return đối tượng StringBinding phản ứng theo localeProperty
     */
    public StringBinding createStringBinding(String key, Object... args) {
        return Bindings.createStringBinding(() -> get(key, args), localeProperty);
    }

    /**
     * Ràng buộc thuộc tính văn bản của một thành phần Labeled với khóa bản địa hóa.
     *
     * @param labeled nút, nhãn hoặc điều khiển có văn bản
     * @param key khóa bản địa hóa
     * @param args tham số định dạng tùy chọn
     */
    public void bind(Labeled labeled, String key, Object... args) {
        Objects.requireNonNull(labeled, "labeled must not be null");
        labeled.textProperty().bind(createStringBinding(key, args));
    }

    /**
     * Ràng buộc văn bản gợi ý (promptText) của ô nhập liệu với khóa bản địa hóa.
     *
     * @param inputControl ô nhập liệu văn bản
     * @param key khóa bản địa hóa
     */
    public void bindPrompt(TextInputControl inputControl, String key) {
        Objects.requireNonNull(inputControl, "inputControl must not be null");
        inputControl.promptTextProperty().bind(createStringBinding(key));
    }

    /**
     * Ràng buộc tiêu đề cột bảng với khóa bản địa hóa.
     *
     * @param column cột bảng JavaFX
     * @param key khóa bản địa hóa
     */
    public void bindColumn(TableColumnBase<?, ?> column, String key) {
        Objects.requireNonNull(column, "column must not be null");
        column.textProperty().bind(createStringBinding(key));
    }

    /**
     * Ràng buộc nội dung Tooltip với khóa bản địa hóa.
     *
     * @param tooltip chú giải nổi
     * @param key khóa bản địa hóa
     * @param args tham số định dạng tùy chọn
     */
    public void bindTooltip(Tooltip tooltip, String key, Object... args) {
        Objects.requireNonNull(tooltip, "tooltip must not be null");
        tooltip.textProperty().bind(createStringBinding(key, args));
    }

    /**
     * Chuẩn hóa mã ngôn ngữ đầu vào thành Locale được hỗ trợ ("vi" hoặc "en").
     *
     * @param languageCode mã ngôn ngữ cần chuẩn hóa
     * @return Locale hợp lệ
     */
    public static Locale resolveLocale(String languageCode) {
        if (languageCode != null && "en".equalsIgnoreCase(languageCode.trim())) {
            return LOCALE_EN;
        }
        return LOCALE_VI;
    }
}
