# System Architecture - SenkoJE

## 1. Project Vision and Core Principles

### 1.1. Application Overview

SenkoJE is a lightweight, distraction-free desktop application designed to help users learn Japanese and English through structured vocabulary acquisition, grammar study, spaced repetition, reading practice, and listening exercises.

The application supports:

* Japanese proficiency levels: JLPT N5-N1.
* English proficiency levels: CEFR A1-C2.
* Vocabulary and phrase learning.
* Grammar learning.
* Contextual reading.
* Listening and dictation.
* Personalized spaced repetition.
* Local learning analytics.

SenkoJE is maintained as an independent educational project by `senko-do-thingz`.

### 1.2. Core Principles

#### Local-First and Offline-First

* All core application functionality must work without an Internet connection.
* All user data must remain on the local machine.
* No cloud accounts, remote databases, authentication servers, or online dependencies are permitted.
* No telemetry, analytics trackers, advertisements, or payment integrations.
* Network connectivity must never be required for application startup, learning, reviewing, or accessing existing data.

#### Zero-Configuration Installation

* End-users must not need to install Java, JDK, Maven, Node.js, or an external database server.
* The application must include its own Java runtime and required JavaFX modules.
* SQLite must initialize automatically on first launch.
* The application must be distributed through native installers.

#### Native Desktop Architecture

* JavaFX is the only UI framework.
* Java 25 handles application logic, persistence, scheduling, and language processing.
* React, Angular, Vue, Electron, WebView, browser-based interfaces, and local REST APIs are not part of the architecture.
* All application modules must communicate through Java interfaces and method calls.

#### User Data Ownership

* Users have full control over their decks, cards, review history, and learning statistics.
* Data must be exportable and restorable without external services.
* Database migrations must preserve existing user data.

#### Maintainability

* Prefer simple, modular, readable implementations.
* Reuse existing components and services before introducing new abstractions.
* Avoid unnecessary dependencies and duplicated logic.

## 2. Technology Stack

### 2.1. Core Technologies

| Layer | Technology | Responsibility |
| ----- | ----- | ----- |
| Runtime | Java 25 LTS | Application runtime |
| UI Framework | JavaFX 25 | Native desktop interface |
| UI Definition | FXML + Java | View composition and controllers |
| UI Styling | JavaFX CSS | Dark and Light themes |
| Dependency Injection | Constructor Injection | Explicit module dependencies |
| Database | SQLite | Local persistence |
| Database Driver | sqlite-jdbc | SQLite connectivity |
| Migration | Flyway | Schema versioning |
| Build | Maven 3.9+ | Dependency and build management |
| SRS | FSRS-6 | Review scheduling |
| Japanese NLP | Kuromoji | Morphological analysis |
| JSON | Jackson | Import and export |
| CSV | Apache Commons CSV | CSV processing |
| Logging | SLF4J + Logback | Local application logging |
| Packaging | jlink + jpackage | Native distribution |
| Testing | JUnit 5 | Automated testing |
| UI Testing | TestFX or JavaFX integration tests | Desktop interaction testing |

Use compatible, explicitly pinned dependency versions. Do not automatically upgrade major versions during implementation.

JavaFX must be obtained through its official Maven artifacts or an equivalent reproducible dependency configuration.

### 2.2. Removed Technologies

The following technologies are explicitly excluded from the architecture:

* React.
* TypeScript.
* Tailwind CSS.
* react-i18next.
* Spring Boot.
* Spring Data JPA.
* Spring REST Controllers.
* Embedded HTTP servers.
* WebView.
* Frontend Maven plugins.
* Node.js runtime dependencies.

Do not introduce these technologies unless a formally approved architecture revision changes this specification.

### 2.3. JavaFX Application Structure

Use JavaFX with:

* `Application` for application lifecycle.
* `Stage` for the main window.
* `Scene` for UI composition.
* FXML for complex views where it improves readability.
* Java controllers for UI interaction.
* JavaFX CSS for consistent styling.
* JavaFX properties and observable collections for UI state.

FXML is recommended for complex screens, but simple components may be constructed directly in Java.

Business logic must never be implemented inside FXML controllers.

## 3. System Architecture

### 3.1. Architectural Style

Use a modular monolith with clear separation of concerns.

The application consists of five logical layers:

1. Presentation Layer.
2. Application Layer.
3. Domain Layer.
4. Infrastructure Layer.
5. Persistence Layer.

Dependency direction must point inward. Domain logic must not depend on JavaFX, SQLite, or file-system implementation details.

### 3.2. Architecture Diagram

```text
+--------------------------------------+
|          JavaFX Presentation         |
|                                      |
| Dashboard | Decks | Study | Settings |
| Reading   | Import / Export          |
+------------------+-------------------+
                   |
                   v
+--------------------------------------+
|          Application Layer           |
|                                      |
| Deck Management                      |
| Card Management                      |
| Study Session Management             |
| Review Management                    |
| Import / Export                      |
| Learning Analytics                   |
+------------------+-------------------+
                   |
                   v
+--------------------------------------+
|             Domain Layer             |
|                                      |
| Deck | Card | Review State           |
| FSRS Scheduler | Learning Rules      |
| Language Models | Validation         |
+------------------+-------------------+
                   |
                   v
+--------------------------------------+
|          Infrastructure Layer        |
|                                      |
| Kuromoji | TTS Providers             |
| File Processing | Backup             |
| Logging | Application Configuration  |
+------------------+-------------------+
                   |
                   v
+--------------------------------------+
|           Persistence Layer          |
|                                      |
| Repositories | SQLite | Flyway       |
+--------------------------------------+
```

### 3.3. Module Responsibilities

#### Presentation Layer

Responsible for:

* Rendering the application interface.
* Handling keyboard and mouse interactions.
* Displaying validation messages.
* Managing temporary view state.
* Binding JavaFX properties to view components.

Must not directly execute SQL queries or calculate FSRS scheduling.

#### Application Layer

Responsible for:

* Coordinating user operations.
* Executing application workflows.
* Managing transactions.
* Calling domain services.
* Mapping domain results to presentation models.

#### Domain Layer

Responsible for:

* Core learning entities.
* Learning rules.
* FSRS scheduling.
* Review state transitions.
* Card validation.
* Deck configuration rules.

Must remain independent of JavaFX and database implementation.

#### Infrastructure Layer

Responsible for:

* Japanese NLP.
* Text-to-Speech providers.
* File import and export.
* Database backup utilities.
* Logging.
* OS-specific integration.

#### Persistence Layer

Responsible for:

* Repository implementations.
* SQL queries.
* SQLite transactions.
* Database connections.
* Flyway migrations.

## 4. Repository Structure

Recommended structure:

```text
SenkoJE/
|
+-- .github/
|   +-- workflows/
|       +-- ci.yml
|       +-- release.yml
|
+-- docs/
|   +-- ARCHITECTURE.md
|   +-- DATABASE_SCHEMA.md
|   +-- FEATURES_AND_UI.md
|   +-- FSRS_SPECIFICATION.md
|   +-- TESTING_AND_RELEASE.md
|
+-- sample-decks/
|   +-- ja_n5_essential_100.json
|   +-- en_b1_collocations.json
|
+-- src/
|   +-- main/
|   |   +-- java/
|   |   |   +-- com/senkoje/
|   |   |       +-- app/
|   |   |       +-- presentation/
|   |   |       |   +-- controller/
|   |   |       |   +-- view/
|   |   |       |   +-- component/
|   |   |       |   +-- viewmodel/
|   |   |       |
|   |   |       +-- application/
|   |   |       |   +-- service/
|   |   |       |   +-- dto/
|   |   |       |   +-- mapper/
|   |   |       |
|   |   |       +-- domain/
|   |   |       |   +-- model/
|   |   |       |   +-- service/
|   |   |       |   +-- fsrs/
|   |   |       |   +-- repository/
|   |   |       |
|   |   |       +-- infrastructure/
|   |   |       |   +-- nlp/
|   |   |       |   +-- tts/
|   |   |       |   +-- importer/
|   |   |       |   +-- exporter/
|   |   |       |   +-- backup/
|   |   |       |   +-- config/
|   |   |       |
|   |   |       +-- persistence/
|   |   |           +-- sqlite/
|   |   |           +-- repository/
|   |   |           +-- migration/
|   |   |
|   |   +-- resources/
|   |       +-- fxml/
|   |       +-- css/
|   |       +-- i18n/
|   |       +-- images/
|   |       +-- fonts/
|   |       +-- db/migration/
|   |
|   +-- test/
|       +-- java/
|       |   +-- com/senkoje/
|       |       +-- domain/
|       |       +-- application/
|       |       +-- persistence/
|       |       +-- integration/
|       |       +-- ui/
|       |
|       +-- resources/
|           +-- fsrs-test-vectors/
|
+-- CODING_RULES.md
+-- CONTRIBUTING.md
+-- LICENSE
+-- README.md
+-- pom.xml
```

## 5. Final Architectural Decisions

| Decision | Selected Implementation |
| ----- | ----- |
| Application type | Native desktop |
| Runtime | Java 25 LTS |
| UI | JavaFX 25 |
| UI architecture | FXML + Java controllers |
| Architecture style | Modular monolith |
| Business logic | Pure Java services |
| Database | SQLite |
| Migrations | Flyway |
| SRS | FSRS-6 |
| Japanese NLP | Kuromoji |
| TTS | Provider-based offline-capable abstraction |
| Import / Export | CSV / JSON / optional APKG |
| Build | Maven |
| Packaging | jlink + jpackage |
| CI/CD | GitHub Actions |
| License | Custom Non-Commercial |
| Network | Not required for core features |
