# MON-09: histórico e resumo do monitor

**Data:** 2026-10-18. **Status:** Implementado; gates locais aprovados, Verifier pendente. Execução local autorizada pelo pedido.
**Origem:** MON-09-AC01 a MON-09-AC07; PROD-34/35/36; AD-001 a AD-010.

## Problem Statement

O painel MON-10 precisa consultar evidências e indicadores sem confundir pausa, confirmação e ausência de dados com disponibilidade. O histórico existente não registra os limites de períodos ativos necessários à cobertura temporal.

## Goals

- Consultar verificações, incidentes e notificações com filtros e páginas consistentes.
- Compor estado atual e métricas reproduzíveis, incluindo lacunas e amostras.
- Preservar segurança, arquitetura JDBC e gates Docker.

## Out of Scope

| Capacidade | Motivo |
| --- | --- |
| Painel e retenção | MON-10/11 |
| Uptime temporal, SLA, alteração de transições/SMTP | Métrica solicitada é por amostragem |
| Push, deploy, banco do operador | Execução local em bancos descartáveis |

## Assumptions & Open Questions

| Assumption / decision | Chosen default | Rationale | Confirmed? |
| --- | --- | --- | --- |
| Janela | from/to opcionais; to=agora, from=to-24h; UTC, [from,to), from<to<=agora, precisão até microssegundos | MON-01 e PostgreSQL; defaults técnicos explícitos | Pedido/default técnico |
| Paginação | page=0, size=20; page 0..2147483647, size 1..100; horário DESC, UUID DESC | Convenção MON-04 com eventos recentes primeiro | Default técnico |
| Filtros | checks: outcome; incidents: status OPEN/CLOSED; notifications: status PENDING/SENDING/ACCEPTED/EXHAUSTED e type DOWN/RECOVERY | Semântica específica de cada recurso | Default técnico |
| Período dos recursos | término de check; confirmação de queda de incidente; confirmação da transição de notificação | Não confundir criação de intenção com tentativas de envio | Default técnico |
| Cobertura histórica | Persistir épocas de geração/política e ciclo via trigger transacional; unir coberturas de resultados externos APPLIED; cortar no fim da época | Necessário a PROD-36, sem acoplar consultas às escritas Java | Default técnico |
| Upgrade | Geração 1 permite época desde createdAt; gerações posteriores somente desde instante de migração; coverageKnownFrom explicita limite | Não é possível reconstruir pausas antigas; não fabricar cobertura | Default técnico |
| Estatísticas | Todos os resultados brutos externos no período, inclusive históricos; cobertura somente APPLIED elegíveis na época | MON-01 separa histórico de evidência aplicada | Contrato existente |
| Latência válida | SUCCESS com durationNanos>=0; zero válido, cancelamento com zero excluído; média em ms e duas casas HALF_UP | Duração persistida monotônica não negativa | Default técnico |
| Consistência | REPEATABLE_READ por resposta; relógio capturado uma vez; páginas independentes não congelam inserções futuras | Mesmo contrato de lista MON-04 | Default técnico |

**Open questions:** none; interpretações explicitadas acima.

## User Stories

### P1: Consultar evidências e estado

**User Story:** Como operador, quero explicar o estado e as observações de um monitor antes de agir.

**Acceptance Criteria**:
1. WHEN uma página de verificações for consultada THEN o sistema SHALL filtrar por término em [from,to) e outcome, ordenar finishedAt/executionId DESC e retornar items/page/size/totalElements/totalPages, com página excedente vazia (READ-01; MON-09-AC01).
2. WHEN incidentes forem consultados THEN o sistema SHALL paginar por downConfirmedAt/id DESC e informar OPEN/CLOSED, id/monitorId/configurationVersion e os quatro instantes, estimatedDurationSeconds=recoveryStartedAt-firstFailureAt somente encerrado e ageSeconds=agora-firstFailureAt somente aberto, preservando frações (READ-02; MON-09-AC02).
3. WHEN o resumo for consultado THEN o sistema SHALL expor lifecycle, confirmedState, evidence, recentObservation, lastObservedAt, lastObservation, contadores, versões, nextCheckAt e currentIncident, com label na ordem PAUSED, SEM_DADOS, SEM_DADOS_RECENTES, CONFIRMANDO_QUEDA, CONFIRMANDO_RECUPERACAO, UP, DOWN (READ-03; MON-09-AC03).
4. WHEN a janela contiver oito SUCCESS e duas falhas externas THEN o sistema SHALL retornar successRatePercent=80.00 e validSamples=10, excluindo INTERNAL_ERROR/DESTINATION_BLOCKED/CANCELLED do denominador (READ-04; MON-09-AC04).
5. IF a janela não contiver verificações externas THEN o sistema SHALL retornar dataStatus=NO_DATA, successRatePercent=null e validSamples=0, expondo também totalChecks, successSamples, latencySamples, coverageKnownFrom e noObservationPeriods como complemento das coberturas [finishedAt,finishedAt+2I+T) cortadas pela época, incluindo observação anterior a from (READ-05; MON-09-AC05).
6. WHEN a latência for calculada THEN o sistema SHALL considerar somente SUCCESS com duração não negativa, retornar averageResponseTimeMs=null sem amostras e 100.00 para oito sucessos de 100ms, documentando UTC e os limites da janela (READ-06; MON-09-AC06).
7. The sistema SHALL identificar metricType=CHECK_SUCCESS_SAMPLING e manter duração estimada de incidente separada dos percentuais, sem alegar uptime temporal (READ-07; MON-09-AC07).
8. WHEN notificações forem consultadas THEN o sistema SHALL paginar por confirmedAt/id DESC com filtros de status/type, IDs de notificação/transição/incidente/monitor, destinatário congelado, contagem/próxima tentativa/conclusão e tentativas ordenadas por número com horários/resultado/erro sanitizado (READ-08; MON-09-AC01/02).
9. IF filtros, UUID ou paginação forem inválidos THEN o sistema SHALL retornar 400 {code:VALIDATION_ERROR,errors:{campo:mensagem fixa}}; UUID válido inexistente SHALL retornar 404 MONITOR_NOT_FOUND; sem Basic SHALL retornar 401 (READ-09; MON-09-AC01/03).

**Independent Test:** JUnit com Clock fixo; HTTP real e PostgreSQL Testcontainers no serviço tests.

## Edge Cases

- Janela vazia/invertida/futura, timestamp sem fuso ou submicrossegundo, campos desconhecidos ou repetidos: 400 por campo; offset explícito normalizado a UTC. Calendário técnico de 0001-01-02 a 9999-12-30, permitindo aritmética/JDBC limitada.
- Empates de horários com UUIDs conhecidos, filtros isolam monitores, offset grande sem overflow, nenhum resultado ou página excedente com totais reais.
- Frescor em L exato é FRESH; L+1µs é STALE; futuro é STALE; contadores antigos não exibem confirmação quando stale; recuperação saturada em UP não significa confirmação.
- Cobertura C-16: t=0 e t=200, I=60/T=5, [0,300): lacuna [125,200). C-20: observação 0, pausa 30, retomada 40, observação 60: lacuna [30,60).
- Mudança de política corta cobertura antiga, nome/e-mail não cortam; resposta tardia/HISTORY_ONLY não cria cobertura; reinício não cria época nem lacuna artificial.
- Janela sem amostras pode ter cobertura por amostra anterior; cobertura não equivale à contagem da janela; período anterior à criação sem observação.
- Incidente fracionário: primeira falha .100 e recuperação +181 gera 180.9s; idade aberta inclui pausas e lacunas, sem duração final.
- Migração V6 preserva checksums/dados, limites não reconstruíveis explícitos; leituras não alteram domínio.

## Dimensões implícitas

Validação: READ-09. Falha parcial: snapshot de leitura e erro de banco propagado. Idempotência: GET sem escrita. Auth: Basic global, CSRF das escritas preservado; throttling N/A ao MVP local. Concorrência: REPEATABLE_READ e desempate UUID. Ciclo de dados: épocas duráveis, limite de upgrade explícito, retenção MON-11. Observabilidade: IDs e mensagens fixas. Dependência externa: PostgreSQL, sem chamadas HTTP/SMTP nas consultas. Integridade: trigger participa da transação administrativa; transições existentes preservadas.

## Requirement Traceability

| Requirement ID | Origem | Tasks | Testes | Status |
| --- | --- | --- | --- | --- |
| READ-01 | MON-09-AC01 | T1,T3,T5 | QueryParametersTest, HistoryQueriesIT, QueryApiIT | Verified |
| READ-02 | MON-09-AC02 | T3,T5 | HistoryQueriesIT, QueryApiIT | Verified |
| READ-03 | MON-09-AC03 | T4,T5 | SummaryQueriesIT, QueryApiIT | Verified |
| READ-04 | MON-09-AC04 | T4 | SummaryQueriesIT | Verified |
| READ-05 | MON-09-AC05 | T2,T4 | ObservationEpochIT, SummaryQueriesIT | Verified |
| READ-06 | MON-09-AC06 | T4,T6 | SummaryQueriesIT, docs/queries.md | Verified |
| READ-07 | MON-09-AC07 | T4,T6 | SummaryQueriesIT, docs/queries.md | Verified |
| READ-08 | MON-09-AC01/02 | T3,T5 | HistoryQueriesIT, QueryApiIT | Verified |
| READ-09 | MON-09-AC01/03 | T1,T5 | QueryParametersTest, QueryApiIT | Verified |

## Success Criteria

Sete ACs de origem com evidência file:line; gates Docker/Checkstyle/runtime/smoke aprovados; commits atômicos e Verifier independente com sensor em scratch.
