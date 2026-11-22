package com.senkoje.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.senkoje.domain.model.Card;
import com.senkoje.domain.model.CardExample;
import com.senkoje.domain.model.CardMeaning;
import com.senkoje.domain.model.CardState;
import com.senkoje.domain.model.CardType;
import com.senkoje.domain.model.Deck;
import com.senkoje.domain.model.DefinitionLanguage;
import com.senkoje.domain.model.ProficiencyLevel;
import com.senkoje.domain.model.TargetLanguage;
import com.senkoje.domain.service.CardValidator;
import com.senkoje.domain.service.DeckSettingsValidator;
import com.senkoje.domain.service.DeckValidator;
import com.senkoje.domain.service.DomainValidationException;
import com.senkoje.persistence.migration.DatabaseMigrator;
import com.senkoje.persistence.repository.SqliteCardRepository;
import com.senkoje.persistence.repository.SqliteDeckRepository;
import com.senkoje.persistence.repository.SqliteReviewStateRepository;
import com.senkoje.persistence.sqlite.SqliteConnectionManager;
import java.nio.file.Path;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Kiểm thử đơn vị và tích hợp cho dịch vụ quản lý thẻ học tập CardService.
 */
class CardServiceTest {

    @TempDir
    Path tempDir;

    private DeckService deckService;
    private CardService cardService;
    private SqliteReviewStateRepository reviewStateRepository;
    private Deck deck;

    @BeforeEach
    void setUp() throws SQLException {
        SqliteConnectionManager manager = new SqliteConnectionManager(tempDir.resolve("card-service.db"));
        new DatabaseMigrator(manager).migrate();
        Clock fixedClock = Clock.fixed(Instant.parse("2026-05-02T09:00:00Z"), ZoneOffset.UTC);

        deckService = new DeckService(
                new SqliteDeckRepository(manager),
                new DeckValidator(),
                new DeckSettingsValidator(),
                fixedClock
        );
        cardService = new CardService(new SqliteCardRepository(manager), new CardValidator(), fixedClock);
        reviewStateRepository = new SqliteReviewStateRepository(manager);

        deck = deckService.createDeck(
                "Japanese Grammar N4", TargetLanguage.JA, DefinitionLanguage.VI,
                ProficiencyLevel.JLPT_N4, "Grammar patterns"
        );
    }

    @Test
    void shouldCreateEditSearchSuspendAndManageMeaningsAndExamples() {
        Card created = cardService.createCard(
                deck.id(),
                CardType.GRAMMAR,
                "〜てもいい",
                null,
                "có thể làm gì đó",
                "grammar",
                ProficiencyLevel.JLPT_N4,
                "{\"connection\":\"V-te\"}",
                "Permission expression",
                List.of(new CardMeaning(null, null, DefinitionLanguage.EN, "may; is allowed to", 0)),
                List.of(new CardExample(null, null, "ここに座ってもいいですか。",
                        "Tôi ngồi đây được không?", DefinitionLanguage.VI, 0))
        );

        assertEquals(CardState.NEW, reviewStateRepository.findByCardId(created.id()).orElseThrow().state());
        assertEquals(1, cardService.getMeanings(created.id()).size());
        assertEquals(1, cardService.getExamples(created.id()).size());

        Card edited = cardService.editCard(
                created.id(),
                CardType.GRAMMAR,
                "〜てもいいです",
                null,
                "được phép làm gì đó",
                "grammar",
                ProficiencyLevel.JLPT_N4,
                "{\"connection\":\"V-te\",\"polite\":true}",
                "Polite form"
        );
        assertEquals("〜てもいいです", edited.term());

        assertTrue(cardService.suspendCard(created.id()));
        assertEquals(1, cardService.searchCards(deck.id(), "được phép", CardType.GRAMMAR, true).size());
        assertTrue(cardService.unsuspendCard(created.id()));
        assertFalse(cardService.getCard(created.id()).orElseThrow().suspended());

        cardService.replaceMeanings(created.id(), List.of(
                new CardMeaning(null, created.id(), DefinitionLanguage.EN, "may", 0),
                new CardMeaning(null, created.id(), DefinitionLanguage.VI, "được phép", 1)
        ));
        assertEquals(2, cardService.getMeanings(created.id()).size());

        cardService.replaceExamples(created.id(), List.of(
                new CardExample(null, created.id(), "写真を撮ってもいいですか。",
                        "Tôi chụp ảnh được không?", DefinitionLanguage.VI, 0)
        ));
        assertEquals("写真を撮ってもいいですか。", cardService.getExamples(created.id()).getFirst().sentence());

        assertTrue(cardService.deleteCard(created.id()));
        assertTrue(cardService.getCard(created.id()).isEmpty());
    }

    @Test
    void shouldRejectInvalidCardJsonAndInvalidSubEntities() {
        assertThrows(DomainValidationException.class, () -> cardService.createCard(
                deck.id(), CardType.VOCABULARY, "学校", "がっこう", "trường học",
                "noun", ProficiencyLevel.JLPT_N5, "{invalid-json", null, List.of(), List.of()
        ));
        assertTrue(cardService.listCardsByDeck(deck.id()).isEmpty());

        Card valid = cardService.createCard(
                deck.id(), CardType.VOCABULARY, "先生", "せんせい", "giáo viên",
                "noun", ProficiencyLevel.JLPT_N5, null, null, List.of(), List.of()
        );
        assertThrows(DomainValidationException.class, () -> cardService.replaceMeanings(
                valid.id(), List.of(new CardMeaning(null, valid.id(), DefinitionLanguage.EN, "   ", 0))
        ));
        assertThrows(DomainValidationException.class, () -> cardService.replaceExamples(
                valid.id(), List.of(new CardExample(null, valid.id(), "", null, null, 0))
        ));
    }
}
