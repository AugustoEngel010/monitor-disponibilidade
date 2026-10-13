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

## T3

Gate completo `docker compose run --build --rm tests`, exit 0; 110 unitários + 148 IT = 258, 15 casos novos de persistência, Checkstyle 0, zero falhas/erros/skips. Log `.validation/mon05-t3.log`. PostgreSQL 17.6 exclusivo de Testcontainers; banco de desenvolvimento intocado.

| AC / cenário | Assertion file:line | Outcome da spec | Coberto |
| --- | --- | --- | --- |
| AC03/04/07 metadados e sanitização | `src/test/java/br/com/monitor/CheckRepositoryIT.java:61` save igual; :62 find igual; :64 containsOnlyKeys sem corpo; :66..75 valores exatos de cada campo e mensagem | Todos os metadados, nove classificações, sem corpo/erro bruto | Sim |
| AC08 repetição sequencial | `src/test/java/br/com/monitor/CheckRepositoryIT.java:84` duplicate retorna first; :86 find first; :87 count=1 | Primeiro resultado preservado | Sim |
| AC08 concorrência | `src/test/java/br/com/monitor/CheckRepositoryIT.java:105` winner isIn(success,failure); :106 second equals winner; :107 persisted winner; :109 count=1 | Única linha mesmo com duas conexões | Sim |
| AC08 identidade | `src/test/java/br/com/monitor/CheckRepositoryIT.java:124` IllegalArgumentException mensagem fixa; :127 first preservado; :128 count=1 | UUID não muda monitor/versão/geração | Sim |
| AC03/04 falha parcial | `src/test/java/br/com/monitor/CheckRepositoryIT.java:136` DataAccessException FK; :137 sem linha; :140 duração negativa rejeitada; :142 retry igual; :143/145 SQL erro bruto/status impossível rejeitados; :147 original igual | Sem registro parcial ou mensagem bruta | Sim |
| AC08 durabilidade/nova conexão | `src/test/java/br/com/monitor/CheckRepositoryIT.java:153` ausência empty; :156 reaberto contains(result); :157 save=result; :158 count=1 | Evidência persistente deduplicada | Sim |
| Migração e regressão | `src/test/java/br/com/monitor/CheckRepositoryIT.java:177` current migra uma vez; :178 zero repetição; :179 histórico antigo igual; :181 monitor igual; :182 tabela vazia | V3 preserva V1/V2 e dados | Sim |

Mapeamento reverso: nove casos parametrizados = AC03/04/07; repetição/concorrência/identidade/nova conexão = AC08; constraints = AC03/04; migração = preservação da arquitetura requerida. Adequação PASS. Fixtures corrigidas antes da implementação ser aprovada: nullable Integer sem unboxing e relógio fixo com precisão PostgreSQL, sem mudar assertions. Testes antigos preservados; expectativas de histórico ampliadas para 1,2,3. O teste específico de upgrade V1→V2 mantém target 2 e suas assertions originais; teste novo prova V2→V3. TRUNCATE inclui somente a nova tabela filha, sem CASCADE.

## T4

Gate completo `docker compose run --build --rm tests`, exit 0; 110 unitários + 154 IT = 264, seis casos novos de serviço, zero falhas/erros/skips/Checkstyle. Log `.validation/mon05-t4.log`.

| AC / cenário | Assertion file:line | Outcome da spec | Coberto |
| --- | --- | --- | --- |
| AC01/03/04 resultado persistido | `src/test/java/br/com/monitor/CheckServiceIT.java:81` SUCCESS; :82 status200; :83..88 metadados; :89 find=result; :90 monitor igual; :95 erro null; :96 não contém corpo/URL | Metadados persistentes, sem efeito administrativo | Sim |
| AC08 repetição | `src/test/java/br/com/monitor/CheckServiceIT.java:92` segunda tentativa retorna primeiro; :93 hits=2; :94 count=1 | Persistência idempotente; não promete envio HTTP único | Sim |
| AC06/07 demo opt-in | `src/test/java/br/com/monitor/CheckServiceIT.java:105` DESTINATION_BLOCKED sem config; :106 external=false; :108 persisted; :109 hits=0; :118 alias bloqueado; :120 hits=0 | Origem exata, política default fechada | Sim |
| AC03 snapshot antigo | `src/test/java/br/com/monitor/CheckServiceIT.java:131` generation1; :132 monitor paused igual; :133 generation2; :134 registro persistido | História preserva versão/geração sem aplicar estado | Sim |
| AC04/07 cancelamento persistido | `src/test/java/br/com/monitor/CheckServiceIT.java:154` CANCELLED; :155 interrupted=true; :156 persisted; :157 statusnull; :159 peer fechado | Cancelamento durável e recursos liberados | Sim |
| AC04 falha parcial | `src/test/java/br/com/monitor/CheckServiceIT.java:170` DataAccessException; :171 interrupted=true; :176 ausência; :177 sem HTTP; :178 count0; :179 retry SUCCESS; :180 count1 | Banco não vira falha externa nem registro parcial | Sim |

Mapeamento reverso: os seis métodos da tabela são todos os casos de CheckServiceIT, necessários aos cenários indicados. Adequação PASS. Segurança administrativa e demais testes da baseline permanecem aprovados. Contrato não cria rota ou agendamento e não aplica resultado ao estado.
