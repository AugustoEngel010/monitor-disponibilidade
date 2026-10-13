# MON-03: contexto

**Gathered:** 2026-10-13. **Status:** execução autorizada pelo pedido.

## Feature Boundary

Autenticação de um operador, configuração externa, proteção do futuro painel/API, CSRF e documentação, preservando Docker da MON-02.

## Implementation Decisions

O pedido autoriza produzir spec/design/tasks e implementar com testes. Basic é a proposta solicitada e já consta no contrato de segurança de MON-01. AD-001 e AD-004 permanecem vigentes. Os contratos de negócio pendentes da MON-01 não são ratificados por esta entrega.

### Undiscussed Gray Areas → Assumptions

Exceções públicas de health, nomes/limites de variáveis, token CSRF em sessão, proteção por padrão e rotação por recriação são defaults técnicos registrados na spec e comunicados ao usuário. Não há decisão de produto impeditiva. Context7 não está disponível; fontes oficiais consultadas depois de código/docs.

## Deferred Ideas

CRUD MON-04; painel MON-10; TLS na hospedagem futura; múltiplos usuários e autenticação alternativa fora do MVP.
