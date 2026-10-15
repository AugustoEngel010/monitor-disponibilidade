# Validation MON-08: FAIL

**Data:** 2026-10-15. **Verifier:** subagente independente; autor diferente do verificador.
**Spec:** `.specs/features/mon-08-notificacoes/spec.md`.
**Diff range:** `52cb94e..0d2efdc`.

Seis dos sete ACs atendem ao resultado da spec. AC06 perde a parte fracionária da duração: diferença de 180.9 segundos produz texto de 180 segundos. Gate, runtime, smoke e seis mutações passaram na verificação independente. A feature requer a correção de precisão antes do fechamento.

## Tasks e integridade

T1–T5 constam concluídas em tasks.md e possuem commits no range. Nenhum código ou teste real foi alterado pelo Verifier. V1–V5 permanecem intactas. As alterações nos testes anteriores atualizam o total de migrações para seis ou mantêm o teste específico de upgrade V4→V5 limitado à versão 5; não removem assertions. Baseline anterior registrado em `52cb94e:.specs/STATE.md`: 356 testes. Gate atual: 386, delta +30.

## Aceitação derivada da spec

| AC | Resultado exigido | Evidência e assertion | Resultado independente |
| --- | --- | --- | --- |
| MON-08-AC01 / MAIL-01 | Intenções distintas para queda/recuperação e preservação em falhas repetidas | `src/test/java/br/com/monitor/IncidentProcessorIT.java:106`: `containsExactly(downIntent)` após dez falhas adicionais; `:124`–`:133`: snapshot RECOVERY e `count(notification_intents).isEqualTo(2)` | PASS; executado no gate completo |
| MON-08-AC02 / MAIL-02 | Intenção atômica com domínio; rollback/restart recuperáveis; SMTP fora da transação | `src/test/java/br/com/monitor/IncidentProcessorIT.java:258` e `:281`: falhas em cada escrita, rollback de snapshot e retry do mesmo resultado; `src/test/java/br/com/monitor/NotificationStoreIT.java:114` e `:127`: `isInstanceOf(DataAccessException.class)`, status PENDING/SENDING e histórico sem escrita parcial; `src/test/java/br/com/monitor/NotificationRuntimeIT.java:89`: após fechar/abrir contexto, duas intenções ACCEPTED e duas tentativas ACCEPTED; `src/test/java/br/com/monitor/SmtpDeliveryIT.java:140`: `hasMessage("SMTP cannot run inside a transaction")`; runtime `:134`–`:143`: contagem SMTP permanece 1 após retry da conclusão | PASS |
| MON-08-AC03 / MAIL-03 | Uma intenção por transição/destinatário, inclusive replay concorrente e UNIQUE PostgreSQL | `src/test/java/br/com/monitor/IncidentProcessorIT.java:214`–`:231`: replay concorrente, `count(notification_intents).isEqualTo(1)`; `src/test/java/br/com/monitor/NotificationSchemaIT.java:59`: insert duplicado deve lançar `DataAccessException`; `src/test/java/br/com/monitor/NotificationStoreIT.java:98`: `deliveries.hasSize(1)` em quatro claims concorrentes | PASS |
| MON-08-AC04 / MAIL-04 | Máximo cinco, espera término +30/60/120/240, não antecipar, quinta falha EXHAUSTED | `src/test/java/br/com/monitor/NotificationStoreIT.java:60`: `claim(due.minusMillis(1)).isEmpty()`; `:61`: igualdade reserva; `:74`–`:81`: próximo horário exato e EXHAUSTED/sem próxima data no quinto; `:84`–`:86`: sem sexta tentativa e cinco históricos | PASS; mutações de fronteira, espera e limite mortas |
| MON-08-AC05 / MAIL-05 | Histórico numerado UTC, estados/erros fixos e identidade estável em retries/restart | `src/test/java/br/com/monitor/NotificationStoreIT.java:63`–`:72`: número, Message-ID, início/fim exatos, FAILED/SMTP_FAILURE; `:149`–`:165`: UNCERTAIN/RESTART_UNCERTAIN, término na reconciliação e identidade preservada; `:177`: SMTP_UNCERTAIN; `src/test/java/br/com/monitor/NotificationRuntimeIT.java:111`–`:125`: restart real, espera de 30s, duas mensagens com mesmo Message-ID e attempt_count=2; `src/test/java/br/com/monitor/SmtpDeliveryIT.java:128`: timeout pós-DATA retorna UNCERTAIN | PASS |
| MON-08-AC06 / MAIL-06 | Destinatário/snapshot, tipos, incidente, quatro tempos pertinentes e duração igual à diferença em segundos | `src/test/java/br/com/monitor/SmtpDeliveryIT.java:59`–`:76`: Mailpit verifica Message-ID, To/From/Subject, nome, URL, tipo, incidente, timestamps e `contains("Duração estimada: 180 segundos")`; `src/test/java/br/com/monitor/NotificationStoreIT.java:192`–`:217`: snapshot anterior preservado após edição. Falta assertion fracionária; `src/main/java/br/com/monitor/notification/SmtpDelivery.java:84` usa `getSeconds()` | FAIL para frações; demais campos PASS |
| MON-08-AC07 / MAIL-07 | Recuperação aguarda todas as quedas não terminais do incidente; ACCEPTED/EXHAUSTED liberam; outros incidentes seguem | `src/test/java/br/com/monitor/NotificationStoreIT.java:185`–`:218`: dois parâmetros ACCEPTED/EXHAUSTED, `claim(BASE).isEmpty()` durante SENDING, outro incidente reservado, espera também em PENDING, recuperação liberada do mesmo incidente com novo destinatário | PASS; mutação da regra de causalidade morta |

## Gate independente

| Comando | Resultado | Log local |
| --- | --- | --- |
| `docker compose run --build --rm tests` | Exit 0; 144 unitários +242 IT =386; zero falhas, erros e skips; Checkstyle e BUILD SUCCESS | `.validation/mon08-verifier-gate.log` |
| `docker compose build app` | Exit 0; imagem runtime construída | `.validation/mon08-verifier-build.log` |
| `powershell -NoProfile -ExecutionPolicy Bypass -File scripts/validate-compose.ps1 -NoBuild` | Exit 0; PASS Compose lifecycle, incluindo queda/recuperação SMTP após pending/restart | `.validation/mon08-verifier-smoke.log`, `.validation/mon02-smoke-f8e2e320c39b.log` |

## Discrimination sensor

Profundidade expandida: seis mutações comportamentais independentes. Cópia criada por `git archive HEAD` em `%TEMP%/mon08-verifier`. Cada execução restaurou fontes originais antes da mutação seguinte. Nunca foi usado stash. A cópia foi removida após conferir o destino absoluto. Porcelain real vazio antes e depois, conferido antes de escrever este relatório; `.validation/mon08-isolation.log`. O arquivo de baseline por pipeline não foi criado por representar uma lista vazia; a comparação final verificou explicitamente a mesma baseline vazia capturada antes do archive.

Comando em cada cópia: `docker run --rm --mount type=bind,source=<scratch>,target=/workspace -v /var/run/docker.sock:/var/run/docker.sock -v monitor-disponibilidade_maven-cache:/root/.m2 -e DOCKER_HOST=unix:///var/run/docker.sock -e TESTCONTAINERS_HOST_OVERRIDE=host.docker.internal --add-host host.docker.internal:host-gateway monitor-disponibilidade-tests mvn --batch-mode --no-transfer-progress -Dcheckstyle.skip=true -Dtest=NotificationConfigurationTest -Dit.test=NotificationStoreIT,SmtpDeliveryIT verify`. Checkstyle fica fora do sensor para medir comportamento; foi exigido no gate completo. Tentativa inicial com argumento PowerShell mal interpretado foi descartada e não conta como mutação morta.

| Mutação | Fonte | Resultado exato nos 17 IT selecionados | Diagnóstico |
| --- | --- | --- | --- |
| due-boundary: `<=` vira `<` | `src/main/java/br/com/monitor/notification/NotificationStore.java:40` | Exit 1; 3 failures, 9 errors | Morta: nenhuma reserva na igualdade; cenários não obtêm o resultado exigido |
| backoff: base 30 vira 31 segundos | `src/main/java/br/com/monitor/notification/NotificationStore.java:88` | Exit 1; 1 failure, 5 errors | Morta: próximo horário incorreto e retries esperados ausentes |
| attempt-limit: quinta vira quarta | `src/main/java/br/com/monitor/notification/NotificationStore.java:85` | Exit 1; 2 failures, 1 error | Morta: EXHAUSTED antecipado |
| causality: bloqueio procura RECOVERY em vez de DOWN | `src/main/java/br/com/monitor/notification/NotificationStore.java:43` | Exit 1; 0 failures, 2 errors | Morta: recuperação não libera após os dois terminais |
| smtp-accepted: retorno positivo vira FAILED | `src/main/java/br/com/monitor/notification/SmtpDelivery.java:49` | Exit 1; 1 failure, 0 errors | Morta por `SmtpDeliveryIT.mailpitCapturesBothTransitionsWithFrozenPayloadDurationAndStableMessageId` |
| message-id: usa `<wrong@monitor.local>` | `src/main/java/br/com/monitor/notification/SmtpDelivery.java:44` | Exit 1; 1 failure, 0 errors | Morta pela assertion do Message-ID capturado no Mailpit |

**Sensor:** 6 injetadas, 6 mortas, 0 sobreviventes. Logs `.validation/mon08-mutant-<nome>.log` e `.validation/mon08-sensor-results.log`.

## Edge cases e qualidade

- Rollback de domínio, reserva e conclusão; replay e claims concorrentes; quinta órfã EXHAUSTED; resposta de commit perdida; retry só da persistência enquanto vivo: cobertos e aprovados.
- Timeout após DATA registra UNCERTAIN; rejeição conhecida FAILED; erros persistidos usam códigos fixos. O contrato admite duplicação após aceitação sem confirmação local.
- Worker SMTP separado permite checks HTTP durante SMTP lento (`NotificationRuntimeIT.java:151`–`:185`). Sem novas rotas, painel ou abstrações alheias ao escopo.
- Upgrade V5 preserva conteúdo e checksums (`NotificationSchemaIT.java:51`–`:55`); base vazia possui seis migrações no gate.
- Assunto com CR/LF: prova independente com a implementação Jakarta Mail/Angus instalada, `setSubject` + serialização MIME, incluindo `Ordinary\r\nX-Injected: yes`, `Ordinary\nBcc: hidden@example.org` e CRLF duplo. As linhas tornam-se continuação do Subject, sem novo cabeçalho nem corpo. Nenhum header injection confirmado. Log `.validation/mon08-subject-probe.log`.
- Shutdown usa `shutdownNow` e espera até cinco segundos (`NotificationRuntime.java:83`–`:85`), conforme `docs/notifications.md:30`. Reinício exige processo anterior completamente parado; a spec limita a instância única. Um envio interrompido permanece recuperável. Não foi identificado desvio adicional desta política.
- Não houve UAT interativo: comportamento de backend coberto por PostgreSQL, SMTP real e smoke Compose.
- Guidelines avaliadas: README.md, pom.xml, config/checkstyle.xml e coding-principles.md da skill. Nenhum teste em escopo foi identificado sem vínculo com os ACs, limites/configuração ou edge cases declarados.

## Gap e fix task

1. **Minor, AC06:** frações de segundo são descartadas no payload. Reprodução independente chamou `SmtpDelivery.body` por reflection sobre classes da cópia: firstFailureAt `2026-10-15T12:00:00.100Z`, recoveryStartedAt `2026-10-15T12:03:01Z`; diferença `PT3M0.9S`; saída `Duração estimada: 180 segundos`. A spec exige a diferença em segundos e não define truncamento. Log `.validation/mon08-duration-probe.log`; fonte `src/main/java/br/com/monitor/notification/SmtpDelivery.java:84`; teste atual inteiro `src/test/java/br/com/monitor/SmtpDeliveryIT.java:71` não discrimina o caso.

**Fix T6:** preservar a diferença exata em segundos decimais na apresentação, preservando a forma inteira quando não há fração; adicionar caso Mailpit fracionário dedicado sem alterar assertions existentes. Verificar exatamente 180.9 para a reprodução acima, conteúdo congelado e gate Docker completo. Reexecutar Verifier após o commit.

MAIL-01/02/03/04/05/07 estão verificados neste relatório. MAIL-06 requer correção. A spec permanece sem alteração pelo Verifier para o autor registrar a traceabilidade ao fechar o ciclo.

`python .codex/skills/tlc-spec-driven/scripts/validate_state.py mon-08-notificacoes` rejeitou corretamente o fechamento: `validation.md verdict is FAIL`, um erro. Lesson `L-001` registrada via script com sinal `ac_gap`, fonte `SmtpDelivery.java:84`, status candidate e recorrência 1. Nenhum commit ou push realizado.
