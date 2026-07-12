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
- [x] Merkregel aus PW-0.3: verification-metadata.xml IMMER mit
      `--refresh-dependencies --write-verification-metadata sha256 <Gate-Tasks>`
      regenerieren — mit warmem lokalem Cache fehlen sonst
      .pom-/.module-Varianten, die nur ein kalter CI-Cache auflöst —
      in PW-3.1 als drittes „Verbindliches Kommando" in
      docs/architektur.md aufgenommen
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
      31. August); ExpiredTargetSdkVersion bleibt als harter Gate aktiv.
      Seit ADR-009 zusätzlich: Robolectric-Version und @Config-SDK-Pin
      beim Bump mitprüfen
- [x] Architekt: `gradlePluginPortal()` in settings.gradle.kts
      (pluginManagement) kollidiert mit dem S6-Wortlaut („nur google() +
      mavenCentral()") — entschieden in ADR-002 (PW-0.4): Portal bleibt,
      per Content-Filter auf Ktlint-Gradle beschränkt, S6 präzisiert;
      Umsetzung siehe Ticket PW-0.4-impl oben (Security-Befund L2
      geschlossen)
- [x] Entwickler/Release-Engineer: Umsetzungsticket PW-2.6-impl aus
      ADR-004 — Gradle-Task `checkModuleGraph` im Root-Build prüft alle
      Konfigurationen jedes Subprojekts gegen die Modul-Whitelist
      (app → data → game → core) und verbietet Android-Plugins sowie
      androidx.*-/com.android*-Koordinaten auf
      :game/:core; Task hängt am Root-`check` und wandert in die
      „Verbindlichen Kommandos" + CI. Vollständige Spezifikation und
      Abnahmekriterien in ADR-004. Werkzeugentscheidung ist gefallen
      (C8): schlanke Eigenlösung ohne Dependency statt Konsist/ArchUnit
      — ersetzt den früheren Punkt „Konsist- oder ArchUnit-Tests" —
      UMGESETZT in PW-2.6-impl: Task im Root-build.gradle.kts, hängt am
      Root-`check` (base-Plugin als Anker), eigener CI-Schritt (nötig,
      weil die CI Einzeltasks statt `check` aufruft — braucht noch
      Security-APPROVE); Negativproben 2a/2b nach ADR durchgeführt.
      Selbstkanten (:x → :x aus AGP-Testvarianten/Kover) sind bewusst
      erlaubt, da sie das Schichtenmodell nicht verletzen können
- [x] Architekt (Security-Finding L3 aus dem PW-2.6-impl-Review, PR #9,
      LOW): Regel-5-Sperrliste (androidx.*/com.android*) ließ das
      android.jar-Stub-JAR `com.google.android:android` passieren —
      Android-Imports in :game wären kompilierbar gewesen, checkModuleGraph
      wäre grün geblieben. GESCHLOSSEN in ADR-005 (PW-2.8): Sperr-Präfix
      `com.google.` (gesamter Google-Namensraum) ergänzt, Negativprobe mit
      com.google.android:android:4.1.1.4 durchgeführt
- [x] Robolectric + Compose-UI-Test-Setup für :app (ab Phase 3 nötig) —
      ENTSCHIEDEN in ADR-009 (PW-3.1): Robolectric + Compose-
      ui-test-junit4 + Turbine/coroutines-test, Dependencies im Katalog
      und in der verification-metadata; Aktivierung inkl. android-all-
      Offline-Pinnung (S6-Auflage!) und Kover-70%-Gates :app/:data in
      den Tickets PW-3.3/PW-3.2 (docs/phase3-tickets.md)
- [ ] Ticket PW-3.3 (aus ADR-009, S6-Auflage): Robolectric
      android-all-instrumented als Gradle-Konfiguration pinnen und
      `robolectric.offline=true` setzen — kein Test-Task lädt zur
      Laufzeit ungeprüfte Artefakte nach
- [ ] Game-Designer (aus PW-3.1, blockiert Teile von PW-3.6): §11.3
      nennt für die Levelbereiche 4–8, 27–31 und 37–41 Tier-SPANNEN
      („D1–D2", „D4–D5", „D5–D6") — die exakte Level→Tier-Zuordnung
      je Levelnummer fehlt. Ohne sie gibt es keine campaignTier(n)-
      Funktion in :game. Design-Präzisierung nötig (kein BREAKING,
      solange nur die offenen Bereiche fixiert werden)
- [ ] Architekt (Phase 4): LevelRepository-API für eingecheckte,
      kuratierte Level-Assets (§11.1) definieren — Formatregeln sind
      mit ADR-007 bereits fixiert (Envelope+Version, Entry-Arrays,
      Duplikat-Check, LevelValidator an der Vertrauensgrenze)
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
- [x] Architekt/Orchestrator (aus PW-2.1): Widerspruch zwischen
      docs/architektur.md („:game und :data kennen nur :core") und
      data/build.gradle.kts (`implementation(project(":game"))`, seit
      Phase 0). Spätestens vor der Phase-3-Persistenz per ADR klären —
      ENTSCHIEDEN in ADR-004 (PW-2.6): Schaubild korrigiert auf
      app → data → game → core, der Build bleibt unverändert;
      Gegenrichtung als Invarianten I-M1–I-M4 festgeschrieben,
      CLAUDE.md-Kurzfassung angepasst, maschinelle Erzwingung als
      Ticket PW-2.6-impl spezifiziert (siehe oben)
- [ ] Orchestrator (aus PW-2.6): `.claude/agents/architekt.md` (Regel 1)
      und docs/plan.md §3.1/§6 tragen noch den alten Wortlaut
      „app → game/data → core". plan.md ist historisch und bleibt;
      die Agent-Definition bindet aber künftige Architekt-Läufe und
      sollte auf „app → data → game → core (ADR-004)" aktualisiert
      werden — Änderung von Agent-Definitionen liegt außerhalb des
      Architekt-Mandats
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
- [x] Entwickler (BUG PW-2.3-QS-B1, Prio niedrig — theoretischer
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
      BEHOBEN im PW-2.3-Korrekturzyklus: extraMoves wird vor der
      Multiplikation per `coerceIn(0, 10)` gekappt (exakt, da der Bonus
      ab Par+10 ohnehin 0 ist — allokationsfrei, kein Long noetig);
      Regressionstest aktiviert und gruen. DefaultGameEngine auf
      denselben Fehlertyp geprueft: keine Multiplikation/Addition auf
      unbeschraenkten Zaehlern (moveCount ist history.size)
- [ ] Entwickler (Befund aus PW-2.4, Prio niedrig — nur adversarialer
      Input): `HexCoord.ringIndex` läuft für `|q|`, `|r|` oder `|q+r|`
      nahe `Int.MIN_VALUE` über (`abs(Int.MIN_VALUE)` bleibt negativ,
      `q + r` kann wrappen) — `HexCoord(Int.MIN_VALUE, 0)` gälte als
      „Zentrum". Innerhalb des Prozesses unkritisch (Generator/Engine
      erzeugen nur kleine Koordinaten), an der Vertrauensgrenze fängt
      der DefaultLevelValidator das mit eigener Long-Arithmetik ab
      (inkl. Regressionstest). Falls ringIndex je außerhalb der
      Validator-Absicherung auf Rohdaten trifft: HexCoord auf
      Long-Arithmetik umstellen (eigenes Ticket, board/ war in PW-2.4
      out of scope)
- [ ] Orchestrator (aus PW-2.4): Ticket-Briefing nannte „Tier-Konsistenz"
      als Validierungsaspekt — §16.2 kennt keine solche Regel und die
      LevelViolation-API (9 Fälle) keinen passenden Verstoßtyp; Tier ist
      als Enum typsicher, jede Stufe ist gültig. Falls Tier-↔-Parameter-
      Konsistenz (z. B. Radius im Tier-Bereich aus §9.2) beim LADEN
      geprüft werden soll: Design-Präzisierung + API-Erweiterung nötig
      (eigenes Ticket; §9.2 ist bisher Generator-Qualitätsregel, keine
      Ladevalidierung)
- [ ] Architekt (aus PW-2.4): veraltete Ticket-Referenz auch in
      level/LevelValidation.kt — KDoc nennt „Implementierung:
      Ticket PW-2.6", implementiert wurde der Validator in PW-2.4
      (DefaultLevelValidator). Beim nächsten API-Touch zusammen mit den
      Tracer-/Engine-/Score-Einträgen korrigieren (KDoc-Änderung war in
      PW-2.4 out of scope)
- [x] Game-Designer/Orchestrator (aus PW-2.5): §9.2-Spalte „Farben im
      Level" ist im Design nicht definiert (zählen abgeleitete
      Strahlfarben? Kristall-Mischfarben?). Der Generator interpretiert
      sie deterministisch als „verschiedene Farben von Quellen,
      Kristallen und Filtern" und verwirft Versuche über der Kappe
      (dokumentiert in LevelConstruction-KDoc + Property-Test). Falls
      eine andere Lesart gewollt ist: Design-Präzisierung — Achtung,
      jede Änderung ist ein Generator-BREAKING (Goldens, Daily, I2) —
      KODIFIZIERT in PW-2.9: docs/game-design.md §9.2 definiert die
      Spalte jetzt als Anzahl distinkter Farbwerte über Quellen,
      Kristalle und Filter (exakt die gepinnte Generator-Lesart,
      kein BREAKING)
- [x] Architekt (aus PW-2.5): Feinheiten der Relaxierungsleiter
      (1000 Versuche je Stufe, optionale Elemente entfallen ab den
      Budget-Stufen, Fallback-Radius = Tier-Minimum) sind
      Implementierungs-Festlegungen über §9.5/7 hinaus. Für eine
      Zweit-Implementierung (z. B. iOS) müssten sie normativ ins
      Design-Dokument; bis dahin pinnen die Golden-Tests das Verhalten —
      KODIFIZIERT in PW-2.9: docs/game-design.md §9.5 dokumentiert
      Versuchszahl je Stufe, Stufenfolge inkl. Wegfall optionaler
      Elementtypen und Fallback-Radius = Tier-Minimum als
      Referenzverhalten; §9.3 zusätzlich die
      Mindest-Splitterzahl-Heuristik. Normativ bindend bleiben
      Determinismus (nur PRNG-Strom aus §8) + Golden-Pins; jede
      Verhaltensänderung ist als BREAKING gekennzeichnet (I2/Daily)
- [ ] Entwickler (aus PW-2.5, Idee): ParSolver ist internal; ein
      späteres Hint-System (Backlog unten) bräuchte ihn public —
      dann API-Design durch den Architekten (KDoc, Vertrag, Kover)
- [ ] Test-Engineer/Architekt (aus PW-2.2-Korrektur): dauerhafte
      Absicherung gegen still ignorierte Tests evaluieren — Detekt
      bringt keine passende Regel mit; Kandidaten: (a) eigene
      Detekt-Regel „@Test-Methode muss Unit zurückgeben" als
      custom-rules-Modul (braucht ADR, da neues Build-Artefakt),
      (b) JUnit `-Djunit.jupiter.testmethod...`-Discovery-Warnungen
      gibt es nicht — stattdessen im CI die testcase-Anzahl je Klasse
      gegen die @Test-Anzahl im Quelltext diffen, oder (c) schlicht
      Review-Checkliste. Empfehlung: (a) bei nächster Gelegenheit

- [ ] Aus PW-3.3: `org.robolectric:android-all-instrumented` steht direkt
      in app/build.gradle.kts (Ticket-Dateimenge ließ den Version Catalog
      unangetastet; Lint UseTomlInstead dafür begründet deaktiviert).
      Beim nächsten Katalog-PR: Koordinate in libs.versions.toml umziehen
      und die UseTomlInstead-Deaktivierung zurücknehmen. Achtung: Version
      ist an Robolectric + @Config(sdk=[35]) gekoppelt (DefaultSdkProvider),
      bei jedem Robolectric-Update mitprüfen.
- [ ] Aus PW-3.3: AppContainer nutzt eine Übergangs-Implementierung
      `InMemoryProgressRepository` (:app, di/) bis die DataStore-
      Repositories aus PW-3.2 verdrahtet sind (PW-3.5/PW-3.6). Beim
      Verdrahten die Übergangsklasse + ihren Test ersatzlos entfernen.

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
