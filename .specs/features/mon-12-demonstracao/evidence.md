# MON-12: evidências de execução

Data: 21/10/2026, America/Sao_Paulo. Baseline Git `f7a6287`; alterações locais sem commits, conforme pedido. Nenhum código Java ou migração foi alterado.

## Gates concluídos

| Comando | Resultado | Evidência local |
| --- | --- | --- |
| `docker compose run --build --rm tests` | exit 0; 169 unitários + 313 integrações = 482; zero failures/errors/skips; Checkstyle sem violações | `.validation/mon12-java.log:136`, `:2487`, `:2492`; XMLs em target |
| `docker compose -p monitor-panel-tests --profile panel-tests run --use-aliases --build --rm panel-tests` | exit 0; 50 passed | `.validation/mon12-panel.log` |
| `powershell -NoProfile -ExecutionPolicy Bypass -File scripts/validate-compose.ps1 -NoBuild` | exit 0; 37 PASS | `.validation/mon12-smoke.log`; `.validation/mon02-smoke-808dd16d06eb.log` |
| `docker compose run --build --rm --no-deps demo-seed node --test server.test.mjs seed.test.mjs` | exit 0; dois casos, zero failures/skips | Execução inicial e execução integrada do roteiro |
| `powershell -NoProfile -ExecutionPolicy Bypass -File scripts/validate-clean.ps1` | exit 0; cópia sem target/configuração do operador; build, quatro serviços, seed repetido e down/up com os mesmos IDs | `.validation/mon12-clean-run.log`, `.validation/mon12-clean.log`, `.validation/mon12-clean.json` |
| `python .codex/skills/tlc-spec-driven/scripts/validate_spec.py mon-12-demonstracao` | exit 0; zero erros/warnings | Execução local |
| `python .codex/skills/tlc-spec-driven/scripts/validate_tasks.py mon-12-demonstracao` | exit 0; zero erros/warnings | Execução local |

O build runtime foi executado por `up --build` tanto no roteiro quanto na cópia limpa, a partir de pom/config/src. Nenhum JAR do host é consumido. O smoke e a cópia limpa removeram apenas seus recursos descartáveis.

## Problemas detectados durante a execução

1. `.validation/mon12-demo.log`: o primeiro roteiro chegou ao fim das regras de domínio, mas Mailpit tinha duas mensagens em vez de quatro após stop/start. O ambiente usava armazenamento em memória. Correção: `MP_DATABASE=/data/mailpit.db` e volume `smtp-data`, sem enfraquecer a assertion de quatro mensagens.
2. `.validation/mon12-demo-final.log`: seed do runner começou antes de terminar o seed de startup, criando identidade duplicada. Correção: orquestração aguarda `docker compose wait demo-seed` antes de qualquer nova execução. Seeds continuam seriais, como o contrato; a API de domínio continua permitindo URLs duplicadas.
3. A tabela narrativa de capacidade MON-11 tinha SUCCESS=144 e HTTP_FAILURE=72, diferentes do JSON versionado. Corrigida para **143 SUCCESS, 71 HTTP_FAILURE, 69 TIMEOUT e 2 CANCELLED**, total 285, e médias 155,071ms/5,883ms. A medição não foi reexecutada ou alterada.

## Reprodução limpa

Projeto descartável `mon12-clean-3a01ebde`, portas aleatórias. O script copiou arquivos versionáveis atuais (incluindo alterações pedidas) sem `.env`, `target`, `.validation`, `.git` ou caches. Gerou `.env` a partir de `.env.example` com fixtures fictícias. `up --build -d`, seed serial repetido, readiness UP e down/up passaram.

IDs preservados nas três leituras: principal `084e34cf-944f-4e4d-951a-649a4c59855a`, secundário `48599aa4-a601-4455-83bf-16fab8d704bf`. O volume desse teste foi removido ao final. A cópia local permanece disponível para inspeção.

## Roteiro completo

A execução final usa `powershell -NoProfile -ExecutionPolicy Bypass -File scripts/demo.ps1 -ProjectName mon12-verified`. **Exit 0; roteiro completo PASS.** Log `.validation/mon12-demo-verified.log`; screenshots e JSON locais em `.validation/demo/`. [Bundle versionável de snapshots](demo-evidence.json) e [oito imagens reais](../../../docs/images/mon12) preservam a evidência fora do diretório ignorado.

Principal `a0d16184-418e-4b9d-bfa1-f9f0e5331044`, secundário `ea4937da-dee9-4bc2-b2de-6a0a3214ac40`. Primeiro incidente `3e64bec2-c479-4a3d-8f52-0d83a5a1e760`; segundo `bb0478da-d7c0-4cb7-91b0-d6fd3d788478`. Dois CLOSED ao terminar, quatro intenções ACCEPTED e quatro mensagens Mailpit para portfolio@example.test.

Pendência preservada no restart: notificação `f8d91122-1d85-4b76-8027-af07c8129e5a`. Tentativa 1 FAILED terminou em `2026-10-21T16:38:58.888640Z`; tentativa 2 começou em `2026-10-21T16:39:29.009653Z`, **30,121013s depois**, e terminou ACCEPTED. O ID e o incidente permaneceram; nenhuma intenção adicional para a mesma transição foi criada.

Concorrência real: TIMEOUT principal entre `16:39:31.675751Z` e `16:39:33.676545Z`; SUCCESS secundário entre `16:39:32.198425Z` e `16:39:32.201632Z`, inteiramente dentro da tentativa lenta. Pausa preservou DOWN/incidente e não criou check nos 11s observados. Scheduler desativado levou a ACTIVE/DOWN/STALE com o mesmo incidente. Recuperação final ocorreu após reativação e dois sucessos.

Capturas de queda, pausa e dados antigos foram inspecionadas visualmente: labels, estado confirmado e IDs correspondem aos snapshots. A captura de pendência vem dos detalhes reais, incluindo histórico de notificações, sem interceptações de navegador. A inspeção automatizada/técnica não substitui aprovação humana de design.

## Limites da evidência

SMTP local com destinatários fictícios e sem relay. ACCEPTED significa aceitação SMTP. O reinício demonstrado é com PENDING após falha conhecida; confirmação incerta durante SENDING continua coberta nas suítes Java. CI foi atualizada para executar os novos gates; execução remota não observada. A medição de capacidade é a evidência MON-11 reaproveitada, não novo benchmark.

## Fechamento independente

Após a entrega e a validação sem commits, o usuário autorizou o commit de fechamento da MON-12. As referências abaixo à ausência de commits descrevem o momento da validação.

[Parecer](validation.md): **PASS 8/8**, sem gaps. Verifier repetiu Quick (2/2) e mediu o atraso padrão real: slow 15005,627ms, healthy 4,430ms, error 2,871ms e secundário 3,431ms. Três mutações em cópias isoladas foram mortas pelas assertions de status 503, atraso e identidade do seed; zero sobreviventes. Porcelain e 247 hashes permaneceram iguais, e as cópias mutantes foram descartadas.

Os gates longos foram reutilizados com justificativa explícita: 125 arquivos de fonte/build/testes/Compose/scripts conferidos por hash contra a cópia limpa, sem diferenças; nenhum teste existente alterado. Dez snapshots auditados, ordem DOWN→RECOVERY em ambos incidentes, distribuição de capacidade e links locais conferidos. Quick independente registrado no parecer; logs adicionais em `.validation/mon12-verifier-default.log`, `.validation/mon12-verifier-sensor/` e auditoria `.validation/mon12-verifier-audit.py`.

`validate_spec.py`, `validate_tasks.py` e `validate_state.py` aprovados após o parecer; `git diff --check` sem erros. As lições históricas dos dois gates iniciais foram registradas via script da skill como **L-007 e L-008**, candidatas; ledger e playbook atualizados automaticamente. Backlog, spec, tasks e handoff refletem conclusão. HEAD continua `f7a6287`; nenhum commit criado.
