# MON-11: evidências

## Fechamento independente — PASS

`validation.md` registra o parecer do Verifier sobre `6fb6cf0..d3e0fef`: MON-11-AC01..AC06 aprovados, nenhum gap aberto. O relatório inicial permanece em `validation-initial.md`; F1 foi resolvido por T10. A versão final detecta sete mutantes distintos: seis execuções anteriores reutilizadas por equivalência explícita e M7 repetido independentemente, com falha SQL expected1/actual4 e zero erros de infraestrutura. O isolamento e a remoção do scratch foram confirmados.

Gates repetidos pelo Verifier: `docker compose run --build --rm tests` (482 Java,169 unitários+313 IT, zero falhas/erros/skips), `docker compose build app` e `powershell -NoProfile -ExecutionPolicy Bypass -File scripts/validate-compose.ps1 -NoBuild` (37 PASS). Logs `.validation/mon11-reverify-java.log`, `-runtime.log`, `-smoke.log` e `-M7.log`. UI50 e capacidade foram reutilizados pela equivalência de código descrita no parecer, sem alegar nova execução.

O fechamento posterior altera somente documentação: status Verified, backlog, handoff, parecer e restauração de acentos em artefatos. `validate_spec.py`, `validate_tasks.py`, `validate_state.py mon-11-operacao` e `git diff --check` aprovados; único aviso esperado: documentação T7 usa Tests:none. Nenhuma alteração posterior de produção/testes. CI remota não executada.

## T10: uma rodada de limpeza, sem polls compensatorios

F1 do Verifier inicial permanece em `validation-initial.md`. O teste agora chama `RetentionRuntime.tick()` uma unica vez, sem `start` nem await. As assertions SQL imediatas em `RetentionIT.java:147-148` exigem um resultado protegido e cinco recibos. O teste de shutdown entre commits permanece. Nenhum teste foi removido ou ignorado.

Gate direcionado: `docker compose run --build --rm tests sh -c 'mvn --batch-mode --no-transfer-progress verify -Dtest=OperationMetricsTest -Dit.test=RetentionIT,RetentionMigrationIT,NotificationSchemaIT; result=$?; cp -a target/. /reports/; exit $result'`: 2 unitarios +14 IT, zero falhas/erros/skips/Checkstyle; `.validation/mon11-t10.log`.

Sensor autor em copia isolada: trocar somente a continuacao por `while (false)` produziu 12 IT, uma falha, zero erros; `oneRuntimeRoundDrainsEligibleBacklogAcrossShortTransactions` esperava1 resultado, encontrou4. M7 morto por estado SQL, sem falha de compilacao/infraestrutura (`.validation/mon11-t10-M7.log`). Porcelain antes/depois identico apos normalizar CRLF/LF; scratch removido sem stash. O Verifier deve repetir este sensor independentemente.

Adequacao A-D: OPS-04/T9/T10 mapeiam diretamente as duas contagens e o teste de shutdown. As assertions existentes foram preservadas e seu escopo temporal foi fortalecido; nenhuma mudanca na elegibilidade SQL. Capacidade e UI anteriores permanecem aplicaveis: runtime de retencao desabilitado no cenario temporizado, DTOs/assets inalterados. A extracao segue o padrao de tick do runtime de notificacoes.

Gate completo T10: `docker compose run --build --rm tests`, `.validation/mon11-t10-full.log`: BUILD SUCCESS, 482 testes (169 unitarios +313 IT), zero falhas/erros/skips/Checkstyle. O Verifier deve repetir o gate independentemente.

## T1: saúde e métricas

Premissas: probes status-only existentes; instrumentação no scheduler real; outbox consultada no scrape. Arquivos: CheckScheduler, SchedulerRuntime, NotificationMetrics, application.yml e OperationMetricsTest/OperationHealthIT. Sucesso: métricas exatas e readiness independente de liveness.

Comando: `docker compose run --build --rm tests mvn --batch-mode --no-transfer-progress verify -Dtest=OperationMetricsTest -Dit.test=OperationHealthIT`. Exit 0, 2 unitários e 1 IT, zero falhas/erros/skips, Checkstyle zero. Log `.validation/mon11-t1.log`. Correções durante desenvolvimento: quebra de linha Checkstyle e fechamento explícito do registro Micrometer (não AutoCloseable); sem mudança de assertions.

| AC / mapeamento direto e reverso | Evidência e assertion | Resultado da spec |
| --- | --- | --- |
| OPS-01/AC01 | OperationHealthIT.java:36 `isEqualTo(503)` e :39 `isEqualTo(200)` | readiness DOWN, liveness UP sem DB |
| OPS-01/02 acesso | OperationHealthIT.java:30 `isEqualTo(401)` e :32 `isEqualTo(200)` | métricas administrativas |
| OPS-02/AC02 concorrência | OperationMetricsTest.java:51 `isEqualTo(1)` e :52 `isEqualTo(4)` | ocupação real e capacidade |
| OPS-02 duração/atraso/classificação | OperationMetricsTest.java:58 `isEqualTo(3)`, :60 `isEqualTo(125)`, :62 `isEqualTo(1)` | segundos/ms/contagem exatos |
| OPS-02 cardinalidade | OperationMetricsTest.java:63 `hasSize(21)`, :65 `allMatch` | tags somente nove enums |
| OPS-02 outbox/erro | OperationMetricsTest.java:76 `isEqualTo(7)`, :79 `isNaN()` | gauge corrente, falha não vira zero |

Adequação A–D: todos os três testes têm vínculo acima, valores esperados correspondem ? spec, sem assertions rasas ou testes removidos/pulados. Diretrizes pom.xml/Checkstyle e padrões JUnit existentes.

## T2: eventos confirmados e JSON

Premissas: eventos de sucesso só após commit; nenhuma mensagem externa entra no payload. Arquivos: OperationalEvents, CheckRepository, IncidentRepository, NotificationStore, OperationalLogsIT e OperationHealthIT. Resultado: quatro IDs atravessam transição e entrega sem incluir snapshot sensível.

Gates Docker: `.validation/mon11-t2.log` (2 unitários +33 IT: IncidentProcessorIT, NotificationStoreIT e OperationalLogsIT), `.validation/mon11-t2-final.log` (2 unitários +2 IT: OperationalLogsIT e OperationHealthIT). Ambos BUILD SUCCESS, zero falhas/erros/skips/Checkstyle. Comando final: `docker compose run --build --rm tests sh -c 'mvn --batch-mode --no-transfer-progress verify -Dtest=OperationMetricsTest -Dit.test=OperationalLogsIT,OperationHealthIT; result=$?; cp -a target/. /reports/; exit $result'`.

| AC / mapeamento direto e reverso | Evidência e assertion | Resultado |
| --- | --- | --- |
| OPS-03 correlação | OperationalLogsIT.java:39..44 `containsEntry` dos quatro UUIDs reais | Transição, reserva e conclusão correspondem ? execução |
| OPS-03 sanitização | OperationalLogsIT.java:45..50 `getThrowableProxy().isNull()`, `doesNotContain` e allowlist de campos | Sem snapshot, URL ou exceção |
| OPS-03 rollback | OperationalLogsIT.java:65 `hasSize(4)` após rollback | Nenhuma confirmação falsa |
| OPS-03 formato JSON | OperationHealthIT.java:59..64 `isEqualTo` dos quatro UUIDs e INTERNAL_ERROR | Saída ECS real parseada por Jackson |

Adequação A–D PASS: assertions de payload, valores derivados da spec e reversão real. Ajuste do coletor de teste para preservar chaves com valor null, sem mudar assertions. Não houve teste apagado/pulado/enfraquecido.

## T3: retenção transacional

Premissas/arquivos: RetentionService/Runtime, V8, CheckRepository e IncidentProcessor; configuração Compose/YAML; RetentionIT/MigrationIT e contagens atuais em ApplicationIT/MonitorRepositoryIT/smoke. V7 histórico isolado explicitamente em ObservationEpochIT para preservar seu contrato; assertions existentes mantidas, contagem atual elevada de 7 para 8 e assertion da versão 8 adicionada. V1?V7 intactas. Deduplicação de dados removidos usa trigger V8 e recibos mínimos sem TTL.

Gate `docker compose run --build --rm tests sh -c 'mvn --batch-mode --no-transfer-progress verify -Dtest=OperationMetricsTest -Dit.test=RetentionIT,RetentionMigrationIT,ObservationEpochIT,MonitorRepositoryIT,ApplicationIT,SchedulerRuntimeIT,IncidentSchemaIT; result=$?; cp -a target/. /reports/; exit $result'`: **2 unitários +45 IT, zero falhas/erros/skips/Checkstyle**, BUILD SUCCESS em 1:04 min. Log `.validation/mon11-t3-final.log`.

| AC / mapeamento direto e reverso | Evidência | Resultado esperado |
| --- | --- | --- |
| OPS-04 corte/replay | `src/test/java/br/com/monitor/RetentionIT.java:35` `assertThat(checks.retired(old.executionId())).isTrue();` | Recibo durável; replay rejeitado |
| OPS-04 igualdade | `src/test/java/br/com/monitor/RetentionIT.java:33` `assertThat(checks.find(exact.executionId())).contains(exact);` | Igualdade não removida |
| OPS-04 pendentes | `src/test/java/br/com/monitor/RetentionIT.java:57` `assertThat(count("notification_intents")).isEqualTo(3);` | Toda cadeia pendente preservada |
| OPS-04 terminal | `src/test/java/br/com/monitor/RetentionIT.java:83` `assertThat(retention(base.plusNanos(1000), 100).clean().incidents()).isEqualTo(1);` | Igualdade preservada; +1às remove |
| OPS-04 continuidade | `src/test/java/br/com/monitor/RetentionIT.java:107` `assertThat(incidents.lastAppliedStart(internal)).contains(internalAt);` | último APPLIED interno e observação preservados |
| OPS-04 reserva | `src/test/java/br/com/monitor/RetentionIT.java:120` `assertThat(count("check_attempts")).isEqualTo(1);` | Reserva mantida |
| OPS-04 lote/tempo | `src/test/java/br/com/monitor/RetentionIT.java:131` `assertThat(count("retired_executions")).isEqualTo(5);` | Lote limitado e watermark monotônico |
| OPS-04 épocas | `src/test/java/br/com/monitor/RetentionIT.java:144` `assertThat(count("observation_epochs")).isEqualTo(2);` | época referenciada e corrente preservadas |
| OPS-04 rollback | `src/test/java/br/com/monitor/RetentionIT.java:158` `assertThat(count("retired_executions")).isZero();` | Falha reverte recibos, aplicações, payload e watermark |
| OPS-04 configuração | `src/test/java/br/com/monitor/RetentionIT.java:176` `.hasMessage("Invalid retention configuration");` | Erro genérico para configuração inválida |
| OPS-04 upgrade | `src/test/java/br/com/monitor/RetentionMigrationIT.java:25` `assertThat(current.migrate().migrationsExecuted).isEqualTo(1);` | V8 aplicada uma vez, checksums e dados anteriores intactos |

Adequação A–D PASS: dez cenários de retenção e um upgrade derivam de OPS-04 e edges; gates incluem regressão dos módulos afetados. Nenhum teste removido/pulado nem assertion enfraquecida.

## T4: consultas e limite histórico

Premissas/arquivos: HistoryQueries/SummaryQueries e RetentionQueriesIT/RetentionQueryApiIT. historyAvailableFrom ? global e durável; retainedOutPeriods ? a interseção da janela com o período anterior ao limite. noObservationPeriods contém somente lacunas posteriores ao corte. Incidentes/notificações protegidos continuam consultáveis com aviso de histórico parcial; amostras anteriores não entram em checks/estatísticas/cobertura.

Gates Docker com verify, Checkstyle e cópia de relatórios: `.validation/mon11-t4.log`, 53 IT +23 unitários QueryParametersTest; `.validation/mon11-t4-api.log`, 6 IT +23 unitários. BUILD SUCCESS, zero falhas/erros/skips. Seleções IT: RetentionQueriesIT,HistoryQueriesIT,SummaryQueriesIT,QueryApiIT; final RetentionQueriesIT,RetentionQueryApiIT.

| AC / mapeamento direto e reverso | Evidência e assertion | Resultado |
| --- | --- | --- |
| OPS-05 janela removida | `src/test/java/br/com/monitor/RetentionQueriesIT.java:33` `assertThat(summary.statistics().dataStatus()).isEqualTo("NO_DATA");` | NO_DATA/null/zero amostras |
| OPS-05 dados operacionais | `src/test/java/br/com/monitor/RetentionQueriesIT.java:29` `assertThat(summary.lastObservation().executionId()).isEqualTo(result.executionId());` | última evidência operacional separada da janela |
| OPS-05 janela cruzada | `src/test/java/br/com/monitor/RetentionQueriesIT.java:60` `assertThat(summary.retainedOutPeriods()).containsExactly(new SummaryQueries.Period(q.from(), base));` | Retenção separada de lacuna |
| OPS-05 cadeia protegida | `src/test/java/br/com/monitor/RetentionQueriesIT.java:74` `assertThat(notificationPage.retainedOutPeriods()).isEqualTo(summary.retainedOutPeriods());` | Pendência preservada com metadados |
| OPS-05 cobertura | `src/test/java/br/com/monitor/RetentionQueriesIT.java:87` `assertThat(summary.noObservationPeriods()).containsExactly(new SummaryQueries.Period(q.from(), q.to()));` | Amostra antiga não atravessa o corte |
| OPS-05 antes da limpeza | `src/test/java/br/com/monitor/RetentionQueriesIT.java:96` `assertThat(summary.historyAvailableFrom()).isNull();` | Contrato anterior preservado |
| OPS-05 HTTP quatro recursos | `src/test/java/br/com/monitor/RetentionQueryApiIT.java:58` `assertThat(json.path("historyAvailableFrom").asText()).isEqualTo(base.toString());` | Corte UTC exato nos quatro DTOs |
| OPS-05 HTTP ausência | `src/test/java/br/com/monitor/RetentionQueryApiIT.java:64` `assertThat(json.at("/statistics/successRatePercent").isNull()).isTrue();` | Ausência não ? sucesso |

Adequação A–D PASS: seis cenários novos derivados de OPS-05, incluindo serialização HTTP real, mais regressão MON-09. Campos de payload verificados por valor, sem teste anterior removido/pulado/enfraquecido.

## T5: retenção no painel

Premissas/arquivos: details.js e retention.spec.js; apenas apresentar metadados do backend, com datas UTC preservadas no atributo datetime. Backend permanece responsável pelos cálculos. Gate `docker compose -p mon11-panel --profile panel-tests run --use-aliases --build --rm panel-tests`: **50/50 PASS**, zero falhas/skips, 1.6 min; `.validation/mon11-t5-panel.log`, `.validation/panel/junit.xml`. Runtime recompilado pelo Docker com 169 unitários e Checkstyle zero.

| AC / mapeamento direto e reverso | Evidência e assertion | Resultado |
| --- | --- | --- |
| OPS-05 limite nas quatro seções | config/panel/retention.spec.js:27 `toBeVisible()` e :29 `toHaveAttribute('datetime', cutoff)` | Corte exato da API, sem recalcular |
| OPS-05 aviso condicional | config/panel/retention.spec.js:31 `toHaveCount(removed ? 1 : 0)` | Aviso somente quando a janela cruza o corte |
| OPS-05 ausência | config/panel/retention.spec.js:33..35 `toHaveText('Sem dados')`, `toContainText('0 amostras externas')` | Retenção não vira sucesso/zero percentual |

Adequação A–D PASS: dois casos novos derivam de OPS-05; 48 cenários MON-10 preservados. Nenhuma alteração em assertions anteriores.

## T6: capacidade controlada

Premissas/arquivos: CapacityScenario, perfil Maven capacity, compose.capacity.yaml e docs/capacity. HTTP e SMTP reais locais, banco Testcontainers próprio; sem mocks de rede. Instância única, 100 monitores, I=10s/T=2s/F=1/R=1/C=4, quatro grupos de 25. Relógio real por 60s; aceleração só depois do período medido para verificar esgotamento e retenção.

Comando: `docker compose -p mon11-capacity -f compose.yaml -f compose.capacity.yaml run --build --rm tests`. Exit 0, 169 unitários +1 cenário, zero falhas/erros/skips/Checkstyle, BUILD SUCCESS. Resultado completo versionado em docs/capacity/mon-11.json e análise em docs/capacity/README.md. Logs `.validation/mon11-t6-capacity.log` e amostra `.validation/mon11-t6-resources.log`.

Resultado: 287 checks/60,265065594s; pico 4, sobreposição 0; SUCCESS144/HTTP_FAILURE72/TIMEOUT69/CANCELLED2 no shutdown. Atraso máximo20,377306s e médio10,860850498s. SMTP421 real: 50 PENDING no pico/término; 50 EXHAUSTED após avanço do relógio; máximo cinco tentativas e 250 conexões totais. Limpeza avançada31dias retirou135 checks e preservou50 incidentes abertos. Relatório explicita saturação: essa mistura não sustentou I=10s, sem prometer SLA.

| AC / mapeamento direto e reverso | Evidência e assertion | Resultado esperado |
| --- | --- | --- |
| OPS-06 limite de monitores | `src/test/java/br/com/monitor/CapacityScenario.java:101` `error -> assertThat(error.code()).isEqualTo("MONITOR_LIMIT"));` | 101? rejeitado MONITOR_LIMIT |
| OPS-06 duração | `src/test/java/br/com/monitor/CapacityScenario.java:161` `assertThat(duration).isGreaterThanOrEqualTo(60);` | Pelo menos 60s reais |
| OPS-02/06 concorrência | `src/test/java/br/com/monitor/CapacityScenario.java:162` `assertThat(peak).isEqualTo(4);` | Pico4 e nunca acima4 |
| OPS-06 sobreposição | `src/test/java/br/com/monitor/CapacityScenario.java:163` `assertThat(overlaps.get()).isZero();` | Zero por monitor |
| OPS-06 reserva | `src/test/java/br/com/monitor/CapacityScenario.java:166` `assertThat(count(jdbc, "check_attempts")).isZero();` | Zero após shutdown |
| OPS-02/06 backlog | `src/test/java/br/com/monitor/CapacityScenario.java:172` `assertThat(pendingPeak).isEqualTo(50);` | 50 pendentes |
| OPS-02/06 atraso | `src/test/java/br/com/monitor/CapacityScenario.java:171` `assertThat(lag.max(TimeUnit.SECONDS)).isGreaterThan(0);` | Atraso medido positivo |
| OPS-02/06 esgotamento | `src/test/java/br/com/monitor/CapacityScenario.java:186` `assertThat(meters.get("monitor.notifications").tag("status", "EXHAUSTED").gauge().value()).isEqualTo(50);` | 50 EXHAUSTED com cinco tentativas |
| OPS-04/06 preservação | `src/test/java/br/com/monitor/CapacityScenario.java:194` `assertThat(count(jdbc, "incidents WHERE recovered_at IS NULL")).isEqualTo(50);` | 50 incidentes abertos após retenção |

Adequação A–D PASS para o cenário: resultados e medições persistidas, sem testes fora dos seis ACs. Gate Build completo aprovado antes do commit desta task (fechamento abaixo).

## T8: compatibilidade do processamento no upgrade histórico

O primeiro gate completo de T6 executou480 testes:479 passaram e NotificationSchemaIT falhou porque IncidentProcessor consultava recibos V8 antes de ler um resultado existente em V5. Evidência preservada em `.validation/mon11-java-before-t8.log`. Corrigido para consultar recibos somente quando a evidência estiver ausente; o fluxo normal faz uma consulta a menos e o replay retirado continua ignorado. Nenhuma assertion de teste alterada.

Gate Docker verify `-Dtest=OperationMetricsTest -Dit.test=NotificationSchemaIT,RetentionIT`, com cópia de relatórios: **2 unitários +11 IT, zero falhas/erros/skips/Checkstyle**, BUILD SUCCESS; `.validation/mon11-t8.log`.

| AC / mapeamento direto e reverso | Evidência e assertion | Resultado |
| --- | --- | --- |
| OPS-04 continuidade no upgrade | src/test/java/br/com/monitor/NotificationSchemaIT.java:53 `isEqualTo(1)` e preservação do snapshot V5 | Upgrade V5?V6 passa com processador atual |
| OPS-04 replay removido | src/test/java/br/com/monitor/RetentionIT.java:34 `processor.process(old.executionId()).isFalse()` | Nenhuma reaplicação de execução retirada |

Adequação A–D PASS; regressão encontrada pelo gate e corrigida em produção, sem enfraquecer testes. T6 repetirá cenário/gates sobre esta correção antes de fechar.

## T9: drenagem e interrupção entre lotes

Revisão do cenário identificou que um único lote1000/hora elimina somente24mil linhas/dia, abaixo da entrada possível. RetentionRuntime agora continua lotes curtos até esvaziar elegibilidade, liberando locks entre commits e respeitando interrupção. RetentionService informa more quando qualquer grupo preenche o lote. Arquivos limitados ao componente de retenção e seus testes derivados de OPS-04.

Gate Docker verify `-Dtest=OperationMetricsTest -Dit.test=RetentionIT,RetentionMigrationIT,NotificationSchemaIT`: **2 unitários +14 IT**, zero falhas/erros/skips/Checkstyle, BUILD SUCCESS; `.validation/mon11-t9-final.log`.

| AC / mapeamento direto e reverso | Evidência e assertion | Resultado |
| --- | --- | --- |
| OPS-04 | `src/test/java/br/com/monitor/RetentionIT.java:135` `assertThat(count("check_results")).isEqualTo(1);` | Rodada drena cinco antigos e preserva último APPLIED |
| OPS-04 | `src/test/java/br/com/monitor/RetentionIT.java:182` `assertThat(count("check_results")).isEqualTo(4);` | Shutdown após primeiro lote impede lotes seguintes |
| OPS-04 | `src/test/java/br/com/monitor/RetentionIT.java:183` `assertThat(count("retired_executions")).isEqualTo(2);` | Primeiro lote permanece confirmado |
| OPS-04 | `src/test/java/br/com/monitor/RetentionIT.java:184` `assertThat(count("result_applications")).isEqualTo(4);` | Aplicações restantes preservadas |

Adequação A–D PASS: duas IT novas com estado SQL real e Clock fixo; barreira permite interromper após commit, sem sleeps de fronteira temporal. Não há teste anterior enfraquecido. A fase temporizada de capacidade não executa RetentionRuntime; critérios SQL da transação são idênticos antes/depois de T9, apenas o flag more foi acrescido.

## T6: repetição final após T8

`.validation/mon11-t6-capacity-final.log`: BUILD SUCCESS, 169 unitários +1 cenário. **285 checks em60,070333095s**, todos100 monitores, pico4, sobreposição0; SUCCESS143/HTTP_FAILURE71/TIMEOUT69/CANCELLED2. Atraso máximo20,634817s, médio10,967730218s. Pendências50, esgotadas50 após relógio acelerado, 250 conexões SMTP, 133 checks retirados após31dias. Os números iniciais acima pertencem ? primeira execução; o relatório versionado docs/capacity/mon-11.json contém esta repetição final.

T9 altera somente a drenagem do runtime de retenção, desabilitado no cenário, e acresce o flag more ao retorno da limpeza sem modificar seus critérios SQL; a fase temporizada usa o mesmo código final. O README de capacidade explicita o custo do harness (tick/coleta/SMTP seguidos por250ms) e que produção usa pollers separados. A medição não ? apresentada como SLA ou throughput universal.

## Gate Build de T6 após T8/T9

- `docker compose run --build --rm tests`: **482/482 Java**,169 unitários +313 IT, zero falhas/erros/skips/Checkstyle; `.validation/mon11-final-java.log`, XMLs em target.
- `docker compose build app`: exit0, imagem runtime final construída; `.validation/mon11-final-runtime.log`.
- `powershell -NoProfile -ExecutionPolicy Bypass -File scripts/validate-compose.ps1 -NoBuild`: exit0,37 PASS; `.validation/mon11-final-smoke.log`, `.validation/mon02-smoke-e183d474e02f.log`. Infraestrutura própria removida.
- Painel T5:50 PASS; alterações T8/T9 são exclusivamente retenção/processamento interno, sem alterar DTOs, segurança ou assets testados. Não se apresenta essa equivalência como outra execução UI.

Adequação T6 completa: limites, medições, rollback/replay, threads e consultas cobertos; comandos reais, nenhum teste suprimido e nenhuma assertion anterior enfraquecida.

## T7: documentação operacional e matriz atual

Arquivos: docs/operations.md, README, .env.example, docs/queries.md, STATE/AD-012 e artefatos MON-11. Sem mudança em produção após os gates finais de T6. Guia confrontado com configuração YAML/Compose, endpoints exercitados pelas IT/smoke, tabelas migradas e resultados reais. Gates Java482/UI50/runtime/smoke37 acima são reutilizados por identidade de código; não foi alegada nova execução para alteração puramente documental. Spec/tasks e diff-check passam. O backlog só será fechado após Verifier PASS.

A matriz abaixo usa as linhas atuais e prevalece sobre referências das etapas nos commits anteriores. Toda assertion nova está limitada a OPS-01..06 e seus edges; não houve remoção, skip ou enfraquecimento de testes anteriores.

| AC / mapeamento direto e reverso | Assertion concreta atual | Resultado definido |
| --- | --- | --- |
| MON-11-AC01 / OPS-01 | `src/test/java/br/com/monitor/OperationHealthIT.java:60` `assertThat(readiness.statusCode()).isEqualTo(503);` | 503/readiness DOWN |
| MON-11-AC01 / OPS-01 | `src/test/java/br/com/monitor/OperationHealthIT.java:63` `assertThat(liveness.statusCode()).isEqualTo(200);` | 200/liveness UP sem PostgreSQL |
| MON-11-AC02 / OPS-02 | `src/test/java/br/com/monitor/OperationMetricsTest.java:49` `assertThat(meters.get("monitor.checks.in.flight").gauge().value()).isEqualTo(1);` | Gauge real durante execução |
| MON-11-AC02 / OPS-02 | `src/test/java/br/com/monitor/OperationMetricsTest.java:61` `assertThat(meters.getMeters().stream().flatMap(m -> m.getId().getTags().stream())) .allMatch(tag -> tag.getKey().equals("outcome") && java.util.Arrays.stream(CheckResult.Outcome.values()) .anyMatch(value -> value.name().equals(tag.getValue())));` | Somente enum outcome, sem IDs/URLs |
| MON-11-AC02 / OPS-02 | `src/test/java/br/com/monitor/OperationMetricsTest.java:79` `assertThat(meters.get("monitor.notifications").tag("status", status).gauge().value()).isNaN();` | Erro SQL ? desconhecido, não zero |
| MON-11-AC03 / OPS-03 | `src/test/java/br/com/monitor/OperationalLogsIT.java:40` `assertThat(fields(event)).containsEntry("monitorId", target.id()) .containsEntry("executionId", result.executionId()) .containsEntry("incidentId", delivery.incidentId()) .containsEntry("notificationId", delivery.id());` | Quatro UUIDs reais de transição/entrega |
| MON-11-AC03 / OPS-03 | `src/test/java/br/com/monitor/OperationalLogsIT.java:62` `assertThat(capture.list).hasSize(4);` | Rollback não confirma |
| MON-11-AC03 / OPS-03 | `src/test/java/br/com/monitor/OperationHealthIT.java:55` `assertThat(json.path("notificationId").asText()).isEqualTo(notification.toString());` | JSON ECS real preserva UUID |
| MON-11-AC04 / OPS-04 | `src/test/java/br/com/monitor/RetentionIT.java:38` `assertThat(checks.find(exact.executionId())).contains(exact);` | Igualdade não expira |
| MON-11-AC04 / OPS-04 | `src/test/java/br/com/monitor/RetentionIT.java:62` `assertThat(count("notification_intents")).isEqualTo(3);` | Cadeias pendentes/SENDING preservadas |
| MON-11-AC04 / OPS-04 | `src/test/java/br/com/monitor/RetentionIT.java:40` `assertThat(checks.retired(old.executionId())).isTrue();` | Recibo durável e trigger rejeitam replay |
| MON-11-AC04 / OPS-04 | `src/test/java/br/com/monitor/RetentionIT.java:112` `assertThat(incidents.lastAppliedStart(internal)).contains(internalAt);` | último APPLIED preserva ordenação |
| MON-11-AC04 / OPS-04 | `src/test/java/br/com/monitor/RetentionIT.java:208` `assertThat(count("retired_executions")).isZero();` | Rollback remove recibos novos |
| MON-11-AC04 / OPS-04 | `src/test/java/br/com/monitor/RetentionIT.java:183` `assertThat(count("retired_executions")).isEqualTo(2);` | Shutdown preserva só primeiro lote confirmado |
| MON-11-AC05 / OPS-05 | `src/test/java/br/com/monitor/RetentionQueriesIT.java:33` `assertThat(summary.statistics().dataStatus()).isEqualTo("NO_DATA");` | NO_DATA em janela removida |
| MON-11-AC05 / OPS-05 | `src/test/java/br/com/monitor/RetentionQueryApiIT.java:58` `assertThat(json.path("historyAvailableFrom").asText()).isEqualTo(base.toString());` | Metadados exatos nos quatro endpoints |
| MON-11-AC06 / OPS-06 | `src/test/java/br/com/monitor/CapacityScenario.java:162` `assertThat(peak).isEqualTo(4);` | Pico4, com assertion por rodada <=4 |
| MON-11-AC06 / OPS-06 | `src/test/java/br/com/monitor/CapacityScenario.java:163` `assertThat(overlaps.get()).isZero();` | Zero sobreposição por monitor |
| MON-11-AC06 / OPS-06 | `src/test/java/br/com/monitor/CapacityScenario.java:172` `assertThat(pendingPeak).isEqualTo(50);` | 50 PENDING com SMTP421 real |

Adequação A–D T7 PASS: documentação explicita limites, matriz atual cita valores e estado; comandos/código são os mesmos dos gates aprovados. Verificação independente será despachada após este commit.
