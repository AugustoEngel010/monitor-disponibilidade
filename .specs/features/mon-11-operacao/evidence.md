# MON-11: evid?ncias

## T1: sa?de e m?tricas

Premissas: probes status-only existentes; instrumenta??o no scheduler real; outbox consultada no scrape. Arquivos: CheckScheduler, SchedulerRuntime, NotificationMetrics, application.yml e OperationMetricsTest/OperationHealthIT. Sucesso: m?tricas exatas e readiness independente de liveness.

Comando: `docker compose run --build --rm tests mvn --batch-mode --no-transfer-progress verify -Dtest=OperationMetricsTest -Dit.test=OperationHealthIT`. Exit 0, 2 unit?rios e 1 IT, zero falhas/erros/skips, Checkstyle zero. Log `.validation/mon11-t1.log`. Corre??es durante desenvolvimento: quebra de linha Checkstyle e fechamento expl?cito do registro Micrometer (n?o AutoCloseable); sem mudan?a de assertions.

| AC / mapeamento direto e reverso | Evid?ncia e assertion | Resultado da spec |
| --- | --- | --- |
| OPS-01/AC01 | OperationHealthIT.java:36 `isEqualTo(503)` e :39 `isEqualTo(200)` | readiness DOWN, liveness UP sem DB |
| OPS-01/02 acesso | OperationHealthIT.java:30 `isEqualTo(401)` e :32 `isEqualTo(200)` | m?tricas administrativas |
| OPS-02/AC02 concorr?ncia | OperationMetricsTest.java:51 `isEqualTo(1)` e :52 `isEqualTo(4)` | ocupa??o real e capacidade |
| OPS-02 dura??o/atraso/classifica??o | OperationMetricsTest.java:58 `isEqualTo(3)`, :60 `isEqualTo(125)`, :62 `isEqualTo(1)` | segundos/ms/contagem exatos |
| OPS-02 cardinalidade | OperationMetricsTest.java:63 `hasSize(21)`, :65 `allMatch` | tags somente nove enums |
| OPS-02 outbox/erro | OperationMetricsTest.java:76 `isEqualTo(7)`, :79 `isNaN()` | gauge corrente, falha n?o vira zero |

Adequa??o A?D: todos os tr?s testes t?m v?nculo acima, valores esperados correspondem ? spec, sem assertions rasas ou testes removidos/pulados. Diretrizes pom.xml/Checkstyle e padr?es JUnit existentes.

## T2: eventos confirmados e JSON

Premissas: eventos de sucesso s? ap?s commit; nenhuma mensagem externa entra no payload. Arquivos: OperationalEvents, CheckRepository, IncidentRepository, NotificationStore, OperationalLogsIT e OperationHealthIT. Resultado: quatro IDs atravessam transi??o e entrega sem incluir snapshot sens?vel.

Gates Docker: `.validation/mon11-t2.log` (2 unit?rios +33 IT: IncidentProcessorIT, NotificationStoreIT e OperationalLogsIT), `.validation/mon11-t2-final.log` (2 unit?rios +2 IT: OperationalLogsIT e OperationHealthIT). Ambos BUILD SUCCESS, zero falhas/erros/skips/Checkstyle. Comando final: `docker compose run --build --rm tests sh -c 'mvn --batch-mode --no-transfer-progress verify -Dtest=OperationMetricsTest -Dit.test=OperationalLogsIT,OperationHealthIT; result=$?; cp -a target/. /reports/; exit $result'`.

| AC / mapeamento direto e reverso | Evid?ncia e assertion | Resultado |
| --- | --- | --- |
| OPS-03 correla??o | OperationalLogsIT.java:39..44 `containsEntry` dos quatro UUIDs reais | Transi??o, reserva e conclus?o correspondem ? execu??o |
| OPS-03 sanitiza??o | OperationalLogsIT.java:45..50 `getThrowableProxy().isNull()`, `doesNotContain` e allowlist de campos | Sem snapshot, URL ou exce??o |
| OPS-03 rollback | OperationalLogsIT.java:65 `hasSize(4)` ap?s rollback | Nenhuma confirma??o falsa |
| OPS-03 formato JSON | OperationHealthIT.java:59..64 `isEqualTo` dos quatro UUIDs e INTERNAL_ERROR | Sa?da ECS real parseada por Jackson |

Adequa??o A?D PASS: assertions de payload, valores derivados da spec e revers?o real. Ajuste do coletor de teste para preservar chaves com valor null, sem mudar assertions. N?o houve teste apagado/pulado/enfraquecido.

## T3: reten??o transacional

Premissas/arquivos: RetentionService/Runtime, V8, CheckRepository e IncidentProcessor; configura??o Compose/YAML; RetentionIT/MigrationIT e contagens atuais em ApplicationIT/MonitorRepositoryIT/smoke. V7 hist?rico isolado explicitamente em ObservationEpochIT para preservar seu contrato; assertions existentes mantidas, contagem atual elevada de 7 para 8 e assertion da vers?o 8 adicionada. V1?V7 intactas. Deduplica??o de dados removidos usa trigger V8 e recibos m?nimos sem TTL.

Gate `docker compose run --build --rm tests sh -c 'mvn --batch-mode --no-transfer-progress verify -Dtest=OperationMetricsTest -Dit.test=RetentionIT,RetentionMigrationIT,ObservationEpochIT,MonitorRepositoryIT,ApplicationIT,SchedulerRuntimeIT,IncidentSchemaIT; result=$?; cp -a target/. /reports/; exit $result'`: **2 unit?rios +45 IT, zero falhas/erros/skips/Checkstyle**, BUILD SUCCESS em 1:04 min. Log `.validation/mon11-t3-final.log`.

| AC / mapeamento direto e reverso | Evid?ncia | Resultado esperado |
| --- | --- | --- |
| OPS-04 corte/replay | `src/test/java/br/com/monitor/RetentionIT.java:35` `assertThat(checks.retired(old.executionId())).isTrue();` | Recibo dur?vel; replay rejeitado |
| OPS-04 igualdade | `src/test/java/br/com/monitor/RetentionIT.java:33` `assertThat(checks.find(exact.executionId())).contains(exact);` | Igualdade n?o removida |
| OPS-04 pendentes | `src/test/java/br/com/monitor/RetentionIT.java:57` `assertThat(count("notification_intents")).isEqualTo(3);` | Toda cadeia pendente preservada |
| OPS-04 terminal | `src/test/java/br/com/monitor/RetentionIT.java:83` `assertThat(retention(base.plusNanos(1000), 100).clean().incidents()).isEqualTo(1);` | Igualdade preservada; +1?s remove |
| OPS-04 continuidade | `src/test/java/br/com/monitor/RetentionIT.java:107` `assertThat(incidents.lastAppliedStart(internal)).contains(internalAt);` | ?ltimo APPLIED interno e observa??o preservados |
| OPS-04 reserva | `src/test/java/br/com/monitor/RetentionIT.java:120` `assertThat(count("check_attempts")).isEqualTo(1);` | Reserva mantida |
| OPS-04 lote/tempo | `src/test/java/br/com/monitor/RetentionIT.java:131` `assertThat(count("retired_executions")).isEqualTo(5);` | Lote limitado e watermark monot?nico |
| OPS-04 ?pocas | `src/test/java/br/com/monitor/RetentionIT.java:144` `assertThat(count("observation_epochs")).isEqualTo(2);` | ?poca referenciada e corrente preservadas |
| OPS-04 rollback | `src/test/java/br/com/monitor/RetentionIT.java:158` `assertThat(count("retired_executions")).isZero();` | Falha reverte recibos, aplica??es, payload e watermark |
| OPS-04 configura??o | `src/test/java/br/com/monitor/RetentionIT.java:176` `.hasMessage("Invalid retention configuration");` | Erro gen?rico para configura??o inv?lida |
| OPS-04 upgrade | `src/test/java/br/com/monitor/RetentionMigrationIT.java:25` `assertThat(current.migrate().migrationsExecuted).isEqualTo(1);` | V8 aplicada uma vez, checksums e dados anteriores intactos |

Adequa??o A?D PASS: dez cen?rios de reten??o e um upgrade derivam de OPS-04 e edges; gates incluem regress?o dos m?dulos afetados. Nenhum teste removido/pulado nem assertion enfraquecida.

## T4: consultas e limite hist?rico

Premissas/arquivos: HistoryQueries/SummaryQueries e RetentionQueriesIT/RetentionQueryApiIT. historyAvailableFrom ? global e dur?vel; retainedOutPeriods ? a interse??o da janela com o per?odo anterior ao limite. noObservationPeriods cont?m somente lacunas posteriores ao corte. Incidentes/notifica??es protegidos continuam consult?veis com aviso de hist?rico parcial; amostras anteriores n?o entram em checks/estat?sticas/cobertura.

Gates Docker com verify, Checkstyle e c?pia de relat?rios: `.validation/mon11-t4.log`, 53 IT +23 unit?rios QueryParametersTest; `.validation/mon11-t4-api.log`, 6 IT +23 unit?rios. BUILD SUCCESS, zero falhas/erros/skips. Sele??es IT: RetentionQueriesIT,HistoryQueriesIT,SummaryQueriesIT,QueryApiIT; final RetentionQueriesIT,RetentionQueryApiIT.

| AC / mapeamento direto e reverso | Evid?ncia e assertion | Resultado |
| --- | --- | --- |
| OPS-05 janela removida | `src/test/java/br/com/monitor/RetentionQueriesIT.java:33` `assertThat(summary.statistics().dataStatus()).isEqualTo("NO_DATA");` | NO_DATA/null/zero amostras |
| OPS-05 dados operacionais | `src/test/java/br/com/monitor/RetentionQueriesIT.java:29` `assertThat(summary.lastObservation().executionId()).isEqualTo(result.executionId());` | ?ltima evid?ncia operacional separada da janela |
| OPS-05 janela cruzada | `src/test/java/br/com/monitor/RetentionQueriesIT.java:60` `assertThat(summary.retainedOutPeriods()).containsExactly(new SummaryQueries.Period(q.from(), base));` | Reten??o separada de lacuna |
| OPS-05 cadeia protegida | `src/test/java/br/com/monitor/RetentionQueriesIT.java:74` `assertThat(notificationPage.retainedOutPeriods()).isEqualTo(summary.retainedOutPeriods());` | Pend?ncia preservada com metadados |
| OPS-05 cobertura | `src/test/java/br/com/monitor/RetentionQueriesIT.java:87` `assertThat(summary.noObservationPeriods()).containsExactly(new SummaryQueries.Period(q.from(), q.to()));` | Amostra antiga n?o atravessa o corte |
| OPS-05 antes da limpeza | `src/test/java/br/com/monitor/RetentionQueriesIT.java:96` `assertThat(summary.historyAvailableFrom()).isNull();` | Contrato anterior preservado |
| OPS-05 HTTP quatro recursos | `src/test/java/br/com/monitor/RetentionQueryApiIT.java:58` `assertThat(json.path("historyAvailableFrom").asText()).isEqualTo(base.toString());` | Corte UTC exato nos quatro DTOs |
| OPS-05 HTTP aus?ncia | `src/test/java/br/com/monitor/RetentionQueryApiIT.java:64` `assertThat(json.at("/statistics/successRatePercent").isNull()).isTrue();` | Aus?ncia n?o ? sucesso |

Adequa??o A?D PASS: seis cen?rios novos derivados de OPS-05, incluindo serializa??o HTTP real, mais regress?o MON-09. Campos de payload verificados por valor, sem teste anterior removido/pulado/enfraquecido.

## T5: reten??o no painel

Premissas/arquivos: details.js e retention.spec.js; apenas apresentar metadados do backend, com datas UTC preservadas no atributo datetime. Backend permanece respons?vel pelos c?lculos. Gate `docker compose -p mon11-panel --profile panel-tests run --use-aliases --build --rm panel-tests`: **50/50 PASS**, zero falhas/skips, 1.6 min; `.validation/mon11-t5-panel.log`, `.validation/panel/junit.xml`. Runtime recompilado pelo Docker com 169 unit?rios e Checkstyle zero.

| AC / mapeamento direto e reverso | Evid?ncia e assertion | Resultado |
| --- | --- | --- |
| OPS-05 limite nas quatro se??es | config/panel/retention.spec.js:27 `toBeVisible()` e :29 `toHaveAttribute('datetime', cutoff)` | Corte exato da API, sem recalcular |
| OPS-05 aviso condicional | config/panel/retention.spec.js:31 `toHaveCount(removed ? 1 : 0)` | Aviso somente quando a janela cruza o corte |
| OPS-05 aus?ncia | config/panel/retention.spec.js:33..35 `toHaveText('Sem dados')`, `toContainText('0 amostras externas')` | Reten??o n?o vira sucesso/zero percentual |

Adequa??o A?D PASS: dois casos novos derivam de OPS-05; 48 cen?rios MON-10 preservados. Nenhuma altera??o em assertions anteriores.

## T6: capacidade controlada

Premissas/arquivos: CapacityScenario, perfil Maven capacity, compose.capacity.yaml e docs/capacity. HTTP e SMTP reais locais, banco Testcontainers pr?prio; sem mocks de rede. Inst?ncia ?nica, 100 monitores, I=10s/T=2s/F=1/R=1/C=4, quatro grupos de 25. Rel?gio real por 60s; acelera??o s? depois do per?odo medido para verificar esgotamento e reten??o.

Comando: `docker compose -p mon11-capacity -f compose.yaml -f compose.capacity.yaml run --build --rm tests`. Exit 0, 169 unit?rios +1 cen?rio, zero falhas/erros/skips/Checkstyle, BUILD SUCCESS. Resultado completo versionado em docs/capacity/mon-11.json e an?lise em docs/capacity/README.md. Logs `.validation/mon11-t6-capacity.log` e amostra `.validation/mon11-t6-resources.log`.

Resultado: 287 checks/60,265065594s; pico 4, sobreposi??o 0; SUCCESS144/HTTP_FAILURE72/TIMEOUT69/CANCELLED2 no shutdown. Atraso m?ximo20,377306s e m?dio10,860850498s. SMTP421 real: 50 PENDING no pico/t?rmino; 50 EXHAUSTED ap?s avan?o do rel?gio; m?ximo cinco tentativas e 250 conex?es totais. Limpeza avan?ada31dias retirou135 checks e preservou50 incidentes abertos. Relat?rio explicita satura??o: essa mistura n?o sustentou I=10s, sem prometer SLA.

| AC / mapeamento direto e reverso | Evid?ncia e assertion | Resultado esperado |
| --- | --- | --- |
| OPS-06 limite de monitores | `src/test/java/br/com/monitor/CapacityScenario.java:101` `error -> assertThat(error.code()).isEqualTo("MONITOR_LIMIT"));` | 101? rejeitado MONITOR_LIMIT |
| OPS-06 dura??o | `src/test/java/br/com/monitor/CapacityScenario.java:161` `assertThat(duration).isGreaterThanOrEqualTo(60);` | Pelo menos 60s reais |
| OPS-02/06 concorr?ncia | `src/test/java/br/com/monitor/CapacityScenario.java:162` `assertThat(peak).isEqualTo(4);` | Pico4 e nunca acima4 |
| OPS-06 sobreposi??o | `src/test/java/br/com/monitor/CapacityScenario.java:163` `assertThat(overlaps.get()).isZero();` | Zero por monitor |
| OPS-06 reserva | `src/test/java/br/com/monitor/CapacityScenario.java:166` `assertThat(count(jdbc, "check_attempts")).isZero();` | Zero ap?s shutdown |
| OPS-02/06 backlog | `src/test/java/br/com/monitor/CapacityScenario.java:172` `assertThat(pendingPeak).isEqualTo(50);` | 50 pendentes |
| OPS-02/06 atraso | `src/test/java/br/com/monitor/CapacityScenario.java:171` `assertThat(lag.max(TimeUnit.SECONDS)).isGreaterThan(0);` | Atraso medido positivo |
| OPS-02/06 esgotamento | `src/test/java/br/com/monitor/CapacityScenario.java:186` `assertThat(meters.get("monitor.notifications").tag("status", "EXHAUSTED").gauge().value()).isEqualTo(50);` | 50 EXHAUSTED com cinco tentativas |
| OPS-04/06 preserva??o | `src/test/java/br/com/monitor/CapacityScenario.java:194` `assertThat(count(jdbc, "incidents WHERE recovered_at IS NULL")).isEqualTo(50);` | 50 incidentes abertos ap?s reten??o |

Adequa??o A?D PASS para o cen?rio: resultados e medi??es persistidas, sem testes fora dos seis ACs. Gate Build completo em execu??o antes do commit desta task.

## T8: compatibilidade do processamento no upgrade hist?rico

O primeiro gate completo de T6 executou480 testes:479 passaram e NotificationSchemaIT falhou porque IncidentProcessor consultava recibos V8 antes de ler um resultado existente em V5. Evid?ncia preservada em `.validation/mon11-java-before-t8.log`. Corrigido para consultar recibos somente quando a evid?ncia estiver ausente; o fluxo normal faz uma consulta a menos e o replay retirado continua ignorado. Nenhuma assertion de teste alterada.

Gate Docker verify `-Dtest=OperationMetricsTest -Dit.test=NotificationSchemaIT,RetentionIT`, com c?pia de relat?rios: **2 unit?rios +11 IT, zero falhas/erros/skips/Checkstyle**, BUILD SUCCESS; `.validation/mon11-t8.log`.

| AC / mapeamento direto e reverso | Evid?ncia e assertion | Resultado |
| --- | --- | --- |
| OPS-04 continuidade no upgrade | src/test/java/br/com/monitor/NotificationSchemaIT.java:53 `isEqualTo(1)` e preserva??o do snapshot V5 | Upgrade V5?V6 passa com processador atual |
| OPS-04 replay removido | src/test/java/br/com/monitor/RetentionIT.java:34 `processor.process(old.executionId()).isFalse()` | Nenhuma reaplica??o de execu??o retirada |

Adequa??o A?D PASS; regress?o encontrada pelo gate e corrigida em produ??o, sem enfraquecer testes. T6 repetir? cen?rio/gates sobre esta corre??o antes de fechar.

## T9: drenagem e interrup??o entre lotes

Revis?o do cen?rio identificou que um ?nico lote1000/hora elimina somente24mil linhas/dia, abaixo da entrada poss?vel. RetentionRuntime agora continua lotes curtos at? esvaziar elegibilidade, liberando locks entre commits e respeitando interrup??o. RetentionService informa more quando qualquer grupo preenche o lote. Arquivos limitados ao componente de reten??o e seus testes derivados de OPS-04.

Gate Docker verify `-Dtest=OperationMetricsTest -Dit.test=RetentionIT,RetentionMigrationIT,NotificationSchemaIT`: **2 unit?rios +14 IT**, zero falhas/erros/skips/Checkstyle, BUILD SUCCESS; `.validation/mon11-t9-final.log`.

| AC / mapeamento direto e reverso | Evid?ncia e assertion | Resultado |
| --- | --- | --- |
| OPS-04 | `src/test/java/br/com/monitor/RetentionIT.java:135` `assertThat(count("check_results")).isEqualTo(1);` | Rodada drena cinco antigos e preserva ?ltimo APPLIED |
| OPS-04 | `src/test/java/br/com/monitor/RetentionIT.java:182` `assertThat(count("check_results")).isEqualTo(4);` | Shutdown ap?s primeiro lote impede lotes seguintes |
| OPS-04 | `src/test/java/br/com/monitor/RetentionIT.java:183` `assertThat(count("retired_executions")).isEqualTo(2);` | Primeiro lote permanece confirmado |
| OPS-04 | `src/test/java/br/com/monitor/RetentionIT.java:184` `assertThat(count("result_applications")).isEqualTo(4);` | Aplica??es restantes preservadas |

Adequa??o A?D PASS: duas IT novas com estado SQL real e Clock fixo; barreira permite interromper ap?s commit, sem sleeps de fronteira temporal. N?o h? teste anterior enfraquecido. A fase temporizada de capacidade n?o executa RetentionRuntime; crit?rios SQL da transa??o s?o id?nticos antes/depois de T9, apenas o flag more foi acrescido.
