# Validation MON-12: PASS

**Data:** 2026-10-21, America/Sao_Paulo. **Verifier:** subagente independente `/root/mon12_verifier`, autor diferente do implementador. **Spec:** `spec.md`. **Diff:** `f7a6287` até working tree, incluindo não rastreados. Nenhum commit criado.

Oito critérios conferidos contra os resultados definidos na spec. Quick independente aprovado, três mutações semânticas detectadas e nenhum gap de produto encontrado. T1–T6 concluídas; a verificação técnica de T7 está concluída neste relatório. O orquestrador deve atualizar seus checkboxes, rastreabilidade e handoff para refletir este parecer.

## Critérios e assertions

| Critério | Resultado definido na spec | Evidência e assertion exata | Parecer |
| --- | --- | --- | --- |
| AC01 / DEMO-01 | Cópia sem artefatos inicia app/db/smtp/demo; dois monitores, seed repetido preserva identidade | `scripts/validate-clean.ps1:16`: `throw 'Clean copy contains generated target'`; `:41`: `$first.seed -ne 'OK'`; `:44`: comparação dos dois IDs; `:47`: `$health.status -ne 'UP'`; `:54`: IDs após down/up. `config/demo/scenario.mjs:55`: `assert.equal(...totalElements, 2)`. `.validation/mon12-clean-run.log:1`: PASS. Bundle `demo-evidence.json:1614`: generatedTargetAbsent true e cleanCheckout PASS. | PASS |
| AC02 / DEMO-02 | healthy 200 imediato, error 503 imediato, slow 200 após 15000 ms; alvos independentes | `config/demo/server.test.mjs:9`: status 200; `:11`: status 503; `:12`: secundário 200; `:16`: atraso reduzido >=110 ms. A verificação independente do default, `.validation/mon12-default.test.mjs:14`, exige `delayMs === 15000`; `:30` exige `slow.elapsed >= 15000`, `:29` exige status 200; `:26` exige secundário healthy. Probe real: healthy 4,430 ms, error 2,871 ms, secundário 3,431 ms e slow 15005,627 ms, exit 0 em `.validation/mon12-verifier-default.log:1`. “Imediato” foi observado sem atraso artificial; limite de 1000 ms é margem do probe, não promessa de SLA. | PASS |
| AC03 / DEMO-03 | I10/T2/F3/R2, queda confirmada, incidente único, DOWN SMTP, recuperação e mesmo incidente CLOSED com RECOVERY | `config/demo/seed.mjs:6`: intervalSeconds 10, timeoutSeconds 2; `:7`: failureThreshold 3/recoveryThreshold 2. `config/demo/scenario.mjs:62`: failureCount 1; `:63`: incidente null; `:66`: failureCount 3; `:71`: totalElements 1; `:73`: uma intenção; `:77`: recoveryCount 1; `:84`: mesmo ID; `:85`: CLOSED; `:89`: tipos DOWN/RECOVERY; `:92`: `downNotice.completedAt <= recovery.attempts[0].startedAt`. `.validation/mon12-demo-verified.log:255`: PASS normal. | PASS |
| AC04 / DEMO-04 | Reinício com DOWN PENDING após falha SMTP preserva IDs/histórico/incidente; mesma intenção aceita em até cinco tentativas, DOWN antes de RECOVERY | `config/demo/scenario.mjs:103`: FAILED; `:104`: SMTP_FAILURE; `:105`: attemptCount 1; `:111`: mesmo incidente; `:113`: PENDING; `:118`: cada executionId anterior presente; `:125`: attemptCount entre 2 e 5; `:126`: última tentativa ACCEPTED; `:127`: retry >=30000 ms. `.validation/mon12-demo-verified.log:260`, `:265`, `:270`: pending/restart/retry PASS. Auditoria independente `.validation/mon12-verifier-audit.py:49` compara ordem SMTP em ambos os incidentes do bundle. ID preservado `f8d91122-1d85-4b76-8027-af07c8129e5a`; retry 30,121013 s após falha. | PASS |
| AC05 / DEMO-05 | TIMEOUT e novo SUCCESS concorrentes; PAUSED, DOWN e SEM_DADOS_RECENTES distintos; pausa/lacuna preservam incidente | `config/demo/scenario.mjs:139`: SUCCESS secundário; `:140`: TIMEOUT principal; `:142`/`:143`: execução rápida inteiramente contida na lenta; `:149`: PAUSED; `:150`: DOWN; `:151`: mesmo incidente; `:154`: nenhum novo check após 11 s; `:161`: SEM_DADOS_RECENTES; `:162`: ACTIVE; `:163`: DOWN; `:164`: STALE; `:165`: mesmo incidente. `.validation/mon12-demo-verified.log:273`, `:274`, `:281`: PASS. Bundle auditado confirma os instantes e IDs. | PASS |
| AC06 / DEMO-06 | README cobre problema, funcionalidades, stack, arquitetura, pré-requisitos, variáveis, execução, painel, testes e diagnóstico; imagens reais | Inspeção documental em `README.md:3`, `:9`, `:11`, `:13`, `:49`, `:74`, `:107`, `:123`, `:160`. `config/demo/scenario.mjs:38` abre app real; `:40` exige quantidade de state-labels; `:41` exige destinatário nos detalhes; `:42` captura PNG, sem interceptação. Oito PNGs presentes em docs/images/mon12; inspeção independente de 02/03/06/07 confirmou labels e IDs do JSON. Auditoria `.validation/mon12-verifier-audit.py:75` exige zero links locais quebrados. | PASS |
| AC07 / DEMO-07 | Rastreabilidade para testes/specs/decisões/limites; valores MON-11 exatos, sem SLA | `docs/traceability.md:7` liga entregas às specs e testes; `:22` mapeia ACs; `:39` referencia AD-001..012; `:45` registra dados. `README.md:156`: 100 monitores, C4, 285 verificações, 60,070333095 s, máximo 20,634817 s e ausência de SLA. `.validation/mon12-verifier-audit.py:61` exige valores exatos do JSON MON-11 e `:63` soma 285; distribuição 143 SUCCESS/71 HTTP_FAILURE/69 TIMEOUT/2 CANCELLED confere com docs/capacity/mon-11.json. | PASS |
| AC08 / DEMO-08 | Comandos/resultados, evidências por AC, sensor e estado final fiel | `evidence.md:7` registra comandos/resultados; este relatório fornece assertion/file:line e sensor. `config/demo/scenario.mjs:174` exige dois incidentes; `:175` exige ambos CLOSED; `:179` exige quatro mensagens; `:182` exige destinatário fictício; `:188` preserva intenção pendente; `:190` exige uma intenção por incidente/tipo. `.validation/mon12-demo-verified.log:288` e `:289`: final e roteiro PASS. `.validation/mon12-verifier-audit.py:45` exige quatro intenções ACCEPTED no bundle. | PASS |

**Spec precision:** nenhum gap impeditivo. Os estados, contagens, status HTTP, atraso, identidade e ordem especificados possuem verificação observável. README e rastreabilidade foram revisados como contratos documentais, sem testes artificiais de texto.

## Gates e integridade

| Gate | Resultado observado |
| --- | --- |
| Quick independente: `docker compose run --build --rm --no-deps demo-seed node --test server.test.mjs seed.test.mjs` | Exit 0; 2 testes, 2 passed, zero failed/skipped. Imagem reconstruída do código atual. |
| Probe independente: Docker Node 22.14.0, bind mounts readonly de config/demo e `.validation`, `node --test /probe/mon12-default.test.mjs` | Exit 0; 1 teste, 1 passed, zero failed/skipped; atraso padrão real de 15 s. |
| Build: `docker compose run --build --rm tests` | Reutilizado por equivalência; `.validation/mon12-java.log:76` Checkstyle 0; `:136` 169 unitários; `:2487` 313 integrações; `:2492` BUILD SUCCESS. Zero failures/errors/skips. |
| Runtime + smoke: build app e `scripts/validate-compose.ps1 -NoBuild` | Reutilizado; build por up --build da execução limpa e Full. `.validation/mon12-smoke.log:151` PASS; `.validation/mon02-smoke-808dd16d06eb.log:452` encerra 37 PASS. |
| Playwright: projeto monitor-panel-tests | Reutilizado; `.validation/mon12-panel.log:288`: 50 passed. |
| Full: `scripts/demo.ps1 -ProjectName mon12-verified` | Reutilizado; dez snapshots, oito capturas, exit 0; `.validation/mon12-demo-verified.log:289`. Assertions e bundle auditados independentemente. |
| Checkout limpo: `scripts/validate-clean.ps1` | Reutilizado; `.validation/mon12-clean-run.log:1` PASS, mesmos IDs após seed repetido/down/up. |
| Estrutural | `validate_state.py mon-12-demonstracao` executado após gravar este relatório; resultado registrado no retorno do Verifier. |

O reúso evita rodar novamente cenários longos sem mudança relevante. `git diff f7a6287 -- src config/panel Dockerfile pom.xml config/run-tests.sh scripts/validate-compose.ps1` está vazio. Comparei hashes de **125 arquivos** de source/build/testes/Compose/scripts atuais com a cópia usada pelo gate limpo: nenhuma diferença. A revisão incluiu o código do Full e seus resultados, não apenas linhas PASS. Baseline: 482 Java e 50 navegador; permanecem 482 e 50, acrescidos de dois testes Node. Nenhum teste existente foi alterado, removido ou enfraquecido.

## Sensor de discriminação

Três mutações executadas exclusivamente em cópias sob `.validation/mon12-verifier-sensor/`, via `docker run --rm --mount type=bind,...,readonly -w /demo node:22.14.0-bookworm-slim node --test server.test.mjs seed.test.mjs`.

| Mutação semântica | Ponto real | Assertion que matou o mutante | Resultado |
| --- | --- | --- | --- |
| error responde 200 em vez de 503 | `config/demo/server.mjs:20` | `config/demo/server.test.mjs:11`: status deve ser 503; recebido 200 | Killed, exit 1; `.validation/mon12-verifier-sensor/status.log:17` |
| Remover espera de slow | `config/demo/server.mjs:19` | `config/demo/server.test.mjs:16`: `slow must delay headers` | Killed, exit 1; `.validation/mon12-verifier-sensor/delay.log:15` |
| Seed ignora existente e cria novos registros | `config/demo/seed.mjs:20` | `config/demo/seed.test.mjs:15`: `assert.deepEqual(await seed(call), first)`; IDs id-2/id-3 em vez de id-0/id-1 | Killed, exit 1; `.validation/mon12-verifier-sensor/seed.log:15` |

Profundidade lightweight proporcional ao auxiliar local: **3 injected, 3 killed, 0 survived**. Baseline em `.validation/mon12-verifier-sensor/baseline.json`. Antes e depois do descarte das três cópias, porcelain idêntico e **247 hashes** da árvore real preservados. Nenhum stash/worktree/commit usado. O único arquivo versionável criado pelo Verifier é este relatório.

## Casos de borda e qualidade

- Modo/recurso inválidos: `config/demo/server.test.mjs:17` exige 400, `:18`/`:19` exigem 404, `:21` mantém slow; métodos indevidos exigem 405.
- Seed incompatível/duplicado: `config/demo/seed.test.mjs:19` e `:22` exigem rejeição; implementação rejeita antes de criar/substituir o registro conflitante.
- Falha de dependência/assertion: `scripts/demo.ps1:28` lança erro para exit não zero; `:59` finally mantém banco/relatórios e tenta restaurar serviços; `:65` registra logs. Execuções iniciais realmente falharam e deixaram logs, antes das correções relatadas abaixo.
- Persistência sem remover volumes: `scripts/validate-clean.ps1:54` exige IDs iguais após down/up; PostgreSQL e Mailpit têm volumes nomeados. Snapshot do restart também preserva todos os executionIds anteriores.
- Basic/CSRF continua exigido; cliente não cria bypass. Origem privada explícita e exata, demo no loopback; SMTP local sem relay e destinatário fictício. Nenhum Java/migração foi alterado.
- Código simples e restrito à demonstração/documentação. Nenhuma regra de domínio nova. Testes Node mapeiam AC01/AC02 e suas bordas; Full mapeia AC03–05 e evidências AC06/08; domínio/painel usam gates existentes. Diretrizes: `.codex/skills/tlc-spec-driven/references/coding-principles.md` e decisões AD-001..012.

## Histórico, limites e lições

Dois gates iniciais falharam de forma útil e foram corrigidos antes desta revisão: Mailpit em memória perdia mensagens após stop/start; agora MP_DATABASE usa smtp-data (`compose.yaml:41`). Seed do runner concorria com seed inicial; agora o roteiro espera seu término (`scripts/demo.ps1:41`). Assertions de quatro mensagens e idempotência foram preservadas. Os logs e causas estão em `evidence.md:21`; a execução final passa. A correção de números narrativos MON-11 também foi conferida contra o JSON preservado.

Não há gaps ranqueados ou sobreviventes. Há sinal histórico para duas lições `gate_fail`: persistir o estado observável de dependências atravessando reinícios e serializar inicialização não protegida por unicidade no domínio. O orquestrador registrará essas lições pelo script da skill após receber este relatório, conforme coordenação; o Verifier não alterou o ledger fora do escopo autorizado.

UAT humano de design não foi realizado. Inspeção técnica de capturas não significa aprovação humana. CI remota não observada. ACCEPTED representa aceite SMTP local; não prova caixa postal nem exactly-once. A medição MON-11 foi reutilizada, sem novo benchmark. O projeto `mon12-verified` foi preservado em execução; a revisão não executou controles nele. Evidência final: dois monitores, dois incidentes CLOSED, quatro intenções ACCEPTED, quatro mensagens locais para portfolio@example.test.
