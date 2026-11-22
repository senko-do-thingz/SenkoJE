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
}
