package com.senkoje.application.service;

import com.senkoje.application.dto.TtsPreferences;
import com.senkoje.application.dto.WindowPreferences;
import com.senkoje.domain.model.AppSetting;
import com.senkoje.domain.repository.AppSettingRepository;
import com.senkoje.domain.service.DomainValidationException;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Dịch vụ ứng dụng quản lý cấu hình giao diện, ngôn ngữ, TTS và cửa sổ lưu cục bộ trong app_settings.
 */
public class AppSettingsService {

    public static final String KEY_THEME = "ui.theme";
    public static final String KEY_LANGUAGE = "ui.language";
    public static final String KEY_TTS_ENABLED = "tts.enabled";
    public static final String KEY_TTS_PROVIDER = "tts.provider";
    public static final String KEY_TTS_SPEECH_RATE = "tts.speech_rate";
    public static final String KEY_TTS_JA_VOICE = "tts.ja_voice";
    public static final String KEY_TTS_EN_VOICE = "tts.en_voice";
    public static final String KEY_WINDOW_WIDTH = "window.width";
    public static final String KEY_WINDOW_HEIGHT = "window.height";
    public static final String KEY_WINDOW_MAXIMIZED = "window.maximized";

    public static final String THEME_DARK = "dark";
    public static final String THEME_LIGHT = "light";
    public static final String DEFAULT_THEME = THEME_DARK;
    public static final String DEFAULT_LANGUAGE = "vi";

    public static final boolean DEFAULT_TTS_ENABLED = true;
    public static final String DEFAULT_TTS_PROVIDER = "system";
    public static final double DEFAULT_TTS_SPEECH_RATE = 1.0;
    public static final double MIN_TTS_SPEECH_RATE = 0.5;
    public static final double MAX_TTS_SPEECH_RATE = 2.0;

    public static final int DEFAULT_WINDOW_WIDTH = 1200;
    public static final int DEFAULT_WINDOW_HEIGHT = 800;
    public static final int MIN_WINDOW_WIDTH = 640;
    public static final int MIN_WINDOW_HEIGHT = 480;
    public static final boolean DEFAULT_WINDOW_MAXIMIZED = false;

    private static final Set<String> SUPPORTED_THEMES = Set.of(THEME_DARK, THEME_LIGHT);
    private static final Set<String> SUPPORTED_LANGUAGES = Set.of("vi", "en");

    private final AppSettingRepository appSettingRepository;
    private final Clock clock;

    /**
     * Khởi tạo dịch vụ cấu hình ứng dụng sử dụng đồng hồ UTC mặc định của hệ thống.
     *
     * @param appSettingRepository kho lưu trữ bảng app_settings
     */
    public AppSettingsService(AppSettingRepository appSettingRepository) {
        this(appSettingRepository, Clock.systemUTC());
    }

    /**
     * Khởi tạo dịch vụ cấu hình ứng dụng với đồng hồ được tiêm vào để kiểm thử tất định.
     *
     * @param appSettingRepository kho lưu trữ bảng app_settings
     * @param clock nguồn thời gian
     */
    public AppSettingsService(AppSettingRepository appSettingRepository, Clock clock) {
        this.appSettingRepository = Objects.requireNonNull(
                appSettingRepository, "appSettingRepository must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    /**
     * Đọc giao diện hiện tại, luôn mặc định là chế độ tối (dark) khi chưa có cấu hình.
     *
     * @return tên giao diện ("dark" hoặc "light")
     */
    public String getTheme() {
        return appSettingRepository.findByKey(KEY_THEME)
                .map(AppSetting::settingValue)
                .filter(SUPPORTED_THEMES::contains)
                .orElse(DEFAULT_THEME);
    }

    /**
     * Lưu lựa chọn giao diện người dùng (chỉ chấp nhận "dark" hoặc "light").
     *
     * @param theme tên giao diện mới
     */
    public void setTheme(String theme) {
        if (theme == null || theme.isBlank()) {
            throw new DomainValidationException(List.of("Theme must not be blank"));
        }
        String normalized = theme.trim().toLowerCase(Locale.ROOT);
        if (!SUPPORTED_THEMES.contains(normalized)) {
            throw new DomainValidationException(List.of("Unsupported theme: " + theme));
        }
        saveValue(KEY_THEME, normalized);
    }

    /**
     * Đọc mã ngôn ngữ giao diện hiện tại, mặc định là tiếng Việt ("vi").
     *
     * @return mã ngôn ngữ ("vi" hoặc "en")
     */
    public String getInterfaceLanguage() {
        return appSettingRepository.findByKey(KEY_LANGUAGE)
                .map(AppSetting::settingValue)
                .filter(SUPPORTED_LANGUAGES::contains)
                .orElse(DEFAULT_LANGUAGE);
    }

    /**
     * Lưu lựa chọn ngôn ngữ giao diện người dùng (chỉ chấp nhận "vi" hoặc "en").
     *
     * @param languageCode mã ngôn ngữ mới
     */
    public void setInterfaceLanguage(String languageCode) {
        if (languageCode == null || languageCode.isBlank()) {
            throw new DomainValidationException(List.of("Interface language must not be blank"));
        }
        String normalized = languageCode.trim().toLowerCase(Locale.ROOT);
        if (!SUPPORTED_LANGUAGES.contains(normalized)) {
            throw new DomainValidationException(List.of("Unsupported interface language: " + languageCode));
        }
        saveValue(KEY_LANGUAGE, normalized);
    }

    /**
     * Đọc toàn bộ tùy chọn TTS ngoại tuyến kèm giá trị mặc định an toàn.
     *
     * @return đối tượng TtsPreferences
     */
    public TtsPreferences getTtsPreferences() {
        boolean enabled = parseBoolean(KEY_TTS_ENABLED, DEFAULT_TTS_ENABLED);
        String provider = appSettingRepository.findByKey(KEY_TTS_PROVIDER)
                .map(AppSetting::settingValue)
                .filter(value -> !value.isBlank())
                .orElse(DEFAULT_TTS_PROVIDER);
        double speechRate = parseDouble(KEY_TTS_SPEECH_RATE, DEFAULT_TTS_SPEECH_RATE);
        String jaVoice = appSettingRepository.findByKey(KEY_TTS_JA_VOICE)
                .map(AppSetting::settingValue)
                .orElse("");
        String enVoice = appSettingRepository.findByKey(KEY_TTS_EN_VOICE)
                .map(AppSetting::settingValue)
                .orElse("");
        return new TtsPreferences(enabled, provider, speechRate, jaVoice, enVoice);
    }

    /**
     * Kiểm tra hợp lệ và lưu toàn bộ tùy chọn TTS ngoại tuyến.
     *
     * @param preferences cấu hình TTS cần lưu
     */
    public void saveTtsPreferences(TtsPreferences preferences) {
        if (preferences == null) {
            throw new DomainValidationException(List.of("TtsPreferences must not be null"));
        }
        if (preferences.provider() == null || preferences.provider().isBlank()) {
            throw new DomainValidationException(List.of("TTS provider must not be blank"));
        }
        if (!Double.isFinite(preferences.speechRate())
                || preferences.speechRate() < MIN_TTS_SPEECH_RATE
                || preferences.speechRate() > MAX_TTS_SPEECH_RATE) {
            throw new DomainValidationException(List.of(
                    "TTS speech rate must be between " + MIN_TTS_SPEECH_RATE + " and " + MAX_TTS_SPEECH_RATE));
        }

        Instant now = clock.instant();
        appSettingRepository.save(new AppSetting(KEY_TTS_ENABLED, Boolean.toString(preferences.enabled()), now));
        appSettingRepository.save(new AppSetting(KEY_TTS_PROVIDER, preferences.provider().trim(), now));
        appSettingRepository.save(new AppSetting(KEY_TTS_SPEECH_RATE, Double.toString(preferences.speechRate()), now));
        appSettingRepository.save(new AppSetting(KEY_TTS_JA_VOICE,
                preferences.japaneseVoice() == null ? "" : preferences.japaneseVoice().trim(), now));
        appSettingRepository.save(new AppSetting(KEY_TTS_EN_VOICE,
                preferences.englishVoice() == null ? "" : preferences.englishVoice().trim(), now));
    }

    /**
     * Đọc tùy chọn kích thước và trạng thái cửa sổ ứng dụng.
     *
     * @return đối tượng WindowPreferences
     */
    public WindowPreferences getWindowPreferences() {
        int width = parseInt(KEY_WINDOW_WIDTH, DEFAULT_WINDOW_WIDTH);
        int height = parseInt(KEY_WINDOW_HEIGHT, DEFAULT_WINDOW_HEIGHT);
        boolean maximized = parseBoolean(KEY_WINDOW_MAXIMIZED, DEFAULT_WINDOW_MAXIMIZED);
        return new WindowPreferences(width, height, maximized);
    }

    /**
     * Kiểm tra kích thước tối thiểu và lưu tùy chọn cửa sổ ứng dụng.
     *
     * @param preferences tùy chọn cửa sổ cần lưu
     */
    public void saveWindowPreferences(WindowPreferences preferences) {
        if (preferences == null) {
            throw new DomainValidationException(List.of("WindowPreferences must not be null"));
        }
        if (preferences.width() < MIN_WINDOW_WIDTH || preferences.height() < MIN_WINDOW_HEIGHT) {
            throw new DomainValidationException(List.of(
                    "Window dimensions must be at least " + MIN_WINDOW_WIDTH + "x" + MIN_WINDOW_HEIGHT));
        }
        Instant now = clock.instant();
        appSettingRepository.save(new AppSetting(KEY_WINDOW_WIDTH, Integer.toString(preferences.width()), now));
        appSettingRepository.save(new AppSetting(KEY_WINDOW_HEIGHT, Integer.toString(preferences.height()), now));
        appSettingRepository.save(new AppSetting(KEY_WINDOW_MAXIMIZED, Boolean.toString(preferences.maximized()), now));
    }

    /**
     * Đọc giá trị thô của một khóa cấu hình bất kỳ trong app_settings.
     *
     * @param settingKey khóa cấu hình
     * @return giá trị cấu hình nếu tồn tại
     */
    public Optional<String> getRawSetting(String settingKey) {
        if (settingKey == null || settingKey.isBlank()) {
            throw new DomainValidationException(List.of("Setting key must not be blank"));
        }
        return appSettingRepository.findByKey(settingKey.trim()).map(AppSetting::settingValue);
    }

    /**
     * Lưu giá trị thô cho một khóa cấu hình bất kỳ sau khi kiểm tra khóa và giá trị không null.
     *
     * @param settingKey khóa cấu hình
     * @param settingValue giá trị chuỗi cần lưu
     */
    public void setRawSetting(String settingKey, String settingValue) {
        if (settingKey == null || settingKey.isBlank()) {
            throw new DomainValidationException(List.of("Setting key must not be blank"));
        }
        if (settingValue == null) {
            throw new DomainValidationException(List.of("Setting value must not be null"));
        }
        saveValue(settingKey.trim(), settingValue);
    }

    private void saveValue(String key, String value) {
        appSettingRepository.save(new AppSetting(key, value, clock.instant()));
    }

    private boolean parseBoolean(String key, boolean defaultValue) {
        return appSettingRepository.findByKey(key)
                .map(AppSetting::settingValue)
                .map(Boolean::parseBoolean)
                .orElse(defaultValue);
    }

    private int parseInt(String key, int defaultValue) {
        return appSettingRepository.findByKey(key)
                .map(AppSetting::settingValue)
                .map(value -> {
                    try {
                        return Integer.parseInt(value);
                    } catch (NumberFormatException exception) {
                        return defaultValue;
                    }
                })
                .orElse(defaultValue);
    }

    private double parseDouble(String key, double defaultValue) {
        return appSettingRepository.findByKey(key)
                .map(AppSetting::settingValue)
                .map(value -> {
                    try {
                        return Double.parseDouble(value);
                    } catch (NumberFormatException exception) {
                        return defaultValue;
                    }
                })
                .orElse(defaultValue);
    }
}
