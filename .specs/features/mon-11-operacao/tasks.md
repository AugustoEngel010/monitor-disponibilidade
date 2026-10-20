# MON-11 Tasks

## Execution Protocol

Usar tlc-spec-driven e seu ciclo de Execute. Implementação local autorizada no pedido. Sete tasks iniciais e duas correções de gate executadas pelo autor; Verifier independente obrigatório após a última task.

**Status:** Verified; independent validation PASS. [Relatório](validation.md). **Design:** [design.md](design.md).

## Test Coverage Matrix

Proveniência: pom.xml, config/checkstyle.xml, config/run-tests.sh, CI e fixtures JUnit existentes; sem AGENTS.md. Assertions derivadas de OPS-01..06; nenhuma redução de cobertura anterior.

| Code Layer | Required Test Type | Coverage Expectation | Location Pattern | Run Command |
| --- | --- | --- | --- | --- |
| Domínio/operação | unit/integration | Fronteiras temporais, falhas, payload e correlação por AC | src/test/java/br/com/monitor | docker compose run --build --rm tests |
| SQL/API | integration | Caminhos felizes, limites, rollback e dependência indisponível | src/test/java/br/com/monitor/*IT.java | docker compose run --build --rm tests |
| Painel | e2e | Retenção visível e NO_DATA | config/panel | docker compose --profile panel-tests run --use-aliases --build --rm panel-tests |
| Documentação | none | Comandos confrontados com execução real | docs | build gate |

## Gate Check Commands

| Gate Level | When to Use | Command |
| --- | --- | --- |
| Quick | Unidade | docker compose run --build --rm tests mvn --batch-mode --no-transfer-progress test |
| Full | Integração | docker compose run --build --rm tests |
| Build | Fechamento | docker compose run --build --rm tests; docker compose build app; powershell -NoProfile -ExecutionPolicy Bypass -File scripts/validate-compose.ps1 -NoBuild |

Cada task registra o comando efetivo e testes selecionados durante desenvolvimento em evidence.md; o gate completo roda antes de fechar fases. T5 usa o gate de painel.

## Execution Plan

### Phase 1: Operação

```text
T1 -> T2 -> T3 -> T4 -> T5 -> T8 -> T9 -> T6 -> T7 -> T10
```

## Task Breakdown

### T1: Instrumentar saúde e métricas

**What**: Instrumentar saúde e métricas.
**Where**: `src/main/java/br/com/monitor/monitoring/CheckScheduler.java`
**Depends on**: None
**Requirement**: OPS-01/02
**Reuses**: Clock, JDBC, Actuator, Docker e contratos anteriores.
**Tools**: shell/Docker/Python; skill tlc-spec-driven, sem MCP novo.
**Done when**:
- [x] probes independentes, timers e gauges com tags limitadas; gate sem falhas ou skips.
**Tests**: integration
**Gate**: full

### T2: Emitir eventos estruturados correlacionáveis

**What**: Emitir eventos estruturados correlacionáveis.
**Where**: `src/main/java/br/com/monitor/operation/OperationalEvents.java`
**Depends on**: T1
**Requirement**: OPS-03
**Reuses**: Clock, JDBC, Actuator, Docker e contratos anteriores.
**Tools**: shell/Docker/Python; skill tlc-spec-driven, sem MCP novo.
**Done when**:
- [x] correlação dos quatro IDs, sanitização e silêncio em rollback; gate sem falhas ou skips.
**Tests**: integration
**Gate**: full

### T3: Limpar histórico elegível com idempotência

**What**: Limpar histórico elegível com idempotência.
**Where**: `src/main/java/br/com/monitor/operation/RetentionService.java`
**Depends on**: T2
**Requirement**: OPS-04
**Reuses**: Clock, JDBC, Actuator, Docker e contratos anteriores.
**Tools**: shell/Docker/Python; skill tlc-spec-driven, sem MCP novo.
**Done when**:
- [x] corte exato, pendências, última evidência, replay, rollback e lotes; gate sem falhas ou skips.
**Tests**: integration
**Gate**: full

### T4: Informar o limite histórico nas consultas

**What**: Informar o limite histórico nas consultas.
**Where**: `src/main/java/br/com/monitor/query/SummaryQueries.java`
**Depends on**: T3
**Requirement**: OPS-05
**Reuses**: Clock, JDBC, Actuator, Docker e contratos anteriores.
**Tools**: shell/Docker/Python; skill tlc-spec-driven, sem MCP novo.
**Done when**:
- [x] metadados em quatro recursos, NO_DATA em janela removida e cobertura cortada; gate sem falhas ou skips.
**Tests**: integration
**Gate**: full

### T5: Apresentar retenção no painel

**What**: Apresentar retenção no painel.
**Where**: `src/main/resources/static/panel/details.js`
**Depends on**: T4
**Requirement**: OPS-05
**Reuses**: Clock, JDBC, Actuator, Docker e contratos anteriores.
**Tools**: shell/Docker/Python; skill tlc-spec-driven, sem MCP novo.
**Done when**:
- [x] limite visível e ausência de dados preservada; gate sem falhas ou skips.
**Tests**: e2e
**Gate**: full

### T6: Medir capacidade controlada

**What**: Medir capacidade controlada.
**Where**: `src/test/java/br/com/monitor/CapacityScenario.java`
**Depends on**: T9
**Requirement**: OPS-06
**Reuses**: Clock, JDBC, Actuator, Docker e contratos anteriores.
**Tools**: shell/Docker/Python; skill tlc-spec-driven, sem MCP novo.
**Done when**:
- [x] cenário com 100 monitores, C=4, atrasos e outbox medidos; gate sem falhas ou skips.
**Tests**: integration
**Gate**: build

### T7: Documentar operação e evidências finais

**What**: Documentar operação e evidências finais.
**Where**: `docs/operations.md`
**Depends on**: T6
**Requirement**: OPS-01/02/03/04/05/06
**Reuses**: Clock, JDBC, Actuator, Docker e contratos anteriores.
**Tools**: shell/Docker/Python; skill tlc-spec-driven, sem MCP novo.
**Done when**:
- [x] comandos executados, resultados reais e rastreabilidade dos seis ACs; gate sem falhas ou skips.
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
| T6 | T9 | T9 | Match |
| T7 | T6 | T6 | Match |
| T8 | T5 | T5 | Match |
| T9 | T8 | T8 | Match |
| T10 | T7 | T7 | Match |

## Test Co-location Validation

| Task | Code Layer | Matrix Requires | Task Says | Status |
| --- | --- | --- | --- | --- |
| T1 | Operação/SQL | integration | integration | OK |
| T2 | Operação/SQL | integration | integration | OK |
| T3 | Operação/SQL | integration | integration | OK |
| T4 | Operação/SQL | integration | integration | OK |
| T5 | Painel | e2e | e2e | OK |
| T6 | Operação/SQL | integration | integration | OK |
| T8 | Operação/SQL | integration | integration | OK |
| T9 | Operação/SQL | integration | integration | OK |
| T10 | Operação/SQL | integration | integration | OK |
| T7 | Documentação | none | none | OK |

### T8: Consultar recibo somente para resultado ausente

**What**: Preservar processamento de resultado existente nos testes históricos V5 e rejeitar replay de resultado retirado.
**Where**: `src/main/java/br/com/monitor/incident/IncidentProcessor.java`
**Depends on**: T5
**Requirement**: OPS-04
**Tools**: shell/Docker; skill tlc-spec-driven.
**Done when**:
- [x] NotificationSchemaIT e RetentionIT passam sem alterar suas assertions.
**Tests**: integration
**Gate**: full

T8 surgiu do gate completo de T6: NotificationSchemaIT usa schema V5 sem retired_executions. Nenhum teste ou assertion foi reduzido. Ordem ajustada para T5 → T8 → T6 → T7.

### T9: Drenar backlog de retenção em lotes transacionais

**What**: Continuar a mesma rodada enquanto um lote estiver cheio, sem limitar a remoção a mil registros por hora.
**Where**: `src/main/java/br/com/monitor/operation/RetentionRuntime.java`
**Depends on**: T8
**Requirement**: OPS-04
**Tools**: shell/Docker; skill tlc-spec-driven.
**Done when**:
- [x] Uma rodada com lote2 remove5 registros elegíveis em transações separadas e preserva a última evidência; shutdown interrompe continuação.
**Tests**: integration
**Gate**: full

Correção do ritmo operacional identificada na revisão de capacidade: a taxa de entrada pode superar um lote por hora. Apenas o runtime de retenção muda; ele está desabilitado na medição temporizada de CapacityScenario. A execução inicial coube em um lote do autor; T8/T9 são correções subsequentes dos gates, sem delegação de implementação.

### T10: Discriminar drenagem em uma única rodada

**What**: Exercitar uma rodada diretamente, sem polls posteriores que mascarem a ausência de continuação.
**Where**: `src/main/java/br/com/monitor/operation/RetentionRuntime.java`
**Depends on**: T7
**Requirement**: OPS-04
**Tools**: shell/Docker; skill tlc-spec-driven; Verifier independente após commit.
**Done when**:
- [x] Uma chamada tick sem start remove5 de6 registros e mantém5 recibos; mutante sem continuação deve falhar por estado SQL; shutdown real preservado.
**Tests**: integration
**Gate**: full

F1/M7 do Verifier inicial: o await de10s permitia três polls. Extração de tick segue o padrão NotificationRuntime, sem endpoint administrativo novo nem mudança na elegibilidade SQL. As assertions de estado são mantidas e seu escopo temporal ? fortalecido.
