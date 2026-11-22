package com.senkoje.domain.repository;

import com.senkoje.domain.model.StudySession;
import java.util.List;
import java.util.Optional;

/**
 * Giao diện kho lưu trữ cho các phiên học tập cục bộ của người dùng.
 */
public interface StudySessionRepository {

    /**
     * Tạo mới một bản ghi phiên học tập.
     *
     * @param session thông tin phiên học mới
     * @return phiên học đã lưu kèm mã định danh sinh tự động
     */
    StudySession create(StudySession session);

    /**
     * Tìm phiên học tập theo mã định danh.
     *
     * @param id mã phiên học
     * @return phiên học nếu tồn tại
     */
    Optional<StudySession> findById(long id);

    /**
     * Lấy danh sách phiên học gắn với một bộ thẻ cụ thể.
     *
     * @param deckId mã bộ thẻ
     * @return danh sách phiên học theo thứ tự mới nhất trước
     */
    List<StudySession> findByDeckId(long deckId);

    /**
     * Lấy danh sách các phiên học gần nhất trên toàn bộ hệ thống theo giới hạn số lượng.
     *
     * @param limit số phiên học tối đa cần lấy
     * @return danh sách phiên học theo thứ tự thời gian giảm dần
     */
    List<StudySession> findRecent(int limit);

    /**
     * Cập nhật tiến độ hoặc thời điểm hoàn thành của một phiên học đã tồn tại.
     *
     * @param session thông tin phiên học cần cập nhật
     * @return phiên học sau khi cập nhật
     */
    StudySession update(StudySession session);

    /**
     * Xóa phiên học theo mã định danh (nhật ký ôn tập liên quan sẽ được đặt session_id về NULL).
     *
     * @param id mã phiên học cần xóa
     * @return true nếu có phiên học bị xóa
     */
    boolean deleteById(long id);
}
