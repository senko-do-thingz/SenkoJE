package com.senkoje.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.senkoje.app.ApplicationContainer;
import com.senkoje.infrastructure.config.AppDataPathResolver;
import com.senkoje.presentation.controller.MainShellController;
import com.senkoje.presentation.navigation.NavigationSection;
import com.senkoje.presentation.theme.ThemeManager;
import java.nio.file.Path;
import java.time.Clock;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Kiểm thử tích hợp giao diện JavaFX xác minh khởi động khung chính, điều hướng 7 mục,
 * thu gọn thanh bên, chuyển đổi Dark/Light theme và chuyển đổi ngôn ngữ vi/en trực tiếp.
 */
class MainDesktopUiIntegrationTest {

    @BeforeAll
    static void initJavaFxToolkit() throws InterruptedException {
        // Khởi động JavaFX Platform một lần duy nhất (hỗ trợ cả desktop và Linux Xvfb headless)
        CountDownLatch latch = new CountDownLatch(1);
        try {
            Platform.startup(latch::countDown);
        } catch (IllegalStateException alreadyStarted) {
            latch.countDown();
        }
        assertTrue(latch.await(15, TimeUnit.SECONDS), "JavaFX toolkit failed to start");
    }

    @Test
    void shouldBootShellNavigateAllSectionsAndSwitchThemeAndLanguageLive(@TempDir Path tempDir) throws Exception {
        Path dbPath = tempDir.resolve("ui-integration.db");
        try (ApplicationContainer container = ApplicationContainer.createForDatabase(
                dbPath,
                new AppDataPathResolver(),
                Clock.system(ApplicationContainer.DEFAULT_TIMEZONE)
        )) {
            runOnFxThread(() -> {
                MainShellController controller = new MainShellController(container);
                FXMLLoader loader = new FXMLLoader(
                        MainShellController.class.getResource("/fxml/main-shell.fxml"));
                loader.setControllerFactory(type -> controller);
                Parent root = loader.load();

                Scene scene = new Scene(root, 1200, 800);
                container.getThemeManager().registerScene(scene);

                // 1. Xác minh trạng thái khởi động mặc định: Dark Mode, tiếng Việt, đang ở Dashboard
                assertEquals(NavigationSection.DASHBOARD, controller.getActiveSection());
                assertTrue(scene.getStylesheets().stream()
                        .anyMatch(url -> url.endsWith(ThemeManager.DARK_THEME_STYLESHEET)));
                Button dashboardButton = (Button) root.lookup("#navDashboardButton");
                assertNotNull(dashboardButton);
                assertEquals("Tổng quan", dashboardButton.getText());

                // 2. Xác minh thu gọn và mở rộng thanh bên
                assertFalse(controller.isSidebarCollapsed());
                controller.handleToggleSidebar();
                assertTrue(controller.isSidebarCollapsed());
                assertEquals("DB", dashboardButton.getText());
                controller.handleToggleSidebar();
                assertFalse(controller.isSidebarCollapsed());
                assertEquals("Tổng quan", dashboardButton.getText());

                // 3. Xác minh điều hướng mượt qua cả 7 khu vực chức năng
                for (NavigationSection section : NavigationSection.values()) {
                    controller.navigateTo(section);
                    assertEquals(section, controller.getActiveSection());
                }

                // 4. Xác minh chuyển đổi ngôn ngữ trực tiếp vi -> en -> vi không cần khởi động lại
                container.getLocalizationManager().setLanguage("en");
                assertEquals("Dashboard", dashboardButton.getText());
                assertEquals("en", container.getAppSettingsService().getInterfaceLanguage());

                container.getLocalizationManager().setLanguage("vi");
                assertEquals("Tổng quan", dashboardButton.getText());
                assertEquals("vi", container.getAppSettingsService().getInterfaceLanguage());

                // 5. Xác minh chuyển đổi chủ đề trực tiếp Dark -> Light -> Dark và lưu vào SQLite
                container.getThemeManager().setTheme("light");
                assertTrue(scene.getStylesheets().stream()
                        .anyMatch(url -> url.endsWith(ThemeManager.LIGHT_THEME_STYLESHEET)));
                assertFalse(scene.getStylesheets().stream()
                        .anyMatch(url -> url.endsWith(ThemeManager.DARK_THEME_STYLESHEET)));
                assertEquals("light", container.getAppSettingsService().getTheme());

                container.getThemeManager().setTheme("dark");
                assertTrue(scene.getStylesheets().stream()
                        .anyMatch(url -> url.endsWith(ThemeManager.DARK_THEME_STYLESHEET)));
                assertEquals("dark", container.getAppSettingsService().getTheme());

                return null;
            });
        }
    }

    private static <T> T runOnFxThread(Callable<T> action) throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<T> result = new AtomicReference<>();
        AtomicReference<Throwable> failure = new AtomicReference<>();

        Platform.runLater(() -> {
            try {
                result.set(action.call());
            } catch (Throwable throwable) {
                failure.set(throwable);
            } finally {
                latch.countDown();
            }
        });

        assertTrue(latch.await(20, TimeUnit.SECONDS), "Timed out waiting for FX Application Thread");
        if (failure.get() != null) {
            if (failure.get() instanceof Exception exception) {
                throw exception;
            }
            throw new AssertionError(failure.get());
        }
        return result.get();
    }
}
