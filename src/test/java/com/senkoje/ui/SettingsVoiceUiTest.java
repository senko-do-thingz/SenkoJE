package com.senkoje.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.senkoje.application.dto.TtsPreferences;
import com.senkoje.application.service.AppSettingsService;
import com.senkoje.domain.model.TargetLanguage;
import com.senkoje.infrastructure.tts.TtsAvailability;
import com.senkoje.infrastructure.tts.TtsManager;
import com.senkoje.infrastructure.tts.TtsPlaybackRequest;
import com.senkoje.infrastructure.tts.TtsPlaybackResult;
import com.senkoje.infrastructure.tts.TtsService;
import com.senkoje.infrastructure.tts.TtsVoice;
import com.senkoje.infrastructure.tts.UnsupportedTtsProvider;
import com.senkoje.persistence.migration.DatabaseMigrator;
import com.senkoje.persistence.repository.SqliteAppSettingRepository;
import com.senkoje.persistence.sqlite.SqliteConnectionManager;
import com.senkoje.presentation.controller.SettingsController;
import com.senkoje.presentation.controller.SettingsController.VoiceOption;
import com.senkoje.presentation.i18n.LocalizationManager;
import com.senkoje.presentation.theme.ThemeManager;
import com.senkoje.presentation.util.BackgroundTaskExecutor;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.ComboBox;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Kiểm thử tích hợp giao diện cho SettingsController xác minh ComboBox chọn giọng đọc JA/EN
 * được nạp từ TtsManager trên luồng nền, có mục Mặc định hệ thống ở đầu và giữ nguyên giọng đã lưu.
 */
class SettingsVoiceUiTest {

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
    @SuppressWarnings("unchecked")
    void shouldPopulateVoiceComboBoxesAndPreserveSavedUndetectedVoice(@TempDir Path tempDir) throws Exception {
        Path dbPath = tempDir.resolve("settings-voice-ui.db");
        SqliteConnectionManager manager = new SqliteConnectionManager(dbPath);
        new DatabaseMigrator(manager).migrate();
        AppSettingsService settingsService = new AppSettingsService(new SqliteAppSettingRepository(manager));

        // Lưu sẵn một giọng tiếng Anh chưa có trong danh sách phát hiện để kiểm tra khả năng bảo toàn
        settingsService.saveTtsPreferences(new TtsPreferences(
                true, "system", 1.0, "Fake Haruka", "Saved Custom Voice"));

        TtsService fakeSystem = new TtsService() {
            @Override
            public String getProviderId() {
                return "system";
            }

            @Override
            public TtsAvailability checkAvailability() {
                return new TtsAvailability(true, "system", "tts.status.ready", "Fake System");
            }

            @Override
            public List<TtsVoice> listVoices(TargetLanguage language) {
                if (language == TargetLanguage.JA) {
                    return List.of(new TtsVoice("Fake Haruka", "Fake Haruka (ja-JP)", TargetLanguage.JA));
                }
                return List.of(new TtsVoice("Fake David", "Fake David (en-US)", TargetLanguage.EN));
            }

            @Override
            public TtsPlaybackResult speak(TtsPlaybackRequest request) {
                return new TtsPlaybackResult(true, "system", "tts.status.played", "");
            }

            @Override
            public void stop() {
            }
        };

        TtsManager ttsManager = new TtsManager(
                settingsService, fakeSystem, new UnsupportedTtsProvider(), new UnsupportedTtsProvider());
        LocalizationManager localizationManager = new LocalizationManager(settingsService);
        ThemeManager themeManager = new ThemeManager(settingsService);

        try (BackgroundTaskExecutor executor = new BackgroundTaskExecutor()) {
            SettingsController controller = new SettingsController(
                    settingsService, ttsManager, localizationManager, themeManager, executor, dbPath);

            Scene scene = runOnFxThread(() -> {
                FXMLLoader loader = new FXMLLoader(
                        SettingsController.class.getResource("/fxml/settings-view.fxml"));
                loader.setControllerFactory(type -> controller);
                Parent root = loader.load();
                Scene created = new Scene(root, 1000, 760);
                themeManager.registerScene(created);
                controller.refresh();
                return created;
            });

            waitUntil(() -> runOnFxThread(() -> {
                Parent root = scene.getRoot();
                root.applyCss();
                root.layout();
                ComboBox<VoiceOption> jaBox = (ComboBox<VoiceOption>) root.lookup("#jaVoiceComboBox");
                return jaBox != null && jaBox.getItems().size() >= 2;
            }));

            runOnFxThread(() -> {
                Parent root = scene.getRoot();
                ComboBox<VoiceOption> jaBox = (ComboBox<VoiceOption>) root.lookup("#jaVoiceComboBox");
                ComboBox<VoiceOption> enBox = (ComboBox<VoiceOption>) root.lookup("#enVoiceComboBox");
                assertNotNull(jaBox);
                assertNotNull(enBox);

                // Mục đầu tiên luôn là mặc định hệ thống (id rỗng)
                assertEquals("", jaBox.getItems().getFirst().id());
                assertEquals("Fake Haruka", jaBox.getValue().id());

                // Giọng đã lưu trước đó dù không nằm trong danh sách phát hiện vẫn được giữ nguyên
                assertEquals("", enBox.getItems().getFirst().id());
                assertEquals(3, enBox.getItems().size());
                assertEquals("Saved Custom Voice", enBox.getValue().id());
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
        throw new AssertionError("Timed out waiting for SettingsController voice ComboBoxes");
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
