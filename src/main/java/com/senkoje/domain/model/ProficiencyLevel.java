package com.senkoje.domain.model;

import java.util.Set;

/**
 * Trình độ ngôn ngữ của bộ thẻ hoặc thẻ học tập, hỗ trợ chuẩn JLPT (N5-N1), CEFR (A1-C2) và trình độ tùy chỉnh.
 *
 * @param code mã trình độ hiển thị và lưu trữ
 */
public record ProficiencyLevel(String code) {

    public static final ProficiencyLevel JLPT_N5 = new ProficiencyLevel("N5");
    public static final ProficiencyLevel JLPT_N4 = new ProficiencyLevel("N4");
    public static final ProficiencyLevel JLPT_N3 = new ProficiencyLevel("N3");
    public static final ProficiencyLevel JLPT_N2 = new ProficiencyLevel("N2");
    public static final ProficiencyLevel JLPT_N1 = new ProficiencyLevel("N1");

    public static final ProficiencyLevel CEFR_A1 = new ProficiencyLevel("A1");
    public static final ProficiencyLevel CEFR_A2 = new ProficiencyLevel("A2");
    public static final ProficiencyLevel CEFR_B1 = new ProficiencyLevel("B1");
    public static final ProficiencyLevel CEFR_B2 = new ProficiencyLevel("B2");
    public static final ProficiencyLevel CEFR_C1 = new ProficiencyLevel("C1");
    public static final ProficiencyLevel CEFR_C2 = new ProficiencyLevel("C2");

    private static final Set<String> JLPT_CODES = Set.of("N5", "N4", "N3", "N2", "N1");
    private static final Set<String> CEFR_CODES = Set.of("A1", "A2", "B1", "B2", "C1", "C2");

    public ProficiencyLevel {
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("Proficiency level code must not be blank");
        }
        code = code.trim();
    }

    /**
     * Khởi tạo đối tượng ProficiencyLevel từ chuỗi mã trình độ.
     *
     * @param code chuỗi mã trình độ
     * @return đối tượng ProficiencyLevel hợp lệ
     */
    public static ProficiencyLevel of(String code) {
        return new ProficiencyLevel(code);
    }

    /**
     * Kiểm tra xem trình độ có thuộc hệ thống JLPT chuẩn hay không.
     *
     * @return true nếu là một trong các cấp độ N5-N1
     */
    public boolean isJlpt() {
        return JLPT_CODES.contains(code);
    }

    /**
     * Kiểm tra xem trình độ có thuộc hệ thống CEFR chuẩn hay không.
     *
     * @return true nếu là một trong các cấp độ A1-C2
     */
    public boolean isCefr() {
        return CEFR_CODES.contains(code);
    }

    /**
     * Kiểm tra xem trình độ có phải giá trị tùy chỉnh do người dùng định nghĩa hay không.
     *
     * @return true nếu không thuộc tập JLPT hoặc CEFR chuẩn
     */
    public boolean isCustom() {
        return !isJlpt() && !isCefr();
    }
}
