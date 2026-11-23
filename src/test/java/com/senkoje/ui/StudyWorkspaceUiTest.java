package com.senkoje.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.senkoje.app.ApplicationContainer;
import com.senkoje.application.dto.CardExtraData;
import com.senkoje.application.mapper.CardExtraDataCodec;
import com.senkoje.domain.model.CardType;
import com.senkoje.domain.model.Deck;
import com.senkoje.domain.model.DefinitionLanguage;
import com.senkoje.domain.model.ProficiencyLevel;
import com.senkoje.domain.model.TargetLanguage;
import com.senkoje.infrastructure.config.AppDataPathResolver;
import com.senkoje.presentation.controller.StudyWorkspaceController;
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
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Kiểm thử tích hợp giao diện JavaFX cho StudyWorkspaceController:
 * xác minh hiển thị thẻ Vocabulary/Grammar, lật thẻ bằng phím Space,
 * xem trước khoảng cách FSRS-6 trên 4 nút đánh giá và thoát phiên an toàn bằng Esc.
 */
class StudyWorkspaceUiTest {

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
    void shouldRevealCardShowFsrsIntervalsAndFinishCleanlyOnEscape(@TempDir Path tempDir) throws Exception {
        Path dbPath = tempDir.resolve("study-ui.db");
        try (ApplicationContainer container = ApplicationContainer.createForDatabase(
                dbPath,
                new AppDataPathResolver(),
                Clock.system(ApplicationContainer.DEFAULT_TIMEZONE)
        )) {
            Deck deck = container.getDeckService().createDeck(
                    "JLPT N4 Study", TargetLanguage.JA, DefinitionLanguage.VI, ProficiencyLevel.JLPT_N4, "Core");
            container.getCardService().createCard(
                    deck.id(),
                    CardType.GRAMMAR,
                    "～てみる",
                    null,
                    "thử làm gì đó",
                    "grammar",
                    ProficiencyLevel.JLPT_N4,
                    CardExtraDataCodec.toJson(new CardExtraData(
                            null, null, null, null, null, null, null, null,
                            "V-て + みる", "Diễn tả việc làm thử để xem kết quả.", "Động từ thể て + みる", "試す"
                    )),
                    "Ghi chú cá nhân",
                    List.of(),
                    List.of()
            );

            StudyWorkspaceController controller = new StudyWorkspaceController(
                    container.getStudySessionService(),
                    container.getDeckService(),
                    container.getLocalizationManager(),
                    container.getThemeManager(),
                    container.getBackgroundTaskExecutor(),
                    () -> {
                    }
            );

            Scene scene = runOnFxThread(() -> {
                FXMLLoader loader = new FXMLLoader(
                        StudyWorkspaceController.class.getResource("/fxml/study-workspace-view.fxml"));
                loader.setControllerFactory(type -> controller);
                Parent root = loader.load();
                Scene createdScene = new Scene(root, 1000, 720);
                container.getThemeManager().registerScene(createdScene);
                controller.startStudyForDeck(deck.id());
                return createdScene;
            });

            waitUntil(() -> runOnFxThread(() -> controller.getCurrentSnapshot() != null
                    && !controller.getCurrentSnapshot().isCompleted()));

            runOnFxThread(() -> {
                Parent root = scene.getRoot();
                root.applyCss();
                root.layout();
                Label frontLabel = (Label) root.lookup("#frontPrimaryLabel");
                assertNotNull(frontLabel);
                assertEquals("V-て + みる", frontLabel.getText());
                assertFalse(controller.isAnswerRevealed());

                // Nhấn Space để lật thẻ và xác minh 4 nút đánh giá kèm xem trước FSRS-6
                scene.getRoot().fireEvent(new KeyEvent(
                        KeyEvent.KEY_PRESSED, "", "", KeyCode.SPACE, false, false, false, false));
                assertTrue(controller.isAnswerRevealed());

                Button goodButton = (Button) root.lookup("#goodButton");
                assertNotNull(goodButton);
                assertTrue(goodButton.getText().contains("[3]"));
                assertTrue(goodButton.getText().contains("\n"));

                // Nhấn Esc để thoát phiên học sạch sẽ
                scene.getRoot().fireEvent(new KeyEvent(
                        KeyEvent.KEY_PRESSED, "", "", KeyCode.ESCAPE, false, false, false, false));
                return null;
            });

            waitUntil(() -> runOnFxThread(() -> controller.getCurrentSnapshot() != null
                    && controller.getCurrentSnapshot().isCompleted()));
            assertEquals(1, container.getStudySessionRepository().findByDeckId(deck.id()).size());
        }
    }

    @Test
    void shouldEvaluateDictationAndNotTriggerStudyShortcutsWhileTypingInInput(@TempDir Path tempDir) throws Exception {
        Path dbPath = tempDir.resolve("study-dictation-ui.db");
        try (ApplicationContainer container = ApplicationContainer.createForDatabase(
                dbPath,
                new AppDataPathResolver(),
                Clock.system(ApplicationContainer.DEFAULT_TIMEZONE)
        )) {
            Deck deck = container.getDeckService().createDeck(
                    "EN Dictation", TargetLanguage.EN, DefinitionLanguage.VI, ProficiencyLevel.CEFR_B1, null);
            container.getCardService().createCard(
                    deck.id(), CardType.PHRASE, "break the ice", null, "phá vỡ sự ngại ngùng",
                    "idiom", ProficiencyLevel.CEFR_B1, null, null, List.of(), List.of()
            );

            StudyWorkspaceController controller = new StudyWorkspaceController(
                    container.getStudySessionService(),
                    container.getDeckService(),
                    container.getAppSettingsService(),
                    container.getLocalizationManager(),
                    container.getThemeManager(),
                    container.getBackgroundTaskExecutor(),
                    () -> {
                    }
            );

            Scene scene = runOnFxThread(() -> {
                FXMLLoader loader = new FXMLLoader(
                        StudyWorkspaceController.class.getResource("/fxml/study-workspace-view.fxml"));
                loader.setControllerFactory(type -> controller);
                Parent root = loader.load();
                Scene createdScene = new Scene(root, 1000, 720);
                container.getThemeManager().registerScene(createdScene);
                controller.setDictationMode(true);
                controller.startStudyForDeck(deck.id());
                return createdScene;
            });

            waitUntil(() -> runOnFxThread(() -> controller.getCurrentSnapshot() != null
                    && !controller.getCurrentSnapshot().isCompleted()));

            runOnFxThread(() -> {
                Parent root = scene.getRoot();
                root.applyCss();
                root.layout();

                TextField inputField = (TextField) root.lookup("#dictationInputField");
                assertNotNull(inputField);
                inputField.requestFocus();
                inputField.setText("Break the ice!");

                // Phím Space khi đang gõ trong ô nhập không được lật thẻ sớm
                inputField.fireEvent(new KeyEvent(
                        KeyEvent.KEY_PRESSED, " ", " ", KeyCode.SPACE, false, false, false, false));
                assertFalse(controller.isAnswerRevealed());

                // Gửi bài gõ -> chấm đúng và hiển thị đáp án kèm 4 nút đánh giá
                controller.handleSubmitDictation();
                assertNotNull(controller.getLastDictationResult());
                assertTrue(controller.getLastDictationResult().correct());
                assertTrue(controller.isAnswerRevealed());
                return null;
            });
        }
    }

    private static void waitUntil(Callable<Boolean> condition) throws Exception {
        long deadline = System.currentTimeMillis() + 10_000L;
        while (System.currentTimeMillis() < deadline) {
            if (Boolean.TRUE.equals(condition.call())) {
                return;
            }
            Thread.sleep(50);
        }
        throw new AssertionError("Timed out waiting for asynchronous JavaFX UI state");
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
