package com.senkoje.persistence.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.senkoje.domain.model.AppSetting;
import com.senkoje.domain.model.Card;
import com.senkoje.domain.model.CardExample;
import com.senkoje.domain.model.CardMeaning;
import com.senkoje.domain.model.CardState;
import com.senkoje.domain.model.CardType;
import com.senkoje.domain.model.Deck;
import com.senkoje.domain.model.DeckSettings;
import com.senkoje.domain.model.DefinitionLanguage;
import com.senkoje.domain.model.ProficiencyLevel;
import com.senkoje.domain.model.ReviewLog;
import com.senkoje.domain.model.ReviewRating;
import com.senkoje.domain.model.ReviewState;
import com.senkoje.domain.model.StudySession;
import com.senkoje.domain.model.TargetLanguage;
import com.senkoje.domain.repository.PersistenceException;
import com.senkoje.persistence.migration.DatabaseMigrator;
import com.senkoje.persistence.sqlite.SqliteConnectionManager;
import java.nio.file.Path;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Kiểm thử tích hợp SQLite cho toàn bộ 6 kho lưu trữ, giao dịch hoàn tác và ràng buộc khóa ngoại.
 */
class SqliteRepositoriesTest {

    @TempDir
    Path tempDir;

    private SqliteDeckRepository deckRepository;
    private SqliteCardRepository cardRepository;
    private SqliteReviewStateRepository reviewStateRepository;
    private SqliteReviewLogRepository reviewLogRepository;
    private SqliteStudySessionRepository studySessionRepository;
    private SqliteAppSettingRepository appSettingRepository;

    @BeforeEach
    void setUp() throws SQLException {
        SqliteConnectionManager manager = new SqliteConnectionManager(tempDir.resolve("repo-test.db"));
        new DatabaseMigrator(manager).migrate();
        deckRepository = new SqliteDeckRepository(manager);
        cardRepository = new SqliteCardRepository(manager);
        reviewStateRepository = new SqliteReviewStateRepository(manager);
        reviewLogRepository = new SqliteReviewLogRepository(manager);
        studySessionRepository = new SqliteStudySessionRepository(manager);
        appSettingRepository = new SqliteAppSettingRepository(manager);
    }

    @Test
    void shouldPerformCrudSearchAndDuplicationAcrossDeckAndCardRepositories() {
        Instant now = Instant.parse("2026-02-01T00:00:00Z");
        Deck createdDeck = deckRepository.createWithSettings(
                new Deck(null, "JLPT N5 Starter", TargetLanguage.JA, DefinitionLanguage.VI,
                        ProficiencyLevel.JLPT_N5, "Core vocabulary", now, now),
                DeckSettings.defaults(null)
        );
        assertNotNull(createdDeck.id());
        assertTrue(deckRepository.findSettingsByDeckId(createdDeck.id()).isPresent());

        Card createdCard = cardRepository.createWithDetails(
                new Card(null, createdDeck.id(), CardType.VOCABULARY, "飲む", "のむ", "uống",
                        "verb", ProficiencyLevel.JLPT_N5, "{\"jlpt\":\"N5\"}", "Godan verb", false, now, now),
                List.of(new CardMeaning(null, null, DefinitionLanguage.EN, "to drink", 0)),
                List.of(new CardExample(null, null, "水を飲みます。", "Tôi uống nước.", DefinitionLanguage.VI, 0)),
                ReviewState.initialNew(null, now)
        );
        assertNotNull(createdCard.id());
        assertEquals(1, cardRepository.findMeaningsByCardId(createdCard.id()).size());
        assertEquals(1, cardRepository.findExamplesByCardId(createdCard.id()).size());
        assertEquals(CardState.NEW, reviewStateRepository.findByCardId(createdCard.id()).orElseThrow().state());

        // Kiểm tra tìm kiếm và lọc bộ thẻ, thẻ học tập
        assertEquals(1, deckRepository.search("starter", TargetLanguage.JA, ProficiencyLevel.JLPT_N5).size());
        assertEquals(0, deckRepository.search("starter", TargetLanguage.EN, null).size());
        assertEquals(1, cardRepository.search(createdDeck.id(), "uống", CardType.VOCABULARY, false).size());

        // Kiểm tra tạm ngưng thẻ và cập nhật nghĩa bổ sung
        assertTrue(cardRepository.setSuspended(createdCard.id(), true, now.plusSeconds(60)));
        assertTrue(cardRepository.findById(createdCard.id()).orElseThrow().suspended());
        cardRepository.replaceMeanings(createdCard.id(), List.of(
                new CardMeaning(null, createdCard.id(), DefinitionLanguage.EN, "to drink", 0),
                new CardMeaning(null, createdCard.id(), DefinitionLanguage.VI, "uống nước", 1)
        ));
        assertEquals(2, cardRepository.findMeaningsByCardId(createdCard.id()).size());

        // Chuyển trạng thái thẻ gốc sang REVIEW rồi nhân bản bộ thẻ để kiểm tra thẻ nhân bản có trạng thái NEW mới
        reviewStateRepository.save(new ReviewState(
                createdCard.id(), CardState.REVIEW, 5.2, 4.1, null,
                now.plusSeconds(86400), now, 5.0, 1.0, 3, 0, now.plusSeconds(120)
        ));

        Deck duplicated = deckRepository.duplicateWithCards(createdDeck.id(), "JLPT N5 Copy", now.plusSeconds(300));
        assertNotNull(duplicated.id());
        List<Card> copiedCards = cardRepository.findByDeckId(duplicated.id());
        assertEquals(1, copiedCards.size());
        Card copiedCard = copiedCards.getFirst();
        assertEquals(2, cardRepository.findMeaningsByCardId(copiedCard.id()).size());
        assertEquals(1, cardRepository.findExamplesByCardId(copiedCard.id()).size());
        ReviewState copiedState = reviewStateRepository.findByCardId(copiedCard.id()).orElseThrow();
        assertEquals(CardState.NEW, copiedState.state());
        assertEquals(0, copiedState.repetitions());
    }

    @Test
    void shouldPersistReviewHistorySessionsAppSettingsAndEnforceCascadeDeletes() {
        Instant now = Instant.parse("2026-03-01T10:00:00Z");
        Deck deck = deckRepository.createWithSettings(
                new Deck(null, "English B1", TargetLanguage.EN, DefinitionLanguage.VI,
                        ProficiencyLevel.CEFR_B1, "Collocations", now, now),
                DeckSettings.defaults(null)
        );
        Card card = cardRepository.createWithDetails(
                new Card(null, deck.id(), CardType.PHRASE, "make a decision", null, "đưa ra quyết định",
                        "phrase", ProficiencyLevel.CEFR_B1, null, null, false, now, now),
                List.of(),
                List.of(),
                ReviewState.initialNew(null, now)
        );

        StudySession session = studySessionRepository.create(
                new StudySession(null, deck.id(), now, null, 0, 0)
        );
        StudySession completedSession = studySessionRepository.update(
                new StudySession(session.id(), deck.id(), now, now.plusSeconds(180), 1, 180)
        );
        assertEquals(1, studySessionRepository.findByDeckId(deck.id()).size());
        assertEquals(180, completedSession.durationSeconds());

        ReviewState dueState = reviewStateRepository.save(new ReviewState(
                card.id(), CardState.LEARNING, 1.8, 4.5, 0,
                now.plusSeconds(600), now, 0.0, 0.0, 1, 0, now
        ));
        assertEquals(1, reviewStateRepository.findDueByDeckId(deck.id(), now.plusSeconds(600)).size());
        assertEquals(1, reviewStateRepository.findByState(CardState.LEARNING).size());

        ReviewLog log = reviewLogRepository.append(new ReviewLog(
                null, card.id(), session.id(), ReviewRating.GOOD, now, 1100,
                CardState.NEW, CardState.LEARNING, 0.0, dueState.stability(),
                0.0, dueState.difficulty(), 0.0, 0.0, "FSRS-6", "default-v1"
        ));
        assertNotNull(log.id());
        assertEquals(1, reviewLogRepository.findByCardId(card.id()).size());
        assertEquals(1, reviewLogRepository.findBySessionId(session.id()).size());
        assertEquals(1, reviewLogRepository.findByReviewedAtBetween(now.minusSeconds(10), now.plusSeconds(10)).size());

        // Kiểm tra CRUD và upsert khóa duy nhất trên app_settings
        appSettingRepository.save(new AppSetting("ui.theme", "dark", now));
        appSettingRepository.save(new AppSetting("ui.theme", "light", now.plusSeconds(5)));
        assertEquals("light", appSettingRepository.findByKey("ui.theme").orElseThrow().settingValue());
        assertTrue(appSettingRepository.deleteByKey("ui.theme"));
        assertTrue(appSettingRepository.findByKey("ui.theme").isEmpty());

        // Kiểm tra xóa deck kéo theo xóa thẻ, trạng thái và nhật ký nhưng giữ phiên học với deck_id = NULL
        assertTrue(deckRepository.deleteById(deck.id()));
        assertTrue(cardRepository.findById(card.id()).isEmpty());
        assertTrue(reviewStateRepository.findByCardId(card.id()).isEmpty());
        assertTrue(reviewLogRepository.findByCardId(card.id()).isEmpty());
        assertNull(studySessionRepository.findById(session.id()).orElseThrow().deckId());
    }

    @Test
    void shouldRollbackMultiTableTransactionsAndEnforceForeignKeys() {
        Instant now = Instant.parse("2026-04-01T00:00:00Z");

        // Vi phạm CHECK constraint trên deck_settings phải hoàn tác luôn bản ghi decks vừa chèn
        DeckSettings invalidSettings = new DeckSettings(null, 1.5, 36500, 20, 200, "1m", "10m", true);
        assertThrows(PersistenceException.class, () -> deckRepository.createWithSettings(
                new Deck(null, "Rollback Deck", TargetLanguage.JA, DefinitionLanguage.VI,
                        ProficiencyLevel.JLPT_N4, null, now, now),
                invalidSettings
        ));
        assertTrue(deckRepository.findAll().isEmpty());

        // Vi phạm khóa ngoại khi tạo thẻ cho deck_id không tồn tại
        assertThrows(PersistenceException.class, () -> cardRepository.createWithDetails(
                new Card(null, 9999L, CardType.VOCABULARY, "犬", "いぬ", "con chó",
                        "noun", ProficiencyLevel.JLPT_N5, null, null, false, now, now),
                List.of(),
                List.of(),
                ReviewState.initialNew(null, now)
        ));

        // Vi phạm CHECK constraint trên card_meanings (displayOrder < 0) phải hoàn tác luôn bản ghi cards
        Deck validDeck = deckRepository.createWithSettings(
                new Deck(null, "Valid Deck", TargetLanguage.JA, DefinitionLanguage.VI,
                        ProficiencyLevel.JLPT_N5, null, now, now),
                DeckSettings.defaults(null)
        );
        assertThrows(PersistenceException.class, () -> cardRepository.createWithDetails(
                new Card(null, validDeck.id(), CardType.VOCABULARY, "猫", "ねこ", "con mèo",
                        "noun", ProficiencyLevel.JLPT_N5, null, null, false, now, now),
                List.of(new CardMeaning(null, null, DefinitionLanguage.EN, "cat", -5)),
                List.of(),
                ReviewState.initialNew(null, now)
        ));
        assertFalse(cardRepository.findByDeckId(validDeck.id()).iterator().hasNext());
    }
}
