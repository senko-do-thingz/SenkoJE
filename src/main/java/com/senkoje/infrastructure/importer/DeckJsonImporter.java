package com.senkoje.infrastructure.importer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.senkoje.application.dto.CardExtraData;
import com.senkoje.application.mapper.CardExtraDataCodec;
import com.senkoje.domain.model.CardExample;
import com.senkoje.domain.model.CardMeaning;
import com.senkoje.domain.model.CardType;
import com.senkoje.domain.model.Deck;
import com.senkoje.domain.model.DeckSettings;
import com.senkoje.domain.model.DefinitionLanguage;
import com.senkoje.domain.model.ProficiencyLevel;
import com.senkoje.domain.model.TargetLanguage;
import com.senkoje.infrastructure.exporter.DeckExportPackage;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/**
 * Tiện ích phân tích và xác thực tệp xuất JSON của SenkoJE trước khi đưa vào cơ sở dữ liệu.
 */
public class DeckJsonImporter {

    private final ObjectMapper objectMapper;
    private final Clock clock;

    /**
     * Khởi tạo bộ nhập JSON với đồng hồ UTC mặc định của hệ thống.
     */
    public DeckJsonImporter() {
        this(Clock.systemUTC());
    }

    /**
     * Khởi tạo bộ nhập JSON với đồng hồ được tiêm vào phục vụ kiểm thử.
     *
     * @param clock nguồn cung cấp thời gian
     */
    public DeckJsonImporter(Clock clock) {
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
        this.objectMapper = new ObjectMapper();
    }

    /**
     * Phân tích tệp JSON từ đường dẫn tệp tin và tạo báo cáo xem trước.
     *
     * @param filePath đường dẫn tệp tin JSON
     * @param existingTerms tập hợp các từ đã tồn tại trong bộ thẻ mục tiêu để kiểm tra trùng lặp
     * @return báo cáo xem trước toàn diện
     * @throws IOException nếu không đọc được tệp tin
     */
    public ImportPreviewReport generatePreview(Path filePath, Set<String> existingTerms) throws IOException {
        Objects.requireNonNull(filePath, "filePath must not be null");
        try (BufferedReader reader = Files.newBufferedReader(filePath, StandardCharsets.UTF_8)) {
            return generatePreview(reader, existingTerms);
        }
    }

    /**
     * Phân tích nội dung chuỗi JSON và tạo báo cáo xem trước.
     *
     * @param jsonText chuỗi JSON
     * @param existingTerms tập hợp các từ đã tồn tại trong bộ thẻ
     * @return báo cáo xem trước
     * @throws IOException nếu chuỗi JSON không hợp lệ
     */
    public ImportPreviewReport generatePreviewFromString(String jsonText, Set<String> existingTerms) throws IOException {
        Objects.requireNonNull(jsonText, "jsonText must not be null");
        return generatePreview(new StringReader(jsonText), existingTerms);
    }

    /**
     * Phân tích luồng ký tự Reader và sinh báo cáo xem trước chi tiết.
     *
     * @param reader luồng đọc ký tự JSON
     * @param existingTerms tập hợp các từ đã có trong bộ thẻ
     * @return báo cáo xem trước
     * @throws IOException nếu phát sinh lỗi vào ra dữ liệu
     */
    public ImportPreviewReport generatePreview(Reader reader, Set<String> existingTerms) throws IOException {
        Objects.requireNonNull(reader, "reader must not be null");

        JsonNode root;
        try {
            root = objectMapper.readTree(reader);
        } catch (JsonProcessingException exception) {
            ImportPreviewItem syntaxError = new ImportPreviewItem(
                    1,
                    null,
                    ImportItemStatus.INVALID,
                    "Malformed JSON syntax: " + exception.getOriginalMessage()
            );
            return ImportPreviewReport.of(List.of(syntaxError), null, null);
        }

        if (root == null || !root.isObject()) {
            ImportPreviewItem invalidRoot = new ImportPreviewItem(
                    1,
                    null,
                    ImportItemStatus.INVALID,
                    "JSON root must be an object"
            );
            return ImportPreviewReport.of(List.of(invalidRoot), null, null);
        }

        // Kiểm tra phiên bản lược đồ JSON
        String schemaVersion = root.path("schema_version").asText(null);
        if (schemaVersion == null || schemaVersion.isBlank()) {
            ImportPreviewItem invalidSchema = new ImportPreviewItem(
                    1,
                    null,
                    ImportItemStatus.INVALID,
                    "Missing required schema_version field"
            );
            return ImportPreviewReport.of(List.of(invalidSchema), null, null);
        }

        if (!DeckExportPackage.CURRENT_SCHEMA_VERSION.equals(schemaVersion)) {
            ImportPreviewItem unsupportedSchema = new ImportPreviewItem(
                    1,
                    null,
                    ImportItemStatus.INVALID,
                    "Unsupported schema version: " + schemaVersion + " (expected "
                            + DeckExportPackage.CURRENT_SCHEMA_VERSION + ")"
            );
            return ImportPreviewReport.of(List.of(unsupportedSchema), null, null);
        }

        Instant now = clock.instant();
        Deck candidateDeck = parseDeckMetadata(root.path("deck"), now);
        DeckSettings candidateSettings = parseDeckSettings(root.path("deck").path("settings"));

        Set<String> normalizedExisting = new HashSet<>();
        if (existingTerms != null) {
            for (String term : existingTerms) {
                if (term != null && !term.isBlank()) {
                    normalizedExisting.add(term.trim().toLowerCase(Locale.ROOT));
                }
            }
        }

        Set<String> seenInBatch = new HashSet<>();
        List<ImportPreviewItem> previewItems = new ArrayList<>();

        JsonNode cardsNode = root.path("cards");
        if (!cardsNode.isArray()) {
            ImportPreviewItem noCards = new ImportPreviewItem(
                    1,
                    null,
                    ImportItemStatus.INVALID,
                    "Field 'cards' must be an array"
            );
            return ImportPreviewReport.of(List.of(noCards), candidateDeck, candidateSettings);
        }

        int index = 1;
        for (JsonNode cardNode : cardsNode) {
            previewItems.add(validateAndParseCard(cardNode, index++, normalizedExisting, seenInBatch));
        }

        return ImportPreviewReport.of(previewItems, candidateDeck, candidateSettings);
    }

    private ImportPreviewItem validateAndParseCard(
            JsonNode node,
            int index,
            Set<String> existingTerms,
            Set<String> seenInBatch
    ) {
        String term = trimToNull(node.path("term").asText(null));
        if (term == null) {
            return new ImportPreviewItem(index, null, ImportItemStatus.INVALID, "Missing required field 'term'");
        }

        String meaning = trimToNull(node.path("meaning").asText(null));
        if (meaning == null) {
            return new ImportPreviewItem(index, null, ImportItemStatus.INVALID, "Missing required field 'meaning'");
        }

        String reading = trimToNull(node.path("reading").asText(null));
        String partOfSpeech = trimToNull(node.path("part_of_speech").asText(null));
        String notes = trimToNull(node.path("notes").asText(null));

        CardType cardType = CardType.VOCABULARY;
        String rawType = trimToNull(node.path("card_type").asText(null));
        if (rawType != null) {
            try {
                cardType = CardType.valueOf(rawType.toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ex) {
                return new ImportPreviewItem(
                        index,
                        null,
                        ImportItemStatus.INVALID,
                        "Invalid card_type: " + rawType + " (allowed: VOCABULARY, PHRASE, GRAMMAR)"
                );
            }
        }

        ProficiencyLevel level = null;
        String rawLevel = trimToNull(node.path("level").asText(null));
        if (rawLevel != null) {
            try {
                level = ProficiencyLevel.of(rawLevel);
            } catch (IllegalArgumentException ex) {
                return new ImportPreviewItem(
                        index,
                        null,
                        ImportItemStatus.INVALID,
                        "Invalid proficiency level: " + rawLevel
                );
            }
        }

        CardExtraData extraData = null;
        JsonNode extraNode = node.path("extra_data");
        if (extraNode.isObject()) {
            try {
                extraData = objectMapper.treeToValue(extraNode, CardExtraData.class);
            } catch (JsonProcessingException ignored) {
                // Giữ extraData là null nếu cấu trúc mở rộng không khớp
            }
        }

        List<CardMeaning> meanings = new ArrayList<>();
        JsonNode meaningsNode = node.path("meanings");
        if (meaningsNode.isArray()) {
            for (JsonNode mNode : meaningsNode) {
                String mText = trimToNull(mNode.path("meaning").asText(null));
                if (mText != null) {
                    String langCode = trimToNull(mNode.path("language_code").asText("vi"));
                    DefinitionLanguage defLang;
                    try {
                        defLang = DefinitionLanguage.fromCode(langCode);
                    } catch (IllegalArgumentException ex) {
                        defLang = DefinitionLanguage.VI;
                    }
                    int order = mNode.path("display_order").asInt(meanings.size() + 1);
                    meanings.add(new CardMeaning(null, null, defLang, mText, order));
                }
            }
        }

        List<CardExample> examples = new ArrayList<>();
        JsonNode examplesNode = node.path("examples");
        if (examplesNode.isArray()) {
            for (JsonNode exNode : examplesNode) {
                String sentence = trimToNull(exNode.path("sentence").asText(null));
                if (sentence != null) {
                    String translation = trimToNull(exNode.path("translation").asText(null));
                    String langCode = trimToNull(exNode.path("translation_language").asText("vi"));
                    DefinitionLanguage transLang;
                    try {
                        transLang = DefinitionLanguage.fromCode(langCode);
                    } catch (IllegalArgumentException ex) {
                        transLang = DefinitionLanguage.VI;
                    }
                    int order = exNode.path("display_order").asInt(examples.size() + 1);
                    examples.add(new CardExample(null, null, sentence, translation, transLang, order));
                }
            }
        }

        ImportCandidateCard candidateCard = new ImportCandidateCard(
                term,
                reading,
                meaning,
                cardType,
                level,
                partOfSpeech,
                notes,
                extraData,
                meanings,
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

    private Deck parseDeckMetadata(JsonNode node, Instant now) {
        if (!node.isObject()) {
            return null;
        }
        String title = trimToNull(node.path("title").asText(null));
        if (title == null) {
            title = "Imported Deck " + now.toString();
        }

        TargetLanguage targetLanguage = TargetLanguage.JA;
        String rawTarget = trimToNull(node.path("target_language").asText(null));
        if (rawTarget != null) {
            try {
                targetLanguage = TargetLanguage.fromCode(rawTarget);
            } catch (IllegalArgumentException ignored) {
            }
        }

        DefinitionLanguage defLang = DefinitionLanguage.VI;
        String rawDef = trimToNull(node.path("definition_language").asText(null));
        if (rawDef != null) {
            try {
                defLang = DefinitionLanguage.fromCode(rawDef);
            } catch (IllegalArgumentException ignored) {
            }
        }

        ProficiencyLevel level = ProficiencyLevel.JLPT_N5;
        String rawLevel = trimToNull(node.path("level").asText(null));
        if (rawLevel != null) {
            try {
                level = ProficiencyLevel.of(rawLevel);
            } catch (IllegalArgumentException ignored) {
            }
        }

        String description = trimToNull(node.path("description").asText(null));

        return new Deck(
                null,
                title,
                targetLanguage,
                defLang,
                level,
                description,
                now,
                now
        );
    }

    private DeckSettings parseDeckSettings(JsonNode node) {
        if (!node.isObject()) {
            return null;
        }
        double desiredRetention = node.path("desired_retention").asDouble(DeckSettings.DEFAULT_DESIRED_RETENTION);
        int maxInterval = node.path("maximum_interval").asInt(DeckSettings.DEFAULT_MAXIMUM_INTERVAL);
        int newCards = node.path("new_cards_per_day").asInt(DeckSettings.DEFAULT_NEW_CARDS_PER_DAY);
        int maxReviews = node.path("maximum_reviews_per_day").asInt(DeckSettings.DEFAULT_MAXIMUM_REVIEWS_PER_DAY);
        String learningSteps = trimToNull(node.path("learning_steps").asText(DeckSettings.DEFAULT_LEARNING_STEPS));
        String relearningSteps = trimToNull(node.path("relearning_steps").asText(DeckSettings.DEFAULT_RELEARNING_STEPS));
        boolean fuzzing = node.path("enable_fuzzing").asBoolean(DeckSettings.DEFAULT_ENABLE_FUZZING);

        return new DeckSettings(
                null,
                desiredRetention,
                maxInterval,
                newCards,
                maxReviews,
                learningSteps != null ? learningSteps : DeckSettings.DEFAULT_LEARNING_STEPS,
                relearningSteps != null ? relearningSteps : DeckSettings.DEFAULT_RELEARNING_STEPS,
                fuzzing
        );
    }

    private static String trimToNull(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        return text.trim();
    }
}
