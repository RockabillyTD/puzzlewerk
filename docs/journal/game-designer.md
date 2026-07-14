# Journal — game-designer

> Aufgabenhistorie dieser Rolle. Pflege: Orchestrator, nach jedem
> gemergten/eskalierten Ticket. Letzte 8 Tickets voll, ältere je 1 Zeile.
> Kappe: 400 Zeilen. Schrittangaben vor Phase 4: n. a. (vor Budget-Regime).

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
- Phase 4, Punkt 1 (PW-4.1): BREAKING-Addendum „Juice" in §13/§15 —
  wartet auf Phase-3-Gate durch Branko und auf die Vorlage
  docs/phase4-juice-update.md (liegt noch NICHT im Repo).