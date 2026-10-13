# MON-05 Tasks

## Execution Protocol

Aplicar tlc-spec-driven: testes derivados da spec, gate antes do commit, commit atômico com status e Verifier independente após T5. Pedido autoriza execução local; ferramentas Docker/PowerShell/Python e skill já escolhidas pelo usuário/contexto.

**Status:** Done; [Verifier](validation.md) PASS, seis mutações mortas. **Design:** [design.md](design.md).

## Test Coverage Matrix

Inferida de pom.xml, Checkstyle, README, MonitorTest/MonitorRepositoryIT/MonitorApiIT. Todos os ACs e edge cases da spec exigem assertions de resultado.

| Code Layer | Required Test Type | Coverage Expectation | Location Pattern | Run Command |
| --- | --- | --- | --- | --- |
| Política | unit | URI, faixas IP, DNS misto, demo exata | src/test/java/br/com/monitor/*Test.java | Quick |
| Executor | integration | HTTP/TLS controlados, classificações, timeout, cleanup | src/test/java/br/com/monitor/*IT.java | Full |
| Repositório/serviço | integration | PostgreSQL real, dedup concorrente, restart, metadados, falhas | src/test/java/br/com/monitor/*IT.java | Full |
| Docs/config | integration | Suite regressão, runtime e smoke | scripts/validate-compose.ps1 | Build |

## Gate Check Commands

| Gate Level | When to Use | Command |
| --- | --- | --- |
| Quick | Política | docker compose run --build --rm tests mvn --batch-mode --no-transfer-progress test |
| Full | Executor/persistência/serviço | docker compose run --build --rm tests |
| Build | Fechamento | Full; docker compose build app; powershell -NoProfile -ExecutionPolicy Bypass -File scripts/validate-compose.ps1 -NoBuild |

## Execution Plan

### Phase 1: HTTP e registro

```text
T1 → T2 → T3 → T4 → T5
```

## Task Breakdown

### T1: Implementar política de destinos
**What**: Validação de destino com exceção por origem.
**Where**: `src/main/java/br/com/monitor/monitoring/DestinationPolicy.java`
**Depends on**: None
**Requirement**: HTTP-05/06
**Done when**:
- [x] URI/IP/DNS misto e demo cobertos por DestinationPolicyTest; quick gate passa: 64 novos casos, 110 unitários, zero falhas/erros/skips.
**Tests**: unit
**Gate**: quick
**Commit**: feat(mon-05): enforce destination policy

### T2: Implementar executor de uma tentativa
**What**: GET com timeout total, classificação e cleanup.
**Where**: `src/main/java/br/com/monitor/monitoring/HttpCheckExecutor.java`
**Depends on**: T1
**Requirement**: HTTP-01/02/03/04/05
**Done when**:
- [x] HttpCheckExecutorIT cobre servidor controlado, TLS/DNS, cancelamento e cleanup; full passa: 243 testes (110 unitários/133 IT), 19 casos novos, zero falhas/erros/skips.
**Tests**: integration
**Gate**: full
**Commit**: feat(mon-05): execute bounded HTTP checks

### T3: Persistir resultado idempotente
**What**: Repositório JDBC e migração V3 com metadados e unicidade.
**Where**: `src/main/java/br/com/monitor/monitoring/CheckRepository.java`
**Depends on**: T2
**Requirement**: HTTP-03/07/08
**Done when**:
- [x] CheckRepositoryIT verifica metadados, dedup sequencial/concorrente, FK, nova conexão e erros; full passa: 258 testes (110 unitários/148 IT), 15 casos novos, zero falhas/erros/skips.
**Tests**: integration
**Gate**: full
**Commit**: feat(mon-05): persist idempotent check results

### T4: Integrar consulta e registro
**What**: Serviço Spring com política configurada e persistência após tentativa.
**Where**: `src/main/java/br/com/monitor/monitoring/CheckService.java`
**Depends on**: T3
**Requirement**: HTTP-01/03/06/07/08
**Done when**:
- [x] CheckServiceIT prova resultado HTTP persistido, demo por configuração, cancelamento e snapshot antigo; full passa: 264 testes (110 unitários/154 IT), 6 casos novos, zero falhas/erros/skips.
**Tests**: integration
**Gate**: full
**Commit**: feat(mon-05): wire HTTP execution and recording

### T5: Documentar operação e validar runtime
**What**: Documentação do contrato e configuração de demonstração.
**Where**: `docs/http-checks.md`
**Depends on**: T4
**Requirement**: HTTP-01 a HTTP-08
**Done when**:
- [x] README/env/Compose documentados; 264 testes completos, runtime e smoke passam; evidence.md com assertions file:line e comandos. Verifier independente PASS 8/8 e seis sensores mortos.
**Tests**: integration
**Gate**: build
**Commit**: docs(mon-05): document HTTP checks and validation

## Diagram-Definition Cross-Check

| Task | Depends on | Diagram | Result |
| --- | --- | --- | --- |
| T1 | None | None | Match |
| T2 | T1 | T1 → T2 | Match |
| T3 | T2 | T2 → T3 | Match |
| T4 | T3 | T3 → T4 | Match |
| T5 | T4 | T4 → T5 | Match |

## Test Co-location Validation

| Task | Layer | Tests | Result |
| --- | --- | --- | --- |
| T1 | Política | unit | Match |
| T2 | Executor | integration | Match |
| T3 | JDBC/migration | integration | Match |
| T4 | Serviço | integration | Match |
| T5 | Docs/config | integration/regressão/smoke | Match |
