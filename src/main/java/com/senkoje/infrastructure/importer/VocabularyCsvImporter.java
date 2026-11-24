package com.senkoje.infrastructure.importer;

import com.senkoje.application.dto.CardExtraData;
import com.senkoje.domain.model.CardExample;
import com.senkoje.domain.model.CardMeaning;
import com.senkoje.domain.model.CardType;
import com.senkoje.domain.model.DefinitionLanguage;
import com.senkoje.domain.model.ProficiencyLevel;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;

/**
 * Tiện ích phân tích và xác thực tệp từ vựng CSV trước khi nhập vào cơ sở dữ liệu.
 */
public class VocabularyCsvImporter {

    public VocabularyCsvImporter() {
    }

    /**
     * Phân tích tệp CSV từ đường dẫn tệp tin và tạo báo cáo xem trước.
     *
     * @param filePath đường dẫn tệp CSV
     * @param existingTerms tập hợp từ vựng hiện có trong bộ thẻ để phát hiện trùng lặp
     * @return báo cáo xem trước kết quả nhập
     * @throws IOException nếu không đọc được tệp tin
     */
    public ImportPreviewReport generatePreview(Path filePath, Set<String> existingTerms) throws IOException {
        Objects.requireNonNull(filePath, "filePath must not be null");
        try (BufferedReader reader = Files.newBufferedReader(filePath, StandardCharsets.UTF_8)) {
            return generatePreview(reader, existingTerms);
        }
    }

    /**
     * Phân tích chuỗi ký tự định dạng CSV và tạo báo cáo xem trước.
     *
     * @param csvText nội dung CSV dạng chuỗi
     * @param existingTerms tập hợp từ vựng hiện có trong bộ thẻ
     * @return báo cáo xem trước
     * @throws IOException nếu phát sinh lỗi phân tích
     */
    public ImportPreviewReport generatePreviewFromString(String csvText, Set<String> existingTerms) throws IOException {
        Objects.requireNonNull(csvText, "csvText must not be null");
        return generatePreview(new StringReader(csvText), existingTerms);
    }

    /**
     * Phân tích luồng đọc ký tự Reader CSV và sinh báo cáo xem trước chi tiết.
     *
     * @param reader luồng đọc ký tự CSV
     * @param existingTerms tập hợp từ vựng đã có trong bộ thẻ
     * @return báo cáo xem trước
     * @throws IOException nếu phát sinh lỗi vào ra dữ liệu
     */
    public ImportPreviewReport generatePreview(Reader reader, Set<String> existingTerms) throws IOException {
        Objects.requireNonNull(reader, "reader must not be null");

        CSVFormat format = CSVFormat.DEFAULT.builder()
                .setHeader()
                .setSkipHeaderRecord(true)
                .setIgnoreHeaderCase(true)
                .setTrim(true)
                .build();

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

        try (CSVParser parser = format.parse(reader)) {
            int rowIndex = 1;
            for (CSVRecord record : parser) {
                previewItems.add(validateAndParseRecord(record, rowIndex++, normalizedExisting, seenInBatch));
            }
        }

        return ImportPreviewReport.of(previewItems, null, null);
    }

    private ImportPreviewItem validateAndParseRecord(
            CSVRecord record,
            int index,
            Set<String> existingTerms,
            Set<String> seenInBatch
    ) {
        String term = getField(record, "term", 0);
        if (term == null || term.isBlank()) {
            return new ImportPreviewItem(index, null, ImportItemStatus.INVALID, "Missing required column 'term'");
        }
        term = term.trim();

        String meaning = getField(record, "meaning", 2);
        if (meaning == null || meaning.isBlank()) {
            return new ImportPreviewItem(index, null, ImportItemStatus.INVALID, "Missing required column 'meaning'");
        }
        meaning = meaning.trim();

        String reading = getField(record, "reading", 1);
        String rawType = getField(record, "card_type", 3);
        String rawLevel = getField(record, "proficiency_level", 4);
        String exampleSentence = getField(record, "example_sentence", 5);
        String exampleReading = getField(record, "example_sentence_reading", 6);
        String exampleMeaning = getField(record, "example_sentence_meaning", 7);
        String notes = getField(record, "notes", 8);

        CardType cardType = CardType.VOCABULARY;
        if (rawType != null && !rawType.isBlank()) {
            try {
                cardType = CardType.valueOf(rawType.trim().toUpperCase(Locale.ROOT));
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
        if (rawLevel != null && !rawLevel.isBlank()) {
            try {
                level = ProficiencyLevel.of(rawLevel.trim());
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
        if (exampleReading != null && !exampleReading.isBlank()) {
            extraData = new CardExtraData(
                    exampleReading.trim(),
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null
            );
        }

        List<CardExample> examples = new ArrayList<>();
        if (exampleSentence != null && !exampleSentence.isBlank()) {
            examples.add(new CardExample(
                    null,
                    null,
                    exampleSentence.trim(),
                    exampleMeaning != null && !exampleMeaning.isBlank() ? exampleMeaning.trim() : null,
                    DefinitionLanguage.VI,
                    1
            ));
        }

        ImportCandidateCard candidateCard = new ImportCandidateCard(
                term,
                reading != null && !reading.isBlank() ? reading.trim() : null,
                meaning,
                cardType,
                level,
                null,
                notes != null && !notes.isBlank() ? notes.trim() : null,
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

    private static String getField(CSVRecord record, String headerName, int fallbackIndex) {
        if (record.isMapped(headerName)) {
            String val = record.get(headerName);
            return val != null ? val : "";
        }
        if (fallbackIndex < record.size()) {
            return record.get(fallbackIndex);
        }
        return "";
    }
}
