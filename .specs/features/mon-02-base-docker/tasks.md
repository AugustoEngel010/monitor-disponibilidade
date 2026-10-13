# MON-02 Tasks

## Execution Protocol

Aplicar `tlc-spec-driven`: testes por spec, gates antes de conclusão, um commit por task, verificador independente e sensor. O pedido autoriza implementação e commits locais. Não executar push ou CI remota.

**Design:** [design.md](design.md). **Status:** In Progress.

## Test Coverage Matrix

Não há código, testes ou diretrizes anteriores. Defaults fortes delimitados pela spec; comandos são definidos junto da base nova. Ferramentas: shell, Docker e skill solicitada.

| Code Layer | Required Test Type | Coverage Expectation | Location Pattern | Run Command |
| --- | --- | --- | --- | --- |
| SDD / documentação | revisão | Oito IDs de origem, premissas e decisões | `.specs/features/mon-02-base-docker/` | validadores da skill |
| Bootstrap / limites | unit | Produção sem bibliotecas de teste | `src/test/**/*Test.java` | serviço tests: Surefire |
| Banco / health | integration | V1, idempotência, checksum, HTTP 200/503 | `src/test/**/*IT.java` | serviço tests: Failsafe |
| Compose / runtime | smoke | Checkout limpo, health, sentinela e isolamento socket | `scripts/validate-compose.ps1` | PowerShell + Docker |
| CI | integration | Comandos obrigatórios propagam falhas; relatórios always | `.github/workflows/ci.yml` | comandos do workflow localmente |

## Gate Check Commands

| Gate Level | Command |
| --- | --- |
| SDD | `python .codex/skills/tlc-spec-driven/scripts/validate_spec.py mon-02-base-docker --strict` e `validate_tasks.py mon-02-base-docker` |
| Bootstrap | `docker build --target build -t monitor-bootstrap .` (unitários e qualidade) |
| Full | `docker compose run --build --rm tests` |
| Build | Full, `docker compose build app` e `powershell -File scripts/validate-compose.ps1` (Windows) ou `pwsh -File scripts/validate-compose.ps1` (CI Linux) |

## Execution Plan

Uma fase; seis tasks, execução inline. Verificador independente depois de T6.

```text
T1 -> T2 -> T3 -> T4 -> T5 -> T6
```

## Task Breakdown

### T1: Registrar contrato Docker e decisão

**What**: Atualizar SDD com decisão expressa, baseline real e rastreabilidade.
**Where**: `.specs/`
**Depends on**: None
**Requirement**: BASE-01 a BASE-08
**Done when**:
- [x] Spec e tasks passam validadores sem erros/avisos; decisão AD-004 registra abandono do wrapper. Revisão: oito critérios de origem mapeados; nenhuma mudança nos contratos de negócio.
**Tests**: revisão documental
**Gate**: SDD
**Commit**: `docs(mon-02): specify container-only build and validation`

### T2: Criar componente de bootstrap e construção

**What**: Tornar o componente Spring compilável e empacotável em contêiner, com teste de limites.
**Where**: raiz e componente bootstrap
**Depends on**: T1
**Requirement**: BASE-01, BASE-04, BASE-07
**Files**: pom, Dockerfile, ignores, Checkstyle, bootstrap, configuração e ArchitectureTest.
**Done when**:
- [ ] Build passa qualidade e um teste unitário; runtime copia somente JAR/JRE e não usa root.
**Tests**: unit
**Gate**: Bootstrap
**Commit**: `build(mon-02): add containerized Spring Boot bootstrap`

### T3: Integrar Compose, migração e serviço tests

**What**: Conectar bootstrap a PostgreSQL saudável e validar migrações/health em banco real.
**Where**: componente infraestrutura Docker/PostgreSQL
**Depends on**: T2
**Requirement**: BASE-01, BASE-02, BASE-08
**Files**: compose, env example, V1, MigrationIT e ApplicationIT.
**Done when**:
- [ ] verify passa um unitário e quatro testes de integração sem skips; relatórios no host.
- [ ] Runtime inicia sem socket; tests tem socket e rede documentados.
**Tests**: integration
**Gate**: Full
**Commit**: `feat(mon-02): wire PostgreSQL Compose and isolated integration tests`

### T4: Validar ciclo operacional e persistência

**What**: Automatizar assertions de runtime, migração e sentinela antes/depois de reinício.
**Where**: `scripts/validate-compose.ps1`
**Depends on**: T3
**Requirement**: BASE-01, BASE-02, BASE-03, BASE-07
**Done when**:
- [ ] Smoke retorna zero com HTTP UP, histórico idêntico, sentinela após restart/down-up e runtime sem ferramentas/socket.
- [ ] Executar também de checkout limpo sem target nem env local.
**Tests**: smoke
**Gate**: Build
**Commit**: `test(mon-02): verify Compose lifecycle and persistent data`

### T5: Configurar CI pelo fluxo Docker

**What**: Exigir verify em tests, build runtime e smoke; publicar relatórios mesmo em falhas.
**Where**: `.github/workflows/ci.yml`
**Depends on**: T4
**Requirement**: BASE-05, BASE-08
**Done when**:
- [ ] Os comandos do workflow passam localmente; job não ignora códigos de saída; execução remota explicitamente não observada.
**Tests**: integration
**Gate**: Build
**Commit**: `ci(mon-02): gate container tests quality and runtime build`

### T6: Documentar operação e fechar evidências

**What**: Documentar comandos observados, ambiente, rede, módulos e política de persistência.
**Where**: `README.md`
**Depends on**: T5
**Requirement**: BASE-04, BASE-06
**Done when**:
- [ ] README confrontado com execução; gates passam; despachar verificador e sensor ao concluir.
**Tests**: revisão e smoke
**Gate**: Build
**Commit**: `docs(mon-02): document Docker operation and test environment`

## Diagram-Definition Cross-Check

| Task | Depends on | Diagram | Status |
| --- | --- | --- | --- |
| T1 | None | raiz | Match |
| T2 | T1 | T1 -> T2 | Match |
| T3 | T2 | T2 -> T3 | Match |
| T4 | T3 | T3 -> T4 | Match |
| T5 | T4 | T4 -> T5 | Match |
| T6 | T5 | T5 -> T6 | Match |

## Test Co-location Validation

| Task | Layer | Matrix / Tests | Status |
| --- | --- | --- | --- |
| T1 | SDD | revisão | Match |
| T2 | bootstrap | unit | Match |
| T3 | banco / health | integration | Match |
| T4 | Compose | smoke | Match |
| T5 | CI | integration | Match |
| T6 | docs | revisão e smoke | Match |
