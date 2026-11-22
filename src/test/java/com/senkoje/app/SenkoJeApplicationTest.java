package com.senkoje.app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.senkoje.infrastructure.config.AppDataPathResolver;
import java.net.URL;
import org.junit.jupiter.api.Test;

/**
 * Kiểm thử đơn vị xác nhận cấu hình khởi chạy và tài nguyên giao diện mặc định của SenkoJE.
 */
class SenkoJeApplicationTest {

    @Test
    void shouldResolveDefaultDarkThemeStylesheet() {
        // Kiểm tra tệp CSS chế độ tối mặc định luôn tồn tại trong classpath
        URL stylesheetUrl = SenkoJeApplication.class.getResource(SenkoJeApplication.DARK_THEME_STYLESHEET);
        assertNotNull(stylesheetUrl, "Default dark theme stylesheet must exist on the classpath");
    }

    @Test
    void shouldExposeExpectedApplicationTitle() {
        // Kiểm tra tiêu đề cửa sổ ứng dụng đúng theo đặc tả
        assertEquals("SenkoJE", SenkoJeApplication.APP_TITLE);
    }

    @Test
    void shouldInitializeLogDirectoryPropertyWhenApplicationClassLoads() throws ClassNotFoundException {
        // Xác minh khối khởi tạo tĩnh của SenkoJeApplication thiết lập thuộc tính thư mục nhật ký trước Logback
        Class.forName(SenkoJeApplication.class.getName());
        String logDir = System.getProperty(AppDataPathResolver.LOG_DIR_SYSTEM_PROPERTY);
        assertNotNull(logDir, "Log directory system property must be initialized");
        assertFalse(logDir.isBlank(), "Log directory system property must not be blank");
    }
}
