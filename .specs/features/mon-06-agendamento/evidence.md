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

## T2: coordenador limitado

Premissas: limite 1..16, mapa limitado ao trabalho iniciado, liberação no finally real. Arquivos: CheckScheduler e CheckSchedulerTest. Sem fila de executor. Quick `docker compose run --build --rm tests mvn --batch-mode --no-transfer-progress test`: exit 0, 124 unitários, zero falhas/erros/skips/Checkstyle; log `.validation/mon06-t2.log`. Fixture de resultado corrigida para evitar unboxing de null; nenhuma assertion alterada.

| Critério / esperado | Evidência de assertion | Coberto |
| --- | --- | --- |
| AC02/03: rodadas simultâneas limitadas a 2 e excedente pendente | CheckSchedulerTest.java:80 `inFlight().isEqualTo(2)`; :81 `due.containsExactly(third)`; :82 IDs dos dois workers | Sim |
| AC04: saudável termina e terceiro avança enquanto lento vive | CheckSchedulerTest.java:88 resultados healthy/third; :90 `inFlight().isEqualTo(1)`; :91 thread lenta viva | Sim |
| AC04: todos os outcomes liberam vaga e preservam payload | CheckSchedulerTest.java:105/106 idle/zero; :108 executionId; :109 outcome exato; :114 IDs dos resultados | Sim |
| AC08: invalidação interrompe sem liberar antes da saída | CheckSchedulerTest.java:139 interrupt recebido; :141 ocupado=1; :142 retomada pendente; :146 CANCELLED | Sim |
| Falha interna não externa | CheckSchedulerTest.java:162 INTERNAL_ERROR; :163 mensagem fixa; :164 external=false | Sim |
| Persistência/claim falhos sem capacidade perdida | CheckSchedulerTest.java:179 zero; :184 órfão reconciliado; :193/194 exceção e zero | Sim |
| Shutdown e configuração inválida | CheckSchedulerTest.java:210 zero; :211 CANCELLED; :215 próximo ainda pendente; :221 IllegalArgumentException | Sim |

Mapeamento inverso: `concurrentRoundsRespectCapacityAndKeepExcessPendingWhileHealthyFreesSlot` → AC02/03/04; cinco casos `everyTerminalOutcomeReleasesCapacityAndPreservesResult` → AC04; `invalidatedGenerationInterruptsButDoesNotFreeCapacityBeforeWorkerExits` → AC02/08; `unexpectedExecutionErrorIsInternalAndReleasesSlot`, `persistenceFailureReleasesSlotAndNextRoundRecoversOrphan`, `claimFailureDoesNotConsumeCapacity` → edges de falha/capacidade; `shutdownCancelsWorkersAndPreventsNewStarts`, três `rejectsInvalidConcurrency` → edges de shutdown/config. Assertions de estado e payload, sem teste fora da spec. Adequacy A/B/C/D PASS, estilo existente JUnit/AssertJ/Checkstyle.
