# MON-03: evidências de execução

**Data:** 2026-10-13. Gates locais em Docker Desktop Linux; CI remota não executada.

## T1

Spec --strict e tasks: zero erros/avisos. Commit f692ef3. AD-005 acrescentada sem alterar decisões anteriores.

## T2

`docker compose run --build --rm tests`: exit 0, 1 unitário + 42 integrações, zero failures/errors/skips e zero Checkstyle violations. Log local `.validation/mon03-t2-tests.log`; XMLs copiados pelo fluxo MON-02 para target. Os seis testes anteriores continuam presentes com assertions originais; ApplicationIT recebe somente credenciais efêmeras de teste.

| Critério / edge | file:line + assertion | Resultado da spec | Reverse mapping / manter |
| --- | --- | --- | --- |
| AUTH-01, caminhos e métodos sem Basic | `src/test/java/br/com/monitor/AdministrativeSecurityIT.java:111`, `response.statusCode()).isEqualTo(401)`; linha 112 header Basic; linha 114 `body()).isEmpty()` | 401, challenge e corpo vazio | MON-03-AC01, todos os paths/métodos parametrizados |
| AUTH-01, usuário/senha errados e header inválido | `src/test/java/br/com/monitor/AdministrativeSecurityIT.java:78`, `assertUnauthorized(send(...))` usa assertions 111–114 | 401 em GET/POST/PUT/PATCH/DELETE/OPTIONS | MON-03-AC01, cada variante necessária |
| AUTH-02/03, operador válido e painel futuro | `src/test/java/br/com/monitor/AdministrativeSecurityIT.java:89`, `statusCode()).isEqualTo(200)` | 200 em probes e actuator/info | MON-03-AC02/03 |
| AUTH-02, recurso inexistente | `src/test/java/br/com/monitor/AdministrativeSecurityIT.java:95`, `statusCode()).isEqualTo(404)` | 404 após autenticação | Edge de AUTH-02 |
| AUTH-03, health público | `src/test/java/br/com/monitor/AdministrativeSecurityIT.java:102`, `isEqualTo(200)`; 103 corpo UP exato; 104 cookies vazios; 105 HEAD 200; 106 POST 401 | Só GET/HEAD públicos, sem detalhes/sessão | MON-03-AC03; readiness DOWN em `ApplicationIT.java:37` 503 e 38 DOWN |
| AUTH-04, ausente/vazio/branco/placeholder/limites | `src/test/java/br/com/monitor/AdministrativeConfigurationIT.java:65`, `hasRootCauseMessage("Administrative credentials are missing or invalid")` | Startup falha sem ecoar inputs | MON-03-AC04, treze entradas parametrizadas |
| AUTH-05, logs e respostas | `src/test/java/br/com/monitor/AdministrativeConfigurationIT.java:67`, `output.getAll()).doesNotContain(username)`; 70 senha; `AdministrativeSecurityIT.java:56`, `doesNotContain(username, password, basic(...))`; 90 resposta sem segredo | Nenhuma credencial em saída da aplicação | MON-03-AC05 |

Adequação A/B/C/D: outcomes exatos, todos os novos testes ligados acima a requisitos/edges, sem shallow assertions ou mocks. Padrões JUnit/AssertJ/Failsafe e Checkstyle da MON-02 mantidos. Token/CSRF será provado em T3, não está declarado entregue em T2.

Falhas corrigidas antes do commit: CsrfConfigurer não oferece accessDeniedHandler, usar exceptionHandling compartilhado; health agregado inclui groups mesmo com show-details=never, extensão retorna status preservando HTTP; linha acima de 120 caracteres corrigida. Nenhuma assertion enfraquecida, removida ou ignorada.
