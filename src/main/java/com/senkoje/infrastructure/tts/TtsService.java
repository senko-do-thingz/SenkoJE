package com.senkoje.infrastructure.tts;

import com.senkoje.domain.model.TargetLanguage;
import java.util.List;

/**
 * Giao diện trừu tượng cho các nhà cung cấp đọc văn bản thành giọng nói ngoại tuyến (TTS) của SenkoJE.
 */
public interface TtsService {

    /**
     * Trả về mã định danh ổn định của nhà cung cấp TTS ("system", "bundled", "unsupported").
     *
     * @return mã định danh nhà cung cấp
     */
    String getProviderId();

    /**
     * Kiểm tra nhà cung cấp TTS có sẵn sàng hoạt động ngoại tuyến trên máy hiện tại hay không.
     *
     * @return thông tin trạng thái sẵn sàng
     */
    TtsAvailability checkAvailability();

    /**
     * Liệt kê các giọng đọc khả dụng cho một ngôn ngữ mục tiêu.
     *
     * @param language ngôn ngữ cần lọc giọng đọc (JA hoặc EN)
     * @return danh sách giọng đọc khả dụng (rỗng nếu không có hoặc không hỗ trợ)
     */
    List<TtsVoice> listVoices(TargetLanguage language);

    /**
     * Thực hiện phát âm văn bản ngoại tuyến theo yêu cầu.
     *
     * @param request yêu cầu phát âm gồm nội dung, ngôn ngữ, tốc độ và giọng ưu tiên
     * @return kết quả phát âm có cấu trúc (không ném ngoại lệ thô ra UI)
     */
    TtsPlaybackResult speak(TtsPlaybackRequest request);

    /**
     * Hủy bỏ ngay tiến trình phát âm đang chạy nếu có.
     */
    void stop();
}
