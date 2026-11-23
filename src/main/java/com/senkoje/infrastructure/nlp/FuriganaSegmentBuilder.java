package com.senkoje.infrastructure.nlp;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Bộ xây dựng và căn chỉnh phân đoạn Furigana ngoại tuyến.
 * Ánh xạ cách đọc Kana đặt đúng phía trên các cụm chữ Hán (Kanji),
 * không gắn đè lên okurigana, Kana thuần hay dấu câu, đồng thời bảo toàn nguyên vẹn văn bản gốc.
 */
public class FuriganaSegmentBuilder {

    /**
     * Xây dựng danh sách phân đoạn Furigana cho toàn bộ danh sách token tiếng Nhật từ Kuromoji.
     *
     * @param tokens danh sách token tiếng Nhật theo đúng thứ tự xuất hiện
     * @return danh sách bất biến các phân đoạn Furigana
     */
    public List<FuriganaSegment> buildSegments(List<JapaneseToken> tokens) {
        if (tokens == null || tokens.isEmpty()) {
            return List.of();
        }
        List<FuriganaSegment> segments = new ArrayList<>(tokens.size());
        for (JapaneseToken token : tokens) {
            segments.add(buildSegment(token));
        }
        return List.copyOf(segments);
    }

    /**
     * Xây dựng phân đoạn Furigana cho một token tiếng Nhật duy nhất.
     *
     * @param token token hình thái từ Kuromoji
     * @return phân đoạn Furigana đã căn chỉnh
     */
    public FuriganaSegment buildSegment(JapaneseToken token) {
        Objects.requireNonNull(token, "token must not be null");
        String reading = normalizeReading(token.readingHiragana());
        List<FuriganaPart> parts = alignParts(token.surface(), reading);
        return new FuriganaSegment(token, token.surface(), reading, parts, false);
    }

    /**
     * Xây dựng phân đoạn Furigana trực tiếp từ dạng bề mặt và cách đọc (hữu ích khi kiểm thử hoặc chỉnh sửa).
     *
     * @param surface dạng bề mặt của từ
     * @param reading cách đọc Kana (Katakana hoặc Hiragana)
     * @return phân đoạn Furigana đã căn chỉnh
     */
    public FuriganaSegment buildSegment(String surface, String reading) {
        String safeSurface = surface == null ? "" : surface;
        String normalizedReading = normalizeReading(reading);
        List<FuriganaPart> parts = alignParts(safeSurface, normalizedReading);
        return new FuriganaSegment(null, safeSurface, normalizedReading, parts, false);
    }

    /**
     * Tạo phân đoạn mới sau khi người dùng chỉnh sửa cách đọc cho các trường hợp đọc mơ hồ trước khi lưu thẻ.
     *
     * @param original phân đoạn gốc
     * @param customReading cách đọc mới do người dùng nhập (Katakana hoặc Hiragana; rỗng để xóa Furigana)
     * @return phân đoạn mới đã cập nhật cách đọc và căn chỉnh lại
     */
    public FuriganaSegment withCustomReading(FuriganaSegment original, String customReading) {
        Objects.requireNonNull(original, "original must not be null");
        String normalizedReading = normalizeReading(customReading);
        List<FuriganaPart> parts = alignParts(original.surface(), normalizedReading);
        return new FuriganaSegment(
                original.token(),
                original.surface(),
                normalizedReading,
                parts,
                true
        );
    }

    /**
     * Căn chỉnh cách đọc Hiragana lên đúng các cụm chữ Hán trong dạng bề mặt,
     * giữ nguyên các đoạn Kana, okurigana, khoảng trắng và dấu câu không có Furigana.
     *
     * @param surface dạng bề mặt của văn bản
     * @param reading cách đọc Kana (có thể null hoặc rỗng)
     * @return danh sách bất biến các phần tử FuriganaPart mà khi ghép lại bằng đúng surface
     */
    public List<FuriganaPart> alignParts(String surface, String reading) {
        if (surface == null || surface.isEmpty()) {
            return List.of();
        }
        String normalizedReading = normalizeReading(reading);
        if (!KanaConverter.containsKanji(surface) || normalizedReading == null) {
            return List.of(new FuriganaPart(surface, null));
        }

        List<CharacterRun> runs = splitIntoRuns(surface);
        if (runs.size() == 1 && runs.getFirst().kanji()) {
            return List.of(new FuriganaPart(surface, normalizedReading));
        }

        // Thử căn chỉnh chính xác bằng các điểm neo không phải Kanji (okurigana, tiền tố, hậu tố)
        List<FuriganaPart> aligned = new ArrayList<>();
        if (alignRecursive(runs, 0, normalizedReading, 0, aligned)) {
            return List.copyOf(aligned);
        }

        // Phương án dự phòng an toàn cho từ đọc ngoại lệ (ateji): tách tiền tố/hậu tố Kana khớp được, gắn phần đọc còn lại cho cụm Kanji
        return buildFallbackParts(runs, normalizedReading);
    }

    private boolean alignRecursive(
            List<CharacterRun> runs,
            int runIndex,
            String reading,
            int readingPos,
            List<FuriganaPart> output
    ) {
        if (runIndex == runs.size()) {
            return readingPos == reading.length();
        }

        CharacterRun current = runs.get(runIndex);
        if (!current.kanji()) {
            String anchor = KanaConverter.katakanaToHiragana(current.text());
            if (!reading.startsWith(anchor, readingPos)) {
                return false;
            }
            output.add(new FuriganaPart(current.text(), null));
            if (alignRecursive(runs, runIndex + 1, reading, readingPos + anchor.length(), output)) {
                return true;
            }
            output.removeLast();
            return false;
        }

        // Cụm Kanji là phần tử cuối cùng: nhận toàn bộ phần cách đọc còn lại
        if (runIndex == runs.size() - 1) {
            if (readingPos >= reading.length()) {
                return false;
            }
            output.add(new FuriganaPart(current.text(), reading.substring(readingPos)));
            return true;
        }

        // Cụm Kanji đứng trước một điểm neo không phải Kanji: tìm vị trí khớp tiếp theo của điểm neo
        CharacterRun nextAnchorRun = runs.get(runIndex + 1);
        String nextAnchor = KanaConverter.katakanaToHiragana(nextAnchorRun.text());
        int minKanjiEnd = readingPos + 1;
        int searchFrom = minKanjiEnd;

        while (searchFrom <= reading.length() - nextAnchor.length()) {
            int anchorPos = reading.indexOf(nextAnchor, searchFrom);
            if (anchorPos < 0) {
                break;
            }
            output.add(new FuriganaPart(current.text(), reading.substring(readingPos, anchorPos)));
            if (alignRecursive(runs, runIndex + 1, reading, anchorPos, output)) {
                return true;
            }
            output.removeLast();
            searchFrom = anchorPos + 1;
        }
        return false;
    }

    private List<FuriganaPart> buildFallbackParts(List<CharacterRun> runs, String normalizedReading) {
        int firstIndex = 0;
        int lastIndex = runs.size() - 1;
        int readingStart = 0;
        int readingEnd = normalizedReading.length();

        List<FuriganaPart> prefix = new ArrayList<>();
        if (!runs.get(firstIndex).kanji()) {
            String anchor = KanaConverter.katakanaToHiragana(runs.get(firstIndex).text());
            if (normalizedReading.startsWith(anchor)) {
                prefix.add(new FuriganaPart(runs.get(firstIndex).text(), null));
                readingStart += anchor.length();
                firstIndex++;
            }
        }

        List<FuriganaPart> suffix = new ArrayList<>();
        if (lastIndex >= firstIndex && !runs.get(lastIndex).kanji()) {
            String anchor = KanaConverter.katakanaToHiragana(runs.get(lastIndex).text());
            if (readingEnd - readingStart >= anchor.length()
                    && normalizedReading.regionMatches(readingEnd - anchor.length(), anchor, 0, anchor.length())) {
                suffix.add(new FuriganaPart(runs.get(lastIndex).text(), null));
                readingEnd -= anchor.length();
                lastIndex--;
            }
        }

        StringBuilder middleText = new StringBuilder();
        for (int i = firstIndex; i <= lastIndex; i++) {
            middleText.append(runs.get(i).text());
        }
        String middleReading = readingEnd > readingStart
                ? normalizedReading.substring(readingStart, readingEnd)
                : normalizedReading;

        List<FuriganaPart> result = new ArrayList<>(prefix.size() + 1 + suffix.size());
        result.addAll(prefix);
        if (!middleText.isEmpty()) {
            result.add(new FuriganaPart(middleText.toString(), middleReading));
        }
        result.addAll(suffix);
        return List.copyOf(result);
    }

    private static List<CharacterRun> splitIntoRuns(String surface) {
        List<CharacterRun> runs = new ArrayList<>();
        int start = 0;
        boolean currentKanji = KanaConverter.isKanji(surface.charAt(0));
        for (int i = 1; i < surface.length(); i++) {
            boolean isKanji = KanaConverter.isKanji(surface.charAt(i));
            if (isKanji != currentKanji) {
                runs.add(new CharacterRun(surface.substring(start, i), currentKanji));
                start = i;
                currentKanji = isKanji;
            }
        }
        runs.add(new CharacterRun(surface.substring(start), currentKanji));
        return runs;
    }

    private static String normalizeReading(String reading) {
        if (reading == null || reading.isBlank()) {
            return null;
        }
        return KanaConverter.katakanaToHiragana(reading.trim());
    }

    private record CharacterRun(String text, boolean kanji) {
    }
}
