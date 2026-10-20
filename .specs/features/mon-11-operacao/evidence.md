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
