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

## T2: esquema e repositório

Premissas: V1–V4 imutáveis; histórico anterior não é reprocessado retroativamente; FK corrente pertence ao mesmo monitor. Arquivos: V5, IncidentRepository, IncidentSchemaIT, IncidentFixtures, fixtures/contagens nos testes anteriores, CheckRepository e smoke. O Checkstyle inicial detectou três linhas longas, corrigidas por quebra de linha. O teste concorrente existente detectou conflito com o novo índice composto: ON CONFLICT passa a tratar todos os índices únicos; assertions originais preservadas. Logs `.validation/mon07-t2.log` e `.validation/mon07-t2-final.log`.

| Critério / resultado definido | file:line + assertion | Coberto |
| --- | --- | --- |
| INC-03: segundo incidente aberto rejeitado; fechado permite novo | `src/test/java/br/com/monitor/IncidentSchemaIT.java:71` `assertThatThrownBy(...).isInstanceOf(DataAccessException.class)`; :78 count=2 após fechar primeiro | Sim |
| INC-03: inserts diretos concorrentes | `src/test/java/br/com/monitor/IncidentSchemaIT.java:92` `containsExactlyInAnyOrder(true, false)`; :94 count de abertos=1 | Sim |
| INC-08: FK de incidente ausente/outro monitor | `src/test/java/br/com/monitor/IncidentSchemaIT.java:103` e :105 `isInstanceOf(DataAccessException.class)`; :106 monitor original preservado | Sim |
| INC-05/08: quatro tempos, identidade/payload, marcador e intenção | `src/test/java/br/com/monitor/IncidentSchemaIT.java:125` a :151 valores exatos; :135 transition contém execution/monitor/incidente/version/type/confirmed; :140 intenção contém snapshot inteiro; :156 `containsExactly(intent)` após repetição rejeitada | Sim |
| INC-08: transição com snapshot incompatível | `src/test/java/br/com/monitor/IncidentSchemaIT.java:166` `isInstanceOf(DataAccessException.class)`; :168/169 counts=0 | Sim |
| INC-07/08: upgrade V4→V5 e histórico imutável | `src/test/java/br/com/monitor/IncidentSchemaIT.java:192` migrações=1; :193=0; :194 histórico igual; :196 resultados iguais; :199 marcador histórico; :202 pendente não marcado | Sim |

Mapeamento reverso: seis métodos de IncidentSchemaIT correspondem às seis linhas acima. Fixtures anteriores agora criam incidente real; TRUNCATE CASCADE opera exclusivamente nos PostgreSQLs descartáveis da própria classe. Migrações do smoke e assertions de banco completo passam a exigir exatamente V1–V5; o teste específico de upgrade V3→V4 permanece fixado em target=4. Nenhuma assertion de domínio anterior foi reduzida. Adequação: cada método tem AC/borda, SQL real e valores definidos pela spec; guidelines de Checkstyle/AssertJ existentes preservadas.

Gate final T2: `docker compose run --build --rm tests`, exit 0, 321 testes (138 unitários/183 IT), zero falhas/erros/skips/Checkstyle. Os 301 casos anteriores foram preservados, com 20 novos na feature até aqui. Sem desvio de spec.
