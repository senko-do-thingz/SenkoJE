package com.senkoje.infrastructure.nlp;

/**
 * Bản ghi bất biến biểu diễn một đơn vị văn bản (từ, dấu câu hoặc khoảng trắng) trong văn bản tiếng Anh.
 *
 * @param text nội dung bề mặt chính xác trong văn bản gốc
 * @param normalizedWord dạng chuẩn hóa chữ thường dùng cho tra cứu hoặc tạo thẻ (null nếu không phải từ)
 * @param word cờ cho biết token có phải từ vựng có thể chọn kiểm tra hay không
 * @param sentenceIndex chỉ số câu chứa token (0-based, hoặc -1 nếu là khoảng trắng giữa các câu)
 * @param startOffset vị trí ký tự bắt đầu trong văn bản gốc
 * @param endOffset vị trí ký tự kết thúc (không bao gồm) trong văn bản gốc
 */
public record EnglishToken(
        String text,
        String normalizedWord,
        boolean word,
        int sentenceIndex,
        int startOffset,
        int endOffset
) {

    /**
     * Kiểm tra token có chứa ký tự ngắt dòng hay không.
     *
     * @return true nếu có chứa ký tự xuống dòng
     */
    public boolean containsLineBreak() {
        return text != null && (text.indexOf('\n') >= 0 || text.indexOf('\r') >= 0);
    }
}
