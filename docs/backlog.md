# Backlog — Puzzlewerk

> Sammelstelle für Ideen und Verbesserungen außerhalb des aktuellen
> Auftrags. Jeder Agent darf hier ergänzen; der Orchestrator priorisiert.

## Technik
- [ ] gitleaks als CI-Schritt ergänzen (Regel S7); bis dahin prüft der
      security-auditor manuell je PR
- [ ] OWASP Dependency-Check oder `gradle-versions-plugin` + CVE-Scan
      in CI aufnehmen (Regel S6) — braucht ADR
- [ ] Renovate/Dependabot für Dependency-Updates konfigurieren
- [ ] Dependency Verification auf PGP-Signaturen erweitern
      (`verify-signatures=true` + trusted-keys) — Zeitpunkt und
      Trigger-Kriterien in ADR-002 entschieden (bewusst verschoben;
      spätestens vor dem ersten an Endnutzer verteilten Release-Build)
- [ ] Merkregel aus PW-0.3: verification-metadata.xml IMMER mit
      `--refresh-dependencies --write-verification-metadata sha256 <Gate-Tasks>`
      regenerieren — mit warmem lokalem Cache fehlen sonst
      .pom-/.module-Varianten, die nur ein kalter CI-Cache auflöst.
      Kandidat für docs/architektur.md (Verbindliche Kommandos)
- [ ] Release-Engineer: Umsetzungsticket PW-0.4-impl aus ADR-002 —
      `gradlePluginPortal()` in settings.gradle.kts per Content-Filter
      auf `org.jlleitschuh.gradle(.ktlint)` beschränken; exakter
      Code-Block steht im ADR. Bei jedem Ktlint-Gradle-Update prüfen,
      ob das Plugin inzwischen auf Maven Central liegt
      (Rückbau-Trigger: Portal dann ganz entfernen, neues ADR)
- [ ] targetSdk 36 wurde in PW-0.1 gesetzt, weil AGP 8.13.2 sonst den
      Lint-Error OldTargetApi wirft und C7 Baselines verbietet.
      Konsequenzen für Phase 3 einplanen: Edge-to-Edge ist ab
      targetSdk 35+ erzwungen (Insets sauber behandeln), Predictive
      Back muss unterstützt und getestet werden
- [ ] Jährlicher targetSdk-Bump als fester Prozess (PW-0.5): Lint-Check
      OldTargetApi ist in app/build.gradle.kts deaktiviert, weil er
      umgebungsabhängig ist (Lint liest die "neueste" API aus den im
      Runner-SDK installierten Platforms; ubuntu-latest rot, lokal grün
      bei identischem Commit — CI-Run 29031584439). Ersatzmechanismus:
      compileSdk/targetSdk gemeinsam mit AGP-Update pro Android-Release
      per ADR anheben (Trigger: Play-Policy-Deadline, üblicherweise
      31. August); ExpiredTargetSdkVersion bleibt als harter Gate aktiv
- [x] Architekt: `gradlePluginPortal()` in settings.gradle.kts
      (pluginManagement) kollidiert mit dem S6-Wortlaut („nur google() +
      mavenCentral()") — entschieden in ADR-002 (PW-0.4): Portal bleibt,
      per Content-Filter auf Ktlint-Gradle beschränkt, S6 präzisiert;
      Umsetzung siehe Ticket PW-0.4-impl oben (Security-Befund L2
      geschlossen)
- [ ] Konsist- oder ArchUnit-Tests, die die Schichtenregel maschinell
      erzwingen (statt nur per Review)
- [ ] Robolectric + Compose-UI-Test-Setup für :app (ab Phase 3 nötig)
- [x] gradlew hat mit PW-0.1 das Executable-Bit verloren (Windows-Checkout,
      Mode 100755 → 100644). Vor dem Linux-CI-Lauf (PW-0.2) per
      `git update-index --chmod=+x gradlew` wiederherstellen, sonst
      schlägt `./gradlew` im CI fehl — erledigt in PW-0.2
- [x] Architekt (aus PW-1.1): SplitMix64 als normative
      `RandomSource`-Implementierung in :core per ADR bestätigen —
      docs/game-design.md §8 legt den Algorithmus fest, damit
      „gleicher Seed ⇒ identisches Level" geräteübergreifend gilt —
      entschieden in ADR-003 (PW-2.1): eigene ~20-Zeilen-Implementierung
      `SplitMix64Random` + `mix64` in :core, `SeededRandom` entfernt
- [ ] Architekt/Orchestrator (aus PW-2.1): Widerspruch zwischen
      docs/architektur.md („:game und :data kennen nur :core") und
      data/build.gradle.kts (`implementation(project(":game"))`, seit
      Phase 0). Spätestens vor der Phase-3-Persistenz per ADR klären —
      die Level-/Spielstand-Serialisierung in :data braucht die
      :game-Typen (LevelDefinition, LevelValidator), vermutlich ist
      also das Schaubild zu korrigieren (app → data → game → core),
      nicht der Build
- [ ] Architekt (aus PW-2.2): KDoc von trace/Tracer.kt nennt als
      Implementierungs-Ticket „PW-2.3", implementiert wurde der Tracer
      in PW-2.2 (DefaultTracer). Veraltete Referenz beim nächsten
      API-Touch korrigieren (KDoc-Änderung war in PW-2.2 out of scope)
- [ ] Architekt (aus PW-2.3): gleiche veraltete Ticket-Referenz auch in
      engine/GameEngine.kt und score/Score.kt — beide KDocs nennen
      „Implementierung: Ticket PW-2.4", implementiert wurden Engine und
      ScoreCalculator in PW-2.3 (DefaultGameEngine, DefaultScoreCalculator).
      Beim nächsten API-Touch zusammen mit dem Tracer-Eintrag korrigieren
      (KDoc-Änderung war in PW-2.3 out of scope)
- [ ] Architekt/Entwickler (aus PW-2.3, NIT-3-Restidee): GameState könnte
      das effektive Brett je Zustand strukturell teilen (nur die gedrehte
      Zelle ersetzen statt mapValues über alle Elemente in currentBoard).
      Aktuell unkritisch (max. 91 Zellen, eine Auswertung je Zug in der
      Engine); nur relevant, falls der Par-Solver (PW-Generator-Ticket)
      currentBoard heiß nutzt — dann eigenes Ticket
- [x] Entwickler (Befund aus PW-2.2-QS, Prio hoch): In
      game/src/test/.../DefaultTracerPropertyTest.kt werden die beiden
      Tests „I1 und I8 …" und „Trace ist referenziell transparent …"
      von JUnit Jupiter STILL NICHT AUSGEFÜHRT: `fun x() = runBlocking {
      checkAll(...) }` hat Rückgabetyp PropertyContext, und Jupiter
      schließt @Test-Methoden mit Rückgabewert von der Discovery aus
      (Test-Report zeigt nur 4 von 6 Methoden). Fix: Block-Body
      verwenden (`fun x() { runBlocking { … } }`). Inhaltlich sind die
      Invarianten seit PW-2.2-QS durch TracerIndependentPropertyTest
      abgedeckt; Fix war hier out of scope (fremde Datei). Idee für die
      CI: Detekt-Regel oder Review-Checkliste „@Test-Methoden geben
      Unit zurück", damit so etwas nicht wieder still verschwindet —
      BEHOBEN im PW-2.2-Korrekturzyklus: beide Methoden auf Block-Body
      umgestellt, Test-Report zeigt 6/6 testcases; alle Testquellen in
      :game/:core/:data/:app projektweit auf das Muster geprüft (kein
      weiterer Treffer, einziges verbliebenes `fun x() =` ist ein
      privater Helfer ohne @Test)
- [ ] Entwickler (BUG PW-2.3-QS-B1, Prio niedrig — theoretischer
      Grenzwert): DefaultScoreCalculator verletzt Invariante I5 bei
      extremer Zugzahl: `50 * extraMoves` ueberlaeuft Int, dadurch
      liefert `scoreFor(Int.MAX_VALUE, 14)` 2250 Punkte statt hoechstens
      1500 (der negative Ueberlauf hebelt beide coerceAtLeast(0) aus;
      Ueberlaufbereich beginnt ab moves ≥ par + 42 949 673). Verstoss
      gegen KDoc-Vertrag (Vorbedingung nur `moves >= 0`) und I5/
      Monotonie. Fix: Bonus in Long rechnen oder extraMoves auf 10
      kappen. Regressionstest liegt @Disabled bereit:
      ScoreValueTableTest.`I5-Regression - Score bleibt auch bei
      extremer Zugzahl in 1000 bis 1500` — beim Fix Annotation entfernen
- [ ] Test-Engineer/Architekt (aus PW-2.2-Korrektur): dauerhafte
      Absicherung gegen still ignorierte Tests evaluieren — Detekt
      bringt keine passende Regel mit; Kandidaten: (a) eigene
      Detekt-Regel „@Test-Methode muss Unit zurückgeben" als
      custom-rules-Modul (braucht ADR, da neues Build-Artefakt),
      (b) JUnit `-Djunit.jupiter.testmethod...`-Discovery-Warnungen
      gibt es nicht — stattdessen im CI die testcase-Anzahl je Klasse
      gegen die @Test-Anzahl im Quelltext diffen, oder (c) schlicht
      Review-Checkliste. Empfehlung: (a) bei nächster Gelegenheit

## Produkt
- (leer — Ideen des game-designers landen hier)

## Game-Design-Ideen
(aus PW-1.1 — bewusst NICHT in Version 1, siehe docs/game-design.md §17)
- [ ] Bewegliche/verschiebbare Elemente als zweiter Zugtyp
      (Kampagne 2 / Level-Pack; braucht Solver- und Generator-Ausbau)
- [ ] Drehbare Lichtquellen als zusätzliches Zugziel (erweitert den
      Zugraum; Par-Solver-Kosten prüfen)
- [ ] Sekundärfarben-Filter (Gelb/Magenta/Cyan) und/oder
      Kombinierer-Element (mischt Strahlen im Feld statt nur am
      Kristall) — Farblogik-Erweiterung
- [ ] Daily-Archiv: vergangene Tagesrätsel nachspielen (ohne
      Serienwertung, rein zum Spaß — kein Nachhol-Druck)
- [ ] Teilen-Feature fürs Tägliche Prisma (Wordle-Stil: Emoji-Raster
      aus Zügen/Par, via Android-Share-Intent, keine Permission nötig)
- [ ] Sanftes Hint-System (z. B. „markiere ein falsch orientiertes
      Element"), strikt ohne Monetarisierung
- [ ] Hybrid-Idee aus der Entscheidungsvorlage: Prisma mit der
      Atmosphäre von Konzept A (Nacht, Glühen, ruhige Musik) —
      Art-Direction-Entscheidung für Phase 3/4
- [ ] Level-Editor + Level-Teilen als Code/QR (offline-tauglich)
- [ ] Statistik-Ausbau: Verteilungsgrafik „Züge über Par" je Wochentag
- [ ] Monetarisierungsmodell nach Release-Erfahrung entscheiden:
      Kampagne teilweise gratis + Einmalkauf, Daily für immer frei
      (Vorlage Konzept C); niemals Hints oder Serien verkaufen
