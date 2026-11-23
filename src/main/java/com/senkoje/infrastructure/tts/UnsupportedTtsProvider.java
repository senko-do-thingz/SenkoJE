package com.senkoje.infrastructure.tts;

import com.senkoje.domain.model.TargetLanguage;
import java.util.List;

/**
 * Nhà cung cấp TTS dự phòng an toàn khi người dùng tắt TTS hoặc môi trường không hỗ trợ tổng hợp giọng nói.
 */
public class UnsupportedTtsProvider implements TtsService {

    public static final String PROVIDER_ID = "unsupported";

    @Override
    public String getProviderId() {
        return PROVIDER_ID;
    }

    @Override
    public TtsAvailability checkAvailability() {
        return new TtsAvailability(
                false,
                PROVIDER_ID,
                "tts.status.disabled",
                "Offline TTS is disabled or unsupported"
        );
    }

    @Override
    public List<TtsVoice> listVoices(TargetLanguage language) {
        return List.of();
    }

    @Override
    public TtsPlaybackResult speak(TtsPlaybackRequest request) {
        return new TtsPlaybackResult(
                false,
                PROVIDER_ID,
                "tts.status.disabled",
                "Offline TTS is disabled or unsupported"
        );
    }

    @Override
    public void stop() {
        // Không có tài nguyên âm thanh nào cần giải phóng
    }
}
