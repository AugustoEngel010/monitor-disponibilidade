# MON-04: ciclo de vida dos monitores

**Data:** 2026-10-13. **Status:** execução local autorizada pelo pedido. Origem: MON-04-AC01 a MON-04-AC07, PROD-02 a PROD-10, AD-001/003/004/005.

## Problem Statement

O operador autenticado precisa administrar monitores persistentes sem perder evidências ou transformar pausa em recuperação. A base Docker e a fronteira Basic/CSRF já existem.

## Goals

- Cadastrar, consultar, editar, pausar, retomar e listar monitores por HTTP.
- Validar configuração e preservar identidade, versões e estado persistente.
- Verificar regras unitariamente e contratos HTTP/PostgreSQL em contêineres.

## Out of Scope

| Capacidade | Destino |
| --- | --- |
| Executar/cancelar requisições HTTP e aplicar resultados tardios | MON-05/06; esta entrega persiste geração de execução e elegibilidade |
| Criar/encerrar incidentes, notificações e histórico de verificações | MON-05/07/08; fixtures provam que administração preserva referências existentes |
| Exclusão, painel, filtros/ordenação configuráveis, patch parcial | Evoluções ou MON-10; edição usa PUT completo |
| Push, deploy, CI remota | Fora da autorização local |

## Assumptions & Open Questions

| Assumption / decision | Chosen default | Rationale | Confirmed? |
| --- | --- | --- | --- |
| Regras MON-01 | Limites, 100 monitores, incidente preservado e 409 para mudança de política com incidente | Pedido exige seguir as regras documentadas | Sim, pedido atual |
| HTTP | /api/admin/monitors, UUID; POST cadastro, GET leitura/lista, PUT edição, POST /pause e /resume | API pequena, idempotência de estado explícita | Default técnico |
| Paginação | page=0, size=20; page 0..2147483647, size 1..100; ordem createdAt ASC, id ASC | Limita resposta e desempata horários iguais | Default técnico |
| URL | Trim externo; preservar restante exatamente, sem canonicalização; URI absoluta, host ASCII DNS/IPv4/IPv6, HTTP/HTTPS case insensitive | Evita equivalência oculta em versões; sem consulta de rede | Default técnico |
| E-mail | Trim; ASCII dot-atom local (até 64), domínio DNS com ponto e labels 1..63; total até 254; sem quoted local/display name/lista | Gramática determinística, um destinatário; a existência não é validada | Default técnico |
| Ausência de números | Cadastro/PUT aplicam defaults I=60,T=5,status=200,F=3,R=2; nome/URL/e-mail obrigatórios | Defaults MON-01; PUT define toda configuração, sem patch | Default técnico |
| Erros | {code,errors}, errors mapa campo→mensagem fixa; sem valores enviados; 409 code MONITOR_LIMIT ou OPEN_INCIDENT | Erros verificáveis sem ecoar segredos | Default técnico |
| Evidência suspensa | Pausa/retomada invalidam observação corrente; lastObservedAt histórico preservado, evidence=NEVER_OBSERVED até nova amostra | Frescor exige evidência do período ativo corrente | Default técnico |
| Integração futura | openIncidentId persistente, contadores e horários provisórios; configurationVersion e executionGeneration independentes | Não inventar incidente fictício; permitir coordenação transacional futura | Default técnico |

**Open questions:** none; defaults acima registrados para detalhes deixados à MON-04. Nenhuma ratificação global das demais premissas MON-01.

## Contrato de configuração

JSON: name, url, intervalSeconds, timeoutSeconds, expectedStatus, failureThreshold, recoveryThreshold, alertRecipient.
Nome trim 1..100; URL trim até 2048, host obrigatório, sem userinfo/query/fragment (inclusive vazios), porta explícita 1..65535. I inteiro 10..3600; T inteiro 1..30 e T<I; status inteiro 200..599; F/R inteiros 1..10; e-mail conforme gramática acima. Nomes duplicados permitidos. Decimal, string numérica, boolean ou inteiro fora de representação em campo inteiro: 400 no campo. JSON inválido/nulo/estrutura incompatível: 400 em body. Campos desconhecidos: 400 no campo. UUID malformado: 400 em id; UUID ausente na base: 404.

Resposta de monitor contém configuração, id, lifecycle (ACTIVE/PAUSED), confirmedState (UNKNOWN/UP/DOWN), evidence (NEVER_OBSERVED/FRESH/STALE), recentObservation, lastObservedAt, openIncidentId, failureCount, recoveryCount, configurationVersion, executionGeneration, nextCheckAt, createdAt, updatedAt. Instantes UTC. recentObservation=true somente FRESH com idade 0..2I+T inclusiva; observação futura é STALE. evidence=NEVER_OBSERVED se nenhuma observação corrente, inclusive após pausa/retomada/edição de política. Lista: {items,page,size,totalElements,totalPages}; página além do fim retorna items=[] e totais reais.

## User Stories

### P1: Administrar monitores

**User Story:** Como operador, quero configurar monitores mantendo seu histórico.

**Acceptance Criteria**:
1. WHEN um POST válido ocorrer THEN o sistema SHALL persistir um monitor ACTIVE/UNKNOWN/NEVER_OBSERVED, contadores zero, versões 1, nextCheckAt imediato e retornar 201, Location e UUID (LIFE-01; MON-04-AC01).
2. IF a configuração violar os limites THEN o sistema SHALL retornar 400 com todos os erros de configuração por campo sem persistir alteração (LIFE-02; MON-04-AC02).
3. IF a URL contiver userinfo ou esquema fora de HTTP/HTTPS THEN o sistema SHALL rejeitar com 400 e erro url, sem ecoar o valor (LIFE-03; MON-04-AC03).
4. IF um UUID válido não existir THEN o sistema SHALL retornar 404 em GET, PUT, pause e resume (LIFE-04; MON-04-AC04).
5. WHEN um PUT válido alterar nome/destinatário ou destino/política sem incidente THEN o sistema SHALL aplicar o contrato de edição abaixo preservando identidade e histórico (LIFE-05; MON-04-AC05).
6. WHEN pause ou resume ocorrer THEN o sistema SHALL aplicar o contrato idempotente abaixo sem fechar incidente ou fabricar recuperação (LIFE-06; MON-04-AC06).
7. WHEN uma lista paginada for consultada THEN o sistema SHALL retornar ordem createdAt/id ascendente, totais e dimensões de ciclo/estado/evidência conforme o contrato (LIFE-07; MON-04-AC07).
8. IF já houver 100 monitores incluindo pausados THEN o sistema SHALL rejeitar cadastro com 409 MONITOR_LIMIT (LIFE-08; PROD-04).
9. The sistema SHALL manter Basic obrigatório para todos os endpoints e CSRF obrigatório para todas as escritas (LIFE-09; AD-005).

**Independent Test:** HTTP real autenticado com token/sessão, banco PostgreSQL Testcontainers e relógio controlado para estado/frescor.

## Contrato de alterações

Nome/e-mail preservam versão, geração, estado, observação, contadores, horários provisórios e incidente. Repetir configuração idêntica preserva inclusive updatedAt. Mudança efetiva de URL/I/T/status/F/R sem incidente incrementa configurationVersion e executionGeneration, zera sequências/horários, torna UNKNOWN, remove observação corrente; conserva lastObservedAt histórico. nextCheckAt=agora se ativo, null se pausado. Com openIncidentId, retornar 409 OPEN_INCIDENT e nenhum campo muda, inclusive nome em PUT misto.

Pausa efetiva torna PAUSED, incrementa executionGeneration, zera sequências/horários, invalida observação corrente, nextCheckAt=null. Retomada efetiva torna ACTIVE, incrementa executionGeneration, zera sequências, invalida observação corrente, nextCheckAt=agora. Ambas preservam confirmedState, configurationVersion, openIncidentId e lastObservedAt. Repetir pause em PAUSED ou resume em ACTIVE não altera qualquer campo ou timestamp. Escritas por monitor usam lock de linha e transação; cadastro serializa contagem/inserção para garantir limite sob concorrência. Listagem usa snapshot único para itens e totais. Nenhum comando administrativo apaga histórico.

## Edge Cases

- Fronteiras de cada número, T=I, URL credenciada/query/fragment/porta inválida, e-mail múltiplo/display name, nome branco e comprimento máximo.
- Sem monitores; páginas adjacentes com createdAt igual; página fora do fim; parâmetros inválidos/decimais; monitor nunca observado.
- DOWN com incidente e recuperação parcial: pausa/retomada preservam DOWN/incidente e zeram recuperação; edição de nome aceita, cada campo de política rejeitado; PUT misto rollback.
- UP sem incidente: política reinicia UNKNOWN; mesma configuração não reinicia; edição pausada não agenda.
- Observação na fronteira L é FRESH; após L é STALE; pausa/retomada só voltam a FRESH com nova evidência.
- Banco reiniciado/contexto reaberto preserva configuração e estado; migração V1 existente recebe V2 uma vez.
- Sem Basic 401; escrita Basic sem CSRF 403 e sem efeito; sucesso requer cookie/token; limite concorrente não excede 100.

## Dimensões implícitas

| Dimensão | Resolução |
| --- | --- |
| Validação | LIFE-02/03; tabela e gramática |
| Falha parcial | Transação e rollback PUT misto; banco não vira erro de validação |
| Idempotência | LIFE-05/06, igualdade integral de estado |
| Auth/rate | LIFE-09; throttling N/A, MVP local operador único |
| Concorrência | Lock por monitor, cadastro serializado, snapshot de lista |
| Ciclo de dados | Histórico preservado; retenção MON-11 |
| Observabilidade | Erros fixos sem inputs; métricas MON-11 |
| Dependência externa | PostgreSQL real; HTTP/SMTP N/A nesta entrega |
| Integridade | Estado inicial e guards de incidente |

## Requirement Traceability

| Requirement ID | Origem | Task | Teste | Status |
| --- | --- | --- | --- | --- |
| LIFE-01 | MON-04-AC01 | T4,T5 | MonitorRepositoryIT, MonitorApiIT | Verified |
| LIFE-02 | MON-04-AC02 | T2,T5 | MonitorConfigurationTest, MonitorApiIT | Verified |
| LIFE-03 | MON-04-AC03 | T2,T5 | MonitorConfigurationTest, MonitorApiIT | Verified |
| LIFE-04 | MON-04-AC04 | T5,T6 | MonitorApiIT | Verified |
| LIFE-05 | MON-04-AC05 | T3,T4,T6 | MonitorTest, MonitorRepositoryIT, MonitorApiIT | Verified |
| LIFE-06 | MON-04-AC06 | T3,T4,T6 | MonitorTest, MonitorRepositoryIT, MonitorApiIT | Verified |
| LIFE-07 | MON-04-AC07 | T3,T7 | MonitorTest, MonitorApiIT | Verified |
| LIFE-08 | PROD-04 | T4,T5 | MonitorRepositoryIT, MonitorApiIT | Verified |
| LIFE-09 | AD-005 | T5,T6,T7 | MonitorApiIT, AdministrativeSecurityIT | Verified |

## Success Criteria

Sete ACs de origem cobertos, suíte Docker sem falhas/skips, qualidade/build e verificador independente com sensor; evidências file:line. Não declarar PASS com daemon indisponível.
