# Verificações HTTP

MON-05 fornece `CheckService.executeAndRecord(Monitor snapshot, UUID executionId)` no módulo `br.com.monitor.monitoring`. O snapshot contém identidade, configuração, configurationVersion e executionGeneration. O retorno é o resultado persistido. Não há endpoint de execução nesta entrega; MON-06 chamará o serviço pelo agendador e coordenará pausa/cancelamento. O cadastro administrativo mantém Basic/CSRF.

A tentativa faz um GET, sem credenciais, cookies, proxy de ambiente, retry ou redirect. O código esperado é comparado exatamente ao código final recebido. Um 301 esperado é SUCCESS; esperando 200, é HTTP_FAILURE. Nenhum Location é visitado. O timeout do monitor inclui validação, DNS, conexão, TLS e cabeçalhos. Duração usa `System.nanoTime`; timestamps usam Clock UTC e PostgreSQL os armazena com precisão de microssegundos.

O recebimento dos cabeçalhos termina a medição. O socket e o cliente são fechados imediatamente, sem drenar ou esperar o corpo. Fechamento da entidade após o socket não substitui a observação por erro de limpeza. Interromper a thread chamadora cancela a tentativa e produz CANCELLED. O serviço limpa temporariamente a flag para registrar o cancelamento pelo JDBC e a restaura em finally, também quando o banco falha.

| Classificação | Evidência externa | Status HTTP | Erro armazenado |
| --- | --- | --- | --- |
| SUCCESS | Sim | Recebido | null |
| HTTP_FAILURE | Sim | Recebido | Unexpected HTTP status |
| TIMEOUT | Sim | null | Attempt timed out |
| DNS_FAILURE | Sim | null | DNS resolution failed |
| CONNECTION_FAILURE | Sim | null | Connection failed |
| TLS_FAILURE | Sim | null | TLS validation or handshake failed |
| INTERNAL_ERROR | Não | null | Internal execution error |
| CANCELLED | Não | null | Attempt cancelled |
| DESTINATION_BLOCKED | Não | null | Destination blocked by policy |

Falha no banco é propagada ao chamador e não vira falha do destino. A persistência pode ser repetida com `CheckRepository.save(result)`. Reinício durante uma tentativa e aplicação ao estado/incidente pertencem a MON-06/07.

## Destinos e demonstração

A URI precisa passar pelas regras administrativas: HTTP/HTTPS, host e porta válidos, sem userinfo, query ou fragmento. Literais não públicos são rejeitados antes de DNS. Todos os endereços A/AAAA resolvidos precisam ser públicos; uma resposta mista é bloqueada integralmente. IPv4 privado, loopback, link-local, CGNAT, multicast, unspecified, documentação e reservado é bloqueado. IPv6 é limitado a unicast global 2000::/3, excluindo faixas especiais, documentação e transição; IPv4 mapeado é validado como IPv4.

Uma tentativa usa o primeiro endereço do snapshot validado, sem resolução adicional nem fallback. O host original permanece no HTTP e no TLS, preservando cadeia de confiança, hostname e SNI. Fallback entre IPs não pode mascarar erro TLS. O pool pertence somente à tentativa e não reaproveita sockets entre monitores. APIs oficiais consultadas: [DNS/operador de conexão Apache 5.5.2](https://raw.githubusercontent.com/apache/httpcomponents-client/rel/v5.5.2/httpclient5/src/main/java/org/apache/hc/client5/http/impl/io/DefaultHttpClientConnectionOperator.java) e [cancelamento duro](https://hc.apache.org/components/httpcomponents-client-5.3.x/5.3.1/httpclient5/apidocs/org/apache/hc/client5/http/config/RequestConfig.Builder.html).

`MONITOR_DEMO_ORIGIN` vazia mantém a política pública. Para um serviço de demonstração configurado separadamente, definir, por exemplo:

```dotenv
MONITOR_DEMO_ORIGIN=http://demo:8080
```

Somente esse esquema/host/porta pode usar os endereços resolvidos do serviço. Outro host, alias, IP literal, porta ou esquema continua bloqueado. Caminhos HTTP dessa origem podem variar. A configuração não aceita caminho além de `/`, query, fragmento ou credenciais, e uma origem inválida impede startup com mensagem fixa sem ecoar o valor. O Compose passa a variável somente à aplicação; o serviço de demonstração completo virá em MON-12. Recriar app após alterar a variável.

DNS nativo pode terminar depois da interrupção do worker. O chamador encerra a tentativa no limite e o worker verifica cancelamento/deadline antes de abrir sockets. Não há conexão HTTP posterior ao timeout. A operação nativa de resolução obedece também aos limites do resolvedor do sistema.

## Persistência e repetição

V3 cria `monitor.check_results`: execution_id UUID PK, monitor_id FK, configuration_version, execution_generation, started_at, finished_at, duration_nanos, outcome, http_status e error_message. Índice por monitor/término/UUID. Não existem colunas de corpo, headers ou URL. Mensagens vêm do enum e são verificadas por CHECK SQL, sem texto bruto de exceções.

`INSERT ... ON CONFLICT (execution_id) DO NOTHING` mantém o primeiro registro; a leitura retorna esse vencedor, inclusive sob concorrência. Reutilização do UUID para outro monitor/versão/geração é rejeitada. Repetir `executeAndRecord` pode enviar outro GET, mas mantém um resultado. Não há garantia de envio HTTP único. O serviço registra snapshots antigos e deixa sua elegibilidade de aplicação para MON-06/07; não altera estado, contadores, incidente ou agenda.

## Validação por Docker

```powershell
docker compose run --build --rm tests
docker compose build app
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/validate-compose.ps1 -NoBuild
```

`tests` executa Checkstyle, unitários e Failsafe com servidores HTTP/TLS em portas aleatórias e PostgreSQLs Testcontainers isolados. O banco de desenvolvimento não é usado. Certificados de fixture são gerados por keytool dentro do contêiner. Relatórios XML e Checkstyle ficam em `target/`. O smoke usa seu próprio projeto/volume e verifica prontidão, autenticação, rotação, histórico completo 1/2/3 e sentinela após restart/down-up; remove somente seus recursos descartáveis.
