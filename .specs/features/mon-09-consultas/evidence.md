# MON-09: evidencias

## T1

Gate: `docker compose run --build --rm tests mvn --batch-mode --no-transfer-progress test`. BUILD SUCCESS: 164 unitarios (20 novos), zero falhas/erros/skips/Checkstyle. Log `.validation/mon09-t1.log`. Primeira execucao detectou linha de 121 caracteres; corrigida antes do commit.

| Criterio | Evidencia e assertion | Outcome da spec | Coberto |
| --- | --- | --- | --- |
| READ-01/09 defaults/offset | QueryParametersTest.java:21 `isEqualTo(now.minusSeconds(86400))`; :28 `isEqualTo(214748364700L)` | 24h, offset long | Sim |
| READ-09 erros | QueryParametersTest.java:41 `isEqualTo(400)`; :42 `isEqualTo("VALIDATION_ERROR")`; :43 `containsKey(pair[0])` | Erro por campo | Sim |
| READ-09 recursos/UUID | QueryParametersTest.java:55 `isEqualTo("CLOSED")`; :57 `isEqualTo("EXHAUSTED")`; :66 `isInstanceOf(MonitorProblem.class)` | Filtros especificos e UUID estrito | Sim |

Reverse mapping: todas as assertions de QueryParametersTest.java:21-66 pertencem a READ-01/09; manter. Adequacao A-D: outcomes precisos, sem testes especulativos, JUnit/AssertJ e Checkstyle existentes. Nenhum teste anterior alterado. Spec e tasks: validadores com zero erros/avisos.

## T2

Gate integral Docker: BUILD SUCCESS, 408 testes (164 unitarios/244 IT), zero falhas/erros/skips/Checkstyle; `.validation/mon09-t2-retest.log`. V7 validada sem modificar V1-V6. Primeiro gate encontrou tres expectativas de migracao desatualizadas, corrigidas preservando assertions anteriores. Teste MON-08 agora limita fixture a target 6; teste atual exige sete versoes e sua identidade no restart.

| Criterio | Evidencia e assertion | Outcome | Coberto |
| --- | --- | --- | --- |
| READ-05 upgrade | ObservationEpochIT.java:39 `isEqualTo(1)`; :40 `isEqualTo(checksums)`; :43 `isEqualTo(base)`; :45 `isAfter(base.plusSeconds(30))` | Uma migracao, checksums e limite conhecido | Sim |
| READ-05 pausa/retomada/nome | ObservationEpochIT.java:54 `containsExactly("ACTIVE", "PAUSED", "ACTIVE")`; :57 `containsExactly(Timestamp.from(base.plusSeconds(30)), Timestamp.from(base.plusSeconds(40)))` | Cortes 30/40, nome nao corta | Sim |
| READ-05 rollback/politica | ObservationEpochIT.java:66 `isEqualTo(3)`; :69 `isEqualTo(21)` | Rollback integral e novo L=21 | Sim |

Reverse mapping: todas as assertions de ObservationEpochIT.java:39-71 cobrem READ-05/edge upgrade e transacao. Adequacao A-D aprovada; SQL PostgreSQL real, sem mocks ou testes removidos. Baseline +21 testes preservada.

## T3

Gate Full selecionado: Docker tests mvn verify com -Dit.test=HistoryQueriesIT,SummaryQueriesIT,QueryApiIT e -Dfailsafe.failIfNoSpecifiedTests=false. BUILD SUCCESS: 164 unitarios + 4 IT novas, zero falhas/erros/skips/Checkstyle; `.validation/mon09-t3.log`. PowerShell requer propriedades -D entre aspas simples.

| Criterio | Evidencia e assertion | Outcome | Coberto |
| --- | --- | --- | --- |
| READ-01 filtros/empates | HistoryQueriesIT.java:29 `containsExactly(uuid(5), uuid(4), uuid(3), uuid(2), uuid(1))` | Cinco sucessos, DESC sem sobreposicao | Sim |
| READ-01/09 paginas/404 | HistoryQueriesIT.java:49 `beyond.items()).isEmpty()`; :63 `problem.status()).isEqualTo(404)` | Vazio com totais, 404 | Sim |
| READ-02 tempos/duracao | HistoryQueriesIT.java:88 `ageSeconds()).isEqualByComparingTo("599.9")`; :99 `estimatedDurationSeconds()).isEqualByComparingTo("180.9")` | Idade aberta e duracao final separadas | Sim |
| READ-08 notificacoes | HistoryQueriesIT.java:125 `first.items().getFirst().id()).isEqualTo(uuid(11))`; :137 `containsExactly(1, 2)` | Ordem UUID e tentativas numeradas | Sim |

Reverse mapping: HistoryQueriesIT inteiro cobre READ-01/02/08/09 e seus casos vazios/limites; QueryFixture so prepara dados controlados. Payloads tem assertions individuais de IDs/versoes/instantes/classificacao/status/erros. Adequacao A-D aprovada; nenhuma alteracao de teste anterior.

## T4

Gate Full selecionado: BUILD SUCCESS, 164 unitarios + 17 IT (13 novas de resumo e 4 de historico), zero falhas/erros/skips/Checkstyle. `.validation/mon09-t4.log`. O primeiro PostgreSQL detectou alias reservado `end`; corrigido para coverage_end sem alterar testes.

| Criterio | Evidencia e assertion | Outcome | Coberto |
| --- | --- | --- | --- |
| READ-04/06/07 | SummaryQueriesIT.java:33 `metricType()).isEqualTo("CHECK_SUCCESS_SAMPLING")`; :39 `successRatePercent()).isEqualByComparingTo("80.00")`; :40 `averageResponseTimeMs()).isEqualByComparingTo("100.00")` | Oito S + duas F, tres excluidos | Sim |
| READ-05 sem dados | SummaryQueriesIT.java:67-75 NO_DATA, contagens zero, percent/latencia null, lacuna integral | Sem inventar percentual | Sim |
| READ-05 cobertura | SummaryQueriesIT.java:101-109 `containsExactly(Period(125,200))`, anterior a janela sem amostras e uniao completa | C-16 | Sim |
| READ-05 pausa/politica | SummaryQueriesIT.java:126-148 lacuna 30-60, 80-90,111-120 e igualdade apos nome | C-20 e mudanca de L | Sim |
| READ-03 estados | SummaryQueriesIT.java:162-222 exige todos os rotulos, frescor L/L+1us e incidente/ultima observacao preservados | Dimensoes independentes | Sim |

Reverse mapping: os 13 casos de SummaryQueriesIT cobrem READ-03 a READ-07, READ-09 inexistente e edge cases listados. Assertions de cada campo do resumo e metricas verificam valores, nao chamadas. Adequacao A-D aprovada. Fixture acrescenta status HTTP nullable via switch para resultados sem status; nenhuma assertion anterior alterada.

## T5

Gate Full selecionado: BUILD SUCCESS, 164 unitarios + 43 IT (26 HTTP novas), zero falhas/erros/skips/Checkstyle. `.validation/mon09-t5.log`. Quatro rotas reais sob Basic, no-store, filtros por recurso e convencao de erros preservados.

| Criterio | Evidencia e assertion | Outcome | Coberto |
| --- | --- | --- | --- |
| READ-01/02/03/08 HTTP | QueryApiIT.java:118-160 payload real de check/incidente/notificacao/resumo, pagina excedente e totais | Contratos JSON e IDs relacionados | Sim |
| READ-09 inexistente/invalido/401 | QueryApiIT.java:83 `statusCode()).isEqualTo(404)`; :87 `statusCode()).isEqualTo(400)`; :90 `statusCode()).isEqualTo(401)`; :84 `isEqualTo("MONITOR_NOT_FOUND")` | Mesmo erro administrativo | Sim |
| READ-09 filtros | QueryApiIT.java:108 `response.statusCode()).isEqualTo(400)`; :110 `isEqualTo("VALIDATION_ERROR")` | 21 combinacoes de campos/rotas | Sim |
| READ-05 vazio | QueryApiIT.java:71 `isEqualTo("SEM_DADOS")`; :72 `isNull()).isTrue()`; :76 `items").size()).isZero()`; :80 `totalPages").asInt()).isZero()` | Estados vazios explicitos | Sim |

Reverse mapping: todos os 26 casos HTTP pertencem a READ-01/02/03/08/09 e casos vazios de READ-05; testes comprovam saida HTTP e preservacao SQL. Adequacao A-D aprovada. QueryErrors reutiliza o envelope MonitorErrors.Error, sem alterar a chain global Basic/CSRF.

## T6 e gate final do autor

| Comando | Resultado |
| --- | --- |
| docker compose run --build --rm tests | Exit 0, BUILD SUCCESS; 451 testes = 164 unitarios + 287 IT; 0 falhas/erros/skips e 0 Checkstyle |
| docker compose build app | Exit 0, imagem monitor-disponibilidade:local construida |
| powershell -NoProfile -ExecutionPolicy Bypass -File scripts/validate-compose.ps1 -NoBuild | Exit 0, 37 PASSs; startup fechado, Basic/CSRF, sete migracoes, SMTP, restart/down-up e runtime UID10001 |
| validate_spec --strict / validate_tasks | Exit 0, zero erros/avisos |
| git diff --check | Exit 0 |

Logs: `.validation/mon09-final-tests.log`, `.validation/mon09-runtime-build.log`, `.validation/mon09-smoke.log`, `.validation/mon02-smoke-305b9fe32087.log`. XMLs em target/surefire-reports e target/failsafe-reports; contagens lidas dos XMLs. Baseline 387; delta +64 (20 unitarios, 44 IT). Nenhum teste removido/enfraquecido/ignorado. Gates dirigidos anteriores constam nas tasks; fechamento usa suite integral, sem selecao.

Docs/queries.md define filtros, exemplos das quatro rotas, [from,to), UTC, limites/paginacao, taxas e denominadores, latencia e arredondamento, coberturaKnownFrom, pausas/politicas e ausencia de retencao implementada. README e monitor-api ligam o contrato. Adequacao A-D: todos os criterios ligados aos testes co-localizados anteriores; esta task documental nao adiciona comportamento de producao. Verificacao independente executa apos este commit. CI remota nao executada; banco do operador preservado.

## T7: correcoes apontadas pelo Verifier

Verificacao inicial independente: gates 451 testes/runtime/smoke verdes, mas sensor 3/5 mortos; transitionId e nextAttemptAt errados sobreviveram. Relatorio FAIL inicial versionado junto desta correcao; lessons L-002/L-003 candidates registradas pelo script da skill. T8 trata a precisao das mensagens.

Gate Full selecionado apos T7: BUILD SUCCESS, 164 unitarios +47 IT (quatro novos estados), zero falhas/erros/skips/Checkstyle. `.validation/mon09-t7.log`.

| Criterio | Evidencia e assertion | Outcome | Coberto |
| --- | --- | --- | --- |
| READ-08 identidade | HistoryQueriesIT.java:47 `item.transitionId()).isEqualTo(stored.get("transition_id"))` | Transicao persistida, nao ID da notificacao | Sim |
| READ-08 tempos | HistoryQueriesIT.java:51 `nextAttemptAt()).isEqualTo(next)`; :52 `completedAt()).isEqualTo(completed)` | PENDING agendado; finais com fim exato; SENDING sem fim | Sim |
| READ-08 estados/snapshot | HistoryQueriesIT.java:49 `recipient()).isEqualTo("ops@example.org")`; :50 `status()).isEqualTo(status)` | Destinatario original apos edicao; quatro estados | Sim |

Reverse mapping: quatro casos parametrizados novos pertencem a READ-08 e payload de conclusao; restantes preservados. Adequacao A-D aprovada: valores exatos, dados/horarios controlados, sem assertions enfraquecidas. Evidencias das tasks T1-T6 citam as linhas nos respectivos commits; a insercao dos novos casos desloca linhas atuais de HistoryQueriesIT.

## T8: mensagens exatas e gate final corrigido

Catalogo de mensagens e precedencia explicitados na spec e docs antes da implementacao. QueryApiIT exige texto exato em 22 combinacoes (inclui precisao submicrossegundo); QueryParametersTest acrescenta bordas inclusivas do calendario e prioridade de erros estruturais. Nenhuma assertion anterior enfraquecida: isNotBlank foi fortalecida para isEqualTo(expected).

Gate integral Docker: BUILD SUCCESS, 458 testes (166 unitarios/292 IT), zero falhas/erros/skips/Checkstyle. Runtime build exit 0; smoke exit 0, 37 PASSs. Logs `.validation/mon09-t8-tests.log`, `.validation/mon09-t8-build.log`, `.validation/mon09-t8-smoke.log`, `.validation/mon02-smoke-4902462c3b4b.log`. Delta da baseline MON-08: +71 testes.

| Criterio | Evidencia atual e assertion | Outcome da spec | Coberto |
| --- | --- | --- | --- |
| READ-09 | src/test/java/br/com/monitor/QueryApiIT.java:123 `assertThat(body.path("errors").path(field).asText()).isEqualTo(expected);` | Mensagens exatas em 22 entradas HTTP | Sim |
| READ-09 | src/test/java/br/com/monitor/QueryParametersTest.java:21 `assertThat(lower.from()).isEqualTo(Instant.parse("0001-01-02T00:00:00Z"));` | Limite inferior inclusivo | Sim |
| READ-09 | src/test/java/br/com/monitor/QueryParametersTest.java:26 `assertThat(upper.to()).isEqualTo(Instant.parse("9999-12-30T00:00:00Z"));` | Limite superior inclusivo | Sim |
| READ-09 | src/test/java/br/com/monitor/QueryParametersTest.java:48 `.containsEntry("to", "must occur once"));` | Repeticao prevalece sobre conteudo | Sim |
| READ-09 | src/test/java/br/com/monitor/QueryParametersTest.java:53 `.containsEntry("page", "unknown parameter"));` | Desconhecido prevalece sobre conteudo | Sim |

Reverse mapping: dois novos unitarios e um novo caso HTTP de T8 cobrem READ-09/edge calendario. Os quatro novos casos T7 cobrem READ-08. Adequacao A-D aprovada; catalogo vem da spec, sem testar comportamento incidental de biblioteca. O relatorio FAIL anterior permanece no historico Git; nova validacao independente e sensores seguem este commit.

## T9: precisao do default na borda

A revisao independente identificou ambiguidade entre calendario tecnico e default de 24h. Decisao: limites sao dos timestamps fornecidos; from derivado preserva exatamente to-24h, podendo alcancar 0001-01-01, representavel pelo PostgreSQL. Spec/docs explicitam essa composicao antes do teste.

Quick Docker: BUILD SUCCESS, 167 unitarios, zero falhas/erros/skips/Checkstyle; `.validation/mon09-t9.log`. Sem mudanca em producao desde T8; runtime/smoke aprovados continuam aplicaveis. A suite possui agora 459 casos (167 unitarios/292 IT); revalidacao final independente executa sobre o novo commit antes dos sensores.

| Criterio | Evidencia e assertion | Outcome | Coberto |
| --- | --- | --- | --- |
| READ-09 default/calendario | src/test/java/br/com/monitor/QueryParametersTest.java:21 `assertThat(window.from()).isEqualTo(Instant.parse("0001-01-01T00:00:00Z"))`; :22 `window.to()` igual a 0001-01-02 | Janela exata de 24h | Sim |

Reverse mapping: unico teste novo corresponde ao edge default/calendario de READ-09. Adequacao A-D aprovada, sem produzir novo comportamento ou alterar testes anteriores. Validadores spec/tasks e git diff --check aprovados. Referencias das tasks anteriores sao relativas aos seus commits; T9 desloca linhas de QueryParametersTest.

## Fechamento independente

PASS: MON-09-AC01 a MON-09-AC07 e READ-01 a READ-09, com evidências atuais por arquivo/linha em [validation.md](validation.md). Gate final único no commit 32ae68d: 459 testes (167 unitários/292 IT), zero falhas/erros/skips/Checkstyle. XMLs finais preservados em `.validation/mon09-reverify-final-reports`; log `.validation/mon09-reverify-final-tests.log`. Runtime e smoke independentes aprovados sobre o mesmo código de produção (diff T8/T9 vazio em fontes e build); smoke 37 PASS.

Revalidação: R4 transitionId, R5 nextAttemptAt, R6 completedAt, R7 mensagem e R1 percentual mortos por assertions, zero erros de infraestrutura e zero sobreviventes. Logs/diffs/XML em `.validation/R*`. Isolamento: porcelain real vazio antes/depois; scratch removida. O relatório preserva o FAIL inicial, os dois sobreviventes originais e as correções. Lessons L-002 a L-004 candidates registradas pelo script da skill.

Validação complementar PostgreSQL 17.6 descartável: `SELECT (timestamptz '0001-01-02T00:00:00Z' - interval '24 hours') = timestamptz '0001-01-01T00:00:00Z', extract(epoch from interval '24 hours')` retornou `t|86400.000000`, exit 0; contêiner próprio removido. Não contado como teste JUnit.

Backlog e handoff atualizados após PASS independente. Comandos finais documentais: validate_spec --strict, validate_tasks, validate_state e git diff --check. CI remota não executada; nenhuma publicação ou alteração no banco do operador.
