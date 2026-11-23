package com.senkoje.presentation.controller;

import com.senkoje.app.ApplicationContainer;
import com.senkoje.presentation.i18n.LocalizationManager;
import com.senkoje.presentation.navigation.NavigationSection;
import com.senkoje.presentation.navigation.RefreshableView;
import java.io.IOException;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import javafx.beans.binding.Bindings;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

/**
 * Bộ điều khiển khung ứng dụng chính với thanh điều hướng bên trái có thể thu gọn
 * và vùng hiển thị trung tâm cho 7 khu vực chức năng của SenkoJE.
 */
public class MainShellController {

    private static final double EXPANDED_SIDEBAR_WIDTH = 230.0;
    private static final double COLLAPSED_SIDEBAR_WIDTH = 68.0;

    private final ApplicationContainer container;
    private final LocalizationManager localizationManager;

    private final BooleanProperty sidebarCollapsed = new SimpleBooleanProperty(false);
    private final ObjectProperty<NavigationSection> activeSection = new SimpleObjectProperty<>();
    private final Map<NavigationSection, Button> navigationButtons = new EnumMap<>(NavigationSection.class);
    private final Map<NavigationSection, LoadedView> loadedViews = new EnumMap<>(NavigationSection.class);

    @FXML
    private BorderPane rootPane;

    @FXML
    private VBox sidebarPane;

    @FXML
    private VBox brandBox;

    @FXML
    private Label brandLabel;

    @FXML
    private Label taglineLabel;

    @FXML
    private Button toggleSidebarButton;

    @FXML
    private Button navDashboardButton;

    @FXML
    private Button navDecksButton;

    @FXML
    private Button navStudyButton;

    @FXML
    private Button navReadingButton;

    @FXML
    private Button navCardsButton;

    @FXML
    private Button navImportExportButton;

    @FXML
    private Button navSettingsButton;

    @FXML
    private StackPane contentHost;

    /**
     * Khởi tạo bộ điều khiển khung chính với bộ chứa phụ thuộc của ứng dụng.
     *
     * @param container bộ chứa dịch vụ và cấu hình ứng dụng
     */
    public MainShellController(ApplicationContainer container) {
        this.container = Objects.requireNonNull(container, "container must not be null");
        this.localizationManager = container.getLocalizationManager();
    }

    @FXML
    private void initialize() {
        localizationManager.bind(brandLabel, "app.title");
        localizationManager.bind(taglineLabel, "app.tagline");

        navigationButtons.put(NavigationSection.DASHBOARD, navDashboardButton);
        navigationButtons.put(NavigationSection.DECKS, navDecksButton);
        navigationButtons.put(NavigationSection.STUDY, navStudyButton);
        navigationButtons.put(NavigationSection.READING_SANDBOX, navReadingButton);
        navigationButtons.put(NavigationSection.CARD_BROWSER, navCardsButton);
        navigationButtons.put(NavigationSection.IMPORT_EXPORT, navImportExportButton);
        navigationButtons.put(NavigationSection.SETTINGS, navSettingsButton);

        configureSidebarBindings();
        navigateTo(NavigationSection.DASHBOARD);
    }

    /**
     * Chuyển đổi trạng thái mở rộng hoặc thu gọn thanh điều hướng bên trái.
     */
    @FXML
    public void handleToggleSidebar() {
        sidebarCollapsed.set(!sidebarCollapsed.get());
    }

    @FXML
    private void handleNavigateDashboard() {
        navigateTo(NavigationSection.DASHBOARD);
    }

    @FXML
    private void handleNavigateDecks() {
        navigateTo(NavigationSection.DECKS);
    }

    @FXML
    private void handleNavigateStudy() {
        navigateTo(NavigationSection.STUDY);
    }

    @FXML
    private void handleNavigateReading() {
        navigateTo(NavigationSection.READING_SANDBOX);
    }

    @FXML
    private void handleNavigateCards() {
        navigateTo(NavigationSection.CARD_BROWSER);
    }

    @FXML
    private void handleNavigateImportExport() {
        navigateTo(NavigationSection.IMPORT_EXPORT);
    }

    @FXML
    private void handleNavigateSettings() {
        navigateTo(NavigationSection.SETTINGS);
    }

    /**
     * Điều hướng đến một khu vực chức năng trong khung chính và làm mới dữ liệu nếu hỗ trợ.
     *
     * @param section mục điều hướng cần mở
     */
    public void navigateTo(NavigationSection section) {
        Objects.requireNonNull(section, "section must not be null");
        NavigationSection previous = activeSection.get();
        if (previous == NavigationSection.STUDY && section != NavigationSection.STUDY) {
            LoadedView studyView = loadedViews.get(NavigationSection.STUDY);
            if (studyView != null && studyView.controller() instanceof StudyWorkspaceController studyController) {
                studyController.finishActiveSessionIfNeeded();
            }
        }
        LoadedView view = loadedViews.computeIfAbsent(section, this::loadSectionView);
        contentHost.getChildren().setAll(view.node());
        activeSection.set(section);
        updateActiveButtonStyles(section);

        if (view.controller() instanceof RefreshableView refreshable) {
            refreshable.refresh();
        }
    }

    /**
     * Chuyển đến Không gian ôn tập và bắt đầu ngay phiên học cho bộ thẻ được chọn.
     *
     * @param deckId mã bộ thẻ cần ôn tập
     */
    public void openStudyForDeck(long deckId) {
        navigateTo(NavigationSection.STUDY);
        LoadedView studyView = loadedViews.get(NavigationSection.STUDY);
        if (studyView != null && studyView.controller() instanceof StudyWorkspaceController studyController) {
            studyController.startStudyForDeck(deckId);
        }
    }

    /**
     * Thuộc tính chỉ đọc cho biết thanh bên đang thu gọn hay mở rộng.
     *
     * @return thuộc tính trạng thái thu gọn
     */
    public ReadOnlyBooleanProperty sidebarCollapsedProperty() {
        return sidebarCollapsed;
    }

    /**
     * Thuộc tính chỉ đọc đại diện cho mục điều hướng đang được hiển thị.
     *
     * @return thuộc tính mục điều hướng hiện tại
     */
    public ReadOnlyObjectProperty<NavigationSection> activeSectionProperty() {
        return activeSection;
    }

    /**
     * Lấy mục điều hướng đang được hiển thị.
     *
     * @return mục điều hướng hiện tại
     */
    public NavigationSection getActiveSection() {
        return activeSection.get();
    }

    /**
     * Kiểm tra thanh điều hướng bên trái đang ở trạng thái thu gọn hay không.
     *
     * @return true nếu thanh bên đang thu gọn
     */
    public boolean isSidebarCollapsed() {
        return sidebarCollapsed.get();
    }

    private void configureSidebarBindings() {
        Tooltip toggleTooltip = new Tooltip();
        toggleTooltip.textProperty().bind(Bindings.createStringBinding(
                () -> localizationManager.get(sidebarCollapsed.get() ? "nav.expand" : "nav.collapse"),
                sidebarCollapsed,
                localizationManager.localeProperty()
        ));
        toggleSidebarButton.setTooltip(toggleTooltip);
        toggleSidebarButton.textProperty().bind(Bindings.when(sidebarCollapsed).then(">").otherwise("<"));

        sidebarPane.prefWidthProperty().bind(
                Bindings.when(sidebarCollapsed).then(COLLAPSED_SIDEBAR_WIDTH).otherwise(EXPANDED_SIDEBAR_WIDTH));
        sidebarPane.minWidthProperty().bind(sidebarPane.prefWidthProperty());
        sidebarPane.maxWidthProperty().bind(sidebarPane.prefWidthProperty());

        brandBox.visibleProperty().bind(sidebarCollapsed.not());
        brandBox.managedProperty().bind(sidebarCollapsed.not());

        for (Map.Entry<NavigationSection, Button> entry : navigationButtons.entrySet()) {
            NavigationSection section = entry.getKey();
            Button button = entry.getValue();
            button.textProperty().bind(Bindings.createStringBinding(
                    () -> sidebarCollapsed.get()
                            ? section.getCompactCode()
                            : localizationManager.get(section.getLabelKey()),
                    sidebarCollapsed,
                    localizationManager.localeProperty()
            ));
            Tooltip tooltip = new Tooltip();
            localizationManager.bindTooltip(tooltip, section.getLabelKey());
            button.setTooltip(tooltip);
        }
    }

    private void updateActiveButtonStyles(NavigationSection current) {
        for (Map.Entry<NavigationSection, Button> entry : navigationButtons.entrySet()) {
            Button button = entry.getValue();
            button.getStyleClass().remove("nav-button-active");
            if (entry.getKey() == current) {
                button.getStyleClass().add("nav-button-active");
            }
        }
    }

    private LoadedView loadSectionView(NavigationSection section) {
        return switch (section) {
            case DASHBOARD -> loadFxml("/fxml/dashboard-view.fxml", new DashboardController(
                    container.getAnalyticsService(),
                    localizationManager,
                    container.getBackgroundTaskExecutor(),
                    container.getClock().getZone(),
                    () -> navigateTo(NavigationSection.DECKS)
            ));
            case DECKS -> loadFxml("/fxml/deck-library-view.fxml", new DeckLibraryController(
                    container.getDeckService(),
                    container.getCardService(),
                    container.getReviewStateRepository(),
                    localizationManager,
                    container.getThemeManager(),
                    container.getBackgroundTaskExecutor(),
                    container.getClock(),
                    this::openStudyForDeck
            ));
            case STUDY -> loadFxml("/fxml/study-workspace-view.fxml", new StudyWorkspaceController(
                    container.getStudySessionService(),
                    container.getDeckService(),
                    container.getAppSettingsService(),
                    container.getTtsManager(),
                    localizationManager,
                    container.getThemeManager(),
                    container.getBackgroundTaskExecutor(),
                    () -> navigateTo(NavigationSection.DASHBOARD)
            ));
            case READING_SANDBOX -> loadFxml("/fxml/reading-sandbox-view.fxml", new ReadingSandboxController(
                    container.getJapaneseTokenizerService(),
                    container.getFuriganaSegmentBuilder(),
                    container.getEnglishTextTokenizer(),
                    container.getDeckService(),
                    container.getCardService(),
                    container.getTtsManager(),
                    localizationManager,
                    container.getThemeManager(),
                    container.getBackgroundTaskExecutor()
            ));
            case CARD_BROWSER -> loadFxml("/fxml/card-browser-view.fxml", new CardBrowserController(
                    container.getDeckService(),
                    container.getCardService(),
                    localizationManager,
                    container.getThemeManager(),
                    container.getBackgroundTaskExecutor()
            ));
            case IMPORT_EXPORT -> loadPlaceholder("placeholder.importExport.title", "placeholder.importExport.subtitle");
            case SETTINGS -> loadFxml("/fxml/settings-view.fxml", new SettingsController(
                    container.getAppSettingsService(),
                    container.getTtsManager(),
                    localizationManager,
                    container.getThemeManager(),
                    container.getBackgroundTaskExecutor(),
                    container.getConnectionManager().getDatabasePath()
            ));
        };
    }

    /**
     * Nạp tệp FXML với bộ điều khiển đã tiêm phụ thuộc thông qua constructor injection.
     *
     * @param fxmlPath đường dẫn tài nguyên FXML trong classpath
     * @param controller thể hiện bộ điều khiển cần gắn vào FXML
     * @return bản ghi gồm Node giao diện và bộ điều khiển tương ứng
     */
    protected LoadedView loadFxml(String fxmlPath, Object controller) {
        FXMLLoader loader = new FXMLLoader(getClass().getResource(fxmlPath));
        loader.setControllerFactory(type -> controller);
        try {
            Node root = loader.load();
            return new LoadedView(root, controller);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to load FXML view: " + fxmlPath, exception);
        }
    }

    private LoadedView loadPlaceholder(String titleKey, String subtitleKey) {
        PlaceholderViewController controller =
                new PlaceholderViewController(localizationManager, titleKey, subtitleKey);
        return loadFxml("/fxml/placeholder-view.fxml", controller);
    }

    /**
     * Trả về bộ chứa ứng dụng để các màn hình con sử dụng khi cần.
     *
     * @return bộ chứa ứng dụng
     */
    protected ApplicationContainer getContainer() {
        return container;
    }

    protected record LoadedView(Node node, Object controller) {
    }
}
