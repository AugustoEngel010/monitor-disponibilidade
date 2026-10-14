# MON-06: evidências de execução

## T1: reserva durável

Premissas: uma instância, início lógico no claim, lock administrativo compartilhado; configuração/histórico não são recriados. Arquivos: ScheduleStore, MonitorRepository, V4, ScheduleStoreIT e fixtures/contagem de migrações existentes.

Gates: validadores spec/tasks com zero erros e avisos. Primeiro Docker gate detectou três linhas Checkstyle; corrigidas. Segundo detectou fixture V2→V3 apontando para latest; alvo limitado a V3, assertions preservadas. Gate final `docker compose run --build --rm tests`: BUILD SUCCESS, 271 testes (110 unitários/161 IT), zero falhas/erros/skips, zero Checkstyle. Log `.validation/mon06-t1.log`.

| Critério / esperado | Evidência de assertion | Coberto |
| --- | --- | --- |
| AC01: ordem, igualdade, pausa/futuro excluídos; +I | ScheduleStoreIT.java:69 `isEqualTo(first.id())`; :72 `isEqualTo(now.plusSeconds(60))`; :75 `isEqualTo(boundary.id())`; :77/78 `contains(paused/future)` | Sim |
| AC02: único claim concorrente e posterior | ScheduleStoreIT.java:89 `containsExactlyInAnyOrder(true, false)`; :91 `isEmpty()` | Sim |
| AC03: excedente mantém horário/estado | ScheduleStoreIT.java:73 `contains(second)` | Sim, limite de workers T2 |
| AC05: CANCELLED não externo, configuração/estado/incidente/agendamento preservados | ScheduleStoreIT.java:124 `isEqualTo(Outcome.CANCELLED)`; :125 `isFalse()`; :132-139 valores preservados e sequências zeradas | Sim |
| AC05: primeiro resultado vence, sem reserva não zera contadores | ScheduleStoreIT.java:159 `contains(result)`; :160 `isEqualTo(2)` | Sim |
| AC06: atraso 600 s executa uma vez e avança a 660 | ScheduleStoreIT.java:144 `isEmpty()`; :145 `isEqualTo(now.plusSeconds(660))` | Sim |
| AC08: retomada bloqueada enquanto tentativa anterior existe, agenda administrativa preservada | ScheduleStoreIT.java:171 `isEmpty()`; :173 `contains(resumed)`; :185 `contains(paused)` | Sim, interrupção T2 |
| Falha parcial: claim faz rollback | ScheduleStoreIT.java:102 `isInstanceOf(DataAccessException.class)`; :105 `contains(monitor)`; :106 `isEmpty()` | Sim |

Mapeamento inverso: `selectsOldestDueActiveWithStableIdTieAndLeavesExcessUnchanged` → AC01/03; `simultaneousClaimsAndSuccessiveRoundsCannotOverlapEvenAfterInterval` → AC02; `failedReservationRollsBackSchedule` → edge de falha parcial; `interruptionRecoveryPreservesStateHistoryAndScheduleAndIsIdempotent` → AC05/06; `recordedResultWinsRecoveryAndRestartWithoutAttemptPreservesCounters` → AC05; `pauseResumeAndPolicyEditDoNotOverwriteTheirScheduleOrReleaseOldReservation` e `finishOnlyRemovesItsOwnExecutionAndPreservesPausedState` → AC08. Nenhum teste sem requisito. Assertions inspecionam estado/resultados reais. Diretrizes: pom.xml e config/checkstyle.xml; sem testes removidos ou skips.
