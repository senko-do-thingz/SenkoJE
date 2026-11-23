package com.senkoje.app;

import com.senkoje.application.service.AnalyticsService;
import com.senkoje.application.service.AppSettingsService;
import com.senkoje.application.service.CardService;
import com.senkoje.application.service.DeckService;
import com.senkoje.application.service.ReviewService;
import com.senkoje.application.service.StudySessionService;
import com.senkoje.domain.fsrs.FsrsParameters;
import com.senkoje.domain.fsrs.FsrsScheduler;
import com.senkoje.domain.repository.AppSettingRepository;
import com.senkoje.domain.repository.CardRepository;
import com.senkoje.domain.repository.DeckRepository;
import com.senkoje.domain.repository.ReviewLogRepository;
import com.senkoje.domain.repository.ReviewStateRepository;
import com.senkoje.domain.repository.ReviewTransactionRepository;
import com.senkoje.domain.repository.StudySessionRepository;
import com.senkoje.domain.service.CardValidator;
import com.senkoje.domain.service.DeckSettingsValidator;
import com.senkoje.domain.service.DeckValidator;
import com.senkoje.infrastructure.config.AppDataPathResolver;
import com.senkoje.infrastructure.nlp.EnglishTextTokenizer;
import com.senkoje.infrastructure.nlp.FuriganaSegmentBuilder;
import com.senkoje.infrastructure.nlp.JapaneseTokenizerService;
import com.senkoje.infrastructure.tts.BundledOfflineTtsProvider;
import com.senkoje.infrastructure.tts.SystemTtsProvider;
import com.senkoje.infrastructure.tts.TtsManager;
import com.senkoje.infrastructure.tts.UnsupportedTtsProvider;
import com.senkoje.persistence.migration.DatabaseMigrator;
import com.senkoje.persistence.repository.SqliteAppSettingRepository;
import com.senkoje.persistence.repository.SqliteCardRepository;
import com.senkoje.persistence.repository.SqliteDeckRepository;
import com.senkoje.persistence.repository.SqliteReviewLogRepository;
import com.senkoje.persistence.repository.SqliteReviewStateRepository;
import com.senkoje.persistence.repository.SqliteReviewTransactionRepository;
import com.senkoje.persistence.repository.SqliteStudySessionRepository;
import com.senkoje.persistence.sqlite.SqliteConnectionManager;
import com.senkoje.presentation.i18n.LocalizationManager;
import com.senkoje.presentation.theme.ThemeManager;
import com.senkoje.presentation.util.BackgroundTaskExecutor;
import java.nio.file.Path;
import java.sql.SQLException;
import java.time.Clock;
import java.time.ZoneId;
import java.util.Objects;

/**
 * Bộ chứa phụ thuộc chính của ứng dụng desktop SenkoJE, kết nối toàn bộ tầng lưu trữ,
 * dịch vụ ứng dụng, lập lịch FSRS-6 và các trình quản lý giao diện bằng tiêm qua hàm khởi tạo.
 */
public class ApplicationContainer implements AutoCloseable {

    // Múi giờ chuẩn Việt Nam (UTC+7) cố định cho phiên bản v1.0 theo đặc tả hệ thống
    public static final ZoneId DEFAULT_TIMEZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    private final Clock clock;
    private final AppDataPathResolver pathResolver;
    private final SqliteConnectionManager connectionManager;
    private final DatabaseMigrator databaseMigrator;

    private final DeckRepository deckRepository;
    private final CardRepository cardRepository;
    private final ReviewStateRepository reviewStateRepository;
    private final ReviewLogRepository reviewLogRepository;
    private final ReviewTransactionRepository reviewTransactionRepository;
    private final StudySessionRepository studySessionRepository;
    private final AppSettingRepository appSettingRepository;

    private final DeckService deckService;
    private final CardService cardService;
    private final AppSettingsService appSettingsService;
    private final ReviewService reviewService;
    private final StudySessionService studySessionService;
    private final AnalyticsService analyticsService;

    private final LocalizationManager localizationManager;
    private final ThemeManager themeManager;
    private final BackgroundTaskExecutor backgroundTaskExecutor;
    private final TtsManager ttsManager;
    private final JapaneseTokenizerService japaneseTokenizerService;
    private final FuriganaSegmentBuilder furiganaSegmentBuilder;
    private final EnglishTextTokenizer englishTextTokenizer;

    /**
     * Khởi tạo bộ chứa ứng dụng mặc định dùng đường dẫn dữ liệu chuẩn theo hệ điều hành và múi giờ Việt Nam.
     *
     * @return bộ chứa ứng dụng đã chạy di trú Flyway
     * @throws SQLException nếu di trú cơ sở dữ liệu thất bại
     */
    public static ApplicationContainer createDefault() throws SQLException {
        AppDataPathResolver resolver = new AppDataPathResolver();
        return createForDatabase(resolver.resolveDatabasePath(), resolver, Clock.system(DEFAULT_TIMEZONE));
    }

    /**
     * Khởi tạo bộ chứa ứng dụng cho một đường dẫn cơ sở dữ liệu cụ thể (hữu ích cho kiểm thử tích hợp UI).
     *
     * @param databasePath đường dẫn tệp SQLite
     * @param pathResolver bộ phân giải đường dẫn ứng dụng
     * @param clock nguồn đồng hồ và múi giờ của ứng dụng
     * @return bộ chứa ứng dụng đã sẵn sàng sử dụng
     * @throws SQLException nếu di trú cơ sở dữ liệu thất bại
     */
    public static ApplicationContainer createForDatabase(
            Path databasePath,
            AppDataPathResolver pathResolver,
            Clock clock
    ) throws SQLException {
        return new ApplicationContainer(databasePath, pathResolver, clock);
    }

    private ApplicationContainer(Path databasePath, AppDataPathResolver pathResolver, Clock clock) throws SQLException {
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
        this.pathResolver = Objects.requireNonNull(pathResolver, "pathResolver must not be null");
        this.connectionManager = new SqliteConnectionManager(databasePath);
        this.databaseMigrator = new DatabaseMigrator(connectionManager);
        // Chạy di trú lược đồ Flyway trước khi đọc cấu hình ban đầu từ app_settings
        this.databaseMigrator.migrate();

        this.deckRepository = new SqliteDeckRepository(connectionManager);
        this.cardRepository = new SqliteCardRepository(connectionManager);
        this.reviewStateRepository = new SqliteReviewStateRepository(connectionManager);
        this.reviewLogRepository = new SqliteReviewLogRepository(connectionManager);
        this.reviewTransactionRepository = new SqliteReviewTransactionRepository(connectionManager);
        this.studySessionRepository = new SqliteStudySessionRepository(connectionManager);
        this.appSettingRepository = new SqliteAppSettingRepository(connectionManager);

        DeckValidator deckValidator = new DeckValidator();
        DeckSettingsValidator deckSettingsValidator = new DeckSettingsValidator();
        CardValidator cardValidator = new CardValidator();
        FsrsScheduler fsrsScheduler = new FsrsScheduler(FsrsParameters.defaults(), this.clock);

        this.deckService = new DeckService(deckRepository, deckValidator, deckSettingsValidator, this.clock);
        this.cardService = new CardService(cardRepository, cardValidator, this.clock);
        this.appSettingsService = new AppSettingsService(appSettingRepository, this.clock);
        this.reviewService = new ReviewService(
                cardRepository,
                deckRepository,
                reviewStateRepository,
                reviewLogRepository,
                reviewTransactionRepository,
                fsrsScheduler,
                this.clock
        );
        this.studySessionService = new StudySessionService(
                reviewService,
                cardService,
                deckService,
                studySessionRepository,
                this.clock
        );
        this.analyticsService = new AnalyticsService(
                deckRepository,
                cardRepository,
                reviewStateRepository,
                reviewLogRepository,
                studySessionRepository,
                this.clock
        );

        this.localizationManager = new LocalizationManager(appSettingsService);
        this.themeManager = new ThemeManager(appSettingsService);
        this.backgroundTaskExecutor = new BackgroundTaskExecutor();
        this.ttsManager = new TtsManager(
                appSettingsService,
                new SystemTtsProvider(),
                new BundledOfflineTtsProvider(this.pathResolver.resolveAppDataDirectory().resolve("voices")),
                new UnsupportedTtsProvider()
        );
        // Khởi tạo dịch vụ Kuromoji ở chế độ tải lười, không nạp từ điển ngay lúc khởi động ứng dụng
        this.japaneseTokenizerService = new JapaneseTokenizerService();
        this.furiganaSegmentBuilder = new FuriganaSegmentBuilder();
        this.englishTextTokenizer = new EnglishTextTokenizer();
    }

    public Clock getClock() {
        return clock;
    }

    public AppDataPathResolver getPathResolver() {
        return pathResolver;
    }

    public SqliteConnectionManager getConnectionManager() {
        return connectionManager;
    }

    public DeckService getDeckService() {
        return deckService;
    }

    public CardService getCardService() {
        return cardService;
    }

    public AppSettingsService getAppSettingsService() {
        return appSettingsService;
    }

    public ReviewService getReviewService() {
        return reviewService;
    }

    public StudySessionService getStudySessionService() {
        return studySessionService;
    }

    public AnalyticsService getAnalyticsService() {
        return analyticsService;
    }

    public DeckRepository getDeckRepository() {
        return deckRepository;
    }

    public CardRepository getCardRepository() {
        return cardRepository;
    }

    public ReviewStateRepository getReviewStateRepository() {
        return reviewStateRepository;
    }

    public ReviewLogRepository getReviewLogRepository() {
        return reviewLogRepository;
    }

    public StudySessionRepository getStudySessionRepository() {
        return studySessionRepository;
    }

    public LocalizationManager getLocalizationManager() {
        return localizationManager;
    }

    public ThemeManager getThemeManager() {
        return themeManager;
    }

    public BackgroundTaskExecutor getBackgroundTaskExecutor() {
        return backgroundTaskExecutor;
    }

    public TtsManager getTtsManager() {
        return ttsManager;
    }

    public JapaneseTokenizerService getJapaneseTokenizerService() {
        return japaneseTokenizerService;
    }

    public FuriganaSegmentBuilder getFuriganaSegmentBuilder() {
        return furiganaSegmentBuilder;
    }

    public EnglishTextTokenizer getEnglishTextTokenizer() {
        return englishTextTokenizer;
    }

    @Override
    public void close() {
        ttsManager.stop();
        backgroundTaskExecutor.close();
    }
}
