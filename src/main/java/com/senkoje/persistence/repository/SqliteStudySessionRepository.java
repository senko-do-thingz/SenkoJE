package com.senkoje.persistence.repository;

import com.senkoje.domain.model.StudySession;
import com.senkoje.domain.repository.PersistenceException;
import com.senkoje.domain.repository.StudySessionRepository;
import com.senkoje.persistence.sqlite.SqliteConnectionManager;
import com.senkoje.persistence.sqlite.SqliteTransactionExecutor;
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
 * Triển khai SQLite cho kho lưu trữ các phiên học tập cục bộ.
 */
public class SqliteStudySessionRepository implements StudySessionRepository {

    private final SqliteTransactionExecutor transactionExecutor;

    /**
     * Khởi tạo kho lưu trữ phiên học sử dụng trình quản lý kết nối SQLite.
     *
     * @param connectionManager trình quản lý kết nối SQLite
     */
    public SqliteStudySessionRepository(SqliteConnectionManager connectionManager) {
        this.transactionExecutor = new SqliteTransactionExecutor(connectionManager);
    }

    @Override
    public StudySession create(StudySession session) {
        Objects.requireNonNull(session, "session must not be null");
        String sql = """
                INSERT INTO study_sessions (deck_id, started_at, completed_at, cards_reviewed, duration_seconds)
                VALUES (?, ?, ?, ?, ?)
                """;
        return transactionExecutor.executeInTransaction("create study session", connection -> {
            try (PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                if (session.deckId() == null) {
                    ps.setNull(1, Types.INTEGER);
                } else {
                    ps.setLong(1, session.deckId());
                }
                ps.setString(2, session.startedAt().toString());
                if (session.completedAt() == null) {
                    ps.setNull(3, Types.VARCHAR);
                } else {
                    ps.setString(3, session.completedAt().toString());
                }
                ps.setInt(4, session.cardsReviewed());
                ps.setInt(5, session.durationSeconds());
                ps.executeUpdate();
                try (ResultSet keys = ps.getGeneratedKeys()) {
                    if (!keys.next()) {
                        throw new SQLException("Failed to retrieve generated study_session id");
                    }
                    return new StudySession(
                            keys.getLong(1),
                            session.deckId(),
                            session.startedAt(),
                            session.completedAt(),
                            session.cardsReviewed(),
                            session.durationSeconds()
                    );
                }
            }
        });
    }

    @Override
    public Optional<StudySession> findById(long id) {
        String sql = """
                SELECT id, deck_id, started_at, completed_at, cards_reviewed, duration_seconds
                FROM study_sessions
                WHERE id = ?
                """;
        return transactionExecutor.executeRead("find study session by id", connection -> {
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                ps.setLong(1, id);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return Optional.of(mapStudySession(rs));
                    }
                    return Optional.empty();
                }
            }
        });
    }

    @Override
    public List<StudySession> findByDeckId(long deckId) {
        String sql = """
                SELECT id, deck_id, started_at, completed_at, cards_reviewed, duration_seconds
                FROM study_sessions
                WHERE deck_id = ?
                ORDER BY started_at DESC, id DESC
                """;
        return transactionExecutor.executeRead("find study sessions by deck id", connection -> {
            List<StudySession> sessions = new ArrayList<>();
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                ps.setLong(1, deckId);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        sessions.add(mapStudySession(rs));
                    }
                }
            }
            return sessions;
        });
    }

    @Override
    public List<StudySession> findRecent(int limit) {
        if (limit <= 0) {
            return List.of();
        }
        String sql = """
                SELECT id, deck_id, started_at, completed_at, cards_reviewed, duration_seconds
                FROM study_sessions
                ORDER BY started_at DESC, id DESC
                LIMIT ?
                """;
        return transactionExecutor.executeRead("find recent study sessions", connection -> {
            List<StudySession> sessions = new ArrayList<>();
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                ps.setInt(1, limit);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        sessions.add(mapStudySession(rs));
                    }
                }
            }
            return sessions;
        });
    }

    @Override
    public StudySession update(StudySession session) {
        Objects.requireNonNull(session, "session must not be null");
        if (session.id() == null) {
            throw new IllegalArgumentException("StudySession id must not be null for update");
        }
        String sql = """
                UPDATE study_sessions
                SET deck_id = ?, started_at = ?, completed_at = ?, cards_reviewed = ?, duration_seconds = ?
                WHERE id = ?
                """;
        return transactionExecutor.executeInTransaction("update study session", connection -> {
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                if (session.deckId() == null) {
                    ps.setNull(1, Types.INTEGER);
                } else {
                    ps.setLong(1, session.deckId());
                }
                ps.setString(2, session.startedAt().toString());
                if (session.completedAt() == null) {
                    ps.setNull(3, Types.VARCHAR);
                } else {
                    ps.setString(3, session.completedAt().toString());
                }
                ps.setInt(4, session.cardsReviewed());
                ps.setInt(5, session.durationSeconds());
                ps.setLong(6, session.id());
                int updated = ps.executeUpdate();
                if (updated == 0) {
                    throw new PersistenceException("StudySession not found for update: " + session.id());
                }
            }
            return session;
        });
    }

    @Override
    public boolean deleteById(long id) {
        String sql = "DELETE FROM study_sessions WHERE id = ?";
        return transactionExecutor.executeInTransaction("delete study session", connection -> {
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                ps.setLong(1, id);
                return ps.executeUpdate() > 0;
            }
        });
    }

    private StudySession mapStudySession(ResultSet rs) throws SQLException {
        Object rawDeckId = rs.getObject("deck_id");
        Long deckId = rawDeckId == null ? null : ((Number) rawDeckId).longValue();
        String completedAt = rs.getString("completed_at");

        return new StudySession(
                rs.getLong("id"),
                deckId,
                Instant.parse(rs.getString("started_at")),
                completedAt == null ? null : Instant.parse(completedAt),
                rs.getInt("cards_reviewed"),
                rs.getInt("duration_seconds")
        );
    }
}
