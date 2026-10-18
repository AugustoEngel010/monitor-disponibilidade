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
| READ-01 filtros/empates | HistoryQueriesIT.java:33 `containsExactly(uuid(5), uuid(4), uuid(3), uuid(2), uuid(1))` | Cinco sucessos, DESC sem sobreposicao | Sim |
| READ-01/09 paginas/404 | HistoryQueriesIT.java:55 `beyond.items()).isEmpty()`; :70 `problem.status()).isEqualTo(404)` | Vazio com totais, 404 | Sim |
| READ-02 tempos/duracao | HistoryQueriesIT.java:99 `ageSeconds()).isEqualByComparingTo("599.9")`; :111 `estimatedDurationSeconds()).isEqualByComparingTo("180.9")` | Idade aberta e duracao final separadas | Sim |
| READ-08 notificacoes | HistoryQueriesIT.java:145 `first.items().getFirst().id()).isEqualTo(uuid(11))`; :160 `containsExactly(1, 2)` | Ordem UUID e tentativas numeradas | Sim |

Reverse mapping: HistoryQueriesIT inteiro cobre READ-01/02/08/09 e seus casos vazios/limites; QueryFixture so prepara dados controlados. Payloads tem assertions individuais de IDs/versoes/instantes/classificacao/status/erros. Adequacao A-D aprovada; nenhuma alteracao de teste anterior.

## T4

Gate Full selecionado: BUILD SUCCESS, 164 unitarios + 17 IT (13 novas de resumo e 4 de historico), zero falhas/erros/skips/Checkstyle. `.validation/mon09-t4.log`. O primeiro PostgreSQL detectou alias reservado `end`; corrigido para coverage_end sem alterar testes.

| Criterio | Evidencia e assertion | Outcome | Coberto |
| --- | --- | --- | --- |
| READ-04/06/07 | SummaryQueriesIT.java:31-37 `metricType` CHECK_SUCCESS_SAMPLING, total13/valid10/success8/latency8, `80.00` e `100.00` | Oito S + duas F, tres excluidos | Sim |
| READ-05 sem dados | SummaryQueriesIT.java:67-75 NO_DATA, contagens zero, percent/latencia null, lacuna integral | Sem inventar percentual | Sim |
| READ-05 cobertura | SummaryQueriesIT.java:101-109 `containsExactly(Period(125,200))`, anterior a janela sem amostras e uniao completa | C-16 | Sim |
| READ-05 pausa/politica | SummaryQueriesIT.java:126-148 lacuna 30-60, 80-90,111-120 e igualdade apos nome | C-20 e mudanca de L | Sim |
| READ-03 estados | SummaryQueriesIT.java:162-222 exige todos os rotulos, frescor L/L+1us e incidente/ultima observacao preservados | Dimensoes independentes | Sim |

Reverse mapping: os 13 casos de SummaryQueriesIT cobrem READ-03 a READ-07, READ-09 inexistente e edge cases listados. Assertions de cada campo do resumo e metricas verificam valores, nao chamadas. Adequacao A-D aprovada. Fixture acrescenta status HTTP nullable via switch para resultados sem status; nenhuma assertion anterior alterada.
