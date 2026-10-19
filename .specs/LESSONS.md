# LESSONS - auto-maintained by scripts/lessons.py

> Machine-owned. Do NOT hand-edit. Changes are overwritten on the next `lessons.py` write.
> Canonical state lives in `.specs/lessons.json`. Edit lessons only via the script.
> promote_threshold=2 distinct features · window_days=45 · quarantine_threshold=2

## Confirmed (load these at Specify/Design)

Corroborated across multiple features. Safe to apply as guidance.

_none_

## Candidates (under observation - do NOT load as guidance yet)

Seen once or not yet corroborated. Tracked, not trusted.

### L-001 - Preserve fractional seconds when presenting elapsed incident duration and assert a fractional timestamp example.
- signal: `ac_gap` · recurrence: 1 feature(s) · scope: `notifications` · harmful: 0
- features: mon-08-notificacoes
- evidence: src/main/java/br/com/monitor/notification/SmtpDelivery.java:84 (notifications)
- last seen: 2026-10-15T21:23:00Z

### L-002 - Assert notification transition IDs and retry timestamps against persisted values instead of checking only non-null presence.
- signal: `surviving_mutant` · recurrence: 1 feature(s) · scope: `query` · harmful: 0
- features: mon-09-consultas
- evidence: M4/M5; src/test/java/br/com/monitor/HistoryQueriesIT.java:129 (query)
- last seen: 2026-10-19T00:13:49Z

### L-003 - Assert exact validation error messages per field instead of checking only that error text is nonblank.
- signal: `ac_gap` · recurrence: 1 feature(s) · scope: `query` · harmful: 0
- features: mon-09-consultas
- evidence: READ-09; src/test/java/br/com/monitor/QueryApiIT.java:111 (query)
- last seen: 2026-10-19T00:13:49Z

### L-004 - Specify whether timestamp bounds constrain supplied values or computed defaults, and test the earliest default window.
- signal: `spec_precision_gap` · recurrence: 1 feature(s) · scope: `query` · harmful: 0
- features: mon-09-consultas
- evidence: T9; src/test/java/br/com/monitor/QueryParametersTest.java:21 (query)
- last seen: 2026-10-19T00:34:23Z

## Quarantined (failed when applied - ignore)

A confirmed lesson that recurred alongside failure. Kept for the maintainer to review.

_none_
