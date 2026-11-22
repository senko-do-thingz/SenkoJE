-- Lưu trạng thái lập lịch FSRS-6 hiện tại của từng thẻ học tập
CREATE TABLE review_states (
    card_id INTEGER PRIMARY KEY,
    state TEXT NOT NULL CHECK (state IN ('NEW', 'LEARNING', 'REVIEW', 'RELEARNING')),
    stability REAL NOT NULL DEFAULT 0.0 CHECK (stability >= 0.0),
    difficulty REAL NOT NULL DEFAULT 0.0 CHECK (difficulty >= 0.0),
    step INTEGER CHECK (step IS NULL OR step >= 0),
    due_at TEXT,
    last_reviewed_at TEXT,
    scheduled_days REAL NOT NULL DEFAULT 0.0 CHECK (scheduled_days >= 0.0),
    elapsed_days REAL NOT NULL DEFAULT 0.0 CHECK (elapsed_days >= 0.0),
    repetitions INTEGER NOT NULL DEFAULT 0 CHECK (repetitions >= 0),
    lapses INTEGER NOT NULL DEFAULT 0 CHECK (lapses >= 0),
    updated_at TEXT NOT NULL,
    FOREIGN KEY (card_id) REFERENCES cards(id) ON DELETE CASCADE
);

-- Lưu nhật ký bất biến của toàn bộ lượt ôn tập đã hoàn thành
-- Cột session_id tham chiếu đến bảng study_sessions được tạo ở kịch bản V4
CREATE TABLE review_logs (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    card_id INTEGER NOT NULL,
    session_id INTEGER,
    rating INTEGER NOT NULL CHECK (rating BETWEEN 1 AND 4),
    reviewed_at TEXT NOT NULL,
    response_time_ms INTEGER CHECK (response_time_ms IS NULL OR response_time_ms >= 0),
    previous_state TEXT NOT NULL CHECK (previous_state IN ('NEW', 'LEARNING', 'REVIEW', 'RELEARNING')),
    next_state TEXT NOT NULL CHECK (next_state IN ('NEW', 'LEARNING', 'REVIEW', 'RELEARNING')),
    previous_stability REAL NOT NULL CHECK (previous_stability >= 0.0),
    next_stability REAL NOT NULL CHECK (next_stability >= 0.0),
    previous_difficulty REAL NOT NULL CHECK (previous_difficulty >= 0.0),
    next_difficulty REAL NOT NULL CHECK (next_difficulty >= 0.0),
    elapsed_days REAL NOT NULL CHECK (elapsed_days >= 0.0),
    scheduled_days REAL NOT NULL CHECK (scheduled_days >= 0.0),
    algorithm_version TEXT NOT NULL,
    parameter_version TEXT NOT NULL,
    FOREIGN KEY (card_id) REFERENCES cards(id) ON DELETE CASCADE,
    FOREIGN KEY (session_id) REFERENCES study_sessions(id) ON DELETE SET NULL
);

-- Tạo chỉ mục tối ưu truy vấn lịch ôn tập FSRS-6
CREATE INDEX idx_review_due
ON review_states(due_at);

CREATE INDEX idx_review_state
ON review_states(state);

-- Tạo chỉ mục tối ưu thống kê lịch sử học tập
CREATE INDEX idx_review_logs_card_date
ON review_logs(card_id, reviewed_at);

CREATE INDEX idx_review_logs_date
ON review_logs(reviewed_at);
