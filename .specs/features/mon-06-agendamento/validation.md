# MON-06 Validation

**Verdict: PASS.** Oito ACs correspondem aos resultados definidos na spec. Gate independente: 301 testes aprovados, zero falhas/erros/skips. Cinco mutações comportamentais mortas, nenhuma sobrevivente.

**Date:** 2026-10-14
**Spec:** `.specs/features/mon-06-agendamento/spec.md`
**Diff range:** `1e0ba8e..a09f257`
**Verifier:** sub-agent independente `/root/verify_mon06`, distinto do autor. Leitura da spec, design, tasks, diff, implementação e testes; resultados abaixo executados novamente, sem assumir evidências do autor.

## Task Completion

T1–T4 estão marcadas Done. Reserva JDBC, coordenador, runtime e documentação correspondem às respectivas entregas. Build-level gate obrigatório concluído. Código/testes reais e traceability da spec não foram alterados pelo Verifier; requisitos já constam Verified.

## Spec-Anchored Acceptance Criteria

Os caminhos abaixo são relativos ao repositório; nomes curtos de testes referem-se a `src/test/java/br/com/monitor/`.

| AC | Resultado definido pela spec | File:line + assertion exata | Resultado |
| --- | --- | --- | --- |
| AC01 / SCHED-01 | Somente ACTIVE vencidos, igualdade elegível, ordem vencimento/UUID; reserva e próximo agora+I atômicos | `src/test/java/br/com/monitor/ScheduleStoreIT.java:69` `assertThat(attempt.monitor().id()).isEqualTo(first.id())`; :72 `nextCheckAt()).isEqualTo(now.plusSeconds(60))`; :74–78 segunda ID, fronteira, claim vazio, pausado/futuro preservados; :102–106 erro de INSERT mantém monitor original e nenhuma reserva | PASS |
| AC02 / SCHED-02 | Uma tentativa por monitor, inclusive rodadas simultâneas e pausa/retomada | `src/test/java/br/com/monitor/ScheduleStoreIT.java:89` `containsExactlyInAnyOrder(true, false)`; :91 `store.claim(now.plusSeconds(3600))).isEmpty()`; :171 claim após retomada vazio. `src/test/java/br/com/monitor/SchedulerRuntimeIT.java:152` `scheduler.inFlight()).isEqualTo(1)`; :153–155 apenas um HTTP/resultado e próximo inalterado na janela cleanup/saída | PASS |
| AC03 / SCHED-03 | Máximo C workers; excedente permanece vencido, sem fila/avanço | `src/test/java/br/com/monitor/CheckSchedulerTest.java:80` `scheduler.inFlight()).isEqualTo(2)`; :81 `assertThat(due).containsExactly(third)`; :82 só dois IDs executando. `src/test/java/br/com/monitor/SchedulerRuntimeIT.java:114` `assertThat(monitors.get(third.id())).isEqualTo(third)` preserva inteiro o excedente PostgreSQL | PASS |
| AC04 / SCHED-04 | Sucesso, falha, timeout e cancelamento liberam vaga após worker sair; próximo avança na rodada seguinte | `src/test/java/br/com/monitor/CheckSchedulerTest.java:105` `awaitIdle(Duration.ofSeconds(2))).isTrue()`; :106 `inFlight()).isZero()`; :109 outcome exato parametrizado SUCCESS/HTTP_FAILURE/TIMEOUT/CANCELLED/INTERNAL_ERROR; :115 resultados dos dois monitores na ordem. :139–146 interrupção observada, vaga continua 1 e resultado vazio até latch de saída. `src/test/java/br/com/monitor/SchedulerRuntimeIT.java:121` outcome TIMEOUT; :117 terceiro avança enquanto lento continua | PASS |
| AC05 / SCHED-05 | Restart reconcilia órfãs idempotentemente; resultado existente vence ou CANCELLED duração 0; preserva configuração/histórico/estado/incidente/próximo | `src/test/java/br/com/monitor/ScheduleStoreIT.java:124` `result.outcome()).isEqualTo(Outcome.CANCELLED)`; :126 duração zero; :132–139 configuração/estado DOWN/incidente/observação/próximo preservados, sequências provisórias zeradas; :140 um único resultado após duas recuperações; :141 reservas vazias; :159 `results.find(attempt.executionId())).contains(result)` preserva resultado existente. `src/test/java/br/com/monitor/SchedulerRuntimeIT.java:254` configuração preservada após reabrir contexto; :255–258 próximo/estado/incidente/sequência; :281–284 monitor/resultado/contagem/HTTP preservados no restart após gravação | PASS |
| AC06 / SCHED-06 | Atraso/restart produz uma tentativa, próximo início lógico+I; nenhum replay | `src/test/java/br/com/monitor/ScheduleStoreIT.java:144` segundo claim no mesmo agora vazio; :146 próximo `now.plusSeconds(660)` após atraso de 600s. `src/test/java/br/com/monitor/SchedulerRuntimeIT.java:263` `count(monitor.id())).isEqualTo(2)` (CANCELLED anterior + uma execução); :264 `healthyHits.get()).isEqualTo(1)`; :265 próximo `clock.instant().plusSeconds(60)` após rodada adicional | PASS |
| AC07 / SCHED-07 | Idade >2I+T implica STALE/recentObservation=false; nenhum sucesso/falha inventado; fronteira inclusiva | `src/test/java/br/com/monitor/SchedulerRuntimeIT.java:315` FRESH exatamente em 125s (I=60,T=5); :316 recentObservation true; :320 `stale.path("evidence").asText()).isEqualTo("STALE")`; :321 recentObservation false; :322 estado UP preservado; :323 contagem de resultados zero; :324 HTTP zero. `src/test/java/br/com/monitor/MonitorTest.java:95` limite FRESH; :96 limite+1ns STALE (mais estrito que +1µs); :97 timestamp futuro STALE; :98 observação inválida NEVER_OBSERVED | PASS |
| AC08 / SCHED-08 | Pausa/política invalida e solicita interrupção; resultado tardio somente histórico; preserva estado/incidente e horário administrativo | `src/test/java/br/com/monitor/CheckSchedulerTest.java:139` latch de interrupção true; :141–146 vaga preservada até saída e CANCELLED gravado. `src/test/java/br/com/monitor/ScheduleStoreIT.java:185` `monitors.find(monitor.id(), false)).contains(paused)` após resultado SUCCESS tardio; :186 SUCCESS no histórico. `src/test/java/br/com/monitor/SchedulerRuntimeIT.java:177` monitor pausado inteiro preservado; :178–180 CANCELLED/DOWN/incidente; :189–192 próximo novo/geração nova/estado preservados; :205 monitor editado preservado; :212–216 histórico versão 1 CANCELLED, versão 2 SUCCESS, estado UNKNOWN e sem observação corrente | PASS |

**Status:** 8/8 ACs cobertos com resultados específicos; nenhuma lacuna de precisão identificada. Estado e incidente usados nestas verificações são fixtures administrativas persistidas; processamento de observações e incidentes reais permanece MON-07.

## Gate Check

| Comando executado pelo Verifier | Resultado independente |
| --- | --- |
| `docker compose run --build --rm tests` | Exit 0, BUILD SUCCESS, 124 unitários +177 IT =301; zero falhas, erros ou skips; Checkstyle 0. Tempo Maven 02:49 min; término 2026-10-14T13:12:46Z |
| `docker compose build app` | Exit 0; imagem `monitor-disponibilidade:local` construída, layers em cache |
| `powershell -NoProfile -ExecutionPolicy Bypass -File scripts/validate-compose.ps1 -NoBuild` | Exit 0, PASS Compose lifecycle; stack temporária `mon02-smoke-696760cd0abf` e volume removidos pelo finally |

Contagens reconfirmadas diretamente em `target/surefire-reports/TEST-*.xml` e `target/failsafe-reports/TEST-*.xml`: CheckSchedulerTest 14, ScheduleStoreIT 7, SchedulerRuntimeIT 16. Os demais 264 casos passaram. Delta +37 em relação ao baseline 264 (110 unitários/154 IT), sem testes removidos ou assertions enfraquecidas. Logs de startup inválido e autenticação falha durante o gate pertencem a casos negativos; XMLs confirmam zero erros de testes. Smoke local: `.validation/mon02-smoke-696760cd0abf.log` (ignorado pelo Git).

## Discrimination Sensor

**Profundidade:** cinco falhas comportamentais, incluindo seleção, exclusão mútua, capacidade, recuperação e agendamento. Cada tentativa iniciou um contêiner efêmero novo da imagem source original. `sed -i` alterou somente `/workspace/src/main/java/...` dentro dele. Nenhum mount de fontes reais, nenhum stash, nenhuma mudança em testes. Script transitório `.validation/sensor.sh` montado somente leitura e removido ao concluir.

Comando de cada tentativa: `docker compose run --rm --volume "<workspace>/.validation:/sensor:ro" tests sh /sensor/sensor.sh <mutation>`. Dentro do contêiner: `mvn --batch-mode --no-transfer-progress -Dcheckstyle.skip -Dtest=CheckSchedulerTest -Dit.test=ScheduleStoreIT verify`. Checkstyle ignorado somente no sensor para distinguir comportamento; gate real já aprovou qualidade. O wrapper imprime o exit Maven e devolve 0 para permitir coletar relatório; os cinco exits Maven foram **1**. Todas as fontes compilaram e os testes chegaram a executar.

| Mutação | Source file:line | Falha observada que mata a mutação | Resultado |
| --- | --- | --- | --- |
| selection: `next_check_at<=:now` → `<:now` | `src/main/java/br/com/monitor/administration/MonitorRepository.java:55` | ScheduleStoreIT:89 esperava true/false nas rodadas concorrentes, recebeu false/false; :75 fronteira deixou de retornar reserva. 7 IT: 1 failure/4 errors; 14 unit PASS | KILLED |
| overlap: predicado `a.monitor_id=m.id` → `false`, desativando exclusão de reserva existente | `src/main/java/br/com/monitor/administration/MonitorRepository.java:56` | ScheduleStoreIT:91 e :171 tentaram INSERT de segunda reserva e falharam com DuplicateKey/check_attempts_pkey em vez de retornar vazio. 7 IT: 0 failures/2 errors; 14 unit PASS. Erros de integridade observados em runtime são falhas comportamentais, não de compilação | KILLED |
| capacity: `running.size() < capacity` → `<= capacity` | `src/main/java/br/com/monitor/monitoring/CheckScheduler.java:46` | CheckSchedulerTest:81 excedente deixou de permanecer pendente; :142 retomado deixou de permanecer pendente. 14 unit: 2 failures/0 errors, verify interrompido antes das IT | KILLED |
| recovery: `Outcome.CANCELLED` → `Outcome.INTERNAL_ERROR` | `src/main/java/br/com/monitor/monitoring/ScheduleStore.java:90` | ScheduleStoreIT:124 esperava CANCELLED, recebeu INTERNAL_ERROR. 7 IT: 1 failure/0 errors; 14 unit PASS | KILLED |
| scheduling: `intervalSeconds()` → `intervalSeconds() + 1` no próximo horário | `src/main/java/br/com/monitor/monitoring/ScheduleStore.java:51` | ScheduleStoreIT:72/:94 esperavam 12:01:00Z, receberam 12:01:01Z; :146 esperava 12:11:00Z, recebeu 12:11:01Z. 7 IT: 3 failures/0 errors; 14 unit PASS | KILLED |

**Resultado:** 5/5 killed, 0 survived. Baseline `git status --porcelain` vazio antes do sensor; vazio após todos os contêineres descartados e remoção do script. Depois da escrita deste relatório, somente `?? .specs/features/mon-06-agendamento/validation.md` é esperado. Git diff real sem alterações em implementação/testes.

## Edge Cases and Code Quality

- Igualdade, futuro, pausa e empate UUID: ScheduleStoreIT:61–79; rollback: :98–106; claims simultâneos: :83–94.
- Janela cleanup/worker: SchedulerRuntimeIT:131–162; limite/excedente/servidor lento e saudável/timeout: :103–127; todas as saídas: CheckSchedulerTest:97–116.
- Pausa/retomada, política e edição somente nome: SchedulerRuntimeIT:166–234; reserva antiga preservada até worker sair: CheckSchedulerTest:120–149.
- Restart antes do HTTP e após resultado: SchedulerRuntimeIT:238–285; recuperação repetida/contadores sem reserva: ScheduleStoreIT:110–161.
- Exceção interna, claim falho, persistência falha e reconciliação sem inventar evidência: CheckSchedulerTest:154–195; falha SQL real de gravação: SchedulerRuntimeIT:289–304.
- Frescor inclusivo, timestamp futuro e nenhuma observação: MonitorTest:22/95–98 e API runtime:315–324. Sem nova rota em escopo; fronteira Basic/CSRF coberta pela regressão completa.
- C inválido/poll não positivo impede startup: SchedulerRuntimeIT:359–364; shutdown real registra CANCELLED antes de fechar datasource: :347–355; upgrade V3→V4 idempotente preserva configuração, resultado e migrações: :368–390.

Mudanças permanecem no escopo: três componentes pequenos reutilizam Clock, JDBC, TransactionTemplate, CheckService e frescor existente. Reserva/horário usam a mesma transação e lock administrativo, HTTP fica fora dela; mapa tem limite C e nenhuma fila de workers. Remoção do mapa ocorre em finally após gravação. Recuperação preserva resultado existente por UUID. Poll tem limite configurável documentado e mensagens operacionais fixas. Migração mantém FK/UUID únicos e índice de vencidos.

Guidelines verificadas: `pom.xml`, `config/checkstyle.xml`, `README.md`, `docs/scheduling.md` e `.codex/skills/tlc-spec-driven/references/coding-principles.md`. Regressões alteradas ajustam somente V4, TRUNCATE com a FK nova e fixtures anteriores com polling desabilitado; nenhuma assertion foi afrouxada. Teste V2→V3 permanece explicitamente target 3 e a nova IT cobre V3→V4. Todos os 37 casos novos correspondem aos ACs ou edges/done-when acima, incluindo os parâmetros de outcomes/configuração. Sem abstração/configuração não solicitada, alteração alheia ou SPEC_DEVIATION identificada.

Backend: UAT interativo não aplicável. Escopo de validação local, instância única; nenhum push/deploy ou banco do operador foi utilizado. O sensor é dirigido e não equivale a uma campanha de mutação exaustiva.

## Ranked Gaps and Lessons

Nenhum gap impeditivo ou sobrevivente identificado. Não há fix tasks. PASS sem sinal de falha/precisão/desvio: nenhuma lição adicionada.

**Overall: PASS.** SCHED-01 a SCHED-08 verificados; relatório pronto para o gate estrutural `validate_state.py mon-06-agendamento`.
