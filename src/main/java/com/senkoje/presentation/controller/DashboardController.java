package com.senkoje.presentation.controller;

import com.senkoje.application.dto.DashboardSummary;
import com.senkoje.application.dto.HeatmapDay;
import com.senkoje.application.dto.RecentSessionSummary;
import com.senkoje.application.service.AnalyticsService;
import com.senkoje.presentation.i18n.LocalizationManager;
import com.senkoje.presentation.navigation.RefreshableView;
import com.senkoje.presentation.util.BackgroundTaskExecutor;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Objects;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Rectangle;

/**
 * Bộ điều khiển màn hình Tổng quan (Dashboard) hiển thị các chỉ số học tập thực tế từ SQLite,
 * biểu đồ nhiệt 12 tuần và bảng phiên học gần đây.
 */
public class DashboardController implements RefreshableView {

    private static final DateTimeFormatter SESSION_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final DateTimeFormatter HEATMAP_DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final AnalyticsService analyticsService;
    private final LocalizationManager localizationManager;
    private final BackgroundTaskExecutor backgroundTaskExecutor;
    private final ZoneId displayZone;
    private final Runnable onNavigateToDecks;

    private DashboardSummary latestSummary;

    @FXML
    private Label titleLabel;
    @FXML
    private Label subtitleLabel;
    @FXML
    private Button refreshButton;

    @FXML
    private VBox emptyStateBox;
    @FXML
    private Label emptyTitleLabel;
    @FXML
    private Label emptyDescriptionLabel;
    @FXML
    private Button emptyActionButton;

    @FXML
    private Label totalDecksLabel;
    @FXML
    private Label totalDecksValue;
    @FXML
    private Label totalCardsLabel;
    @FXML
    private Label totalCardsValue;
    @FXML
    private Label dueTodayLabel;
    @FXML
    private Label dueTodayValue;
    @FXML
    private Label newAvailableLabel;
    @FXML
    private Label newAvailableValue;
    @FXML
    private Label reviewsTodayLabel;
    @FXML
    private Label reviewsTodayValue;
    @FXML
    private Label studyTimeTodayLabel;
    @FXML
    private Label studyTimeTodayValue;
    @FXML
    private Label currentStreakLabel;
    @FXML
    private Label currentStreakValue;

    @FXML
    private Label heatmapTitleLabel;
    @FXML
    private Label heatmapSubtitleLabel;
    @FXML
    private HBox heatmapLegendBox;
    @FXML
    private GridPane heatmapGrid;

    @FXML
    private Label recentTitleLabel;
    @FXML
    private TableView<RecentSessionSummary> recentSessionsTable;
    @FXML
    private TableColumn<RecentSessionSummary, String> sessionDateColumn;
    @FXML
    private TableColumn<RecentSessionSummary, String> sessionDeckColumn;
    @FXML
    private TableColumn<RecentSessionSummary, String> sessionReviewedColumn;
    @FXML
    private TableColumn<RecentSessionSummary, String> sessionDurationColumn;

    /**
     * Khởi tạo bộ điều khiển Dashboard với dịch vụ phân tích và bộ thực thi tác vụ nền.
     *
     * @param analyticsService dịch vụ tổng hợp dữ liệu học tập
     * @param localizationManager trình quản lý đa ngôn ngữ
     * @param backgroundTaskExecutor bộ thực thi nền tránh chặn luồng JavaFX
     * @param displayZone múi giờ hiển thị (Asia/Ho_Chi_Minh)
     * @param onNavigateToDecks hành động chuyển sang màn hình Thư viện bộ thẻ từ trạng thái rỗng
     */
    public DashboardController(
            AnalyticsService analyticsService,
            LocalizationManager localizationManager,
            BackgroundTaskExecutor backgroundTaskExecutor,
            ZoneId displayZone,
            Runnable onNavigateToDecks
    ) {
        this.analyticsService = Objects.requireNonNull(analyticsService, "analyticsService must not be null");
        this.localizationManager = Objects.requireNonNull(
                localizationManager, "localizationManager must not be null");
        this.backgroundTaskExecutor = Objects.requireNonNull(
                backgroundTaskExecutor, "backgroundTaskExecutor must not be null");
        this.displayZone = Objects.requireNonNull(displayZone, "displayZone must not be null");
        this.onNavigateToDecks = Objects.requireNonNull(onNavigateToDecks, "onNavigateToDecks must not be null");
    }

    @FXML
    private void initialize() {
        bindLocalizedLabels();
        configureRecentSessionsTable();
        renderHeatmapLegend();

        // Cập nhật lại định dạng động (ngày, phút, chú giải biểu đồ nhiệt) khi người dùng đổi ngôn ngữ
        localizationManager.localeProperty().addListener((obs, oldLocale, newLocale) -> {
            renderHeatmapLegend();
            if (latestSummary != null) {
                renderSummary(latestSummary);
            }
        });
    }

    @FXML
    private void handleRefresh() {
        refresh();
    }

    @FXML
    private void handleOpenDecks() {
        onNavigateToDecks.run();
    }

    @Override
    public void refresh() {
        backgroundTaskExecutor.runAsync(
                analyticsService::getDashboardSummary,
                summary -> {
                    latestSummary = summary;
                    renderSummary(summary);
                },
                error -> {
                    // Giữ giao diện ổn định nếu gặp lỗi đọc tạm thời
                }
        );
    }

    private void bindLocalizedLabels() {
        localizationManager.bind(titleLabel, "dashboard.title");
        localizationManager.bind(subtitleLabel, "dashboard.subtitle");
        localizationManager.bind(refreshButton, "common.refresh");

        localizationManager.bind(emptyTitleLabel, "dashboard.empty.title");
        localizationManager.bind(emptyDescriptionLabel, "dashboard.empty.description");
        localizationManager.bind(emptyActionButton, "dashboard.empty.action");

        localizationManager.bind(totalDecksLabel, "dashboard.metric.totalDecks");
        localizationManager.bind(totalCardsLabel, "dashboard.metric.totalCards");
        localizationManager.bind(dueTodayLabel, "dashboard.metric.dueToday");
        localizationManager.bind(newAvailableLabel, "dashboard.metric.newAvailable");
        localizationManager.bind(reviewsTodayLabel, "dashboard.metric.reviewsToday");
        localizationManager.bind(studyTimeTodayLabel, "dashboard.metric.studyTimeToday");
        localizationManager.bind(currentStreakLabel, "dashboard.metric.currentStreak");

        localizationManager.bind(heatmapTitleLabel, "dashboard.heatmap.title");
        localizationManager.bind(heatmapSubtitleLabel, "dashboard.heatmap.subtitle");
        localizationManager.bind(recentTitleLabel, "dashboard.recent.title");

        localizationManager.bindColumn(sessionDateColumn, "dashboard.recent.col.date");
        localizationManager.bindColumn(sessionDeckColumn, "dashboard.recent.col.deck");
        localizationManager.bindColumn(sessionReviewedColumn, "dashboard.recent.col.reviewed");
        localizationManager.bindColumn(sessionDurationColumn, "dashboard.recent.col.duration");

        Label tablePlaceholder = new Label();
        tablePlaceholder.getStyleClass().add("muted-label");
        localizationManager.bind(tablePlaceholder, "dashboard.recent.empty");
        recentSessionsTable.setPlaceholder(tablePlaceholder);
    }

    private void configureRecentSessionsTable() {
        recentSessionsTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        sessionDateColumn.setCellValueFactory(cell -> new SimpleStringProperty(
                SESSION_TIME_FORMATTER.format(cell.getValue().startedAt().atZone(displayZone))));
        sessionDeckColumn.setCellValueFactory(cell -> new SimpleStringProperty(
                cell.getValue().deckTitle() != null
                        ? cell.getValue().deckTitle()
                        : localizationManager.get("dashboard.recent.allDecks")));
        sessionReviewedColumn.setCellValueFactory(cell -> new SimpleStringProperty(
                Integer.toString(cell.getValue().cardsReviewed())));
        sessionDurationColumn.setCellValueFactory(cell -> new SimpleStringProperty(
                formatDuration(cell.getValue().durationSeconds())));
    }

    private void renderSummary(DashboardSummary summary) {
        boolean empty = summary.isEmpty();
        emptyStateBox.setVisible(empty);
        emptyStateBox.setManaged(empty);

        totalDecksValue.setText(Integer.toString(summary.totalDecks()));
        totalCardsValue.setText(Integer.toString(summary.totalCards()));
        dueTodayValue.setText(Integer.toString(summary.cardsDueToday()));
        newAvailableValue.setText(Integer.toString(summary.newCardsAvailable()));
        reviewsTodayValue.setText(Integer.toString(summary.reviewsCompletedToday()));
        studyTimeTodayValue.setText(formatDuration(summary.dailyStudyTimeSeconds()));
        currentStreakValue.setText(localizationManager.get("dashboard.metric.daysSuffix", summary.currentStreakDays()));

        renderHeatmap(summary);
        recentSessionsTable.setItems(FXCollections.observableArrayList(summary.recentSessions()));
        recentSessionsTable.refresh();
    }

    private void renderHeatmap(DashboardSummary summary) {
        heatmapGrid.getChildren().clear();
        int index = 0;
        for (HeatmapDay day : summary.heatmapDays()) {
            int col = index / 7;
            int row = index % 7;
            Rectangle cell = new Rectangle(16, 16);
            cell.getStyleClass().add("heatmap-cell-" + day.intensityLevel());
            String tooltipText = localizationManager.get(
                    "dashboard.heatmap.tooltip",
                    HEATMAP_DATE_FORMATTER.format(day.date()),
                    day.reviewCount()
            );
            Tooltip.install(cell, new Tooltip(tooltipText));
            heatmapGrid.add(cell, col, row);
            index++;
        }
    }

    private void renderHeatmapLegend() {
        heatmapLegendBox.getChildren().clear();
        Label lessLabel = new Label(localizationManager.get("dashboard.heatmap.less"));
        lessLabel.getStyleClass().add("muted-label");
        heatmapLegendBox.getChildren().add(lessLabel);

        for (int level = 0; level <= 4; level++) {
            Rectangle swatch = new Rectangle(12, 12);
            swatch.getStyleClass().add("heatmap-cell-" + level);
            heatmapLegendBox.getChildren().add(swatch);
        }

        Label moreLabel = new Label(localizationManager.get("dashboard.heatmap.more"));
        moreLabel.getStyleClass().add("muted-label");
        heatmapLegendBox.getChildren().add(moreLabel);
    }

    private String formatDuration(int totalSeconds) {
        int safeSeconds = Math.max(0, totalSeconds);
        int minutes = safeSeconds / 60;
        int seconds = safeSeconds % 60;
        return localizationManager.get("dashboard.metric.minutesFormat", minutes, seconds);
    }
}
