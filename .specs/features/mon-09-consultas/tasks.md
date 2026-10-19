# MON-09 Tasks

## Execution Protocol

Aplicar tlc-spec-driven; gate antes de cada commit, testes junto do comportamento e Verifier independente automático após T6. Pedido autoriza execução; ferramentas selecionadas: Docker/Compose, PowerShell/Python e skill. Seis tasks, execução inline. Nenhum AGENTS.md aplicável encontrado.

**Status:** Done; T1-T8 completos, gates aprovados; revalidacao independente pendente. **Design:** [design.md](design.md).

## Test Coverage Matrix

Guidelines: README.md, pom.xml, config/checkstyle.xml, CI. Amostras: MonitorApiIT, MonitorTest, IncidentProcessorIT, IncidentFixtures, MigrationIT, NotificationStoreIT. Assertions derivadas da spec; todos os casos happy/edge/error das quatro rotas.

| Code Layer | Required Test Type | Coverage Expectation | Location Pattern | Run Command |
| --- | --- | --- | --- | --- |
| Parâmetros | unit | Limites, defaults, erros por campo | src/test/java/br/com/monitor/QueryParametersTest.java | Quick |
| Épocas | integration | Upgrade, transação, pausa/política e preservação | src/test/java/br/com/monitor/ObservationEpochIT.java | Full |
| Consultas | integration | Filtros, ordenação, cálculos e payloads precisos | src/test/java/br/com/monitor/*QueriesIT.java | Full |
| HTTP | integration | Quatro rotas: sucesso, vazio, inválido, 404, 401 | src/test/java/br/com/monitor/QueryApiIT.java | Full |
| Docs/operação | integration | Build/runtime/smoke e exemplos | docs/queries.md | Build |

## Gate Check Commands

| Gate Level | When to Use | Command |
| --- | --- | --- |
| Quick | T1 | docker compose run --build --rm tests mvn --batch-mode --no-transfer-progress test |
| Full | T2 e fechamento integrais | docker compose run --build --rm tests |
| Full | T3-T5 e T7 consultas | docker compose run --build --rm tests mvn --batch-mode --no-transfer-progress '-Dit.test=HistoryQueriesIT,SummaryQueriesIT,QueryApiIT' '-Dfailsafe.failIfNoSpecifiedTests=false' verify |
| Build | T6 e T8 | Full integral; docker compose build app; powershell -NoProfile -ExecutionPolicy Bypass -File scripts/validate-compose.ps1 -NoBuild |

## Execution Plan

### Phase 1: Consultas

```text
T1 -> T2 -> T3 -> T4 -> T5 -> T6 -> T7 -> T8
```

## Task Breakdown

### T1: Validar parâmetros de consulta
**What**: QueryParametersTest cobre janela, limites, UUID e filtros; incluir testes e evidências na mesma task.
**Where**: `src/main/java/br/com/monitor/query/QueryParameters.java`
**Depends on**: None
**Requirement**: READ-01/09
**Done when**:
- [x] QueryParametersTest cobre janela, limites, UUID e filtros; gate sem falhas/erros/skips/Checkstyle, baseline preservada; T1: 164 unitarios, zero falhas/erros/skips/Checkstyle.
**Tests**: unit
**Gate**: quick

### T2: Persistir Épocas de observação
**What**: ObservationEpochIT cobre pausa, política, idempotência, rollback e upgrade; incluir testes e evidências na mesma task.
**Where**: `src/main/resources/db/migration/V7__create_observation_epochs.sql`
**Depends on**: T1
**Requirement**: READ-05
**Done when**:
- [x] ObservationEpochIT cobre pausa, política, idempotência, rollback e upgrade; gate sem falhas/erros/skips/Checkstyle, baseline de 387 testes preservada.
**Tests**: integration
**Gate**: full

### T3: Consultar históricos paginados
**What**: HistoryQueriesIT cobre filtros, empate, páginas vazias e payloads; incluir testes e evidências na mesma task.
**Where**: `src/main/java/br/com/monitor/query/HistoryQueries.java`
**Depends on**: T2
**Requirement**: READ-01/02/08
**Done when**:
- [x] HistoryQueriesIT cobre filtros, empate, páginas vazias e payloads; gate sem falhas/erros/skips/Checkstyle, baseline de 387 testes preservada.
**Tests**: integration
**Gate**: full

### T4: Compor resumo e indicadores
**What**: SummaryQueriesIT cobre relógio fixo, métricas, estados e cobertura; incluir testes e evidências na mesma task.
**Where**: `src/main/java/br/com/monitor/query/SummaryQueries.java`
**Depends on**: T3
**Requirement**: READ-03/04/05/06/07
**Done when**:
- [x] SummaryQueriesIT cobre relógio fixo, métricas, estados e cobertura; gate sem falhas/erros/skips/Checkstyle, baseline de 387 testes preservada.
**Tests**: integration
**Gate**: full

### T5: Expor contratos HTTP administrativos
**What**: QueryApiIT cobre quatro rotas, validação, 404 e 401; incluir testes e evidências na mesma task.
**Where**: `src/main/java/br/com/monitor/query/QueryController.java`
**Depends on**: T4
**Requirement**: READ-01/02/03/08/09
**Done when**:
- [x] QueryApiIT cobre quatro rotas, validação, 404 e 401; gate sem falhas/erros/skips/Checkstyle, baseline de 387 testes preservada.
**Tests**: integration
**Gate**: full

### T6: Documentar contratos e validar operação
**What**: Exemplos reproduzíveis; gate integral, runtime e smoke aprovados; incluir testes e evidências na mesma task.
**Where**: `docs/queries.md`
**Depends on**: T5
**Requirement**: READ-01/02/03/04/05/06/07/08/09
**Done when**:
- [x] Exemplos reproduzíveis; gate integral, runtime e smoke aprovados; gate sem falhas/erros/skips/Checkstyle, baseline de 387 testes preservada.
**Tests**: integration
**Gate**: build

### T7: Verificar valores persistidos do payload de notificação
**What**: Eliminar os gaps de READ-08 com assertions exatas de identidade, agendamento e conclusão em todos os estados de entrega.
**Where**: `src/test/java/br/com/monitor/HistoryQueriesIT.java`
**Depends on**: T6
**Requirement**: READ-08; MON-09-AC01/02
**Done when**:
- [x] transitionId corresponde à transição persistida; nextAttemptAt/completedAt têm valores exatos; PENDING/SENDING/ACCEPTED/EXHAUSTED e destinatário congelado verificados; gate Full aprovado, sem enfraquecer testes.
**Tests**: integration
**Gate**: full

### T8: Fixar mensagens e precedência dos erros de consulta
**What**: Explicitar catálogo de mensagens e verificar valores exatos por HTTP, incluindo bordas de calendário; parâmetros desconhecidos/repetidos têm prioridade sobre seu conteúdo.
**Where**: `src/main/java/br/com/monitor/query/QueryParameters.java`
**Depends on**: T7
**Requirement**: READ-09; MON-09-AC01/03
**Done when**:
- [x] Mensagens exatas para cada categoria e bordas do calendário são testadas; gate Build integral aprovado; nova verificação independente elimina os mutantes sobreviventes.
**Tests**: integration
**Gate**: build

## Diagram-Definition Cross-Check

| Task | Depends on | Diagram | Result |
| --- | --- | --- | --- |
| T1 | None | None | Match |
| T2 | T1 | T1 -> T2 | Match |
| T3 | T2 | T2 -> T3 | Match |
| T4 | T3 | T3 -> T4 | Match |
| T5 | T4 | T4 -> T5 | Match |
| T6 | T5 | T5 -> T6 | Match |
| T7 | T6 | T6 -> T7 | Match |
| T8 | T7 | T7 -> T8 | Match |

## Test Co-location Validation

| Task | Layer | Matrix requires | Tests | Result |
| --- | --- | --- | --- | --- |
| T1 | QueryParameters.java | unit | unit | Match |
| T2 | V7__create_observation_epochs.sql | integration | integration | Match |
| T3 | HistoryQueries.java | integration | integration | Match |
| T4 | SummaryQueries.java | integration | integration | Match |
| T5 | QueryController.java | integration | integration | Match |
| T6 | docs/queries.md | integration | integration | Match |
| T7 | Históricos | integration | integration | Match |
| T8 | Parâmetros/HTTP | integration | integration | Match |
