# MON-06: agendamento com concorrência controlada

**Data:** 2026-10-14. **Status:** Implementing. Execução local autorizada pelo pedido atual.
**Origem:** MON-06-AC01 a MON-06-AC08; PROD-05/06/17/25/26/27/28/29; AD-001 a AD-007.

## Problem Statement

O executor MON-05 produz resultados, mas não coordena tentativas periódicas. O operador precisa preservar agendamento e histórico após reinício sem sobreposição ou filas ilimitadas.

## Goals

- Executar monitores vencidos com reserva persistente e capacidade limitada.
- Recuperar tentativas interrompidas sem inferir saúde pela ausência de execução.
- Verificar concorrência com relógio controlável, barreiras e PostgreSQL real por Docker.

## Out of Scope

| Capacidade | Motivo |
| --- | --- |
| Múltiplas instâncias, leases distribuídos | MVP explicitamente de instância única |
| Aplicação de sucesso/falha ao estado e incidentes | MON-07; AD-002 permanece vigente |
| Histórico paginado, painel, métricas e retenção | MON-09 a MON-11 |
| Push, deploy ou alteração do banco do operador | Autorização local, bancos de teste descartáveis |

## Assumptions & Open Questions

| Assumption / decision | Chosen default | Rationale | Confirmed? |
| --- | --- | --- | --- |
| Agendamento | Início lógico é a reserva durável imediatamente antes de despachar; próximo = início lógico + I | Persistir antes de HTTP cobre crash entre reserva e envio; MON-01 |
| Poll | Rodada a cada 250 ms, configurável positiva; sem promessa de tempo real | Retomar vagas sem busy loop; atraso permanece consultável |
| Concorrência | Padrão 4, inteiro 1..16 em startup | Limites da MON-01; trabalho excedente permanece vencido no banco |
| Pausa/edição | Invalidar geração no lock administrativo; solicitar interrupção na rodada seguinte, manter vaga até worker sair | MON-01 permite cancelamento quando possível e histórico de resposta tardia |
| Falha de persistência | Liberar thread; reserva órfã reconciliada na próxima rodada com banco disponível, sem retry HTTP imediato | Falha interna não é falha externa; não bloquear indefinidamente |
| Reinício | Somente depois de a instância anterior parar; reserva sem resultado recebe CANCELLED, duração desconhecida 0 | Instância única; não alegar observação ou duração HTTP que não foi medida |
| Frescor | Usar evidence/recentObservation já presentes na API; L = 2I+T inclusivo | Não criar outro endpoint nem antecipar processamento da MON-07 |
| Ferramentas | Docker/Compose, PowerShell/Python e tlc-spec-driven | Selecionadas no pedido e fluxo vigente |

**Open questions:** none; escolhas acima explicitadas dentro do escopo autorizado.

## User Stories

### P1: Executar e retomar verificações

**User Story:** Como operador, quero verificações periódicas que sobrevivam a atrasos e reinícios.

**Acceptance Criteria**:
1. WHEN uma rodada tiver capacidade THEN o agendador SHALL reservar somente ACTIVE com nextCheckAt <= agora, ordenados por nextCheckAt e UUID, persistindo próximo = agora + I na mesma transação da reserva (SCHED-01; MON-06-AC01).
2. WHILE existir uma reserva em andamento o agendador SHALL impedir segunda tentativa do mesmo monitor, inclusive rodadas simultâneas e pausa seguida de retomada (SCHED-02; MON-06-AC02).
3. WHILE C vagas estiverem ocupadas o agendador SHALL manter excedentes no banco com horário vencido inalterado, sem criar workers ou fila para eles (SCHED-03; MON-06-AC03).
4. WHEN uma tentativa concluir, falhar, exceder timeout ou for cancelada THEN o agendador SHALL liberar sua vaga somente após o worker sair, permitindo avanço de outro monitor na próxima rodada (SCHED-04; MON-06-AC04).
5. WHEN a aplicação reiniciar THEN o agendador SHALL reconciliar reservas órfãs idempotentemente, preservar resultados existentes ou registrar CANCELLED se ausentes, limpar a reserva e preservar configuração, histórico, estado confirmado, incidente e próximo horário (SCHED-05; MON-06-AC05).
6. WHEN um monitor estiver vencido após atraso ou reinício THEN o agendador SHALL iniciar uma única tentativa e agendar início lógico + I, sem replay de intervalos perdidos (SCHED-06; MON-06-AC06).
7. IF uma observação corrente tiver idade maior que 2I+T THEN a consulta SHALL apresentar evidence=STALE e recentObservation=false sem inserir sucesso ou falha externa por ausência de execução (SCHED-07; MON-06-AC07).
8. WHEN pausa ou mudança de política invalidar uma tentativa THEN o agendador SHALL solicitar cancelamento, preservar resultado tardio somente no histórico e manter estado confirmado e incidente, sem reaplicar agendamento antigo sobre pausa/retomada (SCHED-08; MON-06-AC08).

**Independent Test:** Clock mutável e latches; PostgreSQL Testcontainers; executor real com servidor lento e saudável; contexto Spring reiniciado.

## Edge Cases

- Horário igual a agora é elegível; futuro e PAUSED não são; empate usa UUID.
- Rodadas concorrentes, timeout, interrupção, exceção no worker e falha de gravação/claim sem vazamento de capacidade.
- Pausa antes da reserva; pausa/retomada durante HTTP; edição de política e edição apenas de nome.
- Reinício antes de HTTP, depois de resultado salvo e antes de remover reserva; recuperação repetida não duplica resultado.
- Cancelamento de execução interrompida zera sequências/horários provisórios somente se versão/geração ainda coincidem; não modifica incidente ou observação. Reinício sem reserva preserva contadores.
- Banco indisponível mantém reserva durável e encerra rodada sem inventar evidência externa; ao retornar, reconciliação libera monitor.
- L exato ainda FRESH, L + 1 microssegundo STALE; timestamp futuro STALE; nenhuma observação NEVER_OBSERVED.
- Configuração C=0/17 ou poll <=0 impede startup; shutdown interrompe workers e aguarda término limitado antes de fechar banco.

## Dimensões implícitas

| Dimensão | Resolução |
| --- | --- |
| Validação e limites | C=1..16; poll positivo; limites de monitores existentes |
| Falha parcial/dependências | Reserva/horário atômicos, HTTP fora de transação, reconciliação |
| Idempotência | Reserva única por monitor e resultado por UUID; recuperação repetida |
| Auth/rate | Basic/CSRF preservados; nenhuma rota de execução nova; rate administrativo N/A |
| Concorrência/ordem | Lock da mesma linha administrativa; mapa de até C workers; ordem vencimento/UUID |
| Ciclo/expiração | Histórico preservado; frescor existente; retenção MON-11 |
| Observabilidade | Agendamento e atraso consultáveis por nextCheckAt; erro operacional fixo, métricas MON-11 |
| Integridade | Versão/geração, sem aplicação ao estado de saúde nesta entrega |

## Requirement Traceability

| Requirement ID | Origem | Task | Teste | Status |
| --- | --- | --- | --- | --- |
| SCHED-01 | MON-06-AC01 | T1,T3 | ScheduleStoreIT, SchedulerRuntimeIT | Implementing |
| SCHED-02 | MON-06-AC02 | T1,T2 | ScheduleStoreIT, CheckSchedulerTest | Implementing |
| SCHED-03 | MON-06-AC03 | T2,T3 | CheckSchedulerTest, SchedulerRuntimeIT | Implementing |
| SCHED-04 | MON-06-AC04 | T2,T3 | CheckSchedulerTest, SchedulerRuntimeIT | Implementing |
| SCHED-05 | MON-06-AC05 | T1,T3 | ScheduleStoreIT, SchedulerRuntimeIT | Implementing |
| SCHED-06 | MON-06-AC06 | T1,T3 | ScheduleStoreIT, SchedulerRuntimeIT | Implementing |
| SCHED-07 | MON-06-AC07 | T3 | SchedulerRuntimeIT, MonitorTest | Implementing |
| SCHED-08 | MON-06-AC08 | T2,T3 | CheckSchedulerTest, SchedulerRuntimeIT | Implementing |

## Success Criteria

Oito ACs com assertions file:line, gates Docker, runtime e smoke aprovados; commits por task e Verifier independente com sensor de discriminação. Não declarar concluído com testes pendentes.
