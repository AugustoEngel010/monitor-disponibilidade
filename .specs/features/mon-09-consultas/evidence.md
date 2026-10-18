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
