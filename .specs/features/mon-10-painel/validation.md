# Validation: MON-10 PASS

**Revisão final independente:** 2026-10-19, `verify_mon10`, autor ≠ verifier. **Commit:** `68b2018a604634b3dbe36e26dc62962c572b8e2f`. **Diff total:** `be7fae1..68b2018`; **correção reavaliada:** `16a0b78..68b2018`.

T8 fecha F1 com assertions de valores próprios para os quatro instantes. Não remove nem enfraquece assertions anteriores. A spec explicita o oráculo já presente em docs/queries.md. Produção, Compose, Dockerfile runtime, Java e testes Java permanecem idênticos ao commit da revisão inicial. Os oito entregáveis estão concluídos, T8 em commit atômico 68b2018.

## Evidência atual por critério

| Critério / requisito | Resultado definido e assertion independente | Resultado |
| --- | --- | --- |
| MON-10-AC01 / PANEL-01 | `config/panel/list.spec.js:7` nome exato; `:28` sete labels oficiais traduzidos; `:29` estado DOWN separado, `:30` incidente preservado, `:31` datetime da observação; `:88`–`:89` paginação real | PASS |
| MON-10-AC02 / PANEL-02 | `config/panel/form.spec.js:24` oito valores/tipos persistidos iguais ao input; `:30` valores carregados; `:35`–`:38` PUT persistido e versão; `:51`–`:54` todos os campos/erros em 400; `:73` valores preservados em 403/409/503/rede, `:74` uma chamada | PASS |
| MON-10-AC03 / PANEL-03 | `config/panel/lifecycle.spec.js:15` pausa confirmada; `:21`–`:24` ACTIVE/DOWN/mesmo incidente; `:39` envio desabilitado, `:40` nenhuma retomada otimista, `:41` backend ainda ACTIVE, `:44` uma chamada; `:54`–`:67` falhas preservam estado | PASS |
| MON-10-AC04 / PANEL-04 | `config/panel/details.spec.js:14`–`:32` checks/incidente/notificações reais; `:59`–`:60` 100,9 s e 180,9 s; **`:63`–`:69` caption→datetime exato para os quatro campos CLOSED distintos**; **`:73`/`:75` instantes próprios OPEN; `:78`–`:79` recuperação null como Sem registro sem time**; `:115`–`:124` status/type/from e somente from/to no summary; `:143`–`:153` páginas independentes/outcome/size; `:169`–`:175` ACCEPTED, tentativas, SMTP_UNCERTAIN e limitação de entrega; `:191`–`:194` paginações dos outros recursos | PASS |
| MON-10-AC05 / PANEL-05 | `config/panel/list.spec.js:40`/`:47` vazios, `:58` loading, `:60`–`:64` erro fixo e nova consulta, `:71`–`:74` falha isolada/texto seguro/sem estado fictício; `config/panel/details.spec.js:53`–`:60` métricas/amostras/antiguidade/duração, `:83`–`:84` lacuna/cobertura, `:96`–`:97` null versus zero, `:203`–`:205` erros independentes, `:229`–`:230` rascunho preservado; `config/panel/security.spec.js:57`–`:58` resultado não confirmado/uma chamada | PASS |
| MON-10-AC06 / PANEL-06 | `config/panel/security.spec.js:13`–`:16` Basic/corpo vazio/challenge e 200 autorizado; `:23` 403 sem CSRF e `:29`–`:32` escrita real com token/cookie e localStorage vazio; `:44`–`:45` status/mensagem 401/403; `config/panel/form.spec.js:74` sem reenvio; `config/panel/responsive.spec.js:7`, `:22`–`:24`, `:33`–`:52` sem overflow, foco visível e todos os fluxos em 1440×900/390×844 | PASS |

**6/6 ACs e 6/6 requisitos demonstrados.** Edges mantidos: 401/403 vazios; falha individual de summary; seções independentes; navegação descarta resposta atrasada; conflitos 409 específicos; páginas excedentes e limites da resposta; currentIncident fora da janela; texto hostil como texto; duração fracionária; SMTP sem promessa de caixa postal; transporte sem reenvio. Evidência detalhada da revisão inicial abaixo continua válida, com linhas de details posteriores à inserção deslocadas +20 na revisão final. Não há lacuna de precisão da spec nem gap aberto.

## Gates finais

- Baseline UI independente final em projeto `mon10-verifier-t8`, app/DB novos, assets copiados sem alterações e perfil panel-tests. Comando: `docker compose -p mon10-verifier-t8 -f compose.yaml -f .validation/mon10-verifier-t8/override.yaml --profile panel-tests run --use-aliases --build --rm panel-tests`. **48 passed, 0 failed, 0 skipped, exit 0**, 1,5 min. Log `.validation/mon10-verifier-t8/baseline.log`; JUnit preservado em `.validation/mon10-verifier-t8/baseline-junit.xml`.
- Spec/tasks estruturais reexecutados independentemente: exit 0, zero erros; único aviso T7 Tests:none corresponde à camada documental.
- Gates Java459/Checkstyle0, runtime e smoke37 reutilizados da revisão inicial após `git diff --exit-code 16a0b78..68b2018 -- src/main compose.yaml Dockerfile pom.xml config/run-tests.sh` retornar 0. Não são apresentados como nova execução do Verifier. Baseline UI independente inicial também era 48/48; T8 mantém 48 testes, acrescenta assertions. Nenhum skip, teste removido ou assertion enfraquecida.
- Log do autor `.validation/mon10-t8-ui-clean.log`: 48 PASS após app/DB recriados. Repetição anterior `.validation/mon10-t8-ui-final.log` com processo reutilizado apresentou DNS_FAILURE antes do HTTP503 esperado; não foi ocultada nem resolvida afrouxando assertion. A revisão final usa processo e DB novos para a baseline real; sensores focam apresentação controlada sem depender da origem HTTP.

## Sensor final e isolamento

Sensor expandido: cinco mutantes mortos da revisão inicial permanecem equivalentes, pois produção e respectivas assertions discriminantes permanecem idênticas. O sobrevivente inicial m6 foi reexecutado com o teste corrigido; outras três trocas verificam cada campo de instante. Cópia isolada `.validation/mon10-verifier-t8/static` montada readonly, projeto próprio, um runner por vez, sem stash, sem alterar código/testes reais ou banco do operador.

| Mutação | Fonte (somente cópia) | Oráculo após T8 |
| --- | --- | --- |
| m6-down | `src/main/resources/static/panel/details.js:12`, downConfirmedAt→firstFailureAt | `config/panel/details.spec.js:69`: esperado 2026-10-18T12:02:00Z, recebido 2026-10-18T12:00:00.100Z |
| m7-first | `src/main/resources/static/panel/details.js:11`, firstFailureAt→downConfirmedAt | `config/panel/details.spec.js:69`: esperado 2026-10-18T12:00:00.100Z, recebido 2026-10-18T12:02:00Z |
| m8-start | `src/main/resources/static/panel/details.js:13`, recoveryStartedAt→recoveredAt | `config/panel/details.spec.js:69`: esperado 2026-10-18T12:03:01Z, recebido 2026-10-18T12:04:00Z |
| m9-recovered | `src/main/resources/static/panel/details.js:14`, recoveredAt→recoveryStartedAt | `config/panel/details.spec.js:69`: esperado 2026-10-18T12:04:00Z, recebido 2026-10-18T12:03:01Z |

Comando por mutante: mesmo projeto/override, `run --use-aliases --no-deps --rm panel-tests details.spec.js --grep exibe`. Só conta como morto se falhar pela comparação do datetime acima. Logs em `.validation/mon10-verifier-t8/m6-down.log` até `m9-recovered.log`.

**F1 resolvido.** A implementação já usava os campos corretos; a correção acrescenta um oráculo que discrimina as quatro associações sem mudar produção. L-005 permanece registrada como candidate, sem nova recorrência ou nova lição para a correção do mesmo sinal.

UAT visual humano **adiado pelo usuário**: “Vou conferir depois”. Isso não representa aprovação humana. CI remota não executada. Mantêm-se as evidências automatizadas e as amostras visuais independentes descritas abaixo.

**Fechamento do sensor:** 4/4 mutações de instantes reexecutadas e mortas, todas por `toHaveAttribute('datetime', timestamp)` em `config/panel/details.spec.js:69`, com os pares esperado/recebido da tabela acima, 1 failed e exit 1 por mutante. Somadas às cinco mutações equivalentes mortas na revisão inicial: **9/9 mutações distintas mortas no código/testes finais, zero sobreviventes** (4 executadas nesta revisão, 5 reutilizadas; m6 inicial sobrevivente foi encerrado pela T8).

**Isolamento:** porcelain antes e depois idênticos e vazios, ambos persistidos em `.validation/mon10-verifier-t8/porcelain-before.txt` e `porcelain-after.txt`, antes de atualizar este relatório. Scratch static removido após conferir caminho absoluto dentro de .validation; app/DB/rede mon10-verifier-t8 removidos. Nenhuma implementação, teste real, commit ou banco do operador alterado. Logs/JUnit/screenshots preservados. A única alteração rastreada desta re-verificação é validation.md.

**Gate de fechamento:** `python .codex/skills/tlc-spec-driven/scripts/validate_state.py mon-10-painel`, **exit 0, zero erros**, executado após gravar o relatório PASS.

## Histórico preservado: revisão inicial de 16a0b78

O relatório seguinte documenta o FAIL inicial e seu sensor sobrevivente. Seu veredito e plano referem-se exclusivamente ao commit inicial; o resultado vigente está no topo deste documento.


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
