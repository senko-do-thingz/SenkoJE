package com.senkoje.infrastructure.importer;

import com.senkoje.application.mapper.CardExtraDataCodec;
import com.senkoje.domain.model.CardExample;
import com.senkoje.domain.model.CardMeaning;
import com.senkoje.domain.model.CardState;
import com.senkoje.domain.model.Deck;
import com.senkoje.domain.model.DeckSettings;
import com.senkoje.domain.model.ReviewState;
import com.senkoje.domain.repository.PersistenceException;
import com.senkoje.persistence.sqlite.SqliteConnectionManager;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;

/**
 * Thực thi quá trình ghi nhận dữ liệu nhập vào cơ sở dữ liệu SQLite trong một giao dịch nguyên tử (commit hoặc rollback).
 */
public class AtomicDeckImporter {

    private final SqliteConnectionManager connectionManager;
    private final Clock clock;

    /**
     * Khởi tạo bộ nhập nguyên tử với trình quản lý kết nối và đồng hồ hệ thống.
     *
     * @param connectionManager trình quản lý kết nối SQLite
     */
    public AtomicDeckImporter(SqliteConnectionManager connectionManager) {
        this(connectionManager, Clock.systemUTC());
    }

    /**
     * Khởi tạo bộ nhập nguyên tử với đồng hồ được tiêm vào phục vụ kiểm thử.
     *
     * @param connectionManager trình quản lý kết nối SQLite
     * @param clock nguồn cung cấp thời gian
     */
    public AtomicDeckImporter(SqliteConnectionManager connectionManager, Clock clock) {
        this.connectionManager = Objects.requireNonNull(connectionManager, "connectionManager must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    /**
     * Thực thi nhập dữ liệu từ báo cáo xem trước vào cơ sở dữ liệu trong một giao dịch SQLite đơn lẻ.
     * Nếu có bất kỳ lỗi nào xảy ra trong quá trình thêm thẻ, toàn bộ thay đổi sẽ được hoàn tác (rollback).
     *
     * @param previewReport báo cáo xem trước dữ liệu
     * @param targetDeckId mã bộ thẻ mục tiêu (nếu null sẽ tạo bộ thẻ mới từ candidateDeck)
     * @param strategy chiến lược xử lý thẻ bị trùng lặp
     * @return kết quả thực thi thao tác nhập
     */
    public ImportResult executeImport(
            ImportPreviewReport previewReport,
            Long targetDeckId,
            DuplicateStrategy strategy
    ) {
        Objects.requireNonNull(previewReport, "previewReport must not be null");
        DuplicateStrategy effectiveStrategy = strategy != null ? strategy : DuplicateStrategy.SKIP;

        try (Connection connection = connectionManager.openConnection()) {
            return executeImport(connection, previewReport, targetDeckId, effectiveStrategy);
        } catch (SQLException exception) {
            throw new PersistenceException("Failed to open connection for atomic deck import", exception);
        }
    }

    /**
     * Thực thi nhập dữ liệu trên một kết nối mở sẵn trong một giao dịch an toàn.
     *
     * @param connection kết nối SQLite hợp lệ
     * @param previewReport báo cáo xem trước
     * @param targetDeckId mã bộ thẻ mục tiêu (nếu null sẽ tạo bộ thẻ mới)
     * @param strategy chiến lược xử lý thẻ trùng lặp
     * @return kết quả thực thi
     */
    public ImportResult executeImport(
            Connection connection,
            ImportPreviewReport previewReport,
            Long targetDeckId,
            DuplicateStrategy strategy
    ) {
        Objects.requireNonNull(connection, "connection must not be null");
        Objects.requireNonNull(previewReport, "previewReport must not be null");
        DuplicateStrategy effectiveStrategy = strategy != null ? strategy : DuplicateStrategy.SKIP;

        boolean previousAutoCommit;
        try {
            previousAutoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
        } catch (SQLException exception) {
            throw new PersistenceException("Failed to start transaction for deck import", exception);
        }

        try {
            Instant now = clock.instant();
            long effectiveDeckId;

            if (targetDeckId != null) {
                effectiveDeckId = targetDeckId;
            } else {
                effectiveDeckId = createDeckInTransaction(connection, previewReport.candidateDeck(), previewReport.candidateSettings(), now);
            }

            int imported = 0;
            int skipped = 0;
            int rejected = 0;

            for (ImportPreviewItem item : previewReport.items()) {
                if (item.isInvalid()) {
                    rejected++;
                    continue;
                }

                if (item.isDuplicate()) {
                    if (effectiveStrategy == DuplicateStrategy.SKIP) {
                        skipped++;
                        continue;
                    } else if (effectiveStrategy == DuplicateStrategy.OVERWRITE) {
                        updateExistingCardInTransaction(connection, effectiveDeckId, item.card(), now);
                        imported++;
                        continue;
                    }
                    // Nếu là IMPORT_AS_DUPLICATE thì rơi xuống khối thêm mới bên dưới
                }

                insertCardInTransaction(connection, effectiveDeckId, item.card(), now);
                imported++;
            }

            connection.commit();
            return new ImportResult(imported, skipped, rejected, effectiveDeckId);
        } catch (SQLException | RuntimeException exception) {
            try {
                connection.rollback();
            } catch (SQLException rollbackException) {
                exception.addSuppressed(rollbackException);
            }
            if (exception instanceof PersistenceException persistenceException) {
                throw persistenceException;
            }
            throw new PersistenceException("Failed to execute atomic deck import, transaction rolled back", exception);
        } finally {
            try {
                connection.setAutoCommit(previousAutoCommit);
            } catch (SQLException ignored) {
            }
        }
    }

    private long createDeckInTransaction(
            Connection connection,
            Deck candidateDeck,
            DeckSettings candidateSettings,
            Instant now
    ) throws SQLException {
        String deckTitle = (candidateDeck != null && candidateDeck.title() != null)
                ? candidateDeck.title()
                : "Imported Deck " + now;
        String targetLang = (candidateDeck != null && candidateDeck.targetLanguage() != null)
                ? candidateDeck.targetLanguage().name()
                : "JA";
        String defLang = (candidateDeck != null && candidateDeck.definitionLanguage() != null)
                ? candidateDeck.definitionLanguage().getCode()
                : "vi";
        String level = (candidateDeck != null && candidateDeck.level() != null)
                ? candidateDeck.level().code()
                : "N5";
        String description = candidateDeck != null ? candidateDeck.description() : null;

        String insertDeckSql = """
                INSERT INTO decks (title, target_language, definition_language, level, description, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """;
        long deckId;
        try (PreparedStatement ps = connection.prepareStatement(insertDeckSql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, deckTitle);
            ps.setString(2, targetLang);
            ps.setString(3, defLang);
            ps.setString(4, level);
            ps.setString(5, description);
            ps.setString(6, now.toString());
            ps.setString(7, now.toString());
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (!rs.next()) {
                    throw new SQLException("Failed to obtain generated ID for new imported deck");
                }
                deckId = rs.getLong(1);
            }
        }

        DeckSettings settings = candidateSettings != null ? candidateSettings : DeckSettings.defaults(deckId);
        String insertSettingsSql = """
                INSERT INTO deck_settings (
                    deck_id, desired_retention, maximum_interval, new_cards_per_day,
                    maximum_reviews_per_day, learning_steps, relearning_steps, enable_fuzzing
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """;
        try (PreparedStatement ps = connection.prepareStatement(insertSettingsSql)) {
            ps.setLong(1, deckId);
            ps.setDouble(2, settings.desiredRetention());
            ps.setInt(3, settings.maximumInterval());
            ps.setInt(4, settings.newCardsPerDay());
            ps.setInt(5, settings.maximumReviewsPerDay());
            ps.setString(6, settings.learningSteps());
            ps.setString(7, settings.relearningSteps());
            ps.setInt(8, settings.enableFuzzing() ? 1 : 0);
            ps.executeUpdate();
        }

        return deckId;
    }

    private void insertCardInTransaction(
            Connection connection,
            long deckId,
            ImportCandidateCard card,
            Instant now
    ) throws SQLException {
        String insertCardSql = """
                INSERT INTO cards (
                    deck_id, card_type, term, reading, meaning, part_of_speech,
                    level, extra_data, notes, suspended, created_at, updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 0, ?, ?)
                """;
        long cardId;
        try (PreparedStatement ps = connection.prepareStatement(insertCardSql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setLong(1, deckId);
            ps.setString(2, card.cardType() != null ? card.cardType().name() : "VOCABULARY");
            ps.setString(3, card.term());
            ps.setString(4, card.reading());
            ps.setString(5, card.meaning());
            ps.setString(6, card.partOfSpeech());
            ps.setString(7, card.level() != null ? card.level().code() : null);
            ps.setString(8, CardExtraDataCodec.toJson(card.extraData()));
            ps.setString(9, card.notes());
            ps.setString(10, now.toString());
            ps.setString(11, now.toString());
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (!rs.next()) {
                    throw new SQLException("Failed to obtain generated ID for imported card");
                }
                cardId = rs.getLong(1);
            }
        }

        if (!card.meanings().isEmpty()) {
            String insertMeaningSql = """
                    INSERT INTO card_meanings (card_id, language_code, meaning, display_order)
                    VALUES (?, ?, ?, ?)
                    """;
            try (PreparedStatement ps = connection.prepareStatement(insertMeaningSql)) {
                for (CardMeaning meaning : card.meanings()) {
                    ps.setLong(1, cardId);
                    ps.setString(2, meaning.languageCode() != null ? meaning.languageCode().getCode() : "vi");
                    ps.setString(3, meaning.meaning());
                    ps.setInt(4, meaning.displayOrder());
                    ps.addBatch();
                }
                ps.executeBatch();
            }
        }

        if (!card.examples().isEmpty()) {
            String insertExampleSql = """
                    INSERT INTO card_examples (card_id, sentence, translation, translation_language, display_order)
                    VALUES (?, ?, ?, ?, ?)
                    """;
            try (PreparedStatement ps = connection.prepareStatement(insertExampleSql)) {
                for (CardExample example : card.examples()) {
                    ps.setLong(1, cardId);
                    ps.setString(2, example.sentence());
                    ps.setString(3, example.translation());
                    ps.setString(4, example.translationLanguage() != null ? example.translationLanguage().getCode() : "vi");
                    ps.setInt(5, example.displayOrder());
                    ps.addBatch();
                }
                ps.executeBatch();
            }
        }

        // Khởi tạo trạng thái FSRS-6 NEW cho thẻ mới
        String insertReviewStateSql = """
                INSERT INTO review_states (
                    card_id, state, stability, difficulty, step, due_at,
                    last_reviewed_at, scheduled_days, elapsed_days, repetitions, lapses, updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;
        try (PreparedStatement ps = connection.prepareStatement(insertReviewStateSql)) {
            ps.setLong(1, cardId);
            ps.setString(2, CardState.NEW.name());
            ps.setDouble(3, 0.0);
            ps.setDouble(4, 0.0);
            ps.setNull(5, Types.INTEGER);
            ps.setString(6, now.toString());
            ps.setNull(7, Types.VARCHAR);
            ps.setDouble(8, 0.0);
            ps.setDouble(9, 0.0);
            ps.setInt(10, 0);
            ps.setInt(11, 0);
            ps.setString(12, now.toString());
            ps.executeUpdate();
        }
    }

    private void updateExistingCardInTransaction(
            Connection connection,
            long deckId,
            ImportCandidateCard card,
            Instant now
    ) throws SQLException {
        String updateCardSql = """
                UPDATE cards
                SET reading = ?, meaning = ?, part_of_speech = ?, level = ?,
                    extra_data = ?, notes = ?, updated_at = ?
                WHERE deck_id = ? AND LOWER(term) = LOWER(?)
                """;
        try (PreparedStatement ps = connection.prepareStatement(updateCardSql)) {
            ps.setString(1, card.reading());
            ps.setString(2, card.meaning());
            ps.setString(3, card.partOfSpeech());
            ps.setString(4, card.level() != null ? card.level().code() : null);
            ps.setString(5, CardExtraDataCodec.toJson(card.extraData()));
            ps.setString(6, card.notes());
            ps.setString(7, now.toString());
            ps.setLong(8, deckId);
            ps.setString(9, card.term());
            ps.executeUpdate();
        }
    }
}
