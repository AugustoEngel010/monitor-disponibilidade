# MON-04: tasks

## Execution Protocol

Aplicar tlc-spec-driven, testes derivados da spec, gates antes de conclusão, commit por task com status e verificador independente final. Pedido autoriza execução e commits locais. Oito tasks inicialmente, um lote inline; correção adicional de smoke descoberta no gate operacional. Ferramentas já determinadas pelo usuário: skill, shell e Docker.

**Design:** [design.md](design.md). **Status:** In Progress.

## Test Coverage Matrix

Guidelines: README.md, pom.xml, config/checkstyle.xml; exemplos ArchitectureTest, MigrationIT, ApplicationIT, AdministrativeSecurityIT e AdministrativeConfigurationIT. Nenhum AGENTS.md encontrado.

| Code Layer | Required Test Type | Coverage Expectation | Location Pattern | Run Command |
| --- | --- | --- | --- | --- |
| Configuração/domínio | unit | Todos os limites e ramos de transição/frescor da spec | src/test/java/br/com/monitor/*Test.java | Quick |
| Repositório/serviço | integration | PostgreSQL real, rollback, locks, limite, estado e reinício | src/test/java/br/com/monitor/*IT.java | Full |
| HTTP | integration | Rotas válidas, inválidas, inexistentes, Basic/CSRF e payloads completos | src/test/java/br/com/monitor/*IT.java | Full |
| Smoke Docker | smoke | Histórico integral V1/V2, restart/down-up e segurança MON-03 | scripts/validate-compose.ps1 | Build |
| SDD/docs | review | Rastreabilidade, comandos e erros | .specs/ e README.md | Structural |

## Gate Check Commands

| Gate Level | When to Use | Command |
| --- | --- | --- |
| Structural | SDD | python .codex/skills/tlc-spec-driven/scripts/validate_spec.py mon-04-ciclo-monitores --strict e validate_tasks.py mon-04-ciclo-monitores |
| Quick | Unit | docker compose run --build --rm tests mvn --batch-mode --no-transfer-progress test |
| Full | Integração/qualidade | docker compose run --build --rm tests |
| Build | Final | Full + docker compose build app + powershell -NoProfile -ExecutionPolicy Bypass -File scripts/validate-compose.ps1 -NoBuild |

## Execution Plan

### Phase 1: Entrega

```text
T1 -> T2 -> T3 -> T4 -> T5 -> T6 -> T7 -> T8 -> T9
```

## Task Breakdown

### T1: Registrar contratos

**What**: Registrar contratos.
**Where**: artefatos MON-04
**Depends on**: None
**Requirement**: LIFE-01 a LIFE-09
**Tools**: shell, Docker; tlc-spec-driven.
**Done when**:
- [x] Spec/design/tasks com critérios, premissas e gates estruturais sem erros; gate Structural exit 0, sem falhas/skips; baseline 48 testes preservada.
**Tests**: review
**Gate**: Structural
**Commit**: `docs(mon-04): specify monitor lifecycle contracts`

### T2: Validar configuração

**What**: Validar configuração.
**Where**: MonitorConfiguration
**Depends on**: T1
**Requirement**: LIFE-02/03
**Tools**: shell, Docker; tlc-spec-driven.
**Done when**:
- [x] Todos os limites, URL e gramática de email verificados por unitários; gate Quick exit 0, sem falhas/skips; baseline 48 testes preservada.
**Tests**: unit
**Gate**: Quick
**Commit**: `feat(mon-04): validate monitor configuration`

### T3: Modelar transições administrativas

**What**: Modelar transições administrativas.
**Where**: Monitor
**Depends on**: T2
**Requirement**: LIFE-05/06/07
**Tools**: shell, Docker; tlc-spec-driven.
**Done when**:
- [x] Estado inicial, edição, incidente, idempotência e frescor verificados; gate Quick exit 0, sem falhas/skips; baseline 48 testes preservada.
**Tests**: unit
**Gate**: Quick
**Commit**: `feat(mon-04): model monitor lifecycle transitions`

### T4: Persistir monitores transacionalmente

**What**: Persistir monitores transacionalmente.
**Where**: MonitorRepository e infraestrutura PostgreSQL
**Depends on**: T3
**Requirement**: LIFE-01/05/06/08
**Tools**: shell, Docker; tlc-spec-driven.
**Done when**:
- [x] V2, roundtrip, rollback, locks, capacidade e reinício verificados; atualizar assertions de histórico V1/V2; gate Full exit 0, sem falhas/skips; baseline 48 testes preservada.
**Tests**: integration
**Gate**: Full
**Commit**: `feat(mon-04): persist monitor lifecycle in PostgreSQL`

### T5: Expor cadastro e consulta

**What**: Expor cadastro e consulta.
**Where**: MonitorController cadastro e consulta
**Depends on**: T4
**Requirement**: LIFE-01/02/03/04/08/09
**Tools**: shell, Docker; tlc-spec-driven.
**Done when**:
- [x] POST 201, GET 200, payload, erros 400/404/409, Basic/CSRF e persistência verificados; gate Full exit 0, sem falhas/skips; baseline 48 testes preservada.
**Tests**: integration
**Gate**: Full
**Commit**: `feat(mon-04): expose monitor registration and lookup`

### T6: Expor edição pausa e retomada

**What**: Expor edição pausa e retomada.
**Where**: MonitorController mutações
**Depends on**: T5
**Requirement**: LIFE-04/05/06/09
**Tools**: shell, Docker; tlc-spec-driven.
**Done when**:
- [x] PUT/pause/resume, 404, 409, rollback, histórico e repetição integral verificados; gate Full exit 0, sem falhas/skips; baseline 48 testes preservada.
**Tests**: integration
**Gate**: Full
**Commit**: `feat(mon-04): expose monitor editing pause and resume`

### T7: Expor listagem paginada

**What**: Expor listagem paginada.
**Where**: MonitorController listagem
**Depends on**: T6
**Requirement**: LIFE-07/09
**Tools**: shell, Docker; tlc-spec-driven.
**Done when**:
- [x] Ordem estável, totais, páginas vazias, parâmetros inválidos e dimensões verificadas; gate Full exit 0, sem falhas/skips; baseline 48 testes preservada.
**Tests**: integration
**Gate**: Full
**Commit**: `feat(mon-04): list monitors with stable pagination`

### T8: Corrigir composição multilinha do smoke

**What**: Compor resultado SQL em string única antes da comparação integral de histórico.
**Where**: scripts/validate-compose.ps1
**Depends on**: T7
**Requirement**: LIFE-01/05/06 e regressão MON-02-AC02
**Done when**:
- [x] Smoke Docker exit 0, histórico V1/V2 idêntico após restart e down/up, sem enfraquecer assertions.
**Tests**: smoke
**Gate**: Build
**Commit**: `fix(mon-04): compare complete multi-migration history in smoke`

### T9: Documentar contratos e evidências

**What**: Documentar contratos e evidências.
**Where**: README.md
**Depends on**: T8
**Requirement**: LIFE-01 a LIFE-09
**Tools**: shell, Docker; tlc-spec-driven.
**Done when**:
- [x] Contratos documentados; suíte completa, imagem e smoke aprovados; verifier após commit; gate Build exit 0, sem falhas/skips; baseline 48 testes preservada.
**Tests**: review
**Gate**: Build
**Commit**: `docs(mon-04): document monitor API and validation`

## Diagram-Definition Cross-Check

| Task | Depends on | Diagram | Status |
| --- | --- | --- | --- |
| T1 | None | raiz | Match |
| T2 | T1 | T1 -> T2 | Match |
| T3 | T2 | T2 -> T3 | Match |
| T4 | T3 | T3 -> T4 | Match |
| T5 | T4 | T4 -> T5 | Match |
| T6 | T5 | T5 -> T6 | Match |
| T7 | T6 | T6 -> T7 | Match |
| T8 | T7 | T7 -> T8 | Match |
| T9 | T8 | T8 -> T9 | Match |

## Test Co-location Validation

| Task | Layer | Matrix / Tests | Status |
| --- | --- | --- | --- |
| T1 | SDD | review | Match |
| T2 | configuração | unit | Match |
| T3 | domínio | unit | Match |
| T4 | persistência | integration | Match |
| T5 | HTTP | integration | Match |
| T6 | HTTP | integration | Match |
| T7 | HTTP | integration | Match |
| T8 | smoke | smoke | Match |
| T9 | docs | review | Match |

## Post-Gate Evidence

Evidências e mapeamento reverso serão registrados em evidence.md por task.
