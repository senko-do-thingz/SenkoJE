package com.senkoje.infrastructure.nlp;

import java.util.List;

/**
 * Một phân đoạn văn bản tiếng Nhật đã được căn chỉnh Furigana gắn liền với token gốc từ Kuromoji.
 *
 * @param token token hình thái gốc từ Kuromoji (có thể null nếu tạo trực tiếp từ chuỗi)
 * @param surface dạng bề mặt chính xác của văn bản gốc
 * @param effectiveReading cách đọc Hiragana hiệu lực hiện tại (từ Kuromoji hoặc do người dùng chỉnh sửa)
 * @param parts danh sách phần tử con đã căn chỉnh Furigana theo từng cụm Kanji
 * @param customEdited cờ cho biết người dùng đã hiệu chỉnh cách đọc thủ công hay chưa
 */
public record FuriganaSegment(
        JapaneseToken token,
        String surface,
        String effectiveReading,
        List<FuriganaPart> parts,
        boolean customEdited
) {

    /**
     * Khởi tạo bản ghi bảo đảm danh sách phần tử con bất biến.
     */
    public FuriganaSegment {
        parts = parts == null ? List.of() : List.copyOf(parts);
    }

    /**
     * Kiểm tra phân đoạn này có thể nhấp để xem chi tiết từ vựng và thêm vào bộ thẻ hay không.
     *
     * @return true nếu gắn với một từ học tập hợp lệ (không phải dấu câu hoặc khoảng trắng)
     */
    public boolean isInspectable() {
        return token != null && token.isInspectableWord();
    }

    /**
     * Kiểm tra phân đoạn có chứa ký tự xuống dòng hay không.
     *
     * @return true nếu chứa ký tự ngắt dòng
     */
    public boolean containsLineBreak() {
        return surface != null && (surface.indexOf('\n') >= 0 || surface.indexOf('\r') >= 0);
    }

    /**
     * Kiểm tra phân đoạn có ít nhất một phần tử mang cách đọc Furigana hay không.
     *
     * @return true nếu có Furigana cần hiển thị
     */
    public boolean hasFurigana() {
        for (FuriganaPart part : parts) {
            if (part.hasReading()) {
                return true;
            }
        }
        return false;
    }

    /**
     * Định dạng phân đoạn thành ký hiệu ngoặc vuông chuẩn (ví dụ: 食[た]べました) để lưu vào trường Furigana của thẻ.
     *
     * @return chuỗi Furigana dạng ký hiệu ngoặc vuông
     */
    public String toBracketNotation() {
        StringBuilder builder = new StringBuilder();
        for (FuriganaPart part : parts) {
            builder.append(part.text());
            if (part.hasReading()) {
                builder.append('[').append(part.reading()).append(']');
            }
        }
        return builder.toString();
    }
}
