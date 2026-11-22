package com.senkoje.persistence.repository;

import com.senkoje.domain.model.Card;
import com.senkoje.domain.model.CardExample;
import com.senkoje.domain.model.CardMeaning;
import com.senkoje.domain.model.CardType;
import com.senkoje.domain.model.DefinitionLanguage;
import com.senkoje.domain.model.ProficiencyLevel;
import com.senkoje.domain.model.ReviewState;
import com.senkoje.domain.repository.CardRepository;
import com.senkoje.domain.repository.PersistenceException;
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
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/**
 * Triển khai SQLite cho kho lưu trữ thẻ học tập, nghĩa bổ sung và câu ví dụ.
 */
public class SqliteCardRepository implements CardRepository {

    private final SqliteTransactionExecutor transactionExecutor;

    /**
     * Khởi tạo kho lưu trữ thẻ sử dụng trình quản lý kết nối SQLite dùng chung.
     *
     * @param connectionManager trình quản lý kết nối SQLite
     */
    public SqliteCardRepository(SqliteConnectionManager connectionManager) {
        this.transactionExecutor = new SqliteTransactionExecutor(connectionManager);
    }

    @Override
    public Card createWithDetails(
            Card card,
            List<CardMeaning> meanings,
            List<CardExample> examples,
            ReviewState initialReviewState
    ) {
        Objects.requireNonNull(card, "card must not be null");
        Objects.requireNonNull(initialReviewState, "initialReviewState must not be null");
        List<CardMeaning> safeMeanings = meanings == null ? List.of() : meanings;
        List<CardExample> safeExamples = examples == null ? List.of() : examples;

        return transactionExecutor.executeInTransaction("create card with details", connection -> {
            long cardId = insertCard(connection, card);
            insertMeanings(connection, cardId, safeMeanings);
            insertExamples(connection, cardId, safeExamples);
            insertReviewState(connection, cardId, initialReviewState);

            return new Card(
                    cardId,
                    card.deckId(),
                    card.cardType(),
                    card.term(),
                    card.reading(),
                    card.meaning(),
                    card.partOfSpeech(),
                    card.level(),
                    card.extraData(),
                    card.notes(),
                    card.suspended(),
                    card.createdAt(),
                    card.updatedAt()
            );
        });
    }

    @Override
    public Optional<Card> findById(long cardId) {
        String sql = """
                SELECT id, deck_id, card_type, term, reading, meaning, part_of_speech,
                       level, extra_data, notes, suspended, created_at, updated_at
                FROM cards
                WHERE id = ?
                """;
        return transactionExecutor.executeRead("find card by id", connection -> {
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                ps.setLong(1, cardId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return Optional.of(mapCard(rs));
                    }
                    return Optional.empty();
                }
            }
        });
    }

    @Override
    public List<Card> findByDeckId(long deckId) {
        String sql = """
                SELECT id, deck_id, card_type, term, reading, meaning, part_of_speech,
                       level, extra_data, notes, suspended, created_at, updated_at
                FROM cards
                WHERE deck_id = ?
                ORDER BY id ASC
                """;
        return transactionExecutor.executeRead("find cards by deck id", connection -> {
            List<Card> cards = new ArrayList<>();
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                ps.setLong(1, deckId);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        cards.add(mapCard(rs));
                    }
                }
            }
            return cards;
        });
    }

    @Override
    public List<Card> search(Long deckId, String query, CardType cardType, Boolean suspended) {
        StringBuilder sql = new StringBuilder("""
                SELECT id, deck_id, card_type, term, reading, meaning, part_of_speech,
                       level, extra_data, notes, suspended, created_at, updated_at
                FROM cards
                WHERE 1 = 1
                """);
        List<Object> params = new ArrayList<>();

        if (deckId != null) {
            sql.append(" AND deck_id = ?");
            params.add(deckId);
        }
        if (query != null && !query.isBlank()) {
            sql.append("""
                     AND (LOWER(term) LIKE ? OR LOWER(COALESCE(reading, '')) LIKE ?
                          OR LOWER(meaning) LIKE ? OR LOWER(COALESCE(notes, '')) LIKE ?)
                    """);
            String pattern = "%" + query.trim().toLowerCase(Locale.ROOT) + "%";
            params.add(pattern);
            params.add(pattern);
            params.add(pattern);
            params.add(pattern);
        }
        if (cardType != null) {
            sql.append(" AND card_type = ?");
            params.add(cardType.name());
        }
        if (suspended != null) {
            sql.append(" AND suspended = ?");
            params.add(suspended ? 1 : 0);
        }
        sql.append(" ORDER BY id ASC");

        return transactionExecutor.executeRead("search cards", connection -> {
            List<Card> cards = new ArrayList<>();
            try (PreparedStatement ps = connection.prepareStatement(sql.toString())) {
                for (int i = 0; i < params.size(); i++) {
                    ps.setObject(i + 1, params.get(i));
                }
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        cards.add(mapCard(rs));
                    }
                }
            }
            return cards;
        });
    }

    @Override
    public Card update(Card card) {
        Objects.requireNonNull(card, "card must not be null");
        if (card.id() == null) {
            throw new IllegalArgumentException("Card id must not be null for update");
        }

        String sql = """
                UPDATE cards
                SET deck_id = ?, card_type = ?, term = ?, reading = ?, meaning = ?, part_of_speech = ?,
                    level = ?, extra_data = ?, notes = ?, suspended = ?, updated_at = ?
                WHERE id = ?
                """;
        return transactionExecutor.executeInTransaction("update card", connection -> {
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                ps.setLong(1, card.deckId());
                ps.setString(2, card.cardType().name());
                ps.setString(3, card.term());
                setNullableString(ps, 4, card.reading());
                ps.setString(5, card.meaning());
                setNullableString(ps, 6, card.partOfSpeech());
                setNullableString(ps, 7, card.level() == null ? null : card.level().code());
                setNullableString(ps, 8, card.extraData());
                setNullableString(ps, 9, card.notes());
                ps.setInt(10, card.suspended() ? 1 : 0);
                ps.setString(11, card.updatedAt().toString());
                ps.setLong(12, card.id());
                int updated = ps.executeUpdate();
                if (updated == 0) {
                    throw new PersistenceException("Card not found for update: " + card.id());
                }
            }
            return card;
        });
    }

    @Override
    public boolean setSuspended(long cardId, boolean suspended, Instant updatedAt) {
        Objects.requireNonNull(updatedAt, "updatedAt must not be null");
        String sql = "UPDATE cards SET suspended = ?, updated_at = ? WHERE id = ?";
        return transactionExecutor.executeInTransaction("update card suspended flag", connection -> {
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                ps.setInt(1, suspended ? 1 : 0);
                ps.setString(2, updatedAt.toString());
                ps.setLong(3, cardId);
                return ps.executeUpdate() > 0;
            }
        });
    }

    @Override
    public boolean deleteById(long cardId) {
        String sql = "DELETE FROM cards WHERE id = ?";
        return transactionExecutor.executeInTransaction("delete card", connection -> {
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                ps.setLong(1, cardId);
                return ps.executeUpdate() > 0;
            }
        });
    }

    @Override
    public List<CardMeaning> findMeaningsByCardId(long cardId) {
        String sql = """
                SELECT id, card_id, language_code, meaning, display_order
                FROM card_meanings
                WHERE card_id = ?
                ORDER BY display_order ASC, id ASC
                """;
        return transactionExecutor.executeRead("find card meanings", connection -> {
            List<CardMeaning> meanings = new ArrayList<>();
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                ps.setLong(1, cardId);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        meanings.add(new CardMeaning(
                                rs.getLong("id"),
                                rs.getLong("card_id"),
                                DefinitionLanguage.fromCode(rs.getString("language_code")),
                                rs.getString("meaning"),
                                rs.getInt("display_order")
                        ));
                    }
                }
            }
            return meanings;
        });
    }

    @Override
    public List<CardMeaning> replaceMeanings(long cardId, List<CardMeaning> meanings) {
        List<CardMeaning> safeMeanings = meanings == null ? List.of() : meanings;
        return transactionExecutor.executeInTransaction("replace card meanings", connection -> {
            try (PreparedStatement deletePs = connection.prepareStatement(
                    "DELETE FROM card_meanings WHERE card_id = ?")) {
                deletePs.setLong(1, cardId);
                deletePs.executeUpdate();
            }
            return insertMeanings(connection, cardId, safeMeanings);
        });
    }

    @Override
    public List<CardExample> findExamplesByCardId(long cardId) {
        String sql = """
                SELECT id, card_id, sentence, translation, translation_language, display_order
                FROM card_examples
                WHERE card_id = ?
                ORDER BY display_order ASC, id ASC
                """;
        return transactionExecutor.executeRead("find card examples", connection -> {
            List<CardExample> examples = new ArrayList<>();
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                ps.setLong(1, cardId);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        String translationLang = rs.getString("translation_language");
                        examples.add(new CardExample(
                                rs.getLong("id"),
                                rs.getLong("card_id"),
                                rs.getString("sentence"),
                                rs.getString("translation"),
                                translationLang == null ? null : DefinitionLanguage.fromCode(translationLang),
                                rs.getInt("display_order")
                        ));
                    }
                }
            }
            return examples;
        });
    }

    @Override
    public List<CardExample> replaceExamples(long cardId, List<CardExample> examples) {
        List<CardExample> safeExamples = examples == null ? List.of() : examples;
        return transactionExecutor.executeInTransaction("replace card examples", connection -> {
            try (PreparedStatement deletePs = connection.prepareStatement(
                    "DELETE FROM card_examples WHERE card_id = ?")) {
                deletePs.setLong(1, cardId);
                deletePs.executeUpdate();
            }
            return insertExamples(connection, cardId, safeExamples);
        });
    }

    private long insertCard(Connection connection, Card card) throws SQLException {
        String sql = """
                INSERT INTO cards (
                    deck_id, card_type, term, reading, meaning, part_of_speech, level,
                    extra_data, notes, suspended, created_at, updated_at
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;
        try (PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setLong(1, card.deckId());
            ps.setString(2, card.cardType().name());
            ps.setString(3, card.term());
            setNullableString(ps, 4, card.reading());
            ps.setString(5, card.meaning());
            setNullableString(ps, 6, card.partOfSpeech());
            setNullableString(ps, 7, card.level() == null ? null : card.level().code());
            setNullableString(ps, 8, card.extraData());
            setNullableString(ps, 9, card.notes());
            ps.setInt(10, card.suspended() ? 1 : 0);
            ps.setString(11, card.createdAt().toString());
            ps.setString(12, card.updatedAt().toString());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (!keys.next()) {
                    throw new SQLException("Failed to retrieve generated card id");
                }
                return keys.getLong(1);
            }
        }
    }

    private List<CardMeaning> insertMeanings(
            Connection connection,
            long cardId,
            List<CardMeaning> meanings
    ) throws SQLException {
        if (meanings.isEmpty()) {
            return List.of();
        }
        String sql = """
                INSERT INTO card_meanings (card_id, language_code, meaning, display_order)
                VALUES (?, ?, ?, ?)
                """;
        List<CardMeaning> saved = new ArrayList<>(meanings.size());
        try (PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            for (CardMeaning meaning : meanings) {
                ps.setLong(1, cardId);
                ps.setString(2, meaning.languageCode().getCode());
                ps.setString(3, meaning.meaning());
                ps.setInt(4, meaning.displayOrder());
                ps.executeUpdate();
                try (ResultSet keys = ps.getGeneratedKeys()) {
                    if (!keys.next()) {
                        throw new SQLException("Failed to retrieve generated card_meaning id");
                    }
                    saved.add(new CardMeaning(
                            keys.getLong(1),
                            cardId,
                            meaning.languageCode(),
                            meaning.meaning(),
                            meaning.displayOrder()
                    ));
                }
            }
        }
        return saved;
    }

    private List<CardExample> insertExamples(
            Connection connection,
            long cardId,
            List<CardExample> examples
    ) throws SQLException {
        if (examples.isEmpty()) {
            return List.of();
        }
        String sql = """
                INSERT INTO card_examples (card_id, sentence, translation, translation_language, display_order)
                VALUES (?, ?, ?, ?, ?)
                """;
        List<CardExample> saved = new ArrayList<>(examples.size());
        try (PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            for (CardExample example : examples) {
                ps.setLong(1, cardId);
                ps.setString(2, example.sentence());
                setNullableString(ps, 3, example.translation());
                setNullableString(ps, 4, example.translationLanguage() == null
                        ? null : example.translationLanguage().getCode());
                ps.setInt(5, example.displayOrder());
                ps.executeUpdate();
                try (ResultSet keys = ps.getGeneratedKeys()) {
                    if (!keys.next()) {
                        throw new SQLException("Failed to retrieve generated card_example id");
                    }
                    saved.add(new CardExample(
                            keys.getLong(1),
                            cardId,
                            example.sentence(),
                            example.translation(),
                            example.translationLanguage(),
                            example.displayOrder()
                    ));
                }
            }
        }
        return saved;
    }

    private void insertReviewState(Connection connection, long cardId, ReviewState state) throws SQLException {
        String sql = """
                INSERT INTO review_states (
                    card_id, state, stability, difficulty, step, due_at, last_reviewed_at,
                    scheduled_days, elapsed_days, repetitions, lapses, updated_at
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setLong(1, cardId);
            ps.setString(2, state.state().name());
            ps.setDouble(3, state.stability());
            ps.setDouble(4, state.difficulty());
            if (state.step() == null) {
                ps.setNull(5, Types.INTEGER);
            } else {
                ps.setInt(5, state.step());
            }
            setNullableString(ps, 6, state.dueAt() == null ? null : state.dueAt().toString());
            setNullableString(ps, 7, state.lastReviewedAt() == null ? null : state.lastReviewedAt().toString());
            ps.setDouble(8, state.scheduledDays());
            ps.setDouble(9, state.elapsedDays());
            ps.setInt(10, state.repetitions());
            ps.setInt(11, state.lapses());
            ps.setString(12, state.updatedAt().toString());
            ps.executeUpdate();
        }
    }

    private Card mapCard(ResultSet rs) throws SQLException {
        String levelCode = rs.getString("level");
        return new Card(
                rs.getLong("id"),
                rs.getLong("deck_id"),
                CardType.fromCode(rs.getString("card_type")),
                rs.getString("term"),
                rs.getString("reading"),
                rs.getString("meaning"),
                rs.getString("part_of_speech"),
                levelCode == null ? null : ProficiencyLevel.of(levelCode),
                rs.getString("extra_data"),
                rs.getString("notes"),
                rs.getInt("suspended") == 1,
                Instant.parse(rs.getString("created_at")),
                Instant.parse(rs.getString("updated_at"))
        );
    }

    private void setNullableString(PreparedStatement ps, int index, String value) throws SQLException {
        if (value == null) {
            ps.setNull(index, Types.VARCHAR);
        } else {
            ps.setString(index, value);
        }
    }
}
