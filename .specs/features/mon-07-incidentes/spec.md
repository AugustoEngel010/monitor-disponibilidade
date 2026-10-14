# MON-07: identificar queda e recuperação e manter incidentes

**Data:** 2026-10-14. **Status:** Implementing. Execução local autorizada pelo pedido.
**Origem:** MON-07-AC01 a MON-07-AC08; PROD-05/06/07/08/09/11 a PROD-20/28/29; AD-001 a AD-008.

## Problem Statement

Os resultados HTTP já são duráveis, mas ainda não confirmam disponibilidade nem mantêm incidentes reais. O processamento precisa sobreviver a repetição, concorrência e reinício sem transformar ausência de dados em evidência externa.

## Goals

- Confirmar queda e recuperação nos limites configurados F/R, preservando um único incidente aberto.
- Persistir aplicação, estado, incidente e transição atomicamente e provar rollback em PostgreSQL real.
- Manter arquitetura modular e build, qualidade e testes exclusivamente em Docker.

## Out of Scope

| Capacidade | Motivo |
| --- | --- |
| Envio SMTP, retry e consulta de notificações | MON-08; somente intenção durável integra a transação sob AD-002 |
| APIs de incidentes, painel e indicadores | MON-09/10 |
| Retenção e métricas | MON-11 |
| Múltiplas instâncias, push, deploy e migração do banco do operador | MVP local, testes em bancos descartáveis |

## Assumptions & Open Questions

| Assumption / decision | Chosen default | Rationale | Confirmed? |
| --- | --- | --- | --- |
| Regras de produto | Aplicar MON-01 e decisões AD-001 a AD-008 | Pedido explícito, incluindo pausa, edição, lacunas e cancelamentos | Sim |
| Atomicidade | Resultado bruto pode anteceder aplicação; marcador aplicado, estado, incidente, transição e intenção confirmam juntos | Resultado MON-05 imutável permite reaplicar após rollback; AD-002 | Decisão vigente |
| Horários | Observação = finishedAt; confirmação = finishedAt da amostra no limiar, nunca relógio do processamento | MON-01 define os quatro instantes; atraso/repetição não deslocam evidência | Contrato existente |
| Lacuna | startedAt da tentativa lógica > lastObservedAt + 2I+T interrompe sequências; igualdade não interrompe | Excluir duração da tentativa da lacuna prévia | Contrato existente |
| Ordem | Scheduler aplica antes de liberar reserva; resultado de tentativa anterior ao último início processado fica somente histórico | Não aplicar evidência antiga sobre observação nova; não executar replay HTTP | Default técnico |
| Migração de histórico anterior | V5 marca resultados anteriores como históricos, sem reinterpretar verificações já concluídas; reservas pendentes serão reconciliadas | Não abrir incidentes retroativos na atualização de MON-06 | Default técnico |
| Ferramentas | Skill solicitada, PowerShell/Python, Docker Compose e Verifier independente | Pedido autoriza execução; nenhum Maven/JDK no host | Sim |

**Open questions:** none; decisões técnicas explicitadas, sem nova regra de produto.

## User Stories

### P1: Confirmar disponibilidade por evidência

**User Story:** Como operador, quero confirmar mudanças sem gerar incidentes para falhas isoladas.

**Acceptance Criteria**:
1. WHEN ocorrerem F=3 falhas externas consecutivas elegíveis em UNKNOWN ou UP THEN o sistema SHALL manter confirmação com contadores 1 e 2 e confirmar DOWN, contador saturado em 3 e um incidente na terceira (INC-01; MON-07-AC01).
2. WHEN ocorrer SUCCESS antes de F THEN o sistema SHALL zerar falhas e firstFailureAt e estabelecer UP sem incidente nem transição de recuperação (INC-02; MON-07-AC02).
3. WHILE existir um incidente aberto o sistema SHALL preservar sua identidade diante de novas falhas e impedir um segundo incidente aberto por monitor também por índice único no PostgreSQL (INC-03; MON-07-AC03).
4. WHEN ocorrerem R=2 sucessos consecutivos elegíveis em DOWN THEN o sistema SHALL iniciar recuperação no primeiro e encerrar o incidente e confirmar UP no segundo; falha externa entre eles limpa contador e início de recuperação (INC-04; MON-07-AC04).
5. WHEN um executionId já processado for reaplicado THEN o sistema SHALL preservar integralmente contadores, instantes, estado, incidentes, transições e intenções, inclusive sob chamadas concorrentes (INC-05; MON-07-AC05).
6. WHEN ocorrer INTERNAL_ERROR, DESTINATION_BLOCKED, CANCELLED ou lacuna antes de tentativa elegível THEN o sistema SHALL limpar sequências e horários provisórios sem alterar estado confirmado nem fechar incidente; resultado não externo preserva lastObservedAt e validade da observação (INC-06; MON-07-AC06).
7. WHEN ocorrer reinício, pausa, retomada ou edição THEN o sistema SHALL aplicar o contrato de ciclo de vida abaixo mantendo histórico e incidentes, e recuperar contadores persistidos somente sem interrupção ou lacuna (INC-07; MON-07-AC07).
8. WHEN ocorrer abertura ou encerramento de incidente THEN o sistema SHALL registrar os quatro horários definidos abaixo e confirmar aplicação, estado, incidente, transição ligada ao resultado e intenção durável em uma única transação, sem estado parcial após erro (INC-08; MON-07-AC08).

**Independent Test:** Modelo puro com Instants explícitos; processamento e concorrência com PostgreSQL Testcontainers; contexto Spring encerrado e reaberto.

## Contrato de processamento e tempo

Somente ACTIVE da configurationVersion/executionGeneration correntes é elegível. Todas as seis classificações externas de MON-05 alimentam a regra: SUCCESS é sucesso; HTTP_FAILURE, TIMEOUT, DNS_FAILURE, CONNECTION_FAILURE e TLS_FAILURE são falhas. Resultado tardio, pausado, versão/geração antiga ou fora de ordem recebe marcador histórico sem mudar monitor/incidente. O primeiro registro bruto por UUID vence, inclusive se uma reaplicação trouxer outro payload.

UNKNOWN + primeiro SUCCESS estabelece UP sem recuperação nem intenção. Contadores saturam nos limites, sem crescimento sob falhas repetidas. Uma confirmação de recuperação deixa recoveryCount=R e limpa firstFailureAt; a observação seguinte em UP limpa recoveryCount e horário provisório. F=1/R=1 confirma imediatamente.

firstFailureAt do incidente é finishedAt da primeira falha da sequência confirmada. downConfirmedAt é finishedAt da F-ésima falha. recoveryStartedAt é finishedAt do primeiro sucesso da sequência corrente, limpo por falha, resultado não externo, lacuna ou pausa. recoveredAt é finishedAt do R-ésimo sucesso. Os horários de um incidente fechado são imutáveis. Duração estimada final = recoveryStartedAt - firstFailureAt; não equivale a uptime temporal e inclui pausas/lacunas. Início lógico da reserva, quando existente, determina a lacuna, mesmo que o executor comece depois.

Pausa/retomada efetivas zeram contadores e horários provisórios, invalidam a observação corrente, incrementam geração e preservam estado/incidente; repetição preserva todos os campos. Nome/destinatário preservam sequências e configuração/geração. Mudança de URL/I/T/status/F/R sem incidente reinicia UNKNOWN; com incidente retorna 409 sem alteração. Intenções existentes preservam destinatário e conteúdo congelados.

Reinício sem reserva interrompida mantém estado/contadores e próximo agendamento; nova tentativa interrompe sequência somente se início ultrapassar L. Reserva sem resultado recebe CANCELLED e quebra confirmações; reserva com resultado aplica o primeiro resultado antes de ser removida. Falha de aplicação mantém resultado bruto/reserva recuperáveis, e a próxima reconciliação reaplica sem novo HTTP. Banco indisponível nunca vira falha externa.

## Edge Cases

- UNKNOWN/UP: F,F,F,F; UP: F,F,S,F; primeiro sucesso sem incidente; F/R=1 e limites 10.
- DOWN: S,F,S,S; sequência de recuperação interrompida por cada resultado não externo.
- Lacuna L exata preserva sequência; L+1 microssegundo reinicia; duração do HTTP não é lacuna anterior.
- Repetição do resultado de abertura/fechamento, payload divergente, concorrência do mesmo resultado e de falhas distintas.
- Rollback ao inserir incidente, transição, intenção ou ao concluir aplicação; reaplicação mantém evidência original.
- Pausa concorrente com processamento; sucesso tardio após pausa/retomada; edição de nome com recuperação parcial e 409 para cada campo de política.
- Reinício com duas falhas, recuperação parcial, reserva interrompida e resultado salvo antes de aplicação; migração V4 para V5 preserva dados/checksums.
- SQL direto tenta segundo incidente aberto; incidente fechado permite novo incidente com outra identidade.
- Resultado de outro executionId/snapshot não pode concluir nem liberar a reserva de uma tentativa diferente.

## Dimensões implícitas

| Dimensão | Resolução |
| --- | --- |
| Validação/limites | Configuração MON-04; F/R=1..10 e contadores saturados |
| Falha parcial | INC-08; rollback e retry sobre resultado durável |
| Idempotência | INC-05; marcador executionId PK e lock de monitor |
| Auth/rate | Nenhuma rota nova; Basic/CSRF preservados; rate limiting N/A no contrato interno |
| Concorrência/ordem | Lock de monitor compartilhado com administração e scheduler; ordem da reserva |
| Ciclo/expiração | Histórico preservado; migração não reinterpreta observações antigas; retenção MON-11 |
| Observabilidade | Transição durável com IDs e instantes; logs sem payload bruto |
| Dependência externa | PostgreSQL real; falha no banco não muda classificação externa |
| Integridade | INC-01 a INC-08; unicidade e transação |

## Requirement Traceability

| Requirement ID | Origem | Task | Teste previsto | Status |
| --- | --- | --- | --- | --- |
| INC-01 | MON-07-AC01 | T1,T3,T4 | IncidentStateMachineTest, IncidentProcessorIT | Implementing |
| INC-02 | MON-07-AC02 | T1,T3 | IncidentStateMachineTest, IncidentProcessorIT | Implementing |
| INC-03 | MON-07-AC03 | T1,T2,T3 | IncidentSchemaIT, IncidentProcessorIT | Implementing |
| INC-04 | MON-07-AC04 | T1,T3,T4 | IncidentStateMachineTest, IncidentProcessorIT | Implementing |
| INC-05 | MON-07-AC05 | T3,T4 | IncidentProcessorIT, IncidentLifecycleIT | Implementing |
| INC-06 | MON-07-AC06 | T1,T3,T4 | IncidentStateMachineTest, IncidentLifecycleIT | Implementing |
| INC-07 | MON-07-AC07 | T4 | IncidentLifecycleIT | Implementing |
| INC-08 | MON-07-AC08 | T2,T3,T4 | IncidentSchemaIT, IncidentProcessorIT, IncidentLifecycleIT | Implementing |

## Success Criteria

Oito ACs com assertions file:line e resultados Docker registrados; um commit por task; Verifier independente e pelo menos cinco mutações comportamentais em cópia isolada. Zero testes removidos, desabilitados ou enfraquecidos.
