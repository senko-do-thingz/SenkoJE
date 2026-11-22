package com.senkoje.presentation.theme;

import java.util.List;
import javafx.scene.text.Font;

/**
 * Cấu hình bộ phông chữ Unicode đa nền tảng cho tiếng Việt có dấu, Kanji, Hiragana, Katakana, Romaji và IPA.
 */
public final class TypographyConfig {

    // Thứ tự ưu tiên phông chữ chuẩn theo đặc tả: Inter, Noto Sans JP và các phông dự phòng hệ thống
    public static final List<String> PREFERRED_FONT_FAMILIES = List.of(
            "Inter",
            "Noto Sans JP",
            "Segoe UI",
            "Hiragino Sans",
            "Yu Gothic UI",
            "Meiryo",
            "Noto Sans",
            "DejaVu Sans",
            "System"
    );

    public static final String CSS_FONT_FAMILY_STACK =
            "\"Inter\", \"Noto Sans JP\", \"Segoe UI\", \"Hiragino Sans\", \"Yu Gothic UI\", \"Meiryo\", \"Noto Sans\", sans-serif";

    // Mẫu chuỗi kiểm tra Unicode đầy đủ bao gồm tiếng Việt, Kanji, Hiragana, Katakana, Romaji và IPA
    public static final String UNICODE_VERIFICATION_SAMPLE =
            "Tiếng Việt có dấu - 日本語 漢字 ひらがな カタカナ - Rōmaji - /ˈsɛn.koʊ/";

    private TypographyConfig() {
    }

    /**
     * Chọn tên phông chữ khả dụng tốt nhất trên hệ điều hành hiện tại theo danh sách ưu tiên.
     *
     * @return tên họ phông chữ được chọn
     */
    public static String resolvePrimaryFontFamily() {
        List<String> installedFamilies = Font.getFamilies();
        for (String candidate : PREFERRED_FONT_FAMILIES) {
            if (installedFamilies.contains(candidate)) {
                return candidate;
            }
        }
        return "System";
    }
}
