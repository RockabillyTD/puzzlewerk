# ADR-002: Dependency-Quellen (gradlePluginPortal vs. Regel S6) und PGP-Roadmap

- Status: AKZEPTIERT
- Datum: 2026-07-09
- Autor: architect (Ticket PW-0.4; Anlass: Security-Befund L2 aus PW-0.2)

## Kontext

Regel S6 (docs/architektur.md) forderte bisher wörtlich „nur `google()` +
`mavenCentral()`". `settings.gradle.kts` nutzt in `pluginManagement`
jedoch zusätzlich `gradlePluginPortal()`. Für Projekt-Dependencies
(`dependencyResolutionManagement`) gilt strikt google + mavenCentral mit
`FAIL_ON_PROJECT_REPOS`; der Widerspruch betrifft ausschließlich den
Plugin-Classpath. Abmilderung im Bestand: Gradle Dependency Verification
(SHA-256, `verify-metadata=true`) deckt auch den Plugin-Classpath ab
(gradle/verification-metadata.xml, ~544 Komponenten).

Auflösbarkeits-Prüfung der fünf verwendeten Plugins (HTTP-Abfrage der
publizierten Marker- und Implementierungs-POMs, Stand 2026-07-09):

| Plugin (Version) | google() | mavenCentral() | Plugin Portal |
|---|---|---|---|
| AGP `com.android.application`/`library` (8.13.2) | ja | — | ja |
| Kotlin `jvm`/`android`/`plugin.compose` (2.0.21) | — | ja | ja |
| Detekt `io.gitlab.arturbosch.detekt` (1.23.7) | — | ja | ja |
| Kover `org.jetbrains.kotlinx.kover` (0.8.3) | — | ja | ja |
| Ktlint-Gradle `org.jlleitschuh.gradle.ktlint` (12.1.2) | — | **nein (404)** | ja |

Einzig Ktlint-Gradle (Marker-Gruppe `org.jlleitschuh.gradle.ktlint`,
Implementierung `org.jlleitschuh.gradle:ktlint-gradle`) ist nicht über
die beiden Hauptquellen verfügbar. Da `mavenCentral()` in
`pluginManagement` vor `gradlePluginPortal()` steht, werden Kotlin,
Detekt und Kover bereits heute von Maven Central aufgelöst und AGP per
Content-Filter von google(); faktisch bedient das Portal nur
Ktlint-Gradle.

## Optionen

1. **Portal entfernen, Ktlint-Gradle ersetzen.** Entweder (1a) Ktlint
   über die Detekt-Formatting-Rules laufen lassen oder (1b)
   `com.pinterest.ktlint:ktlint-cli` (Maven Central) über eine eigene
   `JavaExec`-Task einbinden.
   - (1a) koppelt Formatierung an Detekt-Releases und liefert nicht die
     `ktlintFormat`-Autokorrektur; Detekt selbst rät für reine
     Formatierung zum eigenständigen Ktlint.
   - (1b) bedeutet Eigenbau von Gradle-Integration (per-Modul-Tasks,
     Inkrementalität, Format-Task, Report-Anbindung an Quality Gates)
     — deutlich mehr als „50 Zeilen eigener Code" und dauerhaft selbst
     zu pflegen, gegen ein aktiv gepflegtes Plugin.
2. **Portal unverändert behalten, nur S6-Wortlaut präzisieren.** Portal
   bleibt für beliebige Plugin-Gruppen offen; Absicherung allein durch
   Dependency Verification.
3. **Portal behalten, aber per Content-Filter auf exakt die
   Ktlint-Gradle-Gruppen beschränken, und S6 entsprechend präzisieren.**
   Das Portal kann dann technisch nur noch
   `org.jlleitschuh.gradle`/`org.jlleitschuh.gradle.ktlint` liefern;
   jedes künftige Plugin aus dem Portal erzwingt eine bewusste
   Filter-Erweiterung (= ADR-Pflicht nach C8) statt stiller Aufnahme.

## Entscheidung

**Option 3.** `gradlePluginPortal()` bleibt in `pluginManagement`,
wird aber per `content { includeGroupByRegex(...) }` auf die beiden
Ktlint-Gradle-Gruppen eingeschränkt. Regel S6 wird präzisiert (siehe
docs/architektur.md): google() + mavenCentral() für alle Dependencies
und Plugins; das Plugin Portal ausschließlich in `pluginManagement`,
ausschließlich für dort per Content-Filter freigegebene Plugins ohne
Publikation auf den Hauptquellen, abgesichert durch Dependency
Verification.

Begründung der Abwägung: Die eigentliche Schutzwirkung gegen
Lieferketten-Angriffe liefert das SHA-256-Pinning — ein manipuliertes
Artefakt schlägt unabhängig von der Quelle fehl. Das Restrisiko der
dritten Quelle reduziert der Content-Filter auf genau ein Plugin eines
aktiv gepflegten Projekts. Option 1 würde dieses Restrisiko gegen ein
größeres tauschen: selbstgebaute, ungetestete Build-Integration eines
Quality Gates.

**Rückbau-Trigger:** Sobald Ktlint-Gradle auf Maven Central publiziert
wird oder das Projekt das Plugin ersetzt, wird `gradlePluginPortal()`
komplett entfernt (neues ADR, das dieses referenziert). Der
Release-Engineer prüft das bei jedem Ktlint-Gradle-Versionsupdate.

### Umsetzungsticket (Release-Engineer, NICHT Teil dieses ADR-PRs)

> **PW-0.4-impl — Content-Filter für gradlePluginPortal**
> Datei: `settings.gradle.kts`, Block `pluginManagement.repositories`.
> `gradlePluginPortal()` ersetzen durch:
>
> ```kotlin
> gradlePluginPortal {
>     content {
>         // ADR-002: Portal nur für Ktlint-Gradle (Marker + Implementierung),
>         // nicht auf Maven Central publiziert (Stand 12.1.2).
>         includeGroupByRegex("org\\.jlleitschuh\\.gradle(\\.ktlint)?")
>     }
> }
> ```
>
> Reihenfolge (google → mavenCentral → gradlePluginPortal) beibehalten.
> `gradle/verification-metadata.xml` bleibt unverändert (Checksummen sind
> quellenunabhängig). Verifikation: `./gradlew check` und
> `./gradlew :app:lintDebug :app:assembleDebug` grün; zusätzlich einmal
> mit `--refresh-dependencies` bestätigen, dass alle Plugin-Marker
> auflösen.

## PGP-Signatur-Verifikation: Roadmap

### Kontext

`verify-signatures` steht auf `false`; gepinnt werden nur
SHA-256-Checksummen. Das garantiert Bit-Identität mit dem
Erst-Download, aber nicht dessen Authentizität (Trust-on-first-use:
wäre der Erst-Download bereits kompromittiert gewesen, pinnten die
Hashes das kompromittierte Artefakt). PGP-Verifikation mit
`trusted-keys` schließt diese Lücke, kostet aber: Key-Kuratierung für
~544 Komponenten, laufende Pflege bei Key-Rotationen der Publisher,
Umgang mit unsigniert publizierten Artefakten (Ausnahmeliste), und
zusätzliche CI-Fehlklassen bei jedem Dependency-Update.

### Entscheidung

**Bewusst später, nicht in Phase 0/1.** Begründung: Alle Quellen sind
große, TLS-gesicherte Repositories (Google Maven, Maven Central,
Plugin Portal — Letzteres nach diesem ADR auf eine Gruppe gefiltert);
das TOFU-Restrisiko ist klein gegenüber dem sofortigen Pflegeaufwand.
Solange die App weder Netzwerkzugriff noch Nutzerdaten hat, ist der
Schaden eines (unwahrscheinlichen) kompromittierten Erst-Downloads
zudem begrenzt. Der Sicherheitsgewinn wird relevant, sobald echte
Nutzer echte Builds installieren.

**Trigger (was zuerst eintritt, aktiviert die Umsetzung):**

1. Vorbereitung des ersten an Endnutzer verteilten Release-Builds
   (Play-Store-/APK-Distribution) — spätestens dann
   `verify-signatures=true` + `trusted-keys`, mit dokumentierten
   Ausnahmen für unsigniert publizierte Artefakte.
2. Aufnahme einer Dependency, die zur Laufzeit Daten verarbeitet, die
   die App-Sandbox verlassen (heute per S1/S3 ausgeschlossen).
3. Ein öffentlich bekannter Kompromittierungs-Vorfall bei einer der
   genutzten Quellen oder einem genutzten Publisher.

**Interims-Absicherung bis dahin:** Änderungen an
`gradle/verification-metadata.xml` erfordern Review durch den
security-auditor (neue Komponenten stichprobenartig gegen die
Publisher-Checksummen der Quelle gegenprüfen); Dependency-Updates
laufen nur über Version-Catalog-PRs mit ADR-Referenz (C8/S6).

## Konsequenzen

- (+) S6 ist widerspruchsfrei zu `settings.gradle.kts`; Befund L2
  geschlossen.
- (+) Plugin Portal technisch auf eine Gruppe begrenzt; jede
  Erweiterung ist eine sichtbare, ADR-pflichtige Code-Änderung.
- (+) Kein Build-Umbau, kein Eigenbau von Ktlint-Integration; Quality
  Gates bleiben unverändert.
- (−) Es bleibt eine dritte Artefakt-Quelle bestehen (gemildert durch
  Checksummen-Pinning + Content-Filter, Rückbau-Trigger definiert).
- (−) TOFU-Restrisiko bleibt bis zur PGP-Aktivierung bestehen
  (bewusst akzeptiert, Trigger definiert).
- Folgearbeit: Umsetzungsticket PW-0.4-impl (Release-Engineer, siehe
  oben); Backlog-Eintrag zur PGP-Erweiterung verweist auf dieses ADR.
