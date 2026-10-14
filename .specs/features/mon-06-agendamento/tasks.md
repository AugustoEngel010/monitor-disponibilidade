# MON-06 Tasks

## Execution Protocol

Aplicar tlc-spec-driven, testes por AC antes da implementação, gate antes de marcar completo e commit atômico por task. Pedido já autoriza ferramentas Docker/PowerShell/Python e execução. Quatro tasks inline; Verifier independente obrigatório no fechamento.

**Status:** In Progress. **Design:** [design.md](design.md).

## Test Coverage Matrix

Inferida de pom.xml, config/checkstyle.xml, README, MonitorTest, MonitorRepositoryIT, MonitorApiIT, CheckRepositoryIT e CheckServiceIT. Cobrir todos os ACs e edge cases aplicáveis, sem sleeps para regras de agendamento.

| Code Layer | Required Test Type | Coverage Expectation | Location Pattern | Run Command |
| --- | --- | --- | --- | --- |
| Reserva/JDBC | integration | Seleção, ordem, atomicidade, exclusão mútua, recuperação | src/test/java/br/com/monitor/*IT.java | Full |
| Coordenador | unit | Relógio/latches, limite, sobreposição, cancelamento, falhas, shutdown | src/test/java/br/com/monitor/*Test.java | Quick |
| Runtime/config | integration | Servidores lento/saudável, restart PostgreSQL, frescor e regressão | src/test/java/br/com/monitor/*IT.java | Full |
| Documentação | integration | Gates completos/runtime/smoke e evidências | docs/scheduling.md | Build |

## Gate Check Commands

| Gate Level | When to Use | Command |
| --- | --- | --- |
| Quick | T2 | docker compose run --build --rm tests mvn --batch-mode --no-transfer-progress test |
| Full | T1,T3 | docker compose run --build --rm tests |
| Build | T4 | Full; docker compose build app; powershell -NoProfile -ExecutionPolicy Bypass -File scripts/validate-compose.ps1 -NoBuild |

## Execution Plan

### Phase 1: Agendamento

```text
T1 → T2 → T3 → T4
```

## Task Breakdown

### T1: Persistir reservas de execução
**What**: ScheduleStore transacional com seleção, avanço e recuperação, incluindo V4 e fixtures de migração.
**Where**: `src/main/java/br/com/monitor/monitoring/ScheduleStore.java`
**Depends on**: None
**Requirement**: SCHED-01/02/05/06
**Done when**:
- [x] ScheduleStoreIT prova seleção/ordem/pausa, claim concorrente, rollback, restart idempotente e preservação de estado; Full aprovado: 271 testes (110 unitários/161 IT), zero falhas/erros/skips.
**Tests**: integration
**Gate**: full
**Commit**: feat(mon-06): persist recoverable check reservations

### T2: Coordenar workers limitados
**What**: CheckScheduler sem fila, com interrupção e liberação real de capacidade.
**Where**: `src/main/java/br/com/monitor/monitoring/CheckScheduler.java`
**Depends on**: T1
**Requirement**: SCHED-02/03/04/08
**Done when**:
- [x] CheckSchedulerTest prova limite, rodadas concorrentes, invalidação de geração, falhas e shutdown com latches; Quick aprovado: 124 unitários, zero falhas/erros/skips.
**Tests**: unit
**Gate**: quick
**Commit**: feat(mon-06): coordinate bounded check workers

### T3: Integrar runtime periódico
**What**: Polling Spring/configuração/Compose e integração com HTTP e PostgreSQL.
**Where**: `src/main/java/br/com/monitor/monitoring/SchedulerRuntime.java`
**Depends on**: T2
**Requirement**: SCHED-01/03/04/05/06/07/08
**Done when**:
- [x] SchedulerRuntimeIT prova runtime, servidor lento/saudável, timeout/cancelamento, restart, janela de cleanup e consulta de frescor; Full aprovado: 301 testes (124 unitários/177 IT), zero falhas/erros/skips/Checkstyle.
**Tests**: integration
**Gate**: full
**Commit**: feat(mon-06): enable periodic scheduling lifecycle

### T4: Documentar e validar operação
**What**: Contrato operacional, rastreabilidade e gates runtime/smoke.
**Where**: `docs/scheduling.md`
**Depends on**: T3
**Requirement**: SCHED-01 a SCHED-08
**Done when**:
- [ ] README/env/decisões/evidence atualizados; Build aprovado com contagem real; Verifier independente após commit.
**Tests**: integration
**Gate**: build
**Commit**: docs(mon-06): document scheduling and acceptance evidence

## Diagram-Definition Cross-Check

| Task | Depends on | Diagram | Result |
| --- | --- | --- | --- |
| T1 | None | None | Match |
| T2 | T1 | T1 → T2 | Match |
| T3 | T2 | T2 → T3 | Match |
| T4 | T3 | T3 → T4 | Match |

## Test Co-location Validation

| Task | Layer | Tests | Result |
| --- | --- | --- | --- |
| T1 | JDBC/migration | integration | Match |
| T2 | Coordinator | unit | Match |
| T3 | Runtime | integration | Match |
| T4 | Docs | integration/build/smoke | Match |
