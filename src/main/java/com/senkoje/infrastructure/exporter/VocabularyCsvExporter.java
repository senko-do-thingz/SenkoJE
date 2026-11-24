package com.senkoje.infrastructure.exporter;

import com.senkoje.application.dto.CardExtraData;
import com.senkoje.application.mapper.CardExtraDataCodec;
import com.senkoje.domain.model.Card;
import com.senkoje.domain.model.CardExample;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.StringWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;

/**
 * Tiện ích xuất dữ liệu từ vựng sang định dạng CSV chuẩn theo đặc tả hệ thống SenkoJE
 * sử dụng thư viện Apache Commons CSV.
 */
public class VocabularyCsvExporter {

    /**
     * Danh sách tiêu đề cột cố định theo đặc tả kỹ thuật nhiệm vụ 6.1.
     */
    public static final String[] CSV_HEADERS = {
            "term",
            "reading",
            "meaning",
            "card_type",
            "proficiency_level",
            "example_sentence",
            "example_sentence_reading",
            "example_sentence_meaning",
            "notes",
            "tags"
    };

    /**
     * Khởi tạo bộ xuất CSV.
     */
    public VocabularyCsvExporter() {
    }

    /**
     * Xuất danh sách thẻ học tập ra luồng ký tự Writer theo định dạng CSV chuẩn.
     *
     * @param cards danh sách gói dữ liệu thẻ học
     * @param writer luồng ghi ký tự
     * @throws IOException nếu phát sinh lỗi vào ra dữ liệu
     */
    public void exportCards(List<ExportCardBundle> cards, Writer writer) throws IOException {
        Objects.requireNonNull(cards, "cards must not be null");
        Objects.requireNonNull(writer, "writer must not be null");

        CSVFormat csvFormat = CSVFormat.DEFAULT.builder()
                .setHeader(CSV_HEADERS)
                .build();

        try (CSVPrinter printer = csvFormat.print(writer)) {
            for (ExportCardBundle bundle : cards) {
                Card card = bundle.card();
                List<CardExample> examples = bundle.examples();

                String term = safeString(card.term());
                String reading = safeString(card.reading());
                String meaning = safeString(card.meaning());
                String cardType = card.cardType() != null ? card.cardType().name() : "";
                String level = card.level() != null ? card.level().code() : "";

                String exampleSentence = "";
                String exampleReading = "";
                String exampleMeaning = "";

                if (!examples.isEmpty()) {
                    CardExample firstExample = examples.get(0);
                    exampleSentence = safeString(firstExample.sentence());
                    exampleMeaning = safeString(firstExample.translation());
                }

                // Nếu thẻ có dữ liệu furigana mở rộng thì ưu tiên đưa vào cột cách đọc câu ví dụ nếu phù hợp
                CardExtraData extraData = CardExtraDataCodec.fromJson(card.extraData());
                if (extraData != null && extraData.furigana() != null && !extraData.furigana().isBlank()) {
                    exampleReading = extraData.furigana();
                }

                String notes = safeString(card.notes());
                String tags = "";

                printer.printRecord(
                        term,
                        reading,
                        meaning,
                        cardType,
                        level,
                        exampleSentence,
                        exampleReading,
                        exampleMeaning,
                        notes,
                        tags
                );
            }
            printer.flush();
        }
    }

    /**
     * Xuất danh sách thẻ học tập ra luồng nhị phân OutputStream theo mã hóa UTF-8 chuẩn.
     *
     * @param cards danh sách gói dữ liệu thẻ học
     * @param outputStream luồng nhị phân
     * @throws IOException nếu phát sinh lỗi vào ra dữ liệu
     */
    public void exportCards(List<ExportCardBundle> cards, OutputStream outputStream) throws IOException {
        Objects.requireNonNull(outputStream, "outputStream must not be null");
        BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(outputStream, StandardCharsets.UTF_8));
        exportCards(cards, writer);
        writer.flush();
    }

    /**
     * Xuất danh sách thẻ học tập ra tệp tin đích trên hệ thống tệp cục bộ.
     *
     * @param cards danh sách gói dữ liệu thẻ học
     * @param targetPath đường dẫn tệp tin đích
     * @throws IOException nếu phát sinh lỗi vào ra dữ liệu
     */
    public void exportCards(List<ExportCardBundle> cards, Path targetPath) throws IOException {
        Objects.requireNonNull(targetPath, "targetPath must not be null");
        Path parent = targetPath.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        try (BufferedWriter writer = Files.newBufferedWriter(targetPath, StandardCharsets.UTF_8)) {
            exportCards(cards, writer);
        }
    }

    /**
     * Chuyển đổi danh sách thẻ thành chuỗi CSV hoàn chỉnh bao gồm dòng tiêu đề.
     *
     * @param cards danh sách gói dữ liệu thẻ học
     * @return chuỗi văn bản định dạng CSV
     * @throws IOException nếu phát sinh lỗi tạo dữ liệu CSV
     */
    public String exportCardsToString(List<ExportCardBundle> cards) throws IOException {
        StringWriter writer = new StringWriter();
        exportCards(cards, writer);
        return writer.toString();
    }

    private static String safeString(String value) {
        return value == null ? "" : value;
    }
}
