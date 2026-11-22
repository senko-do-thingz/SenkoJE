# Testing and Release Engineering - SenkoJE

## 1. Testing Strategy

### 1.1. Unit Tests

Test:

* Domain models.
* Deck validation.
* Card validation.
* FSRS scheduling.
* Rating mappings.
* Review state transitions.
* Import validation.
* Export serialization.
* Statistics calculations.

### 1.2. FSRS Tests

Mandatory:

* New card scheduling.
* Again rating.
* Hard rating.
* Good rating.
* Easy rating.
* Learning transitions.
* Relearning transitions.
* Review after overdue intervals.
* Desired retention changes.
* Maximum interval handling.
* Deterministic fuzz behavior where applicable.

Compare the implementation against verified FSRS reference test vectors.

### 1.3. Database Tests

Test:

* Database initialization.
* Flyway migrations.
* Foreign key enforcement.
* CRUD operations.
* Transaction rollback.
* Cascade behavior.
* Database integrity.
* Backup and restore.
* Upgrade from previous schema versions.

### 1.4. Integration Tests

Test complete workflows:

1. Create a deck.
2. Create cards.
3. Start a study session.
4. Review cards.
5. Persist scheduling states.
6. Close the application.
7. Restart the application.
8. Verify due dates and review history.

### 1.5. UI Tests

Test:

* Application startup.
* Main navigation.
* Theme switching.
* Language switching.
* Deck creation.
* Card editing.
* Flashcard interactions.
* Keyboard shortcuts.
* Error and empty states.

### 1.6. Offline Tests

Disconnect the network and verify:

* Application startup.
* Database access.
* Deck management.
* Flashcard review.
* SRS calculations.
* Japanese tokenization.
* Local import/export.
* Backup and restore.

## 2. Packaging and Distribution

### 2.1. Native Runtime

Use Java 25 and JavaFX to generate a trimmed runtime with `jlink`.

Include only required Java and JavaFX modules.

Do not require users to install a separate JDK or JavaFX SDK.

### 2.2. Native Installers

Supported targets:

| Platform | Package |
| ----- | ----- |
| Windows | EXE / MSI |
| macOS | DMG / PKG |
| Linux | DEB / RPM |

AppImage may be provided through a separate packaging workflow.

### 2.3. Release Requirements

Each release must include:

* Version number.
* Release notes.
* Supported operating systems.
* Native installer.
* SHA-256 checksum.
* License.
* Installation instructions.

### 2.4. Upgrade Safety

Application updates must not overwrite the user's database.

Before schema upgrades:

1. Detect the existing database.
2. Create a safety backup.
3. Run migrations.
4. Verify database integrity.
5. Continue startup only after successful migration.

If migration fails, preserve the original database and provide recovery instructions.

## 3. Licensing and Intellectual Property

### 3.1. License Selection

SenkoJE uses a custom Non-Commercial License.

It is not MIT and not GPL-3.0.

The project is source-available but should not be described as OSI-approved Open Source Software.

### 3.2. Intended Permissions

The license should define permissions for:

* Personal use.
* Educational use.
* Non-commercial research.
* Non-commercial modification.
* Non-commercial redistribution.
* Attribution to the original author (`senko-do-thingz`).

### 3.3. Commercial Restrictions

Commercial use must be prohibited unless separately authorized by the copyright holder.

The final license must define commercial use clearly, including:

* Sale of the application.
* Paid distribution.
* Commercial hosting.
* Commercial redistribution.
* Incorporation into paid products or services.

### 3.4. Third-Party Dependencies

Every third-party dependency and bundled dataset must be checked individually.

Maintain a dependency license inventory and include required copyright notices and attribution.

## 4. Privacy and Local Security

* No mandatory account system.
* No telemetry.
* No analytics tracking.
* No remote API calls.
* No automatic uploads.
* No external database connections.
* No unnecessary network listeners.
* No collection of personal information.

Database access must be restricted to the local user account.

Use prepared SQL statements.

Validate all imported content.

Treat imported archives and JSON files as untrusted input.

Avoid unsafe archive extraction paths and unbounded resource consumption.

## 5. Development Roadmap and Definition of Done

### 5.1. Development Roadmap

* **Phase 0 - Architecture Foundation:** Java 25 Maven project, JavaFX 25 window prototype, SQLite + Flyway setup, `jlink` verification.
* **Phase 1 - Database and Domain Foundation:** Core schema migrations, repositories, Deck and Card models, services, validation.
* **Phase 2 - FSRS Engine:** FSRS-6 implementation, review states, immutable review logs, reference test vectors.
* **Phase 3 - Main Desktop UI:** Dashboard, Deck Library, Card Browser, Card Editor, Settings, Dark/Light themes (Dark default), Vietnamese/English i18n.
* **Phase 4 - Study Experience:** Flashcard Mode, keyboard shortcuts, study sessions, TTS provider abstraction, analytics.
* **Phase 5 - Japanese NLP and Reading:** Kuromoji integration, Furigana rendering, token inspection, add-to-deck workflow.
* **Phase 6 - Data Management:** CSV/JSON importers and exporters, SQLite online backup/restore, optional `.apkg` importer.
* **Phase 7 - Release Engineering:** `jlink` + `jpackage` cross-platform installers, SHA-256 checksums, fresh install and upgrade verification.

### 5.2. Definition of Done

A feature is considered complete only when:

1. Its business logic is implemented.
2. Its UI is functional where applicable.
3. Input validation is implemented.
4. Errors are handled.
5. Data persistence is verified where applicable.
6. Automated tests pass.
7. Localization is implemented.
8. Dark and Light themes are supported.
9. Offline operation is verified where applicable.
10. Documentation is updated.

### 5.3. Non-Goals

Explicitly outside the scope of SenkoJE:

* Cloud synchronization, multi-user collaboration, user authentication, social features.
* Payment processing, subscription services, advertisements.
* Online AI tutoring, mandatory online dictionaries, mandatory online TTS.
* Web application deployment, mobile application development, browser-based interfaces.

### 5.4. Final Architectural Decisions

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
