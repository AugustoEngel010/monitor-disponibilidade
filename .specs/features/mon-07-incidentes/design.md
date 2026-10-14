# MON-07: design

**Spec:** [spec.md](spec.md). **Status:** execução autorizada.

## Architecture Overview

ScheduleStore → IncidentProcessor → IncidentStateMachine/IncidentRepository/MonitorRepository → PostgreSQL.
HTTP permanece fora de transação. O processador bloqueia monitor antes de ler marcador e aplica o registro bruto vencedor. A mesma transação registra marcador, estado, incidente, transição e intenção; somente então a reserva é removida. Reconciliação usa o resultado existente ou CANCELLED e o mesmo processamento. Administração sincroniza o horário provisório do incidente sob seu lock.

Alternativas: aplicar por listener assíncrono adicionaria fila/ordenação e uma janela de inconsistência; implementar em SQL tornaria testes puros e manutenção das regras mais difíceis. Serviço explícito com TransactionTemplate segue o padrão atual e mantém o lock único por monitor.

## Code Reuse Analysis

Monitor.State e MonitorRepository já persistem todos os contadores e horários necessários. CheckRepository preserva primeiro registro. ScheduleStore mantém reserva até conclusão. MonitorService serializa alterações administrativas. Flyway, Testcontainers, AssertJ, Checkstyle, Clock UTC e Compose permanecem sem dependências novas.

## Components

| Componente | Responsabilidade |
| --- | --- |
| IncidentStateMachine | Transformação pura de estado e classificação de transição NONE/DOWN/RECOVERY |
| IncidentRepository | Marcadores, incidentes, transições e intenções SQL |
| IncidentProcessor | Transação, lock, elegibilidade, deduplicação e persistência da decisão |
| ScheduleStore | Aplicação no finish/recover, antes da remoção da reserva |
| MonitorService | Limpar início provisório do incidente junto da pausa/retomada |

## Data Models

V5 cria incidents (UUID, monitor/version, quatro instantes), índice parcial UNIQUE monitor_id WHERE recovered_at IS NULL, FK composto da referência corrente do monitor; result_applications (execution_id PK/FK, disposition APPLIED/HISTORY_ONLY); state_transitions (UUID, executionId UNIQUE/FK, monitor/incidente/version, tipo e confirmação); notification_intents (UUID, transitionId/destinatário UNIQUE, snapshot de nome/URL/horários e PENDING). Marcadores antigos são HISTORY_ONLY, exceto resultados de reservas ainda pendentes. FKs são diferidas onde abertura/estado precisam confirmar juntos. Sem alteração nas migrações V1–V4.

## Error Handling Strategy

Falha JDBC propaga e rollback remove todos os efeitos da aplicação. Resultado bruto já confirmado permanece para retry. Repetição não toca horários. Histórico somente não muda monitor. Snapshot conflitante reutilizando UUID continua rejeitado por CheckRepository. Estado DOWN sem incidente real é inválido; fixtures anteriores devem passar a criar evidência de incidente real.

## Risks & Concerns

| Concern | Location | Impacto | Mitigação |
| --- | --- | --- | --- |
| Fixtures antigas usam incidente fictício | ScheduleStoreIT, MonitorApiIT, MonitorRepositoryIT | FK nova rejeitaria fixture | Criar incidente real e manter assertions existentes |
| Contrato anterior adia aplicação | ScheduleStoreIT:160 | SUCCESS existente passará a limpar falhas | Atualização de cenário para MON-07, sem reduzir cobertura de reinício/histórico |
| Truncates e smoke assumem V4 | Testes e scripts/validate-compose.ps1:124 | Gate falharia com tabelas filhas/V5 | Expandir limpeza e histórico exato para cinco migrações |
| Resultado registrado antes do finish | CheckService/CheckScheduler | Crash perde aplicação se reserva removida primeiro | Aplicar antes de remover; retry por reconciliação |
| Horário de executor difere de início lógico | ScheduleStore.Attempt | Lacuna falsa sob atraso de despacho | Usar started_at da reserva na comparação |

## Tech Decisions

Sem nova biblioteca ou arquitetura. Intenção mínima congelada atende AD-002 sem implementar envio. AD-009 registrará contrato de aplicações/transições duráveis e retomada para MON-08/09/11.
