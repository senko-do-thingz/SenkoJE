package com.senkoje.infrastructure.nlp;

/**
 * Bản ghi bất biến biểu diễn một đơn vị từ vựng (token) tiếng Nhật sau khi phân tích hình thái bằng Kuromoji.
 *
 * @param surface dạng bề mặt xuất hiện trong văn bản gốc
 * @param baseForm dạng nguyên thể từ điển (mặc định bằng surface nếu không có)
 * @param readingKatakana cách đọc Katakana từ từ điển IPADIC (có thể null nếu không có)
 * @param readingHiragana cách đọc Hiragana chuyển đổi từ Katakana (có thể null nếu không có)
 * @param partOfSpeech nhãn từ loại tổng hợp ngắn gọn (ví dụ: 名詞-一般, 動詞-自立)
 * @param partOfSpeechLevel1 cấp từ loại chính (ví dụ: 名詞, 動詞, 助詞, 記号)
 * @param partOfSpeechDetail chi tiết phân loại phụ của từ loại (có thể rỗng)
 * @param startOffset vị trí ký tự bắt đầu trong văn bản gốc
 * @param endOffset vị trí ký tự kết thúc (không bao gồm) trong văn bản gốc
 * @param known cờ cho biết từ có nằm trong từ điển nội bộ Kuromoji hay không
 */
public record JapaneseToken(
        String surface,
        String baseForm,
        String readingKatakana,
        String readingHiragana,
        String partOfSpeech,
        String partOfSpeechLevel1,
        String partOfSpeechDetail,
        int startOffset,
        int endOffset,
        boolean known
) {

    /**
     * Kiểm tra dạng bề mặt của token có chứa chữ Hán (Kanji) hay không.
     *
     * @return true nếu chứa ít nhất một ký tự Kanji
     */
    public boolean hasKanji() {
        return KanaConverter.containsKanji(surface);
    }

    /**
     * Kiểm tra token có phải khoảng trắng hoặc dấu câu hay không.
     *
     * @return true nếu là khoảng trắng, xuống dòng hoặc ký hiệu dấu câu
     */
    public boolean isPunctuationOrWhitespace() {
        if (surface == null || surface.isEmpty() || surface.isBlank()) {
            return true;
        }
        return "記号".equals(partOfSpeechLevel1);
    }

    /**
     * Kiểm tra token có phù hợp để kiểm tra chi tiết từ vựng và thêm vào bộ thẻ hay không.
     *
     * @return true nếu là từ có nội dung học tập (không phải khoảng trắng hay dấu câu thuần)
     */
    public boolean isInspectableWord() {
        return !isPunctuationOrWhitespace();
    }
}
