package com.senkoje.infrastructure.nlp;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Bộ tách câu và tách từ tiếng Anh ngoại tuyến phục vụ Không gian thực hành đọc (Reading Sandbox).
 * Xử lý sạch các dạng rút gọn (contractions), từ ghép có dấu gạch nối, chữ viết tắt phổ biến,
 * số thập phân và bảo toàn nguyên vẹn dấu câu, khoảng trắng, xuống dòng cùng vị trí ký tự.
 */
public class EnglishTextTokenizer {

    // Khớp từ tiếng Anh gồm chữ cái/chữ số có thể chứa dấu nháy đơn hoặc dấu gạch nối ở giữa, hoặc số thập phân
    private static final Pattern TOKEN_PATTERN = Pattern.compile(
            "[A-Za-z0-9]+(?:['\\u2019-][A-Za-z0-9]+)*|\\d+(?:\\.\\d+)+|\\r\\n|\\n|\\r|[ \\t]+|[^\\sA-Za-z0-9]+"
    );

    private static final Set<String> ABBREVIATIONS = Set.of(
            "mr.", "mrs.", "ms.", "dr.", "prof.", "sr.", "jr.", "st.",
            "vs.", "etc.", "e.g.", "i.e.", "fig.", "no.", "vol.", "jan.",
            "feb.", "mar.", "apr.", "jun.", "jul.", "aug.", "sep.", "sept.",
            "oct.", "nov.", "dec."
    );

    /**
     * Phân tách toàn bộ văn bản tiếng Anh thành danh sách câu và luồng token không mất dữ liệu.
     *
     * @param text văn bản tiếng Anh đầu vào
     * @return tài liệu đọc tiếng Anh đã phân tách
     */
    public EnglishReadingDocument analyze(String text) {
        if (text == null || text.isEmpty()) {
            return new EnglishReadingDocument("", List.of(), List.of());
        }

        List<SentenceSpan> spans = findSentenceSpans(text);
        List<EnglishSentence> sentences = new ArrayList<>(spans.size());
        List<EnglishToken> allTokens = new ArrayList<>();

        int cursor = 0;
        for (int i = 0; i < spans.size(); i++) {
            SentenceSpan span = spans.get(i);
            if (span.start() > cursor) {
                allTokens.addAll(tokenizeRange(text, cursor, span.start(), -1));
            }
            List<EnglishToken> sentenceTokens = tokenizeRange(text, span.start(), span.end(), i);
            sentences.add(new EnglishSentence(
                    i,
                    text.substring(span.start(), span.end()).trim(),
                    span.start(),
                    span.end(),
                    sentenceTokens
            ));
            allTokens.addAll(sentenceTokens);
            cursor = span.end();
        }

        if (cursor < text.length()) {
            allTokens.addAll(tokenizeRange(text, cursor, text.length(), -1));
        }

        return new EnglishReadingDocument(text, sentences, allTokens);
    }

    /**
     * Tách văn bản tiếng Anh thành danh sách các câu.
     *
     * @param text văn bản tiếng Anh đầu vào
     * @return danh sách câu bất biến
     */
    public List<EnglishSentence> segmentSentences(String text) {
        return analyze(text).sentences();
    }

    private List<SentenceSpan> findSentenceSpans(String text) {
        List<SentenceSpan> spans = new ArrayList<>();
        int length = text.length();
        int index = 0;

        while (index < length) {
            // Bỏ qua khoảng trắng hoặc xuống dòng ở đầu câu mới
            while (index < length && Character.isWhitespace(text.charAt(index))) {
                index++;
            }
            if (index >= length) {
                break;
            }

            int sentenceStart = index;
            int sentenceEnd = length;

            while (index < length) {
                char ch = text.charAt(index);
                if (ch == '\n' || ch == '\r') {
                    sentenceEnd = trimTrailingInlineSpaces(text, sentenceStart, index);
                    break;
                }
                if (ch == '.' || ch == '!' || ch == '?') {
                    int endPunct = index + 1;
                    while (endPunct < length && isSentenceTerminator(text.charAt(endPunct))) {
                        endPunct++;
                    }
                    // Không tách câu giữa số thập phân (ví dụ: 3.14) hoặc sau chữ viết tắt (ví dụ: Dr., U.S.)
                    if (ch == '.' && endPunct == index + 1 && isNonBreakingPeriod(text, sentenceStart, index)) {
                        index = endPunct;
                        continue;
                    }
                    // Gộp dấu ngoặc kép hoặc ngoặc đơn đóng ngay sau dấu kết câu
                    int afterClosing = endPunct;
                    while (afterClosing < length && isClosingPunctuation(text.charAt(afterClosing))) {
                        afterClosing++;
                    }
                    if (afterClosing >= length || Character.isWhitespace(text.charAt(afterClosing))) {
                        sentenceEnd = afterClosing;
                        index = afterClosing;
                        break;
                    }
                    index = afterClosing;
                    continue;
                }
                index++;
            }

            if (sentenceEnd > sentenceStart) {
                spans.add(new SentenceSpan(sentenceStart, sentenceEnd));
            }
            if (index < length && (text.charAt(index) == '\n' || text.charAt(index) == '\r')) {
                index++;
            }
        }
        return spans;
    }

    private List<EnglishToken> tokenizeRange(String text, int start, int end, int sentenceIndex) {
        List<EnglishToken> tokens = new ArrayList<>();
        String slice = text.substring(start, end);
        Matcher matcher = TOKEN_PATTERN.matcher(slice);

        while (matcher.find()) {
            String surface = matcher.group();
            int tokenStart = start + matcher.start();
            int tokenEnd = start + matcher.end();
            boolean isWord = containsAsciiLetter(surface);
            String normalized = isWord
                    ? surface.replace('\u2019', '\'').toLowerCase(Locale.ROOT)
                    : null;
            tokens.add(new EnglishToken(surface, normalized, isWord, sentenceIndex, tokenStart, tokenEnd));
        }
        return tokens;
    }

    private static boolean isNonBreakingPeriod(String text, int sentenceStart, int dotIndex) {
        // Trường hợp số thập phân: chữ số trước và sau dấu chấm
        if (dotIndex > sentenceStart && dotIndex + 1 < text.length()
                && Character.isDigit(text.charAt(dotIndex - 1))
                && Character.isDigit(text.charAt(dotIndex + 1))) {
            return true;
        }

        // Trường hợp chữ viết tắt có chấm xen kẽ (ví dụ: U.S., U.K., e.g.)
        int wordStart = dotIndex;
        while (wordStart > sentenceStart && !Character.isWhitespace(text.charAt(wordStart - 1))) {
            wordStart--;
        }
        String candidate = text.substring(wordStart, dotIndex + 1)
                .replaceAll("^[\"'(]+", "")
                .toLowerCase(Locale.ROOT);
        if (ABBREVIATIONS.contains(candidate)) {
            return true;
        }
        // Các cụm viết tắt kiểu chữ cái đơn có dấu chấm (ví dụ: a.m., u.s., j.k.)
        if (candidate.matches("(?:[a-z]\\.){2,}")) {
            return true;
        }
        if (candidate.length() == 2 && Character.isLetter(candidate.charAt(0)) && candidate.charAt(1) == '.') {
            return dotIndex + 1 < text.length() && !Character.isWhitespace(text.charAt(dotIndex + 1));
        }
        return false;
    }

    private static int trimTrailingInlineSpaces(String text, int start, int end) {
        int pos = end;
        while (pos > start && (text.charAt(pos - 1) == ' ' || text.charAt(pos - 1) == '\t')) {
            pos--;
        }
        return pos;
    }

    private static boolean isSentenceTerminator(char ch) {
        return ch == '.' || ch == '!' || ch == '?';
    }

    private static boolean isClosingPunctuation(char ch) {
        return ch == '"' || ch == '\'' || ch == '\u201D' || ch == '\u2019' || ch == ')' || ch == ']';
    }

    private static boolean containsAsciiLetter(String value) {
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            if ((ch >= 'A' && ch <= 'Z') || (ch >= 'a' && ch <= 'z')) {
                return true;
            }
        }
        return false;
    }

    private record SentenceSpan(int start, int end) {
    }
}
