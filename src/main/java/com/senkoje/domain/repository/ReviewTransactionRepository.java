package com.senkoje.domain.repository;

import com.senkoje.domain.model.ReviewLog;
import com.senkoje.domain.model.ReviewState;
import java.util.Objects;

/**
 * Giao diện kho lưu trữ nguyên tử giúp cập nhật ReviewState và ghi thêm ReviewLog
 * trong cùng một giao dịch cơ sở dữ liệu duy nhất.
 */
public interface ReviewTransactionRepository {

    /**
     * Kết quả trả về sau khi lưu đồng thời trạng thái ôn tập mới và bản ghi nhật ký bất biến.
     *
     * @param reviewState trạng thái ôn tập đã được lưu
     * @param reviewLog bản ghi nhật ký ôn tập đã được lưu kèm mã định danh
     */
    record RecordedReview(ReviewState reviewState, ReviewLog reviewLog) {
        public RecordedReview {
            Objects.requireNonNull(reviewState, "reviewState must not be null");
            Objects.requireNonNull(reviewLog, "reviewLog must not be null");
        }
    }

    /**
     * Cập nhật trạng thái lập lịch hiện tại và chèn bản ghi nhật ký ôn tập mới trong một giao dịch nguyên tử.
     *
     * @param nextState trạng thái FSRS-6 mới của thẻ
     * @param reviewLog bản ghi nhật ký bất biến tương ứng
     * @return kết quả gồm trạng thái và nhật ký đã được xác nhận trong cơ sở dữ liệu
     */
    RecordedReview saveStateAndAppendLog(ReviewState nextState, ReviewLog reviewLog);
}
