# MON-08: evidências do autor

Baseline: `52cb94e`. Guidelines: README, pom.xml, Checkstyle; JUnit/AssertJ, PostgreSQL Testcontainers e Docker. Nenhum teste anterior removido/desabilitado. As assertions da versão corrente passam de cinco para seis migrations; o teste histórico V4→V5 passa a declarar target 5, preservando seu resultado esperado.

## T1: schema

Premissas: snapshot e UNIQUE V5 preservados; upgrade aditivo. Arquivos: V6, NotificationSchemaIT, ApplicationIT, IncidentSchemaIT, smoke e artefatos desta feature. Sucesso: histórico V5 inalterado, intenção PENDING preservada, limites/rollback/UNICIDADE reais e suite Docker aprovada.

| Critério | Assertion file:line | Resultado da spec | Coberto |
| --- | --- | --- | --- |
| MAIL-02 upgrade | `src/test/java/br/com/monitor/NotificationSchemaIT.java:50` `migrationsExecuted.isEqualTo(1)` e :52 histórico `isEqualTo(history)` | V6 aplicada uma vez; V1–V5 intactas | Sim |
| MAIL-01/03 replay | `src/test/java/br/com/monitor/NotificationSchemaIT.java:56` `processor.process(...).isFalse()` e :57 count `isEqualTo(1)` | Uma intenção | Sim |
| MAIL-03 SQL UNIQUE | `src/test/java/br/com/monitor/NotificationSchemaIT.java:59` insert duplicado `isInstanceOf(DataAccessException.class)` | Duplicata rejeitada | Sim |
| MAIL-05 limite/sanitização | `src/test/java/br/com/monitor/NotificationSchemaIT.java:65` e :86 `isInstanceOf(DataAccessException.class)` | Tentativa 6 e erro bruto rejeitados | Sim |
| MAIL-02/05 rollback | `src/test/java/br/com/monitor/NotificationSchemaIT.java:77` `containsEntry("status", "PENDING").containsEntry("attempt_count", 0)` e :79 `isZero()` | Nenhuma reserva parcial | Sim |

Reverse mapping: cada linha acima é também o mapa assertion→MAIL; manter todas. Sem assertion de simples chamada, todos os resultados são SQL observável; sem requisito extra. Gate/resultados registrados após execução.

Gate T1: docker compose run --build --rm tests; BUILD SUCCESS, 357 testes, 138 unitários/219 IT, zero falhas/erros/skips e Checkstyle. Log .validation/mon08-t1.log. Primeira execução detectou contagens V5 remanescentes; contagens atuais atualizadas para V6, sem alterar os cenários históricos.

