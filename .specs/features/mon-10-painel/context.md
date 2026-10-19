# MON-10: contexto

**Data:** 2026-10-19. **Status:** execução local autorizada pelo pedido.

## Feature Boundary

Painel HTML/CSS/JavaScript servido pelo Spring Boot, consumindo os contratos administrativos e de consulta reais. Sem alteração do domínio, segurança administrativa ou migrations.

## Implementation Decisions

O pedido escolhe skill, frontend nativo, backend real e testes Docker. AD-001 a AD-011 permanecem vigentes. A instrução de executar até validar autoriza as fases locais e commits da skill, sem confirmação repetida de ferramentas ou arquitetura já escolhidas.

### Declined / Undiscussed Gray Areas → Assumptions

Escolhas visuais, navegação, atualização, tratamento de respostas incertas e estratégia de testes constam em Assumptions & Open Questions da spec. O arquivo opcional monitor-painel-demo.html não existe no checkout. Manter layout próprio, simples e responsivo.

## Deferred Ideas

Polling automático, API agregada da lista, logout Basic e outros indicadores ficam fora desta entrega.
