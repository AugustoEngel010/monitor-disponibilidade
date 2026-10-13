# MON-02 Tasks

## Execution Protocol

Aplicar `tlc-spec-driven`: testes por spec, gates antes de conclusão, um commit por task, verificador independente e sensor. O pedido autoriza implementação e commits locais. Não executar push ou CI remota.

**Design:** [design.md](design.md). **Status:** Done.

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
- [x] `docker build --target build -t monitor-bootstrap .` passou: um unitário, zero falhas/skips, zero violações Checkstyle. Runtime copia somente JAR/JRE e usa UID 10001; inspeção operacional em T4.
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
- [x] verify passou um unitário e quatro testes de integração, zero falhas/skips; XMLs em target/surefire-reports e target/failsafe-reports, Checkstyle zero violações.
- [x] `docker compose up --build -d` iniciou PostgreSQL saudável e app; HTTP 200/UP; histórico V1 `1|t`. Socket/rede exclusivos do tests no Compose.
**Tests**: integration
**Gate**: Full
**Commit**: `feat(mon-02): wire PostgreSQL Compose and isolated integration tests`

### T4: Validar ciclo operacional e persistência

**What**: Automatizar assertions de runtime, migração e sentinela antes/depois de reinício.
**Where**: `scripts/validate-compose.ps1`
**Depends on**: T3
**Requirement**: BASE-01, BASE-02, BASE-03, BASE-07
**Files**: smoke script, application configuration and ApplicationIT regression for PostgreSQL search_path.
**Done when**:
- [x] Smoke retornou zero com HTTP UP, histórico idêntico, sentinela 4242 após restart/down-up e runtime sem ferramentas/socket. Cinco integrações e um unitário passaram, zero falhas/skips.
- [x] Exportação Git limpa eb6829ab68a80e791be2cfeedd1e3fe6bf82fb77, sem target/env: build e smoke aprovados. Checkout final será repetido pelo verificador independente.
**Tests**: smoke
**Gate**: Build
**Commit**: `test(mon-02): verify Compose lifecycle and persistent data`

### T5: Configurar CI pelo fluxo Docker

**What**: Exigir verify em tests, build runtime e smoke; publicar relatórios mesmo em falhas.
**Where**: `.github/workflows/ci.yml`
**Depends on**: T4
**Requirement**: BASE-05, BASE-08
**Done when**:
- [x] Comandos locais aprovados: verify em T4, build app e smoke -NoBuild (mon02-smoke-83dab4da827e). Job não ignora exit codes; relatórios always. Execução remota não observada.
**Tests**: integration
**Gate**: Build
**Commit**: `ci(mon-02): gate container tests quality and runtime build`

### T6: Documentar operação e fechar evidências

**What**: Documentar comandos observados, ambiente, rede, módulos e política de persistência.
**Where**: `README.md`
**Depends on**: T5
**Requirement**: BASE-04, BASE-06
**Done when**:
- [x] README confrontado com build/up/logs/ps/testes e política de parada já executados; gates estruturais sem erros/avisos, suíte atual 6/6 e smoke aprovados. Verificador independente será despachado imediatamente após este commit.
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

## Post-Gate Evidence

### T2

| Critério | Evidência / assertion | Resultado esperado | Necessidade |
| --- | --- | --- | --- |
| BASE-04, limites de produção | `src/test/java/br/com/monitor/ArchitectureTest.java:17`, `noClasses().should().dependOnClassesThat().resideInAnyPackage(...).check(production)` | Nenhuma dependência de produção em bibliotecas de teste | Manter: AC04 |
| BASE-01, build a partir do código | `Dockerfile:5`, COPY pom/config/src; `Dockerfile:10`, `mvn ... package` | Compilar release 21 e gerar target/monitor.jar | Gate build passou |
| BASE-07, separação runtime | `Dockerfile:12`, JRE; `Dockerfile:14`, único COPY do JAR; `Dockerfile:15`, USER | Sem etapa Maven no runtime | Revisão; confirmação dinâmica T4 |

Adequação: teste ligado a AC04, assertion sobre dependência real, nenhuma suíte anterior removida; Checkstyle aplicado a produção e testes. Gates restantes não são declarados aprovados por esta revisão.

### T3

| Critério | Evidência / assertion | Resultado definido | Necessidade |
| --- | --- | --- | --- |
| BASE-01, pronto | `src/test/java/br/com/monitor/ApplicationIT.java:31`, `healthy.statusCode() == 200`; linha 32, `status == UP` | 200/UP com banco real | AC01 |
| BASE-01, banco indisponível | `src/test/java/br/com/monitor/ApplicationIT.java:35`, `unhealthy.statusCode() == 503`; linha 36, `status == DOWN` | 503/DOWN | Edge case |
| BASE-01/02, configuração inválida | `src/test/java/br/com/monitor/ApplicationIT.java:47`, `hasRootCauseInstanceOf(PSQLException.class)`; linha 48, mensagem de autenticação | Startup falha | Edge case |
| BASE-02, V1 | `src/test/java/br/com/monitor/MigrationIT.java:21`, `migrationsExecuted == 1`; linha 30, schema count == 1 | Uma migração e schema monitor | AC02 |
| BASE-02, idempotência | `src/test/java/br/com/monitor/MigrationIT.java:32`, `migrationsExecuted == 0`; linhas 35/36, checksum e installedOn iguais | Histórico preservado | AC02 |
| BASE-02, checksum | `src/test/java/br/com/monitor/MigrationIT.java:53`, `isInstanceOf(FlywayValidateException.class)` e mensagem checksum mismatch | Alteração rejeitada | Edge case |
| BASE-08, relatórios | `config/run-tests.sh:6`, captura exit code; linha 8, cópia dos relatórios | Verify preservado, XMLs no host | AC08 |

Adequação: cada teste mapeado acima, outcomes exatos, zero skips, nenhuma assertion enfraquecida. Falha inicial de Maven clean sobre bind mount corrigida separando `/reports` de `/workspace/target`.

### T4

| Critério | Evidência / assertion | Resultado definido | Necessidade |
| --- | --- | --- | --- |
| BASE-01 | `scripts/validate-compose.ps1:53`, `Assert-Equal ... 200`; linha 54, `Assert-Equal ... UP` | HTTP 200/UP nas três inicializações | AC01 |
| BASE-02 | `scripts/validate-compose.ps1:79`, comparação integral da linha do histórico; linha 84, após down/up | Histórico exatamente igual | AC02 |
| BASE-03 | `scripts/validate-compose.ps1:78`, `Assert-Equal ... 4242`; linha 83, após down/up | Valor sentinela preservado | AC03 |
| BASE-07 | `scripts/validate-compose.ps1:74`, exit zero de test UID/JAR e ausência de Maven/javac/socket | Runtime restrito | AC07 |
| BASE-02/03, search_path | `src/test/java/br/com/monitor/ApplicationIT.java:69`, `rows.getInt(1) == 4242`; linha 73, count == 1 | Startup repetido preserva linha e histórico em public | Regressão do cenário de reinício |

Adequação: smoke falhou primeiro com busca indevida do histórico em monitor; configuração explicitou public e o teste de regressão foi adicionado no mesmo commit. Nenhuma assertion foi removida/enfraquecida. O export limpo aprovado contém exatamente o código preparado para este commit.

### T5

| Critério | Evidência | Resultado / necessidade |
| --- | --- | --- |
| BASE-05 | `.github/workflows/ci.yml:19`, serviço tests antes de build (linha 21) e smoke (linha 24) | Três gates obrigatórios; sem continue-on-error; AC05 |
| BASE-08 | `.github/workflows/ci.yml:26`, `if: always()`; relatórios Surefire/Failsafe/Checkstyle | Relatórios mesmo em falha; AC08 |
| BASE-05, comandos locais | `.specs/features/mon-02-base-docker/evidence.md:19`; logs locais t4-tests/t5-image-build/t5-workflow-smoke | verify, build e smoke exit 0; não afirma execução remota |

Adequação: workflow usa o fluxo já verificado, sem ferramentas Java no host. Sensor negativo fica com o verificador independente; execução remota pendente não é apresentada como sucesso observado.

### T6

| Critério | Evidência | Resultado / necessidade |
| --- | --- | --- |
| BASE-04 | `README.md:87`, tabela de responsabilidades e dependências; ArchitectureTest | Módulos entregues/futuros distinguidos, sem dependência de produção em tests; AC04 |
| BASE-06 | `README.md:10`, comando principal; tabela de ambiente e comandos abaixo | Build/início/parada/logs/testes reais, relatórios e persistência; AC06 |
| Gate | `evidence.md` e logs locais t6-up/t5-workflow-smoke/t4-tests | Código não mudou após gates; daemon recuperado e up confirmado |

Adequação: revisão documental compara comandos reais, sem teste tautológico de frases. Nenhuma credencial real no README/exemplo. Revisão independente e sensor ainda necessários para conclusão da feature.

## Feature-Level Validation

Verificador independente PASS, 8/8 critérios, no checkout final 6c4eafc. Build runtime sem cache, seis testes, Checkstyle e smoke aprovados. Sensores: schema incorreto fez integração falhar apesar do build passar; prontidão sem banco fez falhar a expectativa HTTP 503. Dois mutantes mortos, nenhum sobrevivente. Scratch removido e runtime restaurado. `validate_state.py` passou sem erros. [Relatório completo](validation.md); CI remota não observada.
