# MON-08: notificações duráveis

**Data:** 2026-10-15. **Status:** Implementing. Execução local autorizada pelo pedido.
**Origem:** MON-08-AC01 a MON-08-AC07; PROD-20/30/31/32/33; AD-001/002/004/009.

## Problem Statement

As intenções da MON-07 sobrevivem ao reinício, mas não são enviadas. O operador precisa de entrega assíncrona com histórico e limite finito, inclusive quando não é possível saber se o SMTP aceitou o e-mail.

## Goals

- Entregar queda e recuperação com conteúdo congelado e identificador estável.
- Provar atomicidade, unicidade, retomada e política temporal usando PostgreSQL e SMTP locais em Docker.

## Out of Scope

| Capacidade | Motivo |
| --- | --- |
| API de consulta/painel/retenção | MON-09/10/11; histórico consultável em SQL nesta entrega |
| Múltiplas instâncias e exactly-once SMTP | MVP de instância única; confirmação externa não é transacional |
| Entrega na caixa postal e bounces | ACCEPTED significa apenas aceitação SMTP |

## Assumptions & Open Questions

| Assumption / decision | Chosen default | Rationale | Confirmed? |
| --- | --- | --- | --- |
| Tentativas e ordem | Política MON-01: cinco tentativas; 30/60/120/240 segundos; queda precede recuperação | Contrato existente aplicado ao escopo solicitado | Pedido atual |
| Ordem entre destinatários | Recuperação aguarda todas as intenções de queda do mesmo incidente, inclusive se destinatário mudou | Preserva causalidade do incidente; outros incidentes independentes | Default técnico |
| Reinício | Instância anterior deve estar parada; SENDING vira UNCERTAIN consumindo tentativa; espera começa na reconciliação | Instante real do término perdido é desconhecido; espera conservadora | Default técnico |
| Timeout | Qualquer falha SMTP com aceitação potencialmente desconhecida é UNCERTAIN; rejeição/autenticação conhecida é FAILED | Evitar alegação falsa de não entrega | Default técnico |
| SMTP | Spring Mail/Angus; conexão/leitura/escrita finitas 5000/3000/5000 ms; TLS e autenticação por ambiente | Seguir stack e evitar defaults infinitos | Default técnico |
| Processamento | Um worker SMTP próprio e polling 250 ms; sem fila em memória; intent desabilitável por ambiente | Isola verificações e limita recursos | Default técnico |
| Precisão de duração | Segundos decimais exatos com ponto, sem zeros finais desnecessários ou truncamento de frações | Preservar a diferença temporal definida por MON-01; exemplo 180.9 segundos | Correção do Verifier |

**Open questions:** none; decisões pertinentes da MON-01 adotadas; escolhas técnicas explícitas.

## User Stories

### P1: Receber avisos recuperáveis

**User Story:** Como operador, quero receber avisos e acompanhar tentativas sem perder transições quando o SMTP estiver indisponível.

**Acceptance Criteria**:
1. WHEN queda ou recuperação for confirmada THEN o sistema SHALL manter uma intenção distinta por transição/destinatário, preservando a intenção existente sob falhas repetidas no mesmo incidente (MAIL-01; MON-08-AC01).
2. WHEN a transição confirmar THEN o sistema SHALL persistir intenção atomicamente com estado/incidente/transição/aplicação e recuperar trabalho pendente após rollback ou reinício, executando SMTP fora dessa transação (MAIL-02; MON-08-AC02).
3. WHEN a mesma transição/destinatário for reprocessada THEN o sistema SHALL manter uma única intenção também sob concorrência e restrição UNIQUE no PostgreSQL (MAIL-03; MON-08-AC03).
4. IF SMTP falhar THEN o sistema SHALL consumir no máximo cinco tentativas na mesma intenção, agendar as próximas em término + 30/60/120/240 segundos, não enviar antes do vencimento e persistir EXHAUSTED na quinta falha (MAIL-04; MON-08-AC04).
5. WHEN uma tentativa iniciar ou terminar THEN o sistema SHALL registrar número 1..5, início UTC, término UTC quando conhecido, resultado SENDING/ACCEPTED/FAILED/UNCERTAIN e erro fixo sanitizado, preservando notification ID e Message-ID em retries e reinício (MAIL-05; MON-08-AC05).
6. WHEN enviar um e-mail THEN o sistema SHALL incluir destinatário congelado, nome, URL, tipo DOWN/RECOVERY, incidente, primeira falha e confirmação da queda; recuperação inclui início e confirmação da recuperação e duração estimada recoveryStartedAt menos firstFailureAt em segundos (MAIL-06; MON-08-AC06).
7. WHILE alguma intenção de queda do mesmo incidente não estiver ACCEPTED ou EXHAUSTED o sistema SHALL manter recuperação pendente, liberando-a após qualquer desses estados terminais sem bloquear outros incidentes (MAIL-07; MON-08-AC07).

**Independent Test:** PostgreSQL Testcontainers, Mailpit com destinatários example.org, relógio controlado e servidor SMTP que perde confirmação após DATA.

## Contrato de entrega

PENDING → SENDING → ACCEPTED, ou PENDING para nova tentativa, ou EXHAUSTED. Reserva e histórico SENDING confirmam juntos antes da rede. Resultado e próximo horário confirmam juntos depois da rede. Cada intenção tem no máximo uma tentativa aberta. Uma falha na gravação do resultado mantém a reserva durável; worker repete somente a gravação enquanto estiver vivo. Após reinício, reserva órfã recebe UNCERTAIN/RESTART_UNCERTAIN, término = reconciliação e a mesma espera progressiva; quinta tentativa órfã esgota.

Timeout ou erro de transporte pode ocorrer após aceitação. O histórico usa UNCERTAIN/SMTP_UNCERTAIN; uma rejeição conhecida usa FAILED/SMTP_FAILURE. Nunca salvar mensagem/stack trace/resposta SMTP/credenciais nos erros. ACCEPTED exige retorno positivo do envio SMTP, sem prometer recebimento. Um crash entre aceitação e confirmação local permite duplicação. Message-ID = <notification-UUID@monitor.local> permanece estável, sem garantir deduplicação pelo servidor.

Cada tentativa usa o snapshot congelado na intenção. UTC em todos os horários; duração inclui pausas/lacunas e é estimativa, não indisponibilidade contínua. Intent criada pela V5 continua PENDING e elegível na V6. Migrações anteriores permanecem intactas.

## Edge Cases

- Queda, falhas repetidas, recuperação; replay concorrente; rollback na criação e na conclusão de tentativa.
- SMTP indisponível, rejeição explícita, timeout após DATA, término perdido após aceitação e quinta tentativa incerta.
- Fronteira exata da espera, 1 ms antes e igualdade; IDs estáveis em retries.
- Reinício com PENDING e SENDING; fim do contexto e novo contexto sobre o mesmo banco.
- Recuperação pendente com queda em PENDING/SENDING/EXHAUSTED/ACCEPTED; destinatário editado; outro incidente elegível.
- Edição posterior não altera conteúdo nem destinatário; nenhum envio dentro de transação; worker SMTP lento não impede verificações.
- Upgrade V5 preserva intenções e checksums, seis migrações numa base vazia.
- Resposta de commit da reserva perdida: reconciliar SENDING sem referência local como incerta antes de reservar outra vez; consumir a tentativa e aplicar a mesma espera.
- Primeira falha 12:00:00.100Z e início de recuperação 12:03:01Z geram duração de 180.9 segundos; o caso inteiro continua 180 segundos.

## Dimensões implícitas

| Dimensão | Resolução |
| --- | --- |
| Validação/limites | SMTP/timeouts/polling positivos e limitados; destinatário MON-04 |
| Falha parcial | MAIL-02/05; reserva e conclusão transacionais distintas |
| Idempotência/retry | MAIL-03/04/05; UNIQUE e Message-ID estável |
| Auth/rate | Nenhuma rota nova; autenticação SMTP externa; um worker limita envio |
| Concorrência/ordem | Lock de intenção, claim exclusivo; MAIL-07; instância única |
| Ciclo/expiração | Sem exclusão; retenção MON-11 deve preservar referências |
| Observabilidade | Histórico SQL sanitizado, IDs; sem resposta SMTP bruta |
| Dependência externa | Timeouts finitos, SMTP fora de transação e do scheduler HTTP |
| Integridade | Estados e tentativas com CHECK/FK/UNIQUE no PostgreSQL |

## Requirement Traceability

| Requirement ID | Origem | Tasks | Testes | Status |
| --- | --- | --- | --- | --- |
| MAIL-01 | MON-08-AC01 | T1,T2 | IncidentProcessorIT, NotificationStoreIT | Implementing |
| MAIL-02 | MON-08-AC02 | T1,T2,T4 | IncidentProcessorIT, NotificationStoreIT, NotificationRuntimeIT | Implementing |
| MAIL-03 | MON-08-AC03 | T1,T2 | NotificationSchemaIT, IncidentProcessorIT | Implementing |
| MAIL-04 | MON-08-AC04 | T2,T3 | NotificationStoreIT, SmtpDeliveryIT | Implementing |
| MAIL-05 | MON-08-AC05 | T1,T2,T3,T4 | NotificationStoreIT, SmtpDeliveryIT, NotificationRuntimeIT | Implementing |
| MAIL-06 | MON-08-AC06 | T3,T6 | SmtpDeliveryIT, SmtpDurationIT | Implementing |
| MAIL-07 | MON-08-AC07 | T2,T4 | NotificationStoreIT, NotificationRuntimeIT | Implementing |

## Success Criteria

Sete ACs com evidência file:line, suite Docker sem falhas/skips, runtime construído e SMTP local validado. Um commit por task; Verifier independente com sensor em cópia isolada.
