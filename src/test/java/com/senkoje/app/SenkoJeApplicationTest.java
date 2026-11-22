package com.senkoje.app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

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
}
