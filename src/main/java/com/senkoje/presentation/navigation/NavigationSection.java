package com.senkoje.presentation.navigation;

/**
 * Danh sách 7 mục điều hướng chính trong thanh bên có thể thu gọn của SenkoJE theo đúng thứ tự đặc tả.
 */
public enum NavigationSection {
    DASHBOARD("nav.dashboard", "DB"),
    DECKS("nav.decks", "DK"),
    STUDY("nav.study", "ST"),
    READING_SANDBOX("nav.reading", "RD"),
    CARD_BROWSER("nav.cards", "CB"),
    IMPORT_EXPORT("nav.importExport", "IE"),
    SETTINGS("nav.settings", "SG");

    private final String labelKey;
    private final String compactCode;

    NavigationSection(String labelKey, String compactCode) {
        this.labelKey = labelKey;
        this.compactCode = compactCode;
    }

    /**
     * Khóa bản địa hóa cho nhãn hiển thị đầy đủ trên thanh điều hướng.
     *
     * @return khóa bản địa hóa
     */
    public String getLabelKey() {
        return labelKey;
    }

    /**
     * Mã ký tự ngắn gọn (2 chữ cái ASCII) hiển thị khi thanh bên được thu gọn.
     *
     * @return mã ký tự rút gọn
     */
    public String getCompactCode() {
        return compactCode;
    }
}
