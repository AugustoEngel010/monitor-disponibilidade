# MON-03: tasks

## Execution Protocol

Usar tlc-spec-driven, gates por task, commit atômico com status e verificador independente após a última task. Pedido atual autoriza execução local das fases.

**Design:** [design.md](design.md). **Status:** In Progress.

## Test Coverage Matrix

Guidelines: README.md, pom.xml, config/checkstyle.xml, CI e testes existentes. JUnit/AssertJ, HTTP real/Testcontainers, *IT via Failsafe; nenhum teste removido ou enfraquecido.

| Code Layer | Required Test Type | Coverage Expectation | Location Pattern | Run Command |
| --- | --- | --- | --- | --- |
| Segurança e bootstrap | integration | AC01/02/03/04/05, ausente/inválido/válido e logs | src/test/java/br/com/monitor/*IT.java | docker compose run --build --rm tests |
| Endpoint/CSRF | integration | AC06, todas as escritas, token/sessão inválidos e efeitos | src/test/java/br/com/monitor/*IT.java | docker compose run --build --rm tests |
| Compose | smoke | Startup fechado, prontidão pública, rotação, persistência existente | scripts/validate-compose.ps1 | powershell -NoProfile -ExecutionPolicy Bypass -File scripts/validate-compose.ps1 |
| SDD/docs | review | Comandos, placeholders, rotas, rastreabilidade | .specs/ e README.md | validadores e revisão |

## Gate Check Commands

| Gate Level | When to Use | Command |
| --- | --- | --- |
| Structural | SDD | python .codex/skills/tlc-spec-driven/scripts/validate_spec.py mon-03-acesso-administrativo --strict; python .codex/skills/tlc-spec-driven/scripts/validate_tasks.py mon-03-acesso-administrativo |
| Full | Integração | docker compose run --build --rm tests |
| Build | Operação | Full + docker compose build app + powershell -NoProfile -ExecutionPolicy Bypass -File scripts/validate-compose.ps1 -NoBuild |

## Execution Plan

### Phase 1: Entrega

```text
T1 -> T2 -> T3 -> T4 -> T5
```

## Task Breakdown

### T1: Registrar contrato administrativo

**What**: Fixar critérios e decisões rastreáveis para implementação autorizada.
**Where**: artefatos MON-03
**Depends on**: None
**Requirement**: AUTH-01 a AUTH-06
**Files**: spec.md, context.md, design.md, tasks.md, STATE.md.
**Done when**:
- [x] Spec/tasks Structural: zero erros/avisos; AD-005 acrescentada preservando AD-001 a AD-004.
**Tests**: review
**Gate**: Structural
**Commit**: `docs(mon-03): specify administrative access contract`

### T2: Configurar autenticação obrigatória

**What**: Entregar chain global e operador externo com startup fechado.
**Where**: componente security
**Depends on**: T1
**Requirement**: AUTH-01, AUTH-02, AUTH-03, AUTH-04, AUTH-05
**Files**: pom.xml, AdministrativeSecurityConfiguration.java, application.yml, AdministrativeConfigurationIT.java, AdministrativeSecurityIT.java, ApplicationIT.java.
**Done when**:
- [ ] Full passa incluindo 6 testes anteriores e novos cenários de acesso/configuração; 401 inclusive escritas sem token; health sem detalhes e sem sessão.
**Tests**: integration
**Gate**: Full
**Commit**: `feat(mon-03): require configured operator for administrative access`

### T3: Expor token CSRF administrativo

**What**: Entregar endpoint autenticado e provar bloqueio de ações forjadas sem efeito.
**Where**: componente CsrfController
**Depends on**: T2
**Requirement**: AUTH-01, AUTH-05, AUTH-06
**Files**: CsrfController.java, AdministrativeSecurityIT.java.
**Done when**:
- [ ] Full passa com POST/PUT/PATCH/DELETE, header/parâmetro válidos, ausente/inválido/outra sessão, formulário cross-origin e cookie sem Basic; contadores comprovam ausência de efeito.
**Tests**: integration
**Gate**: Full
**Commit**: `feat(mon-03): expose session csrf token and verify protected actions`

### T4: Integrar credenciais no fluxo Compose

**What**: Passar configuração administrativa e validar lifecycle seguro pelo smoke existente.
**Where**: infraestrutura Compose
**Depends on**: T3
**Requirement**: AUTH-02, AUTH-03, AUTH-04, AUTH-05
**Files**: compose.yaml, .env.example, scripts/validate-compose.ps1.
**Done when**:
- [ ] Build passa; smoke prova configuração ausente, rotação, prontidão pública, persistência e ausência de segredos nos logs; tests sem credenciais externas permanece operacional.
**Tests**: smoke
**Gate**: Build
**Commit**: `feat(mon-03): integrate operator configuration into Docker lifecycle`

### T5: Documentar operação autenticada

**What**: Documentar variáveis, rotação, rotas e fluxo CSRF com placeholders.
**Where**: README.md
**Depends on**: T4
**Requirement**: AUTH-01 a AUTH-06
**Files**: README.md; evidências de gates nos artefatos MON-03.
**Done when**:
- [ ] Documentação confrontada com gates aprovados, exemplos sem valores reais, rastreabilidade atualizada; despachar Verifier após commit.
**Tests**: review
**Gate**: Build
**Commit**: `docs(mon-03): document credentials routes and csrf operation`

## Diagram-Definition Cross-Check

| Task | Depends on | Diagram | Status |
| --- | --- | --- | --- |
| T1 | None | raiz | Match |
| T2 | T1 | T1 -> T2 | Match |
| T3 | T2 | T2 -> T3 | Match |
| T4 | T3 | T3 -> T4 | Match |
| T5 | T4 | T4 -> T5 | Match |

## Test Co-location Validation

| Task | Layer | Matrix / Tests | Status |
| --- | --- | --- | --- |
| T1 | SDD | review | Match |
| T2 | segurança/bootstrap | integration | Match |
| T3 | endpoint/CSRF | integration | Match |
| T4 | Compose | smoke | Match |
| T5 | docs | review | Match |

## Post-Gate Evidence

Preencher com resultados observados em cada task; cobertura por file:line e reverse mapping em evidence.md, depois revisão independente em validation.md.
