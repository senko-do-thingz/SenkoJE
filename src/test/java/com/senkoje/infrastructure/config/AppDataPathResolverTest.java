package com.senkoje.infrastructure.config;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/**
 * Kiểm thử đơn vị xác minh đường dẫn thư mục dữ liệu ứng dụng trên Windows, macOS và Linux.
 */
class AppDataPathResolverTest {

    @Test
    void shouldResolveWindowsDatabasePathUsingAppDataEnv() {
        AppDataPathResolver resolver = new AppDataPathResolver(
                "Windows 11",
                "C:\\Users\\senko",
                "C:\\Users\\senko\\AppData\\Roaming"
        );

        Path expected = Path.of("C:\\Users\\senko\\AppData\\Roaming", "SenkoJE", "senkoje.db");
        assertEquals(expected, resolver.resolveDatabasePath());
    }

    @Test
    void shouldFallbackToUserHomeRoamingWhenWindowsAppDataMissing() {
        AppDataPathResolver resolver = new AppDataPathResolver(
                "Windows 11",
                "C:\\Users\\senko",
                ""
        );

        Path expected = Path.of("C:\\Users\\senko", "AppData", "Roaming", "SenkoJE", "senkoje.db");
        assertEquals(expected, resolver.resolveDatabasePath());
    }

    @Test
    void shouldResolveMacOsDatabasePath() {
        AppDataPathResolver resolver = new AppDataPathResolver(
                "Mac OS X",
                "/Users/senko",
                null
        );

        Path expected = Path.of("/Users/senko", "Library", "Application Support", "SenkoJE", "senkoje.db");
        assertEquals(expected, resolver.resolveDatabasePath());
    }

    @Test
    void shouldResolveLinuxDatabasePathAndLogsDirectory() {
        AppDataPathResolver resolver = new AppDataPathResolver(
                "Linux",
                "/home/senko",
                null
        );

        Path expectedDb = Path.of("/home/senko", ".local", "share", "SenkoJE", "senkoje.db");
        Path expectedLogs = Path.of("/home/senko", ".local", "share", "SenkoJE", "logs");
        assertEquals(expectedDb, resolver.resolveDatabasePath());
        assertEquals(expectedLogs, resolver.resolveLogsDirectory());
    }

    @Test
    void shouldPopulateLogDirSystemPropertyWhenUnset() {
        String previousValue = System.getProperty(AppDataPathResolver.LOG_DIR_SYSTEM_PROPERTY);
        try {
            System.clearProperty(AppDataPathResolver.LOG_DIR_SYSTEM_PROPERTY);
            AppDataPathResolver resolver = new AppDataPathResolver(
                    "Mac OS X",
                    "/Users/senko",
                    null
            );

            Path configuredPath = resolver.configureLogDirectorySystemProperty();
            Path expected = Path.of("/Users/senko", "Library", "Application Support", "SenkoJE", "logs").normalize();
            assertEquals(expected, configuredPath);
            assertEquals(expected.toString(), System.getProperty(AppDataPathResolver.LOG_DIR_SYSTEM_PROPERTY));
        } finally {
            restoreLogDirProperty(previousValue);
        }
    }

    @Test
    void shouldPopulateLogDirSystemPropertyWhenBlank() {
        String previousValue = System.getProperty(AppDataPathResolver.LOG_DIR_SYSTEM_PROPERTY);
        try {
            System.setProperty(AppDataPathResolver.LOG_DIR_SYSTEM_PROPERTY, "   ");
            AppDataPathResolver resolver = new AppDataPathResolver(
                    "Windows 11",
                    "C:\\Users\\senko",
                    ""
            );

            Path configuredPath = resolver.configureLogDirectorySystemProperty();
            Path expected = Path.of("C:\\Users\\senko", "AppData", "Roaming", "SenkoJE", "logs").normalize();
            assertEquals(expected, configuredPath);
            assertEquals(expected.toString(), System.getProperty(AppDataPathResolver.LOG_DIR_SYSTEM_PROPERTY));
        } finally {
            restoreLogDirProperty(previousValue);
        }
    }

    @Test
    void shouldPreserveExistingLogDirSystemPropertyWhenAlreadyConfigured() {
        String previousValue = System.getProperty(AppDataPathResolver.LOG_DIR_SYSTEM_PROPERTY);
        String customLogDir = Path.of("custom", "senkoje-logs").toAbsolutePath().toString();
        try {
            System.setProperty(AppDataPathResolver.LOG_DIR_SYSTEM_PROPERTY, customLogDir);
            AppDataPathResolver resolver = new AppDataPathResolver(
                    "Linux",
                    "/home/senko",
                    null
            );

            Path configuredPath = resolver.configureLogDirectorySystemProperty();
            assertEquals(Path.of(customLogDir), configuredPath);
            assertEquals(customLogDir, System.getProperty(AppDataPathResolver.LOG_DIR_SYSTEM_PROPERTY));
        } finally {
            restoreLogDirProperty(previousValue);
        }
    }

    // Khôi phục giá trị thuộc tính hệ thống ban đầu sau mỗi ca kiểm thử
    private void restoreLogDirProperty(String previousValue) {
        if (previousValue == null) {
            System.clearProperty(AppDataPathResolver.LOG_DIR_SYSTEM_PROPERTY);
        } else {
            System.setProperty(AppDataPathResolver.LOG_DIR_SYSTEM_PROPERTY, previousValue);
        }
    }
}
