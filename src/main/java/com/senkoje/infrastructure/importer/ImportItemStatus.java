package com.senkoje.infrastructure.importer;

/**
 * Trạng thái đánh giá tính hợp lệ của từng bản ghi dữ liệu trước khi nhập vào cơ sở dữ liệu.
 */
public enum ImportItemStatus {
    VALID,
    DUPLICATE,
    INVALID
}
