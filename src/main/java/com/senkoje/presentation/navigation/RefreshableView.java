package com.senkoje.presentation.navigation;

/**
 * Giao diện cho các màn hình con có khả năng tải lại dữ liệu bất đồng bộ khi người dùng điều hướng tới.
 */
public interface RefreshableView {

    /**
     * Kích hoạt làm mới dữ liệu của màn hình trên luồng nền.
     */
    void refresh();
}
