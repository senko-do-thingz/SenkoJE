package com.senkoje.app;

import java.net.URL;
import javafx.application.Application;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Lớp khởi tạo giao diện JavaFX chính của ứng dụng SenkoJE.
 * Mặc định nạp giao diện chế độ tối (Dark Mode) khi mở cửa sổ chính.
 */
public class SenkoJeApplication extends Application {

    private static final Logger LOGGER = LoggerFactory.getLogger(SenkoJeApplication.class);

    public static final String APP_TITLE = "SenkoJE";
    public static final String DARK_THEME_STYLESHEET = "/css/dark-theme.css";

    private static final double DEFAULT_WIDTH = 960.0;
    private static final double DEFAULT_HEIGHT = 640.0;
    private static final double MIN_WIDTH = 640.0;
    private static final double MIN_HEIGHT = 480.0;

    /**
     * Xây dựng và hiển thị cửa sổ chính ban đầu với giao diện chế độ tối mặc định.
     *
     * @param primaryStage cửa sổ chính do JavaFX cung cấp
     */
    @Override
    public void start(Stage primaryStage) {
        LOGGER.info("Starting SenkoJE JavaFX application");

        Label titleLabel = new Label(APP_TITLE);
        titleLabel.getStyleClass().add("app-title");

        Label subtitleLabel = new Label("Offline-first Japanese and English learning application");
        subtitleLabel.getStyleClass().add("app-subtitle");

        VBox root = new VBox(12.0, titleLabel, subtitleLabel);
        root.setAlignment(Pos.CENTER);
        root.getStyleClass().add("app-root");

        Scene scene = new Scene(root, DEFAULT_WIDTH, DEFAULT_HEIGHT);
        applyDefaultTheme(scene);

        primaryStage.setTitle(APP_TITLE);
        primaryStage.setMinWidth(MIN_WIDTH);
        primaryStage.setMinHeight(MIN_HEIGHT);
        primaryStage.setScene(scene);
        primaryStage.show();

        LOGGER.info("Primary stage displayed with default dark theme");
    }

    /**
     * Nạp tệp định kiểu chế độ tối mặc định vào khung cảnh JavaFX.
     *
     * @param scene khung cảnh cần áp dụng giao diện chế độ tối
     */
    private void applyDefaultTheme(Scene scene) {
        URL stylesheetUrl = getClass().getResource(DARK_THEME_STYLESHEET);
        if (stylesheetUrl == null) {
            LOGGER.warn("Default dark theme stylesheet not found at {}", DARK_THEME_STYLESHEET);
            return;
        }
        scene.getStylesheets().add(stylesheetUrl.toExternalForm());
    }
}
