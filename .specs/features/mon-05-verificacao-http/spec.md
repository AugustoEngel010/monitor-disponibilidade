# MON-05: executar e registrar uma verificação HTTP

**Data:** 2026-10-13. **Status:** Verified; [verificação independente](validation.md) PASS 8/8, 264 testes e seis mutações mortas. Execução local autorizada pelo pedido.
**Origem:** MON-05-AC01 a MON-05-AC08; PROD-21/22/23/24/29; AD-001 a AD-006.

## Problem Statement

A administração persiste configurações, mas ainda não produz observações HTTP. Uma tentativa deve terminar em tempo limitado e produzir evidência persistente sem acessar destinos proibidos ou confundir erros internos com falhas externas.

## Goals

- Executar GET e registrar metadados classificáveis até os cabeçalhos.
- Proteger resolução/conexão e preservar TLS, Basic/CSRF e o fluxo Docker.
- Deduplicar resultados por UUID de execução com PostgreSQL real isolado.

## Out of Scope

| Capacidade | Destino |
| --- | --- |
| Agendamento, limite de concorrência por monitor, recuperação após crash | MON-06 |
| Aplicar resultado ao estado, incidente e intenção atomicamente | MON-07/08; AD-002 permanece vigente |
| Endpoint de execução, painel, consulta de histórico, retenção | MON-09/10/11; executor é contrato Java interno |
| Deploy, push, SMTP e serviço demo completo | Fora desta entrega |

## Assumptions & Open Questions

| Assumption / decision | Chosen default | Rationale | Confirmed? |
| --- | --- | --- | --- |
| Entrada | Snapshot Monitor e UUID executionId | Contém configuração, identidade, versão e geração já persistidas |
| Timeout | T segundos desde entrada até cabeçalhos, incluindo DNS; tolerância de teste 500 ms para cleanup/escalonamento | Contrato MON-01; persistência fica fora do orçamento HTTP |
| Cancelamento | Interrupção da thread chamadora; resultado CANCELLED, flag restaurada após persistência | MON-06 poderá solicitar cancelamento sem nova API HTTP |
| Erros | Mensagem fixa por classificação, nunca exception.getMessage ou URL | Sanitização por construção |
| Demo | MONITOR_DEMO_ORIGIN vazia por padrão; origem HTTP/HTTPS exata, sem path além de /, query, fragment ou userinfo | Exceção explícita para host/esquema/porta, nunca sub-rede |
| DNS | Validar todos os A/AAAA; rejeitar resposta mista; conectar ao primeiro IP do snapshot sem fallback | Fecha rebinding e impede fallback de mascarar erro TLS; uma tentativa sem retry |
| IPv6 | Permitir somente unicast global 2000::/3, excluindo faixas especiais/documentação/transição | Política conservadora; mapeamento IPv4 é tratado como IPv4 |
| Duplicação | Primeiro registro vence; retorno do registro original; conflito de identidade monitor/versão/geração rejeitado | Não reescrever evidência nem associar UUID a outro monitor |
| Falha no banco | Propagar erro de infraestrutura; permitir repetir save do resultado | Banco indisponível não vira falha HTTP nem registro fictício |
| Cliente | Apache HttpClient classic, sem proxy, retry, redirect, cookies ou autenticação automática | DNS injetável, cancelamento duro e TLS padrão |

**Open questions:** none; escolhas técnicas explícitas dentro do pedido. Não ratifica contratos de outras entregas.

## User Stories

### P1: Produzir evidência HTTP segura

**User Story:** Como operador, quero evidências de uma tentativa limitada, classificável e persistente.

**Acceptance Criteria**:
1. WHEN os cabeçalhos de GET forem recebidos THEN o executor SHALL retornar SUCCESS se código = esperado, senão HTTP_FAILURE, preservando o status recebido (HTTP-01; MON-05-AC01).
2. IF a tentativa ultrapassar T segundos THEN o executor SHALL retornar TIMEOUT e cancelar a operação, liberando conexão também em sucesso, falha e cancelamento (HTTP-02; MON-05-AC02).
3. WHEN a tentativa terminar THEN o resultado SHALL conter monitorId, executionId, configurationVersion, executionGeneration, startedAt/finishedAt UTC e durationNanos monotônico não negativo, medido até os cabeçalhos ou erro sem aguardar corpo (HTTP-03; MON-05-AC03).
4. IF ocorrer erro de DNS, conexão, TLS, interno, cancelamento ou bloqueio THEN o executor SHALL classificar respectivamente DNS_FAILURE, CONNECTION_FAILURE, TLS_FAILURE, INTERNAL_ERROR, CANCELLED ou DESTINATION_BLOCKED; somente SUCCESS/HTTP_FAILURE/TIMEOUT/DNS_FAILURE/CONNECTION_FAILURE/TLS_FAILURE são evidências externas (HTTP-04; MON-05-AC07).
5. The executor SHALL rejeitar URLs inválidas e destinos não públicos em validação/resolução/conexão, sem conectar ao endereço bloqueado, mantendo redirects desabilitados e validação padrão de cadeia TLS e hostname (HTTP-05; MON-05-AC05).
6. WHERE MONITOR_DEMO_ORIGIN estiver explicitamente configurada o executor SHALL permitir endereço privado somente para a origem exata autorizada e endereço resolvido para esse host, sem liberar outra origem ou a sub-rede (HTTP-06; MON-05-AC06).
7. WHEN o resultado for registrado THEN a persistência SHALL armazenar somente os metadados definidos, status HTTP opcional e erro fixo sanitizado, sem corpo, headers, URL ou mensagem bruta (HTTP-07; MON-05-AC04).
8. WHEN um UUID de execução for registrado repetidamente ou concorrentemente THEN a persistência SHALL manter uma única linha e retornar o primeiro registro sem alterar seus metadados (HTTP-08; MON-05-AC08).

**Independent Test:** Servidor HTTP/TLS controlado e PostgreSQL Testcontainers executados pelo serviço tests.

## Edge Cases

- 200 esperado/recebido; 503 inesperado; 301 esperado e inesperado sem seguir Location; corpo interminável após cabeçalhos.
- DNS lento, resposta lenta, TLS lento; interrupção antes/durante DNS e conexão; conexão fechada sem cabeçalhos; falha de resolução e certificado não confiável/hostname inválido.
- IPv4/IPv6 loopback, privados, link-local, multicast, unspecified, metadados, CGNAT, reservados; DNS misto público/privado; mudança de DNS depois do snapshot.
- Demo ausente, origem malformada, outro host/porta/esquema, literal IP bloqueado; exceção válida somente no alvo.
- Recursos fechados após sucesso, status inesperado, timeout e cancelamento, observados pelo peer; nenhuma segunda tentativa automática.
- Repetição sequencial/concorrente com metadados diferentes, conflito de identidade, FK inválida, reinício, falha de banco; snapshot antigo preservado sem alterar estado administrativo.

## Dimensões implícitas

Validação/rede: HTTP-05/06. Falhas/dependências externas: HTTP-02/04 e erro de banco propagado. Idempotência/ordem: HTTP-08, primeiro insert vence. Auth: nenhuma rota nova, regressão Basic/CSRF existente. Concorrência HTTP e recuperação: MON-06, fora do escopo. Ciclo/retention: MON-11, histórico preservado. Observabilidade: IDs e mensagens fixas, sem logs de exceções. Integridade: snapshot versionado e FK, sem atualizar estado. Rate limiting/pagamentos N/A no contrato Java interno.

## Requirement Traceability

| Requirement ID | Origem | Task | Teste | Status |
| --- | --- | --- | --- | --- |
| HTTP-01 | MON-05-AC01 | T2,T4 | HttpCheckExecutorIT, CheckServiceIT | Verified |
| HTTP-02 | MON-05-AC02 | T2 | HttpCheckExecutorIT | Verified |
| HTTP-03 | MON-05-AC03 | T2,T3,T4 | HttpCheckExecutorIT, CheckRepositoryIT | Verified |
| HTTP-04 | MON-05-AC07 | T2 | HttpCheckExecutorIT | Verified |
| HTTP-05 | MON-05-AC05 | T1,T2 | DestinationPolicyTest, HttpCheckExecutorIT | Verified |
| HTTP-06 | MON-05-AC06 | T1,T4 | DestinationPolicyTest, CheckServiceIT | Verified |
| HTTP-07 | MON-05-AC04 | T2,T3,T4 | CheckRepositoryIT, CheckServiceIT | Verified |
| HTTP-08 | MON-05-AC08 | T3,T4 | CheckRepositoryIT (evidence.md T3), CheckServiceIT | Verified |

## Success Criteria

Oito ACs com assertions file:line, gates Docker sem skips, runtime/smoke e Verifier independente com sensor. Registrar comandos e contagens reais em evidence.md e validation.md.
