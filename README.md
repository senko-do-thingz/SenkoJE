# SenkoJE

**SenkoJE** (*Senko* - creator nickname; *J* = Japanese; *E* = English) is a local-first, offline-first desktop application for learning Japanese (JLPT N5-N1) and English (CEFR A1-C2).

Created and maintained by **[senko-do-thingz](https://github.com/senko-do-thingz)**.

---

## Overview

SenkoJE is built as a lightweight, distraction-free study companion that runs completely on your local machine without requiring an internet connection, cloud account, or external database server.

### Key Features

* **100% Local and Offline-First:** All decks, cards, and study progress are stored locally in an embedded SQLite database.
* **FSRS-6 Spaced Repetition:** Uses the FSRS-6 scheduling algorithm to optimize vocabulary, phrase, and grammar review intervals.
* **Multi-Level Support:** Designed for Japanese (JLPT N5-N1) and English (CEFR A1-C2) learners.
* **Offline Japanese Analysis:** Built-in Kuromoji morphological analysis for tokenization and Furigana reading assistance.
* **Bilingual Interface:** Instant switching between Vietnamese (`vi`) and English (`en`) UI languages, decoupled from card definition languages.
* **Dark-Mode-First UI:** Native JavaFX desktop interface supporting both Dark Mode (default) and Light Mode.
* **Data Ownership:** Supports CSV and JSON import/export as well as safe one-click local database backup and restore.

---

## Technology Stack

* **Runtime:** Java 25 LTS
* **Desktop UI:** JavaFX 25 (FXML + JavaFX CSS)
* **Database:** SQLite with Flyway schema migrations
* **Scheduling Engine:** FSRS-6
* **Japanese NLP:** Kuromoji
* **Build and Packaging:** Maven 3.9+, `jlink`, and `jpackage`

---

## Installation

### Option 1: End-Users (Native Installers)

No separate Java or database installation is required.

1. Go to the [Releases](https://github.com/senko-do-thingz/SenkoJE/releases) page of this repository.
2. Download the installer for your operating system:
   * **Windows:** `SenkoJE-<version>.exe` or `.msi`
   * **macOS:** `SenkoJE-<version>.dmg`
   * **Linux:** `SenkoJE-<version>.deb` or `.rpm`
3. Run the installer and launch **SenkoJE**. The local SQLite database initializes automatically on first startup.

### Option 2: Developers (Build from Source)

#### Prerequisites

* **JDK 25** (LTS)
* **Maven 3.9+**
* **Git**

#### Build and Run

```bash
git clone https://github.com/senko-do-thingz/SenkoJE.git
cd SenkoJE
mvn clean test
mvn javafx:run
```

## Project Documentation

Detailed architecture, database schema, and development documentation are available in the repository:

* [System Architecture](docs/ARCHITECTURE.md)

* [Features and UI Standards](docs/FEATURES_AND_UI.md)

* [FSRS-6 Specification](docs/FSRS_SPECIFICATION.md)

* [Database Schema](docs/DATABASE_SCHEMA.md)

* [Testing and Release Engineering](docs/TESTING_AND_RELEASE.md)

## License and Credit

* **Author:** [senko-do-thingz](https://github.com/senko-do-thingz)

* **License:** Custom Non-Commercial License. This project is provided strictly for personal, educational, and non-commercial use. Commercial sale, paid distribution, or commercial hosting is prohibited without explicit permission from the author.
