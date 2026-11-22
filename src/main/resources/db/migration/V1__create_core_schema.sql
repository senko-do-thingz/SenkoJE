-- Tạo lược đồ cốt lõi cho bộ thẻ, cấu hình lịch học và thẻ học tập
CREATE TABLE decks (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    title TEXT NOT NULL,
    target_language TEXT NOT NULL CHECK (target_language IN ('JA', 'EN')),
    definition_language TEXT NOT NULL CHECK (definition_language IN ('vi', 'en')),
    level TEXT NOT NULL,
    description TEXT,
    created_at TEXT NOT NULL,
    updated_at TEXT NOT NULL
);

-- Lưu cấu hình thuật toán FSRS-6 và giới hạn học hằng ngày cho từng bộ thẻ
CREATE TABLE deck_settings (
    deck_id INTEGER PRIMARY KEY,
    desired_retention REAL NOT NULL CHECK (desired_retention > 0.0 AND desired_retention < 1.0),
    maximum_interval INTEGER NOT NULL CHECK (maximum_interval >= 1),
    new_cards_per_day INTEGER NOT NULL CHECK (new_cards_per_day >= 0),
    maximum_reviews_per_day INTEGER NOT NULL CHECK (maximum_reviews_per_day >= 0),
    learning_steps TEXT NOT NULL,
    relearning_steps TEXT NOT NULL,
    enable_fuzzing INTEGER NOT NULL DEFAULT 1 CHECK (enable_fuzzing IN (0, 1)),
    FOREIGN KEY (deck_id) REFERENCES decks(id) ON DELETE CASCADE
);

-- Lưu trữ thẻ từ vựng, cụm từ và ngữ pháp thuộc từng bộ thẻ
CREATE TABLE cards (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    deck_id INTEGER NOT NULL,
    card_type TEXT NOT NULL CHECK (card_type IN ('VOCABULARY', 'PHRASE', 'GRAMMAR')),
    term TEXT NOT NULL,
    reading TEXT,
    meaning TEXT NOT NULL,
    part_of_speech TEXT,
    level TEXT,
    extra_data TEXT,
    notes TEXT,
    suspended INTEGER NOT NULL DEFAULT 0 CHECK (suspended IN (0, 1)),
    created_at TEXT NOT NULL,
    updated_at TEXT NOT NULL,
    FOREIGN KEY (deck_id) REFERENCES decks(id) ON DELETE CASCADE
);

-- Tạo chỉ mục tối ưu tốc độ truy vấn thẻ theo bộ thẻ và loại thẻ
CREATE INDEX idx_cards_deck_id
ON cards(deck_id);

CREATE INDEX idx_cards_type
ON cards(card_type);
