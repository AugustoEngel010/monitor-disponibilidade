# MON-12: demonstração reproduzível

## Problem Statement

O avaliador precisa executar o MVP e observar seus contratos de robustez com HTTP e SMTP locais reais. A documentação atual não fornece alvo controlável nem roteiro integrado de reinício e retomada.

## Goals

Entregar Compose completo, seed idempotente, roteiro verificável, imagens reais e documentação rastreável. Preservar AD-001 a AD-012 e não criar commits, conforme instrução expressa.

**Status:** Verified em 21/10/2026; [parecer independente](validation.md) PASS 8/8 e [evidências](evidence.md). Nenhum commit criado.

## Out of Scope

| Capacidade | Motivo |
| --- | --- |
| Publicação, hospedagem e CI remota | Entrega local |
| Novas regras de domínio e migrações | Contratos MON-01 a MON-11 preservados |
| Exactly-once SMTP e SLA | Limitações existentes |

## Assumptions & Open Questions

| Assumption / decision | Chosen default | Rationale | Confirmed? |
| --- | --- | --- | --- |
| Alvos | Dois recursos no serviço demo, modos em memória | Controle independente sem persistência artificial |
| Origem interna | http://demo:8080 explicitamente em .env.example; vazia no Compose sem configuração | Preservar bloqueio privado por padrão |
| Seed | API Basic/CSRF, identidade por URL, execução serial | Nomes duplicados são permitidos; não alterar domínio |
| Reinício | Parar SMTP, observar PENDING após falha, reiniciar app, restaurar SMTP | Provar outbox real sem alterar relógio ou banco |
| Ausência de dados | Desabilitar scheduler por configuração, esperar além de 2I+T | Demonstrar lacuna real sem fabricar resultado |
| Ferramentas | Docker e PowerShell no roteiro de orquestração | Fluxo existente do projeto; Java/Node nos contêineres |
| Aprovação e commits | Execução autorizada pelo pedido; nenhum commit | Instrução atual prevalece sobre cerimônia da skill |

**Open questions:** none; escolhas operacionais registradas como premissas.

## User Stories

### P1: reproduzir e avaliar o monitor

**User Story:** Como avaliador, quero iniciar os serviços e observar transições persistidas e retomada com destinatários fictícios.

**Acceptance Criteria:**

1. WHEN o avaliador configura as variáveis documentadas e executa `docker compose up --build` em uma cópia sem artefatos gerados THEN o ambiente SHALL iniciar app, db, smtp e demo e concluir seed de dois monitores sem duplicação em uma segunda execução (MON-12-AC01; DEMO-01).
2. WHEN o operador seleciona healthy, error ou slow para um alvo THEN a API demo SHALL responder respectivamente 200 imediato, 503 imediato ou 200 após 15000 ms, sem modificar o outro alvo (MON-12-AC02; DEMO-02).
3. WHEN o roteiro alterna healthy, error e healthy com I=10, T=2, F=3, R=2 THEN o monitor SHALL mostrar confirmação de queda, um único incidente aberto, uma intenção DOWN aceita no SMTP, confirmação de recuperação e o mesmo incidente CLOSED com uma intenção RECOVERY aceita (MON-12-AC03; DEMO-03).
4. WHEN app reinicia com incidente aberto e intenção DOWN PENDING após falha SMTP THEN o ambiente SHALL preservar IDs, histórico anterior e incidente, e processar a mesma intenção após restaurar SMTP respeitando até cinco tentativas e ordem DOWN antes de RECOVERY (MON-12-AC04; DEMO-04).
5. WHILE um alvo está lento e o segundo saudável o roteiro SHALL comprovar TIMEOUT no primeiro e novo SUCCESS no segundo, além de registrar PAUSED, DOWN e SEM_DADOS_RECENTES como dimensões distintas sem encerrar incidente por pausa ou lacuna (MON-12-AC05; DEMO-05).
6. The README SHALL documentar problema, funcionalidades, stack, arquitetura, pré-requisitos, variáveis, execução, painel, testes e diagnóstico com imagens capturadas do fluxo real (MON-12-AC06; DEMO-06).
7. The documentação SHALL relacionar requisitos aos testes, specs, decisões, limitações e medições MON-11: 100 monitores, C=4, 285 verificações em 60,070333095s e atraso máximo 20,634817s, sem prometer SLA (MON-12-AC07; DEMO-07).
8. WHEN os gates e a revisão independente terminam THEN a entrega SHALL registrar comandos, resultados, evidências por AC e sensor de discriminação, com estado final fiel ao observado (MON-12-AC08; DEMO-08).

## Edge Cases

- IF modo ou recurso é inválido THEN demo SHALL retornar 400 ou 404 sem mudar estado.
- IF seed encontra configuração incompatível ou identidade duplicada THEN seed SHALL falhar explicitamente sem sobrescrever configuração existente.
- IF dependência ou assertion falha THEN roteiro SHALL terminar com código diferente de zero e manter evidências disponíveis.
- WHEN Compose é parado sem remover volumes THEN PostgreSQL SHALL preservar os registros.

## Dimensões implícitas

Validação: modos e recursos limitados. Falhas: timeouts finitos e exit não zero. Idempotência: seed serial por URL e IDs persistidos na outbox. Segurança: Basic/CSRF sem isenções; demo somente loopback no host, origem exata no executor. Concorrência: dois alvos independentes, C=4 preservado. Ciclo de dados: volume PostgreSQL, evidência JSON e imagens. Observabilidade: consultas e Mailpit reais. Dependências: indisponibilidade SMTP controlada. Transições: regras MON-07/08 preservadas. Rate limit adicional N/A porque API demo é local e não é administração do monitor.

## Requirement Traceability

| Requirement ID | Origem | Tasks | Status |
| --- | --- | --- | --- |
| DEMO-01 | MON-12-AC01 | T1,T2,T3 | Verified |
| DEMO-02 | MON-12-AC02 | T1 | Verified |
| DEMO-03 | MON-12-AC03 | T4 | Verified |
| DEMO-04 | MON-12-AC04 | T4 | Verified |
| DEMO-05 | MON-12-AC05 | T4 | Verified |
| DEMO-06 | MON-12-AC06 | T5,T6 | Verified |
| DEMO-07 | MON-12-AC07 | T6 | Verified |
| DEMO-08 | MON-12-AC08 | T7 | Verified |

## Success Criteria

Oito ACs com evidência local, suítes sem regressão e gates da skill aprovados. Nenhum commit.
