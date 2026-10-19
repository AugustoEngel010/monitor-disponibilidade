# API administrativa de monitores

As consultas MON-09 de verificações, incidentes, notificações e resumo estão em [Histórico e resumo](queries.md).

Contrato MON-04. Base `/api/admin/monitors`. Todas as rotas exigem HTTP Basic do operador. Escritas exigem também cookie `JSESSIONID` e `X-CSRF-TOKEN` obtidos em `GET /api/admin/csrf`; o fluxo e a rotação estão no [README](../README.md). Sem Basic válido: 401. Com Basic válido, escrita sem CSRF válido: 403. Essas respostas têm corpo vazio.

| Método / rota | Sucesso | Operação |
| --- | --- | --- |
| POST /api/admin/monitors | 201, monitor completo e Location | Cadastrar |
| GET /api/admin/monitors/{id} | 200, monitor completo | Consultar UUID |
| PUT /api/admin/monitors/{id} | 200, monitor completo | Substituir configuração |
| POST /api/admin/monitors/{id}/pause | 200, monitor completo | Pausar; repetição preserva estado e timestamps |
| POST /api/admin/monitors/{id}/resume | 200, monitor completo | Retomar; repetição preserva estado e timestamps |
| GET /api/admin/monitors?page=0&size=20 | 200, página | Listar por createdAt ASC, id ASC |

UUID válido inexistente retorna 404 `MONITOR_NOT_FOUND` em consulta e mutações. UUID malformado retorna 400 com erro `id`. Não existe exclusão ou PATCH parcial.

POST e PUT recebem a mesma configuração:

```json
{
  "name": "API de exemplo",
  "url": "https://example.org/health",
  "intervalSeconds": 60,
  "timeoutSeconds": 5,
  "expectedStatus": 200,
  "failureThreshold": 3,
  "recoveryThreshold": 2,
  "alertRecipient": "ops@example.org"
}
```

| Campo | Limites | Se omitido ou null |
| --- | --- | --- |
| name | Trim, 1–100 caracteres; duplicados permitidos | 400 |
| url | Trim, até 2048; HTTP/HTTPS absoluto, host; porta explícita 1–65535 | 400 |
| intervalSeconds | Inteiro 10–3600 | 60 |
| timeoutSeconds | Inteiro 1–30, estritamente menor que intervalo | 5 |
| expectedStatus | Inteiro 200–599 | 200 |
| failureThreshold | Inteiro 1–10 | 3 |
| recoveryThreshold | Inteiro 1–10 | 2 |
| alertRecipient | Trim, um e-mail ASCII de até 254 caracteres | 400 |

URL não aceita userinfo, query ou fragmento, inclusive vazios. Após trim, a URL é preservada exatamente; alteração textual constitui mudança de destino. HTTP/HTTPS não diferenciam maiúsculas no esquema. Exemplos aceitos: `https://example.org/health`, `http://localhost:8080/a`, `http://[::1]:8080/a`. Exemplos rejeitados: `ftp://example.org`, `https://user:password@example.org`, `https://example.org?q=token`, `https://example.org#fragment`, `http://example.org:0`. A API valida sintaxe sem resolver DNS ou acessar destinos; a política de rede será aplicada na execução em MON-05.

E-mail usa local ASCII dot-atom de até 64 caracteres, domínio com pelo menos duas labels DNS de 1–63 caracteres cada, sem hífen no início/fim. `ops+monitor@example.org` é aceito. Display name, lista de endereços, local entre aspas, espaços e pontos consecutivos no local são rejeitados. A validação não verifica a existência da caixa postal.

Números decimais, strings numéricas e booleanos são rejeitados no campo. Campos desconhecidos são rejeitados. JSON inválido, vazio, null ou sem objeto retorna erro `body`. A resposta 400 contém mensagens fixas sem ecoar valores enviados:

```json
{
  "code": "VALIDATION_ERROR",
  "errors": {
    "timeoutSeconds": "must be less than intervalSeconds"
  }
}
```

Erros de configuração são acumulados por campo. Nada é persistido quando a configuração é inválida. O limite é 100 monitores, incluindo pausados: o 101º cadastro retorna 409 `MONITOR_LIMIT`. Cadastro concorrente também respeita esse limite.

```json
{
  "code": "OPEN_INCIDENT",
  "errors": {}
}
```

Esse 409 ocorre ao alterar URL, intervalo, timeout, código esperado ou limiares com `openIncidentId` preenchido. O PUT inteiro é rejeitado, inclusive se também alterar nome. Nome e destinatário podem mudar durante incidente e preservam versão, contadores, estado e evidência. Notificações existentes deverão manter seu destinatário congelado, conforme MON-08.

Sem incidente, uma alteração de destino/política incrementa `configurationVersion` e `executionGeneration`, zera sequências e horários provisórios, torna `UNKNOWN` e invalida a evidência corrente. Um monitor ativo fica elegível imediatamente; um pausado espera a retomada. Reenviar a configuração normalizada idêntica não altera nada, inclusive `updatedAt`.

Pausa efetiva torna `PAUSED`, zera sequências, invalida evidência corrente e remove `nextCheckAt`. Retomada efetiva torna `ACTIVE`, zera sequências, invalida evidência corrente e define `nextCheckAt` imediatamente. Ambas incrementam somente `executionGeneration`, preservam estado confirmado, incidente, versão de configuração e timestamp histórico da última observação. Pausa não representa recuperação. Repetir a ação na condição já atingida não incrementa a geração nem altera timestamps.

O cadastro retorna configuração mais:

```json
{
  "id": "00000000-0000-0000-0000-000000000001",
  "lifecycle": "ACTIVE",
  "confirmedState": "UNKNOWN",
  "evidence": "NEVER_OBSERVED",
  "recentObservation": false,
  "lastObservedAt": null,
  "openIncidentId": null,
  "failureCount": 0,
  "recoveryCount": 0,
  "configurationVersion": 1,
  "executionGeneration": 1,
  "nextCheckAt": "2026-10-13T12:00:00Z",
  "createdAt": "2026-10-13T12:00:00Z",
  "updatedAt": "2026-10-13T12:00:00Z"
}
```

Instantes são UTC. A configuração também aparece no mesmo nível do objeto. `lastObservedAt` preserva o último timestamp histórico após suspensão da cobertura; ele sozinho não prova frescor. `evidence` é `NEVER_OBSERVED` sem observação válida na versão/período ativo corrente; `FRESH` quando a idade está entre zero e `2 × intervalo + timeout` inclusive; `STALE` fora dessa faixa. `recentObservation` é true somente em FRESH. Pausa/retomada exigem nova evidência para voltar a FRESH. Um incidente aberto permanece visível mesmo com evidência antiga ou monitor pausado.

Listagem retorna `{items, page, size, totalElements, totalPages}`. `items` contém os mesmos objetos da consulta individual. `page` começa em zero (até 2147483647); `size` aceita 1–100. Defaults são 0/20; parâmetros vazios, negativos, decimais, não numéricos ou fora dos limites retornam 400 por campo. Banco vazio retorna items=[], totalElements=0 e totalPages=0. Página além do fim mantém totais reais e items=[]. Itens e totais usam o mesmo snapshot transacional; alterações entre requisições podem mudar a composição de páginas. Não há ordenação configurável.

MON-04 persiste configuração e estado administrativo. Ainda não executa verificações nem cria incidentes: os testes de preservação usam fixtures de histórico e referências de incidente. MON-06 deverá adquirir o mesmo lock de linha e comparar versão/geração ao aplicar resultados, além de solicitar cancelamento da tentativa invalidada. As migrações posteriores deverão adicionar as referências reais de incidente/histórico sem reescrever V1/V2.

Validação pelo fluxo existente:

```sh
docker compose run --build --rm tests
docker compose build app
```

No Windows, o smoke isolado de startup, Basic, rotação, migrações e persistência é `powershell -NoProfile -ExecutionPolicy Bypass -File scripts/validate-compose.ps1 -NoBuild`. Relatórios ficam em `target/surefire-reports`, `target/failsafe-reports` e `target/checkstyle-result.xml`.
