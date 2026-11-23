package com.senkoje.infrastructure.nlp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.atilika.kuromoji.ipadic.Tokenizer;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/**
 * Kiểm thử đơn vị cho JapaneseTokenizerService và KanaConverter:
 * xác minh cơ chế tải lười đa luồng, tách từ Kanji, Kana hỗn hợp, động từ chia thể và dấu câu ngoại tuyến.
 */
class JapaneseTokenizerServiceTest {

    @Test
    void shouldLazyLoadTokenizerOnceAcrossConcurrentCalls() throws Exception {
        // Xác minh Kuromoji không nạp khi khởi tạo dịch vụ và chỉ khởi tạo đúng 1 lần khi nhiều luồng gọi đồng thời
        AtomicInteger creationCount = new AtomicInteger(0);
        JapaneseTokenizerService service = new JapaneseTokenizerService(() -> {
            creationCount.incrementAndGet();
            return new Tokenizer();
        });

        assertFalse(service.isLoaded(), "Tokenizer must not load eagerly on construction");
        assertEquals(0, creationCount.get());

        try (ExecutorService pool = Executors.newFixedThreadPool(4)) {
            List<Callable<List<JapaneseToken>>> tasks = List.of(
                    () -> service.tokenize("日本語を勉強します。"),
                    () -> service.tokenize("猫がいます。"),
                    () -> service.tokenize("新しい本を読みました。"),
                    () -> service.tokenize("こんにちは。")
            );
            List<Future<List<JapaneseToken>>> futures = pool.invokeAll(tasks);
            for (Future<List<JapaneseToken>> future : futures) {
                assertFalse(future.get().isEmpty());
            }
        }

        assertTrue(service.isLoaded());
        assertEquals(1, creationCount.get(), "Tokenizer must be initialized exactly once");
    }

    @Test
    void shouldTokenizeKanjiMixedKanaConjugatedVerbsAndPunctuationOffline() {
        JapaneseTokenizerService service = new JapaneseTokenizerService();
        String text = "私は毎日日本語を勉強して、美味しい寿司を食べました。";

        List<JapaneseToken> tokens = service.tokenize(text);
        assertFalse(tokens.isEmpty());

        // Đảm bảo nối toàn bộ dạng bề mặt khôi phục chính xác văn bản gốc
        String reconstructed = tokens.stream()
                .map(JapaneseToken::surface)
                .collect(Collectors.joining());
        assertEquals(text, reconstructed);

        // Kiểm tra từ Kanji thuần: 日本語
        JapaneseToken nihongo = findBySurface(tokens, "日本語");
        assertNotNull(nihongo);
        assertEquals("日本語", nihongo.baseForm());
        assertEquals("ニホンゴ", nihongo.readingKatakana());
        assertEquals("にほんご", nihongo.readingHiragana());
        assertEquals("名詞", nihongo.partOfSpeechLevel1());
        assertTrue(nihongo.hasKanji());
        assertTrue(nihongo.isInspectableWord());

        // Kiểm tra động từ chia thể: 食べ (trong 食べました) có nguyên thể là 食べる
        JapaneseToken tabe = findBySurface(tokens, "食べ");
        assertNotNull(tabe);
        assertEquals("食べる", tabe.baseForm());
        assertEquals("タベ", tabe.readingKatakana());
        assertEquals("たべ", tabe.readingHiragana());
        assertEquals("動詞", tabe.partOfSpeechLevel1());

        // Kiểm tra dấu câu được giữ nguyên và đánh dấu không phải từ cần tra cứu
        JapaneseToken period = findBySurface(tokens, "。");
        assertNotNull(period);
        assertTrue(period.isPunctuationOrWhitespace());
        assertFalse(period.isInspectableWord());
        assertNull(period.readingHiragana());
    }

    @Test
    void shouldPreserveWhitespaceNewlinesAndOffsetsLosslessly() {
        JapaneseTokenizerService service = new JapaneseTokenizerService();
        String text = "東京へ行く。\n明日、友達と会う！";

        List<JapaneseToken> tokens = service.tokenize(text);
        String reconstructed = tokens.stream()
                .map(JapaneseToken::surface)
                .collect(Collectors.joining());
        assertEquals(text, reconstructed);

        for (JapaneseToken token : tokens) {
            assertEquals(token.surface(), text.substring(token.startOffset(), token.endOffset()));
        }

        // Xác minh trích xuất câu ngữ cảnh đúng cho token ở dòng thứ hai
        JapaneseToken tomodachi = findBySurface(tokens, "友達");
        assertNotNull(tomodachi);
        String sentence = service.extractContextSentence(text, tomodachi.startOffset(), tomodachi.endOffset());
        assertEquals("明日、友達と会う！", sentence);
    }

    @Test
    void shouldHandleEmptyAndPureKanaInputGracefully() {
        JapaneseTokenizerService service = new JapaneseTokenizerService();
        assertTrue(service.tokenize(null).isEmpty());
        assertTrue(service.tokenize("").isEmpty());
        assertFalse(service.isLoaded(), "Empty input should not trigger Kuromoji dictionary loading");

        List<JapaneseToken> kanaTokens = service.tokenize("ありがとう");
        assertFalse(kanaTokens.isEmpty());
        assertFalse(kanaTokens.getFirst().hasKanji());
        assertEquals("ありがとう", kanaTokens.getFirst().readingHiragana());
    }

    @Test
    void shouldConvertKatakanaToHiraganaAndDetectKanjiAccurately() {
        assertEquals("にほんご", KanaConverter.katakanaToHiragana("ニホンゴ"));
        assertEquals("コーヒー", KanaConverter.hiraganaToKatakana("こーひー"));
        assertEquals("こーひー", KanaConverter.katakanaToHiragana("コーヒー"));
        assertTrue(KanaConverter.containsKanji("人々"));
        assertTrue(KanaConverter.containsKanji("食べる"));
        assertFalse(KanaConverter.containsKanji("ひらがなカタカナ。"));
    }

    private static JapaneseToken findBySurface(List<JapaneseToken> tokens, String surface) {
        return tokens.stream()
                .filter(token -> surface.equals(token.surface()))
                .findFirst()
                .orElse(null);
    }
}
