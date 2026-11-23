package com.senkoje.infrastructure.nlp;

import java.util.List;

/**
 * Kết quả phân tách ngoại tuyến toàn bộ văn bản tiếng Anh gồm danh sách câu và luồng token đầy đủ.
 *
 * @param originalText văn bản gốc đầu vào
 * @param sentences danh sách các câu đã tách
 * @param tokens toàn bộ luồng token bảo toàn nguyên vẹn khoảng trắng, xuống dòng và dấu câu
 */
public record EnglishReadingDocument(
        String originalText,
        List<EnglishSentence> sentences,
        List<EnglishToken> tokens
) {

    /**
     * Đảm bảo danh sách câu và token luôn bất biến.
     */
    public EnglishReadingDocument {
        sentences = sentences == null ? List.of() : List.copyOf(sentences);
        tokens = tokens == null ? List.of() : List.copyOf(tokens);
    }

    /**
     * Lấy nội dung câu ngữ cảnh theo chỉ số câu.
     *
     * @param sentenceIndex chỉ số câu cần lấy
     * @return nội dung câu tương ứng hoặc chuỗi rỗng nếu chỉ số không hợp lệ
     */
    public String getSentenceText(int sentenceIndex) {
        if (sentenceIndex < 0 || sentenceIndex >= sentences.size()) {
            return "";
        }
        return sentences.get(sentenceIndex).text();
    }
}
