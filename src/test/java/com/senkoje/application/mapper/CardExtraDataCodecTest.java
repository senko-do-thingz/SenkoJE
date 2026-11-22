package com.senkoje.application.mapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.senkoje.app.ApplicationContainer;
import com.senkoje.application.dto.CardExtraData;
import com.senkoje.domain.model.Card;
import com.senkoje.domain.model.CardExample;
import com.senkoje.domain.model.CardMeaning;
import com.senkoje.domain.model.CardType;
import com.senkoje.domain.model.Deck;
import com.senkoje.domain.model.DefinitionLanguage;
import com.senkoje.domain.model.ProficiencyLevel;
import com.senkoje.domain.model.TargetLanguage;
import com.senkoje.infrastructure.config.AppDataPathResolver;
import java.nio.file.Path;
import java.sql.SQLException;
import java.time.Clock;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Kiểm thử chuyển đổi JSON dữ liệu mở rộng thẻ (tiếng Nhật, tiếng Anh, ngữ pháp)
 * và tích hợp với CardService cho nhiều nghĩa bổ sung và nhiều câu ví dụ.
 */
class CardExtraDataCodecTest {

    @Test
    void shouldRoundTripJapaneseEnglishAndGrammarExtraFields() {
        // Kiểm tra tuần tự hóa và giải tuần tự hóa giữ nguyên đầy đủ ký tự Unicode tiếng Nhật và IPA
        CardExtraData original = new CardExtraData(
                "た[食]べる", "食", "ショク", "た.べる", "2",
                "/ˈiː.tɪŋ/", "eat out, eat up", "neutral",
                "〜てみる", "Thử làm một hành động", "V-て + みる", "試す"
        );
        String json = CardExtraDataCodec.toJson(original);
        assertNotNull(json);

        CardExtraData decoded = CardExtraDataCodec.fromJson(json);
        assertEquals(original, decoded);
        assertNull(CardExtraDataCodec.toJson(CardExtraData.empty()));
        assertTrue(CardExtraDataCodec.fromJson("   ").isEmpty());
    }

    @Test
    void shouldPersistExtraDataMultipleMeaningsExamplesAndSuspendState(@TempDir Path tempDir) throws SQLException {
        // Xác minh CardService lưu được JSON extra_data, nhiều nghĩa bổ sung, nhiều câu ví dụ và trạng thái tạm ngưng
        try (ApplicationContainer container = ApplicationContainer.createForDatabase(
                tempDir.resolve("card-browser.db"),
                new AppDataPathResolver(),
                Clock.system(ApplicationContainer.DEFAULT_TIMEZONE)
        )) {
            Deck deck = container.getDeckService().createDeck(
                    "JLPT N4 Grammar", TargetLanguage.JA, DefinitionLanguage.VI, ProficiencyLevel.JLPT_N4, "Grammar");
            CardExtraData grammarExtra = new CardExtraData(
                    null, null, null, null, null, null, null, null,
                    "〜てみる", "Diễn tả việc thử làm gì đó", "Động từ thể て + みる", "試す"
            );
            String extraJson = CardExtraDataCodec.toJson(grammarExtra);

            Card created = container.getCardService().createCard(
                    deck.id(),
                    CardType.GRAMMAR,
                    "〜てみる",
                    "てみる",
                    "thử làm gì đó",
                    "grammar",
                    ProficiencyLevel.JLPT_N4,
                    extraJson,
                    "Dùng phổ biến trong hội thoại",
                    List.of(
                            new CardMeaning(null, null, DefinitionLanguage.VI, "làm thử để xem kết quả", 0),
                            new CardMeaning(null, null, DefinitionLanguage.EN, "try doing something", 1)
                    ),
                    List.of(
                            new CardExample(null, null, "日本語で話してみます。",
                                    "Tôi sẽ thử nói bằng tiếng Nhật.", DefinitionLanguage.VI, 0),
                            new CardExample(null, null, "この料理を食べてみてください。",
                                    "Please try eating this dish.", DefinitionLanguage.EN, 1)
                    )
            );

            assertEquals(2, container.getCardService().getMeanings(created.id()).size());
            assertEquals(2, container.getCardService().getExamples(created.id()).size());
            assertEquals("〜てみる", CardExtraDataCodec.fromJson(created.extraData()).grammarPattern());

            assertTrue(container.getCardService().suspendCard(created.id()));
            assertEquals(1, container.getCardService().searchCards(deck.id(), null, CardType.GRAMMAR, true).size());
            assertTrue(container.getCardService().unsuspendCard(created.id()));
            assertEquals(1, container.getCardService().searchCards(deck.id(), null, CardType.GRAMMAR, false).size());
        }
    }
}
