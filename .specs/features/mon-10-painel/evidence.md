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

## T2: lista

Premissas/arquivos: app.js/dom.js, shell e testes fixtures/list; label exclusivamente do summary, erros isolados e renderizações desconectadas descartadas. Gate Docker Full, 12/12 em 20.2s, exit 0; `.validation/mon10-t2-build.log`, `.validation/mon10-t2-ui.log`. Labels controlados por interceptação sobre summary real; lista e paginação de 21 registros usam backend real.

| AC / done-when | Assertion file:line | Outcome | Coberto |
| --- | --- | --- | --- |
| AC01 nome/links/dados | config/panel/list.spec.js:7 `toHaveText(monitor.name)`; :9 `toContainText('Incidente aberto: não')`; :10 href com UUID | Dados e navegação reais | Sim |
| AC01 sete labels/dimensões | config/panel/list.spec.js:28 `toHaveText(text)`; :29 estado DOWN separado; :30 ID incidente; :31 datetime exato | Inclui pausa, stale e confirmações | Sim |
| AC05 vazio/página excedente | config/panel/list.spec.js:40 mensagem; :41 Anterior habilitado; :47 vazio realçado | Não inventa disponibilidade | Sim |
| AC05 loading/retry/503 | config/panel/list.spec.js:58 loading; :60 mensagem fixa; :61 sem detalhes brutos; :64 paginação após retry | Erro claro e recuperação por GET | Sim |
| AC05 falha parcial/XSS | config/panel/list.spec.js:71 alerta; :72 nome literal; :73 img count0; :74 label count0 | Sem estado fictício nem execução HTML | Sim |
| AC01 página real seguinte | config/panel/list.spec.js:86 primeiro visível; :88 próximo visível; :89 primeiro count0 | Backend page=1 | Sim |

Reverse mapping: os 12 testes correspondem às linhas AC01/05 acima; testes dos sete labels usam a mesma assertion com os sete valores definidos na spec. Adequação A-D PASS; sem testes apagados/pulados/enfraquecidos.

Regressão Java iniciada em T1: `.validation/mon10-java.log`, BUILD SUCCESS, 459 testes (167 unitários/292 integrações), zero falhas/erros/skips, Checkstyle zero. Nenhum fonte Java modificado nesta feature.
