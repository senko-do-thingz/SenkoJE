package com.senkoje.domain.service;

import java.util.List;

/**
 * Ngoại lệ miền được ném ra khi thực thể hoặc cấu hình không đáp ứng các quy tắc hợp lệ.
 */
public class DomainValidationException extends IllegalArgumentException {

    private final List<String> errors;

    /**
     * Khởi tạo ngoại lệ kiểm tra hợp lệ với danh sách chi tiết các lỗi phát hiện được.
     *
     * @param errors danh sách thông báo lỗi không rỗng
     */
    public DomainValidationException(List<String> errors) {
        super(String.join("; ", errors));
        this.errors = List.copyOf(errors);
    }

    /**
     * Trả về danh sách bất biến chứa toàn bộ thông báo lỗi kiểm tra hợp lệ.
     *
     * @return danh sách thông báo lỗi
     */
    public List<String> getErrors() {
        return errors;
    }
}
