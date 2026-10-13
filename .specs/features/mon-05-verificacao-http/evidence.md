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
