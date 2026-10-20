# MON-11 Tasks

## Execution Protocol

Usar tlc-spec-driven e seu ciclo de Execute. Implementa??o local autorizada no pedido. Sete tasks em um lote executado pelo autor; Verifier independente obrigat?rio ap?s a ?ltima task.

**Status:** In Progress. **Design:** [design.md](design.md).

## Test Coverage Matrix

Proveni?ncia: pom.xml, config/checkstyle.xml, config/run-tests.sh, CI e fixtures JUnit existentes; sem AGENTS.md. Assertions derivadas de OPS-01..06; nenhuma redu??o de cobertura anterior.

| Code Layer | Required Test Type | Coverage Expectation | Location Pattern | Run Command |
| --- | --- | --- | --- | --- |
| Dom?nio/opera??o | unit/integration | Fronteiras temporais, falhas, payload e correla??o por AC | src/test/java/br/com/monitor | docker compose run --build --rm tests |
| SQL/API | integration | Caminhos felizes, limites, rollback e depend?ncia indispon?vel | src/test/java/br/com/monitor/*IT.java | docker compose run --build --rm tests |
| Painel | e2e | Reten??o vis?vel e NO_DATA | config/panel | docker compose --profile panel-tests run --use-aliases --build --rm panel-tests |
| Documenta??o | none | Comandos confrontados com execu??o real | docs | build gate |

## Gate Check Commands

| Gate Level | When to Use | Command |
| --- | --- | --- |
| Quick | Unidade | docker compose run --build --rm tests mvn --batch-mode --no-transfer-progress test |
| Full | Integra??o | docker compose run --build --rm tests |
| Build | Fechamento | docker compose run --build --rm tests; docker compose build app; powershell -NoProfile -ExecutionPolicy Bypass -File scripts/validate-compose.ps1 -NoBuild |

Cada task registra o comando efetivo e testes selecionados durante desenvolvimento em evidence.md; o gate completo roda antes de fechar fases. T5 usa o gate de painel.

## Execution Plan

### Phase 1: Opera??o

```text
T1 ? T2 ? T3 ? T4 ? T5 ? T6 ? T7
```

## Task Breakdown

### T1: Instrumentar sa?de e m?tricas

**What**: Instrumentar sa?de e m?tricas.
**Where**: `src/main/java/br/com/monitor/monitoring/CheckScheduler.java`
**Depends on**: None
**Requirement**: OPS-01/02
**Reuses**: Clock, JDBC, Actuator, Docker e contratos anteriores.
**Tools**: shell/Docker/Python; skill tlc-spec-driven, sem MCP novo.
**Done when**:
- [x] probes independentes, timers e gauges com tags limitadas; gate sem falhas ou skips.
**Tests**: integration
**Gate**: full

### T2: Emitir eventos estruturados correlacion?veis

**What**: Emitir eventos estruturados correlacion?veis.
**Where**: `src/main/java/br/com/monitor/operation/OperationalEvents.java`
**Depends on**: T1
**Requirement**: OPS-03
**Reuses**: Clock, JDBC, Actuator, Docker e contratos anteriores.
**Tools**: shell/Docker/Python; skill tlc-spec-driven, sem MCP novo.
**Done when**:
- [x] correla??o dos quatro IDs, sanitiza??o e sil?ncio em rollback; gate sem falhas ou skips.
**Tests**: integration
**Gate**: full

### T3: Limpar hist?rico eleg?vel com idempot?ncia

**What**: Limpar hist?rico eleg?vel com idempot?ncia.
**Where**: `src/main/java/br/com/monitor/operation/RetentionService.java`
**Depends on**: T2
**Requirement**: OPS-04
**Reuses**: Clock, JDBC, Actuator, Docker e contratos anteriores.
**Tools**: shell/Docker/Python; skill tlc-spec-driven, sem MCP novo.
**Done when**:
- [x] corte exato, pend?ncias, ?ltima evid?ncia, replay, rollback e lotes; gate sem falhas ou skips.
**Tests**: integration
**Gate**: full

### T4: Informar o limite hist?rico nas consultas

**What**: Informar o limite hist?rico nas consultas.
**Where**: `src/main/java/br/com/monitor/query/SummaryQueries.java`
**Depends on**: T3
**Requirement**: OPS-05
**Reuses**: Clock, JDBC, Actuator, Docker e contratos anteriores.
**Tools**: shell/Docker/Python; skill tlc-spec-driven, sem MCP novo.
**Done when**:
- [ ] metadados em quatro recursos, NO_DATA em janela removida e cobertura cortada; gate sem falhas ou skips.
**Tests**: integration
**Gate**: full

### T5: Apresentar reten??o no painel

**What**: Apresentar reten??o no painel.
**Where**: `src/main/resources/static/panel/details.js`
**Depends on**: T4
**Requirement**: OPS-05
**Reuses**: Clock, JDBC, Actuator, Docker e contratos anteriores.
**Tools**: shell/Docker/Python; skill tlc-spec-driven, sem MCP novo.
**Done when**:
- [ ] limite vis?vel e aus?ncia de dados preservada; gate sem falhas ou skips.
**Tests**: e2e
**Gate**: full

### T6: Medir capacidade controlada

**What**: Medir capacidade controlada.
**Where**: `src/test/java/br/com/monitor/CapacityIT.java`
**Depends on**: T5
**Requirement**: OPS-06
**Reuses**: Clock, JDBC, Actuator, Docker e contratos anteriores.
**Tools**: shell/Docker/Python; skill tlc-spec-driven, sem MCP novo.
**Done when**:
- [ ] cen?rio com 100 monitores, C=4, atrasos e outbox medidos; gate sem falhas ou skips.
**Tests**: integration
**Gate**: build

### T7: Documentar opera??o e evid?ncias finais

**What**: Documentar opera??o e evid?ncias finais.
**Where**: `docs/operations.md`
**Depends on**: T6
**Requirement**: OPS-01/02/03/04/05/06
**Reuses**: Clock, JDBC, Actuator, Docker e contratos anteriores.
**Tools**: shell/Docker/Python; skill tlc-spec-driven, sem MCP novo.
**Done when**:
- [ ] comandos executados, resultados reais e rastreabilidade dos seis ACs; gate sem falhas ou skips.
**Tests**: none
**Gate**: build

## Diagram-Definition Cross-Check

| Task | Depends On | Diagram Shows | Status |
| --- | --- | --- | --- |
| T1 | None | None | Match |
| T2 | T1 | T1 | Match |
| T3 | T2 | T2 | Match |
| T4 | T3 | T3 | Match |
| T5 | T4 | T4 | Match |
| T6 | T5 | T5 | Match |
| T7 | T6 | T6 | Match |

## Test Co-location Validation

| Task | Code Layer | Matrix Requires | Task Says | Status |
| --- | --- | --- | --- | --- |
| T1 | Opera??o/SQL | integration | integration | OK |
| T2 | Opera??o/SQL | integration | integration | OK |
| T3 | Opera??o/SQL | integration | integration | OK |
| T4 | Opera??o/SQL | integration | integration | OK |
| T5 | Painel | e2e | e2e | OK |
| T6 | Opera??o/SQL | integration | integration | OK |
| T7 | Documenta??o | none | none | OK |
