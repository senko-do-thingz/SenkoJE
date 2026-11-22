# Database Schema Specification - SenkoJE

## 1. Database Location

Use the operating system's application data directory.

Windows:

```text
%APPDATA%/SenkoJE/senkoje.db
```

macOS:

```text
~/Library/Application Support/SenkoJE/senkoje.db
```

Linux:

```text
~/.local/share/SenkoJE/senkoje.db
```

Never store the production database inside the installation directory or application JAR.

## 2. Database Tables

### 2.1. decks

| Column | Type | Description |
| ----- | ----- | ----- |
| id | INTEGER | Primary key |
| title | TEXT | Deck name |
| target_language | TEXT | JA / EN |
| definition_language | TEXT | vi / en |
| level | TEXT | JLPT / CEFR / custom |
| description | TEXT | Description |
| created_at | TEXT | Creation timestamp |
| updated_at | TEXT | Last modification |

### 2.2. deck_settings

| Column | Type | Description |
| ----- | ----- | ----- |
| deck_id | INTEGER | Primary key and foreign key |
| desired_retention | REAL | Target recall probability |
| maximum_interval | INTEGER | Maximum interval in days |
| new_cards_per_day | INTEGER | New card limit |
| maximum_reviews_per_day | INTEGER | Review limit |
| learning_steps | TEXT | Serialized step configuration |
| relearning_steps | TEXT | Serialized step configuration |
| enable_fuzzing | INTEGER | Boolean |

Settings must be validated before persistence.

### 2.3. cards

| Column | Type | Description |
| ----- | ----- | ----- |
| id | INTEGER | Primary key |
| deck_id | INTEGER | Foreign key |
| card_type | TEXT | VOCABULARY / PHRASE / GRAMMAR |
| term | TEXT | Main content |
| reading | TEXT | Reading or pronunciation |
| meaning | TEXT | Primary definition |
| part_of_speech | TEXT | Part of speech |
| level | TEXT | Proficiency level |
| extra_data | TEXT | Optional structured JSON |
| notes | TEXT | Personal notes |
| suspended | INTEGER | Suspension flag |
| created_at | TEXT | Creation timestamp |
| updated_at | TEXT | Modification timestamp |

### 2.4. card_meanings

| Column | Type | Description |
| ----- | ----- | ----- |
| id | INTEGER | Primary key |
| card_id | INTEGER | Foreign key |
| language_code | TEXT | vi / en |
| meaning | TEXT | Definition |
| display_order | INTEGER | Display position |

### 2.5. card_examples

| Column | Type | Description |
| ----- | ----- | ----- |
| id | INTEGER | Primary key |
| card_id | INTEGER | Foreign key |
| sentence | TEXT | Original sentence |
| translation | TEXT | Translation |
| translation_language | TEXT | vi / en |
| display_order | INTEGER | Display position |

### 2.6. review_states

One current scheduling state per card.

| Column | Type | Description |
| ----- | ----- | ----- |
| card_id | INTEGER | Primary key and foreign key |
| state | TEXT | NEW / LEARNING / REVIEW / RELEARNING |
| stability | REAL | FSRS stability |
| difficulty | REAL | FSRS difficulty |
| step | INTEGER | Learning step, nullable |
| due_at | TEXT | Next review timestamp |
| last_reviewed_at | TEXT | Last review timestamp |
| scheduled_days | REAL | Previous scheduled interval |
| elapsed_days | REAL | Previous elapsed interval |
| repetitions | INTEGER | Total review count |
| lapses | INTEGER | Failed review count |
| updated_at | TEXT | Last state update |

### 2.7. review_logs

Immutable history of completed reviews.

| Column | Type | Description |
| ----- | ----- | ----- |
| id | INTEGER | Primary key |
| card_id | INTEGER | Foreign key |
| session_id | INTEGER | Associated study session |
| rating | INTEGER | 1-4 |
| reviewed_at | TEXT | Review timestamp |
| response_time_ms | INTEGER | Time spent answering |
| previous_state | TEXT | State before review |
| next_state | TEXT | State after review |
| previous_stability | REAL | Previous stability |
| next_stability | REAL | New stability |
| previous_difficulty | REAL | Previous difficulty |
| next_difficulty | REAL | New difficulty |
| elapsed_days | REAL | Actual elapsed time |
| scheduled_days | REAL | Computed interval |
| algorithm_version | TEXT | FSRS version |
| parameter_version | TEXT | Parameter set identifier |

Review logs must not be deleted when a card's scheduling state changes.

Deleting a card may remove its associated records through explicit cascade rules, with confirmation.

### 2.8. study_sessions

| Column | Type | Description |
| ----- | ----- | ----- |
| id | INTEGER | Primary key |
| deck_id | INTEGER | Associated deck, nullable |
| started_at | TEXT | Start timestamp |
| completed_at | TEXT | End timestamp |
| cards_reviewed | INTEGER | Number of completed reviews |
| duration_seconds | INTEGER | Session duration |

### 2.9. app_settings

| Column | Type | Description |
| ----- | ----- | ----- |
| setting_key | TEXT | Primary key |
| setting_value | TEXT | Stored value |
| updated_at | TEXT | Last modification |

Stores:

* Interface language.
* Theme.
* TTS settings.
* Global preferences.
* Window preferences.
* Other non-sensitive local configuration.

## 3. Database Constraints

Mandatory requirements:

* Enable SQLite foreign keys for every connection.
* Use prepared statements.
* Use transactions for multi-table operations.
* Add indexes for common filtering and scheduling queries.
* Enforce unique constraints where logically necessary.
* Validate JSON before storing structured data.
* Avoid unnecessary denormalization.
* Never execute SQL from JavaFX controllers.

Foreign key enforcement must be enabled and verified on every database connection.

## 4. Recommended Indexes

```sql
-- Tạo chỉ mục tối ưu tốc độ truy vấn thẻ theo bộ thẻ và loại thẻ
CREATE INDEX idx_cards_deck_id
ON cards(deck_id);

CREATE INDEX idx_cards_type
ON cards(card_type);

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
```

## 5. Database Migrations

Use Flyway.

Migration files:

```text
src/main/resources/db/migration/
    V1__create_core_schema.sql
    V2__add_card_content_tables.sql
    V3__add_fsrs_review_history.sql
    V4__add_study_sessions.sql
    V5__add_application_settings.sql
```

The actual migration sequence must match the implemented schema.

Never modify a migration that has already been released to users.

Always create a new migration for schema changes.
