# MON-11: design

## Architecture Overview

Reutilizar Actuator/Micrometer embarcados, Clock UTC, JDBC/Flyway e workers existentes. Alternativas: broker/exportador externo acrescentariam infraestrutura; apagar em cascata destruiria trabalho. Limpeza JDBC transacional limitada, com locks do monitor, mantém a arquitetura.

## Code Reuse Analysis

CheckScheduler fornece mapa real de workers e snapshot de agendamento. NotificationStore fornece estados duráveis e chaves correlacionáveis. TransactionSynchronization emite eventos somente após commit. HistoryQueries/SummaryQueries mantêm REPEATABLE_READ.

## Components

- Métricas de scheduler e gauges SQL da outbox; tags somente Outcome/status de enum.
- OperationalEvents: eventos com UUIDs e códigos fixos, JSON ECS nativo do Boot.
- RetentionService: configuração validada, lote transacional, recibos de idempotência e watermark; runtime dedicado após ApplicationReadyEvent.
- Consultas: metadados de retenção e exclusão de amostras/cobertura anteriores ao watermark; painel informa o limite.
- CapacityScenario (perfil Maven capacity): cenário explícito opt-in, servidor HTTP local, banco descartável, scheduler e SMTP falho/recuperado controlados.

## Data Models

V8 adiciona retention_state singleton com history_available_from nullable, retired_executions UUID PK e índices temporais. Migrações V1–V7 intactas. Cadeias de incidentes só expiram após recuperação e conclusão de todas as notificações antes do corte. Os recibos mínimos permanecem sem TTL por exigência de idempotência; dados volumosos expiram. O último APPLIED da geração corrente e última observação permanecem como evidência operacional, excluídos dos cálculos históricos anteriores ao watermark.

## Error Handling Strategy

Configuração inválida impede startup. Limpeza com falha reverte todo lote/watermark e será repetida na próxima rodada. Gauge sem PostgreSQL produz NaN, não zero. Não registrar exceção bruta nos eventos operacionais. Probes mantêm status-only.

## Risks & Concerns

| Concern | Local | Mitigação |
| --- | --- | --- |
| FK e deduplicação | CheckRepository/IncidentRepository | Recibos antes de remover aplicações/resultados, rejeição de replay e preservação do último APPLIED |
| Causalidade da recuperação | NotificationStore.claim | Remover cadeia inteira somente quando todas as entregas são terminais antigas |
| Dados protegidos anteriores ao corte | SummaryQueries.gaps | Filtrar explicitamente finishedAt pelo watermark |
| Locks de limpeza | RetentionService | Lote finito, lock_timeout/statement_timeout e ordem estável |
| Crescimento dos recibos | retired_executions | Documentar bytes/linha medidos e limite da retenção; não alegar disco estritamente constante |
| Logs de bibliotecas com Throwable | Boot health/JDBC/SMTP | Eventos de domínio com allowlist; suprimir logger de health DB que imprime causa, manter diagnóstico via status/eventos |

## Tech Decisions

JSON ECS usa logging.structured.format.console=ecs e SLF4J fluent key-values, conforme [Spring Boot 3.5 logging](https://docs.spring.io/spring-boot/3.5/reference/features/logging.html). Timers usam duração persistida monotônica e unidade explícita conforme [Micrometer](https://docs.micrometer.io/micrometer/reference/concepts/timers.html). Não adicionar dependências.
