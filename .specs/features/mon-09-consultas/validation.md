# MON-09 Validation

**Result**: FAIL
**Date**: 2026-10-18 (America/Sao_Paulo; logs UTC de 2026-10-19).
**Spec**: `.specs/features/mon-09-consultas/spec.md`.
**Diff range**: `a2a7df2..db60528`.
**Verifier**: subagente independente, autor diferente do verificador.

Os gates independentes passam. A verificação adversarial e as lacunas de payload abaixo impedem declarar todos os requisitos verificados. Nenhum código ou teste real foi alterado pelo Verifier.

## Tasks e integridade

T1 a T6 estão marcadas concluídas, em seis commits atômicos: `4f56518`, `e5be77a`, `2e9bfcf`, `8aa31be`, `fda1da6`, `db60528`. Os três testes anteriores modificados apenas acompanham V7 ou preservam explicitamente o upgrade V5→V6. Não houve exclusão, desativação ou enfraquecimento identificado. Baseline anterior: 387 testes; atual: 451, delta +64 (20 parâmetros, 1 épocas, 4 históricos, 13 resumo, 26 HTTP).

## Evidência por requisito

As referências abaixo são relativas à raiz. Cada célula cita a asserção, seu resultado esperado derivado da spec e limites da evidência. Fixture de dados e Clock fixo: `src/test/java/br/com/monitor/QueryFixture.java:30`, `src/test/java/br/com/monitor/QueryFixture.java:66`.

| Requisito / origem | Resultado exigido e evidência independente | Resultado |
| --- | --- | --- |
| READ-01 / MON-09-AC01 | Janela [10,12), filtro SUCCESS e isolamento produzem cinco itens; páginas 0..2 de size=2 preservam total=5, totalPages=3 e UUIDs 5,4,3,2,1: `src/test/java/br/com/monitor/HistoryQueriesIT.java:24` (`totalElements==5`), `:30` (`containsExactly(uuid(5),...,uuid(1))`). Sem filtro, evento mais recente é UUID 6 (`:32`). Página MAX_VALUE vazia conserva total=1 (`:49` a `:51`). Campos de check têm valores exatos (`:34` a `:42`). Defaults e offset sem overflow: `src/test/java/br/com/monitor/QueryParametersTest.java:21` a `:29`. HTTP real: `src/test/java/br/com/monitor/QueryApiIT.java:123` a `:126`, `:150` a `:152`. | PASS |
| READ-02 / MON-09-AC02 | Empate downConfirmedAt retorna UUIDs 3,2,1 (`src/test/java/br/com/monitor/HistoryQueriesIT.java:83`). OPEN: total=1, duração null, idade 599.9s (`:85` a `:88`). CLOSED: total=2, status, monitor, versão e quatro instantes exatos; duração 180.9s e idade null (`:90` a `:100`). Término superior excluído e página excedente vazia (`:101`, `:102`). HTTP OPEN idade=100 e duração null (`src/test/java/br/com/monitor/QueryApiIT.java:130` a `:132`). | PASS |
| READ-03 / MON-09-AC03 | Label SEM_DADOS, FRESH em L=125s, confirmação de queda, stale em L+1µs, futuro STALE, UP com recuperação saturada, prioridade PAUSED e retomada SEM_DADOS: `src/test/java/br/com/monitor/SummaryQueriesIT.java:165` a `:191`. DOWN e confirmação de recuperação: `:200`, `:208`; incidente/última observação preservados stale/paused: `:201` a `:215`. Contadores, lifecycle, confirmedState, versões e nextCheckAt: `:172` a `:189`; monitor/generatedAt/janela UTC: `:41` a `:46`. HTTP liga IDs do incidente e observação aos históricos (`src/test/java/br/com/monitor/QueryApiIT.java:142` a `:145`). | PASS |
| READ-04 / MON-09-AC04 | Oito SUCCESS de 100ms, duas falhas externas e três excluídos geram total=13, valid=10, success=8, latency=8 e taxa 80.00: `src/test/java/br/com/monitor/SummaryQueriesIT.java:35` a `:40`. Cinco categorias de falha externa são parametrizadas e geram 50.00, valid=2 e uma amostra de latência (`:50` a `:59`). Eventos fora da janela e outro monitor não contaminam resultado. | PASS |
| READ-05 / MON-09-AC05 | Sem externos: NO_DATA, valid/success/latency=0, total=3, taxa/latência null e lacuna integral (`src/test/java/br/com/monitor/SummaryQueriesIT.java:68` a `:76`). C-16 produz [125,200), amostra anterior fornece cobertura sem contagem e união remove lacuna (`:101` a `:109`). C-20 produz [30,60), política corta em 80 e nova cobertura acaba em 111; nome/e-mail não cortam (`:125` a `:141`). HISTORY_ONLY/unapplied não cobrem (`:148` a `:154`). Upgrade conserva checksums V1..V6, geração 1 desde criação e posterior desde migração (`src/test/java/br/com/monitor/ObservationEpochIT.java:39` a `:45`); pausa, retomada, idempotência, rollback e L=21 (`:53` a `:71`). coverageKnownFrom exato (`src/test/java/br/com/monitor/SummaryQueriesIT.java:46`). | PASS |
| READ-06 / MON-09-AC06 | Oito sucessos de 100ms geram média 100.00 (`src/test/java/br/com/monitor/SummaryQueriesIT.java:40`). Sem sucessos, média null (`:74`, `:80`). Duração zero válida e 1.010001ms geram média HALF_UP 0.51 com duas amostras (`:87` a `:93`). Falhas excluídas da latência (`:56` a `:59`). Janela exata e UTC (`:41` a `:43`); contrato documentado em `docs/queries.md`. | PASS |
| READ-07 / MON-09-AC07 | metricType exatamente CHECK_SUCCESS_SAMPLING (`src/test/java/br/com/monitor/SummaryQueriesIT.java:33`); duração/idade ficam no IncidentView e têm valores separados (`src/test/java/br/com/monitor/HistoryQueriesIT.java:87` a `:100`). Documentação explica amostragem e não alega uptime temporal. | PASS |
| READ-08 / MON-09-AC01/02 | Paginação com empate retorna UUID 11 antes de 10; total=2/totalPages=2 (`src/test/java/br/com/monitor/HistoryQueriesIT.java:123` a `:126`). monitor, incidente, recipient congelado, status, confirmedAt e attemptCount possuem valores exatos (`:128` a `:134`). Tentativas são 1,2 e segunda tem horários/resultados/código exatos (`:137` a `:142`). Filtros status/type, janela e excedente: `:143` a `:148`. **Sem evidência de valor correto para transitionId (`:129`, apenas isNotNull), nextAttemptAt (`:135`, apenas isNotNull) e completedAt não nulo (apenas null em :136).** | GAP |
| READ-09 / MON-09-AC01/03 | Quatro rotas: 404 MONITOR_NOT_FOUND com errors vazio, UUID malformado 400 e mensagem exata, sem Basic 401 e corpo vazio (`src/test/java/br/com/monitor/QueryApiIT.java:82` a `:92`). Vinte e um filtros HTTP inválidos retornam 400 e VALIDATION_ERROR (`:97` a `:112`); parâmetros unitários validam status/código/chave (`src/test/java/br/com/monitor/QueryParametersTest.java:39` a `:43`). **Mensagens de filtros não têm valor fixo assertado: HTTP apenas isNotBlank (:111), unitário apenas containsKey (:43).** | GAP |

Cobertura integral comprovada: **7/9 READ**. As sete ACs de origem têm seus comportamentos centrais cobertos, mas apenas **4/7 estão completas por conjunção**, pois AC01/02 incluem READ-08 e AC01/03 incluem READ-09. Não se transforma existência de campo em evidência do valor correto. A spec exige mensagens fixas mas não enumera seu catálogo literal; `docs/queries.md:25` fixa ao menos a mensagem de size. Recomenda-se explicitar o catálogo esperado no contrato e testá-lo.

## Gates independentes

| Gate | Comando | Resultado / evidência |
| --- | --- | --- |
| Build integral | `docker compose -p mon09-verifier run --build --rm tests` | Exit 0; 164 unitários + 287 IT = 451; zero failures/errors/skipped e zero Checkstyle. `.validation/mon09-verifier-tests.log:95`, `:151`, `:5775`. |
| Runtime | `docker compose -p mon09-verifier build app` | Exit 0. `.validation/mon09-verifier-runtime-build.log`. |
| Smoke | `powershell -NoProfile -ExecutionPolicy Bypass -File scripts/validate-compose.ps1 -NoBuild` | Exit 0; 37 PASS; `.validation/mon09-verifier-smoke.log:77`, `.validation/mon02-smoke-287c2c96581b.log`. Projeto exclusivo `mon02-smoke-287c2c96581b`, removido com seu próprio volume; banco do operador preservado. |

Não houve skip a justificar. Gates foram executados antes de qualquer mutação.

## Sensor de discriminação

Profundidade: cinco falhas comportamentais proporcionais à aritmética, cobertura temporal, ordenação e integridade de payloads. Scratch isolado: `.validation/mon09-verifier-scratch`, worktree detached em `db60528`; nunca stash. Cada mutação é aplicada individualmente e o arquivo original é restaurado antes da seguinte. Testes de cada execução: QueryParametersTest, HistoryQueriesIT, SummaryQueriesIT, QueryApiIT, Maven verify com Checkstyle.

| Mutação | Fonte original | Falha injetada | Evidência e resultado |
| --- | --- | --- | --- |
| M1 | `src/main/java/br/com/monitor/query/SummaryQueries.java:62` | Multiplicador do percentual 100→10 | **Morta**, exit 1; 7 falhas de assertions em SummaryQueriesIT; 80.00 esperado versus 8.00, entre outros. `.validation/M1.diff`, `.validation/M1.log:507`, `.validation/M1-reports`. |
| M2 | `src/main/java/br/com/monitor/query/SummaryQueries.java:85` | Cobertura L duplicada, em vez de 2I+T | **Morta**, exit 1; 2 falhas de assertions de lacunas. `.validation/M2.diff`, `.validation/M2.log:283`, `.validation/M2-reports`. |
| M3 | `src/main/java/br/com/monitor/query/HistoryQueries.java:56` | Desempate executionId DESC→ASC | **Morta**, exit 1; 1 falha, sequência 1..5 no lugar de 5..1. `.validation/M3.diff`, `.validation/M3.log:279`, `.validation/M3-reports`. |
| M4 | `src/main/java/br/com/monitor/query/HistoryQueries.java:142` | transitionId recebe ID da própria notificação | **Sobreviveu**, exit 0; 63 testes aprovados. `.validation/M4.diff`, `.validation/M4.log:106`, `.validation/M4.log:235`, `.validation/M4-reports`. |
| M5 | `src/main/java/br/com/monitor/query/HistoryQueries.java:145` | nextAttemptAt recebe confirmedAt da transição | **Sobreviveu**, exit 0; 63 testes aprovados. `.validation/M5.diff`, `.validation/M5.log:108`, `.validation/M5.log:237`, `.validation/M5-reports`. |

Por mutante foram selecionados 20 testes unitários e 43 IT. Erros de compilação/Checkstyle/infraestrutura não contam como morte; M1–M3 têm falhas de assertions de comportamento, zero errors/skips. Diferenças e XML individuais são preservados fora da scratch.

**Sensor: 5 injetados, 3 mortos, 2 sobreviventes.** M4/M5 confirmam que as assertions superficiais aceitam valores errados. A scratch foi removida por caminho absoluto conferido após restauração; todos os diffs, logs e relatórios XML permanecem em `.validation/M1*` a `.validation/M5*`. Árvore real antes/depois: porcelain vazio, igualdade comprovada após limpeza e antes da criação deste relatório/lessons. Registros normalizados: `.validation/mon09-verifier-porcelain-before.txt` e `.validation/mon09-verifier-porcelain-after.txt`; o comando inicial capturou saída vazia, e o pipeline PowerShell não criou arquivo vazio até sua materialização explícita final. O stdout das consultas original/final também é a evidência da árvore limpa.

## Qualidade e limites

Mudanças seguem JDBC/Spring existente e Checkstyle. SQL é parametrizado, snapshot por serviço usa REPEATABLE_READ readOnly; consultas não acionam HTTP ou SMTP. Trigger de épocas é transacional e não altera migrações anteriores. Não foi identificada ampliação funcional fora da spec. Convenções: README.md, pom.xml, config/checkstyle.xml, testes existentes. Todos os 64 novos casos mapeiam aos READ, bordas ou Done when de T1..T6; fixture não é caso de teste.

UAT interativa não se aplica: backend HTTP com testes reais automatizados. HTTP cobre sucesso, vazio, erro de entrada, 404 e 401 nas quatro rotas; o problema é precisão de alguns campos, não ausência dessas rotas. Evidência de GET sem escrita HTTP é limitada às contagens de checks/incidentes (`src/test/java/br/com/monitor/QueryApiIT.java:154` a `:155`); readOnly e inspeção do código complementam a análise, sem alegar snapshot completo de todas as tabelas.

## Gaps ranqueados e tarefas de correção

1. **Major, READ-08:** comparar transitionId e nextAttemptAt com valores persistidos esperados; adicionar notificação concluída para provar completedAt não nulo e status final. Arquivos: HistoryQueriesIT, QueryApiIT. Done when: payload completo correto e mutações de IDs/horários são mortas por assertion específica.
2. **Minor, READ-09:** fixar expectativas exatas para mensagens por campo, incluindo repetido, desconhecido, enum, inteiro e janela; incluir bordas do calendário técnico. Arquivos: QueryParametersTest, QueryApiIT e contrato da spec/docs se necessário. Done when: 400/code/errors têm conteúdo exato e mensagens incorretas são rejeitadas.

O Verifier não executou correções. Status Verified de READ-08/09 na spec deve retornar a Needs Fix até correção e nova verificação independente; a spec não foi alterada por este papel.

## Fechamento

`python .codex/skills/tlc-spec-driven/scripts/validate_state.py mon-09-consultas` retorna exit 1, corretamente impedindo fechamento com verdict FAIL; evidência `.validation/mon09-verifier-state-gate.log`. Lessons L-002 (payloads de notificação) e L-003 (mensagens exatas) foram registradas pelo script da skill como candidates após a igualdade do porcelain, com autorização do orquestrador. Arquivos reais produzidos: este relatório, `.specs/lessons.json`, `.specs/LESSONS.md`. Nenhum commit realizado.

**Conclusão:** gates operacionais aprovados; READ-08/09 precisam de correção e nova verificação independente. Sensor 3/5 mortos; feature não está pronta para declarar validação completa.
