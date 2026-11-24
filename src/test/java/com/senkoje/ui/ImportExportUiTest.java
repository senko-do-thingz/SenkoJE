package com.senkoje.ui;

import com.senkoje.app.ApplicationContainer;
import com.senkoje.domain.model.Card;
import com.senkoje.domain.model.Deck;
import com.senkoje.domain.model.DefinitionLanguage;
import com.senkoje.domain.model.ProficiencyLevel;
import com.senkoje.domain.model.TargetLanguage;
import com.senkoje.infrastructure.config.AppDataPathResolver;
import com.senkoje.presentation.controller.ImportExportController;
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
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Kiểm thử tích hợp giao diện cho màn hình Nhập và Xuất dữ liệu ImportExportController.
 */
class ImportExportUiTest {

    @BeforeAll
    static void initJavaFxToolkit() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        try {
            Platform.startup(latch::countDown);
        } catch (IllegalStateException alreadyStarted) {
            latch.countDown();
        }
        assertTrue(latch.await(15, TimeUnit.SECONDS), "JavaFX toolkit failed to start");
    }

    @Test
    @DisplayName("Nạp thành công màn hình Nhập Xuất, liên kết đa ngôn ngữ, bảng xem trước và các nút thao tác")
    void shouldInitializeImportExportUiAndHandlePreview(@TempDir Path tempDir) throws Exception {
        Path dbPath = tempDir.resolve("import-export-ui.db");
        try (ApplicationContainer container = ApplicationContainer.createForDatabase(
                dbPath,
                new AppDataPathResolver(),
                Clock.system(ApplicationContainer.DEFAULT_TIMEZONE)
        )) {
            Deck testDeck = container.getDeckService().createDeck(
                    "Từ vựng N5 Mẫu",
                    TargetLanguage.JA,
                    DefinitionLanguage.VI,
                    ProficiencyLevel.JLPT_N5,
                    "Mô tả bộ thẻ mẫu"
            );

            AtomicReference<ImportExportController> controllerRef = new AtomicReference<>();
            AtomicReference<Parent> rootRef = new AtomicReference<>();

            runOnFxThread(() -> {
                ImportExportController controller = new ImportExportController(
                        container.getDeckService(),
                        container.getCardService(),
                        container.getDeckRepository(),
                        container.getCardRepository(),
                        container.getDeckJsonExporter(),
                        container.getVocabularyCsvExporter(),
                        container.getDeckJsonImporter(),
                        container.getVocabularyCsvImporter(),
                        container.getAtomicDeckImporter(),
                        container.getAnkiApkgImporter(),
                        container.getSqliteBackupService(),
                        container.getLocalizationManager(),
                        container.getThemeManager(),
                        container.getBackgroundTaskExecutor()
                );
                FXMLLoader loader = new FXMLLoader(
                        ImportExportController.class.getResource("/fxml/import-export-view.fxml"));
                loader.setControllerFactory(type -> controller);
                Parent root = loader.load();
                Scene scene = new Scene(root, 1200, 800);
                container.getThemeManager().registerScene(scene);
                root.applyCss();
                root.layout();

                controllerRef.set(controller);
                rootRef.set(root);
                return null;
            });

            Parent root = rootRef.get();
            assertNotNull(root);

            // Kiểm tra các phần tử giao diện chính hiện diện
            Label titleLabel = (Label) root.lookup("#titleLabel");
            assertNotNull(titleLabel);
            assertFalse(titleLabel.getText().isBlank());

            TextField filePathField = (TextField) root.lookup("#filePathField");
            assertNotNull(filePathField);

            Button previewButton = (Button) root.lookup("#previewButton");
            assertNotNull(previewButton);

            Button confirmImportButton = (Button) root.lookup("#confirmImportButton");
            assertNotNull(confirmImportButton);
            assertTrue(confirmImportButton.isDisable(), "Nút xác nhận nhập phải bị vô hiệu khi chưa có dữ liệu xem trước");

            TableView<?> previewTable = (TableView<?>) root.lookup("#previewTable");
            assertNotNull(previewTable);
            assertEquals(8, previewTable.getColumns().size(), "Bảng xem trước phải có đủ 8 cột");

            // Chuyển đổi ngôn ngữ sang tiếng Anh và xác nhận cập nhật nhãn
            runOnFxThread(() -> {
                container.getLocalizationManager().setLanguage("en");
                return null;
            });
            assertEquals("Import & Export", titleLabel.getText());

            // Chuyển lại tiếng Việt
            runOnFxThread(() -> {
                container.getLocalizationManager().setLanguage("vi");
                return null;
            });
            assertEquals("Nhập & Xuất dữ liệu", titleLabel.getText());
        }
    }

    private static void runOnFxThread(Callable<Void> action) throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Platform.runLater(() -> {
            try {
                action.call();
            } catch (Throwable t) {
                failure.set(t);
            } finally {
                latch.countDown();
            }
        });
        assertTrue(latch.await(10, TimeUnit.SECONDS), "JavaFX operation timed out");
        if (failure.get() != null) {
            throw new AssertionError("Action failed on JavaFX thread", failure.get());
        }
    }
}
