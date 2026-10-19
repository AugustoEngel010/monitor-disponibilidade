# MON-10 Validation

**Verdict: FAIL.** A baseline independente passa, mas a suíte não detecta a troca do instante de confirmação da queda pelo da primeira falha. A implementação original usa os campos corretos; o bloqueio é uma lacuna comprovada de discriminação dos testes.

**Date:** 2026-10-19. **Verifier:** subagente independente `verify_mon10`, autor ≠ verifier.
**Spec:** `.specs/features/mon-10-painel/spec.md`.
**Diff range:** `be7fae1..16a0b783be39c125c4799e7c05d18a9349145661`.

## Escopo e tarefas

T1–T7 estão marcadas como concluídas e correspondem a sete commits atômicos: aad2998, a07e271, 5e124ed, b53b303, 22097c2, 5f1ad77 e 16a0b78. Revisão independente de spec, design, tasks, STATE, contratos MON-04/09, código estático, testes e workflow. O relatório do autor `evidence.md` não foi usado para derivar a cobertura abaixo.

O diff adiciona cliente estático nativo, infraestrutura de navegador, documentação e CI. Não altera Java, migrações, pom.xml, Dockerfile runtime, config/run-tests.sh nem testes Java. Não há redução nem enfraquecimento de testes preexistentes. Não foi executada CI remota nem UAT humano.

## Critérios e resultados esperados

| AC / requisito | Resultado definido pela spec | Evidência independente: arquivo:linha e assertion | Resultado |
| --- | --- | --- | --- |
| MON-10-AC01 / PANEL-01 | Nome, label oficial, estado confirmado separado, observação e incidente; sete labels compreensíveis | `config/panel/list.spec.js:7` `toHaveText(monitor.name)`; `:28` `toHaveText(text)` para PAUSED/SEM_DADOS/SEM_DADOS_RECENTES/CONFIRMANDO_QUEDA/CONFIRMANDO_RECUPERACAO/UP/DOWN; `:29` estado DOWN separado, `:30` ID do incidente, `:31` `toHaveAttribute('datetime', '2026-10-18T12:00:00Z')`; `:88` próximo monitor real e `:89` remoção do anterior | PASS |
| MON-10-AC02 / PANEL-02 | POST/PUT com oito valores/tipos, erros por campo, preservar valores em 400/409/403/rede | `config/panel/form.spec.js:24` `expect(persisted[key]).toBe(value)` para os oito campos, incluindo números; `:30` valores de edição; `:35`–`:38` nome/destinatário/intervalo persistidos e versão 2; `:51`–`:54` todos os valores, aria-invalid e erro não vazio em 400; `:73` todos os valores após falha e `:74` uma chamada; `:84` mensagem do limite | PASS |
| MON-10-AC03 / PANEL-03 | Pausa/retomada somente após confirmação, incidente preservado, envio bloqueado, sem sucesso otimista ou repetição | `config/panel/lifecycle.spec.js:15` label Pausado; `:16`–`:17` mesmo incidente e DOWN; `:21`–`:24` ACTIVE/DOWN/mesmo ID após retomada; `:39` botão desabilitado, `:40` nenhuma ação Retomar antes da resposta, `:41` backend ACTIVE, `:44` uma chamada; `:54`–`:56` e `:65`–`:67` erro e ciclo preservado | PASS |
| MON-10-AC04 / PANEL-04 | Summary/checks/incidents/notifications reais, paginação independente e filtros; quatro instantes corretos, idade/duração fracionária, tentativas/códigos | `config/panel/details.spec.js:14`–`:32` dados reais de checks, incidente até recuperação, notificações e duração da API; `:59` `100,9 s`, `:60` `180,9 s`; `:95`–`:104` status/type/from e somente from/to no summary; `:123` página 1, `:129`–`:133` page/size/outcome e outro histórico intacto; `:149`–`:155` ACCEPTED/tentativas/SMTP_UNCERTAIN/instante da tentativa/limitação SMTP; `:171`–`:174` outras paginações. Porém `:16`, `:17`, `:27`, `:28` verificam somente títulos dos quatro instantes, sem valores correspondentes | **GAP: mutante m6 sobrevive** |
| MON-10-AC05 / PANEL-05 | Loading/vazio/ausência/antiguidade/falha explícitos, consulta novamente, null diferente de zero, métricas por amostragem e cobertura separadas | `config/panel/list.spec.js:40`/`:47` vazios específicos; `:58` loading, `:60` mensagem fixa e `:61` sem erro bruto, `:64` nova consulta; `:71` erro por cartão e `:74` sem estado inventado; `config/panel/details.spec.js:53`–`:64` 80%, 100 ms, quantidades e lacuna; `:76`–`:77` Sem dados versus 0%/0 ms; `:183`–`:185` falhas independentes; `config/panel/security.spec.js:57` resultado não confirmado, `:58` uma chamada | PASS |
| MON-10-AC06 / PANEL-06 | Basic em página/assets; cookie/CSRF nas escritas; 401/403 claros sem reenvio; labels, foco, fluxos em 1440×900 e 390×844 sem overflow | `config/panel/security.spec.js:13`–`:16` 401/corpo vazio/challenge Basic/200 autenticado; `:23` 403 sem token, `:29`–`:31` escrita real autorizada, `:32` localStorage vazio; `:44`–`:45` status/mensagem 401/403; `config/panel/form.spec.js:74` uma escrita em 403; `config/panel/responsive.spec.js:7` scrollWidth ≤ innerWidth, `:22` foco via Tab, `:24` outline visível, `:33` pausa, `:38` retomada, `:42` edição, `:44` detalhe, `:48` consulta, `:52` nenhum pageerror, em ambos os viewports | PASS |

Não há lacuna de precisão da spec: os campos dos quatro instantes são definidos por `docs/queries.md` e pela própria AC04. A lacuna é a ausência de assertions dos valores de cada campo. Resultado: 5/6 ACs integralmente demonstrados; 1/6 parcial.

## Edges

| Edge | Evidência e resultado |
| --- | --- |
| 401/403 vazios, sem persistência de credenciais | `config/panel/security.spec.js:14`, `:32`, `:44`–`:45`; cliente trata status antes de decodificar corpo em `src/main/resources/static/panel/api.js:32`. PASS |
| Summary individual falha sem fabricar estado | `config/panel/list.spec.js:71` e `:74`; erro reside no cartão e catch individual em `src/main/resources/static/panel/app.js:32`. PASS |
| Seções independentes e resposta tardia após navegação | `config/panel/details.spec.js:183`–`:185`, `:209` rascunho preservado, `:210` nenhum heading Resumo. PASS |
| OPEN_INCIDENT e MONITOR_LIMIT | `config/panel/form.spec.js:72`, `:84`; `config/panel/lifecycle.spec.js:77`–`:83` conflito real preserva formulário/backend/incident ID; mensagens específicas em `src/main/resources/static/panel/api.js:10`. PASS |
| Página excedente, limites da resposta, janela antiga e incidente atual | `config/panel/list.spec.js:40`–`:41`; `config/panel/details.spec.js:105`–`:106`, `:172` próximo desabilitado. PASS |
| Texto hostil como texto | `config/panel/list.spec.js:72` nome literal, `:73` nenhum img; helper DOM comum usa append de strings e textContent, sem innerHTML; também aplicado a URL/destinatário. PASS |
| 180.9 s fracionário e ACCEPTED sem promessa de caixa postal | `config/panel/details.spec.js:60` `180,9 s`, `:149` Aceita pelo SMTP, `:155` explicação. PASS |
| Transporte após escrita, sem repetir | `config/panel/security.spec.js:57`–`:58`; `config/panel/form.spec.js:72`–`:75`. PASS |

## Gates

| Gate | Execução/equivalência | Resultado |
| --- | --- | --- |
| UI completa independente | `docker compose -p mon10-verifier -f compose.yaml -f .validation/mon10-verifier/override.yaml --profile panel-tests up -d --no-build panel-app`; `run --use-aliases --build --rm panel-tests`. Projeto próprio, DB tmpfs inicialmente vazio, app runtime final e cópia idêntica dos arquivos estáticos | **48 passed, 0 failed, 0 skipped, exit 0**, 1,5 min. Log `.validation/mon10-verifier/baseline.log` |
| Spec estrutural independente | `python .codex/skills/tlc-spec-driven/scripts/validate_spec.py mon-10-painel` | exit 0, 0 erros/avisos |
| Tasks estrutural independente | `python .codex/skills/tlc-spec-driven/scripts/validate_tasks.py mon-10-painel` | exit 0, 0 erros; aviso T7 Tests:none confere com matriz documental |
| Java/Checkstyle do checkout final | Reutilizado após inspeção dos logs finais e equivalência do diff, sem alterações em backend/testes/build Java | `.validation/mon10-final-java.log:87` zero Checkstyle; `:143` 167 unit, total IT 292, BUILD SUCCESS. **459/459**, zero falhas/erros/skips; não reexecutado pelo Verifier |
| Runtime Docker final | Reutilizado `.validation/mon10-final-runtime.log`, build concluído; imagem final usada também no app do Verifier | build sucesso, execução local do app confirmada; não recompilado pelo Verifier |
| Smoke Compose | Reutilizado `.validation/mon10-final-smoke.log:77` e `.validation/mon02-smoke-83e9ec7f7eb2.log` | 37 PASS, lifecycle PASS, incluindo restart/persistência/rotação; não reexecutado pelo Verifier |

Contagens anteriores à feature: 459 Java, 0 UI. Contagens finais: 459 Java +48 UI. Delta +48 UI, nenhum skip. As 48 UI mapeiam aos ACs/edges acima e aos Done when de T1–T6. README e CI usam os comandos do gate, preservam relatórios e usam `--use-aliases` para a origem exata do servidor controlado. Seis PNGs existem em docs/images/mon10; inspeção visual independente amostrou desktop-form e mobile-details. Revisão humana continua não executada.

## Sensor comportamental

Profundidade expandida de seis mutações, proporcional ao cliente administrativo e CSRF. Cópia em `.validation/mon10-verifier/static`, montada readonly em `/panel-static`; `SPRING_WEB_RESOURCES_STATIC_LOCATIONS=file:/panel-static/`. Nenhum código ou teste real alterado; nenhum stash. Um runner por vez, projeto próprio e sem portas/banco do operador.

Comando por mutante: `docker compose -p mon10-verifier -f compose.yaml -f .validation/mon10-verifier/override.yaml --profile panel-tests run --use-aliases --no-deps --rm panel-tests <spec> --grep <regex>`.

| ID | Mutação em arquivo original:linha (aplicada só na cópia) | Assertion/resultado discriminante | Estado |
| --- | --- | --- | --- |
| m1-csrf | `src/main/resources/static/panel/api.js:49`, token válido → invalid-token | security.spec.js, cliente envia CSRF: escrita recebe ApiError 403 em vez do monitor criado; 1 failed, exit 1 | KILLED |
| m2-payload | `src/main/resources/static/panel/form.js:55`, Number(value) → Number(value)+1 | `config/panel/form.spec.js:24`, esperado intervalo 120, recebido 121; 1 failed, exit 1 | KILLED |
| m3-disable | `src/main/resources/static/panel/lifecycle.js:10`, desabilitar envio → manter habilitado | `config/panel/lifecycle.spec.js:39`, esperado disabled, recebido enabled; 1 failed, exit 1 | KILLED |
| m4-duration | `src/main/resources/static/panel/details.js:17`, truncar duração final com Math.floor | `config/panel/details.spec.js:60`, esperado 180,9 s, recebido 180 s; 1 failed, exit 1 | KILLED |
| m5-label | `src/main/resources/static/panel/dom.js:36`, label do summary → estado confirmado | `config/panel/list.spec.js:28`, esperado Pausado, recebido Indisponível; 1 failed, exit 1 | KILLED |
| m6-instants | `src/main/resources/static/panel/details.js:12`, downConfirmedAt → firstFailureAt na linha Queda confirmada | Todos os 7 testes MON-10-AC04 passam, inclusive ciclo real completo; exit 0, 29,9 s | **SURVIVED** |

Logs individuais em `.validation/mon10-verifier/m1-csrf.log` até `m6-instants.log`. As primeiras seleções de m3/m4 com acentos deram No tests found e foram descartadas como inválidas; reexecução com regex ASCII `aguarda`/`recebidas`. Falha de infraestrutura/seleção nunca conta como mutante morto.

**Resultado: 5 killed, 1 survived, FAIL.** Porcelain antes: vazio, capturado na saída da ferramenta antes do sensor. Porcelain depois do sensor e restauração: vazio, confirmado novamente com o rascunho do relatório temporariamente fora da árvore rastreada. O arquivo before.txt inicialmente pretendido não foi criado pelo pipeline PowerShell sem saída; o registro anterior é a captura textual da ferramenta, e não um arquivo fabricado posteriormente. A captura final vazia está em `.validation/mon10-verifier/porcelain-after.txt`. Depois disso, somente este relatório e as lições geradas são alterações autorizadas. Contêineres/rede `mon10-verifier` removidos; cópia estática descartada após conferir seu caminho absoluto dentro de .validation. Logs e artefatos diagnósticos preservados.

## Qualidade e plano de correção

Mudanças são coesas, sem dependências runtime novas, sem regras de domínio duplicadas no navegador, com apresentação segura de texto, erros fixos e componentes pequenos. Padrões/guidelines: `.codex/skills/tlc-spec-driven/references/coding-principles.md`, AD-003/004/005/011 e contratos docs/monitor-api.md e docs/queries.md. Cobertura de fluxo feliz/edge/erro dos componentes existe. A exigência de assertions sobre valores compostos não está completa para o incidente.

**F1, Major, PANEL-04 / MON-10-AC04:** fortalecer `config/panel/details.spec.js` com um incidente cujos quatro instantes sejam diferentes. Conferir a correspondência de cada rótulo com o atributo datetime correto de firstFailureAt, downConfirmedAt, recoveryStartedAt e recoveredAt, tanto em summary.currentIncident quanto no histórico. Manter assertions existentes e comparar também o contrato real quando presente. Gate: suíte details e completa passam no original; m6 deve falhar no mutante. Não alterar implementação para adequá-la aos testes, pois o mapeamento original está correto.

Não marcar a feature pronta até o fix e a revalidação independente. `validate_state.py` não é executado como gate PASS nesta revisão FAIL. Nenhuma alteração em spec/tasks/STATE/implementação/testes é feita pelo Verifier.

Lição registrada via script: L-005, candidate, grounded em m6-instants. Nenhuma outra lição foi criada para repetir o mesmo sinal.
