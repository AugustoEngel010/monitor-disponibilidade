# MON-04: validação independente

## Validation: PASS

**Data:** 2026-10-13. **Verifier:** subagente independente, autor diferente do verificador.
**Spec:** `.specs/features/mon-04-ciclo-monitores/spec.md`.
**Diff range:** `da7276f..f46df05`; HEAD verificado `f46df0505725bcff02eacde9665275c13ff19c88`.

Os sete ACs de origem e LIFE-08/09 têm assertions que verificam os resultados definidos na spec. Gate independente: 160 testes aprovados, nenhuma falha, erro ou skip; Checkstyle sem violações. Seis mutações comportamentais foram detectadas por assertions. Build runtime e smoke Docker aprovados.

## Critérios e evidências rederivadas

Os caminhos da tabela são relativos a `src/test/java/br/com/monitor/`. A cobertura foi rederivada das assertions e da spec, sem assumir o relatório do autor como prova.

| Critério | Resultado definido na spec | file:line e assertion observável | Resultado |
| --- | --- | --- | --- |
| MON-04-AC01 / LIFE-01 | POST 201, Location/UUID, configuração persistente, ACTIVE/UNKNOWN/NEVER_OBSERVED, contadores zero, versões 1, agenda imediata | `src/test/java/br/com/monitor/MonitorApiIT.java:71`: status `isEqualTo(201)`; :74 Location `contains(ROOT + "/" + id)`; :77 igualdade dos oito campos; :79–90 ciclo/estado/evidência, contadores, versões e agenda iguais a createdAt; :91 GET igual ao objeto completo; :92 SQL name igual a Demo. `src/test/java/br/com/monitor/MonitorTest.java:20`: State integral inicial igual ao contrato. | PASS |
| MON-04-AC02 / LIFE-02 | 400 com todos os campos inválidos e nenhuma persistência; limites inclusivos/defaults, tipos estritos | `src/test/java/br/com/monitor/MonitorConfigurationTest.java:17`: configuração normalizada e defaults exatos; :23–29 extremos aceitos; :38–40 status 400 e `containsOnlyKeys` dos oito campos; :46–53 limites opostos e T=I rejeitados. `src/test/java/br/com/monitor/MonitorApiIT.java:121`: assertInvalid 400 por campo (:503–506); :122 count zero; :132–134 status 400, oito erros, count zero; :149–173 tipos/JSON inválidos; :359–366 PUT inválido e igualdade integral das linhas persistidas. | PASS |
| MON-04-AC03 / LIFE-03 | URL com credencial ou esquema inválido retorna 400/error url, sem eco | `src/test/java/br/com/monitor/MonitorConfigurationTest.java:57`: matriz de userinfo/esquema/query/fragment/porta; :63 chama rejects, com 400 e chave url em :112–114. `src/test/java/br/com/monitor/MonitorApiIT.java:142`: assertInvalid url; :143 body `doesNotContain(url, "secret")`; :144 count zero. | PASS |
| MON-04-AC04 / LIFE-04 | UUID válido ausente retorna 404 em GET, PUT, pause e resume | `src/test/java/br/com/monitor/MonitorApiIT.java:189`: GET `isEqualTo(404)`; :190 MONITOR_NOT_FOUND; :337–349 parametriza PUT/pause/resume e verifica 404/code, além de UUID malformado 400/id. | PASS |
| MON-04-AC05 / LIFE-05 | Nome/destinatário preservam estado; política reinicia UNKNOWN, incrementa versões/geração, preserva histórico/identidade; incidente bloqueia alteração inteira 409 | `src/test/java/br/com/monitor/MonitorTest.java:31`: state integral preservado; :37 repetição integral; :44–46 todas as políticas bloqueadas com 409/OPEN_INCIDENT; :59–64 identidade/configuração/reset integral; :68–69 pausado sem agenda. `src/test/java/br/com/monitor/MonitorApiIT.java:221`: todos os valores editados iguais ao input; :223–235 identidade, histórico, versões 2, agenda imediata, GET e PUT repetido iguais; :267–269 409/code e GET integral igual ao anterior em PUT misto; :274–280 edição administrativa preserva dimensões e histórico. `src/test/java/br/com/monitor/MonitorRepositoryIT.java:123`: rollback integral; :126 state preservado; :144–149 roundtrip e histórico original. | PASS |
| MON-04-AC06 / LIFE-06 | Pause/resume idempotentes, geração avança só efetivamente; preservam DOWN/incidente e versão, zeram sequências, invalidam evidência e controlam agenda | `src/test/java/br/com/monitor/MonitorTest.java:76`: State PAUSED/DOWN integral; :78 repetição igual; :81–84 State ACTIVE/DOWN integral e repetição igual; :85 ACTIVE resume igual. `src/test/java/br/com/monitor/MonitorApiIT.java:284`: UNKNOWN/UP/DOWN; :298–309 pausa verifica dimensões/agenda e JSON integral repetido; :313–326 retomada verifica dimensões/agenda, JSON integral repetido, GET e histórico. `src/test/java/br/com/monitor/MonitorRepositoryIT.java:128`: repetição pausada igual; :130–135 retomada igual, incidente/DOWN/histórico preservados; :172–175 concorrência produz resultados iguais e geração 2. | PASS |
| MON-04-AC07 / LIFE-07 | Ordem createdAt/id ASC, totais, paginação válida/vazia, ciclo/estado/evidência distintos; FRESH inclusivo 0..2I+T | `src/test/java/br/com/monitor/MonitorApiIT.java:374–385`: defaults 0/20, vazio/totais zero, página máxima vazia com totais reais; :403–416 páginas adjacentes, totalElements=5/totalPages=3, tamanhos 2/2/1, ordem `containsExactlyElementsOf(expected)`, sem duplicatas, além do fim vazio; :439–458 ciclo/estado/evidência e recentObservation por caso; :469 e :476–478 parâmetros inválidos e erros acumulados. `src/test/java/br/com/monitor/MonitorTest.java:94–98`: FRESH em idade 0 e 125s, STALE em 125s+1ns/futuro, NEVER_OBSERVED após pause. | PASS |
| LIFE-08 | Capacidade de 100 inclui pausados e cadastros concorrentes; 101º retorna 409/MONITOR_LIMIT | `src/test/java/br/com/monitor/MonitorApiIT.java:197–201`: 100 retornam 201, próximo 409/code MONITOR_LIMIT e count 100. `src/test/java/br/com/monitor/MonitorRepositoryIT.java:154`: 99 pausados; :161 duas criações retornam exatamente 201/409; :163 count 100. | PASS |
| LIFE-09 | Basic obrigatório; escrita sem CSRF retorna 403 sem efeito | `src/test/java/br/com/monitor/MonitorApiIT.java:178–183`: GET/POST 401, POST Basic sem CSRF 403/count zero, cookie/token válido 201. :343–345 PUT/pause/resume 401/403 e snapshot SQL integral inalterado. :462 lista 401. `src/test/java/br/com/monitor/AdministrativeSecurityIT.java:137–164`: formas forjadas/CSRF inválido não mutam; token válido muta exatamente uma vez. | PASS |

Nenhum gap de precisão identificado. Instantes controlados no modelo provam fronteira exata; testes HTTP usam janelas amplas para frescor, sem depender da duração da requisição. Incidentes e histórico são fixtures/referências existentes. Criação/processamento de incidentes e execução HTTP continuam fora do escopo MON-04.

## Sensor de discriminação

Profundidade ampliada para integridade: seis mutações independentes, uma de cada vez, restauradas em finally. Cópia de `git archive f46df05` em `.validation/verifier-mon04`; testes reais permaneceram intactos. O bind mount de `src` apontou exclusivamente à cópia scratch. Checkstyle foi dispensado somente no sensor para que o resultado fosse uma assertion de comportamento.

| Mutação | Código original file:line | Teste/assertion que detectou | Resultado |
| --- | --- | --- | --- |
| Permitir T=I: `t >= i` → `t > i` | `src/main/java/br/com/monitor/administration/MonitorConfiguration.java:32` | `MonitorConfigurationTest.java:48` / :111: esperava throwable de validação; nenhum lançado. 39 testes, 1 failure, 0 errors/skips. | Killed, exit 1 |
| Remover guard de incidente: condição → `policyChanged && false` | `src/main/java/br/com/monitor/administration/Monitor.java:21` | `MonitorTest.java:44`: esperava MonitorProblem de edição proibida; nenhum lançado. 6 testes, 1 failure, 0 errors/skips. | Killed, exit 1 |
| Remover retorno idempotente da pausa: condição → `false` | `src/main/java/br/com/monitor/administration/Monitor.java:33` | `MonitorTest.java:78`: igualdade integral falha, generation 13 em vez de 12 e updatedAt alterado. 6 testes, 1 failure, 0 errors/skips. | Killed, exit 1 |
| Tornar fronteira FRESH exclusiva: `<= 0` → `< 0` | `src/main/java/br/com/monitor/administration/Monitor.java:48` | `MonitorTest.java:95`: esperado FRESH, obtido STALE em idade 125s. 6 testes, 1 failure, 0 errors/skips. | Killed, exit 1 |
| Deslocar OFFSET: `page * size` → `page * size + 1` | `src/main/java/br/com/monitor/administration/MonitorRepository.java:52` | `MonitorApiIT.java:407`: última página esperava tamanho 1, obteve 0. 6 unitários passam, 1 IT com 1 failure, 0 errors/skips. | Killed, exit 1 |
| Permitir 101º: count `>= 100` → `> 100` | `src/main/java/br/com/monitor/administration/MonitorService.java:34` | `MonitorApiIT.java:199`: esperado status 409, obtido 201. 6 unitários passam, 1 IT com 1 failure, 0 errors/skips. | Killed, exit 1 |

Comando-base em scratch: `docker compose -p verifier-mon04 run --rm -v <scratch>/src:/workspace/src tests mvn --batch-mode --no-transfer-progress -Dcheckstyle.skip=true -Dtest=<classe> test`. Para OFFSET/capacidade: `-Dtest=MonitorTest -Dit.test=MonitorApiIT#<método> verify`, respectivamente `adjacentPagesAreOrderedByCreationAndUuidWithoutOverlap` e `oneHundredAndFirstMonitorReturns409`.

**Sensor: PASS, 6 injected, 6 killed, 0 survived.** Logs individuais e resultados em `.validation/verifier-mon04/`. Nenhuma falha por compilação ou infraestrutura contou como killed.

## Gates executados independentemente

| Gate | Comando | Resultado |
| --- | --- | --- |
| Spec | `python .codex/skills/tlc-spec-driven/scripts/validate_spec.py mon-04-ciclo-monitores --strict` | Exit 0, zero erros/avisos |
| Tasks | `python .codex/skills/tlc-spec-driven/scripts/validate_tasks.py mon-04-ciclo-monitores` | Exit 0, zero erros/avisos |
| Full | `docker compose -p verifier-mon04 run --build --rm tests` na cópia original | Exit 0; mvn clean verify BUILD SUCCESS; 46 unitários/114 IT, zero failures/errors/skips |
| Runtime | `docker compose -p verifier-mon04 build app` após restauração e comparação SHA256 | Exit 0; imagem runtime construída |
| Smoke | `powershell -NoProfile -ExecutionPolicy Bypass -File scripts/validate-compose.ps1 -NoBuild` | Exit 0; PASS Compose lifecycle; projeto isolado mon02-smoke-009037f5b9e1 |

Logs: `.validation/verifier-mon04/full.log`, `runtime-restored.log`, `smoke.log`, `.validation/mon02-smoke-009037f5b9e1.log`. XMLs originais do gate preservados em `.validation/verifier-mon04/target/{surefire-reports,failsafe-reports}`. Checkstyle XML possui zero erros.

Contagens XML: ArchitectureTest 1; MonitorConfigurationTest 39; MonitorTest 6; AdministrativeConfigurationIT 13; AdministrativeSecurityIT 29; ApplicationIT 3; MigrationIT 2; MonitorApiIT 59; MonitorRepositoryIT 8. Baseline 48 preservada: 1+13+29+3+2. Delta +112. Nenhum teste anterior removido, desativado ou enfraquecido. MigrationIT preserva o contrato V1 em location isolada; ApplicationIT passa a exigir as duas migrações. Smoke compara integralmente ambas as linhas row_to_json após restart e down/up, além do sentinela 4242.

Uma tentativa de runtime durante o sensor leu fonte deliberadamente mutada e foi descartada. O gate runtime válido acima foi executado depois da restauração integral das fontes, confirmada por SHA256.

## Qualidade, escopo e isolamento

T1–T9 marcadas concluídas e nove commits locais correspondentes no range. Mudanças seguem README, pom.xml e config/checkstyle.xml; domínio puro, JDBC existente e transações explícitas, sem novas dependências. Rotas têm sucesso, erro de configuração, ausência de recurso e segurança. Testes novos mapeiam a cadastro/defaults, limites/gramática, transições/idempotência/incidente, persistência/migrações/concorrência, paginação/frescor ou segurança das nove linhas de requisitos. Sem funcionalidade além da spec.

O serviço usa lock de linha para mutações (`src/main/java/br/com/monitor/administration/MonitorService.java:67`) e advisory lock antes de count/insert (:33). A listagem usa REPEATABLE_READ (:54); count e itens são lidos na mesma transação. Sem deleção de monitores/histórico. Esses mecanismos foram inspecionados; as verificações de concorrência empíricas cobrem capacidade e pause.

Antes dos sensores, porcelain real estava vazio. Após cada restauração final, hashes das quatro fontes scratch eram iguais aos arquivos reais; depois de descartar fontes scratch e recursos Docker exclusivos, `git status --porcelain` voltou exatamente vazio e `git diff --exit-code` retornou 0, antes de escrever este relatório. HEAD não mudou. Caminhos absolutos de remoção foram verificados dentro de `.validation/verifier-mon04`; logs/XML permaneceram. Nenhum git stash, commit, push ou edição de implementação/testes reais.

UAT interativo não se aplica a backend/API. Validação local Docker; CI remota não executada. Sem gaps ranqueados, desvios ou mutantes sobreviventes. PASS limpo não produz lesson nova.
