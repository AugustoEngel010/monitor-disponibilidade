# MON-08 Tasks

## Execution Protocol

Aplicar tlc-spec-driven, testes derivados da spec, gate antes de cada commit e status no mesmo commit. Cinco tasks inline e Verifier independente automático ao terminar. Ferramentas já escolhidas pelo pedido: Docker, PowerShell, Python e skill. Nenhum AGENTS.md encontrado.

**Status:** Done; aguardando Verifier independente. **Design:** [design.md](design.md).

## Test Coverage Matrix

Guidelines: README.md, pom.xml, config/checkstyle.xml e CI. Amostras: IncidentProcessorIT, IncidentSchemaIT, IncidentLifecycleIT, SchedulerRuntimeIT, CheckServiceIT e MonitorTest. All ACs e edge cases exigem assertions de estado/payload, sem mocks como única evidência.

| Code Layer | Required Test Type | Coverage Expectation | Location Pattern | Run Command |
| --- | --- | --- | --- | --- |
| Schema | integration | Upgrade, UNIQUE, FKs, estados e tentativas | src/test/java/br/com/monitor/NotificationSchemaIT.java | Full |
| Store | integration | ACs, rollback, concorrência, ordem e fronteiras temporais | src/test/java/br/com/monitor/NotificationStoreIT.java | Full |
| SMTP | integration | Payload capturado, indisponibilidade, rejeição e timeout | src/test/java/br/com/monitor/SmtpDeliveryIT.java | Full |
| Runtime | integration | Contexto real, restart, isolamento e conclusão recuperável | src/test/java/br/com/monitor/NotificationRuntimeIT.java | Full |
| Operação/docs | integration | Runtime build e smoke Compose | docs/notifications.md | Build |

## Gate Check Commands

| Gate Level | When to Use | Command |
| --- | --- | --- |
| Quick | Unit | docker compose run --build --rm tests mvn --batch-mode --no-transfer-progress test |
| Full | T1 a T4 | docker compose run --build --rm tests |
| Build | T5 | Full; docker compose build app; powershell -NoProfile -ExecutionPolicy Bypass -File scripts/validate-compose.ps1 -NoBuild |

## Execution Plan

### Phase 1: Entrega

```text
T1 → T2 → T3 → T4 → T5
```

## Task Breakdown

### T1: Persistir estados e histórico de entrega
**What**: V6 e testes PostgreSQL de integridade/upgrade; compatibilidade dos gates atuais.
**Where**: `src/main/resources/db/migration/V6__create_notification_delivery.sql`
**Depends on**: None
**Requirement**: MAIL-01/02/03/05
**Done when**:
- [x] NotificationSchemaIT prova upgrade V5, limites, unicidade e rollback; Full: BUILD SUCCESS, 357 testes (138 unitários/219 IT), zero falhas/erros/skips/Checkstyle.
**Tests**: integration
**Gate**: full
**Commit**: feat(mon-08): persist notification delivery history

### T2: Implementar reserva e conclusão recuperáveis
**What**: NotificationStore com testes de retries, recuperação de órfãs, rollback e ordem.
**Where**: `src/main/java/br/com/monitor/notification/NotificationStore.java`
**Depends on**: T1
**Requirement**: MAIL-01/02/03/04/05/07
**Done when**:
- [x] NotificationStoreIT: 12 cenários novos; Full: 369 testes (138 unitários/231 IT), zero falhas/erros/skips/Checkstyle, BUILD SUCCESS.
**Tests**: integration
**Gate**: full
**Commit**: feat(mon-08): implement recoverable delivery reservations

### T3: Enviar mensagens SMTP com timeout
**What**: SmtpDelivery e configuração por ambiente com testes Mailpit e SMTP controlado.
**Where**: `src/main/java/br/com/monitor/notification/SmtpDelivery.java`
**Depends on**: T2
**Requirement**: MAIL-02/04/05/06
**Done when**:
- [x] SmtpDeliveryIT: cinco casos reais SMTP; NotificationConfigurationTest: seis casos. Full: 380 testes (144 unitários/236 IT), zero falhas/erros/skips/Checkstyle, BUILD SUCCESS.
**Tests**: integration
**Gate**: full
**Commit**: feat(mon-08): send notification emails with bounded smtp

### T4: Executar worker assíncrono e retomada
**What**: NotificationRuntime com testes de ciclo de contexto e persistência após envio.
**Where**: `src/main/java/br/com/monitor/notification/NotificationRuntime.java`
**Depends on**: T3
**Requirement**: MAIL-02/05/07
**Done when**:
- [x] NotificationRuntimeIT: seis cenários; Full: 386 testes (144 unitários/242 IT), zero falhas/erros/skips/Checkstyle. Runtime Docker construído com sucesso.
**Tests**: integration
**Gate**: full
**Commit**: feat(mon-08): run asynchronous notification delivery

### T5: Documentar operação e validar Compose
**What**: Documentação de variáveis/estados/SQL, SMTP Compose e evidências dos sete ACs.
**Where**: `docs/notifications.md`
**Depends on**: T4
**Requirement**: MAIL-01/02/03/04/05/06/07
**Done when**:
- [x] Full final: 386 testes (144 unitários/242 IT), zero falhas/erros/skips/Checkstyle. Runtime build e smoke Compose passaram; Mailpit capturou queda/recuperação fictícias após restart com pending. Docs/README/STATE/backlog/evidence atualizados.
**Tests**: integration
**Gate**: build
**Commit**: docs(mon-08): document notification delivery and evidence

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
| T1 | Schema | integration | integration | Match |
| T2 | Store | integration | integration | Match |
| T3 | SMTP | integration | integration | Match |
| T4 | Runtime | integration | integration | Match |
| T5 | Docs/operation | integration | integration | Match |
