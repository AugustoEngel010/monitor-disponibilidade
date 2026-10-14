# MON-07: evidências de execução

## T1: decisão pura

Premissas: contratos MON-01 e thresholds existentes; nenhuma mudança administrativa. Arquivos: IncidentStateMachine, IncidentStateMachineTest e SDD da MON-07. Gates spec --strict/tasks: zero erros/avisos. Quick `docker compose run --build --rm tests mvn --batch-mode --no-transfer-progress test`: exit 0, 138 unitários (124 anteriores + 14 novos), zero failures/errors/skips/Checkstyle. Log `.validation/mon07-t1.log`.

| Critério / resultado definido | file:line + assertion | Coberto |
| --- | --- | --- |
| INC-01: duas confirmações, DOWN na terceira | `src/test/java/br/com/monitor/IncidentStateMachineTest.java:31` `failureCount()).isEqualTo(2)`; :34 `transition()).isEqualTo(Transition.DOWN)`; :37 `failureCount()).isEqualTo(3)` | Sim |
| INC-02: SUCCESS interrompe sem incidente | `src/test/java/br/com/monitor/IncidentStateMachineTest.java:61` `failureCount()).isZero()`; :62 `firstFailureAt()).isNull()`; :63 `openIncidentId()).isNull()` | Sim |
| INC-03: falhas repetidas preservam identidade | `src/test/java/br/com/monitor/IncidentStateMachineTest.java:43` `transition()).isEqualTo(Transition.NONE)`; :46 `openIncidentId()).isEqualTo(incident)` | Sim |
| INC-04/08: falha reinicia recuperação, segundo sucesso confirma | `src/test/java/br/com/monitor/IncidentStateMachineTest.java:78` `recoveryCount()).isZero()`; :83 `transition()).isEqualTo(Transition.RECOVERY)`; :87 `recoveryStartedAt()).isEqualTo(base.plusSeconds(300))`; :88 observação=360 | Sim |
| INC-06: três classes não externas preservam saúde/evidência | `src/test/java/br/com/monitor/IncidentStateMachineTest.java:103` `confirmedState()).isEqualTo("UNKNOWN")`; :104 última observação=60; :112 DOWN e :113 mesmo incidente | Sim |
| INC-01/06: cinco falhas externas | `src/test/java/br/com/monitor/IncidentStateMachineTest.java:123` `transition()).isEqualTo(Transition.DOWN)`; :125 última observação=base | Sim |
| Limites F/R=1/10 | `src/test/java/br/com/monitor/IncidentStateMachineTest.java:135` e :142 `transition()).isEqualTo(...)` somente no limiar; :147 UP | Sim |
| Lacuna estrita L, duração excluída | `src/test/java/br/com/monitor/IncidentStateMachineTest.java:155` RECOVERY em L; :160 contador=1 em L+1us; :167 falhas=1 depois da lacuna | Sim |

Mapeamento reverso: os sete métodos e 14 casos de IncidentStateMachineTest correspondem, na ordem, às oito linhas acima (fronteiras e lacunas são bordas INC-01/04/06/08). Todos necessários, sem testes fora da spec. Adequação PASS: assertions de estado/valores, nenhuma chamada mock usada como prova; guidelines pom.xml/config/checkstyle.xml atendidas. Sem SPEC_DEVIATION, testes removidos ou enfraquecidos. Usuário autorizou explicitamente atualizar contratos antigos para MON-07 em 2026-10-14.
