package com.senkoje.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.senkoje.infrastructure.nlp.FuriganaSegment;
import com.senkoje.infrastructure.nlp.FuriganaSegmentBuilder;
import com.senkoje.infrastructure.nlp.JapaneseTokenizerService;
import com.senkoje.presentation.component.FuriganaView;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Kiểm thử thành phần JavaFX FuriganaView: hiển thị ruby trên Kanji, bật/tắt Furigana,
 * chọn token và cập nhật cách đọc thủ công trực tiếp trên giao diện.
 */
class FuriganaViewTest {

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
    void shouldRenderFuriganaToggleVisibilitySelectTokenAndUpdateReading() throws Exception {
        JapaneseTokenizerService tokenizerService = new JapaneseTokenizerService();
        FuriganaSegmentBuilder segmentBuilder = new FuriganaSegmentBuilder();
        List<FuriganaSegment> segments = segmentBuilder.buildSegments(
                tokenizerService.tokenize("日本語を食べます。"));

        runOnFxThread(() -> {
            FuriganaView view = new FuriganaView();
            Scene scene = new Scene(view, 700, 300);
            assertNotNull(scene);

            AtomicReference<FuriganaSegment> selectedRef = new AtomicReference<>();
            view.setOnSegmentSelected((index, seg) -> selectedRef.set(seg));
            view.setSegments(segments);

            // Kiểm tra nhãn ruby đang hiển thị mặc định
            List<Node> rubyNodes = view.lookupAll(".furigana-ruby").stream().toList();
            assertFalse(rubyNodes.isEmpty(), "Ruby labels must be rendered for Kanji segments");
            assertTrue(rubyNodes.stream().allMatch(Node::isVisible));

            // Tắt Furigana -> toàn bộ nhãn ruby ẩn đi và không chiếm diện tích bố cục
            view.setShowFurigana(false);
            assertTrue(rubyNodes.stream().noneMatch(Node::isVisible));
            assertTrue(rubyNodes.stream().noneMatch(Node::isManaged));

            // Bật lại Furigana
            view.setShowFurigana(true);
            assertTrue(rubyNodes.stream().allMatch(Node::isVisible));

            // Chọn phân đoạn đầu tiên (日本語) và chỉnh sửa cách đọc thủ công
            view.selectSegment(0);
            assertNotNull(selectedRef.get());
            assertEquals("日本語", selectedRef.get().surface());

            FuriganaSegment edited = segmentBuilder.withCustomReading(selectedRef.get(), "にっぽんご");
            view.updateSegment(0, edited);
            assertEquals("にっぽんご", view.getSelectedSegment().effectiveReading());
            assertTrue(view.lookupAll(".furigana-ruby").stream()
                    .filter(Label.class::isInstance)
                    .map(Label.class::cast)
                    .anyMatch(label -> "にっぽんご".equals(label.getText())));
            return null;
        });
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
