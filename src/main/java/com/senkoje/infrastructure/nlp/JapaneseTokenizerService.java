package com.senkoje.infrastructure.nlp;

import com.atilika.kuromoji.ipadic.Token;
import com.atilika.kuromoji.ipadic.Tokenizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * Dịch vụ phân tích hình thái tiếng Nhật ngoại tuyến dựa trên Kuromoji IPADIC.
 * Từ điển được tải lười (lazy-load) an toàn đa luồng trong lần tách từ đầu tiên
 * để giữ thời gian khởi động ứng dụng desktop nhanh và không chặn luồng JavaFX.
 */
public class JapaneseTokenizerService {

    private static final String UNKNOWN_MARKER = "*";
    private static final String SYMBOL_POS = "記号";

    private final Supplier<Tokenizer> tokenizerSupplier;
    private final Object loadLock = new Object();
    private volatile Tokenizer tokenizer;

    /**
     * Khởi tạo dịch vụ Kuromoji ở chế độ tải lười mà chưa nạp từ điển vào bộ nhớ.
     */
    public JapaneseTokenizerService() {
        this(() -> new Tokenizer());
    }

    /**
     * Khởi tạo dịch vụ với nhà cung cấp Tokenizer tùy biến (phục vụ kiểm thử đơn vị).
     *
     * @param tokenizerSupplier hàm khởi tạo Kuromoji Tokenizer khi cần dùng lần đầu
     */
    JapaneseTokenizerService(Supplier<Tokenizer> tokenizerSupplier) {
        this.tokenizerSupplier = Objects.requireNonNull(tokenizerSupplier, "tokenizerSupplier must not be null");
    }

    /**
     * Kiểm tra từ điển Kuromoji đã được nạp vào bộ nhớ hay chưa.
     *
     * @return true nếu Tokenizer đã sẵn sàng sử dụng
     */
    public boolean isLoaded() {
        return tokenizer != null;
    }

    /**
     * Chủ động nạp từ điển Kuromoji an toàn đa luồng nếu chưa được nạp.
     * Nên gọi trên luồng nền (không gọi trên JavaFX Application Thread).
     */
    public void ensureLoaded() {
        getOrLoadTokenizer();
    }

    /**
     * Phân tích hình thái một đoạn văn bản tiếng Nhật thành danh sách token có giữ nguyên
     * vị trí ký tự, khoảng trắng, xuống dòng và dấu câu của văn bản gốc.
     *
     * @param text văn bản tiếng Nhật cần phân tích
     * @return danh sách bất biến các token theo đúng thứ tự xuất hiện
     */
    public List<JapaneseToken> tokenize(String text) {
        if (text == null || text.isEmpty()) {
            return List.of();
        }
        Tokenizer activeTokenizer = getOrLoadTokenizer();
        List<Token> rawTokens = activeTokenizer.tokenize(text);
        List<JapaneseToken> result = new ArrayList<>(rawTokens.size());

        int cursor = 0;
        for (Token raw : rawTokens) {
            int start = raw.getPosition();
            String surface = raw.getSurface();
            if (start > cursor && start <= text.length()) {
                // Bảo toàn chính xác ký tự xuống dòng hoặc khoảng trắng nếu bộ tách từ bỏ qua
                String gap = text.substring(cursor, start);
                result.add(createWhitespaceToken(gap, cursor, start));
            }
            int end = Math.min(text.length(), start + surface.length());
            result.add(mapToken(raw, start, end));
            cursor = Math.max(cursor, end);
        }

        if (cursor < text.length()) {
            String trailing = text.substring(cursor);
            result.add(createWhitespaceToken(trailing, cursor, text.length()));
        }
        return List.copyOf(result);
    }

    /**
     * Trích xuất câu ngữ cảnh chứa một khoảng ký tự xác định trong văn bản tiếng Nhật
     * để điền sẵn câu ví dụ khi người dùng thêm từ vựng vào bộ thẻ.
     *
     * @param fullText toàn bộ văn bản gốc
     * @param startOffset vị trí bắt đầu của token được chọn
     * @param endOffset vị trí kết thúc của token được chọn
     * @return câu ngữ cảnh đã cắt gọn khoảng trắng hai đầu
     */
    public String extractContextSentence(String fullText, int startOffset, int endOffset) {
        if (fullText == null || fullText.isBlank()) {
            return "";
        }
        int safeStart = Math.max(0, Math.min(startOffset, fullText.length()));
        int safeEnd = Math.max(safeStart, Math.min(endOffset, fullText.length()));

        int sentenceStart = safeStart;
        while (sentenceStart > 0) {
            char prev = fullText.charAt(sentenceStart - 1);
            if (isSentenceBoundary(prev)) {
                break;
            }
            sentenceStart--;
        }

        int sentenceEnd = safeEnd;
        while (sentenceEnd < fullText.length()) {
            char current = fullText.charAt(sentenceEnd);
            sentenceEnd++;
            if (isSentenceBoundary(current)) {
                break;
            }
        }
        return fullText.substring(sentenceStart, sentenceEnd).trim();
    }

    private Tokenizer getOrLoadTokenizer() {
        Tokenizer local = tokenizer;
        if (local == null) {
            synchronized (loadLock) {
                local = tokenizer;
                if (local == null) {
                    local = Objects.requireNonNull(
                            tokenizerSupplier.get(), "tokenizerSupplier returned null");
                    tokenizer = local;
                }
            }
        }
        return local;
    }

    private JapaneseToken mapToken(Token raw, int startOffset, int endOffset) {
        String surface = raw.getSurface();
        String baseForm = normalizeFeature(raw.getBaseForm());
        if (baseForm == null) {
            baseForm = surface;
        }

        String pos1 = normalizeFeature(raw.getPartOfSpeechLevel1());
        String pos2 = normalizeFeature(raw.getPartOfSpeechLevel2());
        String pos3 = normalizeFeature(raw.getPartOfSpeechLevel3());
        String pos4 = normalizeFeature(raw.getPartOfSpeechLevel4());

        String detail = joinNonNull("-", pos2, pos3, pos4);
        String combinedPos = joinNonNull("-", pos1, detail.isEmpty() ? null : detail);

        String readingKatakana = normalizeFeature(raw.getReading());
        if (SYMBOL_POS.equals(pos1) || surface.isBlank()) {
            readingKatakana = null;
        }
        String readingHiragana = readingKatakana == null
                ? null
                : KanaConverter.katakanaToHiragana(readingKatakana);

        return new JapaneseToken(
                surface,
                baseForm,
                readingKatakana,
                readingHiragana,
                combinedPos,
                pos1 == null ? "" : pos1,
                detail,
                startOffset,
                endOffset,
                raw.isKnown()
        );
    }

    private JapaneseToken createWhitespaceToken(String text, int startOffset, int endOffset) {
        return new JapaneseToken(
                text,
                text,
                null,
                null,
                SYMBOL_POS + "-空白",
                SYMBOL_POS,
                "空白",
                startOffset,
                endOffset,
                true
        );
    }

    private static String normalizeFeature(String value) {
        if (value == null || value.isBlank() || UNKNOWN_MARKER.equals(value)) {
            return null;
        }
        return value;
    }

    private static String joinNonNull(String delimiter, String... parts) {
        StringBuilder builder = new StringBuilder();
        for (String part : parts) {
            if (part != null && !part.isBlank()) {
                if (!builder.isEmpty()) {
                    builder.append(delimiter);
                }
                builder.append(part);
            }
        }
        return builder.toString();
    }

    private static boolean isSentenceBoundary(char ch) {
        return ch == '。' || ch == '！' || ch == '？'
                || ch == '!' || ch == '?' || ch == '\n' || ch == '\r';
    }
}
