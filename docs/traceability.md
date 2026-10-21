# Requisitos, testes e decisões

Cada pasta de feature contém `spec.md` com IDs internos e IDs de origem MON-XX-ACYY. `tasks.md` detalha gates, `evidence.md` registra execução e `validation.md` contém o parecer da skill. MON-01 é contrato documental histórico e continua com status de proposta; não recebe testes artificiais que apenas repetem texto.

| Entrega / requisitos | Spec | Testes e evidências principais |
| --- | --- | --- |
| MON-01 / PROD-01..37 | [Produto e contratos](../.specs/features/mon-01-produto-contratos/spec.md) | Cenários C-01..C-23 e rastreabilidade nas entregas abaixo |
| MON-02 / BASE | [Docker](../.specs/features/mon-02-base-docker/spec.md) | ApplicationIT, MigrationIT, ArchitectureTest; scripts/validate-compose.ps1 |
| MON-03 / AUTH | [Segurança](../.specs/features/mon-03-acesso-administrativo/spec.md) | AdministrativeSecurityIT, AdministrativeConfigurationIT; smoke configuração/rotação |
| MON-04 / LIFE | [Administração](../.specs/features/mon-04-ciclo-monitores/spec.md) | MonitorConfigurationTest, MonitorTest, MonitorRepositoryIT, MonitorApiIT |
| MON-05 / HTTP | [Executor](../.specs/features/mon-05-verificacao-http/spec.md) | DestinationPolicyTest, HttpCheckExecutorIT, CheckRepositoryIT, CheckServiceIT |
| MON-06 / SCHED | [Agendamento](../.specs/features/mon-06-agendamento/spec.md) | CheckSchedulerTest, ScheduleStoreIT, SchedulerRuntimeIT |
| MON-07 / INC | [Incidentes](../.specs/features/mon-07-incidentes/spec.md) | IncidentStateMachineTest, IncidentProcessorIT, IncidentLifecycleIT, IncidentSchemaIT |
| MON-08 / MAIL | [Notificações](../.specs/features/mon-08-notificacoes/spec.md) | NotificationStoreIT, NotificationRuntimeIT, NotificationSchemaIT, SmtpDeliveryIT, SmtpDurationIT |
| MON-09 / QUERY | [Consultas](../.specs/features/mon-09-consultas/spec.md) | QueryParametersTest, ObservationEpochIT, HistoryQueriesIT, SummaryQueriesIT, QueryApiIT |
| MON-10 / PANEL | [Painel](../.specs/features/mon-10-painel/spec.md) | config/panel/*.spec.js: cadastro, edição, lista, detalhes, lifecycle, segurança, responsividade |
| MON-11 / OPS | [Operação](../.specs/features/mon-11-operacao/spec.md) | OperationHealthIT, OperationMetricsTest, OperationalLogsIT, Retention*IT; retention.spec.js; CapacityScenario |
| MON-12 / DEMO | [Demonstração](../.specs/features/mon-12-demonstracao/spec.md) | config/demo/server.test.mjs, seed.test.mjs, scenario.mjs e scripts/demo.ps1 |

Os testes Java ficam em [src/test/java/br/com/monitor](../src/test/java/br/com/monitor), com PostgreSQL e servidores controlados reais onde aplicável. [config/panel](../config/panel) contém os testes de navegador. Comandos e relatórios estão no [README](../README.md#testar); testes e qualidade sempre executam por Docker.

## MON-12 por critério

| Origem | Requisito | Verificação derivada do critério |
| --- | --- | --- |
| MON-12-AC01 | DEMO-01 | Build a partir de cópia limpa, quatro serviços, readiness e seed repetido com mesmos IDs; seed.test.mjs rejeita divergência/duplicação |
| MON-12-AC02 | DEMO-02 | server.test.mjs: 200/503/atraso e independência; scenario slow usa atraso real de 15s |
| MON-12-AC03 | DEMO-03 | scenario normal: contadores 1/3 e 1/2, mesmo incidente, intenções únicas e SMTP ordenado |
| MON-12-AC04 | DEMO-04 | pending/restart/retry: IDs e executionIds preservados, mesmo incidente/aviso, retry >=30s e limite cinco |
| MON-12-AC05 | DEMO-05 | slow/paused/stale: sobreposição por timestamps, nenhum check pausado, ACTIVE/DOWN/STALE sem fechar incidente |
| MON-12-AC06 | DEMO-06 | README, roteiro, comandos executados e oito imagens reais |
| MON-12-AC07 | DEMO-07 | Este mapa, specs, STATE e capacidade MON-11 com limitações |
| MON-12-AC08 | DEMO-08 | Suites, smoke, gates da skill, sensor isolado e validation.md |

As assertions e seus números de linha ficam no [parecer MON-12](../.specs/features/mon-12-demonstracao/validation.md), com comandos e resultados em [evidence.md](../.specs/features/mon-12-demonstracao/evidence.md). A demonstração complementa as suítes anteriores e não substitui seus cenários transacionais, concorrentes e de segurança.

## Decisões vigentes

[STATE.md](../.specs/STATE.md) é a fonte de decisões: AD-001 monólito/instância única; AD-002 transação e outbox; AD-003 dimensões independentes; AD-004 Docker exclusivo; AD-005 Basic/CSRF; AD-006 versões/gerações; AD-007 política de destinos; AD-008 reservas/contrapressão; AD-009 aplicação idempotente; AD-010 SMTP/retries/ambiguidade; AD-011 snapshots/épocas; AD-012 retenção e recibos.

Designs e contextos estão junto às specs, sem substituir as decisões globais. O [backlog original](../.specs/task/monitor-disponibilidade-tasks.md) preserva o vínculo de produto e os critérios de aceite.

## Capacidade e limites

[MON-11](capacity/README.md) e [dados brutos](capacity/mon-11.json): 100 monitores; C=4; 285 verificações em 60,070333095s; concorrência máxima 4; zero sobreposições; 143 SUCCESS, 71 HTTP_FAILURE, 69 TIMEOUT e dois CANCELLED de shutdown, incluídos no total de 285. Atraso máximo 20,634817s e médio 10,967730218s. Recursos: app/runner/alvos 2 CPUs/2GiB e PostgreSQL 1 CPU/512MiB. O cenário saturou e não cumpriu I=10s para essa mistura.

O resultado caracteriza um harness curto com polling/coleta e não estabelece SLA ou capacidade universal. Fases com relógio acelerado de retries e retenção são separadas da medição de throughput. A MON-12 reutiliza a medição versionada, sem apresentá-la como nova execução.

Limitações operacionais e de produto estão no [README](../README.md#specs-testes-e-limites) e [operação](operations.md). CI remota, hospedagem, design aprovado pelo operador e disponibilidade comercial não são inferidos de testes locais.
