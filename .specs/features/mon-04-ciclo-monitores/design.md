# MON-04: design

**Spec:** [spec.md](spec.md). **Status:** execução autorizada.

## Architecture Overview

Controller → serviço transacional → repositório JDBC → PostgreSQL. Modelo puro controla configuração e transições com Instant explícito; Clock UTC injetável no serviço. Basic/CSRF global existente protege novas rotas.

## Code Reuse Analysis

Reutilizar starter-jdbc, JdbcTemplate, Flyway, bootstrap, chain de segurança, HTTP real/Testcontainers, AssertJ, Checkstyle e fluxo tests. Alternativas: JPA exigiria dependência e mapeamento novos; SQL no controller misturaria domínio e transporte. JDBC já instalado mantém transações e locks explícitos.

## Components

| Componente | Responsabilidade |
| --- | --- |
| MonitorConfiguration | Validar campos e comparar política |
| Monitor | Identidade, estado, edição, pause/resume, frescor |
| MonitorRepository | Mapear estado, inserir/atualizar/buscar/listar |
| MonitorService | Transação, lock por ID, limite serializado e Clock |
| MonitorController / MonitorErrors | HTTP/JSON, erros sanitizados, paginação |

## Data Models

V2 cria monitor.monitors, configuração com CHECKs, UUID PK, createdAt/updatedAt UTC, versões >=1, ciclo/estado, contadores/horários provisórios, lastObservedAt e currentObservation boolean, openIncidentId UUID, nextCheckAt. Índice (created_at,id). Referência de incidente é nullable sem FK por enquanto: MON-07 criará tabela/FK e processamento. História externa futura referencia monitor por ID e versão; nenhuma operação desta entrega remove linhas. Testes usam tabela auxiliar de histórico para provar preservação e seeding de estado DOWN com referência de incidente, sem alegar criação de incidente real. AD-006 registra o protocolo de versão/geração compartilhado com as próximas entregas.

## Error Handling Strategy

MonitorProblem carrega status, code e mapa de erros fixos; nenhum erro contém inputs. Parsing estrito de inteiros e estruturas evita coerção Jackson. UUID inexistente 404; formato inválido 400; limite e política em incidente 409; infraestrutura permanece falha de servidor. PUT valida antes de alterar; serviço bloqueia linha e decide sobre snapshot atual.

## Risks & Concerns

| Concern | Local | Mitigação |
| --- | --- | --- |
| Testes/smoke MON-02 assumem somente V1 | MigrationIT, ApplicationIT, scripts/validate-compose.ps1 | Preservar teste V1 em location isolada; atualizar contrato de histórico total para V1/V2 sem reduzir assertions |
| Limite count+insert tem corrida | MonitorService | pg_advisory_xact_lock exclusivo de criação antes de count |
| Estado observado confundido com ciclo | Monitor | Campo de validade corrente separado do timestamp histórico |
| Cancelamento futuro ainda inexistente | Monitor | executionGeneration invalida resultados; MON-06 deve bloquear a mesma linha e aplicar geração |
| Docker Desktop indisponível no início | Ambiente | Recuperar daemon; nunca marcar gates não executados como verdes |

## Tech Decisions

Fontes oficiais consultadas: [JDBC Spring](https://docs.spring.io/spring-framework/reference/data-access/jdbc/core.html) e [locks PostgreSQL 17](https://www.postgresql.org/docs/17/explicit-locking.html). Lock de linha serializa mutações; advisory lock de transação evita corrida do limite; REPEATABLE_READ para lista consistente. Sem dependências novas.
