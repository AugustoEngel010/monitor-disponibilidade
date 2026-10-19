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

## T3: formulário

Premissas/arquivos: form.js e form.spec.js; backend decide validação, inputs persistem no DOM após falha, números enviados como números/null. Full Docker 8/8 em 13.0s, exit 0; `.validation/mon10-t3-build-final.log` e `.validation/mon10-t3-ui-final.log`. Primeira execução encontrou foco dependente da ordem dos erros no JSON; implementação corrigida para ordem visual, sem alterar assertion. Fixture usa intervalo 9 (fora do mínimo 10) para exercer erro nesse campo.

| AC / done-when | Assertion file:line | Outcome | Coberto |
| --- | --- | --- | --- |
| AC02 cadastro | config/panel/form.spec.js:22 status cadastrado; :24 `expect(persisted[key]).toBe(value)` para oito campos | POST real persistido com tipos corretos | Sim |
| AC02 edição | config/panel/form.spec.js:30 `toHaveValue(String(monitor[key]))`; :35..38 nome/destinatário/intervalo/version2 | GET e PUT reais | Sim |
| AC02 validação/preservação | config/panel/form.spec.js:51 valores intactos; :52 aria-invalid true; :54 erro não vazio; :56 Nome focado | Todos os oito erros associados | Sim |
| AC02/05 conflitos/403/rede/503 | config/panel/form.spec.js:72 mensagem; :73 todos os valores; :74 uma chamada; :75 botão habilitado | Sem retry automático | Sim |
| AC02 limite/404 | config/panel/form.spec.js:84 limite100; :86 monitor não encontrado | Erro específico | Sim |

Reverse mapping: oito testes cobrem cadastro, edição, validação, quatro falhas e limite/404; todos ligados a AC02/05. Adequação A-D PASS, nenhuma assertion existente enfraquecida nem teste removido/pulado.

## T4: pausa/retomada

Premissas/arquivos: lifecycle.js, app.js e lifecycle.spec.js/target.js; nenhum estado otimista. HTTP 503 controlado produz incidente real pelo scheduler, sem SQL de fixture. Full Docker com `run --use-aliases --build --rm panel-tests lifecycle.spec.js`: exit 0, 9/9 em 23.1s, `.validation/mon10-t4-build.log` e `.validation/mon10-t4-ui.log`. `--use-aliases` permite que o backend acesse somente a origem demo exata do servidor do runner, sem alterar proteção de destino de produção.

| AC / done-when | Assertion file:line | Outcome | Coberto |
| --- | --- | --- | --- |
| AC03 pausa/retomada/incidente | config/panel/lifecycle.spec.js:15 Pausado; :17 estado Indisponível; :21 ACTIVE; :22 DOWN; :23 `toBe(current.currentIncident.id)` | Backend preserva o mesmo incidente | Sim |
| AC03 aguardar confirmação | config/panel/lifecycle.spec.js:39 botão disabled; :40 Retomar count0; :41 ACTIVE antes da resposta; :43 Retomar após resposta; :44 uma chamada | Sem atualização otimista/duplicação | Sim |
| AC03/05 falhas pausa | config/panel/lifecycle.spec.js:54 mensagem; :55 Pausar; :56 ACTIVE | 403/503/rede não simulam pausa | Sim |
| AC03/05 falhas retomada | config/panel/lifecycle.spec.js:65 mensagem; :66 Retomar; :67 PAUSED | 403/503/rede não simulam retomada | Sim |
| AC02 incidente/política real | config/panel/lifecycle.spec.js:77 409 explícito; :78/79 valores preservados; :81 nome original; :82 intervalo10; :83 incidente não nulo | PUT rejeitado integralmente | Sim |

Reverse mapping: nove testes se dividem em fluxo real, confirmação pendente, seis falhas por rota e conflito real; todos ligados a AC02/03/05. Adequação A-D PASS; sem testes removidos/pulados ou assertions enfraquecidas.

## T5: detalhes

Premissas/arquivos: details.js e details.spec.js; nenhum indicador ou duração de incidente recalculado, histórico independente por recurso, filtros ISO com offset preservado. Build Docker aprovado; oito testes iniciais PASS, ampliados para paginação de cada recurso e resposta atrasada. Runner reconstruído por `docker compose --profile panel-tests build panel-tests`; execução `run --no-deps --use-aliases --rm panel-tests details.spec.js`, exit 0, 11/11 em 34.7s. Backend reutilizado sem mudança de produção entre as execuções. Logs `.validation/mon10-t5-build.log`, `.validation/mon10-t5-ui-final.log`.

| AC / done-when | Assertion file:line | Outcome | Coberto |
| --- | --- | --- | --- |
| AC04 fluxo real | config/panel/details.spec.js:14..21 HTTP503/incidente/PENDING/destinatário/count0; :24 UP real; :26..30 Encerrado/quatro instantes/notificação recuperação; :32 duração igual à API | Incidente aberto e recuperação reais | Sim |
| AC04/05 métricas da API | config/panel/details.spec.js:52 `toHaveText('80%')`; :53 100ms; :54..56 counts10/13/8; :57 Dados antigos; :58 idade100,9; :59 duração180,9 | Amostragem separada, frações preservadas | Sim |
| AC05 null/zero | config/panel/details.spec.js:76..77 `toHaveText(value === null ? 'Sem dados' : '0%')` / 0ms | Ausência distinta de zero | Sim |
| AC04 filtros reais/janela | config/panel/details.spec.js:90 vazio CLOSED; :99..100 status/type; :110..111 offset preservado e somente from/to; :112 Sem dados; :113 atual aberto | Sem filtrar incidente atual pela janela | Sim |
| AC04 paginação checks | config/panel/details.spec.js:125 check-page-1; :132..134 page0/size1/outcomeTIMEOUT | Filtros reiniciam página | Sim |
| AC04 notificações | config/panel/details.spec.js:149..155 SMTP/destinatário/transitionId/count2/SMTP_UNCERTAIN/horário exato; :156 sem promessa de caixa postal | Snapshot e tentativas | Sim |
| AC04 paginação incidentes/notificações | config/panel/details.spec.js:171 página1; :172 Próxima disabled; :174 outro histórico página0 | Controles independentes | Sim |
| AC05/06 falhas e validação | config/panel/details.spec.js:183 401; :184 503; :185 seção saudável vazia; :189..191 erro/input intacto/aria-invalid | Falhas por seção, sem mascarar acesso | Sim |
| AC05 navegação | config/panel/details.spec.js:209 rascunho preservado; :210 Resumo count0 | Resposta antiga não substitui formulário | Sim |

Reverse mapping: 11 testes correspondem às linhas acima (dois casos null/zero, dois paginadores). Adequação A-D PASS; todos derivados de AC04/05/06 e edges explícitos. Nenhum teste removido/pulado; nenhuma assertion afrouxada.
