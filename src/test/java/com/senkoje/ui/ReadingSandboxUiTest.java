package com.senkoje.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.senkoje.app.ApplicationContainer;
import com.senkoje.domain.model.Card;
import com.senkoje.domain.model.Deck;
import com.senkoje.domain.model.DefinitionLanguage;
import com.senkoje.domain.model.ProficiencyLevel;
import com.senkoje.domain.model.TargetLanguage;
import com.senkoje.infrastructure.config.AppDataPathResolver;
import com.senkoje.presentation.controller.CardEditorDialog;
import com.senkoje.presentation.controller.ReadingSandboxController;
import java.nio.file.Path;
import java.time.Clock;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Kiểm thử tích hợp giao diện cho ReadingSandboxController:
 * xác minh phân tích tiếng Nhật Kuromoji trên luồng nền, bật/tắt Furigana, chỉnh sửa cách đọc mơ hồ,
 * tách câu/từ tiếng Anh, điền sẵn hộp thoại Thêm vào bộ thẻ và chuyển đổi giao diện Dark/Light, vi/en.
 */
class ReadingSandboxUiTest {

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
    void shouldAnalyzeJapaneseEditFuriganaAnalyzeEnglishAndPrefillAddToDeck(@TempDir Path tempDir) throws Exception {
        Path dbPath = tempDir.resolve("reading-sandbox-ui.db");
        try (ApplicationContainer container = ApplicationContainer.createForDatabase(
                dbPath,
                new AppDataPathResolver(),
                Clock.system(ApplicationContainer.DEFAULT_TIMEZONE)
        )) {
            Deck jaDeck = container.getDeckService().createDeck(
                    "JLPT N4 Reading", TargetLanguage.JA, DefinitionLanguage.VI, ProficiencyLevel.of("N4"), null);
            Deck enDeck = container.getDeckService().createDeck(
                    "English Reading", TargetLanguage.EN, DefinitionLanguage.VI, ProficiencyLevel.of("B2"), null);

            AtomicReference<ReadingSandboxController> controllerRef = new AtomicReference<>();
            AtomicReference<Parent> rootRef = new AtomicReference<>();

            runOnFxThread(() -> {
                ReadingSandboxController controller = new ReadingSandboxController(
                        container.getJapaneseTokenizerService(),
                        container.getFuriganaSegmentBuilder(),
                        container.getEnglishTextTokenizer(),
                        container.getDeckService(),
                        container.getCardService(),
                        container.getTtsManager(),
                        container.getLocalizationManager(),
                        container.getThemeManager(),
                        container.getBackgroundTaskExecutor()
                );
                FXMLLoader loader = new FXMLLoader(
                        ReadingSandboxController.class.getResource("/fxml/reading-sandbox-view.fxml"));
                loader.setControllerFactory(type -> controller);
                Parent root = loader.load();
                Scene scene = new Scene(root, 1240, 820);
                container.getThemeManager().registerScene(scene);
                root.applyCss();
                root.layout();

                controllerRef.set(controller);
                rootRef.set(root);

                // 1. Nhập văn bản tiếng Nhật và chạy phân tích trên luồng nền
                TextArea inputArea = (TextArea) root.lookup("#inputTextArea");
                inputArea.setText("新しい市場で美味しい魚を食べました。");
                controller.handleAnalyze();
                return null;
            });

            // Chờ phân tích nền hoàn tất trên luồng worker
            waitUntil(() -> runOnFxThread(() -> !controllerRef.get().getFuriganaView().getSegments().isEmpty()), 15000);

            runOnFxThread(() -> {
                ReadingSandboxController controller = controllerRef.get();
                Parent root = rootRef.get();

                // 2. Xác minh Furigana hiển thị và bật/tắt hoạt động
                CheckBox showFurigana = (CheckBox) root.lookup("#showFuriganaCheckBox");
                assertTrue(showFurigana.isSelected());
                assertTrue(controller.getFuriganaView().isShowFurigana());
                showFurigana.setSelected(false);
                assertFalse(controller.getFuriganaView().isShowFurigana());
                showFurigana.setSelected(true);
                assertTrue(controller.getFuriganaView().isShowFurigana());

                // 3. Chọn token 市場, kiểm tra bảng chi tiết và hiệu chỉnh cách đọc sang いちば
                int ichibaIndex = -1;
                var segments = controller.getFuriganaView().getSegments();
                for (int i = 0; i < segments.size(); i++) {
                    if ("市場".equals(segments.get(i).surface())) {
                        ichibaIndex = i;
                        break;
                    }
                }
                assertTrue(ichibaIndex >= 0, "Expected 市場 token in analyzed output");
                controller.getFuriganaView().selectSegment(ichibaIndex);

                Label inspectorSurface = (Label) root.lookup("#inspectorSurfaceLabel");
                assertEquals("市場", inspectorSurface.getText());

                TextField customReadingField = (TextField) root.lookup("#customReadingField");
                customReadingField.setText("いちば");
                controller.handleApplyCustomReading();

                Label previewLabel = (Label) root.lookup("#furiganaPreviewValueLabel");
                assertEquals("市場[いちば]", previewLabel.getText());

                // 4. Kiểm tra dữ liệu điền sẵn cho hộp thoại Thêm vào bộ thẻ
                CardEditorDialog.CardPrefillData jaPrefill = controller.buildAddToDeckPrefill();
                assertNotNull(jaPrefill);
                assertEquals("市場", jaPrefill.term());
                assertEquals("いちば", jaPrefill.reading());
                assertEquals("市場[いちば]", jaPrefill.furigana());
                assertEquals(1, jaPrefill.examples().size());
                assertEquals("新しい市場で美味しい魚を食べました。", jaPrefill.examples().getFirst().sentence());

                // Kiểm tra Dialog tạo thẻ mới khởi tạo hợp lệ với dữ liệu điền sẵn
                Dialog<CardEditorDialog.CardFormData> dialog = CardEditorDialog.createDialogForNewCard(
                        null,
                        List.of(jaDeck, enDeck),
                        jaPrefill,
                        null,
                        container.getLocalizationManager(),
                        container.getThemeManager()
                );
                assertNotNull(dialog.getDialogPane().getContent());

                // Lưu thử một thẻ trực tiếp qua CardService để xác minh vòng đời lưu trữ
                Card saved = container.getCardService().createCard(
                        jaDeck.id(),
                        jaPrefill.cardType(),
                        jaPrefill.term(),
                        jaPrefill.reading(),
                        "Chợ / thị trường",
                        jaPrefill.partOfSpeech(),
                        null,
                        null,
                        null,
                        List.of(),
                        jaPrefill.examples()
                );
                assertNotNull(saved.id());

                // 5. Chuyển sang tiếng Anh và phân tích văn bản tiếng Anh ngoại tuyến
                @SuppressWarnings("unchecked")
                ComboBox<TargetLanguage> langBox = (ComboBox<TargetLanguage>) root.lookup("#languageComboBox");
                langBox.setValue(TargetLanguage.EN);
                assertFalse(showFurigana.isVisible());

                TextArea inputArea = (TextArea) root.lookup("#inputTextArea");
                inputArea.setText("Dr. Smith said it's a well-known offline-first tool. Enjoy reading!");
                controller.handleAnalyze();
                return null;
            });

            waitUntil(() -> runOnFxThread(() -> {
                Label stats = (Label) rootRef.get().lookup("#outputStatsLabel");
                return stats.isVisible() && !stats.getText().isBlank();
            }), 10000);

            runOnFxThread(() -> {
                Parent root = rootRef.get();
                // 6. Xác minh chuyển đổi trực tiếp vi -> en và Dark -> Light
                container.getLocalizationManager().setLanguage("en");
                Label titleLabel = (Label) root.lookup("#titleLabel");
                assertEquals("Reading Sandbox", titleLabel.getText());

                container.getThemeManager().setTheme("light");
                assertFalse(container.getThemeManager().isDarkTheme());
                return null;
            });
        }
    }

    private static void waitUntil(Callable<Boolean> condition, long timeoutMs) throws Exception {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            if (Boolean.TRUE.equals(condition.call())) {
                return;
            }
            Thread.sleep(50);
        }
        assertTrue(Boolean.TRUE.equals(condition.call()), "Condition was not met within timeout");
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
