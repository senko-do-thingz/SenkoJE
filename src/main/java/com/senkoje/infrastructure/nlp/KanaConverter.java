package com.senkoje.infrastructure.nlp;

/**
 * Tiện ích chuyển đổi và phân loại ký tự tiếng Nhật (Kanji, Hiragana, Katakana)
 * phục vụ tách từ Kuromoji và căn chỉnh Furigana ngoại tuyến.
 */
public final class KanaConverter {

    private static final int KATAKANA_START = 0x30A1;
    private static final int KATAKANA_END = 0x30F6;
    private static final int HIRAGANA_START = 0x3041;
    private static final int HIRAGANA_END = 0x3096;
    private static final int KANA_OFFSET = KATAKANA_START - HIRAGANA_START;

    private KanaConverter() {
    }

    /**
     * Chuyển đổi các ký tự Katakana toàn chiều rộng sang Hiragana tương ứng,
     * giữ nguyên dấu trường âm và các ký tự không phải Katakana.
     *
     * @param input chuỗi đầu vào cần chuyển đổi
     * @return chuỗi sau khi chuyển sang Hiragana (hoặc null nếu đầu vào null)
     */
    public static String katakanaToHiragana(String input) {
        if (input == null || input.isEmpty()) {
            return input;
        }
        StringBuilder builder = new StringBuilder(input.length());
        for (int i = 0; i < input.length(); i++) {
            char ch = input.charAt(i);
            if (ch >= KATAKANA_START && ch <= KATAKANA_END) {
                builder.append((char) (ch - KANA_OFFSET));
            } else {
                builder.append(ch);
            }
        }
        return builder.toString();
    }

    /**
     * Chuyển đổi các ký tự Hiragana sang Katakana tương ứng.
     *
     * @param input chuỗi đầu vào cần chuyển đổi
     * @return chuỗi sau khi chuyển sang Katakana (hoặc null nếu đầu vào null)
     */
    public static String hiraganaToKatakana(String input) {
        if (input == null || input.isEmpty()) {
            return input;
        }
        StringBuilder builder = new StringBuilder(input.length());
        for (int i = 0; i < input.length(); i++) {
            char ch = input.charAt(i);
            if (ch >= HIRAGANA_START && ch <= HIRAGANA_END) {
                builder.append((char) (ch + KANA_OFFSET));
            } else {
                builder.append(ch);
            }
        }
        return builder.toString();
    }

    /**
     * Kiểm tra một ký tự có phải chữ Hán (Kanji) hoặc dấu lặp chữ Hán (々) hay không.
     *
     * @param ch ký tự cần kiểm tra
     * @return true nếu là chữ Hán
     */
    public static boolean isKanji(char ch) {
        return (ch >= 0x4E00 && ch <= 0x9FFF)
                || (ch >= 0x3400 && ch <= 0x4DBF)
                || (ch >= 0xF900 && ch <= 0xFAFF)
                || ch == '\u3005';
    }

    /**
     * Kiểm tra chuỗi có chứa ít nhất một chữ Hán (Kanji) hay không.
     *
     * @param text chuỗi cần kiểm tra
     * @return true nếu có chứa ít nhất một chữ Hán
     */
    public static boolean containsKanji(String text) {
        if (text == null || text.isEmpty()) {
            return false;
        }
        for (int i = 0; i < text.length(); i++) {
            if (isKanji(text.charAt(i))) {
                return true;
            }
        }
        return false;
    }

    /**
     * Kiểm tra một ký tự có phải Hiragana hay không.
     *
     * @param ch ký tự cần kiểm tra
     * @return true nếu là Hiragana
     */
    public static boolean isHiragana(char ch) {
        return ch >= 0x3040 && ch <= 0x309F;
    }

    /**
     * Kiểm tra một ký tự có phải Katakana hay không.
     *
     * @param ch ký tự cần kiểm tra
     * @return true nếu là Katakana
     */
    public static boolean isKatakana(char ch) {
        return (ch >= 0x30A0 && ch <= 0x30FF) || (ch >= 0x31F0 && ch <= 0x31FF);
    }

    /**
     * Kiểm tra một ký tự có thuộc bảng chữ cái Kana (Hiragana hoặc Katakana) hay không.
     *
     * @param ch ký tự cần kiểm tra
     * @return true nếu là Kana
     */
    public static boolean isKana(char ch) {
        return isHiragana(ch) || isKatakana(ch);
    }
}
