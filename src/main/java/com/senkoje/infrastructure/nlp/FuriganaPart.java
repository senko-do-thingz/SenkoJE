package com.senkoje.infrastructure.nlp;

/**
 * Một phần con trong phân đoạn Furigana, gồm đoạn chữ gốc và cách đọc đặt phía trên (nếu là chữ Hán).
 * Các đoạn okurigana, Kana thuần, dấu câu hoặc khoảng trắng luôn có reading là null.
 *
 * @param text đoạn ký tự gốc cần hiển thị
 * @param reading cách đọc Hiragana phía trên đoạn chữ Hán (null nếu không có Furigana)
 */
public record FuriganaPart(
        String text,
        String reading
) {

    /**
     * Kiểm tra phần tử này có cách đọc Furigana cần hiển thị phía trên hay không.
     *
     * @return true nếu có cách đọc không rỗng
     */
    public boolean hasReading() {
        return reading != null && !reading.isBlank();
    }
}
