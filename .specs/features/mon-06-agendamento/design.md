# MON-06: design

**Spec:** [spec.md](spec.md). **Status:** execução autorizada pelo pedido.

## Architecture Overview

CheckScheduler faz polling, reconcilia reservas sem worker, interrompe gerações inválidas e despacha até C threads virtuais. ScheduleStore coordena transações JDBC; MonitorRepository seleciona linhas vencidas com FOR UPDATE SKIP LOCKED. CheckService executa HTTP fora da transação e grava o resultado MON-05. A reserva é removida depois da gravação. HTTP nunca mantém lock de banco.

Alternativas consideradas: somente mapa em memória não registra tentativa interrompida; fila de futures ilimitada acumula trabalho e atrasa pausa. Reserva persistente mais mapa limitado mantém o contrato de instância única e permite cancelamento pela thread real, sem liberar capacidade antecipadamente por Future.cancel.

## Code Reuse Analysis

MonitorRepository/MonitorService fornecem lock e Clock. Monitor.evidence e MonitorController já representam frescor. CheckService/CheckRepository preservam resultado por UUID e flag de interrupção. Flyway/Testcontainers/AssertJ/Checkstyle e comandos Docker seguem intactos.

## Components

| Componente | Responsabilidade |
| --- | --- |
| MonitorRepository.due | Seleção limitada/ordenada, lock por monitor |
| ScheduleStore | Reserva e avanço de horário atômicos, conclusão e recuperação idempotentes |
| CheckScheduler | Mapa máximo C, despacho sem fila, cancelamento por geração, shutdown |
| SchedulerRuntime | Poll Spring após startup, configuração obrigatoriamente válida, shutdown antes do datasource |

## Data Models

V4 cria monitor.check_attempts: monitor_id PK/FK, execution_id UUID UNIQUE, configuration_version/execution_generation, scheduled_at e started_at. Só reservas ainda não concluídas ocupam a tabela; resultado permanece em check_results. Índice parcial monitors(next_check_at,id) WHERE ACTIVE. Seleção exclui monitor com reserva mesmo após retomada. scheduled_at preserva vencimento original para diagnóstico de atraso; next_check_at continua na API.

## Error Handling Strategy

Claim falho faz rollback. Exceção interna do executor gera INTERNAL_ERROR; falha JDBC não gera falha externa, mantém reserva até reconciliação. Reconciliação usa primeiro resultado persistido se existir; caso contrário CANCELLED com duração 0 desconhecida. Limpa sequências de tentativa interrompida sob lock somente na geração corrente. Não altera agendamento, evidência, estado confirmado ou incidente. Pausa/edição são percebidas a cada rodada; thread interrompida conserva vaga até finally. Shutdown fecha polling antes dos workers e espera até 5 segundos; reserva que não finalizar fica recuperável na próxima instância.

## Risks & Concerns

| Concern | Location | Mitigação |
| --- | --- | --- |
| API/tests anteriores assumem ausência de scheduler | CheckServiceIT, MonitorApiIT | Desabilitar polling apenas em fixtures existentes, mantendo suas assertions; nova IT prova runtime habilitado |
| Migração/FK muda fixtures e smoke | MonitorRepositoryIT, ApplicationIT, validate-compose.ps1 | Expandir histórico para V4 e incluir check_attempts em TRUNCATE |
| DNS nativo pode ignorar interrupção | HttpCheckExecutor | Contrato MON-05 já limita caller e bloqueia conexão posterior; não ocupar vaga pelo DNS residual |
| Recuperação de outra instância causaria duplicação | ScheduleStore | Instância única explícita; recuperação inicial/órfã somente sem worker local; múltiplas instâncias fora do escopo |
| Future cancelado não prova worker concluído | CheckScheduler | Rastrear Thread, liberar no finally real |

## Tech Decisions

Sem dependências novas. Usar TransactionTemplate para chamadas explícitas independentes de proxy no scheduler e um lock Java curto para rodadas/mapa. Reusar clock UTC e truncate microssegundos como administração. AD-008 registra contrato para MON-07.
