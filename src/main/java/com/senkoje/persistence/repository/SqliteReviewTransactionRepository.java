package com.senkoje.persistence.repository;

import com.senkoje.domain.model.ReviewLog;
import com.senkoje.domain.model.ReviewState;
import com.senkoje.domain.repository.ReviewTransactionRepository;
import com.senkoje.persistence.sqlite.SqliteConnectionManager;
import com.senkoje.persistence.sqlite.SqliteTransactionExecutor;
import java.util.Objects;

/**
 * Triển khai SQLite đảm bảo cập nhật review_states và ghi thêm review_logs
 * hoàn toàn nguyên tử trong một giao dịch SQLite duy nhất.
 */
public class SqliteReviewTransactionRepository implements ReviewTransactionRepository {

    private final SqliteTransactionExecutor transactionExecutor;

    /**
     * Khởi tạo kho giao dịch ôn tập từ trình quản lý kết nối SQLite.
     *
     * @param connectionManager trình quản lý kết nối SQLite
     */
    public SqliteReviewTransactionRepository(SqliteConnectionManager connectionManager) {
        this(new SqliteTransactionExecutor(connectionManager));
    }

    /**
     * Khởi tạo kho giao dịch ôn tập tái sử dụng bộ thực thi giao dịch SQLite sẵn có.
     *
     * @param transactionExecutor bộ thực thi giao dịch SQLite
     */
    public SqliteReviewTransactionRepository(SqliteTransactionExecutor transactionExecutor) {
        this.transactionExecutor = Objects.requireNonNull(
                transactionExecutor,
                "transactionExecutor must not be null"
        );
    }

    @Override
    public RecordedReview saveStateAndAppendLog(ReviewState nextState, ReviewLog reviewLog) {
        Objects.requireNonNull(nextState, "nextState must not be null");
        Objects.requireNonNull(reviewLog, "reviewLog must not be null");
        if (nextState.cardId() == null) {
            throw new IllegalArgumentException("ReviewState cardId must not be null");
        }
        return transactionExecutor.executeInTransaction(
                "persist review state and append review log",
                connection -> {
                    ReviewState savedState = SqliteReviewStateRepository.upsert(connection, nextState);
                    ReviewLog savedLog = SqliteReviewLogRepository.insert(connection, reviewLog);
                    return new RecordedReview(savedState, savedLog);
                }
        );
    }
}
