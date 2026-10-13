# MON-02 Validation

**Verdict: PASS.** Oito critérios correspondem aos resultados definidos na spec. Gates locais aprovados e duas mutações mortas; nenhuma execução remota de CI foi observada.

**Date:** 2026-10-13. **Verifier:** sub-agent independente, autor != verifier.
**Spec:** `.specs/features/mon-02-base-docker/spec.md`.
**Diff range:** `d6a9601..6c4eafc`.
**Checkout validado:** `6c4eafc5ba82569708940ffdcd36f4277cc09828`, worktree descartável `.validation/verifier-final`.

## Task completion

| Task | Commit | Status |
| --- | --- | --- |
| T1 | b050cee | Done; validadores spec/tasks sem erros ou avisos |
| T2 | 25217c5 | Done; bootstrap, arquitetura e runtime |
| T3 | a583a9c | Done; Compose, migração e integração |
| T4 | 4d06ba7 | Done; persistência, histórico e regressão search_path |
| T5 | 20d275a | Done; workflow revisado, comandos executados localmente |
| T6 | 6c4eafc | Done; documentação confrontada com configuração e execução |

## Spec-anchored acceptance criteria

Evidências abaixo foram rederivadas da implementação final. Assertions são sobre os valores definidos na spec. Revisão documental/estática complementa os testes nos critérios de configuração e documentação.

| Criterion | Spec-defined outcome | File:line + assertion / verificação | Result |
| --- | --- | --- | --- |
| MON-02-AC01 / BASE-01 | Checkout sem target compila pelo Compose e inicia app/db; readiness 200/UP | `Dockerfile:4` COPY pom, `Dockerfile:6` COPY src, `Dockerfile:10` package; `compose.yaml:30` service_healthy; `scripts/validate-compose.ps1:53` Assert-Equal StatusCode 200 e `scripts/validate-compose.ps1:54` Assert-Equal status UP; `src/test/java/br/com/monitor/ApplicationIT.java:31` isEqualTo(200), linha 32 isEqualTo("UP") | PASS: build --no-cache exit 0 e smoke 200/UP em três startups |
| MON-02-AC02 / BASE-02 | V1 cria monitor, uma migração bem-sucedida, histórico igual após startup repetido | `src/test/java/br/com/monitor/MigrationIT.java:21` migrationsExecuted isEqualTo(1); linhas 23/24 hasSize(1)/version "1"; linha 30 namespace count isEqualTo(1); linha 32 migrationsExecuted isZero; linhas 35/36 checksum/installedOn iguais; `scripts/validate-compose.ps1:69` count success == '1'; linha 70 version == '1'; linhas 79/84 histórico JSON integral igual | PASS: MigrationIT 2/2; smoke count/version 1 e histórico integral inalterado |
| MON-02-AC03 / BASE-03 | Sentinela persistida após restart e down/up sem apagar volumes | `compose.yaml:9` postgres-data persistente; `scripts/validate-compose.ps1:78` e `scripts/validate-compose.ps1:83` Assert-Equal sentinel '4242'; `src/test/java/br/com/monitor/ApplicationIT.java:69` rows.getInt(1) isEqualTo(4242) | PASS: 4242 após ambos os ciclos |
| MON-02-AC04 / BASE-04 | Responsabilidades e dependências documentadas; produção sem bibliotecas de teste | `README.md:89` tabela, linhas 91–96 limites entregues/futuros; `src/test/java/br/com/monitor/ArchitectureTest.java:16` noClasses().should().dependOnClassesThat(), linhas 17/18 proibem JUnit/Testcontainers/ArchUnit e check(production); `pom.xml:49`, linhas 54/59/65 scope test | PASS: ArchitectureTest 1/1 e revisão documental |
| MON-02-AC05 / BASE-05 | Workflow exige tests, runtime build e smoke, propagando falhas | `.github/workflows/ci.yml:19` compose run tests; linha 21 build app; linha 24 smoke -NoBuild; nenhum continue-on-error; `config/run-tests.sh:6` captura exit e linha 10 exit "$result"; `scripts/validate-compose.ps1:24` throw se Docker exit != 0 | PASS: três gates locais exit 0; sensor M1 runtime build exit 0 e verify exit 1. Workflow revisado; CI remota não executada |
| MON-02-AC06 / BASE-06 | README contém comandos reais, variáveis, relatórios, persistência; sem credenciais reais versionadas | `README.md:10` up --build; linhas 32/35/38/39/42/45 build/up/ps/logs/restart/down; linha 53 tests; linha 17 tabela de ambiente; linha 60 relatórios; linha 48 política de volumes; `.env.example:5` password local-development-only; `.gitignore:2` ignora .env | PASS: comandos/configuração coerentes e execução local; valores versionados explicitamente descartáveis |
| MON-02-AC07 / BASE-07 | Sem wrapper; runtime JRE/JAR, não root, sem Maven/javac/socket | `Dockerfile:12` JRE, linha 14 único COPY JAR e linha 15 USER10001; `scripts/validate-compose.ps1:74` test UID=10001 && ! command -v mvn && ! command -v javac && ! socket && JAR; git ls-tree final sem mvnw/mvnw.cmd/.mvn/wrapper; `compose.yaml:43` socket somente tests | PASS: inspeção runtime exit 0, JAR presente, UID10001 e ferramentas/socket ausentes |
| MON-02-AC08 / BASE-08 | verify executa unitários, integrações PostgreSQL real e qualidade; retorna falha e preserva relatórios | `config/run-tests.sh:6` clean verify e captura exit; linha 8 cp target para /reports; linha 10 propaga resultado; `pom.xml:86` Checkstyle validate, linha 100 integration-test e linha 101 verify; `compose.yaml:37`–45 daemon/rede/relatórios | PASS: 1 unitário + 5 integrações, 0 failures/errors/skips, Checkstyle 0; dois sensores exit 1 com XMLs copiados |

**Spec-anchored check:** 8/8 outcomes cobertos; zero spec-precision gaps.

## Build-level gates and integrity

Antes de qualquer gate, o checkout final tinha `git status --porcelain` vazio, `Test-Path target` False e `Test-Path .env` False. O teste compilou seu próprio source; o runtime também foi construído com --no-cache, usando o allowlist `.dockerignore` que exclui target e env.

| Command | Exact result | Local evidence |
| --- | --- | --- |
| validate_spec.py mon-02-base-docker --strict | exit 0, 0 errors, 0 warnings | execução independente |
| validate_tasks.py mon-02-base-docker | exit 0, 0 errors, 0 warnings | execução independente |
| docker compose -p mon02-verifier run --build --rm tests | exit 0; 6 passed, 0 failures, 0 errors, 0 skipped; Checkstyle 0 violations | `.validation/verifier-tests.log` |
| docker compose build --no-cache app | exit 0; runtime construído do source | `.validation/verifier-build.log` |
| powershell -NoProfile -ExecutionPolicy Bypass -File scripts/validate-compose.ps1 | exit 0; projeto mon02-smoke-05570a3df70f | `.validation/verifier-smoke.log`, `.validation/verifier-smoke-evidence.log` |

Relatórios baseline preservados em `.validation/verifier-baseline-reports`. `surefire-reports/TEST-br.com.monitor.ArchitectureTest.xml:2` tests=1/failures=0/errors=0/skipped=0; `failsafe-reports/TEST-br.com.monitor.MigrationIT.xml:2` tests=2 com os mesmos zeros; `failsafe-reports/TEST-br.com.monitor.ApplicationIT.xml:2` tests=3 com os mesmos zeros. Checkstyle XML sem elementos error.

Baseline d6a9601 não tinha aplicação nem testes: 0 -> 6 (+6), sem testes removidos, enfraquecidos ou ignorados. Cada teste mapeia AC/edge: arquitetura AC04; migração/idempotência/checksum AC02; disponibilidade AC01; credenciais inválidas edge de configuração AC01/02; reinicialização search_path AC02/03. Smoke cobre lifecycle/runtime AC01/02/03/07. Não há lógica de domínio nesta entrega.

## Discrimination sensor

Depth: lightweight, duas mutações independentes de comportamento somente no worktree descartável. Os testes reais ficaram intactos. Nenhum stash foi usado.

| Mutation | File:line | Fault and exact outcome | Result |
| --- | --- | --- | --- |
| M1 | `src/main/resources/db/migration/V1__create_monitor_schema.sql:1` | CREATE SCHEMA monitor -> wrong_schema. Runtime build exit 0, ArchitectureTest 1/1 e qualidade passam; verify exit 1, MigrationIT:30 expected1 got0 e ApplicationIT:61 schema monitor inexistente. Integrações: 1 failure + 1 error, zero skips | Killed |
| M2 | `src/main/resources/application.yml:26` | readiness include readinessState,db -> readinessState. verify exit 1, ApplicationIT:35 expected503 got200. Integrações: 1 failure, 0 errors/skips | Killed |

Logs: `.validation/verifier-mutant1-build.log`, `.validation/verifier-mutant1-tests.log`, `.validation/verifier-mutant2-tests.log`. XMLs dos sensores preservados em `.validation/verifier-mutant1-reports` e `.validation/verifier-mutant2-reports`.

**Sensor: PASS, 2 injected, 2 killed, 0 survived.** Ambos os arquivos foram restaurados; scratch porcelain vazio antes de cleanup. Runtime `monitor-disponibilidade:local` foi reconstruído com código real (exit 0, `.validation/verifier-restore-build.log`). O projeto mon02-verifier foi removido incluindo somente seus volumes; o smoke removeu somente seu próprio projeto. O caminho absoluto do scratch foi confirmado sob o workspace antes de `git worktree remove --force`. Scratch removido, logs preservados. Porcelain da árvore real estava vazio no baseline e continuou vazio após cleanup, antes de escrever este relatório. Nenhum volume de desenvolvimento foi removido.

## Code quality and edge cases

Revisão da superfície dos 27 arquivos do diff: mudança limitada a MON-02 e à decisão autorizada de abandonar wrapper nos contratos existentes. Sem classes de negócio vazias, abstrações desnecessárias ou funções de features futuras. Layout mínimo de bootstrap/resources/tests, dependências de testes com scope test, runtime sem socket. Diretrizes: `.codex/skills/tlc-spec-driven/references/coding-principles.md`; nenhuma diretriz de código anterior à feature. Checkstyle inclui produção e testes. Assertions verificam payload/status/valores persistidos, não somente ocorrência de chamada.

- PostgreSQL indisponível: `ApplicationIT.java:35` 503 e linha 36 DOWN, baseline PASS; M2 comprova discriminação.
- Checksum alterado: `MigrationIT.java:53` FlywayValidateException e linha 54 checksum mismatch, PASS.
- Teste falha apesar de imagem construir: M1 build exit 0/verify exit 1, PASS.
- Restart/down sem volumes: histórico integral preservado e sentinela4242, PASS.
- Credenciais inválidas: `ApplicationIT.java:47` PSQLException e linha48 password authentication failed, PASS.

UAT interativo não se aplica: entrega de backend/infraestrutura sem interface de produto.

## Environment and images

Observado pelo verificador: Docker client/server 29.8.2, server OS linux; Docker Compose v5.5.1. PowerShell do Windows executou o smoke. Nenhum Maven/JDK do host foi executado. Imagens e digests locais:

| Image | Observed digest |
| --- | --- |
| maven:3.9.11-eclipse-temurin-21 | sha256:6fdc855a6ed81d288ca7ca37ac6ff5e9308b612485c0801d70b25a858c83d237 |
| eclipse-temurin:21.0.8_9-jre-jammy | sha256:db1689535962d757a5adabf57387584ed543d38c0b9d1fe870123ea362ad73b0 |
| postgres:17.6-alpine | sha256:ef257d85f76e48da1c64832459b59fcaba1a4dac97bf5d7450c77753542eee94 |

Parent Spring Boot3.5.16 e Java21 definidos em `pom.xml:9` e `pom.xml:17`; log baseline observou Testcontainers1.21.4. Tags são explícitas, sem prometer imutabilidade.

## Closure observations

Sem gaps de implementação ou plano de fix comportamental. Observações editoriais comunicadas ao autor: offsets Post-Gate T3 de ApplicationIT, início da seção README:87 e STATE intro/handoff antigo. Após confirmar a preservação do baseline, o autor informou que corrigiu essas referências, reconciliou STATE, marcou backlog, spec Verified e tasks Done. Esses ajustes de fechamento não alteram código/configuração/CI/testes nem o commit de código validado.

Rastreabilidade: BASE-01 a BASE-08 cobertos e elegíveis para Verified. Nenhum outro arquivo de implementação foi escrito pelo verificador. Clean PASS sem sinais de AC gap, mutant sobrevivente, precisão de spec ou desvio: nenhuma lesson criada.

**Overall: PASS, ready for local closure.** CI remota não executada; seus comandos foram validados localmente e o workflow revisado estático. Gate final `python .codex/skills/tlc-spec-driven/scripts/validate_state.py mon-02-base-docker`: exit 0, 0 errors.
