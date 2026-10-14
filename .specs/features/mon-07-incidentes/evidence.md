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

## T3: aplicação transacional

Premissas: primeiro registro bruto vence, lock de monitor precede deduplicação, marcador confirma junto do estado. Arquivos: IncidentProcessor, IncidentProcessorIT e SDD. Testes derivados dos ACs antes do processador. Quatro linhas longas detectadas pelo Checkstyle foram quebradas sem alterar assertions. Nenhuma mudança nos testes anteriores.

| Critério / resultado definido | file:line + assertion | Coberto |
| --- | --- | --- |
| INC-01/03/04/08: sequência e quatro tempos exatos | `src/test/java/br/com/monitor/IncidentProcessorIT.java:84` contador=1; :88=2; :92 DOWN; :93=3; :96/97 tempos base/120; :118/119 recuperação 180/240; :121 duração=180; :125 payload intenção exato; :386 payload transição exato | Sim |
| INC-02: sucesso interrompe, sem recuperação inicial | `src/test/java/br/com/monitor/IncidentProcessorIT.java:143` `failureCount()).isZero()`; :146/147/148 incidentes/transições/intenções=0 | Sim |
| INC-04: falha reinicia início provisório | `src/test/java/br/com/monitor/IncidentProcessorIT.java:160` contador=0; :161 início null; :166/167 sequência final 300/360 | Sim |
| INC-06: três resultados não externos | `src/test/java/br/com/monitor/IncidentProcessorIT.java:178` observação=60; :185 DOWN; :186 contador=0; :189 mesmo incidente; :190 início null | Sim |
| INC-05: replay/payload divergente preserva todos os campos | `src/test/java/br/com/monitor/IncidentProcessorIT.java:204` process=false; :205 e :210 `snapshot()).isEqualTo(before)` sobre seis tabelas | Sim |
| INC-05: quatro callers do mesmo resultado | `src/test/java/br/com/monitor/IncidentProcessorIT.java:222` `containsExactlyInAnyOrder(true,false,false,false)`; :225 contador=3; :226/227/228 counts=1 | Sim |
| INC-03: falhas distintas concorrentes | `src/test/java/br/com/monitor/IncidentProcessorIT.java:246/247` dois process=true; :250/251/252 counts=1 | Sim |
| INC-08: rollback de cada escrita na abertura | `src/test/java/br/com/monitor/IncidentProcessorIT.java:265` DataAccessException; :268 snapshot integral igual; :269 marcador ausente; :270 resultado original; :273 confirmação=120 após retry | Sim |
| INC-08: rollback de cada escrita no encerramento | `src/test/java/br/com/monitor/IncidentProcessorIT.java:290` snapshot integral igual; :291 mesmo incidente; :292 encerramento null; :294/295 instantes 180/240 após retry | Sim |
| INC-08: leitor separado e rollback externo | `src/test/java/br/com/monitor/IncidentProcessorIT.java:310` leitor vê `before`; :314 snapshot igual depois de rollback; :316 DOWN após reaplicar | Sim |
| INC-06/07: resultado antigo só histórico | `src/test/java/br/com/monitor/IncidentProcessorIT.java:325/331` monitor igual; :326 HISTORY_ONLY; :333 incidentes=0 | Sim |
| INC-06/07/08: início lógico e processador reconstruído | `src/test/java/br/com/monitor/IncidentProcessorIT.java:345` process=true; :346 DOWN; :347 primeira falha=base; :348 confirmação=190 apesar de início lógico=185 (L exato) | Sim |

Mapeamento reverso: os doze métodos de IncidentProcessorIT, 20 casos incluindo parametrizações, correspondem na ordem às doze linhas acima. Cada um verifica AC/borda, sem teste especulativo ou chamada mock como evidência. Snapshots incluem contadores, instantes e payloads completos em seis tabelas. Adequação PASS; padrões Testcontainers/AssertJ/Checkstyle respeitados; sem SPEC_DEVIATION.

Gate T3: `docker compose run --build --rm tests`, exit 0, 341 testes (138 unitários/203 IT), zero falhas/erros/skips/Checkstyle. Log `.validation/mon07-t3.log`. Resultado bruto sobrevive aos oito cenários de rollback e pode ser reaplicado sem novo HTTP.

## T4: reservas e administração

Premissas: conclusão aplica antes de remover reserva; reconciliação nunca descarta resultado já salvo; pausa e processador usam o mesmo lock. Arquivos: ScheduleStore, MonitorService, IncidentLifecycleIT, contratos anteriores de ScheduleStoreIT/SchedulerRuntimeIT e SDD. Testes anteriores atualizados com autorização explícita: SUCCESS agora estabelece UP e limpa falhas. Assertions de configuração/versão/geração/horário/histórico permanecem; igualdade integral do monitor renomeado foi preservada com estado esperado UP/observação corrente.

| Critério / resultado definido | file:line + assertion | Coberto |
| --- | --- | --- |
| INC-01/03/04/08: HTTP real em scheduler, novo incidente após fechado | `src/test/java/br/com/monitor/IncidentLifecycleIT.java:100` DOWN; :101/102 tempos base/120; :113/114 recuperação 180/240; :120 ID diferente; :121 fechado imutável; :125 hits=8 | Sim |
| INC-06/07: reinício com duas falhas dentro/fora de L | `src/test/java/br/com/monitor/IncidentLifecycleIT.java:137` monitor integral igual; :139/140 contador=3/DOWN ou 1/UNKNOWN; :142 primeira falha exata | Sim |
| INC-07: recuperação parcial sobrevive reinício sem tentativa | `src/test/java/br/com/monitor/IncidentLifecycleIT.java:152/153` monitor/incidente integrais; :156/157 tempos 180/240 | Sim |
| INC-07: pausa/retomada idempotentes com incidente | `src/test/java/br/com/monitor/IncidentLifecycleIT.java:167/175` igualdade integral; :170 contador=0; :172 início incidente null; :182 novo contador=1; :184 intenção preservada | Sim |
| INC-05/06/07: resultado tardio após pausa/retomada/política | `src/test/java/br/com/monitor/IncidentLifecycleIT.java:204/207` monitor igual; :205 resultado salvo; :206 replay=false; :208/209 counts=0 | Sim |
| INC-07: nome/e-mail preservam sequência e intenção; política 409 | `src/test/java/br/com/monitor/IncidentLifecycleIT.java:220` estado integral igual; :221 intenção igual; :229 HTTP 409 por cada campo; :230 monitor integral; :234/237 destinatários distintos por transição | Sim |
| INC-06/07: reserva interrompida limpa recuperação, preserva incidente | `src/test/java/br/com/monitor/IncidentLifecycleIT.java:253` CANCELLED; :256 mesmo ID; :257/258 contador/início monitor zero/null; :259 início incidente null; :260/261 observação/agendamento preservados | Sim |
| INC-05/07/08: resultado salvo antes de aplicar, restart e replay | `src/test/java/br/com/monitor/IncidentLifecycleIT.java:281` resultado original; :282 DOWN; :283 confirmação=120; :287 monitor integral após replay; :288/289 counts=1 | Sim |
| INC-08: rollback deixa reserva e resultado recuperáveis | `src/test/java/br/com/monitor/IncidentLifecycleIT.java:303` reserva presente; :304 contador=2; :305/306 counts=0; :307 resultado=3; :311 DOWN após restart; :313 HTTP único | Sim |
| INC-07/08: corrida pausa/terceira falha serializada | `src/test/java/br/com/monitor/IncidentLifecycleIT.java:335` estado conforme vencedor; :337/338/339 todos counts=1 ou todos 0; :345 monitor pausado integral após replay | Sim |
| INC-07/08: falha na sincronização do incidente faz rollback de pausa | `src/test/java/br/com/monitor/IncidentLifecycleIT.java:357` DataAccessException; :359/360 monitor/incidente integrais iguais | Sim |
| Integridade de reserva/resultados | `src/test/java/br/com/monitor/IncidentLifecycleIT.java:369` IllegalArgumentException; :370 monitor integral; :371 reserva preservada; :372 resultado ausente; :373/374 zero efeitos | Sim |

Mapeamento reverso: os doze métodos, quinze casos parametrizados, correspondem às doze linhas acima. Cada assertion verifica resultado/estado exigido pelo AC/borda; nenhum teste mock ou fora da spec. Contextos Spring realmente encerrados/reabertos, PostgreSQL persistido durante o cenário. Relógio controlável, sem sleeps de regras temporais. Adequação PASS, sem SPEC_DEVIATION.

Gate final T4: `docker compose run --build --rm tests`, exit 0, 356 testes (138 unitários/218 IT), zero falhas/erros/skips/Checkstyle. Logs `.validation/mon07-t4.log` (duas expectativas pré-MON-07 detectadas) e `.validation/mon07-t4-final.log` (PASS). Quinze casos novos; total da feature até aqui: 55. Nenhum teste anterior removido/desabilitado.

## T5: operação e rastreabilidade

Arquivos: docs/incidents.md, docs/http-checks.md, docs/scheduling.md, README, STATE e SDD. Código/testes inalterados desde 808e7c6. O gate completo final T4 permanece válido: 356 casos recontados diretamente dos XMLs Surefire/Failsafe, zero falhas/erros/skips; Checkstyle sem erros. Build `docker compose build app`: exit 0, log `.validation/mon07-build.log`. Smoke `powershell -NoProfile -ExecutionPolicy Bypass -File scripts/validate-compose.ps1 -NoBuild`: exit 0, 23 PASSs, projeto descartável `mon02-smoke-9940b127c059`, versões exatamente 1,2,3,4,5, histórico idêntico após restart/down-up, sentinela 4242 e runtime UID 10001 sem Maven/javac/socket. Logs `.validation/mon07-smoke.log` e `.validation/mon02-smoke-9940b127c059.log`. Cleanup removeu somente recursos próprios do smoke.

| MON-07 AC | Evidência de resultado | Validação do autor |
| --- | --- | --- |
| AC01 | IncidentStateMachineTest:31/34/37; IncidentProcessorIT:84/88/92/93 | PASS |
| AC02 | IncidentStateMachineTest:61/63; IncidentProcessorIT:143/146 | PASS |
| AC03 | IncidentSchemaIT:71/92/94; IncidentProcessorIT:249/250/251 | PASS |
| AC04 | IncidentStateMachineTest:78/83/87; IncidentProcessorIT:160/166/167 | PASS |
| AC05 | IncidentProcessorIT:205/210/222/232; IncidentLifecycleIT:206/207 | PASS |
| AC06 | IncidentStateMachineTest:103/112/155/160; IncidentProcessorIT:178/185/190 | PASS |
| AC07 | IncidentLifecycleIT:137/139/152/167/175/253/259/281/287 | PASS |
| AC08 | IncidentProcessorIT:96/97/118/119/125/268/290/310; IncidentLifecycleIT:303/311 | PASS |

As tabelas T1–T4 dão os caminhos completos e as assertions exatas; cada cenário tem origem definida e mapeamento reverso. Documentação confrontada com V5, fluxo transacional e resultados executados; sem teste que apenas procure frases. Spec --strict/tasks: zero erros/avisos. AD-009 é acrescentada sem substituir AD-001 a AD-008. CI remota/push não executados. Verificação independente e sensor ainda pendentes neste commit.
