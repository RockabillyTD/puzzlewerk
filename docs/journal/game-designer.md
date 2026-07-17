# Journal — game-designer

> Aufgabenhistorie dieser Rolle. Pflege: Orchestrator, nach jedem
> gemergten/eskalierten Ticket. Letzte 8 Tickets voll, ältere je 1 Zeile.
> Kappe: 400 Zeilen. Schrittangaben vor Phase 4: n. a. (vor Budget-Regime).

## PW-4.1 — BREAKING-Addendum „Juice" §13/§15 (PR #31, gemergt 2026-07-16, ABGENOMMEN durch Branko inkl. aller 6 Entscheidungen)
- Gebaut: V1–V5 aus docs/phase4-juice-update.md normativ als §13.7
  (neu gefasst)/§13.8–13.13 + Randfälle R44–R50; §12.5-Sound-Schalter
  als ERSETZT markiert (→ 13.11, Musik/SFX getrennt, Default AN).
  Alle Werte mit Zahlen + durchgerechnetem Beispiel (Level 7.3).
- Review: MERGEABLE; 3 MINOR + 4 NIT in Folge-Commit gepatcht —
  wichtigste: SFX sind 13, nicht 12 (Zählfehler aus der Vorlage
  übernommen!); Stern-Bounce-ENDE (800 ms) überschreitet die
  600-ms-Frist → als bewusste, abnahmebedürftige V3-Abweichung
  kodifiziert; 3-Hz-Blitz-Grenze auf Vollbild präzisiert, sonst
  verletzt die eigene Kaskade die eigene harte Grenze.
- Learning: Zahlen aus Vorlagen nachzählen statt übernehmen; bei
  Zeitbudgets immer START- und ENDzeiten nachrechnen; harte Grenzen
  gegen die eigenen neuen Effekte gegenprüfen.
- Abnahme: Branko 2026-07-16 — alle 6 vorgelegten Entscheidungen
  (Kaskaden-Kappe ab 5. Burst, F = 60+12·K, Ebene-4-Bedingung
  max(1, K−1), 3 Dreh-Funken fix, V3-Abweichung, Brettrand-Emitter)
  abgenommen; Status-Flip (ABGENOMMEN) vor dem Merge kodifiziert.
- Schritte: 16 von 60+20 (Erstlauf 10, Review-Runde 6).

## PW-3.8 — §11.3/§12.5-Präzisierung (PR #16, gemergt 2026-07-12)
- Gebaut: campaignTier(n) exakt fixiert (D1:1–6, D2:7–12, D3:13–21,
  D4:22–29, D5:30–39, D6:40–46, D7:47–50); §12.5 Reset als zwei
  getrennte Aktionen inkl. kodifiziertem Daily-Reset-Randfall.
- Review: APPROVE mit Handnachrechnung; 1 MINOR per eigener
  Designer-Entscheidung gepatcht.
- Learning: Tier-SPANNEN („D1–D2") im Design waren ein Blocker für
  PW-3.6 — Zahlenwerte immer eindeutig festlegen, nie Bereiche.
- Schritte: n. a.

## PW-2.9 — §9/§16.2-Präzisierungen kodifiziert (PR #14, 2026-07-11)
- Gebaut: Präzisierungen aus dem PW-2.5-Review normativ ins
  Design-Dokument übernommen; ausdrücklich kein BREAKING.
- Kontext: Kodifizierungs-Auflage aus PR #12, vor Frist erledigt.
- Learning: Was Reviewer/Entwickler „zwischen den Zeilen" klären,
  muss zeitnah ins Dokument — sonst driftet die einzige Wahrheit.
- Schritte: n. a.

## PW-1.1 — Prisma-Design-Dokument (PR #4, gemergt 2026-07-09)
- Gebaut: docs/game-design.md komplett (1122+ Zeilen, 43 Randfälle
  R01–R43, 10 Invarianten I1–I10); normativ seit Abnahme.
- Review: 4 Befunde eingearbeitet; Reviewer hat das Beispiel-Level von
  Hand nachgerechnet. Drei Entscheidungen durch Branko (lokale
  Zeitzone, Prisma fix, Sterne-Zahlen). Abnahme durch Branko.
- Prozess: 1 Neustart nach API-Timeout.
- Learning: Randfall-Katalog ist die Testgrundlage des test-engineers —
  die QS-Suiten der Phase 2/3 zitieren R-Nummern direkt.
- Schritte: n. a.

## Offen für diese Rolle
- (nichts — nächstes Design-Ticket erst bei Eskalation aus
  PW-4.2 ff. oder neuem Gate-Feedback)