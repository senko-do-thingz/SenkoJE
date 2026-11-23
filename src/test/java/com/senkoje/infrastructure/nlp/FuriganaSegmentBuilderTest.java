package com.senkoje.infrastructure.nlp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/**
 * Kiểm thử đơn vị cho FuriganaSegmentBuilder: xác minh căn chỉnh cách đọc phía trên cụm Kanji,
 * giữ nguyên okurigana, Kana thuần, dấu câu và hỗ trợ chỉnh sửa cách đọc mơ hồ.
 */
class FuriganaSegmentBuilderTest {

    private final FuriganaSegmentBuilder builder = new FuriganaSegmentBuilder();

    @Test
    void shouldAttachReadingOnlyAbovePureKanjiRun() {
        FuriganaSegment segment = builder.buildSegment("日本語", "ニホンゴ");
        assertEquals(1, segment.parts().size());
        assertEquals(new FuriganaPart("日本語", "にほんご"), segment.parts().getFirst());
        assertEquals("日本語[にほんご]", segment.toBracketNotation());
    }

    @Test
    void shouldAlignOkuriganaWithoutRubyAboveTrailingKana() {
        FuriganaSegment taberu = builder.buildSegment("食べる", "たべる");
        assertEquals(
                List.of(new FuriganaPart("食", "た"), new FuriganaPart("べる", null)),
                taberu.parts()
        );
        assertEquals("食[た]べる", taberu.toBracketNotation());

        FuriganaSegment atarashii = builder.buildSegment("新しい", "アタラシイ");
        assertEquals(
                List.of(new FuriganaPart("新", "あたら"), new FuriganaPart("しい", null)),
                atarashii.parts()
        );
        assertEquals("新[あたら]しい", atarashii.toBracketNotation());
    }

    @Test
    void shouldAlignPrefixMiddleAndMultipleKanjiRunsCorrectly() {
        // Tiền tố Hiragana + Kanji + hậu tố Hiragana
        FuriganaSegment onegai = builder.buildSegment("お願い", "おねがい");
        assertEquals(
                List.of(new FuriganaPart("お", null), new FuriganaPart("願", "ねが"), new FuriganaPart("い", null)),
                onegai.parts()
        );

        // Nhiều cụm Kanji xen kẽ okurigana: 取り組み -> 取[と]り組[く]み
        FuriganaSegment torikumi = builder.buildSegment("取り組み", "とりくみ");
        assertEquals(
                List.of(
                        new FuriganaPart("取", "と"),
                        new FuriganaPart("り", null),
                        new FuriganaPart("組", "く"),
                        new FuriganaPart("み", null)
                ),
                torikumi.parts()
        );
        assertEquals("取[と]り組[く]み", torikumi.toBracketNotation());

        // Kanji kết hợp Katakana: 消しゴム -> 消[け]しゴム
        FuriganaSegment keshigomu = builder.buildSegment("消しゴム", "けしごむ");
        assertEquals(
                List.of(new FuriganaPart("消", "け"), new FuriganaPart("しゴム", null)),
                keshigomu.parts()
        );
    }

    @Test
    void shouldLeavePureKanaAndPunctuationWithoutFurigana() {
        FuriganaSegment kana = builder.buildSegment("ひらがな", "ひらがな");
        assertFalse(kana.hasFurigana());
        assertEquals(List.of(new FuriganaPart("ひらがな", null)), kana.parts());

        FuriganaSegment punct = builder.buildSegment("。", null);
        assertFalse(punct.hasFurigana());
        assertNull(punct.parts().getFirst().reading());
    }

    @Test
    void shouldAllowUserToOverrideAmbiguousReadingsBeforeSaving() {
        // Ví dụ từ có nhiều cách đọc theo ngữ cảnh: 今日 (きょう vs こんにち) hoặc 市場 (いちば vs しじょう)
        FuriganaSegment original = builder.buildSegment("市場", "シジョウ");
        assertEquals("市場[しじょう]", original.toBracketNotation());
        assertFalse(original.customEdited());

        FuriganaSegment edited = builder.withCustomReading(original, "いちば");
        assertTrue(edited.customEdited());
        assertEquals("いちば", edited.effectiveReading());
        assertEquals("市場[いちば]", edited.toBracketNotation());
    }

    @Test
    void shouldPreserveOriginalTextAndNewlinesEndToEndWithKuromoji() {
        JapaneseTokenizerService tokenizerService = new JapaneseTokenizerService();
        String text = "新しい本を読みました。\n明日は学校へ行きます。";

        List<FuriganaSegment> segments = builder.buildSegments(tokenizerService.tokenize(text));
        String reconstructed = segments.stream()
                .map(segment -> segment.parts().stream().map(FuriganaPart::text).collect(Collectors.joining()))
                .collect(Collectors.joining());
        assertEquals(text, reconstructed);
        assertTrue(segments.stream().anyMatch(FuriganaSegment::hasFurigana));
    }
}
