package com.senkoje.persistence.repository;

import com.senkoje.domain.model.CardState;
import com.senkoje.domain.model.ReviewState;
import com.senkoje.domain.repository.ReviewStateRepository;
import com.senkoje.persistence.sqlite.SqliteConnectionManager;
import com.senkoje.persistence.sqlite.SqliteTransactionExecutor;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Triển khai SQLite cho kho lưu trữ trạng thái lập lịch FSRS-6 hiện tại của thẻ học tập.
 */
public class SqliteReviewStateRepository implements ReviewStateRepository {

    private final SqliteTransactionExecutor transactionExecutor;

    /**
     * Khởi tạo kho lưu trữ trạng thái ôn tập sử dụng trình quản lý kết nối SQLite.
     *
     * @param connectionManager trình quản lý kết nối SQLite
     */
    public SqliteReviewStateRepository(SqliteConnectionManager connectionManager) {
        this.transactionExecutor = new SqliteTransactionExecutor(connectionManager);
    }

    @Override
    public Optional<ReviewState> findByCardId(long cardId) {
        String sql = """
                SELECT card_id, state, stability, difficulty, step, due_at, last_reviewed_at,
                       scheduled_days, elapsed_days, repetitions, lapses, updated_at
                FROM review_states
                WHERE card_id = ?
                """;
        return transactionExecutor.executeRead("find review state by card id", connection -> {
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                ps.setLong(1, cardId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return Optional.of(mapReviewState(rs));
                    }
                    return Optional.empty();
                }
            }
        });
    }

    @Override
    public List<ReviewState> findDueByDeckId(long deckId, Instant asOf) {
        Objects.requireNonNull(asOf, "asOf must not be null");
        String sql = """
                SELECT rs.card_id, rs.state, rs.stability, rs.difficulty, rs.step, rs.due_at, rs.last_reviewed_at,
                       rs.scheduled_days, rs.elapsed_days, rs.repetitions, rs.lapses, rs.updated_at
                FROM review_states rs
                INNER JOIN cards c ON c.id = rs.card_id
                WHERE c.deck_id = ?
                  AND c.suspended = 0
                  AND rs.due_at IS NOT NULL
                  AND rs.due_at <= ?
                ORDER BY rs.due_at ASC, rs.card_id ASC
                """;
        return transactionExecutor.executeRead("find due review states by deck id", connection -> {
            List<ReviewState> results = new ArrayList<>();
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                ps.setLong(1, deckId);
                ps.setString(2, asOf.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        results.add(mapReviewState(rs));
                    }
                }
            }
            return results;
        });
    }

    @Override
    public List<ReviewState> findNewByDeckId(long deckId, int limit) {
        if (limit <= 0) {
            return List.of();
        }
        String sql = """
                SELECT rs.card_id, rs.state, rs.stability, rs.difficulty, rs.step, rs.due_at, rs.last_reviewed_at,
                       rs.scheduled_days, rs.elapsed_days, rs.repetitions, rs.lapses, rs.updated_at
                FROM review_states rs
                INNER JOIN cards c ON c.id = rs.card_id
                WHERE c.deck_id = ?
                  AND c.suspended = 0
                  AND rs.state = 'NEW'
                ORDER BY c.id ASC
                LIMIT ?
                """;
        return transactionExecutor.executeRead("find new review states by deck id", connection -> {
            List<ReviewState> results = new ArrayList<>();
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                ps.setLong(1, deckId);
                ps.setInt(2, limit);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        results.add(mapReviewState(rs));
                    }
                }
            }
            return results;
        });
    }

    @Override
    public List<ReviewState> findByState(CardState state) {
        Objects.requireNonNull(state, "state must not be null");
        String sql = """
                SELECT card_id, state, stability, difficulty, step, due_at, last_reviewed_at,
                       scheduled_days, elapsed_days, repetitions, lapses, updated_at
                FROM review_states
                WHERE state = ?
                ORDER BY card_id ASC
                """;
        return transactionExecutor.executeRead("find review states by state", connection -> {
            List<ReviewState> results = new ArrayList<>();
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                ps.setString(1, state.name());
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        results.add(mapReviewState(rs));
                    }
                }
            }
            return results;
        });
    }

    @Override
    public ReviewState save(ReviewState reviewState) {
        Objects.requireNonNull(reviewState, "reviewState must not be null");
        if (reviewState.cardId() == null) {
            throw new IllegalArgumentException("ReviewState cardId must not be null");
        }
        return transactionExecutor.executeInTransaction(
                "save review state",
                connection -> upsert(connection, reviewState)
        );
    }

    /**
     * Lưu mới hoặc cập nhật trạng thái lập lịch hiện tại trên một kết nối giao dịch SQLite có sẵn.
     *
     * @param connection kết nối SQLite đang trong giao dịch
     * @param reviewState trạng thái ôn tập cần lưu
     * @return trạng thái ôn tập đã lưu
     * @throws SQLException nếu câu lệnh SQL thất bại
     */
    static ReviewState upsert(Connection connection, ReviewState reviewState) throws SQLException {
        String sql = """
                INSERT INTO review_states (
                    card_id, state, stability, difficulty, step, due_at, last_reviewed_at,
                    scheduled_days, elapsed_days, repetitions, lapses, updated_at
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT(card_id) DO UPDATE SET
                    state = excluded.state,
                    stability = excluded.stability,
                    difficulty = excluded.difficulty,
                    step = excluded.step,
                    due_at = excluded.due_at,
                    last_reviewed_at = excluded.last_reviewed_at,
                    scheduled_days = excluded.scheduled_days,
                    elapsed_days = excluded.elapsed_days,
                    repetitions = excluded.repetitions,
                    lapses = excluded.lapses,
                    updated_at = excluded.updated_at
                """;
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setLong(1, reviewState.cardId());
            ps.setString(2, reviewState.state().name());
            ps.setDouble(3, reviewState.stability());
            ps.setDouble(4, reviewState.difficulty());
            if (reviewState.step() == null) {
                ps.setNull(5, Types.INTEGER);
            } else {
                ps.setInt(5, reviewState.step());
            }
            setNullableInstant(ps, 6, reviewState.dueAt());
            setNullableInstant(ps, 7, reviewState.lastReviewedAt());
            ps.setDouble(8, reviewState.scheduledDays());
            ps.setDouble(9, reviewState.elapsedDays());
            ps.setInt(10, reviewState.repetitions());
            ps.setInt(11, reviewState.lapses());
            ps.setString(12, reviewState.updatedAt().toString());
            ps.executeUpdate();
        }
        return reviewState;
    }

    private ReviewState mapReviewState(ResultSet rs) throws SQLException {
        Object rawStep = rs.getObject("step");
        Integer step = rawStep == null ? null : ((Number) rawStep).intValue();
        String dueAt = rs.getString("due_at");
        String lastReviewedAt = rs.getString("last_reviewed_at");

        return new ReviewState(
                rs.getLong("card_id"),
                CardState.fromCode(rs.getString("state")),
                rs.getDouble("stability"),
                rs.getDouble("difficulty"),
                step,
                dueAt == null ? null : Instant.parse(dueAt),
                lastReviewedAt == null ? null : Instant.parse(lastReviewedAt),
                rs.getDouble("scheduled_days"),
                rs.getDouble("elapsed_days"),
                rs.getInt("repetitions"),
                rs.getInt("lapses"),
                Instant.parse(rs.getString("updated_at"))
        );
    }

    private static void setNullableInstant(PreparedStatement ps, int index, Instant value) throws SQLException {
        if (value == null) {
            ps.setNull(index, Types.VARCHAR);
        } else {
            ps.setString(index, value.toString());
        }
    }
}
