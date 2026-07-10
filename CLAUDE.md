# CLAUDE.md — Grundregeln Projekt Puzzlewerk

## Rollenverständnis
- Du bist Teil eines Multi-Agent-Teams. Halte dich strikt an den Auftrag
  in deinem Prompt. Arbeite NICHT an Aufgaben anderer Agents, auch wenn
  dir Verbesserungen auffallen — notiere sie stattdessen unter
  docs/backlog.md und erwähne sie in deinem Abschlussbericht.
- Deine Arbeit ist erst fertig, wenn `./gradlew check` lokal grün ist.
  Liefere niemals wissentlich fehlschlagenden Code ab.

## Arbeitsweise
- Ein Auftrag = ein Branch = ein Pull Request. Branch-Schema:
  feature/<ticket>, fix/<ticket>, test/<ticket>.
- Kleine Schritte: Ein PR ändert höchstens ~400 Zeilen produktiven Code.
  Größere Aufträge zerlegst du und meldest die Teilschritte zurück.
- Committe atomar mit Conventional Commits (feat:, fix:, test:, refactor:,
  docs:, build:). Keine Commits direkt auf main.
- Lies vor Beginn IMMER: docs/architektur.md, docs/game-design.md und
  die für deine Aufgabe relevanten ADRs (docs/decisions/). Widersprich
  deinem Auftrag, wenn er gegen ein ADR verstößt — eskaliere an den
  Orchestrator.

## Code-Regeln (Kurzfassung, Details in docs/architektur.md)
- :game bleibt frei von Android-Imports. Ohne Ausnahme.
- Abhängigkeitsrichtung: app → data → game → core (ADR-004). Nie andersherum.
- val > var, immutable > mutable, sealed Results > Exceptions.
- Zufall/Zeit nur über injizierte Abstraktionen (RandomSource, WallClock).
- Jede neue public-Funktion in :game bekommt im selben PR Unit-Tests.
- Keine neuen Dependencies ohne ADR-Referenz im PR-Text.
- Keine Detekt/Lint-Suppressions ohne Begründungskommentar + Issue.

## Sicherheitsregeln (nicht verhandelbar)
- Keine Secrets, Keys oder Passwörter in Code, Config oder Commits.
- Keine neuen Permissions, kein Netzwerkzugriff, kein WebView, keine
  dynamische Code-Ausführung. Wer so etwas braucht: STOPP + Eskalation
  an den Menschen.
- Alle von außen eingelesenen Daten strikt validieren (Schema + Range).
- android:exported="false" für alles außer der Launcher-Activity.

## Eskalation statt Raten
- Wenn Anforderungen mehrdeutig sind, eine Regel dich blockiert oder
  zwei Regeln kollidieren: Stoppe, beschreibe das Problem präzise und
  eskaliere an den Orchestrator. Rate niemals bei Sicherheit,
  Persistenzformaten oder öffentlichen APIs.
- Destruktive Aktionen (Dateien löschen, Branches force-pushen,
  Historie umschreiben, Releases) sind ausschließlich dem Menschen
  bzw. explizit autorisierten Aufträgen vorbehalten.

## Abschlussbericht (Pflichtformat am Ende jedes Auftrags)
1. Was wurde geändert (Dateien, Kernentscheidungen)?
2. Wie wurde es verifiziert (Testläufe, Kommandos, Ergebnisse)?
3. Offene Punkte / Risiken / Backlog-Notizen.
