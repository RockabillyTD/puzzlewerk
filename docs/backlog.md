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
- [ ] Architekt (Review-NIT aus PR #15): DailyRecord in
      daily/DailyStatsRepository.kt dokumentiert Wertebereiche (par 1..14,
      moves ≥ 1) nur im KDoc, während Score sie per init-require erzwingt.
      Beim nächsten API-Touch init-Block ergänzen (Konsistenz mit dem
      Score-Muster; bis dahin fangen die PW-3.2-Mapper-Checks das ab)
- [ ] Architekt (Review-MINOR-4 aus PR #17): Brettform-Zugehörigkeit
      (§2.1) existiert doppelt — game/.../generator/BeamPaths.kt (internal)
      und app/.../ui/game/HexGeometry.boardCells. Public `Board.cells()`
      in :game entscheiden, dann beide Duplikate darauf zurückführen
- [ ] Game-Designer (Review-NIT-1 aus PR #18): §12.3 („empfangene
      Komponenten klein darunter") und §13.3 (Umriss-Reihe) beschreiben
      die Teilerfüllt-Darstellung unterschiedlich — vereinheitlichen
      (Implementierung folgt §13.3; kein Verstoß, nur Doku-Drift)
- [ ] Entwickler (Folge-Ticket aus PR-#20-Review, klein): (a) Lademapper
      Daily: Invariante currentStreak ≤ longestStreak beim Laden als
      Corrupted abweisen; Plausibilitätsgrenzen für Zähler/Arrays prüfen
      oder bewusstes Nicht-Prüfen im KDoc begründen (Int-Überlauf
      currentStreak+1 bei manipuliertem Int.MAX_VALUE-Bestand);
      (b) S4-Strictness-Test „String-Literal statt Zahl ⇒ Corrupted"
      für Progress- UND Daily-Schema (pinnt die Streaming-Decoder-Wahl)
- [ ] Entwickler (aus PW-3.2-Bericht): Fakes können keine Io-Schreibfehler
      simulieren; PersistenceFailure.Io-Pfad nur strukturell, nicht per
      Test provoziert — falls PW-3.5/3.6 Fehler-UI testen wollen,
      failWith um Write-Fehler erweitern
- [ ] Release-Engineer (aus PW-3.4-Bericht): detekt scannt src/testDebug
      mit den Standard-Quellpfaden nicht (ktlint deckt es ab) —
      Build-Pflege-Ticket
- [ ] PW-3.5-MUSS-PUNKT (Review-MINOR-2 aus PR #18): Tap-Hit-Box des
      Spielfelds mit der auf 1,5·size begrenzten Semantics-Box abstimmen;
      Touch-Targets ≥ 48 dp verantwortet der Spiel-Screen (Brettskalierung)
- [ ] ui-entwickler (aus Re-Review PR #18, akzeptierter V1-Randfall):
      GLEICHFARBIGE sich kreuzende Mischfarben-Strahlen verschmelzen in
      der Union-Find-Chipvergabe zu einem Zug — einer kann chip-arm
      werden (Komponente behält ≥ 1 Chip, Farbinfo nie falsch). Falls
      Playtests sichtbare Lücken zeigen: auf from→to-Pfad-Verkettung
      umstellen. Zusätzlich NIT: BoardRenderSpecTest prüft PathEffects
      nur mit assertNotNull — assertNotSame zwischen den drei
      Beam-Effekten würde „alle Muster identisch"-Mutationen fangen
- [x] Release-Engineer (Qualitäts-Gate-Lücke, HIGH-Fund der PW-3.2-
      Fix-Verifikation): Java-9+-APIs oberhalb von minSdk (z. B.
      InputStream.readNBytes, erst API 33) werden in :data/:app-Hauptquellen
      von keinem Gate gestoppt — JVM-Unit-Tests sind blind, :data-Lint
      läuft nicht in der Gate-Kette. Optionen: :data:lintDebug (NewApi)
      in die Verbindlichen Kommandos + CI aufnehmen, oder Desugaring-
      Entscheidung per ADR. Konkreter Vorfall: readNBytes hätte auf
      Android 8–12L jeden Store-Read gecrasht (vor Merge gefangen) —
      UMGESETZT in PW-3.10 (Branch build/pw-3.10-data-lint-gate),
      minimale Option per Orchestrator-Entscheid: :data:lintDebug in
      den Verbindlichen Kommandos (docs/architektur.md) und im
      CI-quality-gates-Job; Lint-Reports von :data sind vom bestehenden
      Failure-Artefakt-Glob **/build/reports/lint-results-* bereits
      erfasst. NewApi-Negativprobe durchgeführt (readNBytes temporär in
      :data ⇒ Task rot, danach zurückgesetzt). KEIN Desugaring, KEIN ADR
- [ ] Release-Engineer (Nachtrag aus PW-3.10, LOW): :data hat — anders
      als :app — keinen lint{}-Block; warningsAsErrors/abortOnError
      laufen dort mit AGP-Defaults (abortOnError=true, Warnungen
      brechen NICHT). NewApi ist per Default Error-Severity, die
      PW-3.10-Lücke ist also geschlossen; für volle Konsistenz mit der
      Quality-Gates-Tabelle („Android Lint | warningsAsErrors") den
      Lint-Block beim nächsten Build-Pflege-Ticket auf :data spiegeln
      (war in PW-3.10 explizit out of scope: „keine inhaltlichen
      Lint-Konfigurationsänderungen über die Gate-Aufnahme hinaus")
- [ ] Security/Orchestrator (wiederholt aus zwei Audits): gitleaks ist
      auf der Maschine nicht installiert — S7-Scan je PR läuft nur als
      manueller Muster-Sweep; Tool in die Toolchain aufnehmen
- [ ] Release-Engineer (Security-LOW-2 aus PW-3.3-Audit): Robolectric-
      Offline-Pinnung wurzelweit heben (Convention/subprojects) beim
      android-all-Katalogumzug; bis dahin schützt nur der Wächter-Test
      in :app
- [ ] Game-Designer (aus Review PR #21, für A11y-Pass PW-3.7/Phase 4):
      Magenta #D6409F erreicht auf #101418 nur 4,49:1 — AA für
      Normaltext knapp verfehlt; als on-Farbe nur für großen Text/
      UI-Komponenten dokumentiert. Finale Ton-Entscheidung (§13.4
      normativ) steht aus
- [ ] Architekt (Review-Frage aus PR #19): Soll der Progress-Lademapper
      Kreuz-Konsistenz Punkte↔Sterne (Punkte ∈ 1000+50er-Raster, Sterne
      passend zu §7.2) als Corrupted werten? Braucht KDoc-/ADR-007-
      Präzisierung — kein Alleingang der Implementierung
- [ ] ui-entwickler (Review-MINOR aus PW-3.5a, notiert in PW-3.5b):
      `GameViewModel.init` ruft `generateLevel`/`newGame` ohne
      Fehlerbehandlung — schlägt die Generierung (§9.4) je fehl, bliebe
      der Screen in `isLoading=true` hängen (Dauer-Ladezustand). Ein
      Lade-Fehlerfeld im `GameUiState` plus definierte Fehler-UI (analog
      LevelSelect `hasLoadError`) erwägen; braucht zuvor eine
      `:game`-Entscheidung, ob/wie `LevelGenerator.generate` scheitern darf.
- [ ] ui-entwickler (NIT-1 aus PR #25, notiert in PW-3.5b): Die
      Tier-Anzeige nutzt `Difficulty.ordinal + 1` in :app (LevelSelect
      und ggf. Spiel-Screen). Sauberer wäre ein Anzeige-Akzessor in :game
      (z. B. `Difficulty.displayNumber`), damit die 1-basierte D1..D7-
      Darstellung nicht in der UI hartkodiert ist.
- [ ] ui-entwickler (aus PW-3.5b): Die Dreh-Animation läuft optisch zum
      JEWEILS neuesten Brettzustand nach (Interrupt statt strenger
      FIFO-Puffer). Bei sehr schnellen Taps wird nur der letzte Übergang
      animiert — Logik bleibt korrekt (im ViewModel längst angewandt).
      Falls Playtests das Nachlaufen als sprunghaft empfinden: echte
      Warteschlange der Einzeldrehungen erwägen.
- [ ] ui-entwickler (Review-MINOR aus PR #26): Dreh-Animation läuft immer
      vorwärts — bei einem Undo (m→m−1) springt das Element 60° zurück und
      dreht +30° vor (ruckelt optisch; Logik korrekt). Drehrichtung aus dem
      Vorzeichen der Orientierungsdifferenz (mod 6, kürzerer Weg) ableiten
      oder Undo nicht einzeln animieren. Beim nächsten Animations-Feinschliff.
- [ ] ui-entwickler/architect (aus PW-3.7): GameViewModels sind Activity-
      scoped und werden je Request geschlüsselt (Fix des „Weiter"-Bugs in
      GameRoute). Folgen des eigenen Backstacks (ADR-008) ohne Owner je
      Eintrag: (a) ViewModels gelöster/verlassener Level bleiben bis zum
      Activity-Ende im Store (bounded: ≤ 50 Kampagnen + Dailys, kleine
      Objekte); (b) Rückkehr zu einem in DIESER Sitzung gelösten Level
      zeigt wieder das Ergebnis-Overlay statt einer frischen Partie
      (Workaround: „Nochmal"). Sauber wäre ein ViewModelStoreOwner je
      Backstack-Eintrag mit Clear beim Pop — bei Gelegenheit als kleine
      Erweiterung des Navigation-Roots (ADR-008-konform) nachziehen.

- [ ] ui-entwickler (QS-Befund PW-3.7-QS, MINOR): „Weiter" im
      Ergebnis-Overlay PUSHT `Game(n+1)` auf den Backstack statt den
      obersten Eintrag zu ersetzen (`GameRoute` → `onNavigate` →
      `NavigationState.navigateTo`). Folgen: (a) System-Zurück nach
      „Weiter" führt zurück auf das GELÖSTE Vorlevel (Overlay wieder
      sichtbar, vgl. bestehenden ViewModel-Store-Eintrag oben); (b) wer
      viele Level am Stück löst, sammelt einen langen Game-Stack an;
      (c) `navigateTo` dedupliziert nicht — ein Doppel-Tap-Race auf
      „Weiter" kann denselben Screen zweimal stapeln (Zurück wirkt dann
      scheinbar wirkungslos). Vorschlag: Replace-Top-Semantik für
      Game→Game-Navigation im Navigation-Root (ADR-008-konform) und/oder
      No-op bei `navigateTo(currentScreen)`.
- [ ] architect/game-designer (QS-Befund PW-3.7-QS, MINOR, Phase 4):
      `LevelRequest.Daily` verlangt `epochDay >= 0` und
      `decodeScreen` verwirft `game/daily/<negativ>` — das kollidiert
      mit R37 (Gerätedatum vor 1970: Daily muss funktionieren, Seed-
      Formel ist definiert). Vor dem Daily-Einstieg (Phase 4)
      entscheiden: R37 auf den Navigations-/Request-Pfad ausdehnen
      (negative epochDay zulassen) ODER R37 im Design auf die reine
      Seed-Formel eingrenzen.
- [ ] ui-entwickler (QS-Befund PW-3.7-QS, MINOR):
      `rememberAnimationsEnabled` liest `ANIMATOR_DURATION_SCALE` genau
      einmal je Context (`remember(context)`) — schaltet die Nutzerin
      „Animationen entfernen" um, während die App läuft, greift die
      Änderung erst nach Activity-Recreate. Falls Feinschliff gewünscht:
      Setting per `ContentObserver`/`snapshotFlow` beobachten.
- [ ] test-engineer (Review-MINOR aus PR #29): die Test-Fixture
      `tinySolvableLevel()` existiert dreifach (E2eSmokeTest,
      GameViewModelQsTest, GameRouteQsTest) — bei Änderungen am
      Level-Schema driften die Kopien auseinander. In eine gemeinsame
      Test-Fixture-Datei im Package `de.puzzlewerk.app.ui.game`
      konsolidieren (zusammen mit der Test-Source-Set-Vereinheitlichung
      aus den PR-#28-NITs erledigen).
- [ ] ui-entwickler/release-engineer (PW-4.0, Security-Audit MINOR-1):
      `app/src/main/res/raw/keep.xml` (tools:keep für die 18 Phase-4-
      OGGs) zurückbauen bzw. auf die dann noch unreferenzierten Assets
      eindampfen, sobald PW-4.5/PW-4.8 die R.raw-Referenzen liefern;
      finale Prüfung inkl. Release-Shrinker gehört zu PW-4.10.
- [ ] release-engineer (PW-4.0, Security-Audit MINOR-2): Der
      Reproduzierbarkeits-Anspruch der Audio-Assets gilt nur für die
      Synthese (Seed 42), nicht für den Vorbis-Encode (ffmpeg/libvorbis-
      versionsabhängig, kein Bit-Determinismus); zudem hartkodierter
      Linux-Out-Pfad in tools/audio/synth.py. Bei Gelegenheit: ffmpeg/
      libvorbis-Version in docs/phase4-juice-update.md §0 pinnen oder
      Anspruch auf „inhaltlich reproduzierbar" präzisieren, OUT relativ.
- [ ] game-designer/orchestrator (PW-4.0/4.1): phase4-juice-update.md §0
      zählt „SFX (12)", geliefert und normativ sind 13 (Zählfehler dort;
      maßgeblich ist die 13er-Tabelle in game-design.md §13.11).
      music_demo_steigerung.ogg ist reines Anhör-Demo — Verbleib im APK
      entscheidet PW-4.8 (Größenbudget PW-4.10).
- [ ] release-engineer (PW-4.8, offene Entscheidung für PW-4.10):
      music_demo_steigerung.ogg (652 KiB, größtes Audio-Asset) wird von
      der AudioEngine bewusst NICHT geladen (§13.11: kein Laufzeit-
      Asset) und ist im Code unreferenziert — nur keep.xml hält es im
      APK. In PW-4.10 entscheiden: aus res/raw + keep.xml entfernen
      (spart ~652 KiB APK) oder als Anhör-Referenz behalten; hängt am
      APK-Größenbudget des Gate-Artefakts.

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
