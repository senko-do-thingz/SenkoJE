package com.senkoje.infrastructure.tts;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Giao diện chạy tiến trình hệ điều hành bằng danh sách tham số an toàn (ProcessBuilder),
 * cho phép tiêm bản giả (fake) trong kiểm thử tự động mà không cần thiết bị âm thanh thật.
 */
public interface ProcessRunner {

    /**
     * Chạy một lệnh hệ điều hành với danh sách tham số tách biệt, biến môi trường tùy chọn và giới hạn thời gian.
     *
     * @param command danh sách tên lệnh và từng tham số riêng lẻ (tuyệt đối không ghép chuỗi shell)
     * @param environment các biến môi trường bổ sung truyền an toàn cho tiến trình con
     * @param timeout thời gian chờ tối đa trước khi ép dừng tiến trình
     * @return kết quả thực thi tiến trình
     */
    ProcessExecutionResult run(List<String> command, Map<String, String> environment, Duration timeout);

    /**
     * Hủy ngay tiến trình đang chạy hiện tại nếu có.
     */
    void cancelActive();
}
