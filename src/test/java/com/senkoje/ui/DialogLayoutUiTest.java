package com.senkoje.ui;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.senkoje.domain.model.Deck;
import com.senkoje.domain.model.DeckSettings;
import com.senkoje.domain.model.DefinitionLanguage;
import com.senkoje.domain.model.ProficiencyLevel;
import com.senkoje.domain.model.TargetLanguage;
import com.senkoje.presentation.controller.CardEditorDialog;
import com.senkoje.presentation.controller.DeckEditorDialog;
import com.senkoje.presentation.controller.DeckSettingsDialog;
import com.senkoje.presentation.i18n.LocalizationManager;
import com.senkoje.presentation.theme.ThemeManager;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.DialogPane;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.GridPane;
import javafx.scene.paint.Color;
import javafx.scene.paint.Paint;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Kiểm thử bố cục giao diện cho các hộp thoại DeckEditorDialog, DeckSettingsDialog và CardEditorDialog
 * ở ngôn ngữ tiếng Việt (vi): xác minh mọi nhãn cột 0 nhận đủ chiều rộng ưu tiên (không cắt thành dấu ba chấm)
 * và nút Lưu/Hủy mang đúng lớp kiểu chủ đề.
 */
class DialogLayoutUiTest {

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
    void shouldAllocateFullPreferredWidthForVietnameseLabelsAndStyleDialogButtons() throws Exception {
        LocalizationManager localizationManager = new LocalizationManager("vi");
        ThemeManager themeManager = new ThemeManager("dark");
        Deck sampleDeck = new Deck(
                1L,
                "JLPT N5 Cơ bản",
                TargetLanguage.JA,
                DefinitionLanguage.VI,
                ProficiencyLevel.JLPT_N5,
                "Từ vựng thiết yếu",
                Instant.parse("2026-06-01T00:00:00Z"),
                Instant.parse("2026-06-01T00:00:00Z")
        );

        runOnFxThread(() -> {
            Dialog<?> deckDialog = DeckEditorDialog.createDialog(null, null, localizationManager, themeManager);
            verifyDialogPaneLayout(deckDialog.getDialogPane(), 540, 400);

            Dialog<?> settingsDialog = DeckSettingsDialog.createDialog(
                    null, sampleDeck, DeckSettings.defaults(1L), localizationManager, themeManager);
            verifyDialogPaneLayout(settingsDialog.getDialogPane(), 580, 440);

            Dialog<?> cardDialog = CardEditorDialog.createDialog(
                    null, List.of(sampleDeck), null, List.of(), List.of(), localizationManager, themeManager);
            verifyDialogPaneLayout(cardDialog.getDialogPane(), 680, 620, false);
            return null;
        });
    }

    @Test
    void shouldRenderDarkReadableLabelsInsideScrollPaneInLightMode() throws Exception {
        LocalizationManager localizationManager = new LocalizationManager("vi");
        ThemeManager lightThemeManager = new ThemeManager("light");
        Deck sampleDeck = new Deck(
                1L,
                "JLPT N5 Cơ bản",
                TargetLanguage.JA,
                DefinitionLanguage.VI,
                ProficiencyLevel.JLPT_N5,
                "Từ vựng thiết yếu",
                Instant.parse("2026-06-01T00:00:00Z"),
                Instant.parse("2026-06-01T00:00:00Z")
        );

        runOnFxThread(() -> {
            Dialog<?> cardDialog = CardEditorDialog.createDialog(
                    null, List.of(sampleDeck), null, List.of(), List.of(), localizationManager, lightThemeManager);
            verifyDialogPaneLayout(cardDialog.getDialogPane(), 680, 620, true);
            return null;
        });
    }

    private static void verifyDialogPaneLayout(DialogPane dialogPane, double width, double height) {
        verifyDialogPaneLayout(dialogPane, width, height, false);
    }

    private static void verifyDialogPaneLayout(
            DialogPane dialogPane,
            double width,
            double height,
            boolean assertDarkTextFill
    ) {
        Scene scene = dialogPane.getScene();
        assertNotNull(scene, "DialogPane must be attached to a Scene");
        dialogPane.resize(width, height);
        dialogPane.applyCss();
        dialogPane.layout();

        // Xác minh nút Lưu mang primary-button và nút Hủy mang secondary-button
        Button saveButton = findButtonByData(dialogPane, ButtonBar.ButtonData.OK_DONE);
        Button cancelButton = findButtonByData(dialogPane, ButtonBar.ButtonData.CANCEL_CLOSE);
        assertNotNull(saveButton, "Save button must exist");
        assertNotNull(cancelButton, "Cancel button must exist");
        assertTrue(saveButton.getStyleClass().contains("primary-button"), "Save button must have primary-button");
        assertTrue(cancelButton.getStyleClass().contains("secondary-button"), "Cancel button must have secondary-button");

        // Thu thập toàn bộ GridPane (kể cả bên trong ScrollPane) và kiểm tra từng nhãn cột 0
        List<GridPane> grids = new ArrayList<>();
        collectGridPanes(dialogPane.getContent(), grids);
        assertFalse(grids.isEmpty(), "Dialog must contain at least one form GridPane");

        int checkedLabels = 0;
        for (GridPane grid : grids) {
            if (!grid.isVisible() || !grid.isManaged()) {
                continue;
            }
            grid.applyCss();
            grid.layout();
            for (Node child : grid.getChildren()) {
                Integer colIndex = GridPane.getColumnIndex(child);
                int col = colIndex == null ? 0 : colIndex;
                if (col == 0 && child instanceof Label label && label.isVisible() && label.isManaged()) {
                    double prefWidth = label.prefWidth(-1);
                    double actualWidth = label.getWidth();
                    assertTrue(
                            actualWidth + 0.5 >= prefWidth,
                            "Label truncated in column 0: '" + label.getText()
                                    + "' actualWidth=" + actualWidth + " < prefWidth=" + prefWidth
                    );
                    if (assertDarkTextFill) {
                        Paint fill = label.getTextFill();
                        assertTrue(fill instanceof Color, "Expected Color textFill for label: " + label.getText());
                        Color color = (Color) fill;
                        double luminance = 0.2126 * color.getRed()
                                + 0.7152 * color.getGreen()
                                + 0.0722 * color.getBlue();
                        assertTrue(
                                luminance < 0.35,
                                "Expected dark readable textFill in Light Mode for label '" + label.getText()
                                        + "' but got color=" + color + " (luminance=" + luminance + ")"
                        );
                    }
                    checkedLabels++;
                }
            }
        }
        assertTrue(checkedLabels >= 5, "Expected at least 5 checked column-0 labels, got " + checkedLabels);
        assertNotNull(scene);
    }

    private static Button findButtonByData(DialogPane dialogPane, ButtonBar.ButtonData expectedData) {
        for (ButtonType type : dialogPane.getButtonTypes()) {
            if (type.getButtonData() == expectedData) {
                Node node = dialogPane.lookupButton(type);
                if (node instanceof Button button) {
                    return button;
                }
            }
        }
        return null;
    }

    private static void collectGridPanes(Node node, List<GridPane> result) {
        if (node == null) {
            return;
        }
        if (node instanceof GridPane grid) {
            result.add(grid);
        }
        if (node instanceof ScrollPane scrollPane) {
            collectGridPanes(scrollPane.getContent(), result);
        } else if (node instanceof Parent parent) {
            for (Node child : parent.getChildrenUnmodifiable()) {
                collectGridPanes(child, result);
            }
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
