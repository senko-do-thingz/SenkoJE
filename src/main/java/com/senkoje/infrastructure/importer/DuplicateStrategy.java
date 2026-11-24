package com.senkoje.infrastructure.importer;

/**
 * Chiến lược xử lý khi phát hiện từ vựng đã tồn tại trong bộ thẻ mục tiêu.
 */
public enum DuplicateStrategy {
    SKIP,
    OVERWRITE,
    IMPORT_AS_DUPLICATE
}
