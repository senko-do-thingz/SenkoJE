-- Lưu danh sách nghĩa bổ sung đa ngôn ngữ cho từng thẻ học tập
CREATE TABLE card_meanings (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    card_id INTEGER NOT NULL,
    language_code TEXT NOT NULL CHECK (language_code IN ('vi', 'en')),
    meaning TEXT NOT NULL,
    display_order INTEGER NOT NULL DEFAULT 0 CHECK (display_order >= 0),
    FOREIGN KEY (card_id) REFERENCES cards(id) ON DELETE CASCADE
);

-- Lưu danh sách câu ví dụ và bản dịch tương ứng cho từng thẻ
CREATE TABLE card_examples (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    card_id INTEGER NOT NULL,
    sentence TEXT NOT NULL,
    translation TEXT,
    translation_language TEXT CHECK (translation_language IS NULL OR translation_language IN ('vi', 'en')),
    display_order INTEGER NOT NULL DEFAULT 0 CHECK (display_order >= 0),
    FOREIGN KEY (card_id) REFERENCES cards(id) ON DELETE CASCADE
);

-- Tạo chỉ mục hỗ trợ nạp nhanh nghĩa bổ sung và câu ví dụ theo thứ tự hiển thị
CREATE INDEX idx_card_meanings_card_order
ON card_meanings(card_id, display_order);

CREATE INDEX idx_card_examples_card_order
ON card_examples(card_id, display_order);
