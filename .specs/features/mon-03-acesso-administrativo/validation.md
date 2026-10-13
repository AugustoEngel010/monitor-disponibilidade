# Validation: MON-03 — PASS

**Result:** PASS. Os seis critérios foram rederivados da spec, o gate Docker completo passou e os oito sensores foram mortos.

**Data:** 2026-10-13. **Diff range:** `54d1f01..46c7832`.
**Verifier:** subagente independente `/root/verify_mon03`, autor diferente do verificador.
**Fonte de verdade:** `.specs/features/mon-03-acesso-administrativo/spec.md`.
**Escopo:** 18 arquivos do diff, testes e integração Compose; T1–T5 completos. Nenhum código ou teste real alterado pelo verificador.

## Critérios e evidência independente

| Critério | Resultado exigido pela spec | Evidência file:line + assertion | Veredito |
| --- | --- | --- | --- |
| AC01 / AUTH-01 | Administrativas sem Basic válido: 401, challenge Basic, corpo vazio, inclusive escritas sem CSRF | `src/test/java/br/com/monitor/AdministrativeSecurityIT.java:210`: `assertThat(response.statusCode()).isEqualTo(401)`; `:211`/`:212`: header `contains("Basic realm=\"monitor-admin\"")`; `:213`: `assertThat(response.body()).isEmpty()`. Chamadas `:73` e `:82`, com rotas `:68`/`:69`, seis métodos `:77` e cinco credenciais `:80`/`:81`. | PASS |
| AC02 / AUTH-02 | Basic válido em /actuator/info: 200; rota inexistente autenticada: 404 | `src/test/java/br/com/monitor/AdministrativeSecurityIT.java:91`: `assertThat(response.statusCode()).isEqualTo(200)`, com /actuator/info na lista `:87`; `:97`: `assertThat(send("/unlisted", "GET", basic(username, password)).statusCode()).isEqualTo(404)`. Rotação em `scripts/validate-compose.ps1:120`/`:121` exige 401 com senha/cookie anteriores e 200 com senha nova, por `:84`: `Assert-Equal $status $ExpectedStatus`. | PASS |
| AC03 / AUTH-03 | Todas as rotas protegidas salvo GET/HEAD dos três health explícitos; painel/API de prova: 401/200 | `src/test/java/br/com/monitor/AdministrativeSecurityIT.java:73` chama o helper 401 para /, /admin/probe, /api/admin/probe, assets, /error, /unlisted e outros actuators; `:91` exige 200 com Basic válido. `:105`: status 200; `:106`: corpo exatamente `{"status":"UP"}`; `:107`: cookies vazios; `:108`: HEAD 200; `:109`: POST 401 em cada health. `src/test/java/br/com/monitor/ApplicationIT.java:36`/`:37`: 503/DOWN sem banco. | PASS |
| AC04 / AUTH-04 | Configuração ausente, vazia, branca, malformada ou placeholder impede startup com erro genérico sem valores | `src/test/java/br/com/monitor/AdministrativeConfigurationIT.java:59`: `hasRootCauseMessage("Administrative credentials are missing or invalid")` sobre startup real. Treze pares `:28`–`:35` cobrem ausência, vazio, branco, placeholders, colon/controle no usuário, usuário 129 caracteres, senha 73 bytes ASCII e 74 bytes UTF-8. `:61`/`:64`: `output.getAll()).doesNotContain(username/password)`. `scripts/validate-compose.ps1:104` exige exit 1 com cada variável ausente; `:105` exige mensagem genérica. | PASS |
| AC05 / AUTH-05 | Sem usuário, senha ou Authorization nas saídas padrão; credenciais não persistidas; .env ignorado; exemplos só placeholders | `src/test/java/br/com/monitor/AdministrativeSecurityIT.java:63`: `doesNotContain(username, password, basic(username, password), "Using generated security password")` nos logs; `:92` e `:130`: corpo sem usuário/senha/Basic. `src/test/java/br/com/monitor/AdministrativeConfigurationIT.java:61`/`:64`/`:66`: logs inválidos sem valores nem senha gerada. `scripts/validate-compose.ps1:90`/`:94` rejeitam credenciais textuais e Basic codificado nos logs das duas versões do app. Revisão: `AdministrativeSecurityConfiguration.java:37`/`:38` usa UserDetails em memória com BCrypt; nenhuma persistência ou migration adicionada. `.gitignore:2`/`:3` ignora env; `.env.example:3`/`:4` contém somente placeholders administrativos; `.dockerignore:1` parte de allowlist; `git ls-files '*env*'` retorna somente .env.example. | PASS |
| AC06 / AUTH-06 | POST/PUT/PATCH/DELETE autenticados sem token válido da mesma sessão: 403 vazio e nenhuma ação; header/form válidos: 200 e ação realizada | `src/test/java/br/com/monitor/AdministrativeSecurityIT.java:169`/`:170`: status 403 e corpo vazio, chamados `:145`–`:150` para token ausente, malformado, sessão diferente, cookie ausente e formulário forjado. `:154`: `assertThat(probe.mutations.get()).isEqualTo(before)`. `:157`–`:159`: status 200, corpo `before+1`, estado `before+1`; `:162`–`:164`: status 200, corpo `before+2`, estado `before+2`, mesma sessão/token. Quatro métodos `:136` × duas rotas `:143`. | PASS |

Os campos do endpoint CSRF têm assertions individuais em `src/test/java/br/com/monitor/AdministrativeSecurityIT.java:121`–`:125`: 200, token não branco, headerName exatamente X-CSRF-TOKEN e parameterName exatamente _csrf. Cookie HttpOnly/SameSite=Lax em `:126`/`:127`, ausência de Access-Control-Allow-Origin em `:128` e no-store em `:129`. Cookie sem Basic retorna 401 em `:131`/`:132`; escritas com cookie/token válidos sem Basic ou com senha errada retornam 401 em `:151`–`:153`.

Payload/conjunção: valores dos campos e estado real são verificados. O teste observa o contador da ação antes e depois das negativas e das duas ações permitidas; não se limita à ocorrência de uma chamada. Nenhum gap de precisão da spec foi encontrado.

## Gate executado

Checkout detached de `46c7832` criado em `.validation/mon03-verifier`, inicialmente sem `.env` e sem `target`. Todos os testes executaram em contêiner; Maven/JDK não foram usados no host.

| Comando no scratch | Resultado observado |
| --- | --- |
| `docker compose -p mon03-verifier run --build --rm tests` | Exit 0. 48 testes: 1 unitário + 47 integrações; 0 falhas, 0 erros, 0 skips; Checkstyle aprovado. |
| `docker compose -p mon03-verifier build app` | Exit 0; imagem runtime construída. |
| `powershell -NoProfile -ExecutionPolicy Bypass -File scripts/validate-compose.ps1 -NoBuild` | Exit 0; projeto exclusivo `mon02-smoke-67ad40aabd2b`; startup fechado, acesso 401/200, rotação, prontidão, persistência/history, runtime UID 10001 sem Maven/javac/socket e ausência de segredos aprovados. |
| `python .codex/skills/tlc-spec-driven/scripts/validate_spec.py mon-03-acesso-administrativo --strict` | Exit 0; 0 erros/avisos. |
| `python .codex/skills/tlc-spec-driven/scripts/validate_tasks.py mon-03-acesso-administrativo` | Exit 0; 0 erros/avisos. |
| `git diff --check 54d1f01..46c7832` | Exit 0. |

Baseline real preservada **antes dos sensores** em `.validation/mon03-verifier-evidence/baseline-target/`, `baseline-tests.log`, `baseline-build.log`, `baseline-smoke.log` e `mon02-smoke-67ad40aabd2b.log`. XMLs baseline: ArchitectureTest 1, MigrationIT 2, ApplicationIT 3, AdministrativeSecurityIT 29, AdministrativeConfigurationIT 13.

## Integridade e qualidade dos testes

Antes: 6 testes. Depois: 48. Delta: +42. Os seis anteriores permanecem com assertions originais; diff de MigrationIT e ArchitectureTest vazio. ApplicationIT adiciona apenas import UUID e duas credenciais descartáveis ao bootstrap, sem mudar suas assertions.

| Teste anterior | Assertions mantidas e adequação |
| --- | --- |
| Readiness com/sem PostgreSQL | `src/test/java/br/com/monitor/ApplicationIT.java:32`/`:33` e `:36`/`:37`: HTTP 200/UP e 503/DOWN com banco real. |
| Credenciais de banco inválidas | `src/test/java/br/com/monitor/ApplicationIT.java:47`–`:49`: PSQLException e mensagem password authentication failed, sem mascarar falha pelo novo requisito administrativo. |
| Restart com schema/usuário monitor | `src/test/java/br/com/monitor/ApplicationIT.java:70`/`:74`: sentinela 4242 e uma entrada de history após segundo startup. |
| Migração exatamente uma vez | `src/test/java/br/com/monitor/MigrationIT.java:21`/`:23`/`:24`/`:30`/`:32`–`:36`: migração/schema reais, segundo migrate zero, checksum e installedOn preservados. |
| Migração aplicada alterada | `src/test/java/br/com/monitor/MigrationIT.java:53`/`:54`: FlywayValidateException com checksum mismatch depois de alterar SQL em arquivo temporário. |
| Independência da infraestrutura de testes | `src/test/java/br/com/monitor/ArchitectureTest.java:15`–`:18`: ArchUnit proíbe dependências de produção em JUnit, Testcontainers e ArchUnit. |

Guidelines revisadas: README.md, pom.xml, config/checkstyle.xml, config/run-tests.sh e padrões JUnit/AssertJ/Failsafe/Testcontainers existentes. Mudanças cirúrgicas; componentes apenas de segurança, configuração e operação; sem CRUD, painel ou migrations antecipados. Rotas de prova existem somente em TestConfiguration. Nenhum teste removido, enfraquecido, ignorado ou sem vínculo: classes novas cobrem AUTH-01–06; testes anteriores cobrem regressões MON-02 exigidas pelo lifecycle. Happy, edge e error paths da fronteira administrativa são exercitados com HTTP real. UAT visual não se aplica ao backend desta entrega.

## Sensor expandido de autenticação

Oito mutações comportamentais independentes, sempre no scratch. A fonte foi restaurada entre mutações e após a última; testes reais nunca foram modificados. Cada execução reconstruiu o serviço tests e executou `docker compose -p mon03-verifier run --rm tests sh -c 'mvn --batch-mode --no-transfer-progress -Dcheckstyle.skip -Dit.test=<classe> clean verify; result=$?; cp -a target/. /reports/; exit $result'`. M7/M8 também limparam /reports antes do Maven. Checkstyle foi omitido somente nos mutantes, para medir comportamento; gate baseline verificou qualidade integralmente.

| Sensor | Mutação em AdministrativeSecurityConfiguration.java | Classe selecionada / resultado | Evidência que matou |
| --- | --- | --- | --- |
| M1 | `:79`: anyRequest hasRole ADMIN → permitAll | SecurityIT: 29 integrações, 16 falhas, 0 erros; exit 1 | Assertions 401 de `AdministrativeSecurityIT.java:210` detectam bypass global. |
| M2 | `:71`: CSRF defaults → disable | SecurityIT: 29 integrações, 1 falha, 4 erros; exit 1 | Endpoint não entrega token esperado e sessão; `AdministrativeSecurityIT.java:123` e helper cookie `:181`. Os quatro erros são NoSuchElementException por cookie ausente, não falhas de infraestrutura. M7 abaixo prova a rejeição de escrita diretamente. |
| M3 | `:45`: remover rejeição de placeholders | ConfigurationIT: 13 integrações, 2 falhas, 0 erros; exit 1 | `AdministrativeConfigurationIT.java:59`, casos 7/8: placeholders indevidamente iniciam app. |
| M4 | `:52`: status 401 → 402 | SecurityIT: 29 integrações, 23 falhas, 0 erros; exit 1 | `AdministrativeSecurityIT.java:210` exige exatamente 401. |
| M5 | `:34`: acrescentar usuário/senha à mensagem de configuração | ConfigurationIT: 13 integrações, 13 falhas, 0 erros; exit 1 | `AdministrativeConfigurationIT.java:59` exige mensagem genérica exata. M8 isola abaixo a assertion dos logs. |
| M6 | `:31`: limite de usuário >128 → >129 | ConfigurationIT: 13 integrações, 1 falha, 0 erros; exit 1 | `AdministrativeConfigurationIT.java:59`, caso 11: usuário 129 indevidamente inicia app. |
| M7 | `:71`: matcher CSRF sempre false, mantendo geração de token/sessão | SecurityIT: 29 integrações, 4 falhas, 0 erros; exit 1 | `AdministrativeSecurityIT.java:169`: expected 403, actual 200 em POST/PUT/PATCH/DELETE. |
| M8 | `:34`: escrever usuário/senha em stdout antes do mesmo erro genérico | ConfigurationIT: 13 integrações, 13 falhas, 0 erros; exit 1 | `AdministrativeConfigurationIT.java:61`/`:64`: output contém os valores proibidos; rootCauseMessage continua correto. |

**Sensor:** 8 injetados, 8 mortos, 0 sobreviventes. Cada seleção executou também o unitário ArchitectureTest, sem falha/skip. Relatórios e logs por M1–M8 preservados em `.validation/mon03-verifier-evidence/`; `sensor-summary.txt` contém somente a classe efetivamente selecionada. XMLs remanescentes de outras classes no mount não foram contados como testes executados: as contagens acima foram rederivadas do XML selecionado e do log Maven de cada execução.

## Isolamento, encerramento e gaps

Baseline do porcelain real: vazio em `46c7832`. Após sensores, `git diff --exit-code` do scratch passou; runtime original reconstruído com exit 0 (`runtime-restored.log`). Projetos/volumes/imagem de testes próprios removidos; smoke já removeu seu projeto/volume. Worktree removido com `git worktree remove --force` após validar caminho absoluto dentro do workspace/.validation. Scripts temporários próprios também removidos.

`git status --porcelain` real imediatamente antes de escrever este relatório: vazio, exatamente igual à baseline. Nenhum git stash, commit ou push foi usado. Evidências permanecem no diretório ignorado .validation; o único arquivo real escrito pelo verificador é este relatório.

**Gaps ranqueados:** nenhum. **Spec:** 6/6 ACs com outcome preciso e evidência. **Gate:** aprovado. **Sensor:** 8/8 mortos. Não há sinal de falha para destilar em lessons.

**Completion gate:** `python .codex/skills/tlc-spec-driven/scripts/validate_state.py mon-03-acesso-administrativo`, executado após persistir este relatório; exit 0, zero erros.
