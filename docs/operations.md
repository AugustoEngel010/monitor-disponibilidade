# Operação e retenção

MON-11 mantém instância única, PostgreSQL/Flyway e os workers HTTP/SMTP existentes. Build, testes, runtime e capacidade continuam em Docker. [Spec](../.specs/features/mon-11-operacao/spec.md), [tasks](../.specs/features/mon-11-operacao/tasks.md), [evidências](../.specs/features/mon-11-operacao/evidence.md) e [capacidade medida](capacity/README.md).

## Saúde e diagnóstico

```powershell
docker compose ps
curl.exe http://localhost:8080/actuator/health
curl.exe http://localhost:8080/actuator/health/liveness
curl.exe http://localhost:8080/actuator/health/readiness
docker compose logs --tail 100 app
```

Os três probes aceitam GET/HEAD sem credencial e retornam somente `{"status":"UP"}` ou `{"status":"DOWN"}`, sem detalhes, componentes ou sessão. Liveness consulta somente o estado do processo; perder PostgreSQL não deve reiniciar um processo vivo. Readiness inclui PostgreSQL: indisponibilidade produz HTTP 503/DOWN, enquanto liveness continua HTTP 200/UP. Configuração/migração inválida impede startup. SMTP não participa da readiness: entrega é assíncrona e sua falha aparece na outbox. Um processo UP não prova que seus alvos estão disponíveis.

Métricas exigem o mesmo Basic administrativo. Substitua o usuário abaixo pelo configurado localmente; curl solicita a senha, evitando colocá-la na linha de comando:

```powershell
curl.exe --user '<seu-usuario>' http://localhost:8080/actuator/metrics
curl.exe --user '<seu-usuario>' http://localhost:8080/actuator/metrics/monitor.checks.in.flight
curl.exe --user '<seu-usuario>' http://localhost:8080/actuator/metrics/monitor.checks.capacity
curl.exe --user '<seu-usuario>' http://localhost:8080/actuator/metrics/monitor.checks.scheduling.delay
curl.exe --user '<seu-usuario>' 'http://localhost:8080/actuator/metrics/monitor.checks.results?tag=outcome:TIMEOUT'
curl.exe --user '<seu-usuario>' 'http://localhost:8080/actuator/metrics/monitor.checks.duration?tag=outcome:SUCCESS'
curl.exe --user '<seu-usuario>' 'http://localhost:8080/actuator/metrics/monitor.notifications?tag=status:PENDING'
curl.exe --user '<seu-usuario>' 'http://localhost:8080/actuator/metrics/monitor.notifications?tag=status:SENDING'
curl.exe --user '<seu-usuario>' 'http://localhost:8080/actuator/metrics/monitor.notifications?tag=status:EXHAUSTED'
```

Use a porta APP_PORT quando diferente de 8080. API/painel e outros actuators continuam administrativos; escritas continuam exigindo Basic e CSRF. Não foram expostos env, loggers ou um endpoint de exclusão.

| Métrica | Significado | Tags |
| --- | --- | --- |
| monitor.checks.in.flight | Workers reais ocupados, incluindo conclusão/persistência; só existe com scheduler habilitado | Nenhuma |
| monitor.checks.capacity | Limite C configurado, padrão4, permitido 1..16 | Nenhuma |
| monitor.checks.scheduling.delay | Timer do início lógico menos vencimento, nunca negativo | Nenhuma |
| monitor.checks.duration | Timer da duração monotônica produzida pelo executor | outcome |
| monitor.checks.results | Contador dos resultados concluídos pelo scheduler | outcome |
| monitor.notifications | Contagem corrente persistida da outbox | status=PENDING/SENDING/EXHAUSTED |

`outcome` aceita somente SUCCESS, HTTP_FAILURE, TIMEOUT, DNS_FAILURE, CONNECTION_FAILURE, TLS_FAILURE, INTERNAL_ERROR, CANCELLED e DESTINATION_BLOCKED. Sem URL, UUID, nome ou destinatário em tags. Timers expõem count/total/max em unidades informadas pelo Actuator; no registro padrão os tempos aparecem em segundos. Máximos têm janela de expiração. Contadores/timers reiniciam com o processo e são sinais operacionais, não auditoria transacional ou substitutos das consultas persistentes. Reconciliação de reservas não reconstrói contadores passados. Outbox é consultada a cada coleta; falha SQL produz NaN/valor desconhecido, nunca backlog zero fictício.

Se inFlight estiver em C e o atraso subir, a carga supera a capacidade atual. Consulte TIMEOUT e a distribuição de duração antes de alterar C; preserve 1..16 e I/T da MON-01. PENDING crescente indica entrega/retry atrasado; SENDING persistente após processo parado requer reconciliação pela próxima instância, com a anterior totalmente encerrada. EXHAUSTED exige análise operacional; não há reenvio automático além de cinco tentativas. Consulte [notificações](notifications.md). O [cenário medido](capacity/README.md) demonstra saturação sem exceder C.

## Logs estruturados

O console da aplicação usa JSON ECS nativo do [Spring Boot 3.5](https://docs.spring.io/spring-boot/3.5/reference/features/logging.html). Eventos operacionais usam campos tipados `event`, `monitorId`, `executionId`, `incidentId`, `notificationId` e `outcome`, além de timestamp/nível/thread do logger. Campos sem contexto ficam null. UUIDs são campos de log, nunca tags de métrica.

| Evento | Momento e correlação |
| --- | --- |
| CHECK_RECORDED | Resultado novo persistido; monitorId/executionId/outcome |
| INCIDENT_TRANSITION | Transição e intenção confirmadas; os quatro UUIDs, DOWN/RECOVERY |
| NOTIFICATION_CLAIMED | Reserva durável; os quatro UUIDs |
| NOTIFICATION_FINISHED | Conclusão/retry durável; os quatro UUIDs e resultado fixo |
| RETENTION_COMPLETED | Lote confirmado, historyAvailableFrom, contagens removidas e more |
| RETENTION_FAILED | Falha sanitizada; lote revertido, próxima rodada pode repetir |

Eventos confirmados são emitidos após commit; rollback não produz confirmação. Não incluem nome/URL do alvo, destinatário, credenciais, Authorization, headers, corpo HTTP, resposta SMTP ou mensagem/stack de exceção externa. Erros HTTP e SMTP permanecem nos vocabulários fixos MON-05/08. O logger de health JDBC que imprime causas fica desabilitado; diagnóstico usa status e os sinais acima. Mantenha níveis padrão e não habilite wire logs de HTTP/SMTP com dados reais. Logs de infraestrutura/startup não são eventos de negócio e não substituem a outbox.

Exemplo de correlação no PowerShell, depois de obter um executionId nas consultas:

```powershell
$executionId = '<uuid-da-execucao>'
docker compose logs --no-color --no-log-prefix app | ForEach-Object {
    try {
        $entry = $_ | ConvertFrom-Json
        if ($entry.executionId -eq $executionId) { $entry }
    } catch { }
}
```

Não use `docker compose config` sem `--quiet` nem imprima o ambiente para diagnosticar credenciais. Consulte [segurança e rotação](../README.md).

## Política de retenção

| Variável | Padrão | Limite / efeito |
| --- | --- | --- |
| MONITOR_RETENTION_ENABLED | true | false suspende somente o runtime de limpeza |
| MONITOR_RETENTION_DAYS | 30 | Inteiro 1..3650; dias UTC de 24h |
| MONITOR_RETENTION_BATCH_SIZE | 1000 | Inteiro 1..10000 por grupo em cada transação |
| MONITOR_RETENTION_POLL_MS | 3600000 | Inteiro 1000..86400000; espera inicial e entre rodadas |

Uma rodada drena lotes sucessivos enquanto algum grupo preencher seu limite. Cada lote confirma e libera locks antes do próximo; não fica limitado a mil exclusões por hora. Shutdown interrompe a continuação entre lotes. As transações usam lock_timeout 2s e statement_timeout 5s; falha reverte lote e watermark e registra RETENTION_FAILED. A próxima rodada pode retomar. Configuração numérica inválida impede startup com mensagem genérica.

Ajuste o `.env` e recrie app para carregar configurações:

```powershell
docker compose config --quiet
docker compose up -d --force-recreate app
```

O corte é `Clock UTC − days`, truncado a microssegundos, e compara **estritamente anterior**: igualdade permanece. O watermark durável nunca recua, mesmo se o relógio recuar ou o prazo aumentar. Aumentar o prazo não restaura histórico. `historyAvailableFrom=null` antes do primeiro lote confirmado. Depois, o limite significa histórico completo não garantido antes desse instante, mesmo que algum payload protegido ou lote ainda por consumir permaneça fisicamente no banco.

Ordem de limpeza e proteções:

1. Incidente somente com recoveredAt anterior ao corte e todas as suas entregas ACCEPTED/EXHAUSTED concluídas antes do corte. Remover tentativas, intenções, transições e incidente na mesma transação. Qualquer PENDING/SENDING ou conclusão recente preserva a cadeia inteira, inclusive a queda já terminal que antecede recuperação pendente.
2. Resultado somente com aplicação registrada e finishedAt anterior ao corte. Preservar reservas, transições ainda referenciadas, última observação e último APPLIED da política/geração corrente, inclusive resultados internos necessários à ordenação. Estado/contadores do monitor não são recalculados pela limpeza.
3. Registrar recibo mínimo antes de retirar aplicação/payload. Trigger impede reinserir o mesmo UUID, mesmo com timestamps alterados; processar resultado retirado não reaplica efeitos. Recibos mantêm idempotência e não expiram.
4. Época somente encerrada antes do corte e sem resultados ou reservas que a referenciem. A época corrente permanece.

Incidentes abertos, pendências, dados ainda não aplicados, evidências correntes e recibos podem persistir por mais que o prazo. A política controla payload/histórico elegível; não promete disco constante ou desaparecimento de todo registro antigo. PostgreSQL reutiliza espaço das exclusões via autovacuum; DELETE não garante redução imediata do arquivo físico. Não use VACUUM FULL como parte da limpeza normal.

Comandos SQL de leitura com os nomes padrão; ajuste usuário/banco se DB_USER/DB_NAME forem diferentes:

```powershell
docker compose exec db psql -U monitor -d monitor -c 'SELECT * FROM monitor.retention_state;'
docker compose exec db psql -U monitor -d monitor -c 'SELECT status,count(*),min(next_attempt_at) FROM monitor.notification_intents GROUP BY status;'
docker compose exec db psql -U monitor -d monitor -c 'SELECT count(*) FROM monitor.incidents WHERE recovered_at IS NULL;'
docker compose exec db psql -U monitor -d monitor -c 'SELECT count(*) FROM monitor.check_attempts;'
docker compose exec db psql -U monitor -d monitor -c 'SELECT count(*) FROM monitor.check_results r LEFT JOIN monitor.result_applications a USING(execution_id) WHERE a.execution_id IS NULL;'
docker compose exec db psql -U monitor -d monitor -c 'SELECT relname,n_live_tup,n_dead_tup,last_autovacuum FROM pg_stat_user_tables WHERE schemaname = $$monitor$$;'
docker compose exec db psql -U monitor -d monitor -c 'SELECT pg_size_pretty(pg_total_relation_size($$monitor.check_results$$)),pg_size_pretty(pg_total_relation_size($$monitor.retired_executions$$));'
```

As duas consultas finais usam dollar quoting do PostgreSQL; no PowerShell preserve as aspas simples externas. Não execute exclusões manuais para resolver pendências: elas podem romper referências e idempotência. Veja [histórico disponível e ausência de dados](queries.md).

## Validação reproduzível

```powershell
docker compose run --build --rm tests
docker compose build app
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/validate-compose.ps1 -NoBuild
docker compose -p mon11-panel --profile panel-tests run --use-aliases --build --rm panel-tests
docker compose -p mon11-panel --profile panel-tests down
docker compose -p mon11-capacity -f compose.yaml -f compose.capacity.yaml run --build --rm tests
```

Gates do autor: 482 Java (169 unitários/313 IT), 50 navegador, 37 smoke, Checkstyle 0 e runtime aprovado. Capacidade: 285 checks/60,07s, pico 4, zero sobreposições, atraso máximo20,63s e 50 notificações pendentes, depois esgotadas na etapa com relógio acelerado. Esses valores são locais; CI remota não foi executada. O [relatório independente](../.specs/features/mon-11-operacao/validation.md) registra a revisão final, suas evidências e o sensor de discriminação.
