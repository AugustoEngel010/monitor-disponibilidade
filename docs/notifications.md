# Notificações por e-mail

A transição confirmada cria uma intenção por destinatário na mesma transação que aplica a verificação, atualiza o estado e abre/fecha o incidente. MON-07 já fornece esse contrato; V6 acrescenta entrega e histórico. Reprocessar a mesma execução não gera outro aviso. Novas falhas no mesmo incidente preservam o aviso de queda. Recuperação cria outra intenção, com snapshot próprio.

Um worker SMTP separado do scheduler HTTP reserva uma intenção, confirma a reserva no PostgreSQL, envia sem transação aberta e confirma o resultado numa segunda transação curta. O histórico fica no banco mesmo se o SMTP estiver indisponível. Desligar o worker preserva as intenções. Nenhuma chamada SMTP mantém a transação de domínio aberta.

## Estados e tentativas

| Estado da intenção | Significado |
| --- | --- |
| PENDING | Aguarda próximo horário e a política de ordem |
| SENDING | Tentativa reservada e durável; envio ou confirmação local em andamento |
| ACCEPTED | Cliente observou aceitação SMTP e a registrou no banco |
| EXHAUSTED | Cinco tentativas consumidas sem aceitação confirmada localmente |

Cada tentativa tem notification_id, attempt_number (1..5), started_at UTC, finished_at UTC quando encerrada, outcome e error_code. Outcomes: SENDING, ACCEPTED, FAILED ou UNCERTAIN. Erros persistidos são somente SMTP_FAILURE, SMTP_UNCERTAIN e RESTART_UNCERTAIN; nunca resposta SMTP, texto de exceção, credenciais ou stack trace.

Há no máximo cinco tentativas. Após falhas 1, 2, 3 e 4, o próximo início é o término + 30, 60, 120 e 240 segundos. Igualdade torna elegível; antes disso não envia. A quinta falha ou tentativa incerta termina como EXHAUSTED. A intenção e seu Message-ID permanecem os mesmos em todas as tentativas: `<notification-UUID@monitor.local>`.

Recuperação aguarda todas as intenções de queda do mesmo incidente ficarem ACCEPTED ou EXHAUSTED, inclusive se o destinatário foi alterado. Queda esgotada libera a recuperação; não haverá retry infinito nem supressão da recuperação. Outros incidentes elegíveis não aguardam essa cadeia. Um único worker limita recursos; um SMTP lento ocupa esse worker até o timeout, sem ocupar workers HTTP.

## Aceitação e confirmação incerta

ACCEPTED comprova aceitação pelo SMTP, não chegada à caixa postal, leitura ou ausência de bounce. Não existe transação distribuída entre PostgreSQL e SMTP. Se o servidor aceitar mas a resposta se perder, ou se o processo cair antes de registrar a aceitação, pode haver mensagem duplicada. Message-ID estável ajuda a correlacionar, mas não obriga o servidor a deduplicar.

Falha de conexão antes do envio e rejeição SMTP conhecida são FAILED/SMTP_FAILURE. Timeout ou perda de transporte depois de conectar, sem confirmação suficiente, são conservadoramente UNCERTAIN/SMTP_UNCERTAIN. A tentativa é consumida e usa a mesma espera progressiva.

Na inicialização do worker, SENDING sem processo anterior recebe UNCERTAIN/RESTART_UNCERTAIN. O término registrado é o instante da reconciliação porque o término real é desconhecido. A próxima tentativa usa esse instante + espera; se era a quinta, esgota. A reconciliação é idempotente. A perda da resposta do commit de reserva também dispara reconciliação na rodada seguinte. Se apenas a gravação da conclusão falhar, o processo conserva o resultado e repete a gravação, sem novo SMTP; se reiniciar, aplica a regra conservadora de incerteza.

O contrato suporta **uma instância**. Pare completamente o processo anterior antes de iniciar outro sobre o mesmo banco. O shutdown espera até cinco segundos pelo worker; sockets têm timeouts finitos. Um processo antigo ainda ativo não pode compartilhar a base com a nova instância. Pausar um monitor não cancela avisos já confirmados; editar nome/destinatário não altera snapshots existentes.

## Conteúdo

Texto UTF-8 inclui nome, URL, DOWN/RECOVERY, ID do incidente, primeira falha e confirmação da queda. Recuperação inclui início da sequência confirmada de sucessos, confirmação da recuperação e duração estimada em segundos: `recoveryStartedAt − firstFailureAt`. Exemplo: falhas em 0/60/120 s e sucessos em 180/240 s dão **180 segundos**, não 120 ou 240. A estimativa inclui pausas/lacunas e não prova indisponibilidade contínua. Timestamps são UTC e permanecem os da evidência, mesmo numa entrega atrasada.

Frações são preservadas em segundos decimais com ponto, sem truncamento nem zeros finais desnecessários: primeira falha em 12:00:00.100Z e início da recuperação em 12:03:01Z produzem **180.9 segundos**.

## Ambiente e Docker

`docker compose up --build -d` inicia aplicação, PostgreSQL e Mailpit v1.27.8. A interface capturadora está em [localhost:8025](http://localhost:8025), e o SMTP local em localhost:1025. Somente loopback é publicado. Mailpit captura destinatários fictícios como ops@example.org; nenhum relay externo é configurado. Seu conteúdo é efêmero; histórico e intenções pertencem ao PostgreSQL persistente.

| Variável | Padrão | Regra |
| --- | --- | --- |
| MONITOR_NOTIFICATIONS_ENABLED | true | false desliga worker e conserva trabalho no banco |
| MONITOR_NOTIFICATIONS_POLL_MS | 250 | Inteiro 1..60000 ms |
| SMTP_HOST / SMTP_PORT | smtp / 1025 | Host não vazio, porta 1..65535 |
| SMTP_FROM | monitor@example.org | Endereço único sem display name ou controles |
| SMTP_USERNAME / SMTP_PASSWORD | vazios | Externos; obrigatórios quando AUTH=true |
| SMTP_AUTH | false | Habilita autenticação |
| SMTP_STARTTLS | false | Habilita e exige STARTTLS; valida identidade do servidor |
| SMTP_SSL | false | TLS desde conexão; incompatível com STARTTLS=true |
| SMTP_CONNECT_TIMEOUT_MS | 5000 | Inteiro 1..60000 ms; conexão |
| SMTP_READ_TIMEOUT_MS | 3000 | Inteiro 1..60000 ms; leitura |
| SMTP_WRITE_TIMEOUT_MS | 5000 | Inteiro 1..60000 ms; escrita |
| SMTP_UI_PORT / SMTP_LOCAL_PORT | 8025 / 1025 | Publicação local; 0 escolhe porta efêmera no smoke |

Configuração inválida impede startup com mensagem fixa sem valores. `.env.example` contém somente defaults locais e placeholders. Para outro SMTP, configurar host/porta/remetente/auth/TLS no ambiente e recriar app; não versionar credenciais. STARTTLS e SSL usam confiança/hostname padrão, sem liberar certificados. Os timeouts seguem as propriedades documentadas pelo [Spring Boot](https://docs.spring.io/spring-boot/3.5/reference/io/email.html) e [Angus Mail](https://eclipse-ee4j.github.io/angus-mail/docs/api/org.eclipse.angus.mail/org/eclipse/angus/mail/smtp/package-summary.html).

## Inspeção durável

APIs de histórico pertencem à MON-09. Nesta entrega, estados e tentativas são consultáveis por SQL:

```sh
docker compose exec db psql -U monitor -d monitor
```

```sql
SELECT i.id, t.incident_id, t.type, i.recipient, i.status, i.attempt_count,
       i.next_attempt_at, i.completed_at
FROM monitor.notification_intents i
JOIN monitor.state_transitions t ON t.id=i.transition_id
ORDER BY t.confirmed_at, i.id;

SELECT notification_id, attempt_number, started_at, finished_at, outcome, error_code
FROM monitor.notification_attempts
ORDER BY notification_id, attempt_number;
```

Adaptar usuário/base se DB_USER/DB_NAME forem personalizados. Não apagar intenções ou evidências referenciadas; retenção será MON-11. V6 preserva intenções V5 como PENDING, sem refazer transições.

## Validação

```sh
docker compose run --build --rm tests
docker compose build app
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/validate-compose.ps1 -NoBuild
```

Testcontainers usa PostgreSQL real e a mesma imagem Mailpit do Compose, sem destinatários reais. Os testes usam relógio controlado para as esperas de 30..240 s e servidor SMTP controlado para timeout após DATA. O smoke usa projeto/volumes próprios, portas efêmeras e credenciais temporárias. Ele para o SMTP, confirma queda, reinicia app com intenção pendente, reinicia SMTP e observa recuperação e duas aceitações capturadas. O banco do operador é preservado.

Rastreabilidade: [spec](../.specs/features/mon-08-notificacoes/spec.md), [tasks](../.specs/features/mon-08-notificacoes/tasks.md) e [evidências](../.specs/features/mon-08-notificacoes/evidence.md).
