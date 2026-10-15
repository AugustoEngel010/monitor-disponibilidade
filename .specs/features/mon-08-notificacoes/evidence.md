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


## T2: reserva e conclusão

Premissas: uma instância, cinco tentativas e cadeia por incidente. Arquivos: NotificationStore, NotificationDelivery, NotificationStoreIT e NotificationFixture, além de status/rastreabilidade. Sucesso: estado SQL e conteúdo de histórico nos limites exatos da spec.

| Critério | Assertion file:line | Resultado definido | Coberto |
| --- | --- | --- | --- |
| MAIL-04 espera/limite | `src/test/java/br/com/monitor/NotificationStoreIT.java:60` `claim(due.minusMillis(1)).isEmpty()`; :77 `containsEntry("next_attempt_at", Timestamp.from(due))`; :80 `containsEntry("status", "EXHAUSTED")`; :85 `isEqualTo(5)` | Esperas 30/60/120/240; cinco tentativas | Sim |
| MAIL-05 ID/histórico | `src/test/java/br/com/monitor/NotificationStoreIT.java:62` `id().isEqualTo(id)`; :64 Message-ID exato; :70 início/término/outcome/error com valores exatos | Mesma intenção; horários e erro fixo | Sim |
| MAIL-03 claim concorrente | `src/test/java/br/com/monitor/NotificationStoreIT.java:98` `deliveries.hasSize(1)`; :103 `row(id).isEqualTo(before)` | Uma reserva, conclusão idempotente | Sim |
| MAIL-02 atomicidade | `src/test/java/br/com/monitor/NotificationStoreIT.java:116` PENDING/count=0; :130 SENDING/count=1; :134 ACCEPTED/count=1 | Rollbacks nas duas escritas de reserva e conclusão recuperáveis | Sim |
| MAIL-02/05 reinício | `src/test/java/br/com/monitor/NotificationStoreIT.java:151` UNCERTAIN/RESTART_UNCERTAIN e horários; :157 status/contagem; :164 Message-ID preservado | Órfã consome tentativa; quinta esgota | Sim |
| MAIL-05 timeout | `src/test/java/br/com/monitor/NotificationStoreIT.java:177` UNCERTAIN/SMTP_UNCERTAIN; :179 antes vazio; :180 mesma intenção na igualdade | Confirmação incerta agenda retry | Sim |
| MAIL-07 ordem | `src/test/java/br/com/monitor/NotificationStoreIT.java:195` `claim(BASE).isEmpty()`; :198 outro incidente; :214 tipo RECOVERY; :218 terminal exato | Recuperação aguarda queda terminal, outros incidentes avançam | Sim |
| MAIL-01/06 snapshot | `src/test/java/br/com/monitor/NotificationStoreIT.java:193` ops@example.org e :194 Original monitor; :216 new@example.org e :217 Changed | Snapshot por transição, independente de edição posterior | Sim |

Reverse mapping: cada assertion da tabela pertence aos MAILs indicados; todos os testes do arquivo correspondem a essas oito linhas. Guidelines JUnit/AssertJ/Checkstyle existentes. Sem testes especulativos, removidos ou desabilitados.

Gate T2: docker compose run --build --rm tests; exit 0, BUILD SUCCESS, 369 testes (138 unitários/231 IT), zero falhas/erros/skips/Checkstyle. Log .validation/mon08-t2.log.


## T3: SMTP

Premissas: Spring Mail/Angus com TLS padrão, conteúdo UTF-8 e Message-ID explícito. Arquivos: NotificationConfiguration, SmtpDelivery, application.yml, pom.xml, NotificationConfigurationTest e SmtpDeliveryIT. Sucesso: captura Mailpit, recusa explícita, indisponibilidade e confirmação incerta sem transação.

| Critério | Assertion file:line | Resultado definido | Coberto |
| --- | --- | --- | --- |
| MAIL-06 campos | `src/test/java/br/com/monitor/SmtpDeliveryIT.java:63` destinatário; :64 remetente; :65 assunto; :66 nome/URL/tipo/incidente/instantes; :70 recuperação e duração 180 s | Valores congelados e quatro horários distintos | Sim |
| MAIL-05 ID estável | `src/test/java/br/com/monitor/SmtpDeliveryIT.java:61` `MessageID.isEqualTo(expected.messageId()...)`; :78 `downCount.isEqualTo(2)` | Retry repete ID | Sim |
| MAIL-04 SMTP indisponível/rejeição | `src/test/java/br/com/monitor/SmtpDeliveryIT.java:85` e :97 `isEqualTo(Result.FAILED)` | Falha conhecida | Sim |
| MAIL-05 timeout após DATA | `src/test/java/br/com/monitor/SmtpDeliveryIT.java:128` `isEqualTo(Result.UNCERTAIN)`; :130 `isBetween(250L, 2500L)` | Confirmação incerta e leitura finita | Sim |
| MAIL-02 transação | `src/test/java/br/com/monitor/SmtpDeliveryIT.java:140` `hasMessage("SMTP cannot run inside a transaction")` | Rede proibida dentro da transação | Sim |
| Timeouts/ambiente | `src/test/java/br/com/monitor/NotificationConfigurationTest.java:21`–:25 valores host/porta/credenciais/TLS/5000/3000/5000; :36 erro fixo; :44 TLS conflitante | Configuração externa finita e falha sanitizada | Sim |

Reverse mapping: todas as assertions correspondem aos critérios acima; manter todas. Sem assertion exclusiva de mock ou escopo extra. Checkstyle/JUnit/AssertJ conforme projeto. Transporte distingue falha antes de conexão SMTP de erro após conexão; fechar QUIT não desfaz aceitação DATA já observada.

Gate T3: docker compose run --build --rm tests; exit 0, BUILD SUCCESS, 380 testes (144 unitários/236 IT), zero falhas/erros/skips/Checkstyle. Mailpit v1.27.8 capturou três mensagens fictícias. Log .validation/mon08-t3.log.


## T4: runtime

Premissas: instância anterior parada, polling em executor próprio, testes anteriores com worker desabilitado somente no classpath de teste. Arquivos: NotificationRuntime, application.yml, test application.properties e NotificationRuntimeIT. Sucesso: contexto reinicia com PENDING/SENDING; SMTP lento não bloqueia HTTP/processamento; falha de conclusão não reenvia dentro do mesmo processo.

| Critério | Assertion file:line | Resultado definido | Coberto |
| --- | --- | --- | --- |
| MAIL-02 pending/restart | `src/test/java/br/com/monitor/NotificationRuntimeIT.java:90` `count(...ACCEPTED).isEqualTo(2)`; :91 status ACCEPTED/count1; :98 mesmo incidente | Dois avisos pendentes retomados no contexto novo | Sim |
| MAIL-05 confirmação perdida | `src/test/java/br/com/monitor/NotificationRuntimeIT.java:108` PENDING/count1/next BASE+30; :111 RESTART_UNCERTAIN; :118 total2; :120 mesmo Message-ID | Uma aceitação perdida permite duas cópias, mesma intenção | Sim |
| MAIL-02 falha de confirmação local | `src/test/java/br/com/monitor/NotificationRuntimeIT.java:136` SENDING/count1; :141 ACCEPTED/count1; :143 Mailpit total1 | Repetir gravação sem repetir rede | Sim |
| MAIL-02 isolamento | `src/test/java/br/com/monitor/NotificationRuntimeIT.java:179` SUCCESS; :180 process true; :181 UP; :182 SENDING1 enquanto SMTP espera | Verificação HTTP e domínio progridem | Sim |
| Limite polling | `src/test/java/br/com/monitor/NotificationRuntimeIT.java:221` `hasMessage("Invalid notification polling interval")` | Positivo até 60000 ms | Sim |

Reverse mapping: testes do arquivo correspondem às cinco linhas acima; MAIL-07 tem verificação direta de elegibilidade/ordem na T2, integrada aqui ao envio dos dois avisos. Guidelines JUnit/AssertJ/Checkstyle, nenhuma assertion anterior removida ou enfraquecida.

Cenário adicional MAIL-02: `src/test/java/br/com/monitor/NotificationRuntimeIT.java:205` verifica PENDING/count1/next BASE+30 após resposta de commit perdida; :208 verifica UNCERTAIN/RESTART_UNCERTAIN; :212 ACCEPTED/count2; :214 Mailpit total1. Reconciliação conservadora também ocorre após claim com confirmação JDBC incerta, sem precisar reiniciar o processo. Gate focado: seis runtime IT e seis testes de configuração aprovados, exit 0 (.validation/mon08-runtime-focused.log). Helper DELETE do Mailpit corrigido para aceitar corpo textual, sem alterar nenhuma assertion.

Gate T4: docker compose run --build --rm tests; exit 0, BUILD SUCCESS, 386 testes (144 unitários/242 IT), zero falhas/erros/skips/Checkstyle. docker compose build app: exit 0. Logs .validation/mon08-t4.log e .validation/mon08-build.log.


## T5: operação

Premissas: Mailpit local sem relay, credenciais efêmeras, projeto Compose exclusivo e nenhum acesso ao banco do operador. Arquivos: compose.yaml, .env.example, scripts/validate-compose.ps1, docs/notifications.md, docs/incidents.md, README, STATE, backlog e artefatos MON-08. Sucesso: gates Docker, ciclo de app/db e captura local dos avisos reais.

| Critério | Assertion file:line | Resultado definido | Coberto |
| --- | --- | --- | --- |
| MAIL-01 queda/recuperação | `scripts/validate-compose.ps1:146` `Wait-NotificationQuery ... DOWN`; :154 count2 | Dois avisos, um por transição | Sim |
| MAIL-02 restart | `scripts/validate-compose.ps1:151` `Assert-Equal ... $intent` | Mesma intenção após restart app | Sim |
| MAIL-04 falha real | `scripts/validate-compose.ps1:147` count FAILED=1 | SMTP parado registra falha | Sim |
| MAIL-05 histórico | `scripts/validate-compose.ps1:156` ACCEPTED=2; :157 SMTP_FAILURE=1 | Duas aceitações e uma falha sanitizada | Sim |
| MAIL-06 SMTP local | `scripts/validate-compose.ps1:160` Mailpit total2; :163 destinatário ops@example.org; :164 nome/incidente no corpo | Mensagens capturadas com payload fictício | Sim |
| MAIL-07 | `scripts/validate-compose.ps1:155` ACCEPTED=2 | Queda pendente retomada e recuperação entregue | Sim |

Reverse mapping: cada assertion pertence ao MAIL indicado; gates de infraestrutura anteriores preservados. Critérios temporais, unicidade, esgotamento e incerteza têm evidência detalhada T1–T4; smoke complementa a suite no runtime empacotado.

Comandos: docker compose build app (exit 0); powershell -NoProfile -ExecutionPolicy Bypass -File scripts/validate-compose.ps1 -NoBuild (exit 0). Smoke aprovado: .validation/mon02-smoke-d335da5dbcde.log, 12 PASSs de notificação, com falha SMTP, pending atravessando restart, recuperação e duas capturas. Corrigido helper para reler porta efêmera do Mailpit após start; nenhum critério foi alterado. Log geral .validation/mon08-smoke.log. Volumes/projeto descartáveis removidos ao concluir.

Gate T5 Full final: docker compose run --build --rm tests; exit 0, BUILD SUCCESS, 386 testes (144 unitários/242 IT), zero falhas/erros/skips/Checkstyle. Log .validation/mon08-final.log. validate_spec e validate_tasks: zero erros/avisos. git diff --check aprovado.


## T6: precisão da duração

Verifier independente: seis ACs PASS e gap Minor em AC06, diferença exata 180.9 apresentada como 180. Sua prova e seis mutações mortas foram preservadas no relatório anterior; lesson L-001 registrada pela skill. Premissas: a fórmula de MON-01 permanece a diferença exata; apresentação decimal usa ponto e remove somente zeros finais. Arquivos: SmtpDelivery, novo SmtpDurationIT, spec/tasks/design/evidence/validation, docs/notifications, README, STATE/backlog e artefatos de lessons do Verifier. Sucesso: captura SMTP de 180.9 segundos, preservando o caso inteiro 180 e todos os testes anteriores.

| Critério | Assertion file:line | Resultado da spec | Coberto |
| --- | --- | --- | --- |
| MAIL-06 fração | `src/test/java/br/com/monitor/SmtpDurationIT.java:42` `assertThat(body).contains("Primeira falha: 2026-10-15T12:00:00.100Z", "Início da recuperação: 2026-10-15T12:03:01Z", "Duração estimada: 180.9 segundos")` | Diferença exata 180.9, sem truncamento | Sim |
| MAIL-06 aceitação real | `src/test/java/br/com/monitor/SmtpDurationIT.java:28` `isEqualTo(NotificationStore.Result.ACCEPTED)`; :36 `isEqualTo(1)` | Um e-mail real capturado no Mailpit | Sim |
| MAIL-06 inteiro preservado | `src/test/java/br/com/monitor/SmtpDeliveryIT.java:71` contém duração 180 segundos | Não introduzir .0 no caso inteiro existente | Sim |

Reverse mapping: cada assertion do teste novo valida aceitação, captura ou conteúdo do mesmo MAIL-06; nenhuma assertion anterior modificada. BigDecimal combina segundos e nanos sem overflow de conversão da duração inteira para nanos; saída decimal simples. Guidelines JUnit/AssertJ/Checkstyle preservadas.

Gate T6: docker compose run --build --rm tests, exit 0, BUILD SUCCESS, 387 testes (144 unitários/243 IT), zero falhas/erros/skips/Checkstyle; docker compose build app, exit 0; smoke Compose, exit 0 e 37 PASSs (.validation/mon02-smoke-b1830bfcfb1a.log). Logs .validation/mon08-t6.log, .validation/mon08-t6-build.log e .validation/mon08-t6-smoke.log. validate_spec/tasks: zero erros/avisos. Sem assertion anterior alterada.


## Fechamento independente

Verifier PASS 7/7 após T6; gate independente com 387 testes (144 unitários/243 IT), zero falhas/erros/skips/Checkstyle, runtime e smoke aprovados. Sensor total 7/7 mutações mortas: seis iniciais sobre código pertinente inalterado e reintrodução de truncamento sobre f2194e2, morta pelo novo SmtpDurationIT. Isolamento de scratch e porcelain confirmados. Evidências exatas, comandos/logs e assertions por AC em [validation.md](validation.md). Primeira revisão encontrou um gap de precisão, corrigido por T6; nenhuma lacuna restante. validate_state aprovado. Sem alteração em V1–V5 ou remoção/desabilitação de testes. CI remota não executada.

