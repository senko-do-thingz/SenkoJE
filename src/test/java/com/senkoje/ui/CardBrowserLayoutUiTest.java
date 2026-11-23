package com.senkoje.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.senkoje.app.ApplicationContainer;
import com.senkoje.domain.model.CardType;
import com.senkoje.domain.model.Deck;
import com.senkoje.domain.model.DefinitionLanguage;
import com.senkoje.domain.model.ProficiencyLevel;
import com.senkoje.domain.model.TargetLanguage;
import com.senkoje.infrastructure.config.AppDataPathResolver;
import com.senkoje.presentation.controller.MainShellController;
import com.senkoje.presentation.navigation.NavigationSection;
import java.nio.file.Path;
import java.time.Clock;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Kiểm thử hồi quy bố cục bảng của màn hình Trình duyệt thẻ ở ngôn ngữ tiếng Việt (vi):
 * xác minh mọi tiêu đề cột (đặc biệt là cột Trình độ và Trạng thái) có đủ chiều rộng hiển thị
 * mà không bị cắt thành dấu ba chấm ở kích thước cửa sổ chuẩn 1280x820.
 */
class CardBrowserLayoutUiTest {

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
    void shouldAllocateSufficientColumnWidthsForVietnameseHeadersAndStatus(@TempDir Path tempDir) throws Exception {
        Path dbPath = tempDir.resolve("card-browser-layout.db");
        try (ApplicationContainer container = ApplicationContainer.createForDatabase(
                dbPath,
                new AppDataPathResolver(),
                Clock.system(ApplicationContainer.DEFAULT_TIMEZONE)
        )) {
            Deck deck = container.getDeckService().createDeck(
                    "JLPT N5 Cơ bản", TargetLanguage.JA, DefinitionLanguage.VI, ProficiencyLevel.JLPT_N5, "Core");
            container.getCardService().createCard(
                    deck.id(), CardType.VOCABULARY, "食べる", "たべる", "ăn", "verb",
                    ProficiencyLevel.JLPT_N5, null, null, List.of(), List.of());

            Parent root = runOnFxThread(() -> {
                container.getLocalizationManager().setLanguage("vi");
                container.getThemeManager().setTheme("light");
                MainShellController controller = new MainShellController(container);
                FXMLLoader loader = new FXMLLoader(
                        MainShellController.class.getResource("/fxml/main-shell.fxml"));
                loader.setControllerFactory(type -> controller);
                Parent loadedRoot = loader.load();
                Scene scene = new Scene(loadedRoot, 1280, 820);
                container.getThemeManager().registerScene(scene);
                controller.navigateTo(NavigationSection.CARD_BROWSER);
                return loadedRoot;
            });

            waitUntil(() -> runOnFxThread(() -> {
                root.applyCss();
                root.layout();
                TableView<?> table = (TableView<?>) root.lookup("#cardsTable");
                return table != null && !table.getItems().isEmpty();
            }));

            runOnFxThread(() -> {
                root.applyCss();
                root.layout();
                root.applyCss();
                root.layout();

                TableView<?> table = (TableView<?>) root.lookup("#cardsTable");
                assertNotNull(table, "Card browser TableView must exist");
                assertEquals(7, table.getColumns().size(), "Expected 7 columns in card browser table");

                TableColumn<?, ?> levelColumn = table.getColumns().get(4);
                TableColumn<?, ?> statusColumn = table.getColumns().get(6);
                assertTrue(
                        levelColumn.getWidth() >= 78.0,
                        "Level column too narrow for Vietnamese header: " + levelColumn.getWidth()
                );
                assertTrue(
                        statusColumn.getWidth() >= 115.0,
                        "Status column too narrow for Vietnamese active label: " + statusColumn.getWidth()
                );

                // Xác minh không có nhãn tiêu đề cột nào bị thu hẹp nhỏ hơn chiều rộng ưu tiên
                Set<Node> headerLabels = table.lookupAll(".column-header .label");
                assertTrue(headerLabels.size() >= 7, "Expected header labels for all 7 columns");
                for (Node node : headerLabels) {
                    if (node instanceof Label label && label.getText() != null && !label.getText().isBlank()) {
                        double prefWidth = label.prefWidth(-1);
                        double actualWidth = label.getWidth();
                        assertTrue(
                                actualWidth + 0.5 >= prefWidth,
                                "Column header truncated: '" + label.getText()
                                        + "' actualWidth=" + actualWidth + " < prefWidth=" + prefWidth
                        );
                    }
                }
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
            Thread.sleep(40);
        }
        throw new AssertionError("Timed out waiting for CardBrowser TableView to populate");
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
