# MON-09: evidencias

## T1

Gate: `docker compose run --build --rm tests mvn --batch-mode --no-transfer-progress test`. BUILD SUCCESS: 164 unitarios (20 novos), zero falhas/erros/skips/Checkstyle. Log `.validation/mon09-t1.log`. Primeira execucao detectou linha de 121 caracteres; corrigida antes do commit.

| Criterio | Evidencia e assertion | Outcome da spec | Coberto |
| --- | --- | --- | --- |
| READ-01/09 defaults/offset | QueryParametersTest.java:21 `isEqualTo(now.minusSeconds(86400))`; :28 `isEqualTo(214748364700L)` | 24h, offset long | Sim |
| READ-09 erros | QueryParametersTest.java:41 `isEqualTo(400)`; :42 `isEqualTo("VALIDATION_ERROR")`; :43 `containsKey(pair[0])` | Erro por campo | Sim |
| READ-09 recursos/UUID | QueryParametersTest.java:55 `isEqualTo("CLOSED")`; :57 `isEqualTo("EXHAUSTED")`; :66 `isInstanceOf(MonitorProblem.class)` | Filtros especificos e UUID estrito | Sim |

Reverse mapping: todas as assertions de QueryParametersTest.java:21-66 pertencem a READ-01/09; manter. Adequacao A-D: outcomes precisos, sem testes especulativos, JUnit/AssertJ e Checkstyle existentes. Nenhum teste anterior alterado. Spec e tasks: validadores com zero erros/avisos.
