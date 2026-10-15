# Validation MON-08: PASS

**Data:** 2026-10-15. **Verifier:** subagente independente; autor diferente do verificador.
**Spec:** `.specs/features/mon-08-notificacoes/spec.md`.
**Diff range:** `52cb94e..f2194e2` (revalidação após T6).

Os sete ACs atendem ao resultado da spec. T6 preserva 180.9 segundos e mantém o caso inteiro em 180 segundos. Gate independente com 387 testes, runtime e smoke passaram. Sete mutações foram mortas, incluindo a reintrodução do truncamento, detectada pelo novo teste SMTP.

## Tasks e integridade

T1–T6 constam concluídas em tasks.md e possuem commits no range. Nenhum código ou teste real foi alterado pelo Verifier. V1–V5 permanecem intactas. As alterações nos testes anteriores atualizam o total de migrações para seis ou mantêm o teste específico de upgrade V4→V5 limitado à versão 5; não removem assertions. Baseline anterior registrado em `52cb94e:.specs/STATE.md`: 356 testes. Gate atual: 387, delta +31. O diff `0d2efdc..f2194e2` altera apenas o cálculo da duração no código e acrescenta SmtpDurationIT; nenhum teste anterior é alterado.

## Aceitação derivada da spec

| AC | Resultado exigido | Evidência e assertion | Resultado independente |
| --- | --- | --- | --- |
| MON-08-AC01 / MAIL-01 | Intenções distintas para queda/recuperação e preservação em falhas repetidas | `src/test/java/br/com/monitor/IncidentProcessorIT.java:106`: `containsExactly(downIntent)` após dez falhas adicionais; `:124`–`:133`: snapshot RECOVERY e `count(notification_intents).isEqualTo(2)` | PASS; executado no gate completo |
| MON-08-AC02 / MAIL-02 | Intenção atômica com domínio; rollback/restart recuperáveis; SMTP fora da transação | `src/test/java/br/com/monitor/IncidentProcessorIT.java:258` e `:281`: falhas em cada escrita, rollback de snapshot e retry do mesmo resultado; `src/test/java/br/com/monitor/NotificationStoreIT.java:114` e `:127`: `isInstanceOf(DataAccessException.class)`, status PENDING/SENDING e histórico sem escrita parcial; `src/test/java/br/com/monitor/NotificationRuntimeIT.java:89`: após fechar/abrir contexto, duas intenções ACCEPTED e duas tentativas ACCEPTED; `src/test/java/br/com/monitor/SmtpDeliveryIT.java:140`: `hasMessage("SMTP cannot run inside a transaction")`; runtime `:134`–`:143`: contagem SMTP permanece 1 após retry da conclusão | PASS |
| MON-08-AC03 / MAIL-03 | Uma intenção por transição/destinatário, inclusive replay concorrente e UNIQUE PostgreSQL | `src/test/java/br/com/monitor/IncidentProcessorIT.java:214`–`:231`: replay concorrente, `count(notification_intents).isEqualTo(1)`; `src/test/java/br/com/monitor/NotificationSchemaIT.java:59`: insert duplicado deve lançar `DataAccessException`; `src/test/java/br/com/monitor/NotificationStoreIT.java:98`: `deliveries.hasSize(1)` em quatro claims concorrentes | PASS |
| MON-08-AC04 / MAIL-04 | Máximo cinco, espera término +30/60/120/240, não antecipar, quinta falha EXHAUSTED | `src/test/java/br/com/monitor/NotificationStoreIT.java:60`: `claim(due.minusMillis(1)).isEmpty()`; `:61`: igualdade reserva; `:74`–`:81`: próximo horário exato e EXHAUSTED/sem próxima data no quinto; `:84`–`:86`: sem sexta tentativa e cinco históricos | PASS; mutações de fronteira, espera e limite mortas |
| MON-08-AC05 / MAIL-05 | Histórico numerado UTC, estados/erros fixos e identidade estável em retries/restart | `src/test/java/br/com/monitor/NotificationStoreIT.java:63`–`:72`: número, Message-ID, início/fim exatos, FAILED/SMTP_FAILURE; `:149`–`:165`: UNCERTAIN/RESTART_UNCERTAIN, término na reconciliação e identidade preservada; `:177`: SMTP_UNCERTAIN; `src/test/java/br/com/monitor/NotificationRuntimeIT.java:111`–`:125`: restart real, espera de 30s, duas mensagens com mesmo Message-ID e attempt_count=2; `src/test/java/br/com/monitor/SmtpDeliveryIT.java:128`: timeout pós-DATA retorna UNCERTAIN | PASS |
| MON-08-AC06 / MAIL-06 | Destinatário/snapshot, tipos, incidente, quatro tempos pertinentes e duração igual à diferença em segundos | `src/test/java/br/com/monitor/SmtpDeliveryIT.java:59`–`:76`: Mailpit verifica Message-ID, To/From/Subject, nome, URL, tipo, incidente, timestamps e `contains("Duração estimada: 180 segundos")`; `src/test/java/br/com/monitor/NotificationStoreIT.java:192`–`:217`: snapshot anterior preservado após edição. `src/test/java/br/com/monitor/SmtpDurationIT.java:42`: corpo real capturado deve conter `Primeira falha: 2026-10-15T12:00:00.100Z`, `Início da recuperação: 2026-10-15T12:03:01Z` e `Duração estimada: 180.9 segundos` | PASS; caso inteiro e fracionário executados e mutação de truncamento morta |
| MON-08-AC07 / MAIL-07 | Recuperação aguarda todas as quedas não terminais do incidente; ACCEPTED/EXHAUSTED liberam; outros incidentes seguem | `src/test/java/br/com/monitor/NotificationStoreIT.java:185`–`:218`: dois parâmetros ACCEPTED/EXHAUSTED, `claim(BASE).isEmpty()` durante SENDING, outro incidente reservado, espera também em PENDING, recuperação liberada do mesmo incidente com novo destinatário | PASS; mutação da regra de causalidade morta |

## Gate independente

| Comando | Resultado | Log local |
| --- | --- | --- |
| `docker compose run --build --rm tests` | Exit 0; 144 unitários +243 IT =387; zero falhas, erros e skips; Checkstyle e BUILD SUCCESS | `.validation/mon08-verifier-t6-gate.log` |
| `docker compose build app` | Exit 0; imagem runtime construída | `.validation/mon08-verifier-t6-build.log` |
| `powershell -NoProfile -ExecutionPolicy Bypass -File scripts/validate-compose.ps1 -NoBuild` | Exit 0; PASS Compose lifecycle, incluindo queda/recuperação SMTP após pending/restart | `.validation/mon08-verifier-t6-smoke.log`, `.validation/mon02-smoke-994734b930a4.log` |

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

As seis execuções acima pertencem ao range inicial `52cb94e..0d2efdc`. O diff da T6 confirma que os pontos mutados e seus testes permanecem inalterados; suas evidências continuam aplicáveis. O único novo comportamento, precisão fracionária, recebeu sensor adicional no HEAD `f2194e2`.

| Mutação adicional | Fonte | Resultado exato | Diagnóstico |
| --- | --- | --- | --- |
| fractional-truncation: remove `.add(BigDecimal.valueOf(duration.getNano(), 9))` e volta a usar apenas segundos inteiros | `src/main/java/br/com/monitor/notification/SmtpDelivery.java:83` | Exit 1; seis IT, um failure, zero errors/skips; cinco SmtpDeliveryIT passaram e SmtpDurationIT falhou | Morta: corpo contém 180 segundos, assertion exige 180.9 |

Execução adicional na cópia `%TEMP%/mon08-verifier-t6`, extraída por archive de `f2194e2`; mesmo comando Docker acima, com `-Dit.test=SmtpDurationIT,SmtpDeliveryIT`. O gate completo passou antes da mutação. Cópia removida, baseline salvo vazio e porcelain final vazio idênticos antes da edição deste relatório. Logs `.validation/mon08-verifier-t6-mutant.log`, `.validation/mon08-verifier-t6-sensor-results.log` e `.validation/mon08-verifier-t6-isolation.log`.

**Sensor:** 7 injetadas, 7 mortas, 0 sobreviventes. Logs anteriores `.validation/mon08-mutant-<nome>.log` e `.validation/mon08-sensor-results.log` preservados.

## Edge cases e qualidade

- Rollback de domínio, reserva e conclusão; replay e claims concorrentes; quinta órfã EXHAUSTED; resposta de commit perdida; retry só da persistência enquanto vivo: cobertos e aprovados.
- Timeout após DATA registra UNCERTAIN; rejeição conhecida FAILED; erros persistidos usam códigos fixos. O contrato admite duplicação após aceitação sem confirmação local.
- Worker SMTP separado permite checks HTTP durante SMTP lento (`NotificationRuntimeIT.java:151`–`:185`). Sem novas rotas, painel ou abstrações alheias ao escopo.
- Upgrade V5 preserva conteúdo e checksums (`NotificationSchemaIT.java:51`–`:55`); base vazia possui seis migrações no gate.
- Assunto com CR/LF: prova independente com a implementação Jakarta Mail/Angus instalada, `setSubject` + serialização MIME, incluindo `Ordinary\r\nX-Injected: yes`, `Ordinary\nBcc: hidden@example.org` e CRLF duplo. As linhas tornam-se continuação do Subject, sem novo cabeçalho nem corpo. Nenhum header injection confirmado. Log `.validation/mon08-subject-probe.log`.
- Shutdown usa `shutdownNow` e espera até cinco segundos (`NotificationRuntime.java:83`–`:85`), conforme `docs/notifications.md:30`. Reinício exige processo anterior completamente parado; a spec limita a instância única. Um envio interrompido permanece recuperável. Não foi identificado desvio adicional desta política.
- Não houve UAT interativo: comportamento de backend coberto por PostgreSQL, SMTP real e smoke Compose.
- Guidelines avaliadas: README.md, pom.xml, config/checkstyle.xml e coding-principles.md da skill. Nenhum teste em escopo foi identificado sem vínculo com os ACs, limites/configuração ou edge cases declarados.

## Gap corrigido e revalidação

O primeiro relatório identificou um gap Minor em AC06: frações eram descartadas. A reprodução usou firstFailureAt `2026-10-15T12:00:00.100Z` e recoveryStartedAt `2026-10-15T12:03:01Z`; diferença `PT3M0.9S`, saída anterior 180 segundos. Log histórico `.validation/mon08-duration-probe.log`.

**T6, commit f2194e2:** cálculo com segundos mais nanos em BigDecimal e apresentação decimal sem zeros finais desnecessários. `SmtpDurationIT.java:42` prova exatamente 180.9 no Mailpit; o caso inteiro anterior continua 180. A mutação que remove os nanos falha somente no novo teste, comprovando que o gap foi fechado. Não há gaps pendentes.

MAIL-01 a MAIL-07 estão verificados. A spec permanece sem alteração pelo Verifier para o autor registrar a traceabilidade ao fechar o ciclo.

A primeira validação rejeitou corretamente o fechamento e registrou a lesson L-001 via script com sinal ac_gap, fonte SmtpDelivery.java:84, status candidate e recorrência 1. A revalidação limpa não registra nova lesson. Nenhum código/teste real, commit ou push realizado pelo Verifier.

Gate de fechamento executado após atualizar o relatório: `python .codex/skills/tlc-spec-driven/scripts/validate_state.py mon-08-notificacoes`, exit 0, `0 error(s) across [mon-08-notificacoes]`.
