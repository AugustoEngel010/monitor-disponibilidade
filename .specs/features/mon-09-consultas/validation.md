# MON-09 Validation

**Result**: PASS
**Date**: 2026-10-18 (America/Sao_Paulo; evidências UTC em 2026-10-19).
**Spec**: `.specs/features/mon-09-consultas/spec.md` em `32ae68d`.
**Diff range**: `a2a7df2..32ae68d`.
**Verifier**: subagente independente; autor diferente do verificador.

Revalidação após as correções atômicas T7 `23a9c1c` e T8 `32f4eab` e T9 `32ae68d`. O Verifier não modificou implementação ou testes reais e não criou commits.

## Histórico da primeira verificação

Em `a2a7df2..db60528`, o resultado foi **FAIL**: os gates aprovavam 451 testes (164 unitários/287 IT), runtime e smoke com 37 PASS, mas apenas 7/9 READ tinham evidência integral. M1 percentual, M2 cobertura e M3 ordem foram mortos; M4 transitionId incorreto e M5 nextAttemptAt incorreto sobreviveram com 63 testes aprovados cada. READ-08 tinha assertions apenas de presença; completedAt não nulo também não era exercitado. READ-09 aceitava mensagens não vazias sem comparar o catálogo esperado.

O relatório original permanece em `.validation/mon09-initial-validation.md` e no histórico Git de T7. Evidências originais preservadas: `.validation/mon09-verifier-tests.log`, `.validation/mon09-verifier-runtime-build.log`, `.validation/mon09-verifier-smoke.log`, `.validation/mon02-smoke-287c2c96581b.log`, `.validation/M1.diff` a `.validation/M5.diff`, logs correspondentes e diretórios `M1-reports` a `M5-reports`. Referências do relatório original apontam às linhas de `db60528`, anteriores à inserção dos testes T7/T8. O gate de fechamento original corretamente retornou 1 (`.validation/mon09-verifier-state-gate.log`).

T7 acrescenta quatro casos PENDING/SENDING/ACCEPTED/EXHAUSTED com valores exatos de IDs, agenda, conclusão, destinatário congelado após edição e tentativa final. T8 publica o catálogo literal e a precedência de erros na spec/docs, compara 22 entradas HTTP com seus valores esperados, cobre limites do calendário e preserva mensagens estruturais com putIfAbsent. T9 resolve a ambiguidade detectada na revalidação: o calendário limita timestamps explícitos, enquanto from omitido conserva to-24h. O cenário to=0001-01-02 produz from=0001-01-01, com valores exatos em `src/test/java/br/com/monitor/QueryParametersTest.java:21` e `:22`. Nenhum teste anterior foi removido, desativado ou enfraquecido. Lessons L-002 e L-003 permanecem candidates registradas na primeira execução.

## Re-derivação independente dos requisitos

A tabela usa a spec como fonte da verdade. Toda evidência abaixo é `file:line` de teste em `32ae68d` e descreve a expressão assertada e o valor esperado. Fixture/Clock fixo: `src/test/java/br/com/monitor/QueryFixture.java:30` e `src/test/java/br/com/monitor/QueryFixture.java:66`.

| Requisito / origem | Resultado definido pela spec e assertion correspondente | Status |
| --- | --- | --- |
| READ-01 / MON-09-AC01 | Janela semiaberta por finishedAt, filtro e isolamento: `src/test/java/br/com/monitor/HistoryQueriesIT.java:73` compara totalElements=5; `:74` totalPages=3; `:75`/`:76` page/size; `:79` exige UUIDs 5,4,3,2,1; `:81` exige evento mais recente UUID 6 sem filtro. `:83` a `:91` comparam valores do payload do check, versão, geração, instantes, duração, outcome, status e erro. Página enorme vazia preserva totais em `:99` a `:101`. Defaults e offset 214748364700: `src/test/java/br/com/monitor/QueryParametersTest.java:66` a `:74`. HTTP real: `src/test/java/br/com/monitor/QueryApiIT.java:135` a `:138`, `:162` a `:164`. | PASS |
| READ-02 / MON-09-AC02 | Incidentes usam confirmação de queda: `src/test/java/br/com/monitor/HistoryQueriesIT.java:133` exige ordem UUID 3,2,1; OPEN total=1, duração null, idade 599.9 (`:135` a `:138`). CLOSED total=2, quatro instantes exatos, monitorId, versão, status e duração 180.9, idade null (`:140` a `:150`). Limite superior e excedente são vazios (`:151`, `:152`). HTTP OPEN idade=100/duração null: `src/test/java/br/com/monitor/QueryApiIT.java:142` a `:144`. | PASS |
| READ-03 / MON-09-AC03 | `src/test/java/br/com/monitor/SummaryQueriesIT.java:165` exige SEM_DADOS; `:169` a `:174` FRESH em L=125s, CONFIRMANDO_QUEDA e valores exatos de contadores/versões; `:175` nextCheckAt; `:178` stale em L+1µs; `:180` futuro STALE; `:184` UP com recuperação saturada; `:188`/`:189` PAUSED preserva UP; `:191` retomada SEM_DADOS. `:200` DOWN, `:208` confirmação de recuperação, `:201` a `:215` incidente/última observação preservados stale/paused. `:41` a `:46` janela UTC, generatedAt, monitorId e coverageKnownFrom exatos. HTTP liga IDs às respostas de histórico: `src/test/java/br/com/monitor/QueryApiIT.java:154` a `:157`. | PASS |
| READ-04 / MON-09-AC04 | `src/test/java/br/com/monitor/SummaryQueriesIT.java:35` a `:40` exige total=13, valid=10, success=8, latency=8, taxa=80.00 e média=100.00; fixture inclui oito sucessos, duas falhas externas, três excluídos, eventos fora da janela e outro monitor. Cinco tipos externos de falha geram denominador=2/taxa=50.00 e não entram na latência (`:50` a `:59`). | PASS |
| READ-05 / MON-09-AC05 | `src/test/java/br/com/monitor/SummaryQueriesIT.java:68` a `:76` exige NO_DATA, valid/success/latency=0, total=3, taxa/média null e lacuna integral. `:101` a `:109` exige C-16 [125,200), cobertura de amostra anterior com valid=0 e taxa null, união sem lacuna. `:125` a `:141` exige C-20 [30,60), corte por política e preservação em edição de nome/e-mail. `:148` a `:154` exclui HISTORY_ONLY/unapplied e não inventa dados. `src/test/java/br/com/monitor/ObservationEpochIT.java:39` a `:45` compara checksum preservado, início desde criação versus migração; `:53` a `:71` exige ACTIVE/PAUSED/ACTIVE, fronteiras 30/40, rollback, L=21 e migração idempotente. coverageKnownFrom exato: `src/test/java/br/com/monitor/SummaryQueriesIT.java:46`. | PASS |
| READ-06 / MON-09-AC06 | `src/test/java/br/com/monitor/SummaryQueriesIT.java:40` compara oito sucessos com 100.00ms; `:74`/`:80` média null sem sucessos; `:87` a `:93` inclui zero válido e duração de 1.010001ms para média HALF_UP=0.51 e latencySamples=2; `:56` a `:59` exclui falhas. `:41` a `:43` comparam limites UTC. docs/queries.md documenta fórmulas e precisão. | PASS |
| READ-07 / MON-09-AC07 | `src/test/java/br/com/monitor/SummaryQueriesIT.java:33` exige metricType=CHECK_SUCCESS_SAMPLING; `src/test/java/br/com/monitor/HistoryQueriesIT.java:137` a `:150` mantém duração/idade em campos de incidente separados dos percentuais. docs/queries.md explicita amostragem e não alega uptime temporal. | PASS |
| READ-08 / MON-09-AC01/02 | T7: `src/test/java/br/com/monitor/HistoryQueriesIT.java:44` total=1 com filtro status/type; `:46` compara ID armazenado; `:47` compara transitionId armazenado; `:48` recipient=ops@example.org após edição; `:49` status exato; `:50` nextAttemptAt=base+50s apenas PENDING, null nos demais; `:51` completedAt=base+11s ACCEPTED/base+51s EXHAUSTED, null nos não terminais; `:52` count=1 ou 5; `:53` quantidade de tentativas; `:55` a `:61` número, início, término, outcome e erro final exatos nos quatro estados. Contrato histórico: `:173` a `:176` ordem e totais; `:178` a `:184` monitorId, incidentId, confirmação e contagem; `:187` a `:192` tentativas 1,2 e horário/resultado/erro sanitizado; `:193` a `:198` filtros, fronteiras e excedente. HTTP liga incidentId e type DOWN (`src/test/java/br/com/monitor/QueryApiIT.java:148` a `:150`). | PASS |
| READ-09 / MON-09-AC01/03 | Catálogo literal/precedência definidos na spec. `src/test/java/br/com/monitor/QueryApiIT.java:99` a `:116` declara 22 entradas com mensagens esperadas; `:120` exige 400, `:122` VALIDATION_ERROR, `:123` comparação exata com expected, `:124` não ecoa input/credencial. `:85` a `:94` exige nas quatro rotas 404 MONITOR_NOT_FOUND/errors vazio, UUID malformado 400/mensagem must be a UUID e sem Basic 401/corpo vazio/desafio Basic. `src/test/java/br/com/monitor/QueryParametersTest.java:28`/`:33` inclui limites 0001-01-02/9999-12-30; `:34` a `:40` exige mensagem exata fora do intervalo; `:44` a `:60` exige prioridade de desconhecido/repetido sobre valores inválidos. | PASS |

**Cobertura por origem:** MON-09-AC01 → READ-01/08/09; AC02 → READ-02/08; AC03 → READ-03/09; AC04 → READ-04; AC05 → READ-05; AC06 → READ-06; AC07 → READ-07. **9/9 READ e 7/7 ACs com outcomes definidos e valores assertados**, incluindo conjunções de payload. As antigas assertions isNotNull permanecem como testes de cenário, complementadas pelas novas comparações exatas; não são a evidência exclusiva de nenhum campo.

## Gates independentes da revalidação

| Gate | Comando | Evidência e resultado |
| --- | --- | --- |
| Build integral | `docker compose -p mon09-sensor run --build --rm tests` na scratch final | Exit 0; 459 testes: 167 unitários + 292 IT, zero falhas/erros/skips/Checkstyle. `.validation/mon09-reverify-final-tests.log:88`, `:144`, `:5670`; XML em `.validation/mon09-reverify-final-reports`. |
| Runtime | `docker compose -p mon09-reverify build app` | `.validation/mon09-reverify-runtime-build.log`; exit 0 em 32f4eab, mesma produção de 32ae68d. |
| Smoke | `powershell -NoProfile -ExecutionPolicy Bypass -File scripts/validate-compose.ps1 -NoBuild` | `.validation/mon09-reverify-smoke.log:77`; exit 0 em 32f4eab, 37 PASS em `.validation/mon02-smoke-c0675574b73f.log`, mesma produção de 32ae68d. |

Runtime/smoke foram executados independentemente em 32f4eab. `git diff --exit-code 32f4eab..32ae68d -- src/main Dockerfile compose.yaml pom.xml config scripts` retornou zero, comprovando a mesma produção; o gate integral foi repetido no HEAD final. Smoke usou projeto próprio `mon02-smoke-c0675574b73f`, removido com seu volume, sem tocar banco do operador. O gate intermediário em 32f4eab também aprovou 458 testes (`.validation/mon09-reverify-tests.log`), mas não substitui o relatório final único de 459.

Baseline pré-feature: 387 testes. Primeira implementação: 451 (+64). Correções T7/T8 acrescentam sete casos (quatro estados de entrega, duas unidades e uma entrada HTTP); T9 acrescenta um caso de default no calendário, total confirmado 459 (+72). Todos os novos testes mapeiam aos READ/bordas/Done when. Testes não foram excluídos ou desativados.

## Sensor da revalidação

Worktree isolada `.validation/mon09-reverify-scratch`, detached em `32ae68d`. Mutações individuais, arquivo restaurado antes da próxima, nenhum stash. Os três gates acima passaram antes da primeira mutação. Suítes selecionadas por execução: QueryParametersTest, HistoryQueriesIT, SummaryQueriesIT e QueryApiIT; Maven verify com Checkstyle (23 unitários e 48 IT selecionados; falha unitária encerra Maven antes dos IT). Diferenças, logs e XML de cada mutação ficam fora da worktree para preservar evidência após limpeza.

| Mutação | Fonte | Falha comportamental | Resultado |
| --- | --- | --- | --- |
| R4 (repetição M4) | `src/main/java/br/com/monitor/query/HistoryQueries.java:142` | transitionId recebe notificationId | **Morta**, exit 1; 4 assertions de IDs errados em HistoryQueriesIT. `.validation/R4.diff`, `.validation/R4.log:415`, `.validation/R4-reports`. |
| R5 (repetição M5) | `src/main/java/br/com/monitor/query/HistoryQueries.java:145` | nextAttemptAt recebe confirmedAt | **Morta**, exit 1; 4 assertions de agenda incorreta em HistoryQueriesIT. `.validation/R5.diff`, `.validation/R5.log:415`, `.validation/R5-reports`. |
| R6 | `src/main/java/br/com/monitor/query/HistoryQueries.java:145` | completedAt recebe confirmedAt | **Morta**, exit 1; 5 assertions de conclusão incorreta em HistoryQueriesIT. `.validation/R6.diff`, `.validation/R6.log:434`, `.validation/R6-reports`. |
| R7 | `src/main/java/br/com/monitor/query/QueryParameters.java:26` | mensagem unknown parameter recebe invalid parameter | **Morta**, exit 1; 1 assertion unitária exige unknown parameter e recebe invalid parameter. `.validation/R7.diff`, `.validation/R7.log:128`, `.validation/R7-reports`. Maven encerrou antes dos IT porque a unidade já detectou a falha; não é skip. |
| R1 | `src/main/java/br/com/monitor/query/SummaryQueries.java:62` | multiplicador do percentual 100→10 | **Morta**, exit 1; 7 assertions de percentual incorreto em SummaryQueriesIT, incluindo esperado 80.00 versus 8.00. `.validation/R1.diff`, `.validation/R1.log:491`, `.validation/R1-reports`. |

**Sensor final: 5 injetados, 5 mortos, 0 sobreviventes.** Todos foram mortos por comparação de resultados, com zero errors/skips, não por compilação, Checkstyle ou infraestrutura. R4/R5 agora discriminam os erros que sobreviveram na primeira verificação. Diffs e XML individuais estão preservados. O processo original continuou durante a interrupção da conversa, foi reconciliado antes de retomar e não foi duplicado.

Isolamento comprovado antes de publicar este relatório ou alterar lessons: porcelain real vazio antes/depois; fontes da scratch restauradas e worktree própria removida por caminho absoluto conferido. Evidências: `.validation/mon09-reverify-porcelain-before.txt`, `.validation/mon09-reverify-porcelain-after.txt` e `.validation/mon09-reverify-isolation.log`. Os arquivos vazios de baseline foram materializados explicitamente antes do gate final e depois da limpeza. Banco do operador não foi usado nem removido.

## Qualidade, limites e fechamento

T1..T9 estão marcadas concluídas em commits atômicos; gate integral final e sensor aprovados. Mudanças correspondem ao escopo da spec e seguem JDBC/Spring, SQL parametrizado e Checkstyle. Leitura usa REPEATABLE_READ/readOnly; não há chamada HTTP/SMTP ou escrita de domínio nas consultas. Trigger transacional preserva fronteiras históricas e migrações V1..V6. Diretrizes: README.md, pom.xml, config/checkstyle.xml e padrões de testes existentes.

HTTP real cobre sucesso, vazio, inválido, 404 e 401 para quatro rotas; UAT interativa não se aplica ao backend. Contagens de checks/incidentes preservadas após GET são assertadas em `src/test/java/br/com/monitor/QueryApiIT.java:166`; a análise readOnly/código complementa a inspeção, sem alegar snapshot integral de todas as tabelas.

**Gaps restantes:** nenhum. As duas lacunas do FAIL original foram corrigidas e os sobreviventes foram mortos. A precisão da composição de defaults/calendário foi explicitada em T9, com valor exato testado; não há outcome indefinido remanescente.

Lessons L-002 e L-003 do FAIL inicial permanecem candidates. A ambiguidade nova, resolvida em T9, fundamentou L-004 sobre distinguir limites de entrada e limites de defaults, registrada pelo script apenas após comprovar isolamento. `python .codex/skills/tlc-spec-driven/scripts/validate_state.py mon-09-consultas` retornou exit 0 e zero erros; evidência `.validation/mon09-reverify-state-gate.log`.

**Conclusão:** 9/9 READ, 7/7 ACs, 459 testes aprovados, runtime/smoke aprovados, 5/5 mutações mortas e gate estrutural aprovado. Pronta para fechamento local.
