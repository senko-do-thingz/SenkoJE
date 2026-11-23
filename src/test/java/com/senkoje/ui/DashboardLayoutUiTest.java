package com.senkoje.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.senkoje.app.ApplicationContainer;
import com.senkoje.domain.model.Card;
import com.senkoje.domain.model.CardType;
import com.senkoje.domain.model.Deck;
import com.senkoje.domain.model.DefinitionLanguage;
import com.senkoje.domain.model.ProficiencyLevel;
import com.senkoje.domain.model.ReviewRating;
import com.senkoje.domain.model.StudySession;
import com.senkoje.domain.model.TargetLanguage;
import com.senkoje.infrastructure.config.AppDataPathResolver;
import com.senkoje.presentation.controller.MainShellController;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Bounds;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Kiểm thử hồi quy bố cục màn hình Tổng quan (Dashboard) ở kích thước chuẩn 1280x820
 * cho cả tiếng Việt (vi) và tiếng Anh (en): xác minh 6 thẻ chỉ số KPI nằm trên đúng một hàng ngang
 * với chiều rộng đồng đều và huy hiệu chuỗi ngày học hiển thị đầy đủ trong khối biểu đồ nhiệt.
 */
class DashboardLayoutUiTest {

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
    void shouldRenderSixEqualKpiTilesInSingleRowAndStreakInsideHeatmapCard(@TempDir Path tempDir) throws Exception {
        Path dbPath = tempDir.resolve("dashboard-layout.db");
        Instant now = Instant.now();
        try (ApplicationContainer container = ApplicationContainer.createForDatabase(
                dbPath,
                new AppDataPathResolver(),
                Clock.system(ApplicationContainer.DEFAULT_TIMEZONE)
        )) {
            Deck deck = container.getDeckService().createDeck(
                    "JLPT N5 Cơ bản", TargetLanguage.JA, DefinitionLanguage.VI, ProficiencyLevel.JLPT_N5, "Core");
            Card card = container.getCardService().createCard(
                    deck.id(), CardType.VOCABULARY, "食べる", "たべる", "ăn", "verb",
                    ProficiencyLevel.JLPT_N5, null, null, List.of(), List.of());
            StudySession session = container.getStudySessionRepository().create(
                    new StudySession(null, deck.id(), now.minusSeconds(240), now, 3, 240));
            container.getReviewService().submitReview(card.id(), ReviewRating.GOOD, session.id(), 2000, now);

            Parent root = runOnFxThread(() -> {
                container.getThemeManager().setTheme("dark");
                container.getLocalizationManager().setLanguage("vi");
                MainShellController controller = new MainShellController(container);
                FXMLLoader loader = new FXMLLoader(
                        MainShellController.class.getResource("/fxml/main-shell.fxml"));
                loader.setControllerFactory(type -> controller);
                Parent loadedRoot = loader.load();
                Scene scene = new Scene(loadedRoot, 1280, 820);
                container.getThemeManager().registerScene(scene);
                return loadedRoot;
            });

            waitUntil(() -> runOnFxThread(() -> {
                root.applyCss();
                root.layout();
                Label totalDecks = (Label) root.lookup("#totalDecksValue");
                return totalDecks != null && "1".equals(totalDecks.getText());
            }));

            // Xác minh bố cục ở cả tiếng Việt và tiếng Anh
            for (String lang : List.of("vi", "en")) {
                runOnFxThread(() -> {
                    container.getLocalizationManager().setLanguage(lang);
                    root.applyCss();
                    root.layout();
                    root.applyCss();
                    root.layout();

                    // 1. Kiểm tra 6 thẻ .metric-card nằm trên đúng 1 hàng ngang với chiều rộng đồng đều
                    Set<Node> metricCards = root.lookupAll("#kpiGrid .metric-card");
                    assertEquals(6, metricCards.size(), "Expected exactly 6 KPI metric cards for lang=" + lang);

                    List<VBox> cards = new ArrayList<>();
                    for (Node node : metricCards) {
                        assertTrue(node instanceof VBox, "Expected VBox for metric card");
                        cards.add((VBox) node);
                    }

                    double firstMinY = cards.getFirst().localToScene(cards.getFirst().getBoundsInLocal()).getMinY();
                    double firstWidth = cards.getFirst().getWidth();
                    assertTrue(firstWidth >= 120.0, "KPI card width too narrow: " + firstWidth);

                    for (VBox cardBox : cards) {
                        Bounds sceneBounds = cardBox.localToScene(cardBox.getBoundsInLocal());
                        assertTrue(
                                Math.abs(sceneBounds.getMinY() - firstMinY) <= 2.0,
                                "KPI card wrapped onto another row in lang=" + lang
                                        + " minY=" + sceneBounds.getMinY() + " vs firstMinY=" + firstMinY
                        );
                        assertTrue(
                                Math.abs(cardBox.getWidth() - firstWidth) <= 3.0,
                                "KPI card widths are not uniform in lang=" + lang
                                        + " width=" + cardBox.getWidth() + " vs firstWidth=" + firstWidth
                        );
                        // Xác minh không có nhãn nào trong thẻ KPI bị cắt thành dấu ba chấm
                        for (Node child : cardBox.getChildren()) {
                            if (child instanceof Label label && label.isVisible()) {
                                double prefWidth = label.prefWidth(-1);
                                double actualWidth = label.getWidth();
                                assertTrue(
                                        actualWidth + 0.5 >= prefWidth,
                                        "KPI label truncated in lang=" + lang + ": '" + label.getText()
                                                + "' actualWidth=" + actualWidth + " < prefWidth=" + prefWidth
                                );
                            }
                        }
                    }

                    // 2. Kiểm tra nhãn và giá trị chuỗi ngày học nằm bên trong khối biểu đồ nhiệt và không bị cắt
                    Node heatmapCard = root.lookup("#heatmapCard");
                    assertNotNull(heatmapCard, "Heatmap card must exist");
                    Label streakLabel = (Label) heatmapCard.lookup("#currentStreakLabel");
                    Label streakValue = (Label) heatmapCard.lookup("#currentStreakValue");
                    assertNotNull(streakLabel, "currentStreakLabel must be inside heatmapCard");
                    assertNotNull(streakValue, "currentStreakValue must be inside heatmapCard");
                    assertTrue(streakLabel.isVisible(), "currentStreakLabel must be visible");
                    assertTrue(streakValue.isVisible(), "currentStreakValue must be visible");
                    assertTrue(
                            streakLabel.getWidth() + 0.5 >= streakLabel.prefWidth(-1),
                            "currentStreakLabel truncated in lang=" + lang + ": '" + streakLabel.getText() + "'"
                    );
                    assertTrue(
                            streakValue.getWidth() + 0.5 >= streakValue.prefWidth(-1),
                            "currentStreakValue truncated in lang=" + lang + ": '" + streakValue.getText() + "'"
                    );

                    return null;
                });
            }
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
        throw new AssertionError("Timed out waiting for Dashboard metrics to load");
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
