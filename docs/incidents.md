# Processamento de incidentes

MON-07 aplica verificações persistidas aos monitores e mantém incidentes e transições. O fluxo segue o scheduler de instância única, o lock administrativo por monitor, JDBC e PostgreSQL/Flyway. HTTP permanece fora de transações de domínio.

`ScheduleStore.finish` grava o primeiro resultado por UUID, processa sua aplicação e remove a reserva na mesma transação. `CheckService.executeAndRecord` permanece responsável somente por HTTP e registro bruto. Se o resultado já foi salvo antes de uma falha, a reserva permite reaplicá-lo após reinício sem repetir HTTP. O contrato interno `IncidentProcessor.process(executionId)` também processa um resultado já persistido; não existe endpoint público de execução.

## Estados e confirmação

Somente ACTIVE da versão e geração correntes pode modificar a saúde. SUCCESS é sucesso; HTTP_FAILURE, TIMEOUT, DNS_FAILURE, CONNECTION_FAILURE e TLS_FAILURE são falhas externas. INTERNAL_ERROR, DESTINATION_BLOCKED e CANCELLED interrompem sequências, preservam estado/incidente e não atualizam a última observação externa.

| Contexto | Resultado | Efeito |
| --- | --- | --- |
| UNKNOWN/UP | Falha externa | Incrementa confirmação; em F confirma DOWN e abre incidente |
| UNKNOWN/UP | SUCCESS | Limpa falhas, estabelece UP sem recuperação nem aviso |
| DOWN | Falha externa | Limpa recuperação, mantém identidade do incidente |
| DOWN | SUCCESS | Incrementa recuperação; em R confirma UP e encerra incidente |
| Elegível | Não externo ou lacuna | Limpa sequências e horários provisórios, mantém saúde confirmada |
| Pausado, versão/geração antiga ou tentativa anterior ao último início aplicado | Qualquer | Somente histórico |
| UUID já processado | Qualquer | Nenhuma reaplicação ou mudança de horário |

F/R são os limites configurados, de 1 a 10. Com F=3/R=2, F,F,F abre e S,S fecha. F,F,S não abre; DOWN com S,F,S,S fecha somente nos dois últimos sucessos. Contadores saturam no limite, sem crescimento indefinido. O primeiro sucesso em UNKNOWN não é recuperação de incidente.

L = 2 × intervalo + timeout. Lacuna interrompe confirmações quando o início lógico da próxima reserva é estritamente posterior ao término da última observação + L. Igualdade preserva a sequência; a duração da própria requisição não conta como lacuna anterior. A consulta de frescor continua independente da saúde confirmada: um incidente pode permanecer aberto com dados antigos ou monitor pausado.

## Tempo e histórico

Todos os instantes persistidos são UTC com precisão PostgreSQL de microssegundos. O relógio do processamento não substitui a evidência:

| Campo do incidente | Evidência |
| --- | --- |
| firstFailureAt | Término da primeira falha da sequência que confirmou queda |
| downConfirmedAt | Término da F-ésima falha |
| recoveryStartedAt | Término do primeiro sucesso da sequência corrente de recuperação |
| recoveredAt | Término do R-ésimo sucesso |

Falha, lacuna, resultado não externo ou pausa durante recuperação limpa recoveryStartedAt. Um incidente encerrado mantém os horários da sequência que efetivamente recuperou. F/R=1 faz observação e confirmação coincidirem. Duração estimada final = recoveryStartedAt − firstFailureAt; inclui lacunas/pausas e não comprova indisponibilidade contínua.

Pausa e retomada efetivas limpam confirmações e invalidam observação corrente, preservando estado/incidente. Repetições são idempotentes. Nome/e-mail preservam as sequências; alteração de política sem incidente reinicia UNKNOWN e invalida execuções antigas. Com incidente, URL/I/T/status/F/R retornam 409. Intenções já criadas mantêm nome/URL/destinatário e horários congelados.

## Transação e reinício

V5 adiciona `incidents`, `result_applications`, `state_transitions` e `notification_intents`, sem alterar V1–V4. Índice parcial único impede dois incidentes abertos por monitor, inclusive em inserts concorrentes diretos. FKs relacionam monitor, versão, resultado causador e incidente. A referência corrente não pode apontar para incidente de outro monitor.

A transação confirma marcador de aplicação, estado/contadores, incidente, transição e intenção durável juntos. Erro em qualquer escrita faz rollback. O resultado bruto previamente confirmado continua imutável e a reserva permanece recuperável. Repetição sequencial/concorrente do mesmo UUID não incrementa contadores nem reescreve horários ou payloads. Resultado bruto e aplicação são registros separados para distinguir evidência de processamento.

No reinício, reservas sem resultado recebem CANCELLED e quebram confirmações somente quando elegíveis. Resultado existente vence e é aplicado antes de remover a reserva. Sem reserva interrompida, contadores e incidentes sobrevivem; a próxima tentativa decide se houve lacuna. Próximo agendamento é preservado e não há replay dos intervalos perdidos.

Na atualização de MON-06 para MON-07, resultados de verificações já concluídas recebem marcador HISTORY_ONLY. Não se abrem incidentes retroativos. Resultados ligados a reservas pendentes continuam reconciliáveis. Intenções nascem PENDING; [MON-08](notifications.md) implementa SMTP, ordem, histórico e retries sobre esse registro durável.

## Validação em contêineres

```sh
docker compose run --build --rm tests
docker compose build app
```

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/validate-compose.ps1 -NoBuild
```

Surefire/Failsafe e Checkstyle mantêm o fluxo existente. A suíte local tem 356 casos, incluindo 55 novos: 14 de decisão pura, 6 de esquema, 20 de processamento e 15 de ciclo de vida. Testes PostgreSQL usam bancos descartáveis, barreiras para concorrência e falhas SQL controladas para provar rollback. Reinício encerra e reabre o contexto Spring com o banco preservado. Os relatórios ficam em `target/`; o smoke usa projeto Compose próprio e preserva o banco do operador.

[Spec](../.specs/features/mon-07-incidentes/spec.md), [design](../.specs/features/mon-07-incidentes/design.md), [tasks](../.specs/features/mon-07-incidentes/tasks.md) e [evidências](../.specs/features/mon-07-incidentes/evidence.md) relacionam MON-07-AC01 a MON-07-AC08 a assertions e comandos executados. A [validação independente](../.specs/features/mon-07-incidentes/validation.md) concluiu PASS 8/8: 356 testes, zero falhas/erros/skips/Checkstyle, runtime e smoke aprovados e nove mutações detectadas, nenhuma sobrevivente.
