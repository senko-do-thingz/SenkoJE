package com.senkoje.infrastructure.tts;

import com.senkoje.application.dto.TtsPreferences;
import com.senkoje.application.service.AppSettingsService;
import com.senkoje.domain.model.TargetLanguage;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Trình điều phối TTS ngoại tuyến chọn đúng nhà cung cấp (System, Bundled hoặc Unsupported)
 * theo cấu hình người dùng lưu trong AppSettingsService.
 */
public class TtsManager {

    private final AppSettingsService appSettingsService;
    private final TtsService systemProvider;
    private final TtsService bundledProvider;
    private final TtsService unsupportedProvider;

    /**
     * Khởi tạo trình điều phối TTS với đầy đủ ba nhà cung cấp theo kiến trúc ứng dụng.
     *
     * @param appSettingsService dịch vụ cấu hình ứng dụng
     * @param systemProvider nhà cung cấp TTS của hệ điều hành
     * @param bundledProvider nhà cung cấp TTS tài nguyên ngoại tuyến tích hợp
     * @param unsupportedProvider nhà cung cấp dự phòng an toàn
     */
    public TtsManager(
            AppSettingsService appSettingsService,
            TtsService systemProvider,
            TtsService bundledProvider,
            TtsService unsupportedProvider
    ) {
        this.appSettingsService = Objects.requireNonNull(
                appSettingsService, "appSettingsService must not be null");
        this.systemProvider = Objects.requireNonNull(systemProvider, "systemProvider must not be null");
        this.bundledProvider = Objects.requireNonNull(bundledProvider, "bundledProvider must not be null");
        this.unsupportedProvider = Objects.requireNonNull(
                unsupportedProvider, "unsupportedProvider must not be null");
    }

    /**
     * Xác định nhà cung cấp TTS hiện đang được kích hoạt theo cấu hình hiện tại.
     *
     * @return nhà cung cấp TTS đang hoạt động (luôn trả về UnsupportedTtsProvider nếu TTS bị tắt)
     */
    public TtsService resolveActiveProvider() {
        TtsPreferences prefs = appSettingsService.getTtsPreferences();
        if (!prefs.enabled()) {
            return unsupportedProvider;
        }
        String providerKey = prefs.provider() == null
                ? ""
                : prefs.provider().trim().toLowerCase(Locale.ROOT);
        return switch (providerKey) {
            case SystemTtsProvider.PROVIDER_ID -> systemProvider;
            case BundledOfflineTtsProvider.PROVIDER_ID -> bundledProvider;
            default -> unsupportedProvider;
        };
    }

    /**
     * Kiểm tra trạng thái sẵn sàng của nhà cung cấp TTS đang được chọn.
     *
     * @return trạng thái sẵn sàng TTS hiện tại
     */
    public TtsAvailability checkActiveAvailability() {
        return resolveActiveProvider().checkAvailability();
    }

    /**
     * Liệt kê giọng đọc khả dụng của nhà cung cấp hiện tại cho một ngôn ngữ.
     *
     * @param language ngôn ngữ mục tiêu (JA hoặc EN)
     * @return danh sách giọng đọc khả dụng
     */
    public List<TtsVoice> listActiveVoices(TargetLanguage language) {
        return resolveActiveProvider().listVoices(language);
    }

    /**
     * Liệt kê giọng đọc khả dụng theo mã nhà cung cấp chỉ định (dùng khi người dùng đổi bộ chọn trong Cài đặt).
     *
     * @param providerId mã nhà cung cấp ("system", "bundled", "unsupported")
     * @param language ngôn ngữ mục tiêu (JA hoặc EN)
     * @return danh sách giọng đọc khả dụng
     */
    public List<TtsVoice> listVoicesForProvider(String providerId, TargetLanguage language) {
        Objects.requireNonNull(language, "language must not be null");
        String normalized = providerId == null ? "" : providerId.trim().toLowerCase(Locale.ROOT);
        TtsService provider = switch (normalized) {
            case SystemTtsProvider.PROVIDER_ID -> systemProvider;
            case BundledOfflineTtsProvider.PROVIDER_ID -> bundledProvider;
            default -> unsupportedProvider;
        };
        return provider.listVoices(language);
    }

    /**
     * Phát âm một đoạn văn bản theo ngôn ngữ đích sử dụng tốc độ đọc và giọng ưu tiên trong cấu hình.
     *
     * @param text nội dung văn bản cần đọc
     * @param language ngôn ngữ đích của văn bản
     * @return kết quả phát âm có cấu trúc
     */
    public TtsPlaybackResult speak(String text, TargetLanguage language) {
        Objects.requireNonNull(language, "language must not be null");
        TtsPreferences prefs = appSettingsService.getTtsPreferences();
        if (!prefs.enabled()) {
            return unsupportedProvider.speak(new TtsPlaybackRequest(text, language, prefs.speechRate(), ""));
        }
        String preferredVoice = language == TargetLanguage.JA ? prefs.japaneseVoice() : prefs.englishVoice();
        TtsService provider = resolveActiveProvider();
        return provider.speak(new TtsPlaybackRequest(text, language, prefs.speechRate(), preferredVoice));
    }

    /**
     * Dừng mọi tiến trình phát âm đang chạy trên tất cả nhà cung cấp.
     */
    public void stop() {
        systemProvider.stop();
        bundledProvider.stop();
        unsupportedProvider.stop();
    }
}
