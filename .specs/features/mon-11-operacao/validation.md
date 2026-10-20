# MON-11: validação independente

## Validation: PASS

T10 fecha F1: uma única chamada ao runtime drena o backlog, e retirar a continuação agora falha pela contagem SQL. Os seis ACs estão cobertos. Gate Java, runtime e smoke foram repetidos independentemente no HEAD final. O relatório inicial FAIL permanece em `validation-initial.md`.

**Data:** 2026-10-20. **Verifier:** subagente independente, autor diferente do verificador.
**Spec:** `.specs/features/mon-11-operacao/spec.md`.
**Diff range:** `6fb6cf0..d3e0fef`, HEAD `d3e0fef`. Revalidação após o fix T10.

## Tasks e escopo

T1..T10 têm Done when marcado e commits atômicos. Spec, design, tasks, diff, implementação e assertions foram inspecionados diretamente. A mudança `e82146d..d3e0fef` em produção/testes contém somente extração de `RetentionRuntime.tick()` e fortalecimento do teste de rodada em `RetentionIT`. Nenhum teste foi removido, desabilitado ou enfraquecido. A documentação de fechamento e a atualização de rastreabilidade ficam com o orquestrador; esta revisão não altera código, spec, tasks ou STATE.

## Critérios ancorados na spec

| AC / requisito | Resultado exigido | Arquivo:linha e assertion/estado verificado | Resultado |
| --- | --- | --- | --- |
| MON-11-AC01 / OPS-01 | PostgreSQL indisponível: readiness503/DOWN e liveness200/UP; três probes públicos status-only; métricas Basic | `src/test/java/br/com/monitor/OperationHealthIT.java:38` métricas sem Basic=401; `:40` autenticadas=200; `:60` readiness503; `:61` JSON exato DOWN; `:63` liveness200; `:64` JSON exato UP. `src/test/java/br/com/monitor/AdministrativeSecurityIT.java:101` parametriza os três probes; `:106` exige corpo exato UP, sem componentes, e `:107` sem cookie | PASS |
| MON-11-AC02 / OPS-02 | inFlight/capacidade reais; atraso não negativo; duração e nove Outcome; backlog PENDING/SENDING/EXHAUSTED; tags sem URL/UUID | `src/test/java/br/com/monitor/OperationMetricsTest.java:49` inFlight=1 enquanto worker bloqueado; `:50` capacidade4; `:53` idle e inFlight0; `:55` atraso3s; `:57` duração125ms HTTP_FAILURE; `:59` contador1; `:60` total21 medidores; `:62` tags exclusivamente outcome enum. `:74` itera três estados; `:76` gauge7; `:79` erro SQL produz NaN. `src/main/java/br/com/monitor/monitoring/CheckScheduler.java:70` limita atraso negativo a ZERO | PASS |
| MON-11-AC03 / OPS-03 | Eventos JSON correlacionáveis, vocabulário fixo, sem conteúdo sensível; rollback sem confirmação | `src/test/java/br/com/monitor/OperationalLogsIT.java:36` CHECK_RECORDED compara monitorId/executionId/HTTP_FAILURE; `:40` compara os quatro UUIDs reais em transição/entrega; `:44` Throwable null; `:46` seis campos exatos; `:48` sem URL/nome; `:50` DOWN; `:53` FAILED; `:62` rollback mantém quatro eventos. `src/test/java/br/com/monitor/OperationHealthIT.java:52` desserializa ECS real e `:53` a `:57` compara IDs, INTERNAL_ERROR e ausência de segredo/Authorization/stack_trace | PASS |
| MON-11-AC04 / OPS-04 | Corte estrito, proteção da cadeia e evidência, reservas/idempotência; lote/watermark atômicos; uma rodada drena backlog e shutdown interrompe entre commits | `src/test/java/br/com/monitor/RetentionIT.java:33` remove1; `:36` igualdade e `:37` +1µs mantidos; `:38` recibo true; `:39` reaplicação false; `:40`/`:41` replay original/alterado rejeitado. `:59` incidentes2, `:60` notificações3 e `:63` SENDING; `:84` conclusão igual ao corte mantém; `:86` +1µs remove cadeia; `:107` última observação e `:108` último APPLIED interno preservados; `:110` ordenação mantida; `:121`/`:122` resultado não aplicado/reservado mantidos; `:132` watermark não recua; `:137` TTL aumentado não recupera histórico; `:204`/`:205`/`:206` rollback sem recibos, aplicações2, watermark null. `:146` chama tick uma vez, sem start/await; `:147` checks1 e `:148` recibos5 imediatamente; `:178`/`:179`/`:180` shutdown real mantém primeiro lote confirmado: checks4, recibos2, aplicações4 | PASS; F1 fechado por M7 |
| MON-11-AC05 / OPS-05 | Limite e retainedOutPeriods; janela removida NO_DATA/null/zero; proteção operacional não fabrica disponibilidade | `src/test/java/br/com/monitor/RetentionQueriesIT.java:28` evidência protegida permanece; `:30` watermark=base; `:31` período removido exato; `:33` NO_DATA; `:34` a `:37` quatro contagens0; `:38`/`:39` taxa/latência null; `:41` checks vazios; `:56` janela cruzada conta1; `:60` retenção separada da lacuna; `:87` observação antiga não cobre janela pós-corte; `:96` watermark null preserva contrato anterior. `src/test/java/br/com/monitor/RetentionQueryApiIT.java:56` quatro endpoints200; `:58` watermark exato; `:60`/`:61` período exato; `:63` NO_DATA; `:64` null; `:65` amostras0. `config/panel/retention.spec.js:26` limite visível; `:28` datetime exato; `:30` aviso condicional; `:32`/`:33` Sem dados | PASS |
| MON-11-AC06 / OPS-06 | Relatório local ≥60s com recursos/parâmetros; C≤4, sem sobreposição, limite100, atraso/outcomes/backlog | `src/test/java/br/com/monitor/CapacityScenario.java:99` 101º rejeitado MONITOR_LIMIT; `:128` inFlight≤4 em cada rodada; `:161` duração≥60; `:162` pico4; `:163` overlaps0; `:165` monitores100; `:167` todos100 observados; `:169` SUCCESS/HTTP_FAILURE/TIMEOUT sem INTERNAL_ERROR/DESTINATION_BLOCKED; `:172` PENDING50; `:186` EXHAUSTED50. `docs/capacity/mon-11.json:3` duração60.070333095s; `:20` pico4; `:21` overlaps0; `:25` checks285; `:26` atraso máximo20.634817s | PASS por inspeção e execução reutilizada |

Seis ACs com resultados observáveis e assertions correspondentes; nenhuma lacuna de precisão identificada. Os testes verificam valores/estados e payloads, além da ocorrência de chamadas.

## Gate Build e evidências

| Gate | Comando / proveniência | Resultado |
| --- | --- | --- |
| Java completo independente no HEAD final | `docker compose run --build --rm tests` | exit0, **482/482**:169 unitários+313 IT, zero falhas/erros/skips. `.validation/mon11-reverify-java.log:85` Checkstyle0; `:145` unitários169; `:2489` IT313; `:2494` BUILD SUCCESS |
| Runtime independente no HEAD final | `docker compose build app` | exit0; `.validation/mon11-reverify-runtime.log:162` imagem construída |
| Smoke independente no HEAD final | `powershell -NoProfile -ExecutionPolicy Bypass -File scripts/validate-compose.ps1 -NoBuild` | exit0, **37 PASS**; `.validation/mon11-reverify-smoke.log:77` sucesso; `.validation/mon02-smoke-88a05ebac747.log:387` lifecycle PASS. Projeto/rede/volume exclusivos removidos |
| Spec/tasks estruturais independentes | `python .codex/skills/tlc-spec-driven/scripts/validate_spec.py mon-11-operacao` e `validate_tasks.py mon-11-operacao` | ambos exit0; spec0 avisos; tasks somente aviso Tests:none T7, consistente com camada documental |
| UI reutilizada | `.validation/mon11-t5-panel.log:290` | **50 passed**; não reexecutada nesta revisão. DTOs/assets/painel e seus testes não mudaram desde o gate; diff posterior foi conferido |
| Capacidade reutilizada | `.validation/mon11-t6-capacity-final.log:135`, `:1047`, `:1052` e `docs/capacity/mon-11.json` | **169 unitários+1 cenário**, zero falhas/erros/skips, BUILD SUCCESS. Não reexecutado nesta revisão; cenário e fase medida idênticos. T10 altera somente runtime de retenção, desabilitado no cenário, e seu teste. SQL de elegibilidade, scheduler e DTOs permanecem idênticos |

Java/Maven executados exclusivamente em Docker. Python e PowerShell no host somente validaram/orquestraram. Baseline MON-10 registrada:459 Java e48 UI; atual482 Java e50 UI, delta+23 Java/+2 UI; nenhum skip ou redução de assertion. Sem execução de CI remota ou UAT humano; painel tem evidência automatizada de browser.

## Sensor de discriminação

Profundidade expandida por integridade de dados: **7 mutações avaliadas, 7 mortas, 0 sobreviventes no fechamento**. Nesta revalidação, M7 foi repetido independentemente sobre d3e0fef. Os seis kills iniciais foram reutilizados após inspeção de logs e equivalência dos branches/assertions; não são apresentados como novas execuções.

| Mutante | Arquivo:linha e comportamento removido | Evidência | Resultado |
| --- | --- | --- | --- |
| M1, reutilizado | `src/main/java/br/com/monitor/operation/RetentionService.java:65`, finished_at `<` → `<=` | `.validation/mon11-verifier-M1.log:182`:12 IT,1 falha,0 erros/skips; esperava1 remoção, recebeu2 | MORTO, exit1 |
| M2, reutilizado | `src/main/java/br/com/monitor/operation/RetentionService.java:44`, cutoff monotônico → somente proposto | `.validation/mon11-verifier-M2.log:183`:12 IT,1 falha; esperava2, recebeu0 ao recuar relógio | MORTO, exit1 |
| M3, reutilizado | `src/main/java/br/com/monitor/operation/RetentionService.java:68`, remove proteção da última observação | `.validation/mon11-verifier-M3.log:183`:12 IT,1 falha; evidência SUCCESS específica esperada, Optional vazio | MORTO, exit1 |
| M4, reutilizado | `src/main/java/br/com/monitor/operation/RetentionService.java:51`, completed_at `>=` → `>` | `.validation/mon11-verifier-M4.log:183`:12 IT,1 falha; esperava0 incidentes removidos, recebeu1 na igualdade | MORTO, exit1 |
| M5, reutilizado | `src/main/java/br/com/monitor/query/SummaryQueries.java:58` e `:100`, ignora watermark em amostras/cobertura | `.validation/mon11-verifier-M5.log:124`:5 IT,3 falhas; NO_DATA→HAS_DATA, checks1→2 e lacuna removida | MORTO, exit1 |
| M6, reutilizado | `src/main/java/br/com/monitor/operation/OperationalEvents.java:25`, afterCommit → afterCompletion | `.validation/mon11-verifier-M6.log:122`:1 IT,1 falha; quatro eventos esperados, cinco após rollback | MORTO, exit1 |
| M7, repetido nesta revisão | `src/main/java/br/com/monitor/operation/RetentionRuntime.java:46`, continuação → false | `.validation/mon11-reverify-M7.log:180`:12 IT,1 falha,0 erros/skips; `:184` expected1, `:185` actual4; `:186` falha em RetentionIT.java:147. Dois unitários passam (`:58`); BUILD FAILURE (`:204`) resulta de assertion SQL, sem erro de compilação/infraestrutura | MORTO, exit1 |

M1..M6: `git diff e82146d..d3e0fef` não modifica RetentionService, SummaryQueries, OperationalEvents ou testes RetentionQueriesIT/OperationalLogsIT. Em RetentionIT muda somente o teste de rodada e seus imports; assertions de fronteira, monotonicidade e proteção usadas por M1..M4 permanecem idênticas. O histórico do M7 sobrevivente anterior continua registrado no relatório inicial; ele não foi apagado ou contado como kill daquela execução.

Reprodução M7: `.validation/mon11-reverify-sensor.ps1`. Scratch por `git archive HEAD`, extraído em `.validation/mon11-reverify-scratch` e montado sobre `/workspace`. Comando: `docker compose run --rm -v <scratch>:/workspace tests mvn --batch-mode --no-transfer-progress clean verify -Dtest=OperationMetricsTest -Dit.test=RetentionIT`. Somente a cópia de RetentionRuntime recebeu a mutação; nunca foi usado stash.

Isolamento confirmado antes de escrever este relatório: `.validation/mon11-reverify-porcelain-before.txt` e `-after.txt` são vazios, normalizados para LF, ambos SHA256 `e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855`. `.validation/mon11-reverify-sensor-result.txt:2` registra ISOLATION=PASS. Scratch/tar removidos após verificar caminhos absolutos sob `.validation`; Test-Path confirmou ambos ausentes. Árvore real permaneceu limpa durante o sensor.

## Edges, qualidade e limites

Corte −1µs/igualdade/+1µs, relógio recuado, TTL aumentado, lote limitado, repetição, replay, rollback, cadeias PENDING/SENDING/EXHAUSTED, reservas e épocas protegidas estão cobertos em RetentionIT. `src/test/java/br/com/monitor/RetentionMigrationIT.java:25` exige exatamente uma migração V8; `:26` preserva checksums V1–V7; `:28` reaplicação0; `:29` preserva sentinela4242. Consultas cobrem janela removida/cruzada/pós-corte, páginas vazias e watermark null. Eventos comparam IDs e resultados exatos e silenciam rollback.

Mudanças seguem JDBC, transações, Clock, Micrometer e workers existentes; sem dependência runtime nova, abstração desnecessária ou escopo além de OPS-01..06. Basic/CSRF e arquitetura anteriores passam no gate completo. Diretrizes conferidas em `.codex/skills/tlc-spec-driven/references/coding-principles.md`, design e contratos do projeto. Testes novos mapeiam saúde/métricas, eventos, retenção/migração/runtime, consultas/API/painel ou capacidade; nenhum teste sem requisito identificado.

A capacidade mede HTTP/SMTP locais reais; coleta/chamadas sequenciais do harness entram no custo. A fase acelerada avança o relógio lógico uma hora por retry e usa SMTP421 real; não mede cinco retries em tempo real. Atraso máximo20.634817s evidencia saturação de I=10s; não há promessa de SLA10s ou throughput universal. Recibos UUID duráveis crescem, portanto retenção não promete disco constante.

**Gaps abertos:** nenhum. **F1:** resolvido por T10 e M7 morto independentemente. **Lições:** L-006 já registra o sinal inicial; não duplicada, nenhuma nova falha nesta revalidação. **Rastreabilidade recomendada:** OPS-01..06 Verified; orquestrador atualiza os status finais após este PASS.
