package com.senkoje.infrastructure.tts;

/**
 * Kết quả thực thi một tiến trình hệ điều hành do ProcessRunner trả về.
 *
 * @param exitCode mã thoát tiến trình (-1 nếu không thể khởi chạy, hết giờ hoặc bị hủy)
 * @param stdout nội dung đầu ra chuẩn
 * @param stderr nội dung lỗi chuẩn
 * @param timedOut true nếu tiến trình vượt quá giới hạn thời gian và bị ngắt
 * @param launchFailed true nếu không tìm thấy tệp thực thi hoặc lỗi khởi chạy tiến trình
 * @param cancelled true nếu tiến trình bị hủy chủ động thông qua cancelActive()
 */
public record ProcessExecutionResult(
        int exitCode,
        String stdout,
        String stderr,
        boolean timedOut,
        boolean launchFailed,
        boolean cancelled
) {

    /**
     * Hàm khởi tạo rút gọn cho các kết quả không bị hủy chủ động.
     *
     * @param exitCode mã thoát tiến trình
     * @param stdout nội dung đầu ra chuẩn
     * @param stderr nội dung lỗi chuẩn
     * @param timedOut cờ hết thời gian chờ
     * @param launchFailed cờ lỗi khởi chạy
     */
    public ProcessExecutionResult(
            int exitCode,
            String stdout,
            String stderr,
            boolean timedOut,
            boolean launchFailed
    ) {
        this(exitCode, stdout, stderr, timedOut, launchFailed, false);
    }

    /**
     * Kiểm tra tiến trình có kết thúc thành công hay không.
     *
     * @return true nếu khởi chạy thành công, không hết giờ, không bị hủy và mã thoát bằng 0
     */
    public boolean isSuccess() {
        return !launchFailed && !timedOut && !cancelled && exitCode == 0;
    }
}
