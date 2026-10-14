# MON-07 Tasks

## Execution Protocol

Aplicar tlc-spec-driven: testes derivados dos ACs, gates antes de conclusão e um commit atômico por task com status/rastreabilidade. Cinco tasks inline; Verifier independente obrigatório após a última. Pedido autoriza execução local e ferramentas Docker/PowerShell/Python.

**Status:** Done; Verifier independente pendente. **Design:** [design.md](design.md).

## Test Coverage Matrix

Guidelines: README.md, pom.xml, config/checkstyle.xml e CI. Padrões de MonitorTest, MonitorRepositoryIT, ScheduleStoreIT, CheckRepositoryIT, CheckServiceIT e SchedulerRuntimeIT. Nenhum AGENTS.md encontrado.

| Code Layer | Required Test Type | Coverage Expectation | Location Pattern | Run Command |
| --- | --- | --- | --- | --- |
| Estado puro | unit | Cada sequência, classificação externa/não externa, limites e fronteira temporal | src/test/java/br/com/monitor/*Test.java | Quick |
| Esquema/persistência | integration | Unicidade, FK, migração, concorrência e rollback | src/test/java/br/com/monitor/*IT.java | Full |
| Processador | integration | ACs, replay, falhas em cada escrita, horários e payload durável | src/test/java/br/com/monitor/*IT.java | Full |
| Ciclo/runtime | integration | Pausa/retomada/edição/reinício e reserva recuperável | src/test/java/br/com/monitor/*IT.java | Full |
| Documentação | integration | Gates runtime/smoke, rastreabilidade e comandos reais | docs/incidents.md | Build |

## Gate Check Commands

| Gate Level | When to Use | Command |
| --- | --- | --- |
| Quick | T1 | docker compose run --build --rm tests mvn --batch-mode --no-transfer-progress test |
| Full | T2,T3,T4 | docker compose run --build --rm tests |
| Build | T5 | Full; docker compose build app; powershell -NoProfile -ExecutionPolicy Bypass -File scripts/validate-compose.ps1 -NoBuild |

## Execution Plan

### Phase 1: Incidentes

```text
T1 → T2 → T3 → T4 → T5
```

## Task Breakdown

### T1: Implementar decisão pura de estado
**What**: IncidentStateMachine com testes derivados das sequências MON-01.
**Where**: `src/main/java/br/com/monitor/incident/IncidentStateMachine.java`
**Depends on**: None
**Requirement**: INC-01/02/03/04/06/08
**Done when**:
- [x] IncidentStateMachineTest prova sequências, limites, classes e horários; Quick passou: 138 unitários, 14 novos, zero falhas/erros/skips e zero Checkstyle. Evidências em evidence.md.
**Tests**: unit
**Gate**: quick
**Commit**: feat(mon-07): implement incident state decisions

### T2: Criar persistência de incidentes e transições
**What**: Migração V5 e IncidentRepository com integridade SQL e fixtures compatíveis.
**Where**: `src/main/java/br/com/monitor/incident/IncidentRepository.java`
**Depends on**: T1
**Requirement**: INC-03/05/08
**Done when**:
- [x] IncidentSchemaIT prova unicidade concorrente, referências, upgrade V4 e imutabilidade do histórico; Full passou: 321 testes (138 unitários/183 IT), zero falhas/erros/skips/Checkstyle. Seis novos casos de esquema.
**Tests**: integration
**Gate**: full
**Commit**: feat(mon-07): persist incident and transition history

### T3: Aplicar resultados atomicamente
**What**: IncidentProcessor transacional e testes PostgreSQL de idempotência/rollback.
**Where**: `src/main/java/br/com/monitor/incident/IncidentProcessor.java`
**Depends on**: T2
**Requirement**: INC-01 a INC-08
**Done when**:
- [x] IncidentProcessorIT prova sequências, replay concorrente, payload vencedor, timestamps e rollback em incidente/transição/intenção/marcador; Full passou: 341 testes (138 unitários/203 IT), 20 novos, zero falhas/erros/skips/Checkstyle.
**Tests**: integration
**Gate**: full
**Commit**: feat(mon-07): apply check results atomically

### T4: Integrar aplicação e ciclo de vida
**What**: ScheduleStore/MonitorService aplicam resultados e sincronizam incidente no fluxo real.
**Where**: `src/main/java/br/com/monitor/monitoring/ScheduleStore.java`
**Depends on**: T3
**Requirement**: INC-05/06/07/08
**Done when**:
- [x] IncidentLifecycleIT prova reinício de contexto, pausa concorrente, edição, geração antiga e reserva recuperável; Full passou: 356 testes (138 unitários/218 IT), quinze novos, zero falhas/erros/skips/Checkstyle. Regressão de scheduler atualizada para MON-07 e coberta.
**Tests**: integration
**Gate**: full
**Commit**: feat(mon-07): integrate recoverable incident processing

### T5: Documentar e validar operação
**What**: Contrato operacional, evidências e rastreabilidade completas.
**Where**: `docs/incidents.md`
**Depends on**: T4
**Requirement**: INC-01 a INC-08
**Done when**:
- [x] Gate Full final T4: 356 testes, zero falhas/erros/skips/Checkstyle. Build runtime e smoke (23 PASSs, V1–V5, restart/down-up) passaram; docs/README/STATE/evidence atualizados. Verifier independente executará após este commit.
**Tests**: integration
**Gate**: build
**Commit**: docs(mon-07): document incident processing and evidence

## Diagram-Definition Cross-Check

| Task | Depends on | Diagram | Result |
| --- | --- | --- | --- |
| T1 | None | None | Match |
| T2 | T1 | T1 → T2 | Match |
| T3 | T2 | T2 → T3 | Match |
| T4 | T3 | T3 → T4 | Match |
| T5 | T4 | T4 → T5 | Match |

## Test Co-location Validation

| Task | Layer | Matrix requires | Tests | Result |
| --- | --- | --- | --- | --- |
| T1 | State | unit | unit | Match |
| T2 | JDBC/schema | integration | integration | Match |
| T3 | Processor | integration | integration | Match |
| T4 | Lifecycle/runtime | integration | integration | Match |
| T5 | Docs/operation | integration/build/smoke | integration | Match |
