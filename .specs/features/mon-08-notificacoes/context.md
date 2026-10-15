# MON-08: contexto

Pedido autoriza spec, design, tasks, implementação, migrações e testes Docker. Defaults de notificações da MON-01 já delimitam as escolhas de produto; spec registra defaults técnicos. Não há pergunta impeditiva nem aprovação repetida necessária.

## Feature Boundary

Entrega SMTP assíncrona sobre intenções MON-07. Consultas HTTP, painel e retenção continuam MON-09/10/11.

## Implementation Decisions

Manter JDBC/PostgreSQL/Flyway, uma instância e SMTP de demonstração local. Intenções congeladas e transação de domínio existentes são preservadas. Ordem causal por incidente, inclusive após mudança de destinatário. Sem envio de mensagens reais: testes usam SMTP capturador e example.org.

## Deferred Ideas

Execução distribuída, API de histórico e dead-letter manual ficam fora do escopo.
