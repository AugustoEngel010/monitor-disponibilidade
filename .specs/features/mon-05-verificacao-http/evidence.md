# MON-05: evidências por task

## T1

Gate: `docker compose run --build --rm tests mvn --batch-mode --no-transfer-progress test`, exit 0; 110 testes unitários, 64 novos, Checkstyle 0, sem falhas/erros/skips.

| AC / cenário | Assertion file:line | Outcome da spec | Coberto |
| --- | --- | --- | --- |
| AC05 IP não público | `src/test/java/br/com/monitor/DestinationPolicyTest.java:25` isInstanceOf(Blocked.class), hasMessage("Destination blocked by policy") | Bloqueado | Sim |
| AC05 público | `src/test/java/br/com/monitor/DestinationPolicyTest.java:35` containsExactlyElementsOf(List.of(addresses)) | A/AAAA públicos aceitos | Sim |
| AC05 URI/literal | `src/test/java/br/com/monitor/DestinationPolicyTest.java:44` isInstanceOf(Blocked.class) | Não tentar DNS proibido | Sim |
| AC05 DNS misto/vazio | `src/test/java/br/com/monitor/DestinationPolicyTest.java:50` Blocked; :53 UnknownHostException | Bloqueado / erro DNS | Sim |
| AC06 demo exata | `src/test/java/br/com/monitor/DestinationPolicyTest.java:62` containsExactly(privateAddress); :66 e :69 Blocked | Exceção somente origem autorizada | Sim |
| AC06 config inválida | `src/test/java/br/com/monitor/DestinationPolicyTest.java:77` hasMessage("Invalid demonstration origin") | Startup falha sem eco | Sim |

Mapeamento reverso: as seis linhas acima cobrem todos os métodos e os 64 casos parametrizados de DestinationPolicyTest; todos necessários a AC05/06. Nenhuma assertion sobre chamadas simuladas. Padrões pom.xml/Checkstyle/AssertJ existentes respeitados. Adequação PASS; sem desvio da spec.

## T2

Gate completo `docker compose run --build --rm tests`, exit 0: 110 unitários + 133 IT = 243, Checkstyle 0, zero falhas/erros/skips. Log `.validation/mon05-t2.log`. Diagnóstico focado: 19 testes executor PASS. Falhas iniciais detectaram erro de cleanup e fallback de IP mascarando TLS; implementação corrigida sem reduzir assertions. Dependência gerenciada pelo parent: httpclient5 5.5.2.

| AC / cenário | Assertion file:line | Outcome da spec | Coberto |
| --- | --- | --- | --- |
| AC01 GET/status/redirect | `src/test/java/br/com/monitor/HttpCheckExecutorIT.java:43` outcome equals; :44 status equals; :52 GET /check HTTP/1.1; :53 connections=1 | SUCCESS/HTTP_FAILURE e código exato | Sim |
| AC03 metadados/cabeçalhos | `src/test/java/br/com/monitor/HttpCheckExecutorIT.java:45` monitorId; :46 executionId; :47/48 versões=1; :49 término; :50 duração<900ms com corpo interminável; :81/82 Clock UTC; :83 inclui 200ms DNS | Payload completo até cabeçalhos | Sim |
| AC02/04 timeout/cleanup | `src/test/java/br/com/monitor/HttpCheckExecutorIT.java:62` TIMEOUT; :63 status null; :64 900..1500ms; :66 peer fechado | Total timeout e socket liberado | Sim |
| AC04/07 cancelamento | `src/test/java/br/com/monitor/HttpCheckExecutorIT.java:118` CANCELLED; :120 interrupted=true; :121 peer fechado; :99 cancelado antes de DNS; :101 calls=0 | CANCELLED sem evidência externa | Sim |
| AC07 conexão/DNS/interno | `src/test/java/br/com/monitor/HttpCheckExecutorIT.java:135` CONNECTION_FAILURE; :137 uma conexão; :148 recusa; :158 DNS_FAILURE; :159 mensagem fixa; :252 INTERNAL_ERROR; :256 external exato | Classificações separadas | Sim |
| AC02 DNS/TLS lentos | `src/test/java/br/com/monitor/HttpCheckExecutorIT.java:181` TIMEOUT; :182 <1500ms; :185 sem conexão posterior; :209 CANCELLED no DNS; :210 sem conexão; :300 TIMEOUT TLS; :302 socket fechado | Orçamento total e não conectar após cancelamento | Sim |
| AC05 bloqueio/snapshot | `src/test/java/br/com/monitor/HttpCheckExecutorIT.java:222` DESTINATION_BLOCKED; :223 DNS=0; :225 DNS misto bloqueado; :240 SUCCESS no primeiro IP; :241 lookup único | Política até conexão sem rebinding | Sim |
| AC05/07 TLS | `src/test/java/br/com/monitor/HttpCheckExecutorIT.java:287` TLS_FAILURE certificado; :289 mensagem fixa; :290 socket fechado; :335 certificado confiável com hostname correto SUCCESS; :339 hostname errado TLS_FAILURE | Cadeia e hostname preservados | Sim |

Mapeamento reverso: cada método de HttpCheckExecutorIT é uma linha desta tabela; as quatro combinações de status pertencem a AC01/05, os demais casos a AC02/03/04/05/07. Todos mantidos. Adequação PASS: payload, erros e recursos observados, sem assertion rasa; nenhum teste existente alterado.
