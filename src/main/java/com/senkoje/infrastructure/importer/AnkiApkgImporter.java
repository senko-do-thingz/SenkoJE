package com.senkoje.infrastructure.importer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.senkoje.application.dto.CardExtraData;
import com.senkoje.domain.model.CardExample;
import com.senkoje.domain.model.CardType;
import com.senkoje.domain.model.Deck;
import com.senkoje.domain.model.DeckSettings;
import com.senkoje.domain.model.DefinitionLanguage;
import com.senkoje.domain.model.ProficiencyLevel;
import com.senkoje.domain.model.TargetLanguage;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Tiện ích nhập gói thẻ học Anki (.apkg) với cơ chế phòng vệ Zip Slip và giới hạn giải nén chống bom nén dữ liệu.
 */
public class AnkiApkgImporter {

    private static final Logger LOGGER = LoggerFactory.getLogger(AnkiApkgImporter.class);

    // Giới hạn an toàn giải nén nhằm phòng chống tấn công từ chối dịch vụ dạng bom nén (Zip Bomb)
    public static final int MAX_ZIP_ENTRIES = 10_000;
    public static final long MAX_TOTAL_UNCOMPRESSED_BYTES = 250 * 1024 * 1024L; // 250 MB
    public static final long MAX_SINGLE_ENTRY_BYTES = 100 * 1024 * 1024L; // 100 MB

    private static final Pattern HTML_TAG_PATTERN = Pattern.compile("<[^>]*>");
    private static final Pattern CLOZE_PATTERN = Pattern.compile("\\{\\{c\\d+::(.*?)(?:::(.*?))?\\}\\}");

    private final ObjectMapper objectMapper;
    private final Clock clock;

    /**
     * Khởi tạo bộ nhập Anki với đồng hồ UTC mặc định.
     */
    public AnkiApkgImporter() {
        this(Clock.systemUTC());
    }

    /**
     * Khởi tạo bộ nhập Anki với đồng hồ chỉ định phục vụ kiểm thử.
     *
     * @param clock nguồn cung cấp thời gian
     */
    public AnkiApkgImporter(Clock clock) {
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
        this.objectMapper = new ObjectMapper();
    }

    /**
     * Giải nén tệp .apkg an toàn vào thư mục đích, phòng thủ nghiêm ngặt lỗ hổng Zip Slip và bom nén.
     *
     * @param apkgPath đường dẫn tệp tin lưu trữ .apkg
     * @param targetDir thư mục tạm đích để chứa nội dung giải nén
     * @throws IOException nếu phát sinh lỗi đọc tệp hoặc phát hiện vi phạm an toàn
     */
    public void extractApkgSafely(Path apkgPath, Path targetDir) throws IOException {
        Objects.requireNonNull(apkgPath, "apkgPath must not be null");
        Objects.requireNonNull(targetDir, "targetDir must not be null");

        Path normalizedTarget = targetDir.toAbsolutePath().normalize();
        Files.createDirectories(normalizedTarget);

        int totalEntries = 0;
        long totalBytes = 0;
        byte[] buffer = new byte[8192];

        try (InputStream fileIn = Files.newInputStream(apkgPath);
             BufferedInputStream bufIn = new BufferedInputStream(fileIn);
             ZipInputStream zipIn = new ZipInputStream(bufIn)) {

            ZipEntry entry;
            while ((entry = zipIn.getNextEntry()) != null) {
                totalEntries++;
                if (totalEntries > MAX_ZIP_ENTRIES) {
                    throw new SecurityException(
                            "Archive exceeds maximum allowed entry count: " + MAX_ZIP_ENTRIES);
                }

                String entryName = entry.getName();
                Path resolvedPath = normalizedTarget.resolve(entryName).normalize();

                // Xác minh triệt để đường dẫn chuẩn hóa không vượt ra ngoài thư mục đích (Zip Slip protection)
                if (!resolvedPath.startsWith(normalizedTarget)) {
                    throw new SecurityException(
                            "Zip Slip vulnerability detected in archive entry: " + entryName);
                }

                if (entry.isDirectory()) {
                    Files.createDirectories(resolvedPath);
                } else {
                    Path parent = resolvedPath.getParent();
                    if (parent != null) {
                        Files.createDirectories(parent);
                    }

                    long entryBytes = 0;
                    try (var outputStream = Files.newOutputStream(resolvedPath)) {
                        int read;
                        while ((read = zipIn.read(buffer)) != -1) {
                            entryBytes += read;
                            totalBytes += read;

                            if (entryBytes > MAX_SINGLE_ENTRY_BYTES) {
                                throw new SecurityException(
                                        "Archive entry exceeds single file size limit: " + entryName);
                            }
                            if (totalBytes > MAX_TOTAL_UNCOMPRESSED_BYTES) {
                                throw new SecurityException(
                                        "Archive exceeds maximum total uncompressed size limit: " + MAX_TOTAL_UNCOMPRESSED_BYTES);
                            }
                            outputStream.write(buffer, 0, read);
                        }
                    }
                }
                zipIn.closeEntry();
            }
        }
    }

    /**
     * Phân tích tệp lưu trữ Anki .apkg và sinh báo cáo xem trước các thẻ được hỗ trợ.
     *
     * @param apkgPath đường dẫn tệp .apkg
     * @param existingTerms tập hợp từ vựng hiện có trong bộ thẻ mục tiêu để kiểm tra trùng lặp
     * @return báo cáo xem trước dữ liệu
     * @throws IOException nếu không đọc hoặc giải nén được tệp
     */
    public ImportPreviewReport generatePreview(Path apkgPath, Set<String> existingTerms) throws IOException {
        Objects.requireNonNull(apkgPath, "apkgPath must not be null");

        Path tempDir = Files.createTempDirectory("senkoje_anki_import_");
        try {
            extractApkgSafely(apkgPath, tempDir);

            Path collectionDb = tempDir.resolve("collection.anki2");
            if (!Files.exists(collectionDb)) {
                // Thử định dạng anki21 nếu có
                collectionDb = tempDir.resolve("collection.anki21");
            }

            if (!Files.exists(collectionDb)) {
                ImportPreviewItem missingCol = new ImportPreviewItem(
                        1,
                        null,
                        ImportItemStatus.INVALID,
                        "Anki collection database (collection.anki2) not found inside archive"
                );
                return ImportPreviewReport.of(List.of(missingCol), null, null);
            }

            return parseAnkiCollection(collectionDb, existingTerms);
        } finally {
            deleteDirectoryRecursively(tempDir);
        }
    }

    private ImportPreviewReport parseAnkiCollection(Path collectionPath, Set<String> existingTerms) {
        String jdbcUrl = "jdbc:sqlite:" + collectionPath.toAbsolutePath().normalize().toString().replace("\\", "/");

        String deckTitle = "Anki Imported Deck";
        TargetLanguage targetLanguage = TargetLanguage.JA;
        DefinitionLanguage defLanguage = DefinitionLanguage.VI;
        ProficiencyLevel level = ProficiencyLevel.JLPT_N5;

        List<ImportPreviewItem> previewItems = new ArrayList<>();
        Set<String> normalizedExisting = new HashSet<>();
        if (existingTerms != null) {
            for (String t : existingTerms) {
                if (t != null && !t.isBlank()) {
                    normalizedExisting.add(t.trim().toLowerCase(Locale.ROOT));
                }
            }
        }
        Set<String> seenInBatch = new HashSet<>();

        try (Connection conn = DriverManager.getConnection(jdbcUrl);
             Statement stmt = conn.createStatement()) {

            // Đọc bảng col để lấy thông tin tên bộ thẻ
            try (ResultSet rs = stmt.executeQuery("SELECT decks FROM col LIMIT 1")) {
                if (rs.next()) {
                    String decksJson = rs.getString(1);
                    if (decksJson != null && !decksJson.isBlank()) {
                        try {
                            JsonNode decksNode = objectMapper.readTree(decksJson);
                            for (JsonNode dNode : decksNode) {
                                String name = dNode.path("name").asText(null);
                                if (name != null && !name.isBlank() && !"Default".equalsIgnoreCase(name)) {
                                    deckTitle = name;
                                    break;
                                }
                            }
                        } catch (Exception ignored) {
                        }
                    }
                }
            }

            // Tự động nhận diện ngôn ngữ đích dựa trên tiêu đề bộ thẻ
            if (deckTitle.toLowerCase(Locale.ROOT).contains("english")
                    || deckTitle.toLowerCase(Locale.ROOT).contains("tiếng anh")) {
                targetLanguage = TargetLanguage.EN;
                level = ProficiencyLevel.CEFR_B1;
            }

            // Đọc danh sách ghi chú (notes) từ SQLite của Anki
            String notesSql = "SELECT id, flds, tags FROM notes ORDER BY id ASC";
            try (PreparedStatement ps = conn.prepareStatement(notesSql);
                 ResultSet rs = ps.executeQuery()) {

                int index = 1;
                while (rs.next()) {
                    String rawFlds = rs.getString("flds");
                    String rawTags = rs.getString("tags");

                    previewItems.add(parseNoteFields(rawFlds, rawTags, index++, normalizedExisting, seenInBatch, level));
                }
            }

        } catch (SQLException exception) {
            ImportPreviewItem sqlError = new ImportPreviewItem(
                    1,
                    null,
                    ImportItemStatus.INVALID,
                    "Failed to read Anki SQLite collection: " + exception.getMessage()
            );
            return ImportPreviewReport.of(List.of(sqlError), null, null);
        }

        Instant now = clock.instant();
        Deck candidateDeck = new Deck(
                null,
                deckTitle,
                targetLanguage,
                defLanguage,
                level,
                "Imported from Anki .apkg archive",
                now,
                now
        );
        DeckSettings candidateSettings = DeckSettings.defaults(null);

        return ImportPreviewReport.of(previewItems, candidateDeck, candidateSettings);
    }

    private ImportPreviewItem parseNoteFields(
            String rawFlds,
            String rawTags,
            int index,
            Set<String> existingTerms,
            Set<String> seenInBatch,
            ProficiencyLevel defaultLevel
    ) {
        if (rawFlds == null || rawFlds.isBlank()) {
            return new ImportPreviewItem(index, null, ImportItemStatus.INVALID, "Empty note fields");
        }

        // Trường trong Anki phân cách bằng ký tự ASCII Unit Separator (\u001f)
        String[] fields = rawFlds.split("\u001f", -1);
        if (fields.length == 0) {
            return new ImportPreviewItem(index, null, ImportItemStatus.INVALID, "No fields found in note");
        }

        String rawFront = cleanHtml(fields[0]);
        String rawBack = fields.length > 1 ? cleanHtml(fields[1]) : "";

        // Xử lý mẫu Cloze deletion nếu có (ví dụ {{c1::word::hint}})
        CardType cardType = CardType.VOCABULARY;
        String term = rawFront;
        if (rawFront.contains("{{c") && rawFront.contains("}}")) {
            cardType = CardType.PHRASE;
            term = extractClozeAnswer(rawFront);
            if (term.isBlank()) {
                term = rawFront;
            }
        }

        if (term.isBlank()) {
            return new ImportPreviewItem(index, null, ImportItemStatus.INVALID, "Missing required front/term content");
        }

        String meaning = rawBack;
        if (meaning.isBlank() && fields.length > 2) {
            meaning = cleanHtml(fields[2]);
        }
        if (meaning.isBlank()) {
            meaning = term; // Sử dụng term làm nghĩa dự phòng nếu Anki note chỉ có 1 mặt
        }

        String reading = null;
        if (fields.length > 2 && !fields[2].isBlank()) {
            String candidateReading = cleanHtml(fields[2]);
            if (!candidateReading.equalsIgnoreCase(meaning)) {
                reading = candidateReading;
            }
        }

        String exampleSentence = fields.length > 3 ? cleanHtml(fields[3]) : null;
        List<CardExample> examples = new ArrayList<>();
        if (exampleSentence != null && !exampleSentence.isBlank()) {
            examples.add(new CardExample(null, null, exampleSentence, null, DefinitionLanguage.VI, 1));
        }

        CardExtraData extraData = null;
        if (rawTags != null && !rawTags.isBlank()) {
            extraData = new CardExtraData(null, null, null, null, null, null, null, null, null, rawTags.trim(), null, null);
        }

        ImportCandidateCard candidateCard = new ImportCandidateCard(
                term,
                reading,
                meaning,
                cardType,
                defaultLevel,
                null,
                rawTags != null ? rawTags.trim() : null,
                extraData,
                List.of(),
                examples
        );

        String normalizedTerm = term.toLowerCase(Locale.ROOT);
        if (existingTerms.contains(normalizedTerm)) {
            return new ImportPreviewItem(
                    index,
                    candidateCard,
                    ImportItemStatus.DUPLICATE,
                    "Duplicate term already in deck: " + term
            );
        }

        if (seenInBatch.contains(normalizedTerm)) {
            return new ImportPreviewItem(
                    index,
                    candidateCard,
                    ImportItemStatus.DUPLICATE,
                    "Duplicate term within import batch: " + term
            );
        }

        seenInBatch.add(normalizedTerm);
        return new ImportPreviewItem(index, candidateCard, ImportItemStatus.VALID, null);
    }

    /**
     * Làm sạch mã HTML, thẻ ngắt dòng và các ký tự thực thể đặc biệt trong ghi chú Anki.
     *
     * @param html chuỗi văn bản HTML
     * @return chuỗi văn bản thuần túy
     */
    public static String cleanHtml(String html) {
        if (html == null || html.isBlank()) {
            return "";
        }
        String cleaned = html.replace("<br>", "\n")
                .replace("<br/>", "\n")
                .replace("<br />", "\n")
                .replace("&nbsp;", " ")
                .replace("&amp;", "&")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&quot;", "\"");
        cleaned = HTML_TAG_PATTERN.matcher(cleaned).replaceAll("");
        return cleaned.trim();
    }

    /**
     * Rút trích câu trả lời chính từ mẫu Cloze deletion của Anki.
     *
     * @param clozeText văn bản chứa cú pháp cloze
     * @return câu trả lời được rút trích
     */
    public static String extractClozeAnswer(String clozeText) {
        if (clozeText == null || clozeText.isBlank()) {
            return "";
        }
        Matcher matcher = CLOZE_PATTERN.matcher(clozeText);
        if (matcher.find()) {
            return matcher.group(1).trim();
        }
        return clozeText.trim();
    }

    private static void deleteDirectoryRecursively(Path dir) {
        try {
            if (Files.exists(dir)) {
                try (var stream = Files.walk(dir)) {
                    stream.sorted((a, b) -> b.compareTo(a))
                            .forEach(path -> {
                                try {
                                    Files.deleteIfExists(path);
                                } catch (IOException ignored) {
                                }
                            });
                }
            }
        } catch (IOException ignored) {
        }
    }
}
