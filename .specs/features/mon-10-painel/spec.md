# MON-10: painel administrativo

**Data:** 2026-10-19. **Status:** execução local autorizada no pedido.
**Origem:** MON-10-AC01 até MON-10-AC06; AD-001 a AD-011.

## Problem Statement

O operador precisa administrar monitores e explicar seu funcionamento no navegador com dados reais. A interface deve preservar a distinção entre ciclo de vida, estado confirmado, evidência e incidente, sem reproduzir regras de negócio em JavaScript.

## Goals

- Administrar configuração e pausa/retomada com Basic e CSRF existentes.
- Consultar estado, resultados, incidentes e notificações com filtros e paginação reais.
- Operar em desktop de 1440×900 e tela pequena de 390×844, com testes Docker e evidências.

## Out of Scope

| Capacidade | Motivo |
| --- | --- |
| Novo login, logout Basic, exclusão, encerramento manual e dashboard público | Contratos existentes não oferecem essas funções |
| Framework frontend, CDN, novo servidor runtime e migrations | HTML/CSS/JS nativos servidos pelo Spring Boot |
| Uptime temporal, novos indicadores, retenção, publicação remota | Contrato MON-09 e entregas posteriores |

## Assumptions & Open Questions

| Assumption / decision | Chosen default | Rationale | Confirmed? |
| --- | --- | --- | --- |
| Layout sem demo disponível | Lista em cartões, detalhes em seções, formulário na própria página | Referência opcional ausente; permite leitura em telas pequenas | Premissa técnica |
| Navegação | / com hashes para lista, novo, edição e detalhe | Links recarregáveis sem criar rotas de backend | Premissa técnica |
| Atualização | Botão Atualizar; sem polling automático | Evitar sobrescrever formulários e consultas concorrentes | Premissa técnica |
| Período | Datas ISO-8601 com offset opcionais; defaults do backend, janela [from,to) | Não inventar calendário nem fuso; horários apresentados no fuso do navegador | Contrato MON-09 |
| Confirmação de escrita | Apenas resposta bem-sucedida altera a tela; falha de transporte pede conferir estado antes de repetir | Resposta perdida pode ocorrer após commit; não repetir POST automaticamente | Premissa técnica |
| Testes | Playwright Chromium em Docker com backend/DB próprios; interceptação somente para falhas e cenários de apresentação controlados | Navegador real, sem tocar banco do operador; servidor HTTP controlado produz incidentes reais | Premissa técnica |

**Open questions:** none; escolhas visuais e técnicas resolvidas acima, mantendo o escopo solicitado.

## User Stories

### P1: Operar e acompanhar monitores

**User Story:** Como operador, quero cadastrar e acompanhar os monitores sem chamadas manuais.

**Acceptance Criteria**:

1. WHEN a lista for aberta THEN o painel SHALL mostrar nome, label recebido do summary, estado confirmado separado, última observação e incidente aberto; labels PAUSED, SEM_DADOS, SEM_DADOS_RECENTES, CONFIRMANDO_QUEDA, CONFIRMANDO_RECUPERACAO, UP e DOWN terão texto compreensível (MON-10-AC01).
2. WHEN o formulário for enviado THEN o painel SHALL enviar os oito campos MON-04 com tipos corretos, cadastrar por POST ou editar por PUT, mostrar erros junto aos respectivos campos e preservar todos os valores em 400, 409, 403 ou falha de rede (MON-10-AC02).
3. WHEN pausar ou retomar receber sucesso do backend THEN o painel SHALL atualizar os dados confirmados, preservando a indicação do incidente aberto; durante envio a ação fica desabilitada e em falha não há atualização otimista nem repetição automática (MON-10-AC03).
4. WHEN os detalhes forem abertos THEN o painel SHALL mostrar summary, checks, incidents e notifications reais, com paginação independente, filtros outcome/status/type e from/to; apresentar quatro instantes do incidente, idade aberta ou duração final estimada fracionária, tentativas e códigos sanitizados de notificação (MON-10-AC04).
5. IF houver carregamento, lista vazia, ausência de amostras, evidência antiga ou falha do backend THEN o painel SHALL mostrar mensagem textual específica, possibilidade de nova consulta e nenhum sucesso fictício; taxa e latência null aparecem como Sem dados, percentuais são por amostragem com quantidade de amostras, cobertura e lacunas separadas da duração dos incidentes (MON-10-AC05).
6. The painel SHALL manter Basic em página e assets, enviar cookie e token CSRF nas escritas, tratar 401 como autenticação necessária e 403 como acesso negado/CSRF expirado sem reenviar a escrita, usar labels e foco visível e permitir cadastro, edição, pausa/retomada e consultas em 1440×900 e 390×844 sem rolagem horizontal da página (MON-10-AC06).

**Independent Test:** Playwright opera o painel contra Spring Boot e PostgreSQL isolados; suíte Java existente valida contratos e segurança.

## Edge Cases

- 401/403 têm corpo vazio; não tentar decodificar JSON antes de tratar status. Credenciais não são armazenadas em JS/localStorage.
- Erro de summary de um cartão não deve fabricar estado nem ocultar os demais; aviso identifica que o estado não foi carregado.
- Erros de detalhe são independentes por seção; trocar rota descarta respostas atrasadas e evita substituir formulário.
- 409 OPEN_INCIDENT explica que nome/destinatário podem mudar, mas política não; 409 MONITOR_LIMIT informa limite.
- Página vazia com total maior que zero oferece voltar; limites de paginação vêm da resposta. Período dos incidentes filtra confirmação, não sobreposição; currentIncident continua visível fora da janela.
- Texto recebido do backend é inserido por textContent, incluindo nome/URL/destinatário; nenhum HTML remoto é executado.
- Duração 180.9 s permanece 180,9 s na apresentação pt-BR. ACCEPTED é Aceita pelo SMTP, sem prometer caixa postal.
- Falha de transporte após escrita informa resultado não confirmado e orienta conferir antes de repetir.

## Dimensões implícitas

Validação/bounds: MON-04 continua autoridade, erros ligados a campos. Falha parcial: seções independentes e mensagens, sem reenvio de escrita. Idempotência: GET pode repetir explicitamente, POST nunca automaticamente. Auth: Basic/CSRF global; throttling novo N/A ao operador local. Concorrência: bloqueio de envio e descarte de renderizações antigas. Ciclo/expiração: CSRF expirado requer nova tentativa consciente e nova leitura de token, sem persistir credenciais. Observabilidade: mensagens fixas e evidências de teste, sem expor erros brutos de infraestrutura. Dependências: backend real, falha de consulta não vira queda. Transições: somente backend confirma. Pagamentos N/A, sem cobrança.

## Requirement Traceability

| Requirement ID | Origem | Tasks | Testes | Status |
| --- | --- | --- | --- | --- |
| PANEL-01 | MON-10-AC01 | T2 | list.spec.js | Verified |
| PANEL-02 | MON-10-AC02 | T3 | form.spec.js | Verified |
| PANEL-03 | MON-10-AC03 | T4 | lifecycle.spec.js | Verified |
| PANEL-04 | MON-10-AC04 | T5 | details.spec.js | Verified |
| PANEL-05 | MON-10-AC05 | T1,T2,T3,T5 | security.spec.js, list.spec.js, form.spec.js, details.spec.js | Verified |
| PANEL-06 | MON-10-AC06 | T1,T6,T7 | security.spec.js, responsive.spec.js | Verified |

## Success Criteria

Seis ACs rastreados com assertions file:line, suíte Java sem regressões, fluxos Playwright e screenshots desktop/mobile aprovados, build/smoke Docker e Verifier independente com sensor em scratch. Execução remota não é afirmada.
