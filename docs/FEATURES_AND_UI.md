# Features and UI Standards - SenkoJE

## 1. Application Features

### 1.1. Dashboard

The Dashboard is the default landing page.

Display:

* Total decks.
* Total cards.
* Cards due today.
* New cards available.
* Reviews completed today.
* Daily study time.
* Current learning streak.
* Learning activity heatmap.
* Recent study activity.

All dashboard information must be derived from actual local database records.

Do not use hardcoded demonstration statistics in production builds.

### 1.2. Deck Management

Users must be able to:

* Create decks.
* Rename decks.
* Edit descriptions.
* Delete decks.
* Duplicate decks.
* Search and filter decks.
* Organize decks by language.
* Assign proficiency levels.
* Configure learning settings.

Supported target languages:

* `JA`: Japanese.
* `EN`: English.

Supported proficiency classifications:

* JLPT N5, N4, N3, N2, N1.
* CEFR A1, A2, B1, B2, C1, C2.
* Custom level.

A deck may contain vocabulary, phrases, or grammar cards. The card type must be explicit.

### 1.3. Card Management

Supported card types:

* Vocabulary.
* Phrase.
* Grammar.

Japanese card fields:

* Term.
* Kana reading.
* Furigana.
* Meaning.
* Part of speech.
* Kanji components.
* On'yomi.
* Kun'yomi.
* Pitch accent, if available.
* Example sentences.
* Example translations.
* JLPT level.
* Personal notes.

English card fields:

* Word or phrase.
* IPA transcription.
* Meaning.
* Part of speech.
* Collocations.
* Register.
* Example sentences.
* Example translations.
* CEFR level.
* Personal notes.

Users must be able to:

* Create cards.
* Edit cards.
* Delete cards.
* Search cards.
* Filter cards.
* Preview cards.
* Suspend and unsuspend cards.
* Browse cards independently of Study Mode.

Support multiple meanings and examples where necessary.

Use structured related records for repeatable information instead of storing every field inside an unrestricted JSON object.

### 1.4. Flashcard Study Mode

The study interface must prioritize concentration and readability.

Keyboard shortcuts:

| Key | Action |
| ----- | ----- |
| Space | Flip card |
| 1 | Again |
| 2 | Hard |
| 3 | Good |
| 4 | Easy |
| R | Replay audio |
| Esc | Exit study session |

Requirements:

* Display one card at a time.
* Allow users to reveal answers.
* Display rating buttons after revealing the answer.
* Show scheduling previews when available.
* Record review duration.
* Save progress immediately after each completed review.
* Prevent accidental duplicate submissions.
* Allow users to pause and resume a study session.

The application must not silently discard completed reviews when a session is closed.

### 1.5. Grammar Learning

Grammar cards must support:

* Grammar patterns.
* Meaning and usage.
* Formation rules.
* Example sentences.
* Translations.
* Difficulty level.
* Related vocabulary.
* Personal notes.

Grammar cards must use the same review engine as vocabulary cards, while allowing different presentation templates.

### 1.6. Reading Sandbox

Users can paste Japanese or English text into a dedicated workspace.

Japanese processing:

* Tokenization.
* Reading extraction.
* Furigana display.
* Part-of-speech information.
* Click-to-inspect vocabulary.
* Add selected vocabulary to an existing or new deck.

English processing:

* Sentence segmentation.
* Basic tokenization.
* Word selection.
* Dictionary lookup when an offline dictionary is available.
* Add selected vocabulary to a deck.

Kuromoji must operate locally (lazy-loaded on first use to keep startup fast).

Do not claim that tokenization alone provides complete semantic understanding, dictionary definitions, or pitch accent information.

### 1.7. Dictation and Typing Practice

Support:

* Listening and typing.
* Japanese Kana input.
* Japanese Kanji input.
* English spelling practice.
* Case-insensitive English comparison where appropriate.
* Configurable punctuation handling.
* Optional strict character matching.

Japanese validation must not incorrectly treat different valid readings or orthographic variants as equivalent without explicit rules.

### 1.8. Text-to-Speech

Define a provider-based TTS abstraction:

```text
TtsService
    |
    +-- SystemTtsProvider
    |
    +-- BundledOfflineTtsProvider
    |
    +-- UnsupportedTtsProvider
```

Requirements:

* Japanese and English voice selection.
* Speech rate configuration.
* Playback controls.
* Graceful handling of unavailable voices.
* No mandatory online TTS services.

JavaFX Media APIs provide media playback, not a complete cross-platform TTS engine.

Therefore, TTS providers must be implemented and tested independently.

The application must explicitly report when offline speech synthesis is unavailable.

Do not claim universal offline TTS support until verified on the target operating systems.

## 2. Internationalization

### 2.1. Interface Languages

Supported interface languages:

* Vietnamese (`vi`).
* English (`en`).

The application must switch interface language without restarting.

### 2.2. Study Definition Language

Interface language and study definition language must remain independent.

Example:

```text
Interface Language: English
Target Language: Japanese
Definition Language: Vietnamese
Proficiency Level: JLPT N2
```

### 2.3. Resource Structure

```text
src/main/resources/
    i18n/
        messages_vi.properties
        messages_en.properties
```

Use standard Java ResourceBundle or an equivalent JavaFX-compatible localization solution.

All UI labels, buttons, menus, alerts, tooltips, and validation messages must use localization keys.

Do not hardcode translated strings in controllers.

### 2.4. Unicode and Typography

Unicode is mandatory for learning content.

The application must correctly display:

* Vietnamese diacritics.
* Kanji.
* Hiragana.
* Katakana.
* Romaji.
* IPA.
* Common mathematical and linguistic notation.

Recommended fonts:

* Inter for Latin and Vietnamese.
* Noto Sans JP for Japanese.
* Appropriate fallback fonts for missing glyphs.

Font fallback must be tested on supported operating systems.

## 3. UI/UX Design Standards

### 3.1. Theme

Supported themes:

* Dark Mode.
* Light Mode.

Dark Mode is the default on first launch.

Remember the user's selected theme across restarts.

### 3.2. Main Navigation

Use a collapsible left sidebar.

Navigation items:

1. Dashboard.
2. Decks.
3. Study.
4. Reading Sandbox.
5. Card Browser.
6. Import / Export.
7. Settings.

### 3.3. Main Application Screens

#### Dashboard

* Daily learning overview.
* Due review count.
* Study streak.
* Learning activity.
* Recent sessions.

#### Deck Library

* Deck cards or rows.
* Search.
* Language filter.
* Proficiency filter.
* Create and edit actions.

#### Card Browser

* Searchable card table.
* Filtering by type and level.
* Bulk selection.
* Edit and delete actions.
* Card preview.

#### Study Workspace

* Minimalist card presentation.
* Clear answer reveal.
* Rating controls.
* Keyboard shortcuts.
* Audio playback.

#### Reading Sandbox

* Text input.
* Reading display.
* Tokenization.
* Vocabulary inspection.
* Add-to-deck action.

#### Settings

* Interface language.
* Theme.
* TTS provider and voice.
* Speech speed.
* SRS configuration.
* Backup and restore.
* Application information.

### 3.4. Visual Guidelines

* Prioritize readability.
* Avoid unnecessary animations.
* Keep navigation consistent.
* Provide clear empty states.
* Provide clear error states.
* Support keyboard navigation.
* Avoid blocking the JavaFX Application Thread with expensive operations.
* Use background tasks for file processing, NLP, and database operations where appropriate.
* Follow JavaFX accessibility best practices.

## 4. Data Management and Portability

### 4.1. Import Formats

Required:

* CSV.
* JSON.

Optional:

* Anki `.apkg`.

Import requirements:

* Validate files before modifying the database.
* Preview import results.
* Detect malformed records.
* Report skipped and rejected records.
* Prevent accidental duplicate imports where possible.
* Use transactions for atomic imports.
* Never silently overwrite existing user data.

### 4.2. Anki Importer

An `.apkg` importer may:

1. Inspect the archive.
2. Extract its contents into a temporary directory.
3. Read the embedded SQLite collection.
4. Parse notes, cards, templates, and scheduling history where supported.
5. Convert supported fields into SenkoJE entities.
6. Report unsupported structures.
7. Import validated data.

Do not assume all Anki note types can be converted losslessly.

Preserve the original source archive when users request it.

### 4.3. Export

Support:

* Individual deck JSON export.
* CSV vocabulary export.
* Full database backup.

### 4.4. Backup and Restore

Requirements:

* Provide one-click backup.
* Provide one-click restore.
* Validate backup files.
* Verify database integrity after restore.
* Create a safety backup before replacing the current database.
* Handle interrupted operations safely.

Do not simply copy an actively written SQLite database file without accounting for SQLite journal or WAL state.

Use SQLite's online backup mechanism or an equivalent safe backup procedure.

## 5. Japanese Language Processing

### 5.1. Kuromoji

Use Kuromoji for offline Japanese morphological analysis.

Supported capabilities:

* Tokenization.
* Part-of-speech extraction.
* Reading extraction.
* Furigana assistance.
* Token-level inspection.

### 5.2. Furigana

Requirements:

* Display readings above appropriate Kanji segments.
* Preserve original text.
* Handle punctuation.
* Handle mixed Kanji and Kana.
* Handle conjugated words.
* Allow users to correct ambiguous readings.

Do not claim perfect Furigana generation for all Japanese text.

### 5.3. Dictionary Data

Dictionary integration is a separate concern from tokenization.

Any bundled dictionary must:

* Work offline.
* Have documented provenance.
* Have a compatible redistribution license.
* Include attribution where required.
* Be stored separately from application code where appropriate.

Pitch accent information must come from a verified source that explicitly provides pitch accent data.

Do not infer pitch accent from Kuromoji alone.
