# MON-11: validação independente

**Verdict: FAIL.** A implementação passa no Build independente, mas o teste de drenagem aceita remover o backlog em rodadas distintas. O mutante que elimina a continuação da rodada sobreviveu. Falta discriminação para o Done when de T9, associado a OPS-04.

**Data:** 2026-10-20. **Verifier:** subagente independente, autor diferente do verificador.
**Spec:** `.specs/features/mon-11-operacao/spec.md`.
**Diff range:** `6fb6cf0..e82146d`, HEAD `e82146d`.

## Escopo e conclusão

Spec, design, tasks, diff, implementação e assertions foram inspecionados diretamente. A matriz abaixo foi derivada dos resultados exigidos pela spec. O relatório do autor não substituiu a execução independente do Build. Não foram alterados código de produção, testes reais, spec, tasks ou STATE. Não houve commit, push, deploy ou acesso ao banco do operador.

T1–T8 têm implementação e evidências compatíveis com seus entregáveis. T9 está implementada, mas sua verificação é insuficiente. Os nove commits da faixa são atômicos e correspondem às nove tasks; as alterações anteriores em testes de migração ajustam V8 ou fixam o alvo V7 específico, sem excluir testes nem enfraquecer seus resultados.

## Critérios ancorados na spec

| AC / requisito | Resultado exigido | Arquivo:linha e assertion exata ou estado verificado | Resultado |
| --- | --- | --- | --- |
| MON-11-AC01 / OPS-01 | PostgreSQL indisponível: readiness 503/DOWN e liveness 200/UP; probes públicos somente status; métricas com Basic | `src/test/java/br/com/monitor/OperationHealthIT.java:60` `readiness.statusCode()).isEqualTo(503)`; `:61` body `{"status":"DOWN"}`; `:63` status 200; `:64` body `{"status":"UP"}`. `:38` métricas sem credencial 401; `:39` autenticadas 200. `src/test/java/br/com/monitor/AdministrativeSecurityIT.java:101` parametriza os três probes, `:105` status 200 e `:106` body exato `{"status":"UP"}` | PASS |
| MON-11-AC02 / OPS-02 | inFlight real, C, atraso não negativo, duração, nove Outcome e outbox PENDING/SENDING/EXHAUSTED; tags limitadas | `src/test/java/br/com/monitor/OperationMetricsTest.java:49` inFlight=1 durante barreira; `:50` capacidade=4; `:53` inFlight=0 depois; `:54` atraso=3s; `:56` duração HTTP_FAILURE=125ms; `:58` contador=1; `:60` 21 meters, correspondentes a nove pares de Outcome mais três meters comuns; `:61` todas as tags são outcome de enum. `:76` três gauges=7; `:79` erro SQL retorna NaN. `src/main/java/br/com/monitor/monitoring/CheckScheduler.java:79` limita atraso negativo a Duration.ZERO | PASS |
| MON-11-AC03 / OPS-03 | JSON com correlação, vocabulário fixo, sem dados sensíveis e sem confirmação de rollback | `src/test/java/br/com/monitor/OperationalLogsIT.java:36` evento CHECK_RECORDED com monitorId/executionId/outcome=HTTP_FAILURE; `:40` compara os quatro UUIDs reais nas transições/entregas; `:44` Throwable null; `:46` allowlist exata de seis campos; `:48` sem URL/nome; `:50` DOWN; `:53` NOTIFICATION_FINISHED/FAILED; `:62` permanece com quatro eventos após rollback. `src/test/java/br/com/monitor/OperationHealthIT.java:52` a `:57` desserializa JSON ECS real e compara os quatro IDs, INTERNAL_ERROR e ausência de segredo/Authorization/stack_trace | PASS |
| MON-11-AC04 / OPS-04 | Corte estrito; preservar cadeias, reservas, evidência, idempotência; lote e watermark atômicos; T9 exige drenagem numa rodada | `src/test/java/br/com/monitor/RetentionIT.java:35` remove1; `:38` igualdade mantida; `:39` +1µs mantido; `:40` recibo true; `:41` reaplicação false; `:42` e `:43` replay original/alterado rejeitado com mensagem fixa; `:62` três notificações preservadas; `:65` SENDING; `:86` conclusão exatamente no corte mantém incidente; `:88` +1µs remove cadeia; `:109` última observação exata mantida; `:110` último APPLIED interno mantido; `:112` preserva ordenação; `:123` não processado mantido; `:124` reservado mantido; `:134` watermark não recua; `:139` TTL aumentado não recupera histórico; `:208` rollback sem recibos; `:209` aplicações restauradas; `:210` watermark null. `:150`/`:151` contam backlog drenado, mas não distinguem a rodada. M7 sobreviveu | PASS funcional; GAP de discriminação T9 |
| MON-11-AC05 / OPS-05 | Limite e retainedOutPeriods; janela removida sem amostras/taxa/latência; evidência protegida não fabrica cobertura | `src/test/java/br/com/monitor/RetentionQueriesIT.java:28` evidência protegida permanece; `:30` watermark=base; `:31` período removido exato; `:33` NO_DATA; `:34` a `:37` quatro contagens zero; `:38`/`:39` taxa e latência null; `:41` checks vazios; `:56` janela cruzada conta somente1; `:60` período removido separado; `:61` lacuna começa no corte; `:87` observação antiga não cobre janela pós-corte; `:96` watermark null preserva contrato anterior. `src/test/java/br/com/monitor/RetentionQueryApiIT.java:56` quatro recursos HTTP200; `:58` watermark exato; `:60`/`:61` limites do período; `:63` NO_DATA; `:64` null; `:65` amostras0. `config/panel/retention.spec.js:26` limite visível; `:28` datetime exato; `:30` aviso condicionado ao período removido; `:32`/`:33` Sem dados | PASS |
| MON-11-AC06 / OPS-06 | Medição local ≥60s com recursos/parâmetros, C≤4, sem sobreposição e limite100 | `src/test/java/br/com/monitor/CapacityScenario.java:99` 101º monitor rejeitado com MONITOR_LIMIT; `:128` inFlight≤4 a cada rodada; `:161` duração≥60; `:162` pico4; `:163` sobreposição0; `:165` monitores100; `:167` todos100 observados; `:169` SUCCESS/HTTP_FAILURE/TIMEOUT sem INTERNAL_ERROR/DESTINATION_BLOCKED; `:172` PENDING50; `:186` EXHAUSTED50. `docs/capacity/mon-11.json:3` duração60,070333095s, parâmetros e recursos persistidos; atraso máximo20,634817s, médio10,96773021754386s e285 checks | PASS por inspeção e evidência de execução reutilizada |

Seis ACs têm resultados observáveis definidos e assertions correspondentes. Não foi identificada lacuna de precisão na spec. A lacuna aberta é específica da verificação da rodada em T9; ela impede o fechamento global.

## Edges, payloads e qualidade

- Corte de checks em −1µs, igualdade e +1µs; igualdade de recovered_at e completed_at; limpeza repetida; limite de lote; relógio recuado; TTL aumentado: `RetentionIT.java:29`, `:73`, `:129`, `:232`.
- Cadeia completa com incidente aberto, recuperação PENDING, tentativa SENDING e entrega EXHAUSTED: `RetentionIT.java:51`, `:73`, `:232`. Comparações SQL verificam contagens e estados, incluindo tentativas.
- Última observação e último APPLIED interno, resultado não aplicado, reserva e épocas protegidas: `RetentionIT.java:97`, `:116`, `:188`. `RetentionMigrationIT.java:14` verifica upgrade V7/V8, dados, checksums e reaplicação zero.
- Falha SQL controlada reverte payload, aplicações, recibos e watermark: `RetentionIT.java:198`. Shutdown mantém somente o primeiro lote confirmado: `:182` checks4, `:183` recibos2 e `:184` aplicações4. Esse teste permanece discriminante para interrupção, mas não supre F1.
- Janela removida/cruzada/pós-corte, metadados mesmo em página vazia e watermark null: cinco casos de `RetentionQueriesIT`; serialização real dos quatro endpoints em `RetentionQueryApiIT`.
- Eventos verificam valores dos UUIDs e resultados, além da presença dos campos. JSON ECS tem teste real separado. O sensor confirma silêncio após rollback.
- Basic/CSRF e arquitetura existentes são preservados. Não há dependência runtime nova. Os componentes de operação seguem JDBC, transações, Clock, Micrometer e workers existentes. Padrões conferidos em `references/coding-principles.md`, design e contratos do projeto.
- Os novos testes mapeiam OPS-01 (saúde), OPS-02 (métricas), OPS-03 (eventos), OPS-04 (migração/retenção/runtime), OPS-05 (consultas/API/painel) e OPS-06 (cenário opt-in). Nenhum teste removido, desabilitado ou com assertion reduzida.

## Build e demais gates

| Gate | Comando / proveniência | Resultado |
| --- | --- | --- |
| Java completo independente | `docker compose run --build --rm tests` | exit0, **482/482**, 169 unitários e313 IT, zero falhas/erros/skips. `.validation/mon11-verifier-java.log:85` Checkstyle0; `:145` unitários169; `:2500` IT313; `:2505` BUILD SUCCESS |
| Runtime independente | `docker compose build app` | exit0; `.validation/mon11-verifier-runtime.log`, imagem final construída |
| Smoke independente | `powershell -NoProfile -ExecutionPolicy Bypass -File scripts/validate-compose.ps1 -NoBuild` | exit0, **37 PASS**; `.validation/mon11-verifier-smoke.log` e `.validation/mon02-smoke-52f5b815b3f4.log`. Projeto, rede e volume exclusivos removidos |
| Spec/tasks estruturais independentes | `python .codex/skills/tlc-spec-driven/scripts/validate_spec.py mon-11-operacao` e `validate_tasks.py mon-11-operacao` | ambos exit0; spec sem avisos; tasks com único aviso Tests:none de T7, coerente com camada documental |
| UI | Evidência do autor reutilizada: `.validation/mon11-t5-panel.log:290` | **50 passed**, não reexecutada pelo Verifier. Assets/DTOs testados em T5 não mudaram em T8/T9/T6/T7 |
| Capacidade | Evidência do autor reutilizada: `.validation/mon11-t6-capacity-final.log:135`, `:1047`, `:1052` e relatório versionado | **169 unitários +1 cenário**, zero falhas/erros/skips, BUILD SUCCESS. Não reexecutado pelo Verifier. T9 altera a drenagem do runtime desabilitado no cenário e acrescenta more ao retorno; SQL de elegibilidade e fase medida permanecem idênticos |

Gate Build obrigatório foi exercido integralmente pelo Verifier, exclusivamente com Java/Maven em Docker. Python e PowerShell executaram somente validadores/orquestração no host. Contagem anterior registrada em MON-10:459 Java e48 UI; atual482 Java e50 UI, delta+23 Java e+2 UI. Nenhum skip. Não foi executada CI remota nem UAT humano nesta revisão.

A capacidade usa HTTP e SMTP locais reais e componentes de produção chamados pelo harness. A coleta e as chamadas sequenciais do harness têm custo incluído; produção usa pollers separados. O relatório documenta saturação de I=10s e não promete SLA/throughput universal. Recibos duráveis crescem; retenção não promete disco constante.

## Sensor de discriminação

Profundidade expandida para integridade de dados: **7 mutações independentes, 6 mortas, 1 sobrevivente**. Cada execução começou com uma cópia original do arquivo; o original foi restaurado em scratch após o comando. Não houve stash ou mutação da árvore real.

Scratch criado por `git archive HEAD`, extraído em `.validation/mon11-verifier-scratch` e montado sobre `/workspace` do runner Docker. Comando por mutante: `docker compose run --rm -v <scratch>:/workspace tests mvn --batch-mode --no-transfer-progress clean verify -Dtest=OperationMetricsTest -Dit.test=<IT>`. Script reproduzível: `.validation/mon11-verifier-sensor.ps1`; resumo: `.validation/mon11-verifier-sensor-summary.txt`. Todos compilaram e executaram assertions; nenhuma falha de infraestrutura conta como morte.

| Mutante | Arquivo:linha e alteração | Evidência de discriminação | Resultado |
| --- | --- | --- | --- |
| M1 | `src/main/java/br/com/monitor/operation/RetentionService.java:65`, finished_at `<` → `<=` corte | `RetentionIT.java:35`: esperado1 removido, recebido2; 12 IT, uma falha, zero erros/skips; `.validation/mon11-verifier-M1.log:183` | MORTO, exit1 |
| M2 | `src/main/java/br/com/monitor/operation/RetentionService.java:44`, cutoff máximo anterior/proposto → somente proposto | `RetentionIT.java:133`: esperado2 removidos, recebido0 ao recuar relógio; 12 IT, uma falha; `.validation/mon11-verifier-M2.log:189` | MORTO, exit1 |
| M3 | `src/main/java/br/com/monitor/operation/RetentionService.java:68`, proteção last_observed_at → AND true | `RetentionIT.java:109`: esperado CheckResult SUCCESS específico, Optional vazio; 12 IT, uma falha; `.validation/mon11-verifier-M3.log:184` | MORTO, exit1 |
| M4 | `src/main/java/br/com/monitor/operation/RetentionService.java:51`, completed_at `>=` → `>` corte | `RetentionIT.java:86`: esperado0 incidentes removidos, recebido1 na igualdade; 12 IT, uma falha; `.validation/mon11-verifier-M4.log:184` | MORTO, exit1 |
| M5 | `src/main/java/br/com/monitor/query/SummaryQueries.java:58` e `:100`, watermark → NULL nos filtros de amostras/cobertura | `RetentionQueriesIT.java:33`: NO_DATA→HAS_DATA; `:56`: 1→2 checks; `:87`: lacuna esperada desaparece. Cinco IT, três falhas; `.validation/mon11-verifier-M5.log:124` | MORTO, exit1 |
| M6 | `src/main/java/br/com/monitor/operation/OperationalEvents.java:25`, afterCommit → afterCompletion | `OperationalLogsIT.java:62`: quatro eventos esperados, cinco recebidos após rollback; uma IT, uma falha; `.validation/mon11-verifier-M6.log:123` | MORTO, exit1 |
| M7 | `src/main/java/br/com/monitor/operation/RetentionRuntime.java:42`, condição de continuação → false | **12/12 RetentionIT e2/2 OperationMetricsTest passam**, zero falhas/erros/skips; `.validation/mon11-verifier-M7.log:183` e `:192`. Log `:161`–`:163` mostra lotes2/2/1 separados por aproximadamente1s, em polls distintos | **SOBREVIVEU, exit0** |

Isolamento: `git status --porcelain` real retornou vazio antes de criar o scratch e após as mutações. O pipeline PowerShell `git status --porcelain | Set-Content ...` não criou arquivos quando não recebeu linhas; as saídas vazias observadas foram materializadas explicitamente depois. A comparação final com a baseline vazia observada confirmou igualdade. Ambos os arquivos `.validation/mon11-verifier-porcelain-before.txt` e `-after.txt` têm SHA256 `e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855`.

Scratch e tar removidos após verificar que o caminho absoluto ficava dentro de `.validation`; logs e script preservados. Nenhum scratch precisa ser mantido para correção.

## Gap ranqueado e fix

**F1 — Major — OPS-04 / MON-11-AC04 / Done when T9.** `src/test/java/br/com/monitor/RetentionIT.java:143` configura poll=1000ms e aguarda até10s (`:149`). Suas assertions de checks1/recibos5 são satisfeitas por três rodadas diferentes. M7 comprovou que remover toda continuação intra-rodada passa pela suíte. A implementação atual drena corretamente, mas uma regressão para um lote por hora ficaria invisível.

Fix: tornar a execução de uma única rodada controlável e exercitá-la uma única vez, sem iniciar polls subsequentes. Manter as assertions SQL de cinco remoções, cinco recibos e uma evidência protegida, além do teste de shutdown real existente. Gate: original aprovado; M7 deve falhar por contagem SQL incorreta, sem erro de compilação, timeout ou falha de infraestrutura. Preservar a separação transacional dos lotes e Basic/CSRF.

Não há outro gap identificado. A rastreabilidade de OPS-04/T9 deve ficar pendente até correção e revalidação. Spec/tasks/STATE permanecem sob responsabilidade do orquestrador. `validate_state.py` não foi executado como gate de fechamento porque o veredito é FAIL. Lição F1 registrada pelo script da skill: **L-006**, candidate, recorrência1, escopo operation/runtime-tests. As únicas mudanças reais desta revisão são este relatório e os dois arquivos de lições geridos pelo script.
