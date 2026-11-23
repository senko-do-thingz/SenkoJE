package com.senkoje.infrastructure.nlp;

import java.util.List;

/**
 * Bản ghi bất biến biểu diễn một câu tiếng Anh đã được tách cùng danh sách token thành phần.
 *
 * @param index chỉ số câu (bắt đầu từ 0)
 * @param text nội dung câu đã cắt gọn khoảng trắng hai đầu
 * @param startOffset vị trí bắt đầu của câu trong văn bản gốc
 * @param endOffset vị trí kết thúc (không bao gồm) của câu trong văn bản gốc
 * @param tokens danh sách token bên trong câu
 */
public record EnglishSentence(
        int index,
        String text,
        int startOffset,
        int endOffset,
        List<EnglishToken> tokens
) {

    /**
     * Đảm bảo danh sách token luôn bất biến.
     */
    public EnglishSentence {
        tokens = tokens == null ? List.of() : List.copyOf(tokens);
    }
}
