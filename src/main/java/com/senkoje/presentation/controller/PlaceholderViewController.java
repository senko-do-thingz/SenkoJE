package com.senkoje.presentation.controller;

import com.senkoje.presentation.i18n.LocalizationManager;
import java.util.Objects;
import javafx.fxml.FXML;
import javafx.scene.control.Label;

/**
 * Bộ điều khiển màn hình giữ chỗ bản địa hóa cho các tính năng thuộc các giai đoạn tiếp theo (Phase 4 - 6).
 */
public class PlaceholderViewController {

    private final LocalizationManager localizationManager;
    private final String titleKey;
    private final String subtitleKey;

    @FXML
    private Label titleLabel;

    @FXML
    private Label subtitleLabel;

    @FXML
    private Label noteLabel;

    /**
     * Khởi tạo màn hình giữ chỗ với khóa bản địa hóa cho tiêu đề và mô tả.
     *
     * @param localizationManager trình quản lý đa ngôn ngữ
     * @param titleKey khóa tiêu đề màn hình
     * @param subtitleKey khóa mô tả màn hình
     */
    public PlaceholderViewController(
            LocalizationManager localizationManager,
            String titleKey,
            String subtitleKey
    ) {
        this.localizationManager = Objects.requireNonNull(
                localizationManager, "localizationManager must not be null");
        this.titleKey = Objects.requireNonNull(titleKey, "titleKey must not be null");
        this.subtitleKey = Objects.requireNonNull(subtitleKey, "subtitleKey must not be null");
    }

    @FXML
    private void initialize() {
        localizationManager.bind(titleLabel, titleKey);
        localizationManager.bind(subtitleLabel, subtitleKey);
        localizationManager.bind(noteLabel, "placeholder.phaseNote");
    }
}
