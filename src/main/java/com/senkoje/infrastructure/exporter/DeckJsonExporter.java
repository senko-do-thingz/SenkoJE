package com.senkoje.infrastructure.exporter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.senkoje.application.dto.CardExtraData;
import com.senkoje.application.mapper.CardExtraDataCodec;
import com.senkoje.domain.model.Card;
import com.senkoje.domain.model.CardExample;
import com.senkoje.domain.model.CardMeaning;
import com.senkoje.domain.model.Deck;
import com.senkoje.domain.model.DeckSettings;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.StringWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Tiện ích xuất dữ liệu bộ thẻ sang tệp tin định dạng JSON chuẩn sử dụng thư viện Jackson.
 */
public class DeckJsonExporter {

    private final ObjectMapper objectMapper;
    private final Clock clock;

    /**
     * Khởi tạo bộ xuất JSON với đồng hồ UTC mặc định của hệ thống.
     */
    public DeckJsonExporter() {
        this(Clock.systemUTC());
    }

    /**
     * Khởi tạo bộ xuất JSON với đồng hồ chỉ định nhằm phục vụ kiểm thử tất định.
     *
     * @param clock nguồn cung cấp thời gian
     */
    public DeckJsonExporter(Clock clock) {
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
        this.objectMapper = createConfiguredObjectMapper();
    }

    /**
     * Xuất trọn vẹn thông tin bộ thẻ và danh sách thẻ ra luồng ký tự Writer.
     *
     * @param deck thực thể bộ thẻ
     * @param settings cấu hình FSRS-6 của bộ thẻ (nếu null sẽ áp dụng cấu hình mặc định)
     * @param cards danh sách các gói dữ liệu thẻ
     * @param writer luồng ghi dữ liệu
     * @throws IOException nếu phát sinh lỗi vào ra dữ liệu
     */
    public void exportDeck(Deck deck, DeckSettings settings, List<ExportCardBundle> cards, Writer writer) throws IOException {
        Objects.requireNonNull(deck, "deck must not be null");
        Objects.requireNonNull(cards, "cards must not be null");
        Objects.requireNonNull(writer, "writer must not be null");

        DeckExportPackage.Root exportRoot = buildExportPackage(deck, settings, cards);
        objectMapper.writerWithDefaultPrettyPrinter().writeValue(writer, exportRoot);
    }

    /**
     * Xuất trọn vẹn thông tin bộ thẻ ra luồng nhị phân OutputStream theo mã hóa UTF-8.
     *
     * @param deck thực thể bộ thẻ
     * @param settings cấu hình FSRS-6 của bộ thẻ
     * @param cards danh sách các gói dữ liệu thẻ
     * @param outputStream luồng nhị phân
     * @throws IOException nếu phát sinh lỗi vào ra dữ liệu
     */
    public void exportDeck(Deck deck, DeckSettings settings, List<ExportCardBundle> cards, OutputStream outputStream)
            throws IOException {
        Objects.requireNonNull(outputStream, "outputStream must not be null");
        BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(outputStream, StandardCharsets.UTF_8));
        exportDeck(deck, settings, cards, writer);
        writer.flush();
    }

    /**
     * Xuất trọn vẹn thông tin bộ thẻ ra tệp tin đích trên hệ thống tệp cục bộ.
     *
     * @param deck thực thể bộ thẻ
     * @param settings cấu hình FSRS-6 của bộ thẻ
     * @param cards danh sách các gói dữ liệu thẻ
     * @param targetPath đường dẫn tệp tin đích
     * @throws IOException nếu phát sinh lỗi vào ra dữ liệu
     */
    public void exportDeck(Deck deck, DeckSettings settings, List<ExportCardBundle> cards, Path targetPath) throws IOException {
        Objects.requireNonNull(targetPath, "targetPath must not be null");
        Path parent = targetPath.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        try (BufferedWriter writer = Files.newBufferedWriter(targetPath, StandardCharsets.UTF_8)) {
            exportDeck(deck, settings, cards, writer);
        }
    }

    /**
     * Chuyển đổi bộ thẻ thành chuỗi JSON hoàn chỉnh có định dạng thụt lề đẹp mắt.
     *
     * @param deck thực thể bộ thẻ
     * @param settings cấu hình FSRS-6 của bộ thẻ
     * @param cards danh sách các gói dữ liệu thẻ
     * @return chuỗi JSON định dạng đẹp
     * @throws IOException nếu phát sinh lỗi tuần tự hóa
     */
    public String exportDeckToString(Deck deck, DeckSettings settings, List<ExportCardBundle> cards) throws IOException {
        StringWriter writer = new StringWriter();
        exportDeck(deck, settings, cards, writer);
        return writer.toString();
    }

    /**
     * Xây dựng cấu trúc dữ liệu xuất chuẩn từ các thực thể miền của ứng dụng.
     *
     * @param deck thực thể bộ thẻ
     * @param settings cấu hình bộ thẻ
     * @param cards danh sách các gói dữ liệu thẻ
     * @return đối tượng gốc đóng gói dữ liệu JSON
     */
    public DeckExportPackage.Root buildExportPackage(Deck deck, DeckSettings settings, List<ExportCardBundle> cards) {
        DeckSettings effectiveSettings = settings != null ? settings : DeckSettings.defaults(deck.id());

        DeckExportPackage.SettingsItem settingsItem = new DeckExportPackage.SettingsItem(
                effectiveSettings.desiredRetention(),
                effectiveSettings.maximumInterval(),
                effectiveSettings.newCardsPerDay(),
                effectiveSettings.maximumReviewsPerDay(),
                effectiveSettings.learningSteps(),
                effectiveSettings.relearningSteps(),
                effectiveSettings.enableFuzzing()
        );

        DeckExportPackage.DeckMetadata deckMetadata = new DeckExportPackage.DeckMetadata(
                deck.title(),
                deck.targetLanguage() != null ? deck.targetLanguage().name() : null,
                deck.definitionLanguage() != null ? deck.definitionLanguage().getCode() : null,
                deck.level() != null ? deck.level().code() : null,
                deck.description(),
                settingsItem
        );

        List<DeckExportPackage.CardItem> cardItems = new ArrayList<>(cards.size());
        for (ExportCardBundle bundle : cards) {
            Card card = bundle.card();
            CardExtraData extraData = CardExtraDataCodec.fromJson(card.extraData());
            CardExtraData effectiveExtraData = (extraData != null && !extraData.isEmpty()) ? extraData : null;

            List<DeckExportPackage.MeaningItem> meaningItems = new ArrayList<>();
            for (CardMeaning meaning : bundle.meanings()) {
                meaningItems.add(new DeckExportPackage.MeaningItem(
                        meaning.languageCode() != null ? meaning.languageCode().getCode() : null,
                        meaning.meaning(),
                        meaning.displayOrder()
                ));
            }

            List<DeckExportPackage.ExampleItem> exampleItems = new ArrayList<>();
            for (CardExample example : bundle.examples()) {
                exampleItems.add(new DeckExportPackage.ExampleItem(
                        example.sentence(),
                        example.translation(),
                        example.translationLanguage() != null ? example.translationLanguage().getCode() : null,
                        example.displayOrder()
                ));
            }

            cardItems.add(new DeckExportPackage.CardItem(
                    card.term(),
                    card.reading(),
                    card.meaning(),
                    card.cardType() != null ? card.cardType().name() : null,
                    card.level() != null ? card.level().code() : null,
                    card.partOfSpeech(),
                    card.notes(),
                    effectiveExtraData,
                    meaningItems.isEmpty() ? null : meaningItems,
                    exampleItems.isEmpty() ? null : exampleItems
            ));
        }

        Instant exportTime = clock.instant();
        return new DeckExportPackage.Root(
                DeckExportPackage.CURRENT_SCHEMA_VERSION,
                exportTime,
                DeckExportPackage.GENERATOR_NAME,
                deckMetadata,
                cardItems
        );
    }

    private static ObjectMapper createConfiguredObjectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        return mapper;
    }
}
