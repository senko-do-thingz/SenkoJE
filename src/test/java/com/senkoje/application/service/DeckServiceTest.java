package com.senkoje.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.senkoje.domain.model.Card;
import com.senkoje.domain.model.CardState;
import com.senkoje.domain.model.CardType;
import com.senkoje.domain.model.Deck;
import com.senkoje.domain.model.DeckSettings;
import com.senkoje.domain.model.DefinitionLanguage;
import com.senkoje.domain.model.ProficiencyLevel;
import com.senkoje.domain.model.ReviewState;
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
 * Kiểm thử đơn vị và tích hợp cho dịch vụ quản lý bộ thẻ DeckService.
 */
class DeckServiceTest {

    @TempDir
    Path tempDir;

    private DeckService deckService;
    private CardService cardService;
    private SqliteReviewStateRepository reviewStateRepository;
    private Instant fixedNow;

    @BeforeEach
    void setUp() throws SQLException {
        SqliteConnectionManager manager = new SqliteConnectionManager(tempDir.resolve("deck-service.db"));
        new DatabaseMigrator(manager).migrate();
        fixedNow = Instant.parse("2026-05-01T08:00:00Z");
        Clock fixedClock = Clock.fixed(fixedNow, ZoneOffset.UTC);

        SqliteDeckRepository deckRepository = new SqliteDeckRepository(manager);
        SqliteCardRepository cardRepository = new SqliteCardRepository(manager);
        reviewStateRepository = new SqliteReviewStateRepository(manager);

        deckService = new DeckService(
                deckRepository,
                new DeckValidator(),
                new DeckSettingsValidator(),
                fixedClock
        );
        cardService = new CardService(cardRepository, new CardValidator(), fixedClock);
    }

    @Test
    void shouldCreateRenameEditSearchUpdateSettingsAndDuplicateDeck() {
        Deck deck = deckService.createDeck(
                "  JLPT N5 Core  ",
                TargetLanguage.JA,
                DefinitionLanguage.VI,
                ProficiencyLevel.JLPT_N5,
                "Initial description"
        );
        assertEquals("JLPT N5 Core", deck.title());
        assertEquals(0.90, deckService.getDeckSettings(deck.id()).orElseThrow().desiredRetention());

        Deck renamed = deckService.renameDeck(deck.id(), "JLPT N5 Essential");
        assertEquals("JLPT N5 Essential", renamed.title());

        Deck edited = deckService.editDescription(deck.id(), "Updated description");
        assertEquals("Updated description", edited.description());

        DeckSettings updatedSettings = deckService.updateDeckSettings(new DeckSettings(
                deck.id(), 0.88, 180, 15, 150, "1m,5m", "10m", false
        ));
        assertEquals(0.88, updatedSettings.desiredRetention());

        Card originalCard = cardService.createCard(
                deck.id(), CardType.VOCABULARY, "本", "ほん", "sách",
                "noun", ProficiencyLevel.JLPT_N5, null, null, List.of(), List.of()
        );
        reviewStateRepository.save(new ReviewState(
                originalCard.id(), CardState.REVIEW, 10.0, 3.5, null,
                fixedNow.plusSeconds(86400), fixedNow, 10.0, 2.0, 4, 0, fixedNow
        ));

        Deck duplicated = deckService.duplicateDeck(deck.id(), "JLPT N5 Backup");
        assertEquals(0.88, deckService.getDeckSettings(duplicated.id()).orElseThrow().desiredRetention());
        List<Card> duplicatedCards = cardService.listCardsByDeck(duplicated.id());
        assertEquals(1, duplicatedCards.size());
        assertEquals(CardState.NEW,
                reviewStateRepository.findByCardId(duplicatedCards.getFirst().id()).orElseThrow().state());

        assertEquals(2, deckService.searchDecks("JLPT", TargetLanguage.JA, ProficiencyLevel.JLPT_N5).size());
        assertTrue(deckService.deleteDeck(duplicated.id()));
        assertEquals(1, deckService.listAllDecks().size());
    }

    @Test
    void shouldRejectInvalidDeckAndSettingsInputsBeforePersistence() {
        assertThrows(DomainValidationException.class, () -> deckService.createDeck(
                "   ", TargetLanguage.JA, DefinitionLanguage.VI, ProficiencyLevel.JLPT_N5, null
        ));
        assertTrue(deckService.listAllDecks().isEmpty());

        Deck deck = deckService.createDeck(
                "English A2", TargetLanguage.EN, DefinitionLanguage.VI, ProficiencyLevel.CEFR_A2, null
        );
        assertThrows(DomainValidationException.class, () -> deckService.updateDeckSettings(
                new DeckSettings(deck.id(), 0.40, 36500, 20, 200, "1m", "10m", true)
        ));
        assertEquals(0.90, deckService.getDeckSettings(deck.id()).orElseThrow().desiredRetention());
    }
}
