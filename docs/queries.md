# Histórico e resumo do monitor

As consultas MON-09 são GETs com HTTP Basic, `Cache-Control: no-store` e sem efeitos de escrita. O fluxo CSRF das operações administrativas permanece vigente. IDs são UUIDs canônicos. Timestamps são instantes UTC com precisão de microssegundos no PostgreSQL; converta para o fuso local somente na apresentação.

Prefixo `/api/admin/monitors/{id}`:

| Rota | Filtro específico | Instante do período | Ordem fixa |
| --- | --- | --- | --- |
| `/checks` | `outcome` | finishedAt | finishedAt DESC, executionId DESC |
| `/incidents` | `status=OPEN` ou `CLOSED` | downConfirmedAt | downConfirmedAt DESC, id DESC |
| `/notifications` | `status`, `type=DOWN` ou `RECOVERY` | confirmedAt da transição | confirmedAt DESC, id DESC |
| `/summary` | Somente from/to | Término para métricas; estado é atual | Não paginado |

`outcome`: SUCCESS, HTTP_FAILURE, TIMEOUT, DNS_FAILURE, CONNECTION_FAILURE, TLS_FAILURE, INTERNAL_ERROR, DESTINATION_BLOCKED, CANCELLED. Status de notificação: PENDING, SENDING, ACCEPTED, EXHAUSTED. Seu período filtra a transição, não cada retry. Incidentes filtram confirmação da queda, não sobreposição; um incidente aberto antes de `from` continua visível em `summary.currentIncident`.

Listas aceitam `page=0` (0..2147483647) e `size=20` (1..100), inteiros estritos. Página excedente retorna `items=[]` com totais reais. UUID desempata horários iguais entre páginas. Cada resposta usa snapshot REPEATABLE_READ para existência, totais, itens e tentativas. Páginas em requisições distintas não congelam inserções futuras: novos registros podem deslocar offsets.

## Janela e erros

`from`/`to`: ISO-8601 com offset, como `2026-10-18T09:00:00-03:00`, equivalente a `2026-10-18T12:00:00Z`. Encode `+` como `%2B` em URLs. Janela **[from,to)**: início inclusivo, fim exclusivo, `from < to <= agora`. Default `to=agora`; default `from=to-24h`. Timestamps explicitamente fornecidos devem estar entre 0001-01-02T00:00:00Z e 9999-12-30T00:00:00Z. Defaults preservam a janela de 24h: com to=0001-01-02T00:00:00Z e from omitido, from=0001-01-01T00:00:00Z, ainda representável no PostgreSQL. Frações menores que microssegundo são rejeitadas para preservar os limites.

Campo inválido, UUID malformado, filtro desconhecido/repetido ou inteiro fora do limite retorna 400 por campo, sem ecoar inputs:

```json
{"code":"VALIDATION_ERROR","errors":{"size":"must be an integer between 1 and 100"}}
```

UUID válido inexistente: HTTP 404 com `{"code":"MONITOR_NOT_FOUND","errors":{}}`. Sem Basic válido: 401, challenge Basic, corpo vazio. Falhas de banco são erros de infraestrutura e não viram resultados de monitoramento.

Mensagens fixas: desconhecido `unknown parameter`; repetido `must occur once`; UUID `must be a UUID`; page/size `must be an integer between 0 and 2147483647` / `must be an integer between 1 and 100`; timestamp/calendário/precisão `must be an ISO-8601 timestamp with offset and at most microsecond precision`; ordem `must precede to`; futuro `must not be in the future`; outcome `must be a check outcome`; status `must be a resource status`; type `must be DOWN or RECOVERY`. Desconhecido tem prioridade sobre conteúdo; repetição de campo conhecido tem prioridade sobre formato; relações da janela não substituem erro já identificado no campo.

## Verificações

`GET /api/admin/monitors/{id}/checks?from=2026-10-18T12:00:00Z&to=2026-10-18T13:00:00Z&outcome=HTTP_FAILURE&size=1`:

```json
{
  "items": [{
    "monitorId":"00000000-0000-0000-0000-000000000001",
    "executionId":"00000000-0000-0000-0000-000000000010",
    "configurationVersion":1,"executionGeneration":1,
    "startedAt":"2026-10-18T12:00:00Z","finishedAt":"2026-10-18T12:00:00.100Z",
    "durationNanos":100000000,"outcome":"HTTP_FAILURE","httpStatus":503,
    "errorMessage":"Unexpected HTTP status"
  }],
  "page":0,"size":1,"totalElements":1,"totalPages":1
}
```

Inclui resultados não aplicados, versões antigas e não externos, preservando o snapshot de cada execução. Não retorna corpo HTTP nem headers de destino.

## Incidentes

`GET /api/admin/monitors/{id}/incidents?status=CLOSED`:

```json
{
  "items": [{
    "id":"00000000-0000-0000-0000-000000000020",
    "monitorId":"00000000-0000-0000-0000-000000000001",
    "configurationVersion":1,"status":"CLOSED",
    "firstFailureAt":"2026-10-18T12:00:00.100Z","downConfirmedAt":"2026-10-18T12:02:00Z",
    "recoveryStartedAt":"2026-10-18T12:03:01Z","recoveredAt":"2026-10-18T12:04:00Z",
    "estimatedDurationSeconds":180.9,"ageSeconds":null
  }],
  "page":0,"size":20,"totalElements":1,"totalPages":1
}
```

Primeira falha e início da recuperação são observações; confirmação da queda e da recuperação são instantes das amostras nos limiares. Duração final estimada = `recoveryStartedAt - firstFailureAt`, em segundos decimais sem truncar frações. OPEN tem `estimatedDurationSeconds=null`, `recoveredAt=null` e `ageSeconds=agora-firstFailureAt`. `recoveryStartedAt` pode existir durante confirmação. Idade/duração incluem pausas e lacunas, sem comprovar indisponibilidade contínua. A janela e a pausa não encerram incidentes.

## Notificações

`GET /api/admin/monitors/{id}/notifications?status=PENDING&type=DOWN`:

```json
{
  "items": [{
    "id":"00000000-0000-0000-0000-000000000030",
    "monitorId":"00000000-0000-0000-0000-000000000001",
    "transitionId":"00000000-0000-0000-0000-000000000040",
    "incidentId":"00000000-0000-0000-0000-000000000020",
    "type":"DOWN","confirmedAt":"2026-10-18T12:02:00Z",
    "recipient":"ops@example.org","status":"PENDING","attemptCount":1,
    "nextAttemptAt":"2026-10-18T12:02:32Z","completedAt":null,
    "attempts":[{
      "attemptNumber":1,"startedAt":"2026-10-18T12:02:01Z","finishedAt":"2026-10-18T12:02:02Z",
      "outcome":"FAILED","errorCode":"SMTP_FAILURE"
    }]
  }],
  "page":0,"size":20,"totalElements":1,"totalPages":1
}
```

Tentativas ordenadas por número, máximo cinco. SENDING tem fim/erro null; ACCEPTED tem erro null; FAILED/UNCERTAIN expõem somente SMTP_FAILURE, SMTP_UNCERTAIN ou RESTART_UNCERTAIN. IDs e destinatário preservam o conteúdo congelado. ACCEPTED significa aceitação SMTP. Consulte [retries e confirmação incerta](notifications.md).

## Resumo

`GET /api/admin/monitors/{id}/summary?from=2026-10-18T12:00:00Z&to=2026-10-18T12:05:00Z`:

```json
{
  "monitorId":"00000000-0000-0000-0000-000000000001","generatedAt":"2026-10-18T12:05:00Z",
  "window":{"from":"2026-10-18T12:00:00Z","to":"2026-10-18T12:05:00Z","timeZone":"UTC"},
  "lifecycle":"ACTIVE","confirmedState":"UP","evidence":"FRESH","recentObservation":true,
  "label":"CONFIRMANDO_QUEDA","lastObservedAt":"2026-10-18T12:03:20Z",
  "lastObservation":{
    "monitorId":"00000000-0000-0000-0000-000000000001",
    "executionId":"00000000-0000-0000-0000-000000000010",
    "configurationVersion":1,"executionGeneration":1,
    "startedAt":"2026-10-18T12:03:19.900Z","finishedAt":"2026-10-18T12:03:20Z",
    "durationNanos":100000000,"outcome":"HTTP_FAILURE","httpStatus":503,
    "errorMessage":"Unexpected HTTP status"
  },
  "failureCount":1,"recoveryCount":0,"configurationVersion":1,"executionGeneration":1,
  "nextCheckAt":"2026-10-18T12:04:19.900Z","currentIncident":null,
  "statistics":{
    "metricType":"CHECK_SUCCESS_SAMPLING","dataStatus":"HAS_DATA",
    "totalChecks":13,"validSamples":10,"successSamples":8,"latencySamples":8,
    "successRatePercent":80.00,"averageResponseTimeMs":100.00
  },
  "coverageKnownFrom":"2026-10-18T11:00:00Z",
  "noObservationPeriods":[{"from":"2026-10-18T12:02:05Z","to":"2026-10-18T12:03:20Z"}]
}
```

Exemplo com oito sucessos de 100ms, duas falhas externas e três não externos. Observações elegíveis em 0s e 200s com L=125s explicam a lacuna. Horário vencido em `nextCheckAt` indica atraso, não falha. Estado é o atual em `generatedAt`, mesmo para janela antiga. `lastObservedAt`/`lastObservation` preservam a última observação aplicada externa, inclusive após pausa/retomada; validade corrente aparece em `evidence`. Sem registro aplicado associado ao timestamp preservado, `lastObservation=null`. Empates no timestamp usam UUID DESC, como no histórico.

Prioridade de `label`: PAUSED; SEM_DADOS (sem observação corrente); SEM_DADOS_RECENTES (stale); CONFIRMANDO_QUEDA (UNKNOWN/UP com falhas); CONFIRMANDO_RECUPERACAO (DOWN com sucessos); UP; DOWN. Contador saturado de recuperação em UP não indica confirmação. `currentIncident` usa o mesmo formato dos incidentes e permanece visível mesmo pausado ou stale.

`evidence`: NEVER_OBSERVED, FRESH ou STALE. Frescor L=`2 × intervalo + timeout`; idade 0..L inclusiva é recente, L+1µs e timestamps futuros são STALE. Pausa/retomada invalidam a observação corrente. Estado confirmado e incidente permanecem; pausa ou dados antigos não significam recuperação.

## Cálculos

Taxa = `100 × SUCCESS / validSamples`. Denominador: SUCCESS, HTTP_FAILURE, TIMEOUT, DNS_FAILURE, CONNECTION_FAILURE e TLS_FAILURE terminados na janela, em todas as versões/gerações do histórico bruto. INTERNAL_ERROR, DESTINATION_BLOCKED e CANCELLED só entram em `totalChecks`. Oito sucessos e duas falhas externas geram dez amostras e **80%**. CHECK_SUCCESS_SAMPLING identifica **métrica por amostragem**, sem alegar uptime temporal. Duração de incidente é separada.

Latência média = média de `durationNanos / 1000000` somente de SUCCESS com duração monotônica não negativa. Zero medido é válido; CANCELLED com zero desconhecido é excluído. O schema rejeita durações negativas. A medição termina nos cabeçalhos, sem corpo. Oito sucessos de 100ms geram **100ms** com `latencySamples=8`. Percentual/média usam aritmética decimal e HALF_UP com duas casas somente na saída, sem arredondar cada amostra.

Sem verificações externas: NO_DATA, validSamples=0, successSamples=0, successRatePercent=null. Sem sucessos válidos: latencySamples=0, averageResponseTimeMs=null. Janela só de falhas pode ter taxa 0% e latência null. Zero não substitui ausência de dados.

## Cobertura e limites históricos

Cada resultado externo APPLIED compatível com sua época ACTIVE fornece `[finishedAt,finishedAt+L)`, cortado pela pausa ou mudança de política seguinte. Retomada exige nova observação. A união inclui coberturas iniciadas antes de `from`; `noObservationPeriods` é seu complemento na janela. Janela sem amostras pode ter cobertura de observação anterior, mantendo taxa null. O ponto L é recente no resumo, mas não adiciona duração à cobertura semiaberta.

I=60s/T=5s, observações 0s/200s, janela [0,300): lacuna [125,200). Observação 0s, pausa 30s, retomada 40s e observação 60s: lacuna [30,60). Nome/destinatário e reinício não interrompem épocas. Nova política preserva L anterior nas coberturas antigas. Resultado sem aplicação, HISTORY_ONLY ou tardio não cria cobertura. Lacunas não criam sucessos ou falhas artificiais.

V7 registra épocas na mesma transação administrativa via trigger. No upgrade, geração 1 é conhecida desde createdAt; gerações posteriores não têm limites antigos reconstruíveis, então `coverageKnownFrom` é o instante da migração. Antes disso não se afirma cobertura. Esse limite de histórico é informado separadamente das lacunas de execução. MON-09 não remove dados; não existe corte de retenção implementado. MON-11 deve informar seu limite separadamente e preservar épocas/referências necessárias. V1–V6 não são editadas.

## Verificação

```powershell
docker compose run --build --rm tests
docker compose build app
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/validate-compose.ps1 -NoBuild
```

QueryParametersTest: validação. ObservationEpochIT: upgrade/transações. HistoryQueriesIT: filtros/paginação/empates. SummaryQueriesIT: estados/cálculos/cobertura. QueryApiIT: quatro rotas e erros HTTP reais. PostgreSQL Testcontainers descartável; sem Maven/JDK no host.

[Spec](../.specs/features/mon-09-consultas/spec.md), [design](../.specs/features/mon-09-consultas/design.md), [tasks](../.specs/features/mon-09-consultas/tasks.md), [evidências](../.specs/features/mon-09-consultas/evidence.md).

[Validação independente](../.specs/features/mon-09-consultas/validation.md): PASS nos sete critérios de aceite e nove requisitos, 459 testes sem falhas/erros/skips/Checkstyle, runtime e smoke 37 PASS; cinco mutações detectadas. A primeira revisão encontrou gaps de payload/mensagens, corrigidos e revalidados. Evidências locais; CI remota não executada.
