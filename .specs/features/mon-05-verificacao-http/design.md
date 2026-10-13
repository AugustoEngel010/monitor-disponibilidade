# MON-05: design

**Status:** execução local autorizada. **Spec:** [spec.md](spec.md).

## Architecture Overview

CheckService → HttpCheckExecutor → DestinationPolicy/Apache HttpClient → CheckRepository → PostgreSQL.
Monitor é snapshot recebido, sem buscar/modificar o estado durante a consulta. MON-06/07 coordenarão aplicação por lock/versão/geração. HTTP ocorre fora de transação; save é insert idempotente e leitura do registro vencedor.

## Code Reuse Analysis

Reutilizar Monitor, MonitorConfiguration.validated, NamedParameterJdbcTemplate, Flyway, Failsafe, Testcontainers e gates Docker. Não mudar a chain administrativa. Novo pacote monitoring depende somente dos contratos administrativos e da infraestrutura.

## Components

- DestinationPolicy valida URI, literal e todos os endereços resolvidos, com exceção por origem exata.
- HttpCheckExecutor recebe Monitor/UUID; Future com orçamento monotônico total; interrupção cancela request/conexão. DNS é resolvido uma vez antes do cliente; todos os IPs são validados e DnsResolver fornece somente o primeiro IP. Fallback de endereço não pode esconder falha TLS. TLS usa o host original e defaults de confiança/hostname.
- CheckResult registra dados imutáveis; Outcome declara evidência externa e mensagem fixa.
- CheckRepository.save retorna o primeiro registro; ON CONFLICT DO NOTHING com UUID PK e leitura posterior, rejeitando reutilização por outra identidade.
- CheckService coordena HTTP/save; interrupção é temporariamente limpa somente ao registrar CANCELLED e restaurada no finally.

## Data Models

V3: monitor.check_results, execution_id UUID PK, monitor_id FK, configuration_version/execution_generation, started_at/finished_at timestamptz, duration_nanos bigint >=0, outcome com CHECK, http_status nullable 100..599 e error_message fixo por outcome. Índice monitor_id/finished_at/execution_id. Sem corpo, headers ou URL. PRIMEIRO insert vence; não oferece exactly-once de envio HTTP.

## Error Handling Strategy

UnknownHostException → DNS_FAILURE; SSLException/CertificateException → TLS_FAILURE; timeout → TIMEOUT; IOException → CONNECTION_FAILURE; DestinationBlockedException → DESTINATION_BLOCKED; interrupção → CANCELLED; demais exceções → INTERNAL_ERROR. Inspecionar causas; nunca persistir texto da exceção. Erros no save propagam.

## Risks & Concerns

| Concern | Local | Mitigação |
| --- | --- | --- |
| Rebinding/proxy/redirect contornam validação | Novo transporte | DNS snapshot, validação de literais antes do cliente, sem proxy/redirect/retry |
| close de resposta pode drenar corpo | Novo transporte | Capturar duração/status nos cabeçalhos e fechar cliente IMMEDIATE antes de response.close |
| DNS nativo pode não responder à interrupção | InetAddress | Worker daemon e checagem de deadline/interrupção após DNS; caller termina no timeout; nenhum socket após cancelamento |
| Migração V3 quebra expectativas de V1/V2 | Testes/smoke existentes | Expandir assertions para histórico completo 1,2,3; incluir tabela filha em TRUNCATE |
| CANCELLED perde flag ao persistir | Serviço | Restaurar flag em finally, inclusive erro de banco |

## Tech Decisions

Escolha: Apache HttpClient classic com DNS snapshot; JDK HttpClient não oferece DnsResolver por cliente, sockets manuais exigiriam parser HTTP novo. Pool de uma tentativa fechado imediatamente dispensa drenar corpo. Não reutilizar conexões entre destinos.
Referências oficiais: [cancelamento Apache](https://hc.apache.org/components/httpcomponents-client-5.3.x/5.3.1/httpclient5/apidocs/org/apache/hc/client5/http/config/RequestConfig.Builder.html), [executeOpen](https://github.com/apache/httpcomponents-client/blob/master/httpclient5/src/main/java/org/apache/hc/client5/http/classic/HttpClient.java). APIs verificadas também na compilação Docker.
