# MON-03: proteger o acesso administrativo

**Data:** 2026-10-13. **Status:** Verified. Execução local autorizada pelo pedido; [verificação independente](validation.md) PASS 6/6, 48 testes e 8 sensores mortos.
**Origem:** MON-03-AC01 a MON-03-AC06; PROD-01; AD-001/004.
**Convenção:** SHALL significa DEVE; AUTH-NN são IDs internos da skill.

## Problem Statement

A base MON-02 não autentica requisições. O operador precisa proteger consultas e configurações administrativas, inclusive o futuro painel, sem credenciais versionadas ou inicialização aberta.

## Goals

- Autenticar um operador e fechar por padrão todas as rotas administrativas.
- Exigir configuração externa e verificar CSRF pelo fluxo Docker existente.

## Out of Scope

| Capacidade | Motivo |
| --- | --- |
| CRUD e painel completo | MON-04 e MON-10; somente rotas de prova em testes |
| Múltiplos usuários, JWT, recuperação de senha, throttling | MVP local de operador único |
| Publicação e execução remota de CI | Pedido autoriza execução e commits locais |

## Assumptions & Open Questions

| Assumption / decision | Chosen default | Rationale | Confirmed? |
| --- | --- | --- | --- |
| Autenticação | HTTP Basic, um operador, ADMIN_USERNAME/ADMIN_PASSWORD obrigatórios | Proposta inicial do contrato e pedido atual | Escopo solicitado |
| Rotas públicas | Apenas GET/HEAD de /actuator/health, /actuator/health/liveness, /actuator/health/readiness | Preservar readiness 200/503 da MON-02 sem fornecer senha ao probe | Default técnico explicitado |
| Demais rotas | Autenticação por padrão, inclusive /, /admin/**, /api/**, assets e outros actuators | Não depender da criação do painel para proteger sua fronteira | Default técnico |
| CSRF | Token em sessão, GET /api/admin/csrf autenticado, header X-CSRF-TOKEN ou parâmetro _csrf | Mecanismo nativo Spring Security com token mascarado | Default técnico |
| Sessão | Basic a cada requisição; cookie HttpOnly/SameSite=Lax só para CSRF, TTL 30 min | Rotação da senha não mantém autorização de sessões antigas | Default técnico |
| Configuração | Usuário não branco, sem colon/controle, até 128 caracteres; senha não branca até 72 bytes UTF-8; placeholders rejeitados | Basic delimita usuário por colon e BCrypt limita entrada; exemplos não podem inicializar | Default técnico |
| Transporte | Porta 127.0.0.1 local; HTTPS obrigatório para expor fora do host | Basic não cifra a credencial | Contrato local existente |
| Compose | Variáveis vazias quando ausentes, falha na aplicação; tests independente de credenciais do operador | Build/tests continuam possíveis sem .env; smoke gera credenciais descartáveis | Default técnico |

**Open questions:** none; defaults técnicos registrados, sem alterar premissas de negócio pendentes da MON-01.

## User Stories

### P1: Operar com acesso restrito

**User Story:** Como operador, quero autenticar as consultas e ações administrativas.

**Acceptance Criteria**:
1. IF uma requisição administrativa não tiver credencial válida, THEN o sistema SHALL responder HTTP 401 com WWW-Authenticate Basic e corpo vazio, inclusive métodos de escrita sem CSRF (AUTH-01; MON-03-AC01).
2. WHEN uma credencial válida consultar /actuator/info, THEN o sistema SHALL responder HTTP 200; rotas inexistentes SHALL continuar HTTP 404 depois da autenticação (AUTH-02; MON-03-AC02).
3. The sistema SHALL exigir autenticação em todas as rotas salvo a lista pública explícita, incluindo /, /admin/** e /api/**; rotas de prova de painel/API SHALL retornar 200 com Basic válido e 401 sem ele (AUTH-03; MON-03-AC03).
4. IF ADMIN_USERNAME ou ADMIN_PASSWORD estiver ausente, vazio, branco, malformado ou for placeholder do exemplo, THEN o sistema SHALL impedir startup com erro genérico sem incluir valores (AUTH-04; MON-03-AC04).
5. The sistema SHALL omitir usuário, senha e Authorization das respostas e dos logs da aplicação em nível padrão, não persistir credenciais, ignorar .env e versionar somente placeholders de credenciais administrativas (AUTH-05; MON-03-AC05).
6. IF uma escrita POST/PUT/PATCH/DELETE autenticada não fornecer token CSRF válido da mesma sessão, THEN o sistema SHALL responder HTTP 403 com corpo vazio e não executar a ação; token válido com cookie da mesma sessão SHALL permitir HTTP 200 nas ações de prova (AUTH-06; MON-03-AC06).

**Independent Test:** HTTP real em porta aleatória com PostgreSQL Testcontainers; rotas de prova apenas em configuração de testes.

## Edge Cases

- IF Basic contiver senha errada, usuário errado, Base64 inválido ou esquema Bearer, THEN o sistema SHALL retornar 401 (AUTH-01).
- WHEN health/readiness sem autenticação for consultado, THEN o sistema SHALL manter 200/UP com banco e 503/DOWN sem banco, sem detalhes, sem sessão (AUTH-03).
- IF um cookie CSRF for enviado sem Basic, THEN o sistema SHALL responder 401 mesmo com token válido (AUTH-01).
- IF um token de outra sessão ou token malformado for enviado, THEN o sistema SHALL rejeitar a ação com 403 (AUTH-06).
- IF um formulário de outra origem autenticado não tiver token, THEN o sistema SHALL retornar 403 e não executar ação (AUTH-06).
- WHEN GET /api/admin/csrf autenticado ocorrer, THEN o sistema SHALL retornar 200 com token não vazio, headerName=X-CSRF-TOKEN, parameterName=_csrf e cookie HttpOnly/SameSite=Lax; CORS SHALL não conceder leitura a outra origem (AUTH-06).
- WHEN o operador trocar a senha e recriar o serviço app, THEN a senha anterior SHALL retornar 401, inclusive com cookie anterior, e a nova SHALL retornar 200 (AUTH-02/05).

## Dimensões implícitas

| Dimensão | Cobertura |
| --- | --- |
| Validação e limites | AUTH-04; limites técnicos Basic/BCrypt |
| Falhas e dependência externa | Startup fechado; prontidão 503 preservada |
| Idempotência | N/A: nenhum CRUD; rotação por recriação |
| Autorização e rate limits | AUTH-01/03; throttling N/A no MVP local |
| Concorrência e ordem | Autenticação antes de CSRF; sessão vincula token |
| Ciclo de dados | Token em memória, 30 min; senha codificada em memória, sem tabela |
| Observabilidade | AUTH-05; erros sem valores; probes sem detalhes |
| Integridade de estado | Escritas bloqueadas sem efeito; nenhum domínio antecipado |

## Requirement Traceability

| Requirement ID | Origem | Task | Teste / evidência | Status |
| --- | --- | --- | --- | --- |
| AUTH-01 | MON-03-AC01 | T2, T3 | AdministrativeSecurityIT; evidence.md T2/T3 | Verified |
| AUTH-02 | MON-03-AC02 | T2, T4 | AdministrativeSecurityIT; smoke rotação; evidence.md T4 | Verified |
| AUTH-03 | MON-03-AC03 | T2, T4 | Rotas de prova; ApplicationIT; smoke; evidence.md T4 | Verified |
| AUTH-04 | MON-03-AC04 | T2, T4 | AdministrativeConfigurationIT; smoke sem configuração | Verified |
| AUTH-05 | MON-03-AC05 | T2, T4, T5 | Captura logs/respostas; revisão Git e exemplos; evidence.md | Verified |
| AUTH-06 | MON-03-AC06 | T3 | AdministrativeSecurityIT, efeitos da ação; evidence.md T3 | Verified |

## Success Criteria

Seis ACs validados com evidências file:line, testes em contêiner sem skips, qualidade/build/smoke aprovados e Verifier independente com sensor de discriminação.
