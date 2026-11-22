package com.senkoje.persistence.repository;

import com.senkoje.domain.model.CardState;
import com.senkoje.domain.model.ReviewLog;
import com.senkoje.domain.model.ReviewRating;
import com.senkoje.domain.repository.ReviewLogRepository;
import com.senkoje.persistence.sqlite.SqliteConnectionManager;
import com.senkoje.persistence.sqlite.SqliteTransactionExecutor;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Triển khai SQLite chỉ ghi thêm cho nhật ký ôn tập bất biến của người học.
 */
public class SqliteReviewLogRepository implements ReviewLogRepository {

    private final SqliteTransactionExecutor transactionExecutor;

    /**
     * Khởi tạo kho lưu trữ nhật ký ôn tập sử dụng trình quản lý kết nối SQLite.
     *
     * @param connectionManager trình quản lý kết nối SQLite
     */
    public SqliteReviewLogRepository(SqliteConnectionManager connectionManager) {
        this.transactionExecutor = new SqliteTransactionExecutor(connectionManager);
    }

    @Override
    public ReviewLog append(ReviewLog reviewLog) {
        Objects.requireNonNull(reviewLog, "reviewLog must not be null");
        return transactionExecutor.executeInTransaction(
                "append review log",
                connection -> insert(connection, reviewLog)
        );
    }

    /**
     * Ghi thêm một bản ghi nhật ký ôn tập bất biến trên một kết nối giao dịch SQLite có sẵn.
     *
     * @param connection kết nối SQLite đang trong giao dịch
     * @param reviewLog bản ghi nhật ký cần chèn
     * @return bản ghi nhật ký đã lưu kèm mã định danh sinh tự động
     * @throws SQLException nếu câu lệnh SQL thất bại
     */
    static ReviewLog insert(Connection connection, ReviewLog reviewLog) throws SQLException {
        String sql = """
                INSERT INTO review_logs (
                    card_id, session_id, rating, reviewed_at, response_time_ms,
                    previous_state, next_state, previous_stability, next_stability,
                    previous_difficulty, next_difficulty, elapsed_days, scheduled_days,
                    algorithm_version, parameter_version
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;
        try (PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setLong(1, reviewLog.cardId());
            if (reviewLog.sessionId() == null) {
                ps.setNull(2, Types.INTEGER);
            } else {
                ps.setLong(2, reviewLog.sessionId());
            }
            ps.setInt(3, reviewLog.rating().getValue());
            ps.setString(4, reviewLog.reviewedAt().toString());
            if (reviewLog.responseTimeMs() == null) {
                ps.setNull(5, Types.INTEGER);
            } else {
                ps.setInt(5, reviewLog.responseTimeMs());
            }
            ps.setString(6, reviewLog.previousState().name());
            ps.setString(7, reviewLog.nextState().name());
            ps.setDouble(8, reviewLog.previousStability());
            ps.setDouble(9, reviewLog.nextStability());
            ps.setDouble(10, reviewLog.previousDifficulty());
            ps.setDouble(11, reviewLog.nextDifficulty());
            ps.setDouble(12, reviewLog.elapsedDays());
            ps.setDouble(13, reviewLog.scheduledDays());
            ps.setString(14, reviewLog.algorithmVersion());
            ps.setString(15, reviewLog.parameterVersion());
            ps.executeUpdate();

            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (!keys.next()) {
                    throw new SQLException("Failed to retrieve generated review_log id");
                }
                long id = keys.getLong(1);
                return new ReviewLog(
                        id,
                        reviewLog.cardId(),
                        reviewLog.sessionId(),
                        reviewLog.rating(),
                        reviewLog.reviewedAt(),
                        reviewLog.responseTimeMs(),
                        reviewLog.previousState(),
                        reviewLog.nextState(),
                        reviewLog.previousStability(),
                        reviewLog.nextStability(),
                        reviewLog.previousDifficulty(),
                        reviewLog.nextDifficulty(),
                        reviewLog.elapsedDays(),
                        reviewLog.scheduledDays(),
                        reviewLog.algorithmVersion(),
                        reviewLog.parameterVersion()
                );
            }
        }
    }

    @Override
    public Optional<ReviewLog> findById(long id) {
        String sql = """
                SELECT id, card_id, session_id, rating, reviewed_at, response_time_ms,
                       previous_state, next_state, previous_stability, next_stability,
                       previous_difficulty, next_difficulty, elapsed_days, scheduled_days,
                       algorithm_version, parameter_version
                FROM review_logs
                WHERE id = ?
                """;
        return transactionExecutor.executeRead("find review log by id", connection -> {
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                ps.setLong(1, id);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return Optional.of(mapReviewLog(rs));
                    }
                    return Optional.empty();
                }
            }
        });
    }

    @Override
    public List<ReviewLog> findByCardId(long cardId) {
        String sql = """
                SELECT id, card_id, session_id, rating, reviewed_at, response_time_ms,
                       previous_state, next_state, previous_stability, next_stability,
                       previous_difficulty, next_difficulty, elapsed_days, scheduled_days,
                       algorithm_version, parameter_version
                FROM review_logs
                WHERE card_id = ?
                ORDER BY reviewed_at ASC, id ASC
                """;
        return transactionExecutor.executeRead("find review logs by card id", connection -> {
            List<ReviewLog> logs = new ArrayList<>();
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                ps.setLong(1, cardId);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        logs.add(mapReviewLog(rs));
                    }
                }
            }
            return logs;
        });
    }

    @Override
    public List<ReviewLog> findBySessionId(long sessionId) {
        String sql = """
                SELECT id, card_id, session_id, rating, reviewed_at, response_time_ms,
                       previous_state, next_state, previous_stability, next_stability,
                       previous_difficulty, next_difficulty, elapsed_days, scheduled_days,
                       algorithm_version, parameter_version
                FROM review_logs
                WHERE session_id = ?
                ORDER BY reviewed_at ASC, id ASC
                """;
        return transactionExecutor.executeRead("find review logs by session id", connection -> {
            List<ReviewLog> logs = new ArrayList<>();
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                ps.setLong(1, sessionId);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        logs.add(mapReviewLog(rs));
                    }
                }
            }
            return logs;
        });
    }

    @Override
    public List<ReviewLog> findByReviewedAtBetween(Instant startInclusive, Instant endInclusive) {
        Objects.requireNonNull(startInclusive, "startInclusive must not be null");
        Objects.requireNonNull(endInclusive, "endInclusive must not be null");
        String sql = """
                SELECT id, card_id, session_id, rating, reviewed_at, response_time_ms,
                       previous_state, next_state, previous_stability, next_stability,
                       previous_difficulty, next_difficulty, elapsed_days, scheduled_days,
                       algorithm_version, parameter_version
                FROM review_logs
                WHERE reviewed_at >= ? AND reviewed_at <= ?
                ORDER BY reviewed_at ASC, id ASC
                """;
        return transactionExecutor.executeRead("find review logs by date range", connection -> {
            List<ReviewLog> logs = new ArrayList<>();
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                ps.setString(1, startInclusive.toString());
                ps.setString(2, endInclusive.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        logs.add(mapReviewLog(rs));
                    }
                }
            }
            return logs;
        });
    }

    @Override
    public int countNewCardsReviewedBetween(long deckId, Instant startInclusive, Instant endExclusive) {
        return countByPreviousStateBetween(deckId, CardState.NEW, startInclusive, endExclusive);
    }

    @Override
    public int countReviewsCompletedBetween(long deckId, Instant startInclusive, Instant endExclusive) {
        return countByPreviousStateBetween(deckId, CardState.REVIEW, startInclusive, endExclusive);
    }

    private int countByPreviousStateBetween(
            long deckId,
            CardState previousState,
            Instant startInclusive,
            Instant endExclusive
    ) {
        Objects.requireNonNull(startInclusive, "startInclusive must not be null");
        Objects.requireNonNull(endExclusive, "endExclusive must not be null");
        String sql = """
                SELECT COUNT(*)
                FROM review_logs rl
                INNER JOIN cards c ON c.id = rl.card_id
                WHERE c.deck_id = ?
                  AND rl.previous_state = ?
                  AND rl.reviewed_at >= ?
                  AND rl.reviewed_at < ?
                """;
        return transactionExecutor.executeRead("count review logs by previous state and date range", connection -> {
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                ps.setLong(1, deckId);
                ps.setString(2, previousState.name());
                ps.setString(3, startInclusive.toString());
                ps.setString(4, endExclusive.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next() ? rs.getInt(1) : 0;
                }
            }
        });
    }

    private ReviewLog mapReviewLog(ResultSet rs) throws SQLException {
        Object rawSessionId = rs.getObject("session_id");
        Long sessionId = rawSessionId == null ? null : ((Number) rawSessionId).longValue();
        Object rawResponseTime = rs.getObject("response_time_ms");
        Integer responseTimeMs = rawResponseTime == null ? null : ((Number) rawResponseTime).intValue();

        return new ReviewLog(
                rs.getLong("id"),
                rs.getLong("card_id"),
                sessionId,
                ReviewRating.fromValue(rs.getInt("rating")),
                Instant.parse(rs.getString("reviewed_at")),
                responseTimeMs,
                CardState.fromCode(rs.getString("previous_state")),
                CardState.fromCode(rs.getString("next_state")),
                rs.getDouble("previous_stability"),
                rs.getDouble("next_stability"),
                rs.getDouble("previous_difficulty"),
                rs.getDouble("next_difficulty"),
                rs.getDouble("elapsed_days"),
                rs.getDouble("scheduled_days"),
                rs.getString("algorithm_version"),
                rs.getString("parameter_version")
        );
    }
}
