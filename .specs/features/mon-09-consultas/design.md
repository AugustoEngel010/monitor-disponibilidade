# MON-09: design

**Spec:** [spec.md](spec.md). **Status:** execução autorizada no pedido.

## Architecture Overview

QueryController → HistoryQueries / SummaryQueries → JDBC PostgreSQL. Cada método público de consulta usa transação readOnly REPEATABLE_READ. QueryParameters valida antes de acessar dados; Clock UTC já existente fornece agora único. Reutilizar MonitorRepository, Monitor.evidence, IncidentRepository e CheckResult.Outcome; erros seguem MonitorProblem.

## Code Reuse Analysis

JDBC e DTOs record preservam o padrão existente. JPA adicionaria dependência e convenção; SQL no controller misturaria transporte e leitura. Épocas registradas no PostgreSQL evitam que todo produtor de alterações precise depender do módulo de consultas. Sem alteração em executor, incidentes, SMTP ou segurança.

## Components

| Componente | Contrato |
| --- | --- |
| QueryParameters | UUID estrito, página e janela [from,to); filtros por recurso, rejeição de parâmetros desconhecidos/duplicados |
| V7 observation_epochs | Snapshot por monitor/geração: versão, ciclo, início/fim, frescor; trigger de insert/update de geração |
| HistoryQueries | Página tipada de checks/incidentes/notificações e tentativas (um batch para IDs da página) |
| SummaryQueries | Snapshot do monitor/incidente; agregados SQL; união de intervalos de cobertura e complemento |
| QueryController / QueryErrors | GET /api/admin/monitors/{id}/{checks,incidents,notifications,summary}; Basic global |

## Data Models

Records de paginação seguem MON-04. Instantes Instant UTC e durações BigDecimal evitam truncamento. Resumo contém window (from,to,timeZone), estado atual (independente da janela), estatísticas da janela e intervalos sem observação. Migração aditiva preserva V1–V6, adiciona índice de época e índice de transição por monitor/confirmed_at/id. Tentativas têm limite existente de cinco por notificação.

## Error Handling Strategy

400 por campo, 404 MONITOR_NOT_FOUND, 401 Basic existente; infraestrutura propaga erro. Sem valores de entrada nem erros SMTP brutos nas mensagens. Snapshot evita total de uma transação e itens de outra. Paginação offset consistente por resposta; inserções entre requisições podem mover páginas, limitação documentada.

## Risks & Concerns

| Concern | Local | Impacto / mitigação |
| --- | --- | --- |
| Pausas/políticas históricas ausentes | MonitorRepository.save | V7 persiste épocas na mesma transação; upgrade conservador explicita coverageKnownFrom |
| Agregação de histórico extenso | check_results | Agregação SQL e índice existente; cobertura traz somente intervalos que cruzam janela; retenção MON-11 |
| N+1 de tentativas | notification_attempts | Batch único por IDs da página, no máximo 500 tentativas |
| Frescor de contadores antigos | Monitor.state | Prioridade de evidência antes da confirmação, recuperação somente em DOWN |
| Gates assumem seis migrações | ApplicationIT, validate-compose.ps1 | Atualizar para sete, mantendo igualdade de histórico/restart e checksums |

## Tech Decisions

Épocas são infraestrutura do contrato de observação, sem novo framework. Fim é exclusivo; igualdade de timestamp de pausa impede cobertura após pausa. Geração 1 pode ser reconstruída; demais só têm cobertura conhecida desde a migração. Histórico bruto nunca é apagado ou reclassificado. AD-011 registra este contrato para MON-10/11.
