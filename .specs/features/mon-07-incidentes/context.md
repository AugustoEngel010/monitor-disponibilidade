# MON-07: contexto

**Gathered:** 2026-10-14. **Status:** execução local autorizada pelo pedido.

## Feature Boundary

Aplicação dos resultados ao estado, incidentes e transições. SMTP e consultas permanecem nas entregas seguintes.

## Implementation Decisions

Pedido ratifica as regras pertinentes da MON-01. AD-002 exige intenção durável junto da transição; AD-006/008 exigem o mesmo lock administrativo e validação de versão/geração. Arquitetura JDBC, Flyway e Docker existentes serão mantidas. O pedido já escolheu skill, ferramentas e execução; não exige confirmação repetida das fases.

### Undiscussed Gray Areas → Assumptions

Spec registra ordem, fronteira temporal e migração de resultados antigos. São decisões técnicas para impedir replay retroativo e resultados fora de ordem. Nenhuma nova decisão de produto é necessária.

## Deferred Ideas

Envio SMTP, APIs de consulta, painel, retenção e coordenação distribuída permanecem fora do escopo.
