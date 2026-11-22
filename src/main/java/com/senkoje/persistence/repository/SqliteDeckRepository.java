package com.senkoje.persistence.repository;

import com.senkoje.domain.model.CardState;
import com.senkoje.domain.model.Deck;
import com.senkoje.domain.model.DeckSettings;
import com.senkoje.domain.model.DefinitionLanguage;
import com.senkoje.domain.model.ProficiencyLevel;
import com.senkoje.domain.model.TargetLanguage;
import com.senkoje.domain.repository.DeckRepository;
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
 * Triển khai SQLite cho kho lưu trữ bộ thẻ và cấu hình FSRS-6 bằng PreparedStatement và giao dịch tường minh.
 */
public class SqliteDeckRepository implements DeckRepository {

    private final SqliteTransactionExecutor transactionExecutor;

    /**
     * Khởi tạo kho lưu trữ bộ thẻ sử dụng trình quản lý kết nối SQLite dùng chung.
     *
     * @param connectionManager trình quản lý kết nối SQLite
     */
    public SqliteDeckRepository(SqliteConnectionManager connectionManager) {
        this.transactionExecutor = new SqliteTransactionExecutor(connectionManager);
    }

    @Override
    public Deck createWithSettings(Deck deck, DeckSettings settings) {
        Objects.requireNonNull(deck, "deck must not be null");
        Objects.requireNonNull(settings, "settings must not be null");

        return transactionExecutor.executeInTransaction("create deck with settings", connection -> {
            long deckId = insertDeck(connection, deck);
            DeckSettings settingsWithDeckId = new DeckSettings(
                    deckId,
                    settings.desiredRetention(),
                    settings.maximumInterval(),
                    settings.newCardsPerDay(),
                    settings.maximumReviewsPerDay(),
                    settings.learningSteps(),
                    settings.relearningSteps(),
                    settings.enableFuzzing()
            );
            upsertDeckSettings(connection, settingsWithDeckId);
            return new Deck(
                    deckId,
                    deck.title(),
                    deck.targetLanguage(),
                    deck.definitionLanguage(),
                    deck.level(),
                    deck.description(),
                    deck.createdAt(),
                    deck.updatedAt()
            );
        });
    }

    @Override
    public Optional<Deck> findById(long deckId) {
        String sql = """
                SELECT id, title, target_language, definition_language, level, description, created_at, updated_at
                FROM decks
                WHERE id = ?
                """;
        return transactionExecutor.executeRead("find deck by id", connection -> {
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                ps.setLong(1, deckId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return Optional.of(mapDeck(rs));
                    }
                    return Optional.empty();
                }
            }
        });
    }

    @Override
    public List<Deck> findAll() {
        String sql = """
                SELECT id, title, target_language, definition_language, level, description, created_at, updated_at
                FROM decks
                ORDER BY id ASC
                """;
        return transactionExecutor.executeRead("list all decks", connection -> {
            List<Deck> results = new ArrayList<>();
            try (PreparedStatement ps = connection.prepareStatement(sql);
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    results.add(mapDeck(rs));
                }
            }
            return results;
        });
    }

    @Override
    public List<Deck> search(String query, TargetLanguage targetLanguage, ProficiencyLevel level) {
        StringBuilder sql = new StringBuilder("""
                SELECT id, title, target_language, definition_language, level, description, created_at, updated_at
                FROM decks
                WHERE 1 = 1
                """);
        List<Object> params = new ArrayList<>();

        if (query != null && !query.isBlank()) {
            sql.append(" AND (LOWER(title) LIKE ? OR LOWER(COALESCE(description, '')) LIKE ?)");
            String pattern = "%" + query.trim().toLowerCase(Locale.ROOT) + "%";
            params.add(pattern);
            params.add(pattern);
        }
        if (targetLanguage != null) {
            sql.append(" AND target_language = ?");
            params.add(targetLanguage.name());
        }
        if (level != null) {
            sql.append(" AND level = ?");
            params.add(level.code());
        }
        sql.append(" ORDER BY id ASC");

        return transactionExecutor.executeRead("search decks", connection -> {
            List<Deck> results = new ArrayList<>();
            try (PreparedStatement ps = connection.prepareStatement(sql.toString())) {
                for (int i = 0; i < params.size(); i++) {
                    ps.setObject(i + 1, params.get(i));
                }
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        results.add(mapDeck(rs));
                    }
                }
            }
            return results;
        });
    }

    @Override
    public Deck update(Deck deck) {
        Objects.requireNonNull(deck, "deck must not be null");
        if (deck.id() == null) {
            throw new IllegalArgumentException("Deck id must not be null for update");
        }

        String sql = """
                UPDATE decks
                SET title = ?, target_language = ?, definition_language = ?, level = ?, description = ?, updated_at = ?
                WHERE id = ?
                """;
        return transactionExecutor.executeInTransaction("update deck", connection -> {
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                ps.setString(1, deck.title());
                ps.setString(2, deck.targetLanguage().name());
                ps.setString(3, deck.definitionLanguage().getCode());
                ps.setString(4, deck.level().code());
                setNullableString(ps, 5, deck.description());
                ps.setString(6, deck.updatedAt().toString());
                ps.setLong(7, deck.id());
                int updated = ps.executeUpdate();
                if (updated == 0) {
                    throw new PersistenceException("Deck not found for update: " + deck.id());
                }
            }
            return deck;
        });
    }

    @Override
    public boolean deleteById(long deckId) {
        String sql = "DELETE FROM decks WHERE id = ?";
        return transactionExecutor.executeInTransaction("delete deck", connection -> {
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                ps.setLong(1, deckId);
                return ps.executeUpdate() > 0;
            }
        });
    }

    @Override
    public Optional<DeckSettings> findSettingsByDeckId(long deckId) {
        String sql = """
                SELECT deck_id, desired_retention, maximum_interval, new_cards_per_day,
                       maximum_reviews_per_day, learning_steps, relearning_steps, enable_fuzzing
                FROM deck_settings
                WHERE deck_id = ?
                """;
        return transactionExecutor.executeRead("find deck settings", connection -> {
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                ps.setLong(1, deckId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return Optional.of(mapDeckSettings(rs));
                    }
                    return Optional.empty();
                }
            }
        });
    }

    @Override
    public DeckSettings saveSettings(DeckSettings settings) {
        Objects.requireNonNull(settings, "settings must not be null");
        if (settings.deckId() == null) {
            throw new IllegalArgumentException("DeckSettings deckId must not be null");
        }
        return transactionExecutor.executeInTransaction("save deck settings", connection -> {
            upsertDeckSettings(connection, settings);
            return settings;
        });
    }

    @Override
    public Deck duplicateWithCards(long sourceDeckId, String newTitle, Instant now) {
        Objects.requireNonNull(newTitle, "newTitle must not be null");
        Objects.requireNonNull(now, "now must not be null");

        return transactionExecutor.executeInTransaction("duplicate deck with cards", connection -> {
            Deck sourceDeck = findDeckInConnection(connection, sourceDeckId)
                    .orElseThrow(() -> new PersistenceException("Source deck not found: " + sourceDeckId));

            Deck duplicatedDeckInput = new Deck(
                    null,
                    newTitle,
                    sourceDeck.targetLanguage(),
                    sourceDeck.definitionLanguage(),
                    sourceDeck.level(),
                    sourceDeck.description(),
                    now,
                    now
            );
            long newDeckId = insertDeck(connection, duplicatedDeckInput);

            // Sao chép cấu hình bộ thẻ nguồn hoặc dùng cấu hình mặc định nếu chưa có
            DeckSettings sourceSettings = findSettingsInConnection(connection, sourceDeckId)
                    .orElse(DeckSettings.defaults(newDeckId));
            DeckSettings copiedSettings = new DeckSettings(
                    newDeckId,
                    sourceSettings.desiredRetention(),
                    sourceSettings.maximumInterval(),
                    sourceSettings.newCardsPerDay(),
                    sourceSettings.maximumReviewsPerDay(),
                    sourceSettings.learningSteps(),
                    sourceSettings.relearningSteps(),
                    sourceSettings.enableFuzzing()
            );
            upsertDeckSettings(connection, copiedSettings);

            // Sao chép toàn bộ thẻ, nghĩa bổ sung, ví dụ và khởi tạo trạng thái NEW mới cho từng thẻ
            copyCardsForDuplicatedDeck(connection, sourceDeckId, newDeckId, now);

            return new Deck(
                    newDeckId,
                    duplicatedDeckInput.title(),
                    duplicatedDeckInput.targetLanguage(),
                    duplicatedDeckInput.definitionLanguage(),
                    duplicatedDeckInput.level(),
                    duplicatedDeckInput.description(),
                    now,
                    now
            );
        });
    }

    private long insertDeck(Connection connection, Deck deck) throws SQLException {
        String sql = """
                INSERT INTO decks (title, target_language, definition_language, level, description, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """;
        try (PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, deck.title());
            ps.setString(2, deck.targetLanguage().name());
            ps.setString(3, deck.definitionLanguage().getCode());
            ps.setString(4, deck.level().code());
            setNullableString(ps, 5, deck.description());
            ps.setString(6, deck.createdAt().toString());
            ps.setString(7, deck.updatedAt().toString());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (!keys.next()) {
                    throw new SQLException("Failed to retrieve generated deck id");
                }
                return keys.getLong(1);
            }
        }
    }

    private void upsertDeckSettings(Connection connection, DeckSettings settings) throws SQLException {
        String sql = """
                INSERT INTO deck_settings (
                    deck_id, desired_retention, maximum_interval, new_cards_per_day,
                    maximum_reviews_per_day, learning_steps, relearning_steps, enable_fuzzing
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT(deck_id) DO UPDATE SET
                    desired_retention = excluded.desired_retention,
                    maximum_interval = excluded.maximum_interval,
                    new_cards_per_day = excluded.new_cards_per_day,
                    maximum_reviews_per_day = excluded.maximum_reviews_per_day,
                    learning_steps = excluded.learning_steps,
                    relearning_steps = excluded.relearning_steps,
                    enable_fuzzing = excluded.enable_fuzzing
                """;
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setLong(1, settings.deckId());
            ps.setDouble(2, settings.desiredRetention());
            ps.setInt(3, settings.maximumInterval());
            ps.setInt(4, settings.newCardsPerDay());
            ps.setInt(5, settings.maximumReviewsPerDay());
            ps.setString(6, settings.learningSteps());
            ps.setString(7, settings.relearningSteps());
            ps.setInt(8, settings.enableFuzzing() ? 1 : 0);
            ps.executeUpdate();
        }
    }

    private void copyCardsForDuplicatedDeck(
            Connection connection,
            long sourceDeckId,
            long newDeckId,
            Instant now
    ) throws SQLException {
        String selectCardsSql = """
                SELECT id, card_type, term, reading, meaning, part_of_speech, level, extra_data, notes, suspended
                FROM cards
                WHERE deck_id = ?
                ORDER BY id ASC
                """;
        String insertCardSql = """
                INSERT INTO cards (
                    deck_id, card_type, term, reading, meaning, part_of_speech, level,
                    extra_data, notes, suspended, created_at, updated_at
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;
        String copyMeaningsSql = """
                INSERT INTO card_meanings (card_id, language_code, meaning, display_order)
                SELECT ?, language_code, meaning, display_order
                FROM card_meanings
                WHERE card_id = ?
                ORDER BY display_order ASC, id ASC
                """;
        String copyExamplesSql = """
                INSERT INTO card_examples (card_id, sentence, translation, translation_language, display_order)
                SELECT ?, sentence, translation, translation_language, display_order
                FROM card_examples
                WHERE card_id = ?
                ORDER BY display_order ASC, id ASC
                """;
        String insertFreshStateSql = """
                INSERT INTO review_states (
                    card_id, state, stability, difficulty, step, due_at, last_reviewed_at,
                    scheduled_days, elapsed_days, repetitions, lapses, updated_at
                )
                VALUES (?, ?, 0.0, 0.0, NULL, NULL, NULL, 0.0, 0.0, 0, 0, ?)
                """;

        try (PreparedStatement selectCards = connection.prepareStatement(selectCardsSql);
             PreparedStatement insertCard = connection.prepareStatement(insertCardSql, Statement.RETURN_GENERATED_KEYS);
             PreparedStatement copyMeanings = connection.prepareStatement(copyMeaningsSql);
             PreparedStatement copyExamples = connection.prepareStatement(copyExamplesSql);
             PreparedStatement insertFreshState = connection.prepareStatement(insertFreshStateSql)) {
            selectCards.setLong(1, sourceDeckId);
            try (ResultSet rs = selectCards.executeQuery()) {
                while (rs.next()) {
                    long sourceCardId = rs.getLong("id");

                    insertCard.setLong(1, newDeckId);
                    insertCard.setString(2, rs.getString("card_type"));
                    insertCard.setString(3, rs.getString("term"));
                    setNullableString(insertCard, 4, rs.getString("reading"));
                    insertCard.setString(5, rs.getString("meaning"));
                    setNullableString(insertCard, 6, rs.getString("part_of_speech"));
                    setNullableString(insertCard, 7, rs.getString("level"));
                    setNullableString(insertCard, 8, rs.getString("extra_data"));
                    setNullableString(insertCard, 9, rs.getString("notes"));
                    insertCard.setInt(10, rs.getInt("suspended"));
                    insertCard.setString(11, now.toString());
                    insertCard.setString(12, now.toString());
                    insertCard.executeUpdate();

                    long newCardId;
                    try (ResultSet keys = insertCard.getGeneratedKeys()) {
                        if (!keys.next()) {
                            throw new SQLException("Failed to retrieve generated duplicated card id");
                        }
                        newCardId = keys.getLong(1);
                    }

                    copyMeanings.setLong(1, newCardId);
                    copyMeanings.setLong(2, sourceCardId);
                    copyMeanings.executeUpdate();

                    copyExamples.setLong(1, newCardId);
                    copyExamples.setLong(2, sourceCardId);
                    copyExamples.executeUpdate();

                    insertFreshState.setLong(1, newCardId);
                    insertFreshState.setString(2, CardState.NEW.name());
                    insertFreshState.setString(3, now.toString());
                    insertFreshState.executeUpdate();
                }
            }
        }
    }

    private Optional<Deck> findDeckInConnection(Connection connection, long deckId) throws SQLException {
        String sql = """
                SELECT id, title, target_language, definition_language, level, description, created_at, updated_at
                FROM decks
                WHERE id = ?
                """;
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setLong(1, deckId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapDeck(rs));
                }
                return Optional.empty();
            }
        }
    }

    private Optional<DeckSettings> findSettingsInConnection(Connection connection, long deckId) throws SQLException {
        String sql = """
                SELECT deck_id, desired_retention, maximum_interval, new_cards_per_day,
                       maximum_reviews_per_day, learning_steps, relearning_steps, enable_fuzzing
                FROM deck_settings
                WHERE deck_id = ?
                """;
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setLong(1, deckId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapDeckSettings(rs));
                }
                return Optional.empty();
            }
        }
    }

    private Deck mapDeck(ResultSet rs) throws SQLException {
        return new Deck(
                rs.getLong("id"),
                rs.getString("title"),
                TargetLanguage.fromCode(rs.getString("target_language")),
                DefinitionLanguage.fromCode(rs.getString("definition_language")),
                ProficiencyLevel.of(rs.getString("level")),
                rs.getString("description"),
                Instant.parse(rs.getString("created_at")),
                Instant.parse(rs.getString("updated_at"))
        );
    }

    private DeckSettings mapDeckSettings(ResultSet rs) throws SQLException {
        return new DeckSettings(
                rs.getLong("deck_id"),
                rs.getDouble("desired_retention"),
                rs.getInt("maximum_interval"),
                rs.getInt("new_cards_per_day"),
                rs.getInt("maximum_reviews_per_day"),
                rs.getString("learning_steps"),
                rs.getString("relearning_steps"),
                rs.getInt("enable_fuzzing") == 1
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
