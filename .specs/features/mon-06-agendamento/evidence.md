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

## T3: runtime e integração

Premissas: polling após ApplicationReadyEvent, configuração externa e shutdown antes do datasource. Arquivos: SchedulerRuntime, YAML/Compose, SchedulerRuntimeIT, fixtures de testes anteriores; seleção/coordenador excluem IDs locais durante janela de cleanup. Registro AD-008. Clock reseta entre testes, mas regras temporais são avançadas explicitamente; HTTP real usa timeout de 1 s, tolerância 0,8..2,5 s. Nenhuma assertion foi enfraquecida.

Full `docker compose run --build --rm tests`: exit 0, BUILD SUCCESS, 301 testes (124 unitários/177 IT), zero falhas/erros/skips/Checkstyle; log `.validation/mon06-t3.log`. Baseline 264 preservada, +37 casos.

| Critério / esperado | Evidência de assertion | Coberto |
| --- | --- | --- |
| AC01: runtime automático, pausa/futuro preservados | SchedulerRuntimeIT.java:335 `healthyHits.isEqualTo(1)`; :336/337 counts=0; :338 monitor pausado igual; :339 próximo +60 | Sim |
| AC02: reserva removida antes da saída ainda não permite nova tentativa | SchedulerRuntimeIT.java:149 reserva vazia; :152 inFlight=1; :153/154 hits/resultados=1; :155 próximo ainda vencido; :160/161 após saída hits/resultados=2 | Sim |
| AC03/04: lento ocupa uma vaga e terceiro avança | SchedulerRuntimeIT.java:113 lento sem resultado; :114 terceiro inalterado; :118 hits saudáveis=2; :121 TIMEOUT; :122 duração limitada; :123 reservas vazias | Sim |
| AC05: restart interrompido preserva configuração/agendamento/estado/incidente | SchedulerRuntimeIT.java:249 count=1; :250 hits=0; :251 CANCELLED; :254/255 configuração/próximo iguais; :256 DOWN; :257 incidente igual; :258 recuperação=0 | Sim |
| AC05: crash após salvar resultado preserva primeiro resultado e contadores | SchedulerRuntimeIT.java:281 monitor igual; :282 resultado igual; :283 count=1 | Sim |
| AC06: avanço 600 s causa somente um novo GET, próximo +60 | SchedulerRuntimeIT.java:263 count=2 (cancelamento+nova amostra); :264 hits=1; :265 próximo +60 | Sim |
| AC07: API FRESH em L, STALE depois, sem resultado artificial | SchedulerRuntimeIT.java:315 FRESH; :316 recent=true; :320 STALE; :321 recent=false; :322 UP preservado; :323 count=0; :324 hits=0 | Sim |
| AC08: pausa cancela HTTP e preserva incidente; retomada gera uma tentativa | SchedulerRuntimeIT.java:177 monitor igual ao pausado; :178 CANCELLED; :179 DOWN; :180 incidente igual; :187/188 count/hits=2; :189 próximo +60 | Sim |
| AC08: versão de política antiga só histórico; nome não cancela | SchedulerRuntimeIT.java:212 versão1/CANCELLED; :213/214 versão2/geração2/SUCCESS; :215/216 UNKNOWN/sem observação; :231 monitor renomeado igual; :232 SUCCESS | Sim |
| Persistência indisponível: reserva recuperável, sem falha externa fictícia | SchedulerRuntimeIT.java:295 count=0; :296 reserva presente; :300 vazia; :301 CANCELLED; :302 hits=1 | Sim |
| Shutdown, limites e upgrade | SchedulerRuntimeIT.java:352/353 CANCELLED; :354 reserva vazia; :362 IllegalArgumentException; :384/385 migração=1/0; :386 histórico igual; :388/389 monitor/resultado iguais | Sim |

Mapeamento inverso: `slowTimeoutDoesNotPreventHealthyAndExcessMonitorFromAdvancing` → AC03/04; `reservationCleanupBeforeWorkerExitStillPreventsAnotherStartOfSameMonitor` → AC02; `pauseDuringRealHttpCancelsWithoutRecoveringIncidentAndResumeRunsOnce`, `policyEditInvalidatesOldWorkerAndSchedulesOnlyNewVersion`, `nameOnlyEditPreservesRunningAttemptAndRealSuccessHistory` → AC08; `interruptedReservationSurvivesContextRestartThenOverdueRunsWithoutReplay` → AC05/06; `restartAfterResultBeforeReservationCleanupPreservesFirstResult` → AC05; `realPersistenceFailureLeavesRecoverableReservationWithoutExternalFabrication` → falha parcial; `apiRepresentsExpiredObservationWithoutInventingCheckResult` → AC07; `enabledRuntimeStartsAutomaticallyAndHonorsPersistedPauseFutureAndCapacity` → AC01/03; `runtimeShutdownCancelsHttpBeforeClosingDatabase`, quatro `invalidRuntimeConfigurationPreventsStartup`, `v3UpgradePreservesConfigurationResultAndMigrationHistory` → edges operacionais. Todos necessários, sem skips. Fixtures de incidentes representam referências administrativas existentes; criação/aplicação real de incidentes permanece MON-07. Adequacy A/B/C/D PASS, resultado/estado/payload exatos, mesmos guidelines.

## T4: operação e gates finais do autor

Arquivos: docs/scheduling.md, README, .env.example, expectativa de quatro migrações no smoke, rastreabilidade e STATE. Sem mudança do código Java após e47a640, portanto o Full T3 de 301 testes é o gate completo do código final; não repetir a mesma suíte apenas por alterações de prosa. Runtime `docker compose build app`: exit 0, 124 unitários e Checkstyle aprovados; `.validation/mon06-build.log`. Smoke `powershell -NoProfile -ExecutionPolicy Bypass -File scripts/validate-compose.ps1 -NoBuild`: exit 0, PASS Compose lifecycle, `.validation/mon06-smoke.log`; projeto descartável `mon02-smoke-92b2dd8e49f0` removido. Primeiro smoke sinalizou contagem antiga 3; expectativa expandida a 4 mantendo a comparação V1/V2/V3/V4 e histórico integral.

Comandos executados: validate_spec.py/validate_tasks.py/check_commit.py da skill; Quick Docker de T2; Full Docker T1/T3; runtime Docker; smoke PowerShell/Compose. Todos os gates finais com exit 0. Execuções negativas intermediárias corrigidas, sem excluir/enfraquecer assertions. Baseline: 264 testes; final: 301 (+37). Verifier independente ainda produzirá validation.md com evidência e sensor em scratch. Sem push/CI remota/deploy.

## Fechamento independente

[validation.md](validation.md): PASS 8/8, Full independente exit 0 com 301 testes (124 unitários/177 IT), zero falhas/erros/skips/Checkstyle, build e smoke independentes aprovados. Cinco mutações em contêineres isolados compiladas/executadas e mortas pelos testes; zero sobreviventes. Porcelain confirmado limpo antes/depois do sensor, somente relatório novo ao concluir. validate_state.py: zero erros. Sem sinal para lições.

Commits atômicos: T1 `52826be`, T2 `c9deb64`, T3 `e47a640`, T4 `a09f257`. Fechamento inclui relatório, backlog e snapshot final. Código/testes reais não alterados após T3. Todos os oito critérios de origem estão marcados concluídos com evidência independente.
