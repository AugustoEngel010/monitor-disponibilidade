# MON-03: evidências de execução

**Data:** 2026-10-13. Gates locais em Docker Desktop Linux; CI remota não executada.

## T1

Spec --strict e tasks: zero erros/avisos. Commit f692ef3. AD-005 acrescentada sem alterar decisões anteriores.

## T2

`docker compose run --build --rm tests`: exit 0, 1 unitário + 42 integrações, zero failures/errors/skips e zero Checkstyle violations. Log local `.validation/mon03-t2-tests.log`; XMLs copiados pelo fluxo MON-02 para target. Os seis testes anteriores continuam presentes com assertions originais; ApplicationIT recebe somente credenciais efêmeras de teste.

| Critério / edge | file:line + assertion | Resultado da spec | Reverse mapping / manter |
| --- | --- | --- | --- |
| AUTH-01, caminhos e métodos sem Basic | `src/test/java/br/com/monitor/AdministrativeSecurityIT.java:210`, `response.statusCode()).isEqualTo(401)`; linha 211 header Basic; linha 213 `body()).isEmpty()` | 401, challenge e corpo vazio | MON-03-AC01, todos os paths/métodos parametrizados |
| AUTH-01, usuário/senha errados e header inválido | `src/test/java/br/com/monitor/AdministrativeSecurityIT.java:82`, `assertUnauthorized(send(...))` usa assertions 210–213 | 401 em GET/POST/PUT/PATCH/DELETE/OPTIONS | MON-03-AC01, cada variante necessária |
| AUTH-02/03, operador válido e painel futuro | `src/test/java/br/com/monitor/AdministrativeSecurityIT.java:91`, `statusCode()).isEqualTo(200)` | 200 em probes e actuator/info | MON-03-AC02/03 |
| AUTH-02, recurso inexistente | `src/test/java/br/com/monitor/AdministrativeSecurityIT.java:97`, `statusCode()).isEqualTo(404)` | 404 após autenticação | Edge de AUTH-02 |
| AUTH-03, health público | `src/test/java/br/com/monitor/AdministrativeSecurityIT.java:105`, `isEqualTo(200)`; 106 corpo UP exato; 107 cookies vazios; 108 HEAD 200; 109 POST 401 | Só GET/HEAD públicos, sem detalhes/sessão | MON-03-AC03; readiness DOWN em `src/test/java/br/com/monitor/ApplicationIT.java:36` 503 e 37 DOWN |
| AUTH-04, ausente/vazio/branco/placeholder/limites | `src/test/java/br/com/monitor/AdministrativeConfigurationIT.java:59`, `hasRootCauseMessage("Administrative credentials are missing or invalid")` | Startup falha sem ecoar inputs | MON-03-AC04, treze entradas parametrizadas |
| AUTH-05, logs e respostas | `src/test/java/br/com/monitor/AdministrativeConfigurationIT.java:61`, `output.getAll()).doesNotContain(username)`; 64 senha; `src/test/java/br/com/monitor/AdministrativeSecurityIT.java:63`, `doesNotContain(username, password, basic(...))`; 92 resposta sem segredo | Nenhuma credencial em saída da aplicação | MON-03-AC05 |

Adequação A/B/C/D: outcomes exatos, todos os novos testes ligados acima a requisitos/edges, sem shallow assertions ou mocks. Padrões JUnit/AssertJ/Failsafe e Checkstyle da MON-02 mantidos. Token/CSRF será provado em T3, não está declarado entregue em T2.

Falhas corrigidas antes do commit: CsrfConfigurer não oferece accessDeniedHandler, usar exceptionHandling compartilhado; health agregado inclui groups mesmo com show-details=never, extensão retorna status preservando HTTP; linha acima de 120 caracteres corrigida. Nenhuma assertion enfraquecida, removida ou ignorada.

## T3

`docker compose run --build --rm tests`: exit 0, 1 unitário + 47 integrações (48 total), zero failures/errors/skips/Checkstyle. Log `.validation/mon03-t3-tests.log`.

| Critério / edge | file:line + assertion | Resultado da spec | Reverse mapping / manter |
| --- | --- | --- | --- |
| Token protegido | `src/test/java/br/com/monitor/AdministrativeSecurityIT.java:115`, `assertUnauthorized(...)`; linha 116 senha inválida | 401 | AUTH-01/06 |
| Campos do endpoint | `src/test/java/br/com/monitor/AdministrativeSecurityIT.java:121`, `isEqualTo(200)`; 123 `token...isNotBlank()`; 124 headerName exato; 125 parameterName exato | 200, token, X-CSRF-TOKEN, _csrf | AUTH-06, cada campo tem assertion |
| Cookie/cache/origem | `src/test/java/br/com/monitor/AdministrativeSecurityIT.java:126`, `contains("JSESSIONID=", "HttpOnly", "SameSite=Lax")`; 128 CORS vazio; 129 no-store | Cliente da mesma origem; cookie de sessão protegido | AUTH-06 |
| Sessão sem autorização | `src/test/java/br/com/monitor/AdministrativeSecurityIT.java:131`, `assertUnauthorized(...)`; 151 Basic ausente com token válido; 152 Basic inválido | 401 mesmo com cookie/token | AUTH-01 |
| Ausente/inválido/outra sessão/token sem cookie/form forjado | `src/test/java/br/com/monitor/AdministrativeSecurityIT.java:145` a 150 `assertForbidden(...)`, helper 169 `isEqualTo(403)` e 170 `body().isEmpty()`; 154 `mutations.get()).isEqualTo(before)` | 403 sem efeito | AUTH-06, oito pares rota/método exercitam cada variante |
| Header válido | `src/test/java/br/com/monitor/AdministrativeSecurityIT.java:157`, `isEqualTo(200)`; 158 corpo before+1; 159 estado before+1 | Uma ação executada | AUTH-06 |
| Form válido | `src/test/java/br/com/monitor/AdministrativeSecurityIT.java:162`, `isEqualTo(200)`; 163 corpo before+2; 164 estado before+2 | Uma segunda ação executada | AUTH-06 |

Adequação A/B/C/D: todos os novos casos correspondem a AUTH-06 e edges AUTH-01/05, outcomes precisos e estado real observado. Nenhum teste removido/enfraquecido. Estratégia de login CSRF invalidava o token a cada escrita sob STATELESS; desabilitar sessionManagement e usar contexto apenas por requisição resolve Basic contínuo e preserva 404 no dispatch de erro. A confirmação usa a mesma sessão/token em duas ações válidas.

## T4

| Comando | Resultado |
| --- | --- |
| docker compose run --build --rm tests | exit 0; 48 testes; zero falhas/erros/skips/Checkstyle; `.validation/mon03-t4-tests.log` |
| docker compose build app | exit 0; runtime construído; `.validation/mon03-t4-build.log` |
| powershell -NoProfile -ExecutionPolicy Bypass -File scripts/validate-compose.ps1 -NoBuild | exit 0; projeto mon02-smoke-6c483208df76; `.validation/mon03-t4-smoke.log` e log do projeto |

| Critério | file:line + assertion/verificação | Resultado | Reverse mapping |
| --- | --- | --- | --- |
| Externo obrigatório | `compose.yaml:26` e 27 ADMIN_* sem defaults válidos; `scripts/validate-compose.ps1:104` ExpectedExit 1; 105 mensagem genérica | Ausência de cada variável impede app; tests/build independentes | AUTH-04 |
| Operador válido/ausente | `scripts/validate-compose.ps1:84`, `Assert-Equal $status $ExpectedStatus`; chamadas 112/113 | 401 sem Basic, 200 com Basic | AUTH-01/02 |
| Rotação | `scripts/validate-compose.ps1:117`, novo ambiente e force-recreate; 120 ExpectedStatus401 com cookie antigo; 121 ExpectedStatus200 nova senha | Senha anterior e sessão antiga não autorizam | AUTH-02/05 |
| Logs sem segredos | `scripts/validate-compose.ps1:90` / 94 rejeitam texto e Basic codificado; 140 executa verificação | Nenhuma credencial nas duas versões do app ou startups inválidos | AUTH-05 |
| Persistência/saúde | `scripts/validate-compose.ps1:137` / 138 assertions sentinela/history; Wait-Ready 58/59 200/UP | Prontidão pública e fluxo MON-02 preservados | AUTH-03; regressão MON-02 |
| Exemplos/Git | `.env.example:3` / 4 placeholders, `.gitignore:2` / 3 env ignorado, `.dockerignore:1` allowlist | Nenhuma credencial administrativa preenchida versionada ou na imagem | AUTH-05 |

Adequação: smoke usa somente seu projeto, credenciais aleatórias e restaura ambiente do processo. Não remove volume de desenvolvimento. Mesmos estágios Dockerfile, gates de CI, rede/socket e cópia de relatórios da MON-02. Compose continua com healthcheck de db; prontidão de app é consultada pelo smoke, não exige segredo nem nova ferramenta no runtime.

## T5

Documentação revisada contra código e comandos executados. Gate Build de T4 permanece válido: T5 só altera README e artefatos, sem mudança na implementação/configuração. Spec --strict/tasks zero erros/avisos; git diff --check exit 0; git ls-files .env* retorna somente .env.example. Nenhuma credencial real definida ou versionada. Revisão independente repete o gate sobre o checkout commitado.

| Critério | file:line + verificação | Resultado / reverse mapping |
| --- | --- | --- |
| Configuração e rotação | `README.md:7` instrução de preencher .env; 22/23 variáveis; 43 rotação e 46 comando force-recreate | AUTH-04/05 e edge AUTH-02 |
| Rotas e CSRF | `README.md:51` tabela; 61 fluxo; 66 exemplo do endpoint; 73 cookie/origem | AUTH-01/02/03/06 |
| Prontidão | `README.md:75` healthcheck de db, dependência, probe app público e startup fechado | AUTH-03/04; mantém MON-02 |
| Placeholders e Git | `README.md:35` / 36 placeholders; `.env.example:3` / 4; `.gitignore:2` / 3; `.dockerignore:1` allowlist | AUTH-05; nenhum teste tautológico de frases criado |

Adequação: revisão documental com gate operacional já observado; todos os parágrafos novos rastreiam requisitos de segurança/configuração/CSRF solicitados. Nenhuma interface CRUD ou painel antecipada, nenhum comando Maven/JDK no host.

## Encerramento

Verificador independente PASS 6/6 em 46c7832; gates Docker repetidos em checkout limpo isolado, 48 testes e oito sensores comportamentais mortos. Nenhuma correção de código/testes exigida. [Relatório](validation.md) contém evidência rederivada e comandos exatos; baseline XML preservada em `.validation/mon03-verifier-evidence/baseline-target`. Fechamento altera somente documentação, backlog e handoff. Spec/tasks/state validados deterministicamente; CI remota não observada.
