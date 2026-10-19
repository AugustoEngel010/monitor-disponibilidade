# MON-10: evidências

Baseline `be7fae1`, main limpa. Leitura: backlog, STATE AD-001..011, specs/contexts/designs e evidências relevantes MON-01..09, contratos docs/monitor-api.md e docs/queries.md, controllers, segurança, consultas e testes existentes. Sem AGENTS.md nem monitor-painel-demo.html. Sem lessons confirmadas.

## T1: cliente e infraestrutura

Gate: `docker compose --profile panel-tests up -d --build panel-app` e `docker compose --profile panel-tests run --build --rm panel-tests security.spec.js`, exit 0, 6/6. Logs `.validation/mon10-t1-build.log` e `.validation/mon10-t1-ui-final.log`. Backend e DB panel-* próprios; banco tmpfs, sem portas host, credenciais somente de fixture. Docker Desktop iniciado; nenhum banco do operador alterado.

Tentativa inicial 5/6: contexto request anônimo herdou credenciais globais do runner. Setup passou a declarar `httpCredentials: undefined`; todas as assertions mantidas. Imagem de testes reduzida de browsers completos para Node fixo + instalador oficial Chromium headless; lockfile gerado em contêiner. Não é teste pulado nem comportamento alterado.

| AC / done-when | Assertion file:line | Resultado definido | Coberto |
| --- | --- | --- | --- |
| AC06 página/assets anônimos | config/panel/security.spec.js:13 `expect(denied.status()).toBe(401)`; :14 corpo vazio; :15 challenge; :16 autenticado 200 | Basic global | Sim |
| AC06 CSRF/cookie | config/panel/security.spec.js:23 `toBe(403)` sem token; :29 `expect(created.name).toBe(configuration.name)` e :30 ACTIVE após cliente write | Efeito real somente com token | Sim |
| AC05/06 401/403/503 | config/panel/security.spec.js:44 `expect(result.status).toBe(status)` e :45 mensagem específica | Erro sanitizado, corpos vazios | Sim |
| AC05 escrita incerta | config/panel/security.spec.js:57 `toContain('Resultado da operação não confirmado')`; :58 `expect(calls).toBe(1)` | Não promete sucesso nem repete | Sim |

Reverse mapping: todos os seis testes acima correspondem a AC05/06 e edges de segurança/rede. Adequação A-D PASS: resultados de domínio assertados, nenhum teste removido/pulado, sem alteração em Java/segurança. Matriz/gates vêm de README/pom/CI; documentação sem teste próprio confirmada para T7.
