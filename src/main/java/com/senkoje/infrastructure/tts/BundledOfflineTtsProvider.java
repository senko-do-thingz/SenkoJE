package com.senkoje.infrastructure.tts;

import com.senkoje.domain.model.TargetLanguage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Properties;
import java.util.stream.Stream;

/**
 * Nhà cung cấp TTS ngoại tuyến dựa trên gói giọng đọc cục bộ (không lưu tệp nhị phân lớn trong Git).
 * Kiểm tra thư mục giọng đọc cục bộ và báo cáo rõ ràng trạng thái không khả dụng nếu chưa cài gói giọng đọc.
 */
public class BundledOfflineTtsProvider implements TtsService {

    public static final String PROVIDER_ID = "bundled";

    private final Path voicesDirectory;

    /**
     * Khởi tạo nhà cung cấp TTS ngoại tuyến tích hợp trỏ tới thư mục chứa tài nguyên giọng đọc cục bộ.
     *
     * @param voicesDirectory đường dẫn thư mục chứa các tệp mô tả giọng đọc cục bộ
     */
    public BundledOfflineTtsProvider(Path voicesDirectory) {
        this.voicesDirectory = Objects.requireNonNull(voicesDirectory, "voicesDirectory must not be null");
    }

    @Override
    public String getProviderId() {
        return PROVIDER_ID;
    }

    @Override
    public TtsAvailability checkAvailability() {
        List<TtsVoice> detected = scanBundledVoices();
        if (detected.isEmpty()) {
            return new TtsAvailability(
                    false,
                    PROVIDER_ID,
                    "tts.status.bundledMissing",
                    "No bundled offline voice assets found in " + voicesDirectory
            );
        }
        return new TtsAvailability(
                true,
                PROVIDER_ID,
                "tts.status.bundledReady",
                "Detected " + detected.size() + " bundled voice asset(s)"
        );
    }

    @Override
    public List<TtsVoice> listVoices(TargetLanguage language) {
        Objects.requireNonNull(language, "language must not be null");
        return scanBundledVoices().stream()
                .filter(voice -> voice.language() == language)
                .toList();
    }

    @Override
    public TtsPlaybackResult speak(TtsPlaybackRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        if (request.text().isBlank()) {
            return new TtsPlaybackResult(false, PROVIDER_ID, "tts.status.emptyText", "");
        }
        TtsAvailability availability = checkAvailability();
        if (!availability.available()) {
            return new TtsPlaybackResult(
                    false,
                    PROVIDER_ID,
                    "tts.status.bundledMissing",
                    availability.detailMessage()
            );
        }
        // Chưa tích hợp engine tổng hợp nhị phân độc lập nên báo rõ để tránh thất bại âm thầm
        return new TtsPlaybackResult(
                false,
                PROVIDER_ID,
                "tts.status.bundledEngineUnavailable",
                "Bundled offline synthesis runtime is not installed"
        );
    }

    @Override
    public void stop() {
        // Không có tiến trình phát âm nền cần hủy
    }

    private List<TtsVoice> scanBundledVoices() {
        if (!Files.isDirectory(voicesDirectory)) {
            return List.of();
        }
        List<TtsVoice> voices = new ArrayList<>();
        try (Stream<Path> stream = Files.list(voicesDirectory)) {
            List<Path> manifests = stream
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".voice.properties"))
                    .toList();
            for (Path manifest : manifests) {
                parseManifest(manifest, voices);
            }
        } catch (IOException ignored) {
            return List.of();
        }
        return List.copyOf(voices);
    }

    private void parseManifest(Path manifestPath, List<TtsVoice> voices) {
        Properties props = new Properties();
        try (InputStream in = Files.newInputStream(manifestPath)) {
            props.load(in);
            String id = props.getProperty("id", "").trim();
            String displayName = props.getProperty("name", id).trim();
            String lang = props.getProperty("language", "").trim().toUpperCase(Locale.ROOT);
            if (!id.isEmpty() && ("JA".equals(lang) || "EN".equals(lang))) {
                voices.add(new TtsVoice(id, displayName.isEmpty() ? id : displayName, TargetLanguage.valueOf(lang)));
            }
        } catch (IOException | IllegalArgumentException ignored) {
            // Bỏ qua tệp mô tả giọng đọc bị hỏng mà không làm lỗi ứng dụng
        }
    }
}
