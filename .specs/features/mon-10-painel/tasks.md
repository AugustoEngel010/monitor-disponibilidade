# MON-10 Tasks

## Execution Protocol

Executar com a skill tlc-spec-driven, gate por task, revisão de adequação, commit atômico e Verifier independente ao concluir. Execução local autorizada pelo pedido; ferramentas de filesystem, Git e Docker. Não publicar.

**Design:** [design.md](design.md). **Status:** In Progress.

## Test Coverage Matrix

Derivada de pom.xml, config/checkstyle.xml, README e CI; amostras MonitorApiIT, AdministrativeSecurityIT, QueryApiIT, SummaryQueriesIT, HistoryQueriesIT. Sem AGENTS.md no projeto.

| Code Layer | Required Test Type | Coverage Expectation | Location Pattern | Run Command |
| --- | --- | --- | --- | --- |
| Cliente e páginas | e2e | Fluxos de todos os ACs e erros listados, backend real; falhas controladas por interceptação | config/panel/*.spec.js | docker compose --profile panel-tests run --use-aliases --build --rm panel-tests |
| Backend preservado | integration | Manter todos os 459 testes e Checkstyle | src/test/java | docker compose run --build --rm tests |
| CSS / acessibilidade | e2e | Desktop 1440×900, mobile 390×844, teclado e sem overflow da página | config/panel/responsive.spec.js | docker compose --profile panel-tests run --build --rm panel-tests responsive.spec.js |
| Documentação | none | Comandos executáveis e rastreabilidade | README.md | Gates estruturais e build |

## Gate Check Commands

| Gate Level | When to Use | Command |
| --- | --- | --- |
| Quick | Estrutura | python .codex/skills/tlc-spec-driven/scripts/validate_spec.py mon-10-painel |
| Full | Cada componente | docker compose --profile panel-tests up -d --build panel-app; docker compose --profile panel-tests run --use-aliases --build --rm panel-tests [arquivo da task] |
| Build | Fechamento | docker compose run --build --rm tests; docker compose build app; suíte panel-tests completa; scripts/validate-compose.ps1 -NoBuild |

## Execution Plan

### Phase 1: Painel

```text
T1 → T2 → T3 → T4 → T5 → T6 → T7
```

## Task Breakdown

### T1: Cliente autenticado e navegador Docker

**What**: Cliente fetch seguro com infraestrutura mínima para testar no navegador real.
**Where**: `src/main/resources/static/panel/api.js`
**Depends on**: None
**Requirement**: PANEL-05, PANEL-06
**Support files**: shell index.html, config/panel (Dockerfile, package, configuração, security.spec.js), compose.yaml, .dockerignore.
**Assumptions**: Basic nativo; token por escrita; browser/package fixos.
**Done when**:
- [x] 401/403 vazios, CSRF válido e erro 5xx/rede produzem resultados definidos; sem credenciais persistidas; security.spec.js passa (6/6, exit 0).
**Tests**: e2e
**Gate**: Full, security.spec.js

### T2: Lista de monitores e estados oficiais

**What**: Lista paginada com summary real por cartão, mensagens e navegação.
**Where**: `src/main/resources/static/panel/app.js`
**Depends on**: T1
**Requirement**: PANEL-01, PANEL-05
**Support files**: dom.js, list.spec.js, fixtures.js e shell.
**Assumptions**: Até 20 summaries, erro por cartão; nenhum cálculo de estado no browser.
**Done when**:
- [x] Nome, label, estado, última observação, incidente, páginas, vazio, loading, falhas e texto hostil testados por list.spec.js (12/12, exit 0).
**Tests**: e2e
**Gate**: Full, list.spec.js

### T3: Formulário de configuração

**What**: Componente de cadastro/edição dos oito campos MON-04.
**Where**: `src/main/resources/static/panel/form.js`
**Depends on**: T2
**Requirement**: PANEL-02, PANEL-05
**Support files**: app.js, form.spec.js.
**Assumptions**: Backend valida; erros associados por aria-describedby; preservação no próprio DOM.
**Done when**:
- [x] Cadastro, edição, 400 por campo, conflito, 403 e rede preservam valores; form.spec.js passa (8/8, exit 0).
**Tests**: e2e
**Gate**: Full, form.spec.js

### T4: Ação confirmada de pausa e retomada

**What**: Componente de ciclo de vida sem alteração otimista.
**Where**: `src/main/resources/static/panel/lifecycle.js`
**Depends on**: T3
**Requirement**: PANEL-03
**Support files**: app.js, lifecycle.spec.js e servidor HTTP de teste.
**Assumptions**: Pausa não encerra incidente; indisponibilidade de rede exige conferir resultado.
**Done when**:
- [x] Pausa/retomada reais preservam incidente, botão bloqueia duplicação e falha não simula sucesso; lifecycle.spec.js passa (9/9, exit 0).
**Tests**: e2e
**Gate**: Full, lifecycle.spec.js

### T5: Detalhes e históricos filtrados

**What**: Componente de consulta com resumo e três históricos independentes.
**Where**: `src/main/resources/static/panel/details.js`
**Depends on**: T4
**Requirement**: PANEL-04, PANEL-05
**Support files**: app.js, details.spec.js.
**Assumptions**: Período opcional ISO com offset; summary usa somente from/to.
**Done when**:
- [x] Resultados, quatro instantes, duração fracionária, tentativas, indicadores, filtros, páginas, erros e nulos têm assertions em details.spec.js (11/11, exit 0).
**Tests**: e2e
**Gate**: Full, details.spec.js

### T6: Layout responsivo e acesso por teclado

**What**: Estilo final e evidências visuais em desktop e tela pequena.
**Where**: `src/main/resources/static/panel/panel.css`
**Depends on**: T5
**Requirement**: PANEL-06
**Support files**: responsive.spec.js, shell e ajustes de acessibilidade dos componentes.
**Assumptions**: CSS local sem fonte externa, foco visível e texto além de cor.
**Done when**:
- [ ] Fluxos principais em dois viewports sem overflow, screenshots e navegação de teclado; suíte de navegador completa passa.
**Tests**: e2e
**Gate**: Full, suíte completa

### T7: Documentar acesso e executar fechamento Docker

**What**: Documentação de acesso, execução e evidências rastreáveis.
**Where**: `README.md`
**Depends on**: T6
**Requirement**: PANEL-01, PANEL-02, PANEL-03, PANEL-04, PANEL-05, PANEL-06
**Support files**: CI, evidence.md, STATE.md, backlog e spec/tasks.
**Assumptions**: Não declarar CI remota ou UAT humano executado.
**Done when**:
- [ ] Suite Java, build/runtime, smoke e UI passam; README informa Basic/CSRF, acesso, filtros e comandos; relatório de evidências contém assertions file:line.
**Tests**: none, camada documental; executa regressão existente como gate
**Gate**: Build

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
| T1 | Cliente | e2e | e2e | OK |
| T2 | Lista | e2e | e2e | OK |
| T3 | Formulário | e2e | e2e | OK |
| T4 | Ciclo | e2e | e2e | OK |
| T5 | Detalhes | e2e | e2e | OK |
| T6 | Estilos | e2e | e2e | OK |
| T7 | Docs | none | none | OK |

Sete entregáveis coesos, uma fase, execução inline. Verifier independente automático após T7 conforme skill.
