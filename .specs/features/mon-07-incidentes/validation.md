# MON-07 Validation

**Verdict: PASS.** Oito ACs têm resultados específicos verificados independentemente. Build Docker e sensor comportamental aprovados.

**Date:** 2026-10-14. **Verifier:** subagente independente `/root/verify_mon07`, autor != verificador.
**Spec:** `.specs/features/mon-07-incidentes/spec.md`. **Diff range:** `2179a4b..197551d`.

## Task Completion

T1–T5 estão marcadas Done. Cinco commits atômicos no range: 491d478, 5e61f87, 1a2e8da, 808e7c6 e 197551d. O último é documental. A evidência abaixo foi rederivada dos testes e executada pelo Verifier; o relatório do autor não foi usado como prova.

## Spec-Anchored Acceptance Criteria

Todos os caminhos de testes desta seção partem de `src/test/java/br/com/monitor/`.

| AC | Resultado exigido pela spec | file:line + assertion independente | Resultado |
| --- | --- | --- | --- |
| MON-07-AC01 / INC-01 | F=3: contadores 1,2 sem incidente; terceira DOWN,3, um incidente; saturação | `src/test/java/br/com/monitor/IncidentProcessorIT.java:84` failureCount isEqualTo(1); :88 isEqualTo(2); :86 count(incidents).isZero(); :92 confirmedState isEqualTo("DOWN"); :93 isEqualTo(3); :94 count isEqualTo(1). `src/test/java/br/com/monitor/IncidentStateMachineTest.java:45` repeated failureCount isEqualTo(3) | PASS |
| MON-07-AC02 / INC-02 | SUCCESS antes do limite: UP, falhas=0, firstFailureAt=null, nenhum incidente/transição/intenção | `src/test/java/br/com/monitor/IncidentProcessorIT.java:143` failureCount.isZero(); :144 firstFailureAt.isNull(); :145 confirmedState.isEqualTo("UP"); :146–148 counts.isZero(); `src/test/java/br/com/monitor/IncidentStateMachineTest.java:55` primeiro SUCCESS isEqualTo("UP") | PASS |
| MON-07-AC03 / INC-03 | Novas falhas preservam UUID; banco impede segundo aberto | `src/test/java/br/com/monitor/IncidentProcessorIT.java:103` openIncidentId.isEqualTo(id); :104 count.isEqualTo(1); `src/test/java/br/com/monitor/IncidentSchemaIT.java:71` segundo insert throws DataAccessException; :92 concorrência containsExactlyInAnyOrder(true,false); :94 count aberto isEqualTo(1) | PASS |
| MON-07-AC04 / INC-04 | R=2: primeiro DOWN/1; segundo UP/2 fecha; falha intermediária apaga início | `src/test/java/br/com/monitor/IncidentProcessorIT.java:108` DOWN; :109 recoveryCount=1; :112 UP; :114 recoveryCount=2; :119 recoveredAt=base+240s; :160 recoveryCount.isZero(); :161 recoveryStartedAt.isNull(); :166 início novo=base+300s; :167 fechamento=base+360s | PASS |
| MON-07-AC05 / INC-05 | Replay e concorrência preservam todas as tabelas e horários; primeiro payload vence | `src/test/java/br/com/monitor/IncidentProcessorIT.java:202` checks.save(divergent).isEqualTo(third); :205/:210 snapshot().isEqualTo(before); :222 resultados concorrentes containsExactlyInAnyOrder(true,false,false,false); :232 snapshot().isEqualTo(before). Snapshot em :376-382 inclui as seis tabelas: monitors, incidents, transitions, intents, applications e check_results | PASS |
| MON-07-AC06 / INC-06 | INTERNAL_ERROR/DESTINATION_BLOCKED/CANCELLED e lacuna limpam sequências, preservam confirmação/incidente/observação | `src/test/java/br/com/monitor/IncidentStateMachineTest.java:101` failureCount.isZero(); :102 firstFailureAt.isNull(); :104 lastObservedAt=base+60s; :105 currentObservation.isTrue(); :110 recoveryCount.isZero(); :111 recoveryStartedAt.isNull(); :112 DOWN; :113 mesmo UUID. EnumSource em :95 cobre os três resultados. :155 L exata RECOVERY; :159 L+1us NONE; :160 contador=1 | PASS |
| MON-07-AC07 / INC-07 | Reinício mantém contadores somente sem interrupção/lacuna; pausa/retomada invalidam e preservam histórico; edição obedece contrato | `src/test/java/br/com/monitor/IncidentLifecycleIT.java:137` current().isEqualTo(before) após restart; :139 contador=3 ou1 para gaps60/126; :152 restart preserva recuperação; :170 recoveryCount.isZero(); :171 currentObservation.isFalse(); :177 retomada mantém incidente; :229 edit throws status409; :230 current().isEqualTo(renamed); :221 intents.isEqualTo(intentions); `src/test/java/br/com/monitor/MonitorTest.java:76`/:81 assert State completo com gerações incrementadas | PASS |
| MON-07-AC08 / INC-08 | Quatro finishedAt e efeito atômico: marcador/estado/incidente/transição/intenção; rollback sem parcial | `src/test/java/br/com/monitor/IncidentProcessorIT.java:96` firstFailureAt=base; :97 downConfirmedAt=base+120s; :118 recoveryStartedAt=base+180s; :119 recoveredAt=base+240s; :125 payload congelado/status PENDING; :386 assertTransition vincula execution/monitor/incident/version/type/confirmedAt; :268/:290 snapshots iguais após falha; :271/:293 retry true. ValueSource :257/:280 injeta erro em incidents, state_transitions, notification_intents e result_applications | PASS |

**Status:** 8/8 ACs com evidência específica. Zero gaps de precisão. Valores e estados conferem com a spec; não apenas presença de assertions.

## Edge Cases e contratos complementares

| Borda/contrato | Evidência e resultado esperado | Resultado |
| --- | --- | --- |
| FFFF, FF S F e primeiro S | IncidentStateMachineTest.java:43 transition NONE e :45/:46 contador3/mesmoUUID; :61–66 zero/null seguido de contador1 e novo início | PASS |
| F/R=1 e10; todas as falhas externas | IncidentStateMachineTest.java:130 parâmetros1/10; :135/:142 transição somente no índice threshold-1; :146 início da recuperação exato. :118–125 EnumSource cinco falhas e transição DOWN em F1 | PASS |
| DOWN S F S S; três interrupções não externas | IncidentProcessorIT.java:160–167 zero/null e horários300/360; :172–192 EnumSource preserva DOWN/UUID, apaga recuperação e não fecha | PASS |
| L exata/L+1us e duração HTTP | IncidentStateMachineTest.java:155–169 RECOVERY em305s mesmo finished335s; +1000ns reinicia em306s; falha exata185 confirma e186 reinicia. IncidentProcessorIT.java:341–349 início reservado185s e finish190s; :345 process.isTrue(); :346 confirmedState.isEqualTo("DOWN"); :347 firstFailureAt.isEqualTo(base); :348–349 downConfirmedAt.isEqualTo(base.plusSeconds(190)) | PASS |
| Concorrência de falhas distintas | IncidentProcessorIT.java:246–253 ambos true, contador3, um incidente/transição/intenção e quatro aplicações | PASS |
| Histórico somente: anterior/geração/versão/pausado | IncidentProcessorIT.java:324–333 resultado anterior e geração antiga: process.isFalse(), monitor igual, disposition.isEqualTo("HISTORY_ONLY") e incidentes0; IncidentLifecycleIT.java:204–210 monitor igual, replay false e zero transições, para PAUSE/RESUME/POLICY, incluindo versão antiga após edição | PASS |
| Leitor não vê parcial e rollback externo | IncidentProcessorIT.java:308 process.isTrue() dentro da transação; :310 reader snapshot.isEqualTo(before); :314 snapshot.isEqualTo(before) após rollback; :315 retry true; :316 DOWN | PASS |
| Reserva sem resultado e com resultado salvo | IncidentLifecycleIT.java:253–270 CANCELLED, DOWN/UUID preservados, recoveryCount0/inícios null/observação e agenda preservadas; :281–289 resultado original, DOWN no timestamp120s e um efeito antes de retirar reserva | PASS |
| Retry de aplicação sem HTTP adicional; banco não vira falha externa | IncidentLifecycleIT.java:303–316 reserva permanece, contador2/incidentes0, três brutos, retry DOWN e hits=1. IncidentProcessorIT.java:268–274 rollback mantém bruto/sem marcador e retry evidencia120s | PASS |
| Pausa concorrente | IncidentLifecycleIT.java:334–346 PAUSED, contador0, estado/tabelas coerentes com vencedor do lock, finish tardio preserva paused | PASS |
| Pausa também reverte atualização do incidente | IncidentLifecycleIT.java:357–360 erro DataAccessException e monitor/incidente iguais ao snapshot anterior | PASS |
| Nome/destinatário, todos os campos de política e payload congelado | IncidentLifecycleIT.java:220 estado igual; :221 intents iguais; :229 status409 para URL/I/T/status/F/R; :234–239 RECOVERY novo destinatário e DOWN destinatário antigo. MonitorTest.java:62 State UNKNOWN completo sem incidente após política | PASS |
| Upgrade V4/V5 sem alterar legado | IncidentSchemaIT.java:192 uma migração, :193 zero reaplicações, :194–198 histórico/checksums/resultados/monitor iguais; :199–203 completed HISTORY_ONLY, pending não processado e incidentes0 | PASS |
| Fechado imutável e novo incidente | IncidentSchemaIT.java:76–78 antigo recoveredAt=60s, segundo aberto e total2; :150–151 fechado preserva início180s/fechamento240s após recoveryStarted(null); IncidentLifecycleIT.java:119–123 UUID novo e fechado igual | PASS |
| Snapshot incorreto não libera outra reserva | IncidentLifecycleIT.java:369 IllegalArgumentException; :370 monitor igual; :371 reserva original; :372 check inexistente; :373–374 zero aplicações/transições | PASS |
| FK composto e evidência correta | IncidentSchemaIT.java:103–106 FK rejeita ausente/outro monitor e monitor igual; :166–169 transição de snapshot incorreto rejeitada, zero transições/intenção | PASS |

## Gate Check

Execuções independentes em cópias de `git archive`, sem Maven/JDK no host:

- HEAD: `docker compose -p verifier-mon07-head run --build --rm tests`, exit0. XML: **138 unitários +218 IT =356; 0 falhas, 0 erros, 0 skips**. Zero Checkstyle.
- Baseline2179a4b: `docker compose -p verifier-mon07-base -f compose.yaml -f cache.yaml run --build --rm tests`, exit0. XML: **124 unitários +177 IT =301; 0 falhas, 0 erros, 0 skips**. Delta **+55**.
- Runtime: `docker compose -p verifier-mon07-head build app`, exit0; package e testes no Docker.
- Smoke: `powershell -NoProfile -ExecutionPolicy Bypass -File scripts/validate-compose.ps1 -NoBuild`, exit0; **23 PASSs**, V1–V5, readiness200/UP, credenciais, restart/down-up, sentinela4242 e runtime UID10001 sem Maven/javac/socket. Projeto descartável `mon02-smoke-54ec3a76af49`, limpo pelo finally.

Logs e XMLs em `.validation/verifier-mon07/`: `full-surefire/`, `full-failsafe/`, `baseline-surefire/`, `baseline-failsafe/`, `full-gate.log`, `baseline-gate.log`, `runtime-build.log`, `smoke-gate.log`, `smoke-evidence.log`. Testes Full foram preservados antes das mutações; cada mutação limpa reports e copia apenas sua própria execução.

## Discrimination Sensor

Profundidade de integridade: nove mutações comportamentais isoladas. Fonte/compilação/Checkstyle válidos; kills abaixo são falhas de assertions. Comandos reproduzíveis e diffs ficam em `sensor.py`, `sensor-extra.py`, `scratch-config-sensor-tests.sh` e em `<mutation>/mutation.diff`, `<mutation>/run.log` e XMLs por mutante. Unitários: `docker compose -p verifier-mon07-head run --build --rm tests sh config/sensor-tests.sh test`; persistência: mesmo comando com `verify`. Maven recebe `-Dtest=IncidentStateMachineTest -Dit.test=IncidentProcessorIT,IncidentSchemaIT` exclusivamente dentro do container.

| Mutação | Fonte real | Falha introduzida | Testes / assertions falhas / erros / skips | Resultado |
| --- | --- | --- | --- | --- |
| M1-threshold | `src/main/java/br/com/monitor/incident/IncidentStateMachine.java:59` | Limiar DOWN antecipado F-1 | 14 / 13 / 0 / 0 | KILLED por assertion; exit1 |
| M2-recovery | `src/main/java/br/com/monitor/incident/IncidentStateMachine.java:43` | Recuperação antecipada R-1 | 14 / 7 / 0 / 0 | KILLED por assertion; exit1 |
| M3-interruption | `src/main/java/br/com/monitor/incident/IncidentStateMachine.java:28` | Interrupção OR substituído por AND | 14 / 4 / 0 / 0 | KILLED por assertion; exit1 |
| M4-gap | `src/main/java/br/com/monitor/incident/IncidentStateMachine.java:26` | Lacuna passa a incluir igualdade | 14 / 1 / 0 / 0 | KILLED por assertion; exit1 |
| M5-idempotence | `src/main/java/br/com/monitor/incident/IncidentProcessor.java:34` | Replay retorna true indevidamente | 40 / 2 / 0 / 0 | KILLED por assertion; exit1 |
| M6-side-effect | `src/main/java/br/com/monitor/incident/IncidentProcessor.java:57` | Remove transição/intenção durável | 40 / 13 / 1 / 0 | KILLED por assertion; exit1 |
| M7-uniqueness | `src/main/resources/db/migration/V5__create_incident_history.sql:14` | Índice de aberto deixa de ser UNIQUE | 40 / 2 / 0 / 0 | KILLED por assertion; exit1 |
| M8-integrity | `src/main/java/br/com/monitor/incident/IncidentRepository.java:56` | firstFailureAt recebe downConfirmedAt | 40 / 3 / 0 / 0 | KILLED por assertion; exit1 |
| M9-replay-state | `src/main/java/br/com/monitor/incident/IncidentProcessor.java:34` | Replay apaga sequências persistidas | 40 / 2 / 0 / 0 | KILLED por assertion; exit1 |


**Result: PASS, 9/9 mortos; 0 sobreviventes.** `assertion-evidence.json` preserva tipos/mensagens das assertions: DOWN/NONE, RECOVERY/NONE, replay booleano, transições esperadas1/obtidas0, ausência de erro SQL de unicidade e timestamps divergentes. M9 mata a igualdade de snapshots após apagar contadores. M6 possui ainda um erro runtime de consulta de transição removida; suas 13 falhas de assertions sustentam o kill. Nenhuma falha de compilação/Checkstyle foi contabilizada como kill. Cada mutante foi restaurado antes do próximo.

## Code Quality e integridade

Mudanças de produção examinadas no diff: máquina pura, repositório JDBC, transação/lock, migração V5, administração e agendamento. Mantêm padrões existentes e introduzem somente as capacidades especificadas. Sem dependências novas ou APIs novas; intenção durável está prevista por AD-002. Guidelines: README.md, pom.xml, config/checkstyle.xml e .github/workflows/ci.yml. Qualidade automática: zero Checkstyle.

Os 55 novos casos executados pertencem a quatro suítes: StateMachine14 (sequências, classes, limites e lacuna; INC01/02/03/04/06/08), Schema6 (unicidade/FK/payload/migração; INC03/07/08), Processor20 (confirmação/replay/ordem/rollback/tempo; INC01–08) e Lifecycle15 (HTTP real/reinício/pausa/edição/reservas; INC01–08). Não há casos sem requisito, edge ou Done-when associado. Não há novas rotas; MonitorApiIT e demais suítes anteriores executaram integralmente.

Fixtures anteriores agora criam incidentes reais, TRUNCATE inclui filhos e assertions V4→V5/SUCCESS→UP correspondem aos contratos autorizados. Nenhum teste foi removido/desabilitado; nenhum skip executado. Assertions de igualdade de snapshot continuam completas.

Sensor somente em `.validation/verifier-mon07/scratch`, cópia de `git archive 197551d`; baseline em outra cópia de2179a4b. Sem stash. O diff das mutações está preservado; código real nunca foi alterado. Antes e após sensor/limpeza, `git status --porcelain` real vazio. Comparação final de todos os arquivos das cópias contra os ZIPs passou byte a byte, após restauração explícita de line endings dos quatro arquivos mutados. Registros em `isolation.json` e `porcelain-*.txt`. Cópias scratch/baseline removidas por PowerShell após validar paths absolutos dentro de `.validation`. Logs/XMLs/ZIPs preservados. Compose exclusivo do Verifier limpo; recursos do operador preservados. A única escrita real posterior ao baseline é este validation.md.

## Requirement Traceability e resultado

INC-01 a INC-08: Verified confirmado por este relatório. Spec/tasks não foram editadas pelo Verifier; rastreabilidade permanece no commit do autor. Backend: UAT humana não se aplica. Sem gaps ou fix tasks. PASS limpo não registra lesson. Validação estrutural final: `python .codex/skills/tlc-spec-driven/scripts/validate_state.py mon-07-incidentes`.
