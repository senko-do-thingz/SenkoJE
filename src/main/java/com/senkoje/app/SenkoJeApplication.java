package com.senkoje.app;

import com.senkoje.application.dto.WindowPreferences;
import com.senkoje.application.service.AppSettingsService;
import com.senkoje.infrastructure.config.AppDataPathResolver;
import com.senkoje.presentation.controller.MainShellController;
import com.senkoje.presentation.theme.ThemeManager;
import java.io.IOException;
import java.sql.SQLException;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Lớp khởi tạo giao diện JavaFX chính của ứng dụng SenkoJE.
 * Mặc định nạp giao diện chế độ tối (Dark Mode) và khung điều hướng thanh bên có thể thu gọn.
 */
public class SenkoJeApplication extends Application {

    static {
        // Thiết lập đường dẫn thư mục nhật ký trước khi khởi tạo Logger để hỗ trợ khởi chạy trực tiếp qua mvn javafx:run
        new AppDataPathResolver().configureLogDirectorySystemProperty();
    }

    private static final Logger LOGGER = LoggerFactory.getLogger(SenkoJeApplication.class);

    public static final String APP_TITLE = "SenkoJE";
    public static final String DARK_THEME_STYLESHEET = ThemeManager.DARK_THEME_STYLESHEET;

    private static final double MIN_WIDTH = AppSettingsService.MIN_WINDOW_WIDTH;
    private static final double MIN_HEIGHT = AppSettingsService.MIN_WINDOW_HEIGHT;

    private ApplicationContainer container;

    /**
     * Xây dựng và hiển thị cửa sổ chính của ứng dụng với cấu hình cửa sổ, ngôn ngữ và chủ đề đã lưu.
     *
     * @param primaryStage cửa sổ chính do JavaFX cung cấp
     */
    @Override
    public void start(Stage primaryStage) {
        LOGGER.info("Starting SenkoJE JavaFX application");
        try {
            this.container = ApplicationContainer.createDefault();
            WindowPreferences windowPrefs = container.getAppSettingsService().getWindowPreferences();

            MainShellController shellController = new MainShellController(container);
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/main-shell.fxml"));
            loader.setControllerFactory(type -> shellController);
            Parent root = loader.load();

            Scene scene = new Scene(root, windowPrefs.width(), windowPrefs.height());
            container.getThemeManager().registerScene(scene);

            primaryStage.setTitle(APP_TITLE);
            primaryStage.setMinWidth(MIN_WIDTH);
            primaryStage.setMinHeight(MIN_HEIGHT);
            primaryStage.setScene(scene);
            primaryStage.setMaximized(windowPrefs.maximized());

            primaryStage.setOnCloseRequest(event -> saveWindowPreferencesAsync(primaryStage));
            primaryStage.show();

            LOGGER.info("Primary stage displayed with theme={} and language={}",
                    container.getThemeManager().getCurrentTheme(),
                    container.getLocalizationManager().getCurrentLanguage());
        } catch (SQLException | IOException exception) {
            LOGGER.error("Failed to start SenkoJE application", exception);
            throw new IllegalStateException("Failed to start SenkoJE application", exception);
        }
    }

    @Override
    public void stop() {
        if (container != null) {
            container.close();
        }
    }

    private void saveWindowPreferencesAsync(Stage stage) {
        if (container == null) {
            return;
        }
        int width = Math.max(AppSettingsService.MIN_WINDOW_WIDTH, (int) Math.round(stage.getWidth()));
        int height = Math.max(AppSettingsService.MIN_WINDOW_HEIGHT, (int) Math.round(stage.getHeight()));
        boolean maximized = stage.isMaximized();
        container.getBackgroundTaskExecutor().submitFireAndForget(() ->
                container.getAppSettingsService().saveWindowPreferences(
                        new WindowPreferences(width, height, maximized))
        );
    }
}
