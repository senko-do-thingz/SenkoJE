package com.senkoje.infrastructure.config;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Objects;

/**
 * Xác định đường dẫn thư mục dữ liệu ứng dụng, cơ sở dữ liệu SQLite và nhật ký theo hệ điều hành.
 * Đảm bảo dữ liệu người dùng luôn được lưu cục bộ ngoài thư mục cài đặt ứng dụng.
 */
public class AppDataPathResolver {

    public static final String APP_DIRECTORY_NAME = "SenkoJE";
    public static final String DATABASE_FILE_NAME = "senkoje.db";
    public static final String LOGS_DIRECTORY_NAME = "logs";

    private final String osName;
    private final String userHome;
    private final String windowsAppData;

    /**
     * Khởi tạo bộ phân giải đường dẫn theo thông tin hệ điều hành hiện tại.
     */
    public AppDataPathResolver() {
        this(
                System.getProperty("os.name", ""),
                System.getProperty("user.home", ""),
                System.getenv("APPDATA")
        );
    }

    /**
     * Khởi tạo bộ phân giải đường dẫn với thông tin môi trường chỉ định để phục vụ kiểm thử đa nền tảng.
     *
     * @param osName tên hệ điều hành
     * @param userHome đường dẫn thư mục người dùng
     * @param windowsAppData giá trị biến môi trường APPDATA trên Windows (có thể null)
     */
    public AppDataPathResolver(String osName, String userHome, String windowsAppData) {
        this.osName = Objects.requireNonNull(osName, "osName must not be null");
        this.userHome = Objects.requireNonNull(userHome, "userHome must not be null");
        this.windowsAppData = windowsAppData;
    }

    /**
     * Xác định thư mục dữ liệu gốc của SenkoJE theo chuẩn từng hệ điều hành.
     *
     * @return đường dẫn thư mục dữ liệu ứng dụng
     */
    public Path resolveAppDataDirectory() {
        String normalizedOs = osName.toLowerCase(Locale.ROOT);

        if (normalizedOs.contains("win")) {
            if (windowsAppData != null && !windowsAppData.isBlank()) {
                return Path.of(windowsAppData, APP_DIRECTORY_NAME);
            }
            // Dự phòng theo đường dẫn Roaming chuẩn nếu biến môi trường APPDATA không được thiết lập
            return Path.of(userHome, "AppData", "Roaming", APP_DIRECTORY_NAME);
        }

        if (normalizedOs.contains("mac") || normalizedOs.contains("darwin")) {
            return Path.of(userHome, "Library", "Application Support", APP_DIRECTORY_NAME);
        }

        // Mặc định theo chuẩn XDG cho Linux và các hệ điều hành tương tự Unix
        return Path.of(userHome, ".local", "share", APP_DIRECTORY_NAME);
    }

    /**
     * Xác định đường dẫn đầy đủ đến tệp cơ sở dữ liệu SQLite cục bộ.
     *
     * @return đường dẫn tệp senkoje.db
     */
    public Path resolveDatabasePath() {
        return resolveAppDataDirectory().resolve(DATABASE_FILE_NAME);
    }

    /**
     * Xác định đường dẫn thư mục lưu tệp nhật ký cục bộ.
     *
     * @return đường dẫn thư mục logs
     */
    public Path resolveLogsDirectory() {
        return resolveAppDataDirectory().resolve(LOGS_DIRECTORY_NAME);
    }

    /**
     * Tạo thư mục dữ liệu ứng dụng nếu chưa tồn tại trên đĩa.
     *
     * @return đường dẫn thư mục dữ liệu đã sẵn sàng sử dụng
     * @throws IOException nếu không thể tạo thư mục trên hệ thống tệp
     */
    public Path ensureAppDataDirectoryExists() throws IOException {
        Path appDataDirectory = resolveAppDataDirectory();
        Files.createDirectories(appDataDirectory);
        return appDataDirectory;
    }
}
