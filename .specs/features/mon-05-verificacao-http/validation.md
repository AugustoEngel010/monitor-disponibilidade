# Validation: MON-05 — PASS

**Result:** PASS. Oito ACs com resultados definidos na spec e assertions correspondentes. Gate independente: 264 testes aprovados, nenhuma falha, erro ou skip. Runtime e smoke Docker aprovados. Seis mutações detectadas por assertions, nenhuma sobrevivente.

**Data:** 2026-10-13. **Verifier:** subagente independente, distinto do autor.
**Spec:** `.specs/features/mon-05-verificacao-http/spec.md`.
**Diff:** `c5ad290..d65d791`; HEAD verificado `d65d791776bdb04c8a4fdadd59470a73b3fd0142`.

## Tasks e escopo

T1–T5 têm os critérios Done when marcados. A verificação confirmou política, transporte, persistência, integração Spring e documentação/configuração. O contrato é Java interno; não há endpoint ou scheduler novo. A validação não alterou código, testes, commits ou estado administrativo. UAT visual não se aplica a esta entrega de backend.

## Resultados ancorados na spec

As evidências abaixo foram derivadas da spec e das assertions, sem usar o `evidence.md` do autor como prova.

| AC / requisito | Resultado exigido | Evidência `file:line` e assertion | Resultado |
| --- | --- | --- | --- |
| MON-05-AC01 / HTTP-01 | GET; SUCCESS somente para status esperado, HTTP_FAILURE nos demais; preservar status; não seguir redirect | `src/test/java/br/com/monitor/HttpCheckExecutorIT.java:36`: casos 200/200, 200/503, 200/301 e 301/301; :43 `assertThat(result.outcome()).isEqualTo(outcome)`; :44 `httpStatus().isEqualTo(received)`; :52 método `GET /check HTTP/1.1`; :53 `connections.get().isEqualTo(1)`. A fixture emite Location e corpo interminável. | PASS |
| MON-05-AC02 / HTTP-02 | Timeout total T incluindo DNS/TLS, cancelamento e liberação de conexão em todos os términos | `src/test/java/br/com/monitor/HttpCheckExecutorIT.java:62`: `outcome().isEqualTo(Outcome.TIMEOUT)`; :64 duração entre 900 e 1500 ms para T=1; :66 peer fechado. :181–186 DNS termina dentro de 1500 ms e `peer.connections.get().isZero()` mesmo após resolver tardio. :300–302 TLS TIMEOUT, duração <1500 ms e peer fechado. :118–121 CANCELLED, status null, flag restaurada e peer fechado. :51 peer fechado no sucesso/status inesperado; :135–137 conexão interrompida classificada e uma única tentativa. | PASS |
| MON-05-AC03 / HTTP-03 | Identidade, versão, geração, instantes UTC e duração monotônica não negativa até headers/erro, sem aguardar corpo | `src/test/java/br/com/monitor/HttpCheckExecutorIT.java:45`: monitorId exato; :46 executionId exato; :47–48 versão/geração 1; :49 ordem dos instantes; :50 duração entre 1 e 900 ms com corpo interminável. :81–84 `startedAt/finishedAt.isEqualTo(instant)` UTC fixo e duração incluindo DNS entre 190 e 900 ms. `src/test/java/br/com/monitor/CheckRepositoryIT.java:66`–:74 IDs, versão 3, geração 7, timestamps exatos, duração 123456789 e status exato na linha SQL. `src/test/java/br/com/monitor/CheckServiceIT.java:130`–:134 snapshot antigo preservado, geração 1 e monitor pausado permanece na geração 2. | PASS |
| MON-05-AC07 / HTTP-04 | DNS_FAILURE, CONNECTION_FAILURE, TLS_FAILURE, INTERNAL_ERROR, CANCELLED e DESTINATION_BLOCKED; conjunto exato de evidências externas | `src/test/java/br/com/monitor/HttpCheckExecutorIT.java:158`: DNS_FAILURE; :148 CONNECTION_FAILURE; :287 TLS_FAILURE; :252 INTERNAL_ERROR; :99 e :118 CANCELLED; :222 e :225 DESTINATION_BLOCKED. :256–257 `outcome.external().isEqualTo(outcome != CANCELLED && outcome != INTERNAL_ERROR && outcome != DESTINATION_BLOCKED)` percorre todos os nove outcomes e comprova o conjunto de seis externos. :159, :253 e :289 verificam mensagens fixas; :160/:254/:288 status null. | PASS |
| MON-05-AC05 / HTTP-05 | Rejeitar URI inválida/IP não público/DNS misto antes da conexão; snapshot DNS fixado; redirect desativado; TLS cadeia e hostname padrão | `src/test/java/br/com/monitor/DestinationPolicyTest.java:25`–:27: 33 endereços não públicos geram Blocked; :44 URLs inválidas/literais bloqueados; :50–54 DNS misto bloqueado e vazio DNS_FAILURE. `src/test/java/br/com/monitor/HttpCheckExecutorIT.java:222`–:227 literal bloqueado antes do DNS (`calls.isZero()`), DNS misto bloqueado/status null; :240–242 SUCCESS e `calls.get().isEqualTo(1)` apesar de resposta DNS posterior alterada. :43/:53 301 sem segunda conexão; :287 cadeia não confiável TLS_FAILURE; :335–340 certificado confiável com hostname correspondente SUCCESS e divergente TLS_FAILURE/status null. | PASS |
| MON-05-AC06 / HTTP-06 | Exceção privada somente com origem demo explicitamente autorizada, host/esquema/porta exatos, sem liberar alias/sub-rede | `src/test/java/br/com/monitor/DestinationPolicyTest.java:62`–:63 origem autorizada retorna endereço exato; :64–70 outro host/porta/esquema/literal/alias e demo ausente geram Blocked; :77–78 configuração malformada rejeitada com mensagem fixa. `src/test/java/br/com/monitor/CheckServiceIT.java:81`–:89 demo configurada produz SUCCESS persistido; :105–110 demo ausente gera DESTINATION_BLOCKED, external=false e `hits.isZero()`; :118–121 alias do mesmo IP bloqueado sem hits e persistido. | PASS |
| MON-05-AC04 / HTTP-07 | Armazenar apenas metadados, status opcional e erro fixo sanitizado; sem corpo/headers/URL/texto bruto | `src/test/java/br/com/monitor/CheckRepositoryIT.java:58`: todos os nove outcomes; :64–65 `row.containsOnlyKeys(...)` enumera exatamente as dez colunas permitidas; :73–75 outcome/status/mensagem com valores exatos; :143–146 banco rejeita mensagem bruta e status incompatível. `src/test/java/br/com/monitor/HttpCheckExecutorIT.java:159`–:161 mensagem DNS fixa/status null e `toString().doesNotContain("secret-password", "private.example")`. `src/test/java/br/com/monitor/CheckServiceIT.java:95`–:97 SUCCESS sem mensagem e linha SQL não contém segredo do corpo nem origem. V3 impõe mensagens fixas por outcome. | PASS |
| MON-05-AC08 / HTTP-08 | UUID repetido/concorrente: única linha, primeiro registro vence sem reescrever metadados | `src/test/java/br/com/monitor/CheckRepositoryIT.java:84`: `repository.save(duplicate).isEqualTo(first)` com instantes/duração/status diferentes; :86–87 `find(...).contains(first)` e count=1. :105–109 winner é uma das observações, segunda chamada e find iguais ao winner, count=1. :124–128 identidade monitor/versão/geração divergente rejeitada e registro original preservado. :156–158 nova conexão lê registro durável e repetição mantém count=1. `src/test/java/br/com/monitor/CheckServiceIT.java:92`–:94 serviço retorna original após nova resposta 503, duas requisições e uma linha. | PASS |

Sem AC descoberto, assertion superficial ou lacuna de precisão na spec.

## Gates independentes e integridade dos testes

Cópia de `git archive d65d791` em `.validation/verifier-mon05`, sem `.env` real. Todas as execuções Java/Maven ocorreram em Docker. Python no host foi usado somente para ler XML e validar o relatório.

| Gate | Comando na cópia | Resultado |
| --- | --- | --- |
| Full / qualidade | `docker compose -p verifier-mon05 run --build --rm tests` | Exit 0, BUILD SUCCESS, Checkstyle sem violações; 110 unitários + 154 IT = 264 aprovados; 0 failures/errors/skipped |
| Runtime | `docker compose -p verifier-mon05 build app` | Exit 0; imagem runtime construída pelo Dockerfile, com cache de build permitido |
| Smoke | `powershell -NoProfile -ExecutionPolicy Bypass -File scripts/validate-compose.ps1 -NoBuild` | Exit 0; 23 PASSs; projeto `mon02-smoke-5a431a980e3b`, porta/credenciais descartáveis; cleanup concluído |

Contagens lidas diretamente dos XML Surefire/Failsafe da execução independente: ArchitectureTest 1, DestinationPolicyTest 64, MonitorConfigurationTest 39, MonitorTest 6; AdministrativeConfigurationIT 13, AdministrativeSecurityIT 29, ApplicationIT 3, CheckRepositoryIT 15, CheckServiceIT 6, HttpCheckExecutorIT 19, MigrationIT 2, MonitorApiIT 59, MonitorRepositoryIT 8. Nenhum skip a justificar.

Antes da feature: 160 testes (46 unitários/114 IT), conforme relatório independente de MON-04 no commit baseline `c5ad290`, `.specs/features/mon-04-ciclo-monitores/validation.md:52`. Depois: 264, delta +104 = 64 casos de política + 19 executor + 15 repositório + 6 serviço. O diff não remove testes nem enfraquece assertions. Ajustes existentes preservam V1/V2, acrescentam V3 às expectativas e incluem a tabela filha no cleanup. O teste antigo de upgrade V1→V2 continua com target 2; o novo teste em `src/test/java/br/com/monitor/CheckRepositoryIT.java:168`–:182 prova V2→V3, aplicação única e dados/histórico anteriores intactos.

Logs: `.validation/verifier-mon05-full.log`, `verifier-mon05-runtime.log`, `verifier-mon05-smoke.log`. XML: `.validation/verifier-mon05/target/{surefire-reports,failsafe-reports}`. O smoke comprovou readiness, Basic/rotação, migrações 1/2/3, UID 10001 sem Maven/javac/socket, persistência em restart/down-up e ausência de credenciais nos logs.

## Sensor de discriminação

Profundidade ampliada para rede/integridade: seis mutações individuais, apenas em produção da cópia scratch. Mesmos testes/assertions do HEAD. Bind mount de `src` apontou exclusivamente à cópia. Cada tentativa executou `mvn --batch-mode --no-transfer-progress clean verify -Dtest=DestinationPolicyTest -Dit.test=HttpCheckExecutorIT,CheckRepositoryIT,CheckServiceIT` no serviço tests. Checkstyle e compilação permaneceram ativos. Cada execução produziu 104 testes, zero errors/skips; exit 1 foi causado por assertion failures abaixo.

| Mutação | Código real correspondente | Falha injetada | Evidência que matou | Resultado |
| --- | --- | --- | --- | --- |
| M1 | `src/main/java/br/com/monitor/monitoring/HttpCheckExecutor.java:92` | Resolver novamente durante conexão, substituindo snapshot pinado | `src/test/java/br/com/monitor/HttpCheckExecutorIT.java:240`: resolutionSnapshotIsUsedForConnectionWithoutSecondLookup esperava SUCCESS, recebeu CONNECTION_FAILURE | Morto; 103 passed/1 failure |
| M2 | `src/main/java/br/com/monitor/monitoring/HttpCheckExecutor.java:62` | Timeout total retorna CONNECTION_FAILURE | `src/test/java/br/com/monitor/HttpCheckExecutorIT.java:62`, :181, :300 esperavam TIMEOUT, receberam CONNECTION_FAILURE (headers/DNS/TLS) | Morto; 101 passed/3 failures |
| M3 | `src/main/java/br/com/monitor/monitoring/HttpCheckExecutor.java:146` | UnknownHostException vira CONNECTION_FAILURE | `src/test/java/br/com/monitor/HttpCheckExecutorIT.java:158`: DNS_FAILURE esperado, CONNECTION_FAILURE observado | Morto; 103 passed/1 failure |
| M4 | `src/main/java/br/com/monitor/monitoring/HttpCheckExecutor.java:115` | Inverter comparação do status esperado | `src/test/java/br/com/monitor/HttpCheckExecutorIT.java:43`: quatro casos SUCCESS/HTTP_FAILURE invertidos; serviço e TLS válido também detectaram | Morto; 94 passed/10 failures |
| M5 | `src/main/java/br/com/monitor/monitoring/HttpCheckExecutor.java:140` | durationNanos sempre zero | `src/test/java/br/com/monitor/HttpCheckExecutorIT.java:50`, :64, :83 e `src/test/java/br/com/monitor/CheckServiceIT.java:88`: zero viola duração até headers, DNS, timeout e positividade | Morto; 97 passed/7 failures |
| M6 | `src/main/java/br/com/monitor/monitoring/CheckRepository.java:33` | ON CONFLICT sobrescreve duration_nanos com EXCLUDED.duration_nanos | `src/test/java/br/com/monitor/CheckRepositoryIT.java:84`, :127 e `src/test/java/br/com/monitor/CheckServiceIT.java:92`: original esperado, duração sobrescrita observada | Morto; 101 passed/3 failures |

**Sensor:** 6 injetadas, 6 mortas, 0 sobreviventes. Logs `.validation/verifier-mon05-sensor-{1..6}.log`; XML separados em `.validation/verifier-mon05/target/sensor-{1..6}`; exits em `.validation/verifier-mon05-sensor-exits.txt`. Não foram contadas falhas de compilação ou infraestrutura como kills.

Restauração em finally de cada arquivo. SHA-256 dos seis arquivos de produção na cópia confirmou igualdade antes/depois. Baseline real capturado por `git status --porcelain` antes do trabalho: vazio. Após restauração e cleanup Docker: vazio novamente, antes de escrever este relatório. Código/testes/HEAD reais intactos; nenhuma utilização de stash. A única escrita versionada é este relatório. Fontes restauradas e evidências scratch permanecem em `.validation`; rede/cache próprios de `verifier-mon05` removidos com `docker compose -p verifier-mon05 down --volumes`. O projeto smoke removeu seus próprios recursos. Nenhum volume de desenvolvimento foi tocado.

## Qualidade, bordas e rastreabilidade

Princípios de `.codex/skills/tlc-spec-driven/references/coding-principles.md`, gates Docker do README e Checkstyle de `config/checkstyle.xml` atendidos. Mudanças restritas à feature e aos ajustes de V3 necessários. Não há abstração/configuração além de resolver/Clock testáveis e exceção demo exigida. Transporte não lê corpo, não usa proxy/retry/redirect, valida todos os IPs e mantém cadeia/hostname TLS padrão. Banco armazena somente metadados e aplica unicidade/FK/CHECKs. Serviço restaura interrupção mesmo em erro de banco e preserva snapshot administrativo.

Todos os 104 novos casos mapeiam para ACs, bordas ou Done when: política 64 (HTTP-05/06); executor 19 (HTTP-01/02/03/04/05); repositório 15 (HTTP-03/07/08, FK, durabilidade, migração); serviço 6 (HTTP-01/03/06/07/08, cancelamento/falha de persistência). Não há rota nova que exija cobertura adicional; os 59 casos MonitorApiIT e 29 AdministrativeSecurityIT passaram. Bordas da spec cobertas incluem corpo interminável/301, DNS/headers/TLS lentos, interrupção antes/durante DNS/conexão, ausência de retry, certificado não confiável/hostname divergente, IPs especiais/DNS misto/rebinding, demo ausente/malformada/alias, UUID sequencial/concorrente/conflito de identidade, FK/erro de banco e snapshot antigo.

HTTP-01 a HTTP-08: verificados. O orquestrador pode atualizar os estados ainda Implementing em spec/tasks/STATE após este relatório; o Verifier não os editou. Nenhum gap/fix task ou sinal fundamentado para lessons: PASS limpo, sem mutant sobrevivente, SPEC_DEVIATION ou lacuna de precisão. Nenhuma lesson nova necessária.

**Fechamento:** `python .codex/skills/tlc-spec-driven/scripts/validate_state.py mon-05-verificacao-http` executado após a escrita do relatório. Resultado registrado pelo Verifier no resumo de retorno.
