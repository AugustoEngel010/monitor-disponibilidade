# MON-09 Tasks

## Execution Protocol

Aplicar tlc-spec-driven; gate antes de cada commit, testes junto do comportamento e Verifier independente autom?tico ap?s T6. Pedido autoriza execu??o; ferramentas selecionadas: Docker/Compose, PowerShell/Python e skill. Seis tasks, execu??o inline. Nenhum AGENTS.md aplic?vel encontrado.

**Status:** In Progress. **Design:** [design.md](design.md).

## Test Coverage Matrix

Guidelines: README.md, pom.xml, config/checkstyle.xml, CI. Amostras: MonitorApiIT, MonitorTest, IncidentProcessorIT, IncidentFixtures, MigrationIT, NotificationStoreIT. Assertions derivadas da spec; todos os casos happy/edge/error das quatro rotas.

| Code Layer | Required Test Type | Coverage Expectation | Location Pattern | Run Command |
| --- | --- | --- | --- | --- |
| Par?metros | unit | Limites, defaults, erros por campo | src/test/java/br/com/monitor/QueryParametersTest.java | Quick |
| ?pocas | integration | Upgrade, transa??o, pausa/pol?tica e preserva??o | src/test/java/br/com/monitor/ObservationEpochIT.java | Full |
| Consultas | integration | Filtros, ordena??o, c?lculos e payloads precisos | src/test/java/br/com/monitor/*QueriesIT.java | Full |
| HTTP | integration | Quatro rotas: sucesso, vazio, inv?lido, 404, 401 | src/test/java/br/com/monitor/QueryApiIT.java | Full |
| Docs/opera??o | integration | Build/runtime/smoke e exemplos | docs/queries.md | Build |

## Gate Check Commands

| Gate Level | When to Use | Command |
| --- | --- | --- |
| Quick | T1 | docker compose run --build --rm tests mvn --batch-mode --no-transfer-progress test |
| Full | T2 e fechamento integrais | docker compose run --build --rm tests |
| Full | T3-T5 consultas | docker compose run --build --rm tests mvn --batch-mode --no-transfer-progress -Dit.test=HistoryQueriesIT,SummaryQueriesIT,QueryApiIT -Dfailsafe.failIfNoSpecifiedTests=false verify |
| Build | T6 | Full; docker compose build app; powershell -NoProfile -ExecutionPolicy Bypass -File scripts/validate-compose.ps1 -NoBuild |

## Execution Plan

### Phase 1: Consultas

```text
T1 -> T2 -> T3 -> T4 -> T5 -> T6
```

## Task Breakdown

### T1: Validar par?metros de consulta
**What**: QueryParametersTest cobre janela, limites, UUID e filtros; incluir testes e evid?ncias na mesma task.
**Where**: `src/main/java/br/com/monitor/query/QueryParameters.java`
**Depends on**: None
**Requirement**: READ-01/09
**Done when**:
- [x] QueryParametersTest cobre janela, limites, UUID e filtros; gate sem falhas/erros/skips/Checkstyle, baseline preservada; T1: 164 unitarios, zero falhas/erros/skips/Checkstyle.
**Tests**: unit
**Gate**: quick

### T2: Persistir ?pocas de observa??o
**What**: ObservationEpochIT cobre pausa, pol?tica, idempot?ncia, rollback e upgrade; incluir testes e evid?ncias na mesma task.
**Where**: `src/main/resources/db/migration/V7__create_observation_epochs.sql`
**Depends on**: T1
**Requirement**: READ-05
**Done when**:
- [x] ObservationEpochIT cobre pausa, pol?tica, idempot?ncia, rollback e upgrade; gate sem falhas/erros/skips/Checkstyle, baseline de 387 testes preservada.
**Tests**: integration
**Gate**: full

### T3: Consultar hist?ricos paginados
**What**: HistoryQueriesIT cobre filtros, empate, p?ginas vazias e payloads; incluir testes e evid?ncias na mesma task.
**Where**: `src/main/java/br/com/monitor/query/HistoryQueries.java`
**Depends on**: T2
**Requirement**: READ-01/02/08
**Done when**:
- [ ] HistoryQueriesIT cobre filtros, empate, p?ginas vazias e payloads; gate sem falhas/erros/skips/Checkstyle, baseline de 387 testes preservada.
**Tests**: integration
**Gate**: full

### T4: Compor resumo e indicadores
**What**: SummaryQueriesIT cobre rel?gio fixo, m?tricas, estados e cobertura; incluir testes e evid?ncias na mesma task.
**Where**: `src/main/java/br/com/monitor/query/SummaryQueries.java`
**Depends on**: T3
**Requirement**: READ-03/04/05/06/07
**Done when**:
- [ ] SummaryQueriesIT cobre rel?gio fixo, m?tricas, estados e cobertura; gate sem falhas/erros/skips/Checkstyle, baseline de 387 testes preservada.
**Tests**: integration
**Gate**: full

### T5: Expor contratos HTTP administrativos
**What**: QueryApiIT cobre quatro rotas, valida??o, 404 e 401; incluir testes e evid?ncias na mesma task.
**Where**: `src/main/java/br/com/monitor/query/QueryController.java`
**Depends on**: T4
**Requirement**: READ-01/02/03/08/09
**Done when**:
- [ ] QueryApiIT cobre quatro rotas, valida??o, 404 e 401; gate sem falhas/erros/skips/Checkstyle, baseline de 387 testes preservada.
**Tests**: integration
**Gate**: full

### T6: Documentar contratos e validar opera??o
**What**: Exemplos reproduz?veis; gate integral, runtime e smoke aprovados; incluir testes e evid?ncias na mesma task.
**Where**: `docs/queries.md`
**Depends on**: T5
**Requirement**: READ-01/02/03/04/05/06/07/08/09
**Done when**:
- [ ] Exemplos reproduz?veis; gate integral, runtime e smoke aprovados; gate sem falhas/erros/skips/Checkstyle, baseline de 387 testes preservada.
**Tests**: integration
**Gate**: build

## Diagram-Definition Cross-Check

| Task | Depends on | Diagram | Result |
| --- | --- | --- | --- |
| T1 | None | None | Match |
| T2 | T1 | T1 ? T2 | Match |
| T3 | T2 | T2 ? T3 | Match |
| T4 | T3 | T3 ? T4 | Match |
| T5 | T4 | T4 ? T5 | Match |
| T6 | T5 | T5 ? T6 | Match |

## Test Co-location Validation

| Task | Layer | Matrix requires | Tests | Result |
| --- | --- | --- | --- | --- |
| T1 | QueryParameters.java | unit | unit | Match |
| T2 | V7__create_observation_epochs.sql | integration | integration | Match |
| T3 | HistoryQueries.java | integration | integration | Match |
| T4 | SummaryQueries.java | integration | integration | Match |
| T5 | QueryController.java | integration | integration | Match |
| T6 | docs/queries.md | integration | integration | Match |
