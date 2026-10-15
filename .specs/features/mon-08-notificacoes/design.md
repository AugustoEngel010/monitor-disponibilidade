# MON-08: design

**Spec:** [spec.md](spec.md). **Status:** execução autorizada.

## Architecture Overview

Recomendação adotada: outbox PostgreSQL existente + um worker SMTP separado. Alternativas: broker exigiria infraestrutura/coordenação adicionais; enviar no processador manteria a transação aberta e perderia recuperabilidade. Ambas contrariariam os limites atuais.

IncidentProcessor → intenção PENDING (transação existente).
NotificationRuntime → NotificationStore.claim (transação curta) → SmtpDelivery (sem transação) → NotificationStore.finish (transação curta).
Runtime reconcilia SENDING uma vez no startup, antes de reservar. Falha de conclusão conserva resultado em memória apenas para repetir gravação; reserva durável continua recuperável após crash.

Se a resposta do commit de claim se perder, a próxima rodada reconcilia novamente antes de reservar: pode existir SENDING confirmado sem referência local. Essa tentativa é conservadoramente incerta e consumida, mesmo se nenhum SMTP começou.

## Code Reuse Analysis

Reutilizar intenção/snapshot/UNIQUE da V5, TransactionTemplate, Clock UTC, JDBC, eventos ApplicationReadyEvent e padrão de executor dedicado do SchedulerRuntime. FKs mantêm evidências necessárias. Spring Mail entra como único starter novo; Mailpit local usa a mesma imagem nos testes e Compose.

## Components

| Componente | Contrato |
| --- | --- |
| NotificationStore | claim(now), finish(delivery,outcome,now), recover(now); transações REQUIRES_NEW; claim com FOR UPDATE SKIP LOCKED |
| NotificationDelivery | Snapshot imutável, attempt number, Message-ID, assunto e corpo |
| SmtpDelivery | JavaMailSender; MIME UTF-8; timeout/rejeição classificada sem texto bruto; proíbe envio com transação ativa |
| NotificationRuntime | Um scheduled executor próprio, fila somente no PostgreSQL, reconciliação inicial e retry de persistência |

## Data Models

V6 expande status varchar para PENDING/SENDING/ACCEPTED/EXHAUSTED, adiciona attempt_count 0..5, next_attempt_at e completed_at. notification_attempts usa PK(notification_id,attempt_number), started_at, finished_at, outcome e error_code de vocabulário fixo. Índice parcial para PENDING e uma tentativa aberta por intenção. Estado terminal não tem next_attempt_at. Snapshot da V5 não muda.

## Error Handling Strategy

Falha no claim não envia; falha após rede repete conclusão sem reenviar. Reinício transforma SENDING em UNCERTAIN, com tentativa consumida. Não há transação que englobe rede. Mesmo Message-ID não oferece exactly-once. Recuperação consulta todas as quedas do incidente, sem depender do destinatário atual. Seleção ordenada por próximo horário e UUID.

## Risks & Concerns

| Concern | Location | Impacto | Mitigação |
| --- | --- | --- | --- |
| Estado V5 aceita só PENDING | V5__create_incident_history.sql | Não suporta entrega | V6 aditiva, preservando migrations antigas |
| Confirmação SMTP perdida | SmtpDelivery | Duplicação inevitável | UNCERTAIN durável, ID estável, limite cinco |
| Reconciliar com outra instância ativa | NotificationRuntime | Reenvio concorrente | Contrato de instância única com anterior parada |
| Erros SMTP contêm destinatários/credenciais | Transporte | Vazamento | Persistir somente códigos fixos e logs genéricos |
| Testes assumem versão atual V5 | ApplicationIT e smoke | Falsa regressão | Atualizar contagem atual para seis; teste histórico V4→V5 usa target 5 explícito |

## Tech Decisions

AD-010 documenta entrega, ordem e incerteza. Fontes oficiais: [Spring Boot mail](https://docs.spring.io/spring-boot/3.5/reference/io/email.html), [Angus SMTP](https://eclipse-ee4j.github.io/angus-mail/docs/api/org.eclipse.angus.mail/org/eclipse/angus/mail/smtp/package-summary.html) e [Mailpit Docker](https://mailpit.axllent.org/docs/install/docker/). Timeouts connectiontimeout/timeout/writetimeout são explícitos porque os defaults podem ser infinitos.
