-- Lưu thông tin các phiên học tập cục bộ của người dùng
CREATE TABLE study_sessions (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    deck_id INTEGER,
    started_at TEXT NOT NULL,
    completed_at TEXT,
    cards_reviewed INTEGER NOT NULL DEFAULT 0 CHECK (cards_reviewed >= 0),
    duration_seconds INTEGER NOT NULL DEFAULT 0 CHECK (duration_seconds >= 0),
    FOREIGN KEY (deck_id) REFERENCES decks(id) ON DELETE SET NULL
);

-- Tạo chỉ mục hỗ trợ truy vấn phiên học theo bộ thẻ và nhật ký ôn tập theo phiên học
CREATE INDEX idx_study_sessions_deck_id
ON study_sessions(deck_id);

CREATE INDEX idx_review_logs_session_id
ON review_logs(session_id);
