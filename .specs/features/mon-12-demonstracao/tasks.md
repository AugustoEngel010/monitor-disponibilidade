# MON-12 Tasks

## Execution Protocol

Usar tlc-spec-driven. O pedido inicial autorizou execução local sem commits; após a validação, o usuário autorizou o commit de fechamento da MON-12. Sete tasks executadas inline, com revisão final por Verifier independente.

**Status:** Verified; parecer independente PASS 8/8. **Design:** [design.md](design.md). **Validação:** [validation.md](validation.md). Commit de fechamento autorizado posteriormente pelo usuário.

## Test Coverage Matrix

| Code Layer | Required Test Type | Coverage Expectation | Location Pattern | Run Command |
| --- | --- | --- | --- | --- |
| API demo | integration | 200/503/atraso, independência, 400/404 | config/demo/server.test.mjs | docker compose run --build --rm --no-deps demo-seed node --test server.test.mjs |
| Seed | integration | Dois IDs preservados, divergência rejeitada | config/demo/seed.test.mjs | docker compose run --build --rm --no-deps demo-seed node --test seed.test.mjs |
| Compose e roteiro | e2e | Transições, SMTP real, restart, concorrência, pausa/lacuna | config/demo/scenario.mjs | powershell -NoProfile -ExecutionPolicy Bypass -File scripts/demo.ps1 |
| Domínio existente | unit/integration | Preservar 482 Java e Checkstyle | src/test/java | docker compose run --build --rm tests |
| Painel existente | e2e | Preservar 50 casos | config/panel | docker compose -p monitor-panel-tests run --use-aliases --build --rm panel-tests |
| Documentação | review | Comandos executados e links válidos | README e docs | Revisão e execução limpa |

## Gate Check Commands

| Gate Level | When to Use | Command |
| --- | --- | --- |
| Quick | Demo/seed | docker compose run --build --rm --no-deps demo-seed node --test server.test.mjs seed.test.mjs |
| Full | Roteiro | powershell -NoProfile -ExecutionPolicy Bypass -File scripts/demo.ps1 |
| Build | Fechamento | Quick + Full + docker compose run --build --rm tests + docker compose build app + smoke + 50 Playwright |

## Execution Plan

### Phase 1: Demonstração local

```text
T1 → T2 → T3 → T4 → T5 → T6 → T7
```

## Task Breakdown

### T1: API demo controlável
**What**: Entregar servidor HTTP local com modos independentes e testes dos contratos.
**Where**: `config/demo/server.mjs`
**Depends on**: None
**Requirement**: DEMO-02
**Tests**: integration; server.test.mjs, Node em Docker.
**Gate**: Quick; servidor 200/503/slow, independência, 400/404.
**Done when**:
- [x] Testes do servidor passam sem alterar o monitor; dois casos Node em Docker, exit 0.

### T2: Seed idempotente
**What**: Criar dois monitores pela API autenticada e testar segunda execução e divergência.
**Where**: `config/demo/seed.mjs`
**Depends on**: T1
**Requirement**: DEMO-01
**Tests**: integration; seed.test.mjs e repetição no ambiente real.
**Gate**: Quick.
**Done when**:
- [x] Segunda execução preserva IDs e configuração incompatível falha; teste e cópia limpa PASS.

### T3: Integrar Compose
**What**: Construir demo e executar seed com configuração explícita.
**Where**: `compose.yaml`
**Depends on**: T2
**Requirement**: DEMO-01
**Tests**: e2e; build e startup com configuração limpa.
**Gate**: Full; docker compose config --quiet e up --build.
**Done when**:
- [x] Quatro serviços disponíveis e seed concluído num volume novo; validate-clean PASS, inclusive down/up.

### T4: Roteiro automatizado
**What**: Provar transições, restart pendente, lentidão e pausa/lacuna com snapshots reais.
**Where**: `config/demo/scenario.mjs`
**Depends on**: T3
**Requirement**: DEMO-03,DEMO-04,DEMO-05
**Tests**: e2e; HTTP, SMTP, PostgreSQL e painel reais, sem interceptações.
**Gate**: Full.
**Done when**:
- [x] Assertions do roteiro completo aprovadas, evidência JSON disponível em demo-evidence.json; exit 0.

### T5: Capturar imagens reais
**What**: Capturar o painel durante queda, pendência, pausa/lacuna e recuperação.
**Where**: `docs/images/mon12/`
**Depends on**: T4
**Requirement**: DEMO-06
**Tests**: e2e; screenshots do runner e inspeção visual.
**Gate**: Full, mesma execução do roteiro.
**Done when**:
- [x] Oito imagens reais em docs/images/mon12; IDs conferidos no JSON e inspeção visual.

### T6: Guia de portfólio
**What**: Documentar operação, roteiro, rastreabilidade e capacidade medida.
**Where**: `README.md`
**Depends on**: T5
**Requirement**: DEMO-06,DEMO-07
**Tests**: review; comandos executados no checkout limpo e links conferidos.
**Gate**: Build.
**Done when**:
- [x] README e guia permitem repetir os cenários; clean checkout, 482 Java, 50 UI, 37 smoke e roteiro passaram.

### T7: Fechamento verificável
**What**: Registrar gates, parecer independente, sensor e estado do MVP.
**Where**: `.specs/features/mon-12-demonstracao/validation.md`
**Depends on**: T6
**Requirement**: DEMO-08
**Tests**: review e sensor em scratch isolado.
**Gate**: Build e validate_state.py.
**Done when**:
- [x] Parecer independente PASS 8/8; sensor 3/3 detectados, zero sobreviventes; validate_state aprovado.

## Post-Gate Evidence

As matrizes de suficiência/necessidade, assertions exatas por AC e evidências file:line estão em [validation.md](validation.md). Dois testes Node cobrem DEMO-01/02 e suas bordas; Full cobre DEMO-03/04/05 e capturas DEMO-06; revisão documental cobre DEMO-06/07; gates e sensor cobrem DEMO-08. Nenhum teste novo sem requisito. Gate Build: 482 Java, 50 UI, 37 smoke, Quick 2/2, Full e checkout limpo PASS. Verifier adicionou probe independente do atraso padrão, 1/1 PASS. Adequação aprovada sem enfraquecer testes ou alterar Java/migrações.

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

| Task | Layer | Matrix Requires | Task Says | Status |
| --- | --- | --- | --- | --- |
| T1 | API | integration | integration | OK |
| T2 | Seed | integration | integration | OK |
| T3 | Compose | e2e | e2e | OK |
| T4 | Roteiro | e2e | e2e | OK |
| T5 | Imagens | e2e | e2e | OK |
| T6 | Documentação | review | review | OK |
| T7 | Verificação | review | review e sensor | OK |

Ferramentas de todas as tasks: exec/apply_patch, Docker, PowerShell, validadores Python da skill. Java e Node executados exclusivamente em Docker. Escopo autorizado pelo pedido atual.
